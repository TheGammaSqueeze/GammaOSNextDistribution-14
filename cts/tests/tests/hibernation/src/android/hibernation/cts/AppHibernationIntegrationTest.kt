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

package android.hibernation.cts

import android.Manifest
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.Instrumentation
import android.apphibernation.AppHibernationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.Flags
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.permission.PermissionControllerManager
import android.permission.PermissionControllerManager.HIBERNATION_ELIGIBILITY_ELIGIBLE
import android.permission.PermissionControllerManager.HIBERNATION_ELIGIBILITY_UNKNOWN
import android.platform.test.annotations.AppModeFull
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_APP_HIBERNATION
import android.provider.Settings
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.ExceptionUtils.wrappingExceptions
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.android.compatibility.common.util.UiDumpUtils
import com.android.modules.utils.build.SdkLevel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for app hibernation.
 */
@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot access app hibernation")
@CddTest(requirements = ["3.5.2"])
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
class AppHibernationIntegrationTest {
    companion object {
        const val LOG_TAG = "AppHibernationIntegrationTest"
        const val WAIT_TIME_MS = 1000L
        const val TIMEOUT_TIME_MS = 5000L
        const val MAX_SCROLL_ATTEMPTS = 3
        const val TEST_UNUSED_THRESHOLD = 1L
        const val HIBERNATION_ENABLED_KEY = "app_hibernation_enabled"

        const val CMD_KILL = "am kill %s"

        @JvmStatic
        @BeforeClass
        fun beforeAllTests() {
            runBootCompleteReceiver(InstrumentationRegistry.getTargetContext(), LOG_TAG)
        }
    }
    private val context: Context = InstrumentationRegistry.getTargetContext()
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var packageManager: PackageManager
    private lateinit var permissionControllerManager: PermissionControllerManager
    private var oldHibernationValue: String? = null

    @get:Rule
    val disableAnimationRule = DisableAnimationRule()

    @get:Rule
    val freezeRotationRule = FreezeRotationRule()

