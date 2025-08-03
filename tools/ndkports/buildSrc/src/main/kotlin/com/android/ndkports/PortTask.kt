package com.android.ndkports

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

@Suppress("UnstableApiUsage")
abstract class PortTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val buildDir: DirectoryProperty

    @get:OutputDirectory
    val installDir: Provider<Directory>
        get() = buildDir.dir("install")

    @get:InputDirectory
    abstract val prefabGenerated: DirectoryProperty

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:InputDirectory
    abstract val ndkPath: DirectoryProperty

    private val ndk: Ndk
        get() = Ndk(ndkPath.asFile.get())

    /**
     * The number of CPUs available for building.
     *
     * May be passed to the build system if required.
     */
    @Internal
    protected val ncpus = Runtime.getRuntime().availableProcessors()

    protected fun executeSubprocess(
        args: List<String>,
        workingDirectory: File,
        additionalEnvironment: Map<String, String>? = null
    ) {
        val pb = ProcessBuilder(args).redirectErrorStream(true)
            .directory(workingDirectory)

        if (additionalEnvironment != null) {
            pb.environment().putAll(additionalEnvironment)
        }

        val result = pb.start()
        val output = result.inputStream.bufferedReader().use { it.readText() }
        if (result.waitFor() != 0) {
            throw RuntimeException("Subprocess failed with:\n$output")
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun buildDirectoryFor(abi: Abi): File =
        buildDir.asFile.get().resolve("build/$abi")

    @Suppress("MemberVisibilityCanBePrivate")
    fun installDirectoryFor(abi: Abi): File =
        installDir.get().asFile.resolve("$abi")

    @TaskAction
    fun run() {
        for (abi in Abi.values()) {
            val api = abi.adjustMinSdkVersion(minSdkVersion.get())
            buildForAbi(
                Toolchain(ndk, abi, api),
                buildDir.asFile.get(),
                buildDirectory = buildDirectoryFor(abi),
                installDirectory = installDirectoryFor(abi),
            )
        }
    }

    abstract fun buildForAbi(
        toolchain: Toolchain,
        workingDirectory: File,
        buildDirectory: File,
        installDirectory: File
    )
}