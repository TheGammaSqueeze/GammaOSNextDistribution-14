import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import javax.inject.Inject

abstract class PushApkTask
@Inject constructor() : AbstractExecTask<PushApkTask>(PushApkTask::class.java) {

    @get:InputFiles
    abstract val apkFolder: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    override fun exec() {
        val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
            ?: throw RuntimeException("Cannot load APKs")
        if (builtArtifacts.elements.isEmpty()) {
            throw RuntimeException("Build artifact not found. Can't install apk if it doesn't exist.")
        }
        val numArtifacts = builtArtifacts.elements.size
        if (numArtifacts > 1) {
            throw RuntimeException(
                "Too many build artifacts. Expected 1 apk file but received $numArtifacts. " +
                        "The push-apk.sh script only supports installing one apk file."
            )
        }
        // TODO(b/234033515): This does not yet account for ANDROID_ADB_SERVER_PORT
        val deviceSerials =
            project.providers.gradleProperty("internal.android.inject.device.serials")
        if (!deviceSerials.isPresent) {
            throw RuntimeException(
                "No Android serial present. Make sure your Android Studio VM options contains " +
                        "-Dgradle.ide.internal.build.injection.device.serial.number=true"
            )
        }
        builtArtifacts.elements.forEach {
            commandLine("sh", "push-apk.sh", it.outputFile, deviceSerials.get())
        }

        CommandLineUtils.debugPrintCommandLineArgs(this)
        super.exec()
    }
}
