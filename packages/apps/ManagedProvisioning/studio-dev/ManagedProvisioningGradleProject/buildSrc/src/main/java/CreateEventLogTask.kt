import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import java.io.File
import javax.inject.Inject

/**
 * Runs java-event-log-tags.py to generate a java class containing constants for each of the event
 * log tags in the given input file.
 */
abstract class CreateEventLogTask
@Inject constructor() : AbstractExecTask<CreateEventLogTask>(CreateEventLogTask::class.java) {

    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty

    @get:Input
    abstract var androidBuildTop: String

    @get:Input
    abstract var outputFileName: String

    @get:InputFile
    abstract var logtagsFile: RegularFile

    override fun exec() {
        workingDir = File("$androidBuildTop/build/make/tools")

        val outputFile = File(outputFolder.get().asFile, "$outputFileName")

        val platform = if (Os.isFamily(Os.FAMILY_MAC)) "darwin" else "linux"
        commandLine(
                "$androidBuildTop/prebuilts/build-tools/path/$platform-x86/python3",
                "java-event-log-tags.py",
                "-o", outputFile, logtagsFile
        )
        println("commandLine = $commandLine")
        super.exec()
        println("Tags file created at $outputFile")
    }
}
