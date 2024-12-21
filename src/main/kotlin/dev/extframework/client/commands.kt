package dev.extframework.client

//import dev.extframework.internal.api.extension.artifact.ExtensionDescriptor
//import dev.extframework.internal.api.extension.artifact.ExtensionRepositorySettings
import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.launch
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ClassLoadedArchiveNode
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenLikeResolver
import dev.extframework.common.util.immutableLateInit
import dev.extframework.common.util.resolve
import dev.extframework.minecraft.bootstrapper.MinecraftNode
import dev.extframework.minecraft.bootstrapper.MinecraftProviderFinder
import dev.extframework.minecraft.bootstrapper.MinecraftProviderRemoteLookup
import dev.extframework.minecraft.bootstrapper.loadMinecraft
import dev.extframework.tooling.api.extension.artifact.ExtensionDescriptor
import dev.extframework.tooling.api.extension.artifact.ExtensionRepositorySettings
import dev.extframework.tooling.api.target.ApplicationTarget
import java.nio.file.Path
import kotlin.io.path.Path

internal class LaunchInfo(
    val args: Array<String>,
    val mainClass: String,
//    val minecraftNode: MinecraftNode,

    val requests: Map<ExtensionDescriptor, ExtensionRepositorySettings>,
    val app: ApplicationTarget,
    val extensionDirPath: Path,

    val mappingNS: String
)

internal class GameOptions(
    val workingDir: Path,
    val username: String?,
    val version: String,
    val gameDir: String,
    val assetsDir: String?,
    val assetIndex: String?,
    val uuid: String?,
    val accessToken: String = "",
    val clientId: String?,
    val xuid: String?,
    val userType: String?,
    val versionType: String?,
    val quickPlayPath: String?
) {
    override fun toString() = listOfNotNull(
        "workingDir=$workingDir",
        username?.let { "username=$it" },
        "version=$version",
        gameDir.let { "gameDir=$it" },
        assetsDir?.let { "assetsDir=$it" },
        assetIndex?.let { "assetIndex=$it" },
        uuid?.let { "uuid=$it" },
        if (accessToken.isNotEmpty()) "accessToken=$accessToken" else null,
        clientId?.let { "clientId=$it" },
        xuid?.let { "xuid=$it" },
        userType?.let { "userType=$it" },
        versionType?.let { "versionType=$it" },
        quickPlayPath?.let { "quickPlayPath=$it" }
    ).joinToString(", ", "GameOptions[", "]")
}

internal data class LaunchContext(
    val launchInfo: LaunchInfo,
    val options: GameOptions,
    val archiveGraph: ArchiveGraph,
    val extraAuditors: Auditors,
    val dependencyTypes: DependencyTypeContainer,
)

internal val VERSION_REGEX = Regex("extframework-(?<version>[0-9a-zA-Z.]+)")

