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
//TODO : parse imports from files
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

            val classes = ktFiles.flatMap { file ->
                parseFile(file, psiFactory)
            }

            KGraph(classes)
        }
    }

    private fun parseFile(file: File, psiFactory: KtPsiFactory): List<KGClass> {
        val ktFile = psiFactory.createFile(file.readText())
        val packageName = ktFile.packageFqName.asString()

        return ktFile.declarations
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
                    implementedInterfaces = interfaces
                )
            }
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

