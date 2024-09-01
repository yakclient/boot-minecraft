package dev.extframework.client

import BootLoggerFactory
import com.durganmcbroom.jobs.launch
import com.github.ajalt.clikt.core.subcommands
import dev.extframework.boot.archive.*
import dev.extframework.common.util.resolve
import dev.extframework.components.extloader.environment.ExtraAuditorsAttribute
import dev.extframework.components.extloader.work
import dev.extframework.components.extloader.workflow.Workflow
import dev.extframework.components.extloader.workflow.WorkflowContext
import dev.extframework.internal.api.environment.ExtensionEnvironment
import java.nio.file.Path
import kotlin.system.exitProcess

internal fun getHomedir(): Path {
    return Path.of(System.getProperty("user.home")) resolve ".extframework"
}

public fun main(args: Array<String>) {
    val command = ProductionCommand()
    command.subcommands(DevCommand()).main(args)

    val launchContext = command.launchContext

    launch(BootLoggerFactory()) {
        val launchInfo: LaunchInfo<*> = launchContext.launchInfo.value

        work(
            launchContext.options.workingDir,
            launchContext.archiveGraph,
            launchContext.dependencyTypes,
            launchInfo.context,
            launchInfo.workflow as Workflow<WorkflowContext>,
            AppTarget(
                launchInfo.classloader,
                VERSION_REGEX.matchEntire(
                    launchContext.options.version
                )!!.groups["version"]!!.value
            ),
            ExtensionEnvironment().apply {
                plusAssign(ExtraAuditorsAttribute(launchContext.extraAuditors))
            }
        )().merge()

        val mainClass = launchInfo.classloader.loadClass(
            launchInfo.mainClass
        )

        mainClass.getMethod("main", Array<String>::class.java).invoke(null, launchInfo.args)
    }

    exitProcess(0)
}
