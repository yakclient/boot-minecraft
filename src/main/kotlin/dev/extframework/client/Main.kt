package dev.extframework.client

import BootLoggerFactory
import com.durganmcbroom.jobs.*
import dev.extframework.boot.archive.*
import dev.extframework.boot.loader.*
import dev.extframework.common.util.resolve
import dev.extframework.extloader.InternalExtensionEnvironment
import dev.extframework.extloader.extension.DefaultExtensionResolver
import dev.extframework.extloader.extension.partition.DefaultPartitionResolver
import dev.extframework.extloader.initExtensions
import dev.extframework.tooling.api.environment.ExtensionEnvironment
import dev.extframework.tooling.api.environment.extract
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.partition.artifact.PartitionDescriptor
import dev.extframework.tooling.api.target.ApplicationTarget
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

private fun getHomedir(): Path {
    return getMinecraftDir() resolve ".extframework"
}

private fun getMinecraftDir(): Path {
    val osName = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")

    return when {
        osName.contains("win") -> {
            val appData = System.getenv("APPDATA")?.let(::Path) ?: Path(userHome, "AppData", "Roaming")
            appData resolve ".minecraft"
        }

        osName.contains("mac") -> Paths.get(userHome, "Library", "Application Support", "minecraft")
        else -> Paths.get(userHome, ".minecraft") // Assuming Linux/Unix-like
    }
}

public fun main(args: Array<String>) {
    val command = ProductionCommand(getMinecraftDir(), getHomedir()).apply { main(args) }

    val launchContext = command.launchContext

    launch(BootLoggerFactory()) {
        val environment = InternalExtensionEnvironment(
            getHomedir(),
            launchContext.archiveGraph,
            launchContext.dependencyTypes,
            AppTarget(
                launchContext.launchInfo.minecraftNode,
                launchContext.launchInfo.minecraftPath
            )
        )
        environment += ClientExtensionResolver(
            environment,
            launchContext.launchInfo.extensionDirPath
        )

        System.setProperty("mapping.target", launchContext.launchInfo.mappingNS)
        initExtensions(
            launchContext.launchInfo.requests,
            environment
        )().merge()

        val app = environment[ApplicationTarget].extract().node.handle!!.classloader


        val mainClass = app.loadClass(
            launchContext.launchInfo.mainClass
        )

        mainClass.getMethod("main", Array<String>::class.java).invoke(null, launchContext.launchInfo.args)
    }
}

private class ClientExtensionResolver(
    environment: ExtensionEnvironment,
    private val path: Path,
) : DefaultExtensionResolver(
    ClientExtensionResolver::class.java.classLoader, environment
) {
    override val partitionResolver: DefaultPartitionResolver = object : DefaultPartitionResolver(
        factory,
        environment,
        { extensionLoaders[it]!! }) {
        override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
            return path resolve super.pathForDescriptor(descriptor, classifier, type)
        }
    }

    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
        return path resolve super.pathForDescriptor(descriptor, classifier, type)
    }
}