import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileType
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

import java.nio.file.Files

/**
 * Create symbolic links of a collection of files in a set output directory.
 *
 * The files must all be rooted at androidTop. This will create a file tree at outputDirectory that
 * mimics the structure rooted at androidTop.
 */
abstract class SymbolicLinksTask : DefaultTask() {

    @get:Incremental
    @get:InputFiles
    abstract val inputDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract var outputDirectory: String

    @get:Input
    abstract var androidBuildTop: String

    init {
        group = "symlink"
    }

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        var androidTopFile = File(androidBuildTop).canonicalFile
        inputChanges.getFileChanges(inputDirectories).forEach { change ->
            var file = change.file
            if (change.fileType == FileType.DIRECTORY) {
                println("Creating link to ${file.path}")
                var relativePath = file.canonicalFile.relativeTo(androidTopFile)
                var symbolicLinkPath =
                        project.file("$outputDirectory/$relativePath").toPath()
                if (change.changeType == ChangeType.ADDED && !symbolicLinkPath.toFile().exists()) {
                    symbolicLinkPath.parent.toFile().mkdirs()
                    Files.createSymbolicLink(symbolicLinkPath, file.toPath())
                }
            } else {
                // We only need to create symbolic links to directories.
                // System.err.println("${file.path} is not a directory")
            }
        }
    }
}
