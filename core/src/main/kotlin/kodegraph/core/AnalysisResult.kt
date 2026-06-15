package kodegraph.core

import kodegraph.exporter.GraphExporter
import kodegraph.model.KGraph
import java.io.File

class AnalysisResult internal constructor(
    private val graph: KGraph
) {

    fun export(exporter: GraphExporter, outputFile: File): File {
        val content = exporter.export(graph)
        outputFile.writeText(content)
        return outputFile
    }

    fun graph(): KGraph = graph
}

