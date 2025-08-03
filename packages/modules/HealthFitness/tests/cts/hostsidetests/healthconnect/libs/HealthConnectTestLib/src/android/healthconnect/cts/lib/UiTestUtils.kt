/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.healthconnect.cts.lib

import android.Manifest
import android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.health.connect.datatypes.*
import android.health.connect.datatypes.units.Length
import android.os.SystemClock
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObjectOrNull
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

/** UI testing helper. */
object UiTestUtils {

    /** The label of the rescan button. */
    const val RESCAN_BUTTON_LABEL = "Scan device"

    private val WAIT_TIMEOUT = Duration.ofSeconds(5)
    private val NOT_DISPLAYED_TIMEOUT = Duration.ofMillis(500)

    private val TAG = UiTestUtils::class.java.simpleName

    private val TEST_DEVICE: Device =
        Device.Builder().setManufacturer("google").setModel("Pixel").setType(1).build()

    private val PACKAGE_NAME = "android.healthconnect.cts.ui"

    const val TEST_APP_PACKAGE_NAME = "android.healthconnect.cts.app"

    private val TEST_APP_2_PACKAGE_NAME = "android.healthconnect.cts.app2"

    const val TEST_APP_NAME = "Health Connect cts test app"

    /**
     * Waits for the given [selector] to be displayed and performs the given [uiObjectAction] on it.
     */
    fun waitDisplayed(selector: BySelector, uiObjectAction: (UiObject2) -> Unit = {}) {
        waitFor("$selector to be displayed", WAIT_TIMEOUT) {
            uiObjectAction(waitFindObject(selector, it.toMillis()))
            true
        }
    }

    fun scrollDownTo(selector: BySelector) {
        waitFindObject(By.scrollable(true)).scrollUntil(Direction.DOWN, Until.findObject(selector))
    }

    fun scrollDownToAndClick(selector: BySelector) {
        getUiDevice()
            .findObject(By.scrollable(true))
            .scrollUntil(Direction.DOWN, Until.findObject(selector))
            .click()
        getUiDevice().waitForIdle()
    }

    fun skipOnboardingIfAppears() {
        try {
            clickOnText("Get started")
        } catch (e: Exception) {
            try {
                clickOnText("GET STARTED")
            } catch (e: Exception) {
                // No-op if onboarding was not displayed.
            }
        }
    }

    /** Clicks on [UiObject2] with given [text]. */
    fun clickOnText(string: String) {
        waitDisplayed(By.text(string)) { it.click() }
    }

    fun deleteAllDataAndNavigateToHomeScreen() {
        navigateBackToHomeScreen()
        clickOnText("Data and access")
        clickOnText("Delete all data")
        try {
            clickOnText("Delete all data")
            clickOnText("Next")
            clickOnText("Delete")
            clickOnText("Done")
        } catch (e: Exception) {
            // No-op if all data is already deleted and the delete button is disabled.
        }
        navigateBackToHomeScreen()
    }

    fun navigateBackToHomeScreen() {
        while (isNotDisplayed("Permissions and data")) {
            try {
                waitDisplayed(By.desc("Navigate up"))
                clickOnContentDescription("Navigate up")
            } catch (e: Exception) {
                break
            }
        }
    }

