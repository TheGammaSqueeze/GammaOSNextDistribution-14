import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

/**
 * Filters resources to the given product type.
 */
abstract class FilterResourcesTask extends DefaultTask {
    @Incremental
    @InputDirectory
    abstract DirectoryProperty getInputDir()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    @Input
    String productType

    @TaskAction
    void execute(InputChanges inputChanges) {
        def inputPath = inputDir.get().asFile
        inputChanges.getFileChanges(inputDir).each { change ->

            File changedFile = change.file;

            def relative = inputPath.relativePath(changedFile)
            File targetFile = outputDir.file(relative).get().asFile
            if (!changedFile.exists()) {
                targetFile.delete()
                return
            }
            targetFile.parentFile.mkdirs()

            if (changedFile.name.endsWith(".xml")) {
                String match1 = "product="
                String match2 = match1 + '"' + productType + '"'
                String match3 = match1 + "'" + productType + "'"
                Pattern match4 = Pattern.compile(/<\/\w+>/);
                StringBuilder filteredText = new StringBuilder();
                boolean bulkDelete = false;

                changedFile.eachLine { line ->
                    if (bulkDelete) {
                        bulkDelete = !line.find(match4);
                    } else if (!line.contains(match1)
                            || line.contains(match2)
                            || line.contains(match3)) {
                        filteredText.append(line).append('\n')
                    } else {
                        bulkDelete = !line.find(match4);
                    }
                }
                targetFile.text = filteredText.toString();
            } else {
                Files.copy(changedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}