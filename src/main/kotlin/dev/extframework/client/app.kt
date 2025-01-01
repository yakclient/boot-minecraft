package dev.extframework.client

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.zip.ZipFinder
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.core.minecraft.api.MinecraftAppApi
import dev.extframework.tooling.api.target.ApplicationDescriptor
import dev.extframework.tooling.api.target.ApplicationTarget
import java.nio.file.Path

private fun emptyAccess(version: String) = object : ArchiveAccessTree {
    override val descriptor: ArtifactMetadata.Descriptor = ApplicationDescriptor(
        "net.minecraft",
        "client",
        version,
        null
    )
    override val targets: List<ArchiveTarget> = listOf()
}

internal class ClasspathApp(
    classpath: List<Path>,
    version: String,
    override val path: Path,
    override val gameJar: Path
) : MinecraftAppApi(classpath) {
    override val gameDir: Path = path

    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
            override val descriptor: ApplicationDescriptor = ApplicationDescriptor(
                "net.minecraft",
                "client",
                version,
                null
            )

            override val access: ArchiveAccessTree = emptyAccess(version)
            private val references = classpath.map { it ->
                ZipFinder.find(it)
            }

            override val handle: ArchiveHandle = classLoaderToArchive(
                MutableClassLoader(
                    name = "minecraft-loader",
                    resources = MutableResourceProvider(
                        references.mapTo(ArrayList()) { ArchiveResourceProvider(it) }
                    ),
                    sources = MutableSourceProvider(
                        references.mapTo(ArrayList()) { ArchiveSourceProvider(it) }
                    ),
                    parent = ClassLoader.getSystemClassLoader(),
                )
            )
        }
}

//internal class MCNodeApp(
//    minecraftNode: MinecraftNode,
//    override val path: Path,
//) : ApplicationTarget {
//    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
//        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
//            override val descriptor: ApplicationDescriptor = ApplicationDescriptor(
//                "net.minecraft",
//                "client",
//                minecraftNode.descriptor.version,
//                null
//            )
//            override val access: ArchiveAccessTree =emptyAccess(minecraftNode.descriptor.version) // minecraftNode.access
//            override val handle: ArchiveHandle = classLoaderToArchive(
//                MutableClassLoader(
//                    name = "minecraft-loader",
//                    resources = MutableResourceProvider(
//                        (minecraftNode.libraries.map { it.archive } + minecraftNode.archive)
//                            .mapTo(ArrayList()) { ArchiveResourceProvider(it) }
//                    ),
//                    sources = MutableSourceProvider(
//                        (minecraftNode.libraries.map { it.archive } + minecraftNode.archive)
//                            .mapTo(ArrayList()) { ArchiveSourceProvider(it) }
//                    ),
//                    parent = ClassLoader.getSystemClassLoader(),
//                )
//            )
//        }
//}