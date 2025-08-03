import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile

abstract class AConfigCreateCacheTask :
        AbstractExecTask<AConfigCreateCacheTask>(AConfigCreateCacheTask::class.java) {

    @get:InputFile
    abstract val aconfigPath: RegularFileProperty

    @get:Input
    abstract var packageName: String

    @get:Input
    abstract var containerName: String

    @get:InputFiles
    abstract val srcFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun exec() {
        commandLine(aconfigPath.get())
        args("create-cache", "--package", packageName, "--container", containerName)

        srcFiles.files.forEach { aconfigFile ->
            args("--declarations", aconfigFile)
        }
        args("--cache", "${outputFile.get()}")
        CommandLineUtils.debugPrintCommandLineArgs(this)
        super.exec()
    }
}
