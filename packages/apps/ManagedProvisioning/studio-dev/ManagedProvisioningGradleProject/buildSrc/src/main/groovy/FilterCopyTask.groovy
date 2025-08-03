import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Filters resources to the given product type.
 */
abstract class FilterCopyTask extends DefaultTask {
    @Incremental
    @InputDirectory
    abstract DirectoryProperty getInputDir()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Input
    abstract ListProperty<String> getIncludes()

    @TaskAction
    void execute(InputChanges inputChanges) {
        System.out.println("test copy task was called")
        def inputPath = inputDir.get().asFile
        inputChanges.getFileChanges(inputDir).each { change ->
            File changedFile = change.file
            def relative = inputPath.relativePath(changedFile)
            File targetFile = outputDir.file(relative).get().asFile
            if (!changedFile.exists() || ! includes.get().contains(relative)) {
                targetFile.delete()
                return
            }
            if (includes.get().contains(relative)) {
                System.out.println("checking file: " + relative)
                targetFile.parentFile.mkdirs()
                Files.copy(changedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}