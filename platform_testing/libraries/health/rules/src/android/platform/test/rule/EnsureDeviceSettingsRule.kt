package android.platform.test.rule

import android.os.SystemProperties
import android.platform.uiautomator_helpers.DeviceHelpers.shell
import android.provider.Settings
import org.junit.runner.Description

/**
 * Makes sure settings that are required to run tests are present and in the correct states.
 *
 * Suggests commands to fix them.
 */
class EnsureDeviceSettingsRule : TestWatcher() {

    private val setupErrors = mutableListOf<SetupError>()
    override fun starting(description: Description?) {
        checkAdbRootEnabled()
        checkTestHarnessEnabled()
        checkStayAwakeEnabled()
        if (setupErrors.isNotEmpty()) throwSetupErrors()
    }

    private fun checkAdbRootEnabled() {
        val adbIdResult = uiDevice.shell("id -u").trim()

        if (adbIdResult != "0") {
            setupErrors.add(
                SetupError(
                    description = "ADB root access is required but disabled.",
                    adbCommandToFixIt = "adb root"
                )
            )
        }
    }

    private fun checkTestHarnessEnabled() {
        val mobileHarnessModeEnabled = SystemProperties.getBoolean(TEST_HARNESS_PROP, false)
        if (!mobileHarnessModeEnabled) {
            setupErrors.add(
                SetupError(
                    description = "Test harness' mode is required but disabled.",
                    adbCommandToFixIt =
                        "adb shell setprop $TEST_HARNESS_PROP 1; " +
                            "adb shell am force-stop $LAUNCHER_PACKAGE",
                )
            )
        }
    }

    /**
     * Setting value of "Stay awake" is bit-based with 4 bits responsible for different types of
     * charging. So the value is device-dependent but non-zero value means the settings is on. See
     * [Settings.Global.STAY_ON_WHILE_PLUGGED_IN] for more information.
     */
    private fun checkStayAwakeEnabled() {
        val stayAwakeResult =
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN
            )
        if (stayAwakeResult == 0) {
            setupErrors.add(
                SetupError(
                    description = "'Stay awake' option in developer settings should be enabled",
                    adbCommandToFixIt = "adb shell settings put global stay_on_while_plugged_in 7"
                )
            )
        }
    }

    private fun throwSetupErrors() {
        val message = setupErrors.map { it.description }.joinToString("\n")
        val command = setupErrors.map { it.adbCommandToFixIt }.joinToString("; \\\n")
        throw AssertionError(
            """

      ${"-".repeat(80)}
      SETUP ERROR:
      $message

      Run the following command to fix:

      ${command.prependIndent("   ")}
      ${"-".repeat(80)}
      """
                .trimIndent()
        )
    }

    private data class SetupError(
        val description: String,
        val adbCommandToFixIt: String,
    )

    private companion object {
        const val TEST_HARNESS_PROP = "ro.test_harness"
        const val LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"
    }
}
