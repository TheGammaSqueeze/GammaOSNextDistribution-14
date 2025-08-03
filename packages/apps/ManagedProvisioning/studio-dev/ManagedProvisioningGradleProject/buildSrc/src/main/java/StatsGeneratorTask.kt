import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class StatsGeneratorTask : DefaultTask() {

    @get:InputFile
    abstract val atomsFile: RegularFileProperty

    @get:InputFiles
    abstract val atomsExtensions: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty

    @get:Input
    abstract var javaFileName: String

    @get:Input
    abstract var androidTop: String

    @get:Input
    abstract var module: String

    @get:Input
    abstract var packageName: String

    @TaskAction
    fun taskAction() {
        val generator = StatsGenerator(File(androidTop))
        generator.process(atomsFile.get().asFile, atomsExtensions.files,
                module, packageName, File(outputFolder.get().asFile, javaFileName))
    }
}
