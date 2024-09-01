package dev.extframework.client

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.zip.classLoaderToArchive
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.loader.*
import dev.extframework.common.util.readInputStream
import dev.extframework.internal.api.target.ApplicationDescriptor
import dev.extframework.internal.api.target.ApplicationTarget
import java.nio.ByteBuffer

internal class AppTarget(
    private val delegate: ClassLoader,
    version: String,
) : ApplicationTarget {
    private val transformers: MutableList<(ByteArray) -> ByteArray> = ArrayList()
    override val node: ClassLoadedArchiveNode<ApplicationDescriptor> = object : ClassLoadedArchiveNode<ApplicationDescriptor> {
        private val appDesc = ApplicationDescriptor(
            "net.minecraft",
            "minecraft",
            version,
            "client"
        )
        override val descriptor: ApplicationDescriptor = appDesc
        override val access: ArchiveAccessTree = object : ArchiveAccessTree {
            override val descriptor: ArtifactMetadata.Descriptor = appDesc
            override val targets: List<ArchiveTarget> = listOf()
        }
        override val handle: ArchiveHandle = classLoaderToArchive(MutableClassLoader(
            name = "minecraft-loader",
            sources = object : MutableSourceProvider(mutableListOf(
                object : SourceProvider {
                    override val packages: Set<String> = setOf("*")

                    override fun findSource(name: String): ByteBuffer? {
                        val stream = delegate.getResourceAsStream(name.replace(".", "/") + ".class") ?: return null

                        val bytes = stream.readInputStream()
                        val transformedBytes = transformers.fold(bytes) { acc, transformer -> transformer(acc) }
                        return ByteBuffer.wrap(transformedBytes)
                    }
                }
            )) {
                override fun findSource(name: String): ByteBuffer? =
                    ((packageMap[name.substring(0, name.lastIndexOf('.').let { if (it == -1) 0 else it })] ?: listOf()) +
                            (packageMap["*"] ?: listOf())).firstNotNullOfOrNull { it.findSource(name) }
            },
            parent = ClassLoader.getPlatformClassLoader(),
        ))
    }

//    override val mappingNamespace: String = "mojang:obfuscated"

//    override fun addTransformer(transform: (ByteArray) -> ByteArray) {
//        transformers.add(transform)
//    }
//
//    override fun reTransform(name: String) {
//        TODO("Re transformation not implemented yet.")
//    }
}