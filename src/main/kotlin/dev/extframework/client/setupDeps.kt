@file:JvmName("SetupDeps")

package dev.extframework.client

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider

internal fun setupDependencyTypes(
    archiveGraph: ArchiveGraph,
    auditors: Auditors,
): DependencyTypeContainer {
    val maven = object : MavenDependencyResolver(
        parentClassLoader = ClassLoader.getSystemClassLoader(),
    ) {
        override val auditors: Auditors
            get() = auditors
    }

    val dependencyTypes = DependencyTypeContainer(archiveGraph)
    dependencyTypes.register("simple-maven", MavenResolverProvider(maven))

    return dependencyTypes
}