package kodegraph.core

import kodegraph.engine.KotlinSourceScanner
import java.io.File

class KodeGraph private constructor() {

    companion object {
        fun analyze(sourceRoot: File): AnalysisResult {
            val scanner = KotlinSourceScanner()
            val graph = scanner.scan(listOf(sourceRoot))
            return AnalysisResult(graph)
        }
    }
}

