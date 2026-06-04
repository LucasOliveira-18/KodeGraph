package kodegraph.cli

import kodegraph.core.KodeGraph
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    val start = System.currentTimeMillis()

    if (args.isEmpty()) {
        println("Usage: kodegraph <source-root> [output-file]")
        exitProcess(1)
    }

    val sourceRoot = File(args[0])

    if (!sourceRoot.exists() || !sourceRoot.isDirectory) {
        println("Error: Source root does not exist or is not a directory: ${sourceRoot.absolutePath}")
        exitProcess(1)
    }

    val outputFile = if (args.size >= 2) {
        File(args[1])
    } else {
        File("dependency-graph.puml")
    }

    try {
        println("Scanning Kotlin sources in: ${sourceRoot.absolutePath}")

        KodeGraph
            .analyze(sourceRoot)
            .exportPlantUml(outputFile)

        println("PlantUML written to: ${outputFile.absolutePath}")
        println("Done in ${System.currentTimeMillis() - start} ms")
    } catch (e: Exception) {
        println("Error during analysis:")
        e.printStackTrace()
        exitProcess(1)
    }
}
