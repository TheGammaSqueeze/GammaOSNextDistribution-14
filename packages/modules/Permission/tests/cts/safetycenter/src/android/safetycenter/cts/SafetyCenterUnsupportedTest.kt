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

package android.safetycenter.cts

import android.content.Context
import android.os.UserManager
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetySourceErrorDetails
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterEnabledChangedReceiver
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestListener
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.android.safetycenter.testing.SettingsPackage.getSettingsPackageName
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.UiTestHelper.waitDisplayed
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for our APIs and UI on devices that do not support Safety Center. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterUnsupportedTest {

    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    @get:Rule(order = 1)
    val supportsSafetyCenterRule = SupportsSafetyCenterRule(context, requireSupportIs = false)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)
    @get:Rule(order = 3) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 4) val freezeRotationRule = FreezeRotationRule()

    @Test
    fun launchActivity_opensSettings() {
        context.launchSafetyCenterActivity {
            waitDisplayed(By.pkg(context.getSettingsPackageName()))
        }
    }

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsFalse() {
        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagDisabled_returnsFalse() {
        safetyCenterTestHelper.setEnabled(false)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.isSafetyCenterEnabled }
    }

    @Test
    fun setAndGetSafetySourceData_doesntSetData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetySourceData(
                SINGLE_SOURCE_ID,
                safetySourceTestData.unspecified,
                EVENT_SOURCE_STATE_CHANGED
            )
        }
    }

    @Test
    fun getSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.getSafetySourceData(SINGLE_SOURCE_ID)
        }
    }

    @Test
    fun reportSafetySourceError_doesntCallListener() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun reportSafetySourceError_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.reportSafetySourceError(
                SINGLE_SOURCE_ID,
                SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
            )
        }
    }

    @Test
    fun safetyCenterEnabledChanged_withImplicitReceiver_doesntCallReceiver() {
        // Implicit broadcast is only sent to system user.
        assumeTrue(context.getSystemService(UserManager::class.java)!!.isSystemUser)
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false,
                TIMEOUT_SHORT
            )
        }
        enabledChangedReceiver.unregister()
    }

    @Test
    fun safetyCenterEnabledChanged_withSourceReceiver_doesntCallReceiver() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_doesntRefreshSources() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                timeout = TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
    }

    @Test
    fun getSafetyCenterConfig_isNull() {
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNull()
    }

    @Test
    fun getSafetyCenterConfig_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterConfig }
    }

    @Test
    fun getSafetyCenterData_returnsDefaultData() {
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(SafetyCenterTestData.DEFAULT)
    }

    @Test
    fun getSafetyCenterData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterData }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalled() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        val listener = SafetyCenterTestListener()

        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = SafetyCenterTestListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(directExecutor(), listener)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNotCalled() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_doesntCallListenerOrDismiss() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyCenterIssue("bleh")
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_doesntCallListenerOrExecute() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueAction("bleh", "blah")
        }
    }

    @Test
    fun clearAllSafetySourceDataForTests_doesntClearData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )
        val apiSafetySourceDataBeforeClearing =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        val apiSafetySourceDataAfterClearing =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceDataAfterClearing).isEqualTo(apiSafetySourceDataBeforeClearing)
    }

    @Test
    fun clearAllSafetySourceDataForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearAllSafetySourceDataForTests()
        }
    }

    @Test
    fun setSafetyCenterConfigForTests_doesntSetConfig() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNull()
    }

    @Test
    fun setSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetyCenterConfigForTests(
                safetyCenterTestConfigs.singleSourceConfig
            )
        }
    }

    @Test
    fun clearSafetyCenterConfigForTests_doesntClearConfig() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )
        val configBeforeClearing = safetyCenterManager.getSafetyCenterConfigWithPermission()

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        val configAfterClearing = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(configAfterClearing).isEqualTo(configBeforeClearing)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearSafetyCenterConfigForTests()
        }
    }
}
