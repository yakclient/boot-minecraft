package dev.extframework.client

import BootLoggerFactory
import com.durganmcbroom.jobs.*
import dev.extframework.boot.archive.*
import dev.extframework.common.util.resolve
import dev.extframework.extloader.InternalExtensionEnvironment
import dev.extframework.extloader.initExtensions
import dev.extframework.internal.api.environment.*
import dev.extframework.internal.api.extension.*
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.file.Path
import java.nio.file.Paths

private fun getHomedir(): Path {
    return getMinecraftDir() resolve ".extframework"
}

private fun getMinecraftDir(): Path {
    val osName = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")

    return when {
        osName.contains("win") -> {
            val appData = System.getenv("APPDATA")?.let(Path::of) ?: Path.of(userHome, "AppData", "Roaming")
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
            AppTarget(launchContext.launchInfo.classloader, launchContext.options.version, launchContext.launchInfo.minecraftPath)
        )
        initExtensions(
            launchContext.launchInfo.requests,
            environment
        )

//        initExtensions(
//            launchContext,
//            AppTarget(launchContext.launchInfo.classloader, launchContext.options.version)
//        )().merge()

        val app = environment[ApplicationTarget].extract().node.handle!!.classloader

        val mainClass = app.loadClass(
            launchContext.launchInfo.mainClass
        )

        mainClass.getMethod("main", Array<String>::class.java).invoke(null, launchContext.launchInfo.args)
    }
}

//private fun initExtensions(
//    launchContext: LaunchContext,
//    app: ApplicationTarget
//): Job<ExtensionEnvironment> = job {
//    val environment = ExtensionEnvironment()
//
//    environment += ArchiveGraphAttribute(launchContext.archiveGraph)
//    environment += DependencyTypeContainerAttribute(
//        launchContext.dependencyTypes
//    )
//
//    environment += app
//    environment += ValueAttribute(launchContext.options.workingDir, wrkDirAttrKey)
//
//    environment += ValueAttribute(ClassLoader.getSystemClassLoader(), parentCLAttrKey)
//
//    environment += object : ExtensionClassLoaderProvider {}
//
//    environment += MutableObjectContainerAttribute(partitionLoadersAttrKey).apply {
//        TweakerPartitionLoader().also { container.register(it.type, it) }
//    }
//
//    val extensionsPath = environment[wrkDirAttrKey].extract().value resolve "extensions"
//
//    extensionsPath.toFile().deleteRecursively()
//
//    // Add dev graph to environment
//    environment += DevExtensionResolver(
//        environment[parentCLAttrKey].extract().value,
//        environment,
//        extensionsPath
//    )
//
//    fun allExtensions(node: ExtensionNode): Set<ExtensionNode> {
//        return node.access.targets.map { it.relationship.node }
//            .filterIsInstance<ExtensionNode>()
//            .flatMapTo(HashSet(), ::allExtensions) + node
//    }
//
//    // Get extension resolver
//    val extensionResolver = environment[ExtensionResolver].extract()
//
//    fun loadTweakers(
//        artifact: Artifact<ExtensionArtifactMetadata>
//    ): AsyncJob<List<ExtensionPartitionContainer<TweakerPartitionNode, *>>> = asyncJob {
//        val parents =
//            artifact.parents.mapAsync {
//                loadTweakers(it)().merge()
//            }
//
//        val tweakerContainer: ExtensionPartitionContainer<TweakerPartitionNode, *>? = run {
//            val descriptor = PartitionDescriptor(artifact.metadata.descriptor, TweakerPartitionLoader.TYPE)
//
//            val cacheResult = environment.archiveGraph.cacheAsync(
//                PartitionArtifactRequest(descriptor),
//                artifact.metadata.repository,
//                extensionResolver.partitionResolver,
//            )()
//            if (cacheResult.isFailure && cacheResult.exceptionOrNull() is ArchiveException.ArchiveNotFound) return@run null
//            else cacheResult.merge()
//
//            environment.archiveGraph.get(
//                descriptor,
//                extensionResolver.partitionResolver,
//            )().merge()
//        } as? ExtensionPartitionContainer<TweakerPartitionNode, *>
//
//        parents.awaitAll().flatten() + listOfNotNull(tweakerContainer)
//    }
//
//    val tweakers = job(JobName("Load tweakers")) {
//        runBlocking(Dispatchers.IO) {
//            val artifact = extensionResolver.createContext(launchContext.launchInfo.repository)
//                .getAndResolveAsync(
//                    ExtensionArtifactRequest(launchContext.launchInfo.extension)
//                )().merge()
//
//            loadTweakers(artifact)().merge()
//        }
//    }().mapException {
//        ExtensionLoadException(launchContext.launchInfo.extension, it) {
//            launchContext.launchInfo.extension asContext "Extension"
//        }
//    }.merge()
//
//    tweakers.map { it.node }.forEach {
//        it.tweaker.tweak(environment)().merge()
//    }
//
//    val extensionNode = job(JobName("Load extensions")) {
//        environment.archiveGraph.cache(
//            ExtensionArtifactRequest(
//                launchContext.launchInfo.extension,
//            ),
//            launchContext.launchInfo.repository,
//            extensionResolver
//        )().merge()
//        environment.archiveGraph.get(
//            launchContext.launchInfo.extension,
//            extensionResolver
//        )().merge()
//    }().mapException {
//        ExtensionLoadException(launchContext.launchInfo.extension, it) {
//            launchContext.launchInfo.extension asContext "Extension"
//        }
//    }.merge()
//
//    // Get all extension nodes in order
//    val extensions = allExtensions(extensionNode)
//
//    // Get extension observer (if there is one after tweaker application) and observer each node
//    environment[ExtensionNodeObserver].getOrNull()?.let { extensions.forEach(it::observe) }
//
//    // Call init on all extensions, this is ordered correctly
//    extensions.forEach {
//        environment[ExtensionRunner].extract().init(it)().merge()
//    }
//
//    environment
//}
//
//private class DevExtensionResolver(
//    parent: ClassLoader,
//    environment: ExtensionEnvironment,
//    private val path: Path,
//) : DefaultExtensionResolver(
//    parent, environment
//) {
//    override val partitionResolver: DefaultPartitionResolver = object : DefaultPartitionResolver(
//        factory,
//        environment,
//        { extensionLoaders[it]!! }) {
//        override fun pathForDescriptor(descriptor: PartitionDescriptor, classifier: String, type: String): Path {
//            return path resolve super.pathForDescriptor(descriptor, classifier, type)
//        }
//    }
//
//    override fun pathForDescriptor(descriptor: ExtensionDescriptor, classifier: String, type: String): Path {
//        return path resolve super.pathForDescriptor(descriptor, classifier, type)
//    }
//}