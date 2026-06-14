package kodegraph.exporter.html

import kodegraph.exporter.GraphExporter
import kodegraph.model.KGClass
import kodegraph.model.KGraph
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.stream.Collectors

class HtmlGraphExporter : GraphExporter {

    override fun export(graph: KGraph): String {
        val classes = graph.classes
        val byFqName = classes.associateBy { it.fqName }
        val bySimpleName = classes.groupBy { it.simpleName }

        val jsonNodes = classes.joinToString(separator = ",\n") { clazz ->
            val type = clazz.type.name
            val packageName = clazz.packageName
            val fqName = escapeJson(clazz.fqName)
            val simpleName = escapeJson(clazz.simpleName)
            val implemented = clazz.implementedInterfaces.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
            """      { "id": "$fqName", "label": "$simpleName", "packageName": "$packageName", "type": "$type", "implementedInterfaces": $implemented }"""
        }

        val jsonEdges = mutableListOf<String>()
        classes.forEach { clazz ->
            // Interfaces implemented
            clazz.implementedInterfaces.forEach { iface ->
                resolve(iface, clazz.packageName, byFqName, bySimpleName)?.let { target ->
                    jsonEdges.add(
                        """      { "from": "${escapeJson(clazz.fqName)}", "to": "${escapeJson(target.fqName)}", "label": "implements", "type": "implements" }"""
                    )
                }
            }
            // Outgoing class dependencies
            clazz.dependencies.forEach { dep ->
                resolve(dep.type, clazz.packageName, byFqName, bySimpleName)?.let { target ->
                    jsonEdges.add(
                        """      { "from": "${escapeJson(clazz.fqName)}", "to": "${escapeJson(target.fqName)}", "label": "depends", "type": "depends" }"""
                    )
                }
            }
        }
        val jsonEdgesStr = jsonEdges.distinct().joinToString(separator = ",\n")

        return getHtmlTemplate(jsonNodes, jsonEdgesStr)
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

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun loadResource(path: String): String {
        val inputStream = javaClass.getResourceAsStream(path)
            ?: throw IllegalStateException("Resource not found: $path")
        return BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.lines().collect(Collectors.joining("\n"))
        }
    }

    private fun getHtmlTemplate(nodesJson: String, edgesJson: String): String {
        val template = loadResource("/kodegraph/exporter/html/template.html")
        val script = loadResource("/kodegraph/exporter/html/script.js")

        return template
            .replace("/*NODES_JSON*/", nodesJson)
            .replace("/*EDGES_JSON*/", edgesJson)
            .replace("/*SCRIPT_JS*/", script)
    }
}
