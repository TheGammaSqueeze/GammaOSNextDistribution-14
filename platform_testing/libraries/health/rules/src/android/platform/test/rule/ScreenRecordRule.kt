/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.platform.test.rule

import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.platform.test.rule.ScreenRecordRule.Companion.SCREEN_RECORDING_CLASS_LEVEL_OVERRIDE_KEY
import android.platform.test.rule.ScreenRecordRule.Companion.SCREEN_RECORDING_TEST_LEVEL_OVERRIDE_KEY
import android.platform.test.rule.ScreenRecordRule.ScreenRecord
import android.platform.uiautomator_helpers.DeviceHelpers.shell
import android.platform.uiautomator_helpers.FailedEnsureException
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.platform.uiautomator_helpers.WaitUtils.waitFor
import android.platform.uiautomator_helpers.WaitUtils.waitForValueToSettle
import android.util.Log
import androidx.test.InstrumentationRegistry.getInstrumentation
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.nio.file.Files
import java.time.Duration
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Rule which captures a screen record for a test.
 *
 * After adding this rule to the test class either:
 * - apply the annotation [ScreenRecord] to individual tests or classes
 * - pass the [SCREEN_RECORDING_TEST_LEVEL_OVERRIDE_KEY] or
 *   [SCREEN_RECORDING_CLASS_LEVEL_OVERRIDE_KEY] instrumentation argument. e.g. `adb shell am
 *   instrument -w -e <key> true <test>`).
 *
 * Important note: After the file is created, in order to see it in artifacts, it needs to be pulled
 * from the device. Typically, this is done using [FilePullerLogCollector] at module level, changing
 * AndroidTest.xml.
 *
 * Note that when this rule is set as:
 * - `@ClassRule`, it will check only if the class has the [ScreenRecord] annotation, and will
 *   record one video for the entire test class
 * - `@Rule`, it will check each single test method, and record one video for each test annotated.
 *   If the class is annotated, then it will record a separate video for every test, regardless of
 *   if the test is annotated.
 *
 * @param keepTestLevelRecordingOnSuccess: Keep a recording of a single test, if the test passes. If
 *   false, the recording will be deleted. Does not apply to whole-class recordings
 * @param waitExtraAfterEnd: Sometimes, recordings are cut off by ~3 seconds (b/266186795). If true,
 *   then all recordings will wait 3 seconds after the test ends before stopping recording
 */
