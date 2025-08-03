package com.android.ndkports

import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ModuleProperty @Inject constructor(
    objectFactory: ObjectFactory,
    @get:Input val name: String,
) {
    @Suppress("UnstableApiUsage")
    @get:Input
    val static: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    @Suppress("UnstableApiUsage")
    @get:Input
    val headerOnly: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    @Suppress("UnstableApiUsage")
    @get:Input
    val includesPerAbi: Property<Boolean> =
        objectFactory.property(Boolean::class.java).convention(false)

    @Suppress("UnstableApiUsage")
    @get:Input
    val dependencies: ListProperty<String> =
        objectFactory.listProperty(String::class.java).convention(emptyList())
}

abstract class PackageBuilderTask @Inject constructor(
    objectFactory: ObjectFactory,
) : DefaultTask() {
    /**
     * The name of the port. Will be used as the package name in prefab.json.
     */
    @Suppress("UnstableApiUsage")
    @get:Input
    val packageName: Property<String> =
        objectFactory.property(String::class.java).convention(project.name)

    /**
     * The version to encode in the prefab.json.
     *
     * This version must be compatible with CMake's `find_package` for
     * config-style packages. This means that it must be one to four decimal
     * separated integers. No other format is allowed.
     *
     * If not set, the default is [Project.getVersion] as interpreted by
     * [CMakeCompatibleVersion.parse].
     *
     * For example, OpenSSL 1.1.1g will set this value to
     * `CMakeCompatibleVersion(1, 1, 1, 7)`.
     */
    @get:Input
    abstract val version: Property<CMakeCompatibleVersion>

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Nested
    abstract val modules: NamedDomainObjectContainer<ModuleProperty>

    @Suppress("UnstableApiUsage")
    @get:Input
    val licensePath: Property<String> =
        objectFactory.property(String::class.java).convention("LICENSE")

    @Suppress("UnstableApiUsage")
    @get:Input
    abstract val dependencies: MapProperty<String, String>

    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val installDirectory: DirectoryProperty

    @get:Internal
    abstract val outDir: DirectoryProperty

    @get:OutputDirectory
    val intermediatesDirectory: Provider<Directory>
        get() = outDir.dir("aar")

    @get:InputDirectory
    abstract val ndkPath: DirectoryProperty

    private val ndk: Ndk
        get() = Ndk(ndkPath.asFile.get())

    @TaskAction
    fun run() {
        val modules = modules.asMap.values.map {
            ModuleDescription(
                it.name,
                it.static.get(),
                it.headerOnly.get(),
                it.includesPerAbi.get(),
                it.dependencies.get()
            )
        }
        PrefabPackageBuilder(
            PackageData(
                packageName.get(),
                project.version as String,
                version.get(),
                minSdkVersion.get(),
                licensePath.get(),
                modules,
                dependencies.get(),
            ),
            intermediatesDirectory.get().asFile,
            installDirectory.get().asFile,
            sourceDirectory.get().asFile,
            ndk,
        ).build()
    }
}