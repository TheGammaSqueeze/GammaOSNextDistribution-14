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

package android.permissionui.cts

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.modules.utils.build.SdkLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/** Permission rationale activity tests. Permission rationale is only available on U+ */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@FlakyTest
class PermissionRationaleTest : BaseUsePermissionTest() {

    private var activityManager: ActivityManager? = null

    @get:Rule
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PERMISSION_RATIONALE_ENABLED,
            true.toString()
        )

    @Before
    fun setup() {
        Assume.assumeTrue("Permission rationale is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)

        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        enableComponent(TEST_INSTALLER_ACTIVITY_COMPONENT_NAME)

        installPackageWithInstallSourceAndMetadata(APP_APK_NAME_31)

        assertAppHasPermission(Manifest.permission.ACCESS_FINE_LOCATION, false)
    }

    @After
    fun disableTestInstallerActivity() {
        disableComponent(TEST_INSTALLER_ACTIVITY_COMPONENT_NAME)
    }

    @Test
    fun startsPermissionRationaleActivity_failedByNullMetadata() {
        installPackageWithInstallSourceAndNoMetadata(APP_APK_NAME_31)
        navigateToPermissionRationaleActivity_failedShowPermissionRationaleContainer()
    }

    @Test
    fun startsPermissionRationaleActivity_failedByEmptyMetadata() {
        installPackageWithInstallSourceAndEmptyMetadata(APP_APK_NAME_31)
        navigateToPermissionRationaleActivity_failedShowPermissionRationaleContainer()
    }

    @Test
    fun startsPermissionRationaleActivity_failedByNoTopLevelVersion() {
        installPackageWithInstallSourceAndMetadataWithoutTopLevelVersion(APP_APK_NAME_31)
        navigateToPermissionRationaleActivity_failedShowPermissionRationaleContainer()
    }

    @Test
    fun startsPermissionRationaleActivity_failedByInvalidTopLevelVersion() {
        installPackageWithInstallSourceAndMetadataWithInvalidTopLevelVersion(APP_APK_NAME_31)
        navigateToPermissionRationaleActivity_failedShowPermissionRationaleContainer()
    }

    @Test
    fun startsPermissionRationaleActivity_failedByNoSafetyLabelVersion() {
        installPackageWithInstallSourceAndMetadataWithoutSafetyLabelVersion(APP_APK_NAME_31)
        navigateToPermissionRationaleActivity_failedShowPermissionRationaleContainer()
    }

    @Test
    fun startsPermissionRationaleActivity_failedByInvalidSafetyLabelVersion() {
        installPackageWithInstallSourceAndMetadataWithInvalidSafetyLabelVersion(APP_APK_NAME_31)
        navigateToPermissionRationaleActivity_failedShowPermissionRationaleContainer()
    }

    @Test
    fun startsPermissionRationaleActivity() {
        navigateToPermissionRationaleActivity()

        assertPermissionRationaleDialogIsVisible(true)
    }

    @Test
    fun linksToInstallSource() {
        navigateToPermissionRationaleActivity()

        assertPermissionRationaleDialogIsVisible(true)

        clickInstallSourceLink()

        eventually {
            assertStoreLinkClickSuccessful(installerPackageName = TEST_INSTALLER_PACKAGE_NAME)
        }
    }

    @Ignore("b/282063206")
    @Test
    fun clickLinkToHelpCenter_opensHelpCenter() {
        Assume.assumeFalse(getPermissionControllerResString(HELP_CENTER_URL_ID).isNullOrEmpty())

        navigateToPermissionRationaleActivity()

        assertPermissionRationaleActivityTitleIsVisible(true)
        assertHelpCenterLinkAvailable(true)

        clickHelpCenterLink()

        eventually({ assertHelpCenterLinkClickSuccessful() }, NEW_WINDOW_TIMEOUT_MILLIS)
    }

    @Test
    fun noHelpCenterLinkAvailable_noHelpCenterClickAction() {
        Assume.assumeTrue(getPermissionControllerResString(HELP_CENTER_URL_ID).isNullOrEmpty())

        navigateToPermissionRationaleActivity()

        assertPermissionRationaleActivityTitleIsVisible(true)
        assertHelpCenterLinkAvailable(false)
    }

    @Test
    fun linksToSettings_noOp_dialogsNotClosed() {
        navigateToPermissionRationaleActivity()

        assertPermissionRationaleDialogIsVisible(true)

        clicksSettings_doesNothing_leaves()

        eventually { assertPermissionRationaleDialogIsVisible(true) }
    }

    @Test
    fun linksToSettings_grants_dialogsClose() {
        navigateToPermissionRationaleActivity()

        assertPermissionRationaleDialogIsVisible(true)

        clicksSettings_allowsForeground_leaves()

        // Setting, Permission rationale and Grant dialog should be dismissed
        eventually {
            assertPermissionSettingsVisible(false)
            assertPermissionRationaleDialogIsVisible(false)
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }

        assertAppHasPermission(Manifest.permission.ACCESS_FINE_LOCATION, true)
    }

    @Test
    fun linksToSettings_denies_dialogsClose() {
        navigateToPermissionRationaleActivity()

        assertPermissionRationaleDialogIsVisible(true)

        clicksSettings_denies_leaves()

        // Setting, Permission rationale and Grant dialog should be dismissed
        eventually {
            assertPermissionSettingsVisible(false)
            assertPermissionRationaleDialogIsVisible(false)
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }

        assertAppHasPermission(Manifest.permission.ACCESS_FINE_LOCATION, false)
    }

    private fun navigateToPermissionRationaleActivity_failedShowPermissionRationaleContainer() {
        requestAppPermissionsForNoResult(Manifest.permission.ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(false)
        }
    }

    private fun navigateToPermissionRationaleActivity() {
        requestAppPermissionsForNoResult(Manifest.permission.ACCESS_FINE_LOCATION) {
            assertPermissionRationaleContainerOnGrantDialogIsVisible(true)
            clickPermissionRationaleViewInGrantDialog()
        }
    }

    private fun clickInstallSourceLink() {
        findView(By.res(DATA_SHARING_SOURCE_MESSAGE_ID), true)

        eventually {
            // UiObject2 doesn't expose CharSequence.
            val node =
                uiAutomation.rootInActiveWindow
                    .findAccessibilityNodeInfosByViewId(DATA_SHARING_SOURCE_MESSAGE_ID)[0]
            assertTrue(node.isVisibleToUser)
            val text = node.text as Spanned
            val clickableSpan = text.getSpans(0, text.length, ClickableSpan::class.java)[0]
            // We could pass in null here in Java, but we need an instance in Kotlin.
            doAndWaitForWindowTransition { clickableSpan.onClick(View(context)) }
        }
    }

    private fun clickHelpCenterLink() {
        findView(By.res(LEARN_MORE_MESSAGE_ID), true)

        eventually {
            // UiObject2 doesn't expose CharSequence.
            val node =
                uiAutomation.rootInActiveWindow
                    .findAccessibilityNodeInfosByViewId(LEARN_MORE_MESSAGE_ID)[0]
            assertTrue(node.isVisibleToUser)
            val text = node.text as Spanned
            val clickableSpan = text.getSpans(0, text.length, ClickableSpan::class.java)[0]
            // We could pass in null here in Java, but we need an instance in Kotlin.
            doAndWaitForWindowTransition { clickableSpan.onClick(View(context)) }
        }
    }

    private fun clickSettingsLink() {
        findView(By.res(SETTINGS_MESSAGE_ID), true)

        eventually {
            // UiObject2 doesn't expose CharSequence.
            val node =
                uiAutomation.rootInActiveWindow
                    .findAccessibilityNodeInfosByViewId(SETTINGS_MESSAGE_ID)[0]
            assertTrue(node.isVisibleToUser)
            val text = node.text as Spanned
            val clickableSpan = text.getSpans(0, text.length, ClickableSpan::class.java)[0]
            // We could pass in null here in Java, but we need an instance in Kotlin.
            doAndWaitForWindowTransition { clickableSpan.onClick(View(context)) }
        }
    }

    private fun clicksSettings_doesNothing_leaves() {
        clickSettingsLink()
        eventually { assertPermissionSettingsVisible(true) }
        pressBack()
    }

    private fun clicksSettings_allowsForeground_leaves() {
        clickSettingsLink()
        eventually { clickAllowForegroundInSettings() }
        pressBack()
    }

    private fun clicksSettings_denies_leaves() {
        clickSettingsLink()
        eventually { clicksDenyInSettings() }
        pressBack()
    }

    private fun assertHelpCenterLinkAvailable(expected: Boolean) {
        // Message should always be visible
        findView(By.res(LEARN_MORE_MESSAGE_ID), true)

        // Verify the link is (or isn't) in message
        eventually {
            // UiObject2 doesn't expose CharSequence.
            val node =
                uiAutomation.rootInActiveWindow
                    .findAccessibilityNodeInfosByViewId(LEARN_MORE_MESSAGE_ID)[0]
            assertTrue(node.isVisibleToUser)
            val text = node.text as Spanned
            val clickableSpans = text.getSpans(0, text.length, ClickableSpan::class.java)

            if (expected) {
                assertFalse("Expected help center link, but none found", clickableSpans.isEmpty())
            } else {
                assertTrue("Expected no links, but found one", clickableSpans.isEmpty())
            }
        }
    }

    private fun assertPermissionSettingsVisible(expected: Boolean) {
        findView(By.res(DENY_RADIO_BUTTON), expected = expected)
    }

    private fun assertStoreLinkClickSuccessful(
        installerPackageName: String,
        packageName: String? = null
    ) {
        SystemUtil.runWithShellPermissionIdentity {
            val runningTasks = activityManager!!.getRunningTasks(1)

            assertFalse("Expected runningTasks to not be empty", runningTasks.isEmpty())

            val taskInfo = runningTasks[0]
            val observedIntentAction = taskInfo.baseIntent.action
            val observedPackageName = taskInfo.baseIntent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            val observedInstallerPackageName = taskInfo.topActivity?.packageName

            assertEquals(
                "Unexpected intent action",
                Intent.ACTION_SHOW_APP_INFO,
                observedIntentAction
            )
            assertEquals(
                "Unexpected installer package name",
                installerPackageName,
                observedInstallerPackageName
            )
            assertEquals("Unexpected package name", packageName, observedPackageName)
        }
    }

    private fun assertHelpCenterLinkClickSuccessful() {
        SystemUtil.runWithShellPermissionIdentity {
            val runningTasks = activityManager!!.getRunningTasks(5)

            Log.v(TAG, "# running tasks: ${runningTasks.size}")
            assertFalse("Expected runningTasks to not be empty", runningTasks.isEmpty())

            runningTasks.forEachIndexed { index, runningTaskInfo ->
                Log.v(TAG, "task $index ${runningTaskInfo.baseIntent}")
            }

            val taskInfo = runningTasks[0]
            val observedIntentAction = taskInfo.baseIntent.action
            val observedIntentDataString = taskInfo.baseIntent.dataString
            val observedIntentScheme: String? = taskInfo.baseIntent.scheme

            Log.v(TAG, "task base intent: ${taskInfo.baseIntent}")
            assertEquals("Unexpected intent action", Intent.ACTION_VIEW, observedIntentAction)

            val expectedUrl = getPermissionControllerResString(HELP_CENTER_URL_ID)!!
            assertFalse(observedIntentDataString.isNullOrEmpty())
            assertTrue(observedIntentDataString?.startsWith(expectedUrl) ?: false)

            assertFalse(observedIntentScheme.isNullOrEmpty())
            assertEquals("https", observedIntentScheme)
        }
    }

    companion object {
        private val TAG = PermissionRationaleTest::class.java.simpleName

        private const val DATA_SHARING_SOURCE_MESSAGE_ID =
            "com.android.permissioncontroller:id/data_sharing_source_message"
        private const val LEARN_MORE_MESSAGE_ID =
            "com.android.permissioncontroller:id/learn_more_message"
        private const val SETTINGS_MESSAGE_ID =
            "com.android.permissioncontroller:id/settings_message"

        private const val HELP_CENTER_URL_ID = "data_sharing_help_center_link"
    }
}
