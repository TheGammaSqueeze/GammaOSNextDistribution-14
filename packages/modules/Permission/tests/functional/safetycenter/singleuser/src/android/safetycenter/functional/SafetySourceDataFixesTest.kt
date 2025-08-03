package android.safetycenter.functional

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceStatus
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.preconditions.ScreenLockHelper
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Functional tests for "fixes" applied to Safety Source data received by [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetySourceDataFixesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)

    @Test
    fun lockScreenSource_withoutReplaceLockScreenIconActionFlag_doesntReplace() {
        // Must have a screen lock for the icon action to be set
        assumeTrue(ScreenLockHelper.isDeviceSecure(context))
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.settingsLockScreenSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        SafetyCenterFlags.replaceLockScreenIconAction = false

        safetyCenterManager.refreshSafetySourcesWithPermission(
            SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
        )
        // Skip loading data.
        listener.receiveSafetyCenterData()

        val lockScreenSafetyCenterData = listener.receiveSafetyCenterData()
        val lockScreenEntry = lockScreenSafetyCenterData.entriesOrGroups.first().entry!!
        val entryPendingIntent = lockScreenEntry.pendingIntent!!
        val iconActionPendingIntent = lockScreenEntry.iconAction!!.pendingIntent
        // This test passes for now but will eventually start failing once we introduce the fix in
        // the Settings app. This will warn if the assumption is failed rather than fail, at which
        // point we can remove this test (and potentially even this magnificent hack).
        assumeTrue(iconActionPendingIntent == entryPendingIntent)
    }

    @Test
    fun lockScreenSource_withReplaceLockScreenIconActionFlag_replaces() {
        // Must have a screen lock for the icon action to be set
        assumeTrue(ScreenLockHelper.isDeviceSecure(context))
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.settingsLockScreenSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithPermission(
            SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
        )
        // Skip loading data.
        listener.receiveSafetyCenterData()

        val lockScreenSafetyCenterData = listener.receiveSafetyCenterData()
        val lockScreenEntry = lockScreenSafetyCenterData.entriesOrGroups.first().entry!!
        val entryPendingIntent = lockScreenEntry.pendingIntent!!
        val iconActionPendingIntent = lockScreenEntry.iconAction!!.pendingIntent
        assertThat(iconActionPendingIntent).isNotEqualTo(entryPendingIntent)
    }

    @Test
    fun defaultActionOverride_issue_overridesMatchingActions() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val targetActionId = "TargetActionId"
        SafetyCenterFlags.actionsToOverrideWithDefaultIntent =
            mapOf(SINGLE_SOURCE_ID to setOf(targetActionId, "AdditionalActionId"))

        val originalPendingIntent = pendingIntent(Intent("blah.wrong.INTENT"))
        val dataWithActionToOverride =
            sourceDataBuilder()
                .addIssue(
                    issueBuilder()
                        .clearActions()
                        .addAction(
                            safetySourceTestData.action(
                                id = targetActionId,
                                pendingIntent = originalPendingIntent
                            )
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataWithActionToOverride)

        val overriddenPendingIntent =
            safetyCenterManager
                .getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)!!
                .issues[0]
                .actions[0]
                .pendingIntent
        val expectedPendingIntent =
            pendingIntent(
                Intent(SafetyCenterTestConfigs.ACTION_TEST_ACTIVITY).setPackage(context.packageName)
            )
        assertThat(intentsFilterEqual(overriddenPendingIntent, expectedPendingIntent)).isTrue()
    }
    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun defaultActionOverride_notification_overridesMatchingActions() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val targetActionId = "TargetActionId"
        SafetyCenterFlags.actionsToOverrideWithDefaultIntent =
            mapOf(SINGLE_SOURCE_ID to setOf(targetActionId, "AdditionalActionId"))

        val originalPendingIntent = pendingIntent(Intent("blah.wrong.INTENT"))
        val dataWithNotificationActionToOverride =
            sourceDataBuilder()
                .addIssue(
                    issueBuilder()
                        .setCustomNotification(
                            notification(
                                safetySourceTestData.action(
                                    id = targetActionId,
                                    pendingIntent = originalPendingIntent
                                )
                            )
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataWithNotificationActionToOverride)

        val overriddenPendingIntent =
            safetyCenterManager
                .getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)!!
                .issues[0]
                .customNotification!!
                .actions[0]
                .pendingIntent
        val expectedPendingIntent =
            pendingIntent(
                Intent(SafetyCenterTestConfigs.ACTION_TEST_ACTIVITY).setPackage(context.packageName)
            )
        assertThat(intentsFilterEqual(overriddenPendingIntent, expectedPendingIntent)).isTrue()
    }

    @Test
    fun defaultActionOverride_sameActionIdDifferentSource_doesNotOverride() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        val targetActionId = "TargetActionId"
        SafetyCenterFlags.actionsToOverrideWithDefaultIntent =
            mapOf(SOURCE_ID_1 to setOf(targetActionId, "AdditionalActionId"))

        val originalPendingIntent = pendingIntent(Intent("blah.wrong.INTENT"))
        val dataWithoutActionToOverride =
            sourceDataBuilder()
                .addIssue(
                    issueBuilder()
                        .clearActions()
                        .addAction(
                            safetySourceTestData.action(
                                id = targetActionId,
                                pendingIntent = originalPendingIntent
                            )
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(
            SOURCE_ID_2, // Different source ID
            dataWithoutActionToOverride
        )

        val actualPendingIntent =
            safetyCenterManager
                .getSafetySourceDataWithPermission(SOURCE_ID_2)!!
                .issues[0]
                .actions[0]
                .pendingIntent
        assertThat(intentsFilterEqual(actualPendingIntent, originalPendingIntent)).isTrue()
    }

    @Test
    fun defaultActionOverride_sameSourceDifferentActionId_doesNotOverride() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        SafetyCenterFlags.actionsToOverrideWithDefaultIntent =
            mapOf(SOURCE_ID_1 to setOf("TargetActionId"))

        val originalPendingIntent = pendingIntent(Intent("blah.wrong.INTENT"))
        val dataWithoutActionToOverride =
            sourceDataBuilder()
                .addIssue(
                    issueBuilder()
                        .clearActions()
                        .addAction(
                            safetySourceTestData.action(
                                id = "DifferentActionId",
                                pendingIntent = originalPendingIntent
                            )
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SOURCE_ID_1, dataWithoutActionToOverride)

        val actualPendingIntent =
            safetyCenterManager
                .getSafetySourceDataWithPermission(SOURCE_ID_1)!!
                .issues[0]
                .actions[0]
                .pendingIntent
        assertThat(intentsFilterEqual(actualPendingIntent, originalPendingIntent)).isTrue()
    }

    @Test
    fun defaultActionOverride_noDefaultIntent_doesNotOverride() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceInvalidIntentConfig)
        val targetActionId = "TargetActionId"
        SafetyCenterFlags.actionsToOverrideWithDefaultIntent =
            mapOf(SINGLE_SOURCE_ID to setOf(targetActionId, "AdditionalActionId"))

        val originalPendingIntent = pendingIntent(Intent("blah.wrong.INTENT"))
        val dataWithActionToOverride =
            sourceDataBuilder()
                .addIssue(
                    issueBuilder()
                        .clearActions()
                        .addAction(
                            safetySourceTestData.action(
                                id = targetActionId,
                                pendingIntent = originalPendingIntent
                            )
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataWithActionToOverride)

        val actualPendingIntent =
            safetyCenterManager
                .getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)!!
                .issues[0]
                .actions[0]
                .pendingIntent
        assertThat(intentsFilterEqual(actualPendingIntent, originalPendingIntent)).isTrue()
    }

    private fun issueBuilder() = safetySourceTestData.defaultInformationIssueBuilder()

    private fun pendingIntent(intent: Intent) =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private fun sourceDataBuilder() =
            SafetySourceData.Builder()
                .setStatus(
                    SafetySourceStatus.Builder("OK", "Blah", SEVERITY_LEVEL_INFORMATION).build()
                )

        @RequiresApi(UPSIDE_DOWN_CAKE)
        private fun notification(action: SafetySourceIssue.Action) =
            SafetySourceIssue.Notification.Builder("Blah", "Bleh").addAction(action).build()

        private fun intentsFilterEqual(
            actualPendingIntent: PendingIntent,
            expectedPendingIntent: PendingIntent?
        ) =
            callWithShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT") {
                actualPendingIntent.intentFilterEquals(expectedPendingIntent)
            }
    }
}
