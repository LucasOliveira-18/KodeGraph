package kodegraph.core

import kodegraph.exporter.uml.PlantUmlExporter
import kodegraph.model.KGraph
import java.io.File

class AnalysisResult internal constructor(
    private val graph: KGraph
) {

    fun exportPlantUml(outputFile: File): File {
        val exporter = PlantUmlExporter()
        val content = exporter.export(graph)
        outputFile.writeText(content)
        return outputFile
    }

    fun graph(): KGraph = graph
}

