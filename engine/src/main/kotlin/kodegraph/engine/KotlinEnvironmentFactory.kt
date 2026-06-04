package kodegraph.engine

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.Closeable

class ManagedKotlinEnvironment internal constructor(
    val coreEnvironment: KotlinCoreEnvironment,
    private val disposable: Disposable
) : Closeable {
    override fun close() {
        Disposer.dispose(disposable)
    }
}

object KotlinEnvironmentFactory {

    fun create(): ManagedKotlinEnvironment {
        val disposable = Disposer.newDisposable()

        val configuration = CompilerConfiguration().apply {
            put(
                CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                MessageCollector.NONE
            )
        }

        val environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        return ManagedKotlinEnvironment(environment, disposable)
    }
}

