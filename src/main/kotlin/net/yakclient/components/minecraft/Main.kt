package net.yakclient.components.minecraft

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import net.yakclient.boot.BootInstance
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyTypeProvider
import net.yakclient.boot.main.ProductionBootInstance
import net.yakclient.boot.new
import java.nio.file.Path

public fun main(args: Array<String>) {
    val parser = ArgParser("minecraft-boot")

    val workingDir by parser.option(ArgType.String, "working-dir", "w").default(System.getProperty("user.dir"))
    val mcVersion by parser.option(ArgType.String, "mcVersion", "v").required()


    parser.parse(args)

    val boot = ProductionBootInstance(Path.of(workingDir), DependencyTypeProvider())
    val request = SoftwareComponentArtifactRequest("net.yakclient.components:yak:1.0-SNAPSHOT")
    if (!boot.isCached(request.descriptor)) boot.cache(
        request,
        SoftwareComponentRepositorySettings.default(
            "http://maven.yakclient.net/snapshots",
            preferredHash = HashType.SHA1
        )
    )

    boot.componentGraph.get(request.descriptor).orNull()!!.factory?.parseConfiguration( )

}