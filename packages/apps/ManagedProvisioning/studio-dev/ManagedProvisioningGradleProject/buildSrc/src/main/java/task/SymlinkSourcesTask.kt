package task

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import kotlin.io.path.Path

@Suppress("LeakingThis")
internal abstract class SymlinkSourcesTask : DefaultTask() {
    @get:Input
    abstract val sourcesRoot: Property<String>

    @get:InputFiles
    @get:Incremental
    protected abstract val files: ConfigurableFileTree

    fun files(filter: Action<PatternFilterable>) {
        filter.execute(files)
    }

    @get:OutputDirectory
    abstract val destinationDir: DirectoryProperty

    @get:Inject
    protected abstract val layout: ProjectLayout

    init {
        group = "symlink"
        files.from(sourcesRoot)
    }

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        val sourcesRootDir = File(sourcesRoot.get())
        inputChanges.getFileChanges(files).forEach { change ->
            val file = change.file
            val relativePath = file.canonicalFile.relativeTo(sourcesRootDir)
            val symbolicLinkPath = Path("${destinationDir.get()}/$relativePath")
            val symbolicLinkFile = symbolicLinkPath.toFile()
            if (file !in files && file.absolutePath != sourcesRootDir.absolutePath) {
                logger.info("Cleaning up filtered out symlink {}", symbolicLinkPath)
                symbolicLinkFile.deleteRecursively()
            } else if (change.changeType == ChangeType.REMOVED) {
                logger.info("Removing symbolic link at {}", symbolicLinkPath)
                symbolicLinkFile.deleteRecursively()
            } else if (change.fileType == FileType.FILE && change.changeType == ChangeType.ADDED && !symbolicLinkFile.exists()) {
                logger.info("Creating symbolic link to {}", file.path)
                symbolicLinkFile.parentFile.mkdirs()
                Files.createSymbolicLink(symbolicLinkPath, file.toPath())
            }
        }
    }
}