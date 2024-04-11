package net.yakclient.client

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.resources.ResourceAlgorithm
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import net.yakclient.boot.archive.*
import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.dependency.BasicDependencyNode
import net.yakclient.boot.main.ProductionBootInstance
import net.yakclient.common.util.readInputStream
import net.yakclient.common.util.resolve
import runBootBlocking
import java.nio.file.Path

private fun getYakClientDir(): Path {
    return Path.of(System.getProperty("user.home")) resolve ".yakclient"
}

public fun main(args: Array<String>) {
    val parser = ArgParser("minecraft-boot")

    val workingDir by parser.option(ArgType.String, "working-dir", "w").default(getYakClientDir().toString())
    val devMode by parser.option(ArgType.Boolean, "devmode").default(false)

    parser.parse(args)
    val pwdPath = Path.of(workingDir)

    val clientDependencies = loadClientDependencyInfo()

    val archiveGraphMapDelegate = HashMap<ArtifactMetadata.Descriptor, ArchiveNode<*>>()
    val boot = ProductionBootInstance(
        pwdPath, ArchiveGraph(
            pwdPath resolve "archives",
            object : MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>> by archiveGraphMapDelegate {

                override fun get(key: ArtifactMetadata.Descriptor): ArchiveNode<*>? {
                    if (key is SimpleMavenDescriptor && clientDependencies.contains(
                            "${key.group}:${key.artifact}"
                        )
                    ) {
                        put(
                            key, BasicDependencyNode(
                                key,
                                null,
                                setOf(),
                                object : ArchiveAccessTree {
                                    override val descriptor: ArtifactMetadata.Descriptor = key
                                    override val targets: List<ArchiveTarget> = emptyList()
                                },
                                EMPTY_RESOLVER
                            )
                        )
                    }

                    return archiveGraphMapDelegate[key]
                }
            }
        )
    )
    val request = SoftwareComponentArtifactRequest("net.yakclient.components:ext-loader:1.1-SNAPSHOT")

    runBootBlocking(JobName("Cache and start yakclient extloader")) {
        if (!boot.isCached(request.descriptor)) boot.cache(
            request,
            if (devMode) SoftwareComponentRepositorySettings.local() else
                SoftwareComponentRepositorySettings
                    .default(
                        "http://maven.yakclient.net/snapshots",
                        preferredHash = ResourceAlgorithm.SHA1
                    )
        )().merge()

        val factory = boot.archiveGraph.get(
            request.descriptor,
            boot.componentResolver
        )().merge().factory!! as ComponentFactory<ComponentConfiguration, ComponentInstance<ComponentConfiguration>>

        val extensions =
            if (System.`in`.available() == 0) mapOf() else ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<Map<String, Any>>(System.`in`)
        val config = factory.parseConfiguration(
            ContextNodeTypes.newValueType(
                extensions
            )
        )

        val instance = factory.new(config)

        instance.start()().merge()
    }
}

private fun loadClientDependencyInfo(): Set<String> {
    val fileIn = ClassLoader.getSystemResourceAsStream("dependencies.txt")!!
    val fileStr = String(fileIn.readInputStream())
    return fileStr.split("\n").toSet()
}

private val EMPTY_RESOLVER = object :
    ArchiveNodeResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, BasicDependencyNode, RepositorySettings, ArtifactMetadata<ArtifactMetadata.Descriptor, *>> {
    override val metadataType: Class<ArtifactMetadata<ArtifactMetadata.Descriptor, *>>
        get() = TODO("Not yet implemented")
    override val name: String
        get() = TODO("Not yet implemented")
    override val nodeType: Class<in BasicDependencyNode>
        get() = TODO("Not yet implemented")

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<ArtifactMetadata.Descriptor> {
        TODO("Not yet implemented")
    }

    override fun createContext(settings: RepositorySettings): ResolutionContext<ArtifactRequest<ArtifactMetadata.Descriptor>, *, ArtifactMetadata<ArtifactMetadata.Descriptor, *>, *> {
        TODO("Not yet implemented")
    }

    override fun serializeDescriptor(descriptor: ArtifactMetadata.Descriptor): Map<String, String> {
        TODO("Not yet implemented")
    }

    override fun pathForDescriptor(
        descriptor: ArtifactMetadata.Descriptor,
        classifier: String,
        type: String
    ): Path {
        TODO("Not yet implemented")
    }

    override fun load(
        data: ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<BasicDependencyNode> {
        TODO("Not yet implemented")
    }

    override fun cache(
        metadata: ArtifactMetadata<ArtifactMetadata.Descriptor, *>,
        helper: ArchiveCacheHelper<ArtifactMetadata.Descriptor>
    ): Job<ArchiveData<ArtifactMetadata.Descriptor, CacheableArchiveResource>> {
        TODO("Not yet implemented")
    }

}