import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.mavenLocal
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.resources.openStream
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.extframework.common.util.readInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.util.HexFormat

abstract class GenerateProfileTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val configuration: Property<String>

    @TaskAction
    fun generate() {
        val allArtifacts =
            project.configurations
                .asSequence()
                .filter { it.isCanBeResolved }
                .map { it.resolvedConfiguration }
                .flatMap { it.firstLevelModuleDependencies }
                .map { it.name }
                .toSet()
                .map {
                    SimpleMavenDescriptor.parseDescription(it)!!
                }

        val repositories = project.repositories
            .filterIsInstance<MavenArtifactRepository>()
            .map {
                if (it is DefaultMavenLocalArtifactRepository) {
                    SimpleMavenRepositorySettings.local(path = it.url.path)
                } else SimpleMavenRepositorySettings.default(url = it.url.toString(), requireResourceVerification = false)
            }

        launch {
            val libraries = allArtifacts.associateWith {
                repositories.filter { settings ->
                     settings.layout.resourceOf(it.group, it.artifact, it.version, null, "jar")().isSuccess
                }.map { settings ->
                    fun readChecksum(
                        type: String,
                    ) = settings.layout
                        .resourceOf(it.group, it.artifact, it.version, null, "jar.$type")()
                        .getOrNull()
                        ?.openStream()
                        ?.readInputStream()
                        ?.let(::String)

                    LibraryChecksums(
                        readChecksum("sha1"),
                        readChecksum("sha256"),
                        readChecksum("sha512"),
                        readChecksum("md5"),
                    ) to settings
                }.firstOrNull() ?: throw IllegalStateException("Couldn't find descriptor: '$it' in repositories")
            }.map { (descriptor, pair) ->
                Library(
                    pair.first.sha1,
                    pair.first.sha256,
                    pair.first.sha512,
                    pair.first.md5,
                    descriptor.name,
                    (pair.second.layout as? SimpleMavenDefaultLayout)?.url ?: mavenLocal // FIXME
                )
            }

            val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            println(mapper.writeValueAsString(libraries))
        }
    }
}