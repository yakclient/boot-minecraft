package dev.extframework.client

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import dev.extframework.minecraft.bootstrapper.MinecraftNode
import java.nio.file.Path

internal class AppTarget(
    private val minecraftNode: MinecraftNode,
    override val path: Path,
) : ApplicationTarget {
    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> =
        object : ClassLoadedArchiveNode<ApplicationDescriptor> {
            override val descriptor: ApplicationDescriptor = ApplicationDescriptor(
                "net.minecraft",
                "client",
                minecraftNode.descriptor.version,
                null
            )
            override val access: ArchiveAccessTree = minecraftNode.access
            override val handle: ArchiveHandle = classLoaderToArchive(
                MutableClassLoader(
                    name = "minecraft-loader",
                    resources = MutableResourceProvider(
                        (minecraftNode.libraries.map { it.archive } + minecraftNode.archive)
                            .mapTo(ArrayList()) { ArchiveResourceProvider(it) }
                    ),
                    sources = MutableSourceProvider(
                        (minecraftNode.libraries.map { it.archive } + minecraftNode.archive)
                            .mapTo(ArrayList()) { ArchiveSourceProvider(it) }
                    ),
                    parent = ClassLoader.getPlatformClassLoader(),
                )
            )
        }
}
