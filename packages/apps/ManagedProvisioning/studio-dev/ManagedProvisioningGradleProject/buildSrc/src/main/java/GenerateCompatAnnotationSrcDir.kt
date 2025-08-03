import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import javax.inject.Inject

abstract class GenerateCompatAnnotationSrcDir
@Inject constructor() : AbstractExecTask<GenerateCompatAnnotationSrcDir>(GenerateCompatAnnotationSrcDir::class.java) {

    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty

    @get:Input
    abstract var symlinkTarget: String

    @get:Input
    abstract var linkName: String

    override fun exec() {
        commandLine("ln", "-sf", symlinkTarget, outputFolder.get())
        CommandLineUtils.debugPrintCommandLineArgs(this)
        super.exec()
    }
}