    private fun isNotDisplayed(text: String): Boolean {
        try {
            waitNotDisplayed(By.text(text))
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun navigateUp() {
        clickOnContentDescription("Navigate up")
    }

    /** Clicks on [UiObject2] with given [string] content description. */
    fun clickOnContentDescription(string: String) {
        waitDisplayed(By.desc(string)) { it.click() }
    }

    /** Waits for all the given [textToFind] to be displayed. */
    fun waitAllTextDisplayed(vararg textToFind: CharSequence?) {
        for (text in textToFind) {
            if (text != null) waitDisplayed(By.text(text.toString()))
        }
    }

    /**
     * Waits for a button with the given [label] to be displayed and performs the given
     * [uiObjectAction] on it.
     */
    fun waitButtonDisplayed(label: CharSequence, uiObjectAction: (UiObject2) -> Unit = {}) =
        waitDisplayed(buttonSelector(label), uiObjectAction)

    /** Waits for the given [selector] not to be displayed. */
    fun waitNotDisplayed(selector: BySelector) {
        waitFor("$selector not to be displayed", NOT_DISPLAYED_TIMEOUT) {
            waitFindObjectOrNull(selector, it.toMillis()) == null
        }
    }

    /** Waits for all the given [textToFind] not to be displayed. */
    fun waitAllTextNotDisplayed(vararg textToFind: CharSequence?) {
        for (text in textToFind) {
            if (text != null) waitNotDisplayed(By.text(text.toString()))
        }
    }

    /** Waits for a button with the given [label] not to be displayed. */
    fun waitButtonNotDisplayed(label: CharSequence) {
        waitNotDisplayed(buttonSelector(label))
    }

    fun UiDevice.rotate() {
        unfreezeRotation()
        if (isNaturalOrientation) {
            setOrientationLeft()
        } else {
            setOrientationNatural()
        }
        freezeRotation()
        waitForIdle()
    }

    fun UiDevice.resetRotation() {
        if (!isNaturalOrientation) {
            unfreezeRotation()
            setOrientationNatural()
            freezeRotation()
            waitForIdle()
        }
    }

    private fun buttonSelector(label: CharSequence): BySelector {
        return By.clickable(true).text(Pattern.compile("$label|${label.toString().uppercase()}"))
    }

    private fun waitFor(
        message: String,
        uiAutomatorConditionTimeout: Duration,
        uiAutomatorCondition: (Duration) -> Boolean,
    ) {
        val elapsedStartMillis = SystemClock.elapsedRealtime()
        while (true) {
            getUiDevice().waitForIdle()
            val durationSinceStart =
                Duration.ofMillis(SystemClock.elapsedRealtime() - elapsedStartMillis)
            if (durationSinceStart >= WAIT_TIMEOUT) {
                break
            }
            val remainingTime = WAIT_TIMEOUT - durationSinceStart
            val uiAutomatorTimeout = minOf(uiAutomatorConditionTimeout, remainingTime)
            try {
                if (uiAutomatorCondition(uiAutomatorTimeout)) {
                    return
                } else {
                    Log.d(TAG, "Failed condition for $message, will retry if within timeout")
                }
            } catch (e: StaleObjectException) {
                Log.d(TAG, "StaleObjectException for $message, will retry if within timeout", e)
            }
        }

        throw TimeoutException("Timed out waiting for $message")
    }

    fun stepsRecordFromTestApp(): StepsRecord {
        return stepsRecord(TEST_APP_PACKAGE_NAME, /* stepCount= */ 10)
    }

    fun stepsRecordFromTestApp(stepCount: Long): StepsRecord {
        return stepsRecord(TEST_APP_PACKAGE_NAME, stepCount)
    }

    fun stepsRecordFromTestApp(startTime: Instant): StepsRecord {
        return stepsRecord(
            TEST_APP_PACKAGE_NAME, /* stepCount= */ 10, startTime, startTime.plusSeconds(100))
    }

    fun stepsRecordFromTestApp(stepCount: Long, startTime: Instant): StepsRecord {
        return stepsRecord(TEST_APP_PACKAGE_NAME, stepCount, startTime, startTime.plusSeconds(100))
    }

    fun stepsRecordFromTestApp2(): StepsRecord {
        return stepsRecord(TEST_APP_2_PACKAGE_NAME, /* stepCount= */ 10)
    }

    fun distanceRecordFromTestApp(): DistanceRecord {
        return distanceRecord(TEST_APP_PACKAGE_NAME)
    }

    fun distanceRecordFromTestApp2(): DistanceRecord {
        return distanceRecord(TEST_APP_2_PACKAGE_NAME)
    }

    private fun stepsRecord(packageName: String, stepCount: Long): StepsRecord {
        return stepsRecord(packageName, stepCount, Instant.now().minusMillis(1000), Instant.now())
    }

    private fun stepsRecord(
        packageName: String,
        stepCount: Long,
        startTime: Instant,
        endTime: Instant
    ): StepsRecord {
        val dataOrigin: DataOrigin = DataOrigin.Builder().setPackageName(packageName).build()
        val testMetadataBuilder: Metadata.Builder = Metadata.Builder()
        testMetadataBuilder.setDevice(TEST_DEVICE).setDataOrigin(dataOrigin)
        testMetadataBuilder.setClientRecordId("SR" + Math.random())
        return StepsRecord.Builder(testMetadataBuilder.build(), startTime, endTime, stepCount)
            .build()
    }

    private fun distanceRecord(packageName: String): DistanceRecord {
        val dataOrigin: DataOrigin = DataOrigin.Builder().setPackageName(packageName).build()
        val testMetadataBuilder: Metadata.Builder = Metadata.Builder()
        testMetadataBuilder.setDevice(TEST_DEVICE).setDataOrigin(dataOrigin)
        testMetadataBuilder.setClientRecordId("SR" + Math.random())
        return DistanceRecord.Builder(
                testMetadataBuilder.build(),
                Instant.now().minusMillis(1000),
                Instant.now(),
                Length.fromMeters(500.0))
            .build()
    }

    fun grantPermissionViaPackageManager(context: Context, packageName: String, permName: String) {
        val pm = context.packageManager
        if (pm.checkPermission(permName, packageName) == PERMISSION_GRANTED) {
            return
        }
        runWithShellPermissionIdentity(
            { pm.grantRuntimePermission(packageName, permName, context.user) },
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS)
    }

    fun revokePermissionViaPackageManager(context: Context, packageName: String, permName: String) {
        val pm = context.packageManager
        if (pm.checkPermission(permName, packageName) == PERMISSION_DENIED) {
            return
        }
        runWithShellPermissionIdentity(
            { pm.revokeRuntimePermission(packageName, permName, context.user, /* reason= */ "") },
            REVOKE_RUNTIME_PERMISSIONS)
    }
}