    @Before
    fun setup() {
        oldHibernationValue = callWithShellPermissionIdentity {
            DeviceConfig.getProperty(NAMESPACE_APP_HIBERNATION, HIBERNATION_ENABLED_KEY)
        }
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, HIBERNATION_ENABLED_KEY, "true",
                false /* makeDefault */)
        }
        packageManager = context.packageManager
        permissionControllerManager =
            context.getSystemService(PermissionControllerManager::class.java)!!

        // Collapse notifications
        assertThat(
            runShellCommandOrThrow("cmd statusbar collapse"),
            CoreMatchers.equalTo(""))
        clearNotifications()

        // Wake up the device
        runShellCommandOrThrow("input keyevent KEYCODE_WAKEUP")
        runShellCommandOrThrow("input keyevent 82")
        runShellCommandOrThrow("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")

        resetJob(context)
        bypassBatterySavingRestrictions(context)
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG)
    }

    @After
    fun cleanUp() {
        goHome()
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, HIBERNATION_ENABLED_KEY,
                oldHibernationValue, false /* makeDefault */)
        }
        resetBatterySavingRestrictions(context)
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity()
    }

    @Test
    @CddTest(requirement = "3.5.2/C-1-2")
    fun testUnusedApp_getsForceStopped() {
        withUnusedThresholdMs(TEST_UNUSED_THRESHOLD) {
            withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
                // Use app
                startApp(APK_PACKAGE_NAME_S_APP)
                leaveApp(APK_PACKAGE_NAME_S_APP)
                killApp(APK_PACKAGE_NAME_S_APP)

                // Wait for the unused threshold time to pass
                Thread.sleep(TEST_UNUSED_THRESHOLD)

                // Run job
                runAppHibernationJob(context, LOG_TAG)

                // Verify
                val ai =
                    packageManager.getApplicationInfo(APK_PACKAGE_NAME_S_APP, 0 /* flags */)
                val stopped = ((ai.flags and ApplicationInfo.FLAG_STOPPED) != 0)
                assertTrue(stopped)

                if (hasFeatureTV()) {
                    // Skip checking unused apps screen because it may be unavailable on TV
                    return
                }
                openUnusedAppsNotification()
                waitFindObject(By.text(APK_PACKAGE_NAME_S_APP))
            }
        }
    }

    @Test
    @CddTest(requirement = "3.5.2/C-1-2")
    fun testPreSVersionUnusedApp_doesntGetForceStopped() {
        assumeFalse(
            "TV may have different behaviour for Pre-S version apps",
            hasFeatureTV())
        withUnusedThresholdMs(TEST_UNUSED_THRESHOLD) {
            withApp(APK_PATH_R_APP, APK_PACKAGE_NAME_R_APP) {
                // Use app
                startApp(APK_PACKAGE_NAME_R_APP)
                leaveApp(APK_PACKAGE_NAME_R_APP)
                killApp(APK_PACKAGE_NAME_R_APP)

                // Wait for the unused threshold time to pass
                Thread.sleep(TEST_UNUSED_THRESHOLD)

                // Run job
                runAppHibernationJob(context, LOG_TAG)

                // Verify
                val ai =
                    packageManager.getApplicationInfo(APK_PACKAGE_NAME_R_APP, 0 /* flags */)
                val stopped = ((ai.flags and ApplicationInfo.FLAG_STOPPED) != 0)
                assertFalse(stopped)
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.permission.PermissionControllerManager#getUnusedAppCount"])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    fun testUnusedAppCount() {
        withUnusedThresholdMs(TEST_UNUSED_THRESHOLD) {
            withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
                // Use app
                startApp(APK_PACKAGE_NAME_S_APP)
                leaveApp(APK_PACKAGE_NAME_S_APP)
                killApp(APK_PACKAGE_NAME_S_APP)

                // Wait for the unused threshold time to pass
                Thread.sleep(TEST_UNUSED_THRESHOLD)

                // Run job
                runAppHibernationJob(context, LOG_TAG)

                // Verify unused app count pulled correctly
                val countDownLatch = CountDownLatch(1)
                var unusedAppCount = -1
                runWithShellPermissionIdentity {
                    permissionControllerManager.getUnusedAppCount({ r -> r.run() },
                        { res ->
                            unusedAppCount = res
                            countDownLatch.countDown()
                        })

                    assertTrue("Timed out waiting for unused app count",
                        countDownLatch.await(TIMEOUT_TIME_MS, TimeUnit.MILLISECONDS))
                    assertTrue("Expected non-zero unused app count but is $unusedAppCount",
                        unusedAppCount > 0)
                }
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.permission.PermissionControllerManager#getHibernationEligibility"])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    fun testGetHibernationEligibility_eligibleByDefault() {
        withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
            // Verify app is eligible for hibernation
            val countDownLatch = CountDownLatch(1)
            var hibernationEligibility = HIBERNATION_ELIGIBILITY_UNKNOWN
            runWithShellPermissionIdentity {
                permissionControllerManager.getHibernationEligibility(APK_PACKAGE_NAME_S_APP,
                    { r -> r.run() },
                    { res ->
                        hibernationEligibility = res
                        countDownLatch.countDown()
                    })

                assertTrue("Timed out waiting for hibernation eligibility",
                    countDownLatch.await(TIMEOUT_TIME_MS, TimeUnit.MILLISECONDS))
                assertEquals("Expected test app to be eligible for hibernation but wasn't.",
                    HIBERNATION_ELIGIBILITY_ELIGIBLE, hibernationEligibility)
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.apphibernation.AppHibernationManager#getHibernationStatsForUser"])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    fun testGetHibernationStatsForUser_getsStatsForIndividualPackages() {
        val appHibernationManager = context.getSystemService(AppHibernationManager::class.java)!!
        withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
            runWithShellPermissionIdentity {
                val stats =
                    appHibernationManager.getHibernationStatsForUser(
                        setOf(APK_PACKAGE_NAME_S_APP))

                assertNotNull(stats[APK_PACKAGE_NAME_S_APP])
                assertTrue(stats[APK_PACKAGE_NAME_S_APP]!!.diskBytesSaved >= 0)
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.apphibernation.AppHibernationManager#getHibernationStatsForUser"])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    fun testGetHibernationStatsForUser_getsStatsForAllPackages() {
        val appHibernationManager = context.getSystemService(AppHibernationManager::class.java)!!
        withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
            runWithShellPermissionIdentity {
                val stats = appHibernationManager.getHibernationStatsForUser()

                assertFalse("Expected non-empty list of hibernation stats", stats.isEmpty())
                assertTrue("Expected test package to be in list of returned savings but wasn't",
                    stats.containsKey(APK_PACKAGE_NAME_S_APP))
            }
        }
    }

    @Test
    @ApiTest(apis = ["android.apphibernation.AppHibernationManager#isOatArtifactDeletionEnabled"])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        codeName = "UpsideDownCake")
    fun testIsOatArtifactDeletionEnabled_verifyConfigWithRuntimeValue() {
        val appHibernationManager = context.getSystemService(AppHibernationManager::class.java)!!
        withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
            runWithShellPermissionIdentity {
                val enabled =
                    appHibernationManager.isOatArtifactDeletionEnabled()
                val res = InstrumentationRegistry.getInstrumentation().getContext().getResources()
                val runtimeConfig = res.getBoolean(res.getIdentifier(
                    "config_hibernationDeletesOatArtifactsEnabled", "bool", "android"))

                assertEquals("Expected API return value is different from device config value",
                    enabled, runtimeConfig)
            }
        }
    }

    @Test
    @CddTest(requirements = ["3.5.1/C-1-2, C-1-4"])
    fun testAppInfo_RemovePermissionsAndFreeUpSpaceToggleExists() {
        assumeFalse(
            "Remove permissions and free up space toggle may be unavailable on TV",
            hasFeatureTV())
        assumeFalse(
            "Remove permissions and free up space toggle may be unavailable on Wear",
            hasFeatureWatch())

        withApp(APK_PATH_S_APP, APK_PACKAGE_NAME_S_APP) {
            // Open app info
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", APK_PACKAGE_NAME_S_APP, null /* fragment */)
            intent.data = uri
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)

            waitForIdle()

            val packageManager = context.packageManager
            val settingsPackage = intent.resolveActivity(packageManager).packageName
            val res = packageManager.getResourcesForApplication(settingsPackage)
            val title = if (isArchivingEnabled()) {
                res.getString(res.getIdentifier("unused_apps_switch_v2", "string", settingsPackage))
            } else {
                res.getString(res.getIdentifier("unused_apps_switch", "string", settingsPackage))
            }

            // Attempt standard search first (only uses first scrollable instance)
            var toggleFound = UiAutomatorUtils2.waitFindObjectOrNull(By.text(title)) != null

            if (!toggleFound) {
                // Settings can have multiple scrollable containers so all of them should be
                // searched.
                var i = 0
                var scrollableObject = UiScrollable(UiSelector().scrollable(true).instance(i))
                // Assert that at least one scrollable exists on screen
                var scrollableExists = scrollableObject.waitForExists(WAIT_TIME_MS)
                wrappingExceptions({ cause: Throwable? -> UiDumpUtils.wrapWithUiDump(cause)}) {
                    assertTrue("No scrollable exists on screen", scrollableExists)
                }
                while (!toggleFound && scrollableExists) {
                    toggleFound = scrollableObject.scrollTextIntoView(title) ||
                        UiAutomatorUtils2.waitFindObjectOrNull(By.text(title)) != null
                    scrollableObject = UiScrollable(UiSelector().scrollable(true).instance(++i))
                    scrollableExists = scrollableObject.waitForExists(WAIT_TIME_MS)
                }
            }

            wrappingExceptions({ cause: Throwable? -> UiDumpUtils.wrapWithUiDump(cause)}) {
                assertTrue("Remove permissions and free up space toggle not found", toggleFound)
            }
        }
    }

    private fun isArchivingEnabled(): Boolean {
        if (!SdkLevel.isAtLeastV()) return false
        if (Flags.archiving()) return true
        var systemProperty: String? = null
        runWithShellPermissionIdentity {
            systemProperty = System.getProperty("pm.archiving.enabled")
        }
        return "true" == systemProperty
    }

    private fun leaveApp(packageName: String) {
        eventually {
            goHome()
            SystemUtil.runWithShellPermissionIdentity {
                val packageImportance = context
                    .getSystemService(ActivityManager::class.java)!!
                    .getPackageImportance(packageName)
                assertThat(packageImportance, Matchers.greaterThan(IMPORTANCE_TOP_SLEEPING))
            }
        }
    }

    private fun killApp(packageName: String) {
        eventually {
            SystemUtil.runWithShellPermissionIdentity {
                runShellCommandOrThrow(String.format(CMD_KILL, packageName))
                val packageImportance = context
                    .getSystemService(ActivityManager::class.java)!!
                    .getPackageImportance(packageName)
                assertThat(packageImportance, Matchers.equalTo(IMPORTANCE_GONE))
            }
        }
    }

    private fun waitFindObject(selector: BySelector): UiObject2 {
        return waitFindObject(instrumentation.uiAutomation, selector)
    }
}
