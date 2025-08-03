import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import javax.inject.Inject

abstract class GenerateJavaAidlDependencies
@Inject constructor() : AbstractExecTask<GenerateJavaAidlDependencies>(GenerateJavaAidlDependencies::class.java) {

    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty

    @get:Input
    abstract var androidBuildTop: String

    @get:InputFiles
    abstract val aidlSrcDirs: ConfigurableFileCollection

    @get:InputFiles
    abstract val aidlIncludeDirs: ConfigurableFileCollection

    override fun exec() {
        val platform = if (Os.isFamily(Os.FAMILY_MAC)) "darwin" else "linux"
        commandLine("$androidBuildTop/prebuilts/build-tools/${platform}-x86/bin/aidl")
        args("--lang=java", "--stability=vintf", "-v", "1", "--hash=1", "--structured")

        aidlIncludeDirs.files.forEach { includeDir ->
            args("-I", includeDir)
        }
        args("--out", outputFolder.get())

        // Recursively list all the aidl files in the src directories
        aidlSrcDirs.files.forEach { srcDir ->
            args(project.fileTree(srcDir) {
                include("**/*.aidl")
            }.files)
        }

        CommandLineUtils.debugPrintCommandLineArgs(this)
        super.exec()
    }
}
