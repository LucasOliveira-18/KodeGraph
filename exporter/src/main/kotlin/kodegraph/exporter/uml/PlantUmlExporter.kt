package kodegraph.exporter.uml

import kodegraph.exporter.GraphExporter
import kodegraph.model.KGClass
import kodegraph.model.KGClassType
import kodegraph.model.KGraph

class PlantUmlExporter : GraphExporter {

    override fun export(graph: KGraph): String {

        val sb = StringBuilder()

        val classes = graph.classes

        val byFqName = classes.associateBy { it.fqName }
        val bySimpleName = classes.groupBy { it.simpleName }

        val interfaceImpls = mutableMapOf<String, MutableSet<String>>()

        // Interface implementation
        classes
            .filter { !it.isInterfaceType() }
            .forEach { clazz ->
                clazz.implementedInterfaces.forEach { iface ->
                    resolve(iface, clazz.packageName, byFqName, bySimpleName)
                        ?.takeIf { it.isInterfaceType() }
                        ?.let { target ->
                            interfaceImpls
                                .getOrPut(target.fqName) { mutableSetOf() }
                                .add(clazz.fqName)
                        }
                }
            }

        sb.appendLine("@startuml")
        sb.appendLine("skinparam linetype ortho")
        sb.appendLine("skinparam classFontSize 20")
        sb.appendLine("skinparam packageFontSize 20")
        sb.appendLine("skinparam nodesep 150")
        sb.appendLine("skinparam ranksep 100")
        sb.appendLine("title Dependency Graph")
        sb.appendLine()

        // Declare classes
        classes.forEach { clazz ->
            val alias = sanitize(clazz.simpleName)

            when (clazz.type) {
                KGClassType.CLASS -> sb.appendLine("""class "${clazz.simpleName}" as $alias""")
                KGClassType.DATA -> sb.appendLine("""class "${clazz.simpleName}" << (D,LightBlue) Data class >>""")
                KGClassType.INTERFACE -> sb.appendLine("""interface "${clazz.simpleName}" as $alias""")
                KGClassType.ENUM -> sb.appendLine("""enum "${clazz.simpleName}" as $alias""")
                KGClassType.ANNOTATION -> sb.appendLine("""annotation "${clazz.simpleName}" as $alias""")
                KGClassType.BROADCAST -> sb.appendLine("""class "${clazz.simpleName}" << (B,Yellow) BroadcastReceiver >>""")
            }
        }

        sb.appendLine()

        // Group interfaces and impls together
        interfaceImpls.forEach { (ifaceFq, implFqSet) ->
            val iface = byFqName[ifaceFq] ?: return@forEach

            sb.appendLine("together {")

            sb.appendLine("interface ${sanitize(iface.simpleName)}")

            implFqSet.forEach { implFq ->
                val impl = byFqName[implFq] ?: return@forEach
                sb.appendLine("class ${sanitize(impl.simpleName)}")
            }

            sb.appendLine("}")
            sb.appendLine()
        }

        // Interface implementation arrows
        classes
            .filter { !it.isInterfaceType() }
            .forEach { clazz ->

                clazz.implementedInterfaces.forEach { iface ->

                    resolve(iface, clazz.packageName, byFqName, bySimpleName)
                        ?.takeIf { it.isInterfaceType() }
                        ?.let { target ->

                            val ifaceAlias = sanitize(target.simpleName)
                            val implAlias = sanitize(clazz.simpleName)

                            sb.appendLine("class $implAlias implements $ifaceAlias")
                            // Optional hidden edge to stabilize layout
                            sb.appendLine("$ifaceAlias -[hidden]- $implAlias")
                        }
                }
            }

        sb.appendLine()

        // Constructor dependencies
        classes.forEach { clazz ->
            clazz.dependencies.forEach { dep ->
                resolve(dep.type, clazz.packageName, byFqName, bySimpleName)
                    ?.let { target ->
                        sb.appendLine(
                            "${sanitize(clazz.simpleName)} --> ${sanitize(target.simpleName)}"
                        )
                    }
            }
        }

        sb.appendLine("@enduml")
        return sb.toString()
    }

    private fun resolve(
        typeName: String,
        currentPackage: String,
        byFqName: Map<String, KGClass>,
        bySimpleName: Map<String, List<KGClass>>
    ): KGClass? {

        byFqName[typeName]?.let { return it }

        if (currentPackage.isNotEmpty()) {
            byFqName["$currentPackage.$typeName"]?.let { return it }
        }

        return bySimpleName[typeName]?.firstOrNull()
    }

    private fun sanitize(fqName: String): String = fqName.replace(".", "_")
}

