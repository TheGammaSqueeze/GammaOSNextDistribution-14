/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.healthconnect.cts.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.health.connect.HealthConnectManager.ACTION_REQUEST_EXERCISE_ROUTE
import android.health.connect.HealthConnectManager.EXTRA_EXERCISE_ROUTE
import android.health.connect.HealthConnectManager.EXTRA_SESSION_ID
import android.health.connect.HealthPermissions
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType
import android.healthconnect.cts.lib.TestAppProxy
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.lib.UiTestUtils.waitDisplayed
import android.healthconnect.cts.lib.UiTestUtils.waitNotDisplayed
import android.healthconnect.cts.utils.DataFactory.getEmptyMetadata
import android.healthconnect.cts.utils.PermissionHelper.READ_EXERCISE_ROUTES
import android.healthconnect.cts.utils.PermissionHelper.getDeclaredHealthPermissions
import android.healthconnect.cts.utils.PermissionHelper.grantPermission
import android.healthconnect.cts.utils.PermissionHelper.runWithDeviceConfigForController
import android.healthconnect.cts.utils.PermissionHelper.runWithRevokedPermission
import android.healthconnect.cts.utils.PermissionHelper.runWithUserFixedPermission
import android.healthconnect.cts.utils.ProxyActivity
import android.healthconnect.cts.utils.RevokedHealthPermissionRule
import android.healthconnect.cts.utils.TestUtils
import android.healthconnect.cts.utils.TestUtils.insertRecordAndGetId
import androidx.test.uiautomator.By
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.AfterClass
import org.junit.Assert.assertThrows
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class ExerciseRouteRequestTest : HealthConnectBaseTest() {

    companion object {
        private const val READ_ROUTES_ALL_FEATURE_FLAG = "exercise_routes_read_all_enable"
        private val ROUTE_READER_WRITER_APP =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.A")

        @JvmStatic
        @AfterClass
        fun tearDown() {
            TestUtils.verifyDeleteRecords(
                ExerciseSessionRecord::class.java,
                TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now())
                    .build())
        }
    }

    @JvmField
    @Rule
    public val revokedPermissionRule =
        RevokedHealthPermissionRule(ROUTE_READER_WRITER_APP.getPackageName(), READ_EXERCISE_ROUTES)

    @Test
    fun requestRoute_showsDialog_clickAllowThis_returnsRoute_permissionRemainsRevoked() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent) {
                waitDisplayed(By.text("Allow all routes"))
                waitDisplayed(By.text("Don't allow"))
                clickOnText("Allow this route")
            }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_showsDialog_clickAllowAll_returnsRoute_grantsPermission() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent) {
                waitDisplayed(By.text("Allow this route"))
                waitDisplayed(By.text("Don't allow"))
                clickOnText("Allow all routes")
            }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionGranted(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_showsDialog_clickDeny_returnsNull_permissionRemainsRevoked() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent) {
                waitDisplayed(By.text("Allow all routes"))
                waitDisplayed(By.text("Allow this route"))
                clickOnText("Don't allow")
            }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_ownSession_writeRouteGranted_readRoutesDenied_returnsRoute() {
        val record = getExerciseSessionWithRoute()
        val recordId: String = ROUTE_READER_WRITER_APP.insertRecords(record).get(0)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result = ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_ownSession_writeRouteDenied_readRoutesDenied_notUserFixed_showsDialog() {
        val record = getExerciseSessionWithRoute()
        val recordId: String = ROUTE_READER_WRITER_APP.insertRecords(record).get(0)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            runWithRevokedPermission(
                ROUTE_READER_WRITER_APP.packageName, HealthPermissions.WRITE_EXERCISE_ROUTE) {
                    ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent) {
                        waitDisplayed(By.text("Allow all routes"))
                        clickOnText("Allow this route")
                    }
                }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_ownSession_writeRouteDenied_readRoutesDenied_userFixed_returnsNull() {
        val record = getExerciseSessionWithRoute()
        val recordId: String = ROUTE_READER_WRITER_APP.insertRecords(record).get(0)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            runWithRevokedPermission(
                ROUTE_READER_WRITER_APP.packageName, HealthPermissions.WRITE_EXERCISE_ROUTE) {
                    runWithUserFixedPermission(
                        ROUTE_READER_WRITER_APP.packageName, READ_EXERCISE_ROUTES) {
                            ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)
                        }
                }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_ownSession_writeRouteDenied_readRoutesGranted_returnsRoute() {
        val record = getExerciseSessionWithRoute()
        val recordId: String = ROUTE_READER_WRITER_APP.insertRecords(record).get(0)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)
        grantPermission(ROUTE_READER_WRITER_APP.getPackageName(), READ_EXERCISE_ROUTES)

        val result =
            runWithRevokedPermission(
                ROUTE_READER_WRITER_APP.packageName, HealthPermissions.WRITE_EXERCISE_ROUTE) {
                    ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)
                }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionGranted(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_readRoutesGranted_doesNotShowDialog_returnsRoute() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)
        grantPermission(ROUTE_READER_WRITER_APP.getPackageName(), READ_EXERCISE_ROUTES)

        val result = ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionGranted(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_sessionWithoutRoute_returnsNull() {
        val record = getExerciseSessionWithoutRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result = ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_nonExistingSession_returnsNull() {
        val requestIntent = Intent(ACTION_REQUEST_EXERCISE_ROUTE)
        requestIntent.putExtra(EXTRA_SESSION_ID, "nonExistingId")

        val result = ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_sessionIdNotSet_returnsNull() {
        val requestIntent = Intent(ACTION_REQUEST_EXERCISE_ROUTE)

        val result = ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        assertThat(result.resultData.extras).isNull()
        assertPermissionRevoked(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_withReadWriteRoutePermissionsRevoked_showsDialog_returnsRoute() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            runWithRevokedPermission(
                ROUTE_READER_WRITER_APP.getPackageName(), HealthPermissions.WRITE_EXERCISE_ROUTE) {
                    ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent) {
                        waitDisplayed(By.text("Allow this route"))
                        clickOnText("Allow all routes")
                    }
                }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
        assertPermissionGranted(ROUTE_READER_WRITER_APP, READ_EXERCISE_ROUTES)
    }

    @Test
    fun requestRoute_readExercisePermissionRevoked_throwsSecurityException() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)
        grantPermission(ROUTE_READER_WRITER_APP.getPackageName(), READ_EXERCISE_ROUTES)

        assertThrows(SecurityException::class.java) {
            runWithRevokedPermission(
                ROUTE_READER_WRITER_APP.getPackageName(), HealthPermissions.READ_EXERCISE) {
                    ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)
                }
        }
    }

    @Test
    fun requestRoute_readRoutesPermissionNotDeclared_doesNotShowDialog_returnsNull() {
        assertThat(getDeclaredHealthPermissions(context.packageName))
            .doesNotContain(READ_EXERCISE_ROUTES)
        val record = getExerciseSessionWithRoute()
        val recordId = ROUTE_READER_WRITER_APP.insertRecords(record).get(0)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result = ProxyActivity.launchActivityForResult(requestIntent) {}

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
    }

    @Test
    fun requestRoute_ownSession_readRoutesPermissionNotDeclared_doesNotShowDialog_returnsNull() {
        assertThat(getDeclaredHealthPermissions(context.packageName))
            .doesNotContain(READ_EXERCISE_ROUTES)
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result = ProxyActivity.launchActivityForResult(requestIntent) {}

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
    }

    @Test
    fun requestRoute_readRoutesPermissionDeniedUserFixed_doesNotShowDialog_returnsNull() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            runWithUserFixedPermission(
                ROUTE_READER_WRITER_APP.getPackageName(), READ_EXERCISE_ROUTES) {
                    ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)
                }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
    }

    @Test
    @Ignore("b/322300333#comment5")
    fun requestRoute_allowAllFeatureOff_allowAllNotDisplayed() {
        val record = getExerciseSessionWithRoute()
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            runWithDeviceConfigForController(READ_ROUTES_ALL_FEATURE_FLAG, "false") {
                ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent) {
                    waitNotDisplayed(By.text("Allow all routes"))
                    waitDisplayed(By.text("Don't allow"))
                    clickOnText("Allow this route")
                }
            }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
    }

    @Test
    fun requestRoute_enforcesAccessTimeLimits() {
        val record =
            getExerciseSessionWithRoute(
                TestUtils.yesterdayAt("11:00")
                    .minus(Duration.ofDays(30))
                    .minus(Duration.ofHours(1)))
        val recordId = insertRecordAndGetId(record)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result = ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_SESSION_ID)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
    }

    @Test
    fun requestRoute_ownRoute_writeRouteGranted_ignoresAccessTimeLimits() {
        val record =
            getExerciseSessionWithRoute(TestUtils.yesterdayAt("11:00").minus(Duration.ofDays(60)))
        val recordId: String = ROUTE_READER_WRITER_APP.insertRecords(record).get(0)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result = ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent)

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
    }

    @Test
    fun requestRoute_ownRoute_writeRouteDenied_ignoresAccessTimeLimits_showsDialog() {
        val record =
            getExerciseSessionWithRoute(TestUtils.yesterdayAt("11:00").minus(Duration.ofDays(60)))
        val recordId: String = ROUTE_READER_WRITER_APP.insertRecords(record).get(0)
        val requestIntent =
            Intent(ACTION_REQUEST_EXERCISE_ROUTE).putExtra(EXTRA_SESSION_ID, recordId)

        val result =
            runWithRevokedPermission(
                ROUTE_READER_WRITER_APP.packageName, HealthPermissions.WRITE_EXERCISE_ROUTE) {
                    ROUTE_READER_WRITER_APP.startActivityForResult(requestIntent) {
                        waitDisplayed(By.text("Allow all routes"))
                        clickOnText("Allow this route")
                    }
                }

        assertThat(result.resultCode).isEqualTo(Activity.RESULT_OK)
        val extras = result.resultData.extras!!
        assertThat(extras.keySet()).containsExactly(EXTRA_EXERCISE_ROUTE, EXTRA_SESSION_ID)
        assertThat(extras.getParcelable(EXTRA_EXERCISE_ROUTE, ExerciseRoute::class.java))
            .isEqualTo(record.route)
        assertThat(extras.getString(EXTRA_SESSION_ID)).isEqualTo(recordId)
    }

    private fun assertPermissionGranted(testAppProxy: TestAppProxy, permission: String) {
        assertThat(context.packageManager.checkPermission(permission, testAppProxy.packageName))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    private fun assertPermissionRevoked(testAppProxy: TestAppProxy, permission: String) {
        assertThat(context.packageManager.checkPermission(permission, testAppProxy.packageName))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    private fun getExerciseSessionWithRoute() =
        getExerciseSessionWithRoute(TestUtils.yesterdayAt("11:00"))

    private fun getExerciseSessionWithRoute(startTime: Instant) =
        ExerciseSessionRecord.Builder(
                getEmptyMetadata(),
                startTime,
                startTime.plus(Duration.ofMinutes(30)),
                ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
            .setRoute(
                ExerciseRoute(
                    listOf(
                        ExerciseRoute.Location.Builder(startTime.plusSeconds(5), 13.0, -22.0)
                            .build())))
            .build()

    private fun getExerciseSessionWithoutRoute(): ExerciseSessionRecord {
        val startTime = TestUtils.yesterdayAt("11:00")
        return ExerciseSessionRecord.Builder(
                getEmptyMetadata(),
                startTime,
                startTime.plus(Duration.ofMinutes(30)),
                ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
            .build()
    }
}