internal class ProductionCommand(
    val minecraftDir: Path,
    val extframeworkDir: Path
) : CliktCommand(
    invokeWithoutSubcommand = true
) {
    val username by option()
    val version by option()
        .required()

    val gameDir by option("--gameDir")
    val assetsDir by option("--assetsDir")
    val assetIndex by option("--assetIndex")
    val uuid by option("--uuid")
    val accessToken by option("--accessToken").default("")
    val clientId by option("--clientId")
    val xuid by option("--xuid")
    val userType by option("--userType")
    val versionType by option("--versionType")
    val quickPlayPath by option("--quickPlayPath")

    val extensions by option("--extension", "-e")
        .extensionDescriptor()
        .multiple()
    val repositories by option("--repository", "-r")
        .repository()
        .multiple()

    val mcProviderRepository by option()
        .repository()
        .default(SimpleMavenRepositorySettings.default(url = "https://maven.extframework.dev/snapshots"))
    val forceProvider by option()
        .mavenDescriptor()
    val mappingNamespace by option()
        .default("mojang:obfuscated")

    val extensionDir by option()
        .default((extframeworkDir resolve "extensions").toString())

    val mainClass by option()
    val classpath by option()

    var launchContext: LaunchContext by immutableLateInit()

    override fun run() {
        launch(BootLoggerFactory()) {
            val packagedDependencies = parsePackagedDependencies()
            val archiveGraph = setupArchiveGraph(extframeworkDir resolve "archives", packagedDependencies)
            val extraAuditors: Auditors = setupExtraAuditors(archiveGraph, packagedDependencies)
            val dependencyTypes = setupDependencyTypes(archiveGraph, extraAuditors)

            val appInfo = createApp(archiveGraph, dependencyTypes)().merge()

            val info = LaunchInfo(
                arrayOfNonNulls(
                    "--username" to username,
                    "--version" to version,
                    "--gameDir" to appInfo.gameDir.toString(),
                    "--assetsDir" to (appInfo.assetsDir.toString()),
                    "--assetIndex" to (appInfo.assetIndex.toString()),
                    "--uuid" to uuid,
                    "--accessToken" to accessToken,
                    "--clientId" to clientId,
                    "--xuid" to xuid,
                    "--userType" to userType,
                    "--versionType" to versionType,
                    "--quickPlayPath" to quickPlayPath,
                ),
                appInfo.mainClass,
                extensions.zip(repositories).toMap(),
                appInfo.app,
                Path(extensionDir),
                mappingNamespace,
            )

            val context = LaunchContext(
                info,
                GameOptions(
                    minecraftDir,
                    username,
                    version,
                    appInfo.gameDir.toString(),
                    appInfo.assetsDir.toString(),
                    appInfo.assetIndex,
                    uuid,
                    accessToken,
                    clientId,
                    xuid,
                    userType,
                    versionType,
                    quickPlayPath
                ),
                archiveGraph,
                extraAuditors,
                dependencyTypes
            )

            launchContext = context
        }

        echo("Launch context built: ${launchContext.options}")
    }

    private data class RequiredGameInfo(
        val app: ApplicationTarget,
        val mainClass: String,
        val gameDir: Path,
        val assetsDir: Path,
        val assetIndex: String,
    )

    private fun createApp(
        archiveGraph: ArchiveGraph,
        dependencyTypes: DependencyTypeContainer,
    ): Job<RequiredGameInfo> = job {
        val cp = classpath

        if (cp == null) {
            val handle: MinecraftNode = loadMinecraft(
                VERSION_REGEX.matchEntire(version)!!.groups["version"]!!.value,
                mcProviderRepository,
                minecraftDir,
                extframeworkDir,
                archiveGraph,
                dependencyTypes.get("simple-maven")!!.resolver as MavenLikeResolver<ClassLoadedArchiveNode<SimpleMavenDescriptor>, *>,
                forceProvider
            )().merge()

            val gameDir = gameDir?.let(::Path) ?: handle.runtimeInfo.gameDir
            RequiredGameInfo(
                MCNodeApp(handle, gameDir),
                handle.runtimeInfo.mainClass,
                gameDir,
                assetsDir?.let(::Path) ?: handle.runtimeInfo.assetsPath,
                assetIndex ?: handle.runtimeInfo.assetsName
            )
        } else {
            fun <T> T?.require(
                arg: String
            ) : T {
                return this ?: throw IllegalArgumentException("Option $arg must be set!")
            }

            val gameDir = Path(gameDir.require("--gameDir"))
            RequiredGameInfo(
                ClasspathApp(cp.split(":").map(::Path), version, gameDir),
                mainClass.require("--main-class"),
                gameDir,
                Path(assetsDir.require("--assetsDir")),
                assetIndex.require("--assetIndex"),
            )
        }
    }
}


private fun loadMinecraft(
    version: String,
    mcProviderRepo: SimpleMavenRepositorySettings,
    mcPath: Path,
    extframework: Path,
    archiveGraph: ArchiveGraph,
    mavenResolver: MavenLikeResolver<ClassLoadedArchiveNode<SimpleMavenDescriptor>, *>,
    forceProvider: SimpleMavenDescriptor?,
) = job {
    loadMinecraft(
        version,
        mcProviderRepo,
        mcPath,
        archiveGraph,
        mavenResolver,
        if (forceProvider == null) MinecraftProviderRemoteLookup(extframework resolve "minecraft")
        else object : MinecraftProviderFinder {
            override fun find(version: String): SimpleMavenDescriptor {
                return forceProvider
            }
        },
    )().merge()
}

private fun arrayOfNonNulls(
    vararg args: Pair<String, String?>,
): Array<String> = args.filter { it.second != null }.flatMap {
    listOf(it.first, it.second!!)
}.toTypedArray()

private fun NullableOption<String, String>.mavenDescriptor(): NullableOption<SimpleMavenDescriptor, SimpleMavenDescriptor> =
    convert {
        SimpleMavenDescriptor.parseDescription(it) ?: throw BadParameterValue(
            "Incorrectly formatted descriptor, expected a colon seperated 'group:artifact:version', got '$it'",
            this@mavenDescriptor
        )
    }

private fun NullableOption<String, String>.extensionDescriptor(): NullableOption<ExtensionDescriptor, ExtensionDescriptor> =
    convert {
        runCatching { ExtensionDescriptor.parseDescriptor(it) }.getOrNull() ?: throw BadParameterValue(
            "Incorrectly formatted descriptor, expected a colon seperated 'group:artifact:version', got '$it'",
            this@extensionDescriptor
        )
    }

private fun NullableOption<String, String>.repository(): NullableOption<SimpleMavenRepositorySettings, SimpleMavenRepositorySettings> =
    convert { repo ->
        repo.split("@")
            .takeIf { it.size == 2 }
            ?.let { (type, path) ->
                when (type) {
                    "local" -> SimpleMavenRepositorySettings.local(path = path)
                    "default" -> SimpleMavenRepositorySettings.default(url = path)
                    else -> throw BadParameterValue("Repository type '$type' is not supported.", this@repository)
                }
            }
            ?: throw BadParameterValue(
                "Incorrectly formatted maven repository given for CLI arg: 'mcProviderRepository'. Format is : '<local/default>@<URL>'",
                this@repository
            )
    }