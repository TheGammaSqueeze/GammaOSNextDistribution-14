package com.android.ndkports

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class SourceExtractTask : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun run() {
        val pb = ProcessBuilder(
            listOf(
                "tar",
                "xf",
                source.get().asFile.absolutePath,
                "--strip-components=1"
            )
        ).redirectErrorStream(true).directory(outDir.get().asFile)

        val result = pb.start()
        val output = result.inputStream.bufferedReader().use { it.readText() }
        if (result.waitFor() != 0) {
            throw RuntimeException("Subprocess failed with:\n$output")
        }
    }
}