package kodegraph.engine

import kodegraph.model.KGClass
import kodegraph.model.KGClassType
import kodegraph.model.KGDependency
import kodegraph.model.KGraph
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import java.io.File

class KotlinSourceScanner {

    private data class FileParseResult(
        val classes: List<KGClass>,
        val starImportPackages: List<String>
    )

    fun scan(sourceRoots: List<File>): KGraph {
        return KotlinEnvironmentFactory.create().use { managed ->
            val psiFactory = KtPsiFactory(managed.coreEnvironment.project, false)

            val ktFiles = sourceRoots.flatMap { root ->
                if (!root.exists()) return@flatMap emptyList<File>()
                root.walkTopDown()
                    .filter { it.extension == "kt" }
                    .filter { !it.name.contains("test", ignoreCase = true) }
                    .toList()
            }

            // First pass: parse all files, collecting classes and star-import data
            val fileResults = ktFiles.map { file ->
                parseFile(file, psiFactory)
            }
            val allClasses = fileResults.flatMap { it.classes }

            // Second pass: resolve star imports and add import-based dependencies
            val projectFqNames = allClasses.map { it.fqName }.toSet()
            val resolvedClasses = fileResults.flatMap { result ->
                result.classes.map { clazz ->
                    var enriched = clazz

                    // Resolve star imports
                    if (result.starImportPackages.isNotEmpty()) {
                        val starResolved = resolveStarImports(result.starImportPackages, allClasses)
                        if (starResolved.isNotEmpty()) {
                            enriched = enriched.copy(imports = enriched.imports + starResolved)
                        }
                    }

                    // Add import-based dependencies: any imported project class becomes a dependency
                    val importDeps = enriched.imports.values
                        .filter { it in projectFqNames && it != enriched.fqName }
                        .map { KGDependency(it) }
                        .toSet()
                    // Deduplicate: existing deps use short names; also check short form of import FQ names
                    val existingDepTypes = enriched.dependencies.map { it.type }.toSet()
                    val existingShortNames = existingDepTypes.map { it.substringAfterLast(".") }.toSet()
                    val newDeps = importDeps.filter { dep ->
                        dep.type !in existingDepTypes &&
                        dep.type.substringAfterLast(".") !in existingShortNames
                    }
                    if (newDeps.isNotEmpty()) {
                        enriched = enriched.copy(dependencies = enriched.dependencies + newDeps)
                    }

                    enriched
                }
            }

            KGraph(resolvedClasses)
        }
    }

    /**
     * Resolves star imports (e.g. `import com.example.data.*`) against the known class set.
     * Maps every class whose package matches a star-imported package from simpleName → fqName.
     */
    private fun resolveStarImports(
        starImportPackages: List<String>,
        allClasses: List<KGClass>
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        starImportPackages.forEach { pkg ->
            allClasses
                .filter { it.packageName == pkg }
                .forEach { clazz ->
                    result[clazz.simpleName] = clazz.fqName
                }
        }
        return result
    }

    private fun parseFile(file: File, psiFactory: KtPsiFactory): FileParseResult {
        val ktFile = psiFactory.createFile(file.readText())
        val packageName = ktFile.packageFqName.asString()

        // Parse imports: explicit (shortName → fqName) and star imports (package names)
        val explicitImports = mutableMapOf<String, String>()
        val starImportPackages = mutableListOf<String>()

        ktFile.importDirectives.forEach { directive ->
            val fqName = directive.importedFqName
            if (directive.isAllUnder) {
                // Star import: `import com.example.data.*`
                fqName?.asString()?.let { starImportPackages.add(it) }
            } else {
                // Explicit import: `import com.example.data.User`
                val fq = fqName?.asString()
                if (fq != null) {
                    val shortName = fq.substringAfterLast(".")
                    val alias = directive.aliasName
                    if (alias != null) {
                        explicitImports[alias] = fq
                    } else {
                        // Only add if not already present (first import wins)
                        if (shortName !in explicitImports) {
                            explicitImports[shortName] = fq
                        }
                    }
                }
            }
        }

        val classes = ktFile.declarations
            .filterIsInstance<KtClass>()
            .mapNotNull { ktClass ->

                val className = ktClass.name ?: return@mapNotNull null
                val fqName =
                    if (packageName.isNotEmpty())
                        "$packageName.$className"
                    else className

                // Constructor dependencies
                val constructorDeps =
                    ktClass.primaryConstructor?.valueParameters
                        ?.mapNotNull { param ->
                            param.typeReference?.text
                                ?.substringBefore("<")
                                ?.replace("?", "")
                                ?.trim()
                        }
                        ?.map { KGDependency(it) }
                        ?: emptyList()

                // lateinit property dependencies
                val lateinitDeps =
                    ktClass.getProperties()
                        .filter { it.hasModifier(KtTokens.LATEINIT_KEYWORD) }
                        .mapNotNull { prop ->
                            extractInnerType(prop.typeReference?.text)
                        }
                        .map { KGDependency(it) }

                //
                val propertyDeps =
                    ktClass.getProperties()
                        .filter { prop ->
                            prop.typeReference != null &&
                                    prop.initializer != null
                        }
                        .mapNotNull { prop ->
                            prop.typeReference?.text
                                ?.substringBefore("<")
                                ?.removeSuffix("?")
                                ?.trim()
                        }
                        .map { KGDependency(it) }


                val delegatedDeps =
                    ktClass.getProperties()
                        .mapNotNull { prop ->
                            val delegate = prop.delegate ?: return@mapNotNull null
                            val call = delegate.expression as? KtCallExpression ?: return@mapNotNull null

                            // Extract the type argument: inject<Repo>(), lazyGet<Repo>(), viewModels<MyVM>()
                            call.typeArgumentList
                                ?.arguments
                                ?.firstOrNull()
                                ?.typeReference
                                ?.text
                                ?.substringBefore("<")
                                ?.removeSuffix("?")
                                ?.trim()
                        }
                        .map { KGDependency(it) }


                // merge dependencies
                val mergedDependencies = constructorDeps + lateinitDeps + propertyDeps + delegatedDeps

                val interfaces =
                    ktClass.superTypeListEntries
                        .mapNotNull { it.typeReference?.text }

                val type = if (ktClass.isInterface()) {
                    KGClassType.INTERFACE
                } else if (ktClass.isEnum()) {
                    KGClassType.ENUM
                } else if(ktClass.isAnnotation()) {
                    KGClassType.ANNOTATION
                } else if(ktClass.isData()) {
                    KGClassType.DATA
                } else if(ktClass.isBroadcastReceiver()) {
                    KGClassType.BROADCAST
                }else {
                    KGClassType.CLASS
                }

                KGClass(
                    fqName = fqName,
                    simpleName = className,
                    packageName = packageName,
                    type = type,
                    dependencies = mergedDependencies,
                    implementedInterfaces = interfaces,
                    imports = explicitImports
                )
            }

        return FileParseResult(classes, starImportPackages)
    }



    private fun extractInnerType(raw: String?): String? {
        if (raw == null) return null
        val text = raw.trim().removeSuffix("?")

        return if ("<" in text && ">" in text) {
            text.substringAfter("<")
                .substringBeforeLast(">")
                .trim()
        } else {
            text
        }
    }



    private fun KtClass.isBroadcastReceiver(): Boolean {
        return this.superTypeListEntries.any { entry ->
            // Only look at call entries (BroadcastReceiver())
            (entry as? KtSuperTypeCallEntry)?.calleeExpression?.text == "BroadcastReceiver"
        }
    }

}

