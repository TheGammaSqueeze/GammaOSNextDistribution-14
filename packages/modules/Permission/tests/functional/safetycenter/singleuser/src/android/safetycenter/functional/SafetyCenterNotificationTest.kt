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

package android.safetycenter.functional

import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.SafetySourceIssue
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.safetycenter.pendingintents.PendingIntentSender
import com.android.safetycenter.testing.Coroutines
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.NotificationCharacteristics
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.executeBlockAndExit
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_5
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.ISSUE_TYPE_ID
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.android.safetycenter.testing.StatusBarNotificationWithChannel
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.TestNotificationListener
import com.android.safetycenter.testing.UiTestHelper.waitSourceIssueDisplayed
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Notification-related tests for [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterNotificationTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager =
        requireNotNull(context.getSystemService(SafetyCenterManager::class.java)) {
            "Could not get SafetyCenterManager"
        }

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2)
    val safetyCenterTestRule =
        SafetyCenterTestRule(safetyCenterTestHelper, withNotifications = true)
    @get:Rule(order = 3) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 4) val freezeRotationRule = FreezeRotationRule()

    @Before
    fun enableNotificationsForTestSourceBeforeTest() {
        SafetyCenterFlags.notificationsEnabled = true
        setFlagsForImmediateNotifications(SINGLE_SOURCE_ID)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun setSafetySourceData_withNoIssue_noNotification() {
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withoutImmediateNotificationBehavior_noNotification() {
        SafetyCenterFlags.immediateNotificationBehaviorIssues = emptySet()

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withoutNotificationsAllowedSource_noNotification() {
        SafetyCenterFlags.notificationsAllowedSources = emptySet()

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_noNotification() {
        SafetyCenterFlags.notificationsEnabled = false

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withNotificationBehaviorNever_noNotification() {
        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder()
                        .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withNotificationBehaviorDelay_noImmediateNotification() {
        SafetyCenterFlags.notificationsMinDelay = Duration.ofDays(1)
        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder()
                        .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withNotificationBehaviorDelay_sendsNotificationAfterDelay() {
        SafetyCenterFlags.notificationsMinDelay = Duration.ofDays(1)
        val delayedNotificationIssue =
            safetySourceTestData
                .defaultRecommendationIssueBuilder("Notify later", "This is not urgent.")
                .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED)
                .build()
        val neverNotifyIssue =
            safetySourceTestData
                .defaultInformationIssueBuilder()
                .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER)
                .build()
        val data1 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(delayedNotificationIssue)
                .build()
        val data2 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(delayedNotificationIssue)
                .addIssue(neverNotifyIssue)
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)
        TestNotificationListener.waitForZeroNotifications()

        SafetyCenterFlags.notificationsMinDelay = TIMEOUT_SHORT

        // Sending new data causes Safety Center to recompute which issues to send notifications
        // about and this should now include the delayed issue sent in data1 above.
        Thread.sleep(TIMEOUT_SHORT.toMillis())
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Notify later",
                text = "This is not urgent.",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withNotificationBehaviorDelayOfZero_sendsNotificationImmediately() {
        SafetyCenterFlags.immediateNotificationBehaviorIssues = emptySet()
        SafetyCenterFlags.notificationsMinDelay = Duration.ofSeconds(0)
        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Notify immediately", "This is urgent!")
                        .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Notify immediately",
                text = "This is urgent!",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withNotificationBehaviorImmediately_sendsNotification() {
        SafetyCenterFlags.immediateNotificationBehaviorIssues = emptySet()
        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Notify immediately", "This is urgent!")
                        .setNotificationBehavior(
                            SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Notify immediately",
                text = "This is urgent!",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    fun setSafetySourceData_withNotificationsAllowedForSourceByFlag_sendsNotification() {
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ID)
        val data = safetySourceTestData.recommendationWithAccountIssue

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Recommendation issue title",
                text = "Recommendation issue summary",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    fun setSafetySourceData_issueWithTwoActions_notificationWithTwoActions() {
        val intent1 = safetySourceTestData.createTestActivityRedirectPendingIntent(identifier = "1")
        val intent2 = safetySourceTestData.createTestActivityRedirectPendingIntent(identifier = "2")

        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder()
                        .clearActions()
                        .addAction(
                            SafetySourceIssue.Action.Builder("action1", "Action 1", intent1).build()
                        )
                        .addAction(
                            SafetySourceIssue.Action.Builder("action2", "Action 2", intent2).build()
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Recommendation issue title",
                text = "Recommendation issue summary",
                actions = listOf("Action 1", "Action 2")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withNotificationsAllowedForSourceByConfig_sendsNotification() {
        SafetyCenterFlags.notificationsAllowedSources = emptySet()
        SafetyCenterFlags.immediateNotificationBehaviorIssues = emptySet()
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.singleSourceConfig(
                safetyCenterTestConfigs
                    .dynamicSafetySourceBuilder("MyNotifiableSource")
                    .setNotificationsAllowed(true)
                    .build()
            )
        )
        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Notify immediately", "This is urgent!")
                        .setNotificationBehavior(
                            SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData("MyNotifiableSource", data)

        TestNotificationListener.waitForSingleNotification()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withCustomNotification_usesCustomValues() {
        val intent1 = safetySourceTestData.createTestActivityRedirectPendingIntent(identifier = "1")
        val intent2 = safetySourceTestData.createTestActivityRedirectPendingIntent(identifier = "2")

        val notification =
            SafetySourceIssue.Notification.Builder("Custom title", "Custom text")
                .addAction(
                    SafetySourceIssue.Action.Builder("action1", "Custom action 1", intent1).build()
                )
                .addAction(
                    SafetySourceIssue.Action.Builder("action2", "Custom action 2", intent2).build()
                )
                .build()

        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Default title", "Default text")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "default_action",
                                    "Default action",
                                    safetySourceTestData.createTestActivityRedirectPendingIntent()
                                )
                                .build()
                        )
                        .setCustomNotification(notification)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Custom title",
                text = "Custom text",
                actions = listOf("Custom action 1", "Custom action 2")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_withEmptyCustomActions_notificationHasNoActions() {
        val notification =
            SafetySourceIssue.Notification.Builder("Custom title", "Custom text")
                .clearActions()
                .build()

        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Default title", "Default text")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "default_action",
                                    "Default action",
                                    safetySourceTestData.createTestActivityRedirectPendingIntent()
                                )
                                .build()
                        )
                        .setCustomNotification(notification)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Custom title",
                text = "Custom text",
                actions = emptyList()
            )
        )
    }

    @Test
    fun setSafetySourceData_twiceWithSameIssueId_updatesNotification() {
        val data1 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Initial", "Blah")
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)

        val initialNotification =
            TestNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Initial",
                    text = "Blah",
                    actions = listOf("See issue")
                )
            )

        val data2 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "new_action",
                                    "New action",
                                    safetySourceTestData.createTestActivityRedirectPendingIntent(
                                        identifier = "new_action"
                                    )
                                )
                                .build()
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        val revisedNotification =
            TestNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Revised",
                    text = "Different",
                    actions = listOf("See issue", "New action")
                )
            )
        assertThat(initialNotification.statusBarNotification.key)
            .isEqualTo(revisedNotification.statusBarNotification.key)
    }

    @Test
    fun setSafetySourceData_twiceWithExactSameIssue_doNotNotifyTwice() {
        val data = safetySourceTestData.recommendationWithAccountIssue

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotification()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForZeroNotificationEvents()
    }

    @Test
    fun setSafetySourceData_twiceRemovingAnIssue_cancelsNotification() {
        val data1 = safetySourceTestData.recommendationWithAccountIssue
        val data2 = safetySourceTestData.information

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)

        TestNotificationListener.waitForSingleNotification()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        TestNotificationListener.waitForZeroNotifications()
    }

    // TODO(b/284271124): Decide what to do with existing notifications when flag flipped off
    @Test
    fun setSafetySourceData_removingAnIssue_afterFlagTurnedOff_noNotificationChanges() {
        val data1 = safetySourceTestData.recommendationWithAccountIssue
        val data2 = safetySourceTestData.information

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)

        TestNotificationListener.waitForSingleNotification()

        SafetyCenterFlags.notificationsEnabled = false
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        TestNotificationListener.waitForZeroNotificationEvents()
    }

    @Test
    fun reportSafetySourceError_sourceWithNotification_cancelsNotification() {
        val data = safetySourceTestData.recommendationWithAccountIssue
        val error =
            SafetySourceErrorDetails(
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
            )

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotification()

        safetyCenterManager.reportSafetySourceErrorWithPermission(SINGLE_SOURCE_ID, error)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withDismissedIssueId_doesNotNotify() {
        // We use two different issues/data here to ensure that the reason the notification is not
        // posted the second time is specifically because of the dismissal. Notifications are not
        // re-posted/updated for unchanged issues but that functionality is different and tested
        // separately.
        val data1 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Initial", "Blah")
                        .build()
                )
                .build()
        val data2 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)

        val notificationWithChannel =
            TestNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Initial",
                    text = "Blah",
                    actions = listOf("See issue")
                )
            )

        TestNotificationListener.cancelAndWait(notificationWithChannel.statusBarNotification.key)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withDismissedIssueIdButResurfaceDelayZero_doesNotify() {
        SafetyCenterFlags.notificationResurfaceInterval = Duration.ZERO
        val data1 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Initial", "Blah")
                        .build()
                )
                .build()
        val data2 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .build()
                )
                .build()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        TestNotificationListener.cancelAndWait(notificationWithChannel.statusBarNotification.key)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        TestNotificationListener.waitForSingleNotification()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_duplicateIssues_sendsOneNotification() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        setFlagsForImmediateNotifications(SOURCE_ID_1, SOURCE_ID_5)

        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Critical issue title",
                "Critical issue summary",
                actions = listOf("Solve issue"),
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_duplicateIssueOfLowerSeverityDismissed_sendsNotification() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        setFlagsForImmediateNotifications(SOURCE_ID_1, SOURCE_ID_5)

        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )

        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()

        TestNotificationListener.cancelAndWait(notificationWithChannel.statusBarNotification.key)

        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Critical issue title",
                "Critical issue summary",
                actions = listOf("Solve issue"),
            )
        )
    }

    @Test
    fun setSafetySourceData_withInformationIssue_lowImportanceBlockableNotification() {
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Information issue title",
                "Information issue summary",
                actions = listOf("Review"),
                importance = NotificationManager.IMPORTANCE_LOW,
                blockable = true
            )
        )
    }

    @Test
    fun setSafetySourceData_withRecommendationIssue_defaultImportanceUnblockableNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Recommendation issue title",
                "Recommendation issue summary",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                actions = listOf("See issue"),
                blockable = false
            )
        )
    }

    @Test
    fun setSafetySourceData_withCriticalIssue_highImportanceUnblockableNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Critical issue title",
                "Critical issue summary",
                actions = listOf("Solve issue"),
                importance = NotificationManager.IMPORTANCE_HIGH,
                blockable = false
            )
        )
    }

    @Test
    fun dismissSafetyCenterIssue_dismissesNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForSingleNotification()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(
                SINGLE_SOURCE_ID,
                SafetySourceTestData.RECOMMENDATION_ISSUE_ID
            )
        )

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun dismissingNotification_doesNotUpdateSafetyCenterData() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        // Add the listener after setting the initial data so that we don't need to consume/receive
        // an update for that
        val listener = safetyCenterTestHelper.addListener()

        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()

        TestNotificationListener.cancelAndWait(notificationWithChannel.statusBarNotification.key)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun dismissingNotification_withDuplicateIssues_allDismissed() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        setFlagsForImmediateNotifications(SOURCE_ID_1, SOURCE_ID_5)

        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )

        val notificationWithChannel =
            TestNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    "Critical issue title",
                    "Critical issue summary",
                    actions = listOf("Solve issue"),
                )
            )

        // We dismiss the notification and then clear the corresponding issue belonging to
        // SOURCE_ID_1. This ensures that the only reason no notification is shown for the issue
        // belonging to SOURCE_ID_5 is that the dismissal data was copied.
        TestNotificationListener.cancelAndWait(notificationWithChannel.statusBarNotification.key)
        safetyCenterTestHelper.setData(SOURCE_ID_1, SafetySourceTestData.issuesOnly())

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun clearSafetySourceData_cancelsAllNotifications() {
        val data = safetySourceTestData.recommendationWithAccountIssue

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotification()

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun sendActionPendingIntent_successful_updatesListener() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        sendActionPendingIntentAndWaitWithPermission(action)

        val listenerData1 = listener.receiveSafetyCenterData()
        assertThat(listenerData1.inFlightActions).hasSize(1)
        val listenerData2 = listener.receiveSafetyCenterData()
        assertThat(listenerData2.issues).isEmpty()
        assertThat(listenerData2.status.severityLevel)
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
    }

    @Test
    fun sendActionPendingIntent_successfulNoSuccessMessage_removesNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        sendActionPendingIntentAndWaitWithPermission(action)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun sendActionPendingIntent_successfulWithSuccessMessage_successNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingIssueWithSuccessMessage
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        sendActionPendingIntentAndWaitWithPermission(action)

        TestNotificationListener.waitForSuccessNotification("Issue solved")
    }

    @Test
    fun successNotification_notificationHasAutoCancel() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingIssueWithSuccessMessage
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        sendActionPendingIntentAndWaitWithPermission(action)

        TestNotificationListener.waitForSuccessNotification("Issue solved") {
            assertThat(it.hasAutoCancel()).isTrue()
        }
    }

    // TODO(b/284271124): Decide what to do with existing notifications when flag flipped off
    @Test
    fun sendActionPendingIntent_flagDisabled_pendingIntentNotSentToSource() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingIssueWithSuccessMessage
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        SafetyCenterFlags.notificationsEnabled = false

        assertFailsWith(TimeoutCancellationException::class) {
            sendActionPendingIntentAndWaitWithPermission(action, timeout = TIMEOUT_SHORT)
        }
    }

    @Test
    fun sendActionPendingIntent_sourceStateChangedSafetyEvent_successNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingIssueWithSuccessMessage
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(
                safetySourceTestData.information,
                overrideSafetyEvent =
                    SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
            )
        )

        sendActionPendingIntentAndWaitWithPermission(action)

        TestNotificationListener.waitForSuccessNotification("Issue solved")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun sendActionPendingIntent_actionIdDiffersFromIssueActionId_successNotification() {
        val notification =
            SafetySourceIssue.Notification.Builder("Custom title", "Custom text")
                .addAction(
                    SafetySourceIssue.Action.Builder(
                            "notification_action_id",
                            "Solve now!",
                            safetySourceTestData.resolvingActionPendingIntent(
                                sourceIssueActionId = "notification_action_id"
                            )
                        )
                        .setWillResolve(true)
                        .setSuccessMessage("Solved via notification action :)")
                        .build()
                )
                .build()
        val data =
            safetySourceTestData
                .defaultCriticalDataBuilder()
                .clearIssues()
                .addIssue(
                    safetySourceTestData
                        .defaultCriticalResolvingIssueBuilder()
                        .clearActions()
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "issue_action_id",
                                    "Default action",
                                    safetySourceTestData.resolvingActionPendingIntent(
                                        sourceIssueActionId = "issue_action_id"
                                    )
                                )
                                .setWillResolve(true)
                                .setSuccessMessage("Solved via issue action :(")
                                .build()
                        )
                        .setCustomNotification(notification)
                        .setNotificationBehavior(
                            SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        sendActionPendingIntentAndWaitWithPermission(action)

        TestNotificationListener.waitForSuccessNotification("Solved via notification action :)")
    }

    @Test
    fun sendActionPendingIntent_error_updatesListenerDoesNotRemoveNotification() {
        // Here we cause a notification with an action to be posted and prepare the fake receiver
        // to resolve that action successfully.
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)
        val listener = safetyCenterTestHelper.addListener()

        sendActionPendingIntentAndWaitWithPermission(action)

        val listenerData1 = listener.receiveSafetyCenterData()
        assertThat(listenerData1.inFlightActions).hasSize(1)
        val listenerData2 = listener.receiveSafetyCenterData()
        assertThat(listenerData2.issues).hasSize(1)
        assertThat(listenerData2.inFlightActions).isEmpty()
        assertThat(listenerData2.status.severityLevel)
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        TestNotificationListener.waitForSingleNotification()
    }

    @Test
    fun sendContentPendingIntent_singleIssue_opensSafetyCenterWithIssueVisible() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithDeviceIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()

        sendContentPendingIntent(notificationWithChannel) {
            waitSourceIssueDisplayed(safetySourceTestData.recommendationDeviceIssue)
        }
    }

    @Test
    fun sendContentPendingIntent_anotherHigherSeverityIssue_opensSafetyCenterWithIssueVisible() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        setFlagsForImmediateNotifications(SOURCE_ID_1)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.recommendationWithDeviceIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()

        sendContentPendingIntent(notificationWithChannel) {
            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceTestData.recommendationDeviceIssue)
        }
    }

    @Test
    fun whenGreenIssue_notificationHasAutoCancel() {
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()

        assertThat(notificationWithChannel.statusBarNotification.hasAutoCancel()).isTrue()
    }

    @Test
    fun whenNotGreenIssue_notificationDoesntHaveAutoCancel() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithDeviceIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()

        assertThat(notificationWithChannel.statusBarNotification.hasAutoCancel()).isFalse()
    }

    private companion object {
        val SafetyCenterData.inFlightActions: List<SafetyCenterIssue.Action>
            get() = issues.flatMap { it.actions }.filter { it.isInFlight }

        fun sendActionPendingIntentAndWaitWithPermission(
            action: Notification.Action,
            timeout: Duration = Coroutines.TIMEOUT_LONG
        ) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                PendingIntentSender.send(action.actionIntent)
                // Sending the action's PendingIntent above is asynchronous and we need to wait for
                // it to be received by the fake receiver below.
                SafetySourceReceiver.receiveResolveAction(timeout)
            }
        }

        fun setFlagsForImmediateNotifications(vararg sourceIds: String) {
            SafetyCenterFlags.notificationsAllowedSources = sourceIds.toSet()
            SafetyCenterFlags.immediateNotificationBehaviorIssues =
                sourceIds.map { "$it/$ISSUE_TYPE_ID" }.toSet()
        }

        fun StatusBarNotification.hasAutoCancel(): Boolean {
            val autoCancelMask = notification.flags and Notification.FLAG_AUTO_CANCEL
            return autoCancelMask != 0
        }

        fun sendContentPendingIntent(
            statusBarNotificationWithChannel: StatusBarNotificationWithChannel,
            andExecuteBlock: () -> Unit = {}
        ) {
            val contentIntent =
                statusBarNotificationWithChannel.statusBarNotification.notification.contentIntent
            executeBlockAndExit(
                launchActivity = { PendingIntentSender.send(contentIntent) },
                block = andExecuteBlock
            )
        }
    }
}
