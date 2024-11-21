package dev.extframework.client

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.FailingJob
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.*
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.audit.chain
import dev.extframework.boot.constraint.ConstraintArchiveAuditor
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenConstraintNegotiator
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.monad.removeIf
import dev.extframework.boot.monad.tag
import dev.extframework.boot.util.typeOf
import dev.extframework.common.util.readInputStream
import java.nio.file.Path
import kotlin.io.path.Path


internal fun setupExtraAuditors(
    archiveGraph: ArchiveGraph,
    packagedDependencies: Set<SimpleMavenDescriptor>
): Auditors {
    val negotiator = MavenConstraintNegotiator()

    val alreadyLoaded = packagedDependencies.mapTo(HashSet()) {
        negotiator.classify(it)
    }

    val packagedDependencyRemover = object : ArchiveTreeAuditor {
        override fun audit(event: ArchiveTreeAuditContext): Job<ArchiveTreeAuditContext> = job {
           event.copy(tree = event.tree.removeIf {
                alreadyLoaded.contains(negotiator.classify(it.value.descriptor as? SimpleMavenDescriptor ?: return@removeIf false))
            }!!)
        }
    }
    val archiveTreeAuditor = ConstraintArchiveAuditor(
        listOf(MavenConstraintNegotiator()),
    ).chain(packagedDependencyRemover)

    return Auditors(
        archiveTreeAuditor
    )
}

private class THIS

internal fun parsePackagedDependencies(): Set<SimpleMavenDescriptor> {
    val dependencies: java.util.HashSet<SimpleMavenDescriptor> =
        THIS::class.java.getResourceAsStream("/dependencies.txt")?.use {
            val fileStr = String(it.readInputStream())
            fileStr.split("\n").toSet()
        }?.filterNot { it.isBlank() }?.mapTo(HashSet()) { SimpleMavenDescriptor.parseDescription(it)!! }
            ?: throw IllegalStateException("Cant load dependencies?")

    return dependencies
}

internal fun setupArchiveGraph(
    path: Path,
    packagedDependencies: Set<SimpleMavenDescriptor>,
): ArchiveGraph {
    val primordial = PrimordialNodeResolver()

    val archiveGraph = DefaultArchiveGraph(
        path,
//        packagedDependencies.associateWithTo(HashMap()) {
//            BasicDependencyNode(it, null,
//                object : ArchiveAccessTree {
//                    override val descriptor: ArtifactMetadata.Descriptor = it
//                    override val targets: List<ArchiveTarget> = listOf()
//                }
//            ).tag(
//                primordial
//            )
//        }
    )

    archiveGraph.registerResolver(primordial)

    return archiveGraph
}

internal class PrimordialNodeResolver :
    ArchiveNodeResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, BasicDependencyNode<ArtifactMetadata.Descriptor>, RepositorySettings, ArtifactMetadata<ArtifactMetadata.Descriptor, *>> {
    override val metadataType: Class<ArtifactMetadata<ArtifactMetadata.Descriptor, *>> = typeOf()
    override val name: String = "primordial"
    override val nodeType: Class<in BasicDependencyNode<ArtifactMetadata.Descriptor>> = typeOf()

    override fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace
    ): Result<ArtifactMetadata.Descriptor> {
        return Result.failure(ArchiveException(trace, "Operation not supported"))
    }

    override fun serializeDescriptor(descriptor: ArtifactMetadata.Descriptor): Map<String, String> {
        return mapOf()
    }

    override fun pathForDescriptor(
        descriptor: ArtifactMetadata.Descriptor,
        classifier: String,
        type: String
    ): Path {
        return Path("")
    }

    override fun load(
        data: ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>,
        accessTree: ArchiveAccessTree,
        helper: ResolutionHelper
    ): Job<BasicDependencyNode<ArtifactMetadata.Descriptor>> {
        return FailingJob { ArchiveException(helper.trace, "Operation not supported") }
    }

    override fun createContext(settings: RepositorySettings): ResolutionContext<RepositorySettings, ArtifactRequest<ArtifactMetadata.Descriptor>, ArtifactMetadata<ArtifactMetadata.Descriptor, *>> {
        throw UnsupportedOperationException()
    }

    override fun cache(
        artifact: Artifact<ArtifactMetadata<ArtifactMetadata.Descriptor, *>>,
        helper: CacheHelper<ArtifactMetadata.Descriptor>
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        throw ArchiveException(helper.trace, "Operation not supported")
    }
}