class ScreenRecordRule
@JvmOverloads
constructor(
    private val keepTestLevelRecordingOnSuccess: Boolean = true,
    private val waitExtraAfterEnd: Boolean = true,
) : TestRule {

    private val automation: UiAutomation = getInstrumentation().uiAutomation

    override fun apply(base: Statement, description: Description): Statement {
        if (!shouldRecordScreen(description)) {
            log("Not recording the screen.")
            return base
        }
        return object : Statement() {
            override fun evaluate() {
                runWithRecording(description) { base.evaluate() }
            }
        }
    }

    private fun shouldRecordScreen(description: Description): Boolean {
        return if (description.isTest) {
            val screenRecordBinaryAvailable = File("/system/bin/screenrecord").exists()
            log("screenRecordBinaryAvailable: $screenRecordBinaryAvailable")
            screenRecordBinaryAvailable &&
                (description.getAnnotation(ScreenRecord::class.java) != null ||
                    description.testClass.hasAnnotation(ScreenRecord::class.java) ||
                    testLevelOverrideEnabled())
        } else { // class level annotation is set
            description.testClass.hasAnnotation(ScreenRecord::class.java) ||
                classLevelOverrideEnabled()
        }
    }

    private fun classLevelOverrideEnabled() =
        screenRecordOverrideEnabled(SCREEN_RECORDING_CLASS_LEVEL_OVERRIDE_KEY)
    private fun testLevelOverrideEnabled() =
        screenRecordOverrideEnabled(SCREEN_RECORDING_TEST_LEVEL_OVERRIDE_KEY)
    /**
     * This is needed to enable screen recording when a parameter is passed to the instrumentation,
     * avoid having to recompile the test.
     */
    private fun screenRecordOverrideEnabled(key: String): Boolean {
        val args = InstrumentationRegistry.getArguments()
        val override = args.getString(key, "false").toBoolean()
        if (override) {
            log("Screen recording enabled due to $key param.")
        }
        return override
    }

    private fun runWithRecording(description: Description?, runnable: () -> Unit) {
        val outputFile = ArtifactSaver.artifactFile(description, "ScreenRecord", "mp4")
        log("Executing test with screen recording. Output file=$outputFile")

        if (screenRecordingInProgress()) {
            Log.w(
                TAG,
                "Multiple screen recording in progress (pids=\"$screenrecordPids\"). " +
                    "This might cause performance issues."
            )
        }
        // --bugreport adds the timestamp as overlay
        val screenRecordingFileDescriptor =
            automation.executeShellCommand("screenrecord --verbose --bugreport $outputFile")
        // Getting latest PID as there might be multiple screenrecording in progress.
        val screenRecordPid =
            waitFor("screenrecording pid") { screenrecordPids.maxOrNull() }
        var success = false
        try {
            runnable()
            success = true
        } finally {
            // Doesn't crash if the file doesn't exist, as we want the command output to be logged.
            outputFile.tryWaitingForFileToExists()

            if (waitExtraAfterEnd) {
                // temporary measure to see if b/266186795 is fixed
                Thread.sleep(3000)
            }
            val killOutput = shell("kill -INT $screenRecordPid")

            outputFile.tryWaitingForFileSizeToSettle()

            val screenRecordOutput = screenRecordingFileDescriptor.readAllAndClose()
            log(
                """
                screenrecord killed (kill command output="$killOutput")
                screenrecord command output:

                """
                    .trimIndent() + screenRecordOutput.prependIndent("   ")
            )

            val shouldDeleteRecording = !keepTestLevelRecordingOnSuccess && success
            if (shouldDeleteRecording) {
                shell("rm $outputFile")
                log("$outputFile deleted, because test passed")
            }

            if (outputFile.exists()) {
                val fileSizeKb = Files.size(outputFile.toPath()) / 1024
                log("Screen recording captured at: $outputFile. File size: $fileSizeKb KB")
            } else if (!shouldDeleteRecording) {
                Log.e(TAG, "File not created successfully. Can't determine size of $outputFile")
            }
        }

        if (screenRecordingInProgress()) {
            Log.w(
                TAG,
                "Other screen recordings are in progress after this is done. " +
                    "(pids=\"$screenrecordPids\")."
            )
        }
    }

    private fun File.tryWaitingForFileToExists() {
        try {
            ensureThat("Recording output created") { exists() }
        } catch (e: FailedEnsureException) {
            Log.e(TAG, "Recording not created successfully.", e)
        }
    }

    private fun File.tryWaitingForFileSizeToSettle() {
        try {
            waitForValueToSettle(
                "Screen recording output size",
                minimumSettleTime = Duration.ofSeconds(5)
            ) {
                length()
            }
        } catch (e: FailedEnsureException) {
            Log.e(TAG, "Recording size didn't settle.", e)
        }
    }

    private fun screenRecordingInProgress() = screenrecordPids.isNotEmpty()

    private val screenrecordPids: List<String>
        get() = shell("pidof screenrecord").split(" ").filter { it != "" }

    /** Interface to indicate that the test should capture screenrecord */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(FUNCTION, CLASS, PROPERTY_GETTER, PROPERTY_SETTER)
    annotation class ScreenRecord

    private fun log(s: String) = Log.d(TAG, s)

    // Reads all from the stream and closes it.
    private fun ParcelFileDescriptor.readAllAndClose(): String =
        AutoCloseInputStream(this).use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }

    companion object {
        private const val TAG = "ScreenRecordRule"
        private const val SCREEN_RECORDING_TEST_LEVEL_OVERRIDE_KEY =
            "screen-recording-always-enabled-test-level"
        private const val SCREEN_RECORDING_CLASS_LEVEL_OVERRIDE_KEY =
            "screen-recording-always-enabled-class-level"
    }
}
