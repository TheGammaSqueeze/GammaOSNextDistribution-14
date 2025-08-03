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

package android.permissionmultiuser.cts

import android.app.Instrumentation
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.UiAutomation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.os.UserHandle
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.StaleObjectException
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject2
import android.util.Log
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireNotWatch
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.bedstead.harrier.annotations.RequireSdkVersion
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.waitForBroadcasts
import com.android.compatibility.common.util.UiAutomatorUtils
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the UI that displays information about apps' updates to their data sharing policies when
 * device has multiple users.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@RequireSdkVersion(min = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
@RequireDoesNotHaveFeature(FEATURE_LEANBACK)
@RequireNotWatch(reason = "Data sharing update page is unavailable on watch")
@RunWith(BedsteadJUnit4::class)
@EnsureSecureSettingSet(key = "user_setup_complete", value = "1")
class AppDataSharingUpdatesTest {

    @get:Rule
    val deviceConfigSafetyLabelChangeNotificationsEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED,
            true.toString()
        )

    @get:Rule
    val deviceConfigDataSharingUpdatesPeriod =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS,
            "600000"
        )

    /**
     * This rule serves to limit the max number of safety labels that can be persisted, so that
     * repeated tests don't overwhelm the disk storage on the device.
     */
    @get:Rule
    val deviceConfigMaxSafetyLabelsPersistedPerApp =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP,
            "2"
        )

    @Before
    fun registerInstallSessionResultReceiver() {
        context.registerReceiver(
            installSessionResultReceiver,
            IntentFilter(INSTALL_ACTION_CALLBACK),
            RECEIVER_EXPORTED
        )
    }

    @After
    fun unregisterInstallSessionResultReceiver() {
        try {
            context.unregisterReceiver(installSessionResultReceiver)
        } catch (ignored: IllegalArgumentException) {}
    }

    @Test
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    @RequireRunOnWorkProfile
    @ApiTest(apis = ["android.content.Intent#ACTION_REVIEW_APP_DATA_SHARING_UPDATES"])
    fun openDataSharingUpdatesPage_workProfile_whenAppHasUpdateAndLocationGranted_showUpdates() {
        installPackageViaSession(LOCATION_PACKAGE_APK_PATH, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        installPackageViaSession(
            LOCATION_PACKAGE_APK_PATH,
            createAppMetadataWithLocationSharingNoAds()
        )
        waitForBroadcasts()
        grantLocationPermission(LOCATION_PACKAGE_NAME)

        startAppDataSharingUpdatesActivityForUser(deviceState.initialUser().userHandle())

        try {
            assertUpdatesPresent()
            findView(By.textContains(LOCATION_PACKAGE_NAME_SUBSTRING), true)
        } finally {
            pressBack()
            uninstallPackage(LOCATION_PACKAGE_NAME)
        }
    }

    @Test
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    @RequireRunOnAdditionalUser
    @ApiTest(apis = ["android.content.Intent#ACTION_REVIEW_APP_DATA_SHARING_UPDATES"])
    fun openDataSharingUpdatesPage_additionalUser_whenAppHasUpdateAndLocationGranted_showUpdates() {
        installPackageViaSession(LOCATION_PACKAGE_APK_PATH, createAppMetadataWithNoSharing())
        waitForBroadcasts()
        installPackageViaSession(
            LOCATION_PACKAGE_APK_PATH,
            createAppMetadataWithLocationSharingNoAds()
        )
        waitForBroadcasts()
        grantLocationPermission(LOCATION_PACKAGE_NAME)

        startAppDataSharingUpdatesActivityForUser(deviceState.additionalUser().userHandle())

        try {
            assertUpdatesPresent()
            findView(By.textContains(LOCATION_PACKAGE_NAME_SUBSTRING), true)
        } finally {
            pressBack()
            uninstallPackage(LOCATION_PACKAGE_NAME)
        }

        deviceState.initialUser().switchTo()

        startAppDataSharingUpdatesActivityForUser(deviceState.initialUser().userHandle())

        try {
            // Verify that state does not leak across users.
            assertNoUpdatesPresent()
            findView(By.textContains(LOCATION_PACKAGE_NAME_SUBSTRING), false)
        } finally {
            pressBack()
        }
    }

    /** Companion object for [AppDataSharingUpdatesTest]. */
    companion object {
        @JvmField @ClassRule @Rule val deviceState: DeviceState = DeviceState()

        @JvmStatic
        private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context: Context = instrumentation.context
        @JvmStatic private val uiAutomation: UiAutomation = instrumentation.uiAutomation
        @JvmStatic private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
        @JvmStatic private val packageManager: PackageManager = context.packageManager
        @JvmStatic private val packageInstaller = packageManager.packageInstaller
        private data class SessionResult(val status: Int?)
        private val TAG = AppDataSharingUpdatesTest::class.simpleName

        private const val APK_DIRECTORY = "/data/local/tmp/cts-permissionmultiuser"
        private const val LOCATION_PACKAGE_NAME = "android.permissionmultiuser.cts.requestlocation"
        private const val LOCATION_PACKAGE_APK_PATH = "CtsRequestLocationApp.apk"
        private const val INSTALL_ACTION_CALLBACK = "AppDataSharingUpdatesTest.install_callback"
        private const val PACKAGE_INSTALLER_TIMEOUT = 60000L
        private const val IDLE_TIMEOUT_MILLIS: Long = 1000
        private const val TIMEOUT_MILLIS: Long = 20000

        private const val KEY_VERSION = "version"
        private const val KEY_SAFETY_LABELS = "safety_labels"
        private const val KEY_DATA_SHARED = "data_shared"
        private const val KEY_DATA_LABELS = "data_labels"
        private const val KEY_PURPOSES = "purposes"
        private const val INITIAL_SAFETY_LABELS_VERSION = 1L
        private const val INITIAL_TOP_LEVEL_VERSION = 1L
        private const val LOCATION_CATEGORY = "location"
        private const val APPROX_LOCATION = "approx_location"
        private const val PURPOSE_FRAUD_PREVENTION_SECURITY = 4

        private const val DATA_SHARING_UPDATES = "Data sharing updates for location"
        private const val DATA_SHARING_UPDATES_SUBTITLE =
            "These apps have changed the way they may share your location data. They may not" +
                " have shared it before, or may now share it for advertising or marketing" +
                " purposes."
        private const val DATA_SHARING_NO_UPDATES_MESSAGE = "No updates at this time"
        private const val UPDATES_IN_LAST_30_DAYS = "Updated within 30 days"
        private const val DATA_SHARING_UPDATES_FOOTER_MESSAGE =
            "The developers of these apps provided info about their data sharing practices" +
                " to an app store. They may update it over time.\n\nData sharing" +
                " practices may vary based on your app version, use, region, and age."
        private const val LOCATION_PACKAGE_NAME_SUBSTRING = "android.permissionmultiuser"
        private const val PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS =
            "data_sharing_update_period_millis"
        private const val PROPERTY_MAX_SAFETY_LABELS_PERSISTED_PER_APP =
            "max_safety_labels_persisted_per_app"

        private var installSessionResult = LinkedBlockingQueue<SessionResult>()

        private val installSessionResultReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)
                    val msg = intent.getStringExtra(EXTRA_STATUS_MESSAGE)
                    Log.d(TAG, "status: $status, msg: $msg")

                    installSessionResult.offer(SessionResult(status))
                }
            }

        /** Installs an app with the provided [appMetadata] */
        private fun installPackageViaSession(
            apkName: String,
            appMetadata: PersistableBundle? = null,
            packageSource: Int? = null
        ) {
            val session = createPackageInstallerSession(packageSource)
            runWithShellPermissionIdentity {
                writePackageInstallerSession(session, apkName)
                if (appMetadata != null) {
                    setAppMetadata(session, appMetadata)
                }
                commitPackageInstallerSession(session)

                // No need to click installer UI here due to running in shell permission identity
                // and  not needing user interaction to complete install.
                // Install should have succeeded.
                val result = getInstallSessionResult()
                assertThat(result.status).isEqualTo(STATUS_SUCCESS)
            }
        }

        private fun createPackageInstallerSession(
            packageSource: Int? = null
        ): PackageInstaller.Session {
            val sessionParam = SessionParams(SessionParams.MODE_FULL_INSTALL)
            if (packageSource != null) {
                sessionParam.setPackageSource(packageSource)
            }

            val sessionId = packageInstaller.createSession(sessionParam)
            return packageInstaller.openSession(sessionId)
        }

        private fun writePackageInstallerSession(
            session: PackageInstaller.Session,
            apkName: String
        ) {
            val apkFile = File(APK_DIRECTORY, apkName)
            apkFile.inputStream().use { fileOnDisk ->
                session
                    .openWrite(/* name= */ apkName, /* offsetBytes= */ 0, /* lengthBytes= */ -1)
                    .use { sessionFile -> fileOnDisk.copyTo(sessionFile) }
            }
        }

        private fun commitPackageInstallerSession(session: PackageInstaller.Session) {
            // PendingIntent that triggers a INSTALL_ACTION_CALLBACK broadcast that gets received by
            // installSessionResultReceiver when install actions occur with this session
            val installActionPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(INSTALL_ACTION_CALLBACK).setPackage(context.packageName),
                    FLAG_UPDATE_CURRENT or FLAG_MUTABLE
                )
            session.commit(installActionPendingIntent.intentSender)
        }

        private fun setAppMetadata(session: PackageInstaller.Session, data: PersistableBundle) {
            try {
                session.setAppMetadata(data)
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
        }

        private fun getInstallSessionResult(
            timeout: Long = PACKAGE_INSTALLER_TIMEOUT
        ): SessionResult {
            return installSessionResult.poll(timeout, TimeUnit.MILLISECONDS)
                ?: SessionResult(null /* status */)
        }

        private fun uninstallPackage(packageName: String) {
            runShellCommand("pm uninstall $packageName").trim()
        }

        private fun pressBack() {
            uiDevice.pressBack()
            uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS)
        }

        /** Returns an App Metadata [PersistableBundle] representation where no data is shared. */
        private fun createAppMetadataWithNoSharing(): PersistableBundle {
            return createMetadataWithDataShared(PersistableBundle())
        }

        /**
         * Returns an App Metadata [PersistableBundle] representation where location data is shared,
         * but not for advertising purpose.
         */
        private fun createAppMetadataWithLocationSharingNoAds(): PersistableBundle {
            val locationBundle =
                PersistableBundle().apply {
                    putPersistableBundle(
                        APPROX_LOCATION,
                        PersistableBundle().apply {
                            putIntArray(
                                KEY_PURPOSES,
                                listOf(PURPOSE_FRAUD_PREVENTION_SECURITY).toIntArray()
                            )
                        }
                    )
                }

            val dataSharedBundle =
                PersistableBundle().apply {
                    putPersistableBundle(LOCATION_CATEGORY, locationBundle)
                }

            return createMetadataWithDataShared(dataSharedBundle)
        }

        /**
         * Returns an App Metadata [PersistableBundle] representation where with the provided data
         * shared.
         */
        private fun createMetadataWithDataShared(
            dataSharedBundle: PersistableBundle
        ): PersistableBundle {
            val dataLabelBundle =
                PersistableBundle().apply {
                    putPersistableBundle(KEY_DATA_SHARED, dataSharedBundle)
                }

            val safetyLabelBundle =
                PersistableBundle().apply {
                    putLong(KEY_VERSION, INITIAL_SAFETY_LABELS_VERSION)
                    putPersistableBundle(KEY_DATA_LABELS, dataLabelBundle)
                }

            return PersistableBundle().apply {
                putLong(KEY_VERSION, INITIAL_TOP_LEVEL_VERSION)
                putPersistableBundle(KEY_SAFETY_LABELS, safetyLabelBundle)
            }
        }

        /**
         * Starts activity with intent [ACTION_REVIEW_APP_DATA_SHARING_UPDATES] for the provided
         * user.
         */
        fun startAppDataSharingUpdatesActivityForUser(userHandle: UserHandle) {
            runWithShellPermissionIdentity {
                context.startActivityAsUser(
                    Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    userHandle
                )
            }
        }

        private fun assertUpdatesPresent() {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_UPDATES_SUBTITLE), true)
            findView(By.textContains(UPDATES_IN_LAST_30_DAYS), true)
            findView(By.textContains(DATA_SHARING_UPDATES_FOOTER_MESSAGE), true)
        }

        private fun assertNoUpdatesPresent() {
            findView(By.descContains(DATA_SHARING_UPDATES), true)
            findView(By.textContains(DATA_SHARING_NO_UPDATES_MESSAGE), true)
            findView(By.textContains(LOCATION_PACKAGE_NAME_SUBSTRING), false)
        }

        private fun grantLocationPermission(packageName: String) {
            uiAutomation.grantRuntimePermission(
                packageName,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        protected fun waitFindObject(
            selector: BySelector,
            timeoutMillis: Long = 20_000L
        ): UiObject2 {
            uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS)
            val startTime = SystemClock.elapsedRealtime()
            return try {
                UiAutomatorUtils.waitFindObject(selector, timeoutMillis)
            } catch (e: StaleObjectException) {
                val remainingTime = timeoutMillis - (SystemClock.elapsedRealtime() - startTime)
                if (remainingTime <= 0) {
                    throw e
                }
                UiAutomatorUtils.waitFindObject(selector, remainingTime)
            }
        }

        private fun findView(selector: BySelector, expected: Boolean) {
            val timeoutMillis =
                if (expected) {
                    20000L
                } else {
                    1000L
                }

            val exception =
                try {
                    uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS)
                    val startTime = SystemClock.elapsedRealtime()
                    try {
                        UiAutomatorUtils.waitFindObject(selector, timeoutMillis)
                    } catch (e: StaleObjectException) {
                        val remainingTime =
                            timeoutMillis - (SystemClock.elapsedRealtime() - startTime)
                        if (remainingTime <= 0) {
                            throw e
                        }
                        UiAutomatorUtils.waitFindObject(selector, remainingTime)
                    }
                    null
                } catch (e: Exception) {
                    e
                }
            val actual = exception == null
            val message =
                if (expected) {
                    "Expected view $selector not found"
                } else {
                    "Unexpected view found: $selector"
                }
            Assert.assertTrue(message, actual == expected)
        }
    }
}
