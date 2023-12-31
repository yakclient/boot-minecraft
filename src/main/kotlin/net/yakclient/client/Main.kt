package net.yakclient.client

import bootFactories
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.jobs.JobName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.main.ProductionBootInstance
import net.yakclient.common.util.resolve
import net.yakclient.`object`.ObjectContainerImpl
import orThrow
import java.nio.file.Path

private fun getYakClientDir() : Path {
    return Path.of(System.getProperty("user.home")) resolve ".yakclient"
}

public fun main(args: Array<String>) {
    val parser = ArgParser("minecraft-boot")

    val workingDir by parser.option(ArgType.String, "working-dir", "w").default(getYakClientDir().toString())
    val devMode by parser.option(ArgType.Boolean, "devmode").default(false)

    parser.parse(args)
    val pwdPath = Path.of(workingDir)

    val boot = ProductionBootInstance(pwdPath, ArchiveGraph(
        pwdPath resolve "archives",
        HashMap()
    ))
    val request = SoftwareComponentArtifactRequest("net.yakclient.components:ext-loader:1.0-SNAPSHOT")

    runBlocking(bootFactories() + JobName("Cache and start yakclient extloader")) {
        if (!boot.isCached(request.descriptor)) boot.cache(
            request,
            if (devMode) SoftwareComponentRepositorySettings.local() else
                SoftwareComponentRepositorySettings
                    .default(
                        "http://maven.yakclient.net/snapshots",
                        preferredHash = HashType.SHA1
                    )
        ).orThrow()

        val factory = boot.archiveGraph.get(request.descriptor, boot.componentResolver)
            .orThrow().factory!! as ComponentFactory<ComponentConfiguration, ComponentInstance<ComponentConfiguration>>

        val extensions =
            if (System.`in`.available() == 0) mapOf() else ObjectMapper().registerModule(KotlinModule.Builder().build())
                .readValue<Map<String, Any>>(System.`in`)
        val config = factory.parseConfiguration(
            ContextNodeTypes.newValueType(
                extensions
            )
        )

        val instance = factory.new(config)

        instance.start()
    }
}