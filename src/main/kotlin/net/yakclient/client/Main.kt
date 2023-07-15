package net.yakclient.client

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.component.context.ContextNodeTypes
import net.yakclient.boot.dependency.DependencyTypeProvider
import net.yakclient.boot.main.ProductionBootInstance
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.io.path.exists

public fun main(args: Array<String>) {
    val parser = ArgParser("minecraft-boot")

    val workingDir by parser.option(ArgType.String, "working-dir", "w").default(System.getProperty("user.dir"))
    val mcVersion by parser.option(ArgType.String, "mcVersion", "v").required()
    val accessToken by parser.option(ArgType.String, "accessToken").required()
    val devMode by parser.option(ArgType.Boolean, "devmode").default(false)

    parser.parse(args)
    val pwdPath = Path.of(workingDir)

    val boot = ProductionBootInstance(pwdPath, DependencyTypeProvider())
    val request = SoftwareComponentArtifactRequest("net.yakclient.components:ext-loader:1.0-SNAPSHOT")
    if (!boot.isCached(request.descriptor)) boot.cache(
        request,
        if (devMode) SoftwareComponentRepositorySettings.local() else
            SoftwareComponentRepositorySettings
                .default(
                    "http://maven.yakclient.net/snapshots",
                    preferredHash = HashType.SHA1
                )
    )

    val factory = boot.componentGraph.get(request.descriptor)
        .orNull()?.factory!! as ComponentFactory<ComponentConfiguration, ComponentInstance<ComponentConfiguration>>

    val extensions =
        if (System.`in`.available() == 0) listOf() else ObjectMapper().registerModule(KotlinModule.Builder().build())
            .readValue<List<Map<String, Any>>>(System.`in`)
    val config = factory.parseConfiguration(
        ContextNodeTypes.newValueType(
            mapOf(
                "mcVersion" to mcVersion,
                "mcArgs" to listOf("--version", mcVersion, "--accessToken", accessToken),
                "extensions" to extensions
            )
        )
    )

    val instance = factory.new(config)

    instance.start()
}