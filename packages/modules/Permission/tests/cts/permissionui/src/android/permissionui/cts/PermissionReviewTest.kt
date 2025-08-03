/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.permissionui.cts

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FlakyTest
class PermissionReviewTest : BaseUsePermissionTest() {

    @Before
    fun assumeNotIndividuallyControlled() {
        Assume.assumeFalse(packageManager.arePermissionsIndividuallyControlled())
    }

    @Before
    fun installApp22CalendarOnly() {
        installPackage(APP_APK_PATH_22_CALENDAR_ONLY)
    }

    @get:Rule val activityRule = ActivityTestRule(StartForFutureActivity::class.java, false, false)

    @Test
    fun testDenyCalendarDuringReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {
            // Deny
            clickPermissionControllerUi(By.text("Calendar"))
            // Confirm deny
            click(By.res("android:id/button1"))

            clickPermissionReviewContinue()
        }

        clearTargetSdkWarning()
        assertAppHasCalendarAccess(false)
    }

    @Test
    fun testDenyGrantCalendarDuringReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {
            // Deny
            clickPermissionControllerUi(By.text("Calendar"))
            // Confirm deny
            click(By.res("android:id/button1"))

            // Grant
            clickPermissionControllerUi(By.text("Calendar"))

            clickPermissionReviewContinue()
        }

        clearTargetSdkWarning()
        assertAppHasCalendarAccess(true)
    }

    @Test
    fun testDenyGrantDenyCalendarDuringReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_OK) {
            // Deny
            clickPermissionControllerUi(By.text("Calendar"))

            // Confirm deny
            click(By.res("android:id/button1"))

            // Grant
            clickPermissionControllerUi(By.text("Calendar"))

            // Deny
            clickPermissionControllerUi(By.text("Calendar"))

            clickPermissionReviewContinue()
        }

        clearTargetSdkWarning()
        assertAppHasCalendarAccess(false)
    }

    @Test
    fun testCancelReview() {
        // Start APK_22_ONLY_CALENDAR, but cancel review
        cancelPermissionReview()

        // Start APK_22_ONLY_CALENDAR again, now approve review
        approvePermissionReview()

        assertAppDoesNotNeedPermissionReview()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "TIRAMISU")
    fun testNotificationPermissionAddedToReview() {
        startAppActivityAndAssertResultCode(Activity.RESULT_CANCELED) {
            waitFindObject(By.text("Notifications"), 5000L)
            clickPermissionReviewCancel()
        }
    }

    @Test
    fun testReviewPermissionWhenServiceIsBound() {
        val results = LinkedBlockingQueue<Int>()
        // We are starting a activity instead of the service directly, because
        // the service comes from a different app than the CTS tests.
        // This app will be considered idle on devices that have idling enabled (automotive),
        // and the service wouldn't be allowed to be started without the activity.
        activityRule
            .launchActivity(null)
            .startActivity(
                Intent().apply {
                    component =
                        ComponentName(
                            APP_PACKAGE_NAME,
                            "$APP_PACKAGE_NAME.StartCheckPermissionServiceActivity"
                        )
                    putExtra(
                        "$APP_PACKAGE_NAME.RESULT",
                        object : ResultReceiver(Handler(Looper.getMainLooper())) {
                            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                                results.offer(resultCode)
                            }
                        }
                    )
                    putExtra(
                        "$APP_PACKAGE_NAME.PERMISSION",
                        android.Manifest.permission.READ_CALENDAR
                    )
                }
            )

        // Service is not started before permission are reviewed
        assertNull(results.poll(UNEXPECTED_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))

        clickPermissionReviewContinueAndClearSdkWarning()

        // Service should be started after permission review
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            results.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        )
    }
}
