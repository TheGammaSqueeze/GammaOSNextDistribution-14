package com.android.ndkports

import com.google.prefab.api.Android
import com.google.prefab.api.BuildSystemInterface
import com.google.prefab.api.Package
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

abstract class PrefabTask : DefaultTask() {
    @InputFiles
    lateinit var aars: FileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    val generatedDirectory: Provider<Directory>
        get() = outputDirectory.dir("generated")

    @get:Optional
    @get:Input
    abstract val generator: Property<Class<out BuildSystemInterface>>

    @get:InputDirectory
    abstract val ndkPath: DirectoryProperty

    @get:Input
    abstract val minSdkVersion: Property<Int>

    private val ndk: Ndk
        get() = Ndk(ndkPath.asFile.get())

    @TaskAction
    fun run() {
        if (!generator.isPresent) {
            // Creating the generated directory even if we have no generator
            // makes it easier to write tasks that *might* consume prefab
            // packages.
            generatedDirectory.get().asFile.mkdirs()
            return
        }

        val outDir = outputDirectory.get().asFile
        val packages = mutableListOf<Package>()
        for (aar in aars) {
            val packagePath = outDir.resolve(aar.nameWithoutExtension)
            extract(aar, packagePath)
            packages.add(Package(packagePath.toPath().resolve("prefab")))
        }
        generateSysroot(packages, minSdkVersion.get(), ndk.version.major)
    }

    private fun extract(aar: File, extractDir: File) {
        ZipFile(aar).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val outFile = extractDir.resolve(entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun generateSysroot(
        packages: List<Package>, osVersion: Int, ndkVersion: Int
    ) {
        val generatorType = generator.get()
        val constructor =
            generatorType.getConstructor(File::class.java, List::class.java)
        val buildSystemIntegration =
            constructor.newInstance(generatedDirectory.get().asFile, packages)

        buildSystemIntegration.generate(Android.Abi.values().map {
            Android(it, osVersion, Android.Stl.CxxShared, ndkVersion)
        })
    }
}