import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory

abstract class AConfigCreateJavaLibTask :
    AbstractExecTask<AConfigCreateJavaLibTask>(AConfigCreateJavaLibTask::class.java) {

    @get:InputFile
    abstract val aconfigPath: RegularFileProperty

    @get:InputFile
    abstract val cacheFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty

    override fun exec() {
        commandLine(aconfigPath.get())
        args(
            "create-java-lib",
            "--mode",
            "production",
            "--cache",
            cacheFile.get(),
            "--out",
            outputFolder.get()
        )
        CommandLineUtils.debugPrintCommandLineArgs(this)
        super.exec()
    }
}
