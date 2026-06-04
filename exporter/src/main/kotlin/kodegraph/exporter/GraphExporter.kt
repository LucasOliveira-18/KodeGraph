package kodegraph.exporter

import kodegraph.model.KGraph

interface GraphExporter {
    fun export(graph: KGraph): String
}

