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

package android.safetycenter.functional.ui

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_3
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_4
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_5
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.RECOMMENDATION_ISSUE_ID
import com.android.safetycenter.testing.SettingsPackage.getSettingsPackageName
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.UiTestHelper.MORE_ISSUES_LABEL
import com.android.safetycenter.testing.UiTestHelper.RESCAN_BUTTON_LABEL
import com.android.safetycenter.testing.UiTestHelper.clickConfirmDismissal
import com.android.safetycenter.testing.UiTestHelper.clickDismissIssueCard
import com.android.safetycenter.testing.UiTestHelper.clickMoreIssuesCard
import com.android.safetycenter.testing.UiTestHelper.resetRotation
import com.android.safetycenter.testing.UiTestHelper.rotate
import com.android.safetycenter.testing.UiTestHelper.setAnimationsEnabled
import com.android.safetycenter.testing.UiTestHelper.waitAllTextDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitAllTextNotDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitButtonDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitCollapsedIssuesDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitExpandedIssuesDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitPageTitleDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitSourceDataDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitSourceIssueDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitSourceIssueNotDisplayed
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Functional tests for the Safety Center Activity. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterActivityTest {

    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)
    @get:Rule(order = 3) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 4) val freezeRotationRule = FreezeRotationRule()

    @After
    fun clearDataAfterTest() {
        getUiDevice().resetRotation()
    }

    @Test
    fun launchActivity_allowingSettingsTrampoline() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val dataToDisplay = safetySourceTestData.criticalWithResolvingGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

        context.launchSafetyCenterActivity(preventTrampolineToSettings = false) {
            waitSourceDataDisplayed(dataToDisplay)
        }
    }

    @Test
    fun launchActivity_displaysStaticSources() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.staticSourcesConfig)

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.staticSourceGroup1.titleResId),
                context.getString(safetyCenterTestConfigs.staticSource1.titleResId),
                context.getString(safetyCenterTestConfigs.staticSource1.summaryResId),
                context.getString(safetyCenterTestConfigs.staticSourceGroup2.titleResId),
                context.getString(safetyCenterTestConfigs.staticSource2.titleResId),
                context.getString(safetyCenterTestConfigs.staticSource2.summaryResId)
            )
        }
    }

    @Test
    fun launchActivity_displaysSafetyData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val dataToDisplay = safetySourceTestData.criticalWithResolvingGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

        context.launchSafetyCenterActivity { waitSourceDataDisplayed(dataToDisplay) }
    }

    @Test
    fun launchActivity_displaysCollapsedGroupsOfMultipleSource() {
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(
                SOURCE_ID_1,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId),
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.summaryResId),
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup3.titleResId),
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup3.summaryResId)
            )
            waitAllTextNotDisplayed(
                SAFETY_SOURCE_1_TITLE,
                SAFETY_SOURCE_1_SUMMARY,
                SAFETY_SOURCE_2_TITLE,
                SAFETY_SOURCE_2_SUMMARY,
                SAFETY_SOURCE_4_TITLE,
                SAFETY_SOURCE_4_SUMMARY,
                SAFETY_SOURCE_5_TITLE,
                SAFETY_SOURCE_5_SUMMARY
            )
        }
    }

    @Test
    fun launchActivity_displaysPrioritizedGroupSummary() {
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(
                SOURCE_ID_1,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = true
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = true
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = true
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId),
                SAFETY_SOURCE_2_SUMMARY,
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup3.titleResId),
                SAFETY_SOURCE_5_SUMMARY
            )
            waitAllTextNotDisplayed(
                SAFETY_SOURCE_1_TITLE,
                SAFETY_SOURCE_2_TITLE,
                SAFETY_SOURCE_1_SUMMARY,
                SAFETY_SOURCE_4_TITLE,
                SAFETY_SOURCE_5_TITLE,
                SAFETY_SOURCE_4_SUMMARY
            )
        }
    }

    @Test
    fun launchActivity_displaysGroupsOfSingleSourceAsEntity() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSource3.titleResId)
            )
            waitAllTextNotDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup2.titleResId)
            )
        }
    }

    @Test
    fun updatingSafetySourceData_updatesDisplayedSafetyData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        context.launchSafetyCenterActivity {
            val dataToDisplay = safetySourceTestData.recommendationWithGeneralIssue
            safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

            waitSourceDataDisplayed(dataToDisplay)
        }
    }

    @Test
    fun updatingSafetySourceData_withoutSubtitle_newIssueWithSubtitle() {
        val initialDataToDisplay = safetySourceTestData.informationWithIssue
        val updatedDataToDisplay = safetySourceTestData.informationWithSubtitleIssue

        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, initialDataToDisplay)

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceTestData.informationIssue)

            safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, updatedDataToDisplay)

            waitSourceIssueDisplayed(safetySourceTestData.informationIssueWithSubtitle)
        }
    }

    @Test
    fun updatingSafetySourceData_withSubtitle_newIssueWithoutSubtitle() {
        val initialDataToDisplay = safetySourceTestData.informationWithSubtitleIssue
        val updatedDataToDisplay = safetySourceTestData.informationWithIssue

        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, initialDataToDisplay)

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceTestData.informationIssueWithSubtitle)

            safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, updatedDataToDisplay)

            waitAllTextNotDisplayed(safetySourceTestData.informationIssueWithSubtitle.subtitle)
            waitSourceIssueDisplayed(safetySourceTestData.informationIssue)
        }
    }

    @Test
    fun entryListWithEntryGroup_informationState_hasContentDescription() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.information)
        safetyCenterTestHelper.setData(SOURCE_ID_2, safetySourceTestData.information)
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.information)

        context.launchSafetyCenterActivity {
            // Verify content description for the collapsed entry group, and click on it to expand
            waitDisplayed(By.desc("List. OK. OK")) { it.click() }

            // Verify content descriptions for the expanded group header and entry list item
            waitAllTextDisplayed("OK")
            waitDisplayed(By.desc("List item. Ok title. Ok summary"))
        }
    }

    @Test
    fun entryListWithEntryGroup_recommendationState_hasActionsNeededContentDescription() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_2, safetySourceTestData.information)
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.information)

        context.launchSafetyCenterActivity {
            // Verify content description for the collapsed entry group, and click on it to expand.
            waitDisplayed(By.desc("List. OK. Actions needed. Recommendation summary")) {
                it.click()
            }

            // Verify content descriptions for the expanded group header and entry list items.
            waitAllTextDisplayed("OK")
            waitDisplayed(By.desc("List item. Recommendation title. Recommendation summary"))
            waitDisplayed(By.desc("List item. Ok title. Ok summary"))
        }
    }

    @Test
    fun entryListWithEntryGroup_clickingAnUnclickableDisabledEntry_doesNothing() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesConfigWithSourceWithInvalidIntent
        )
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.unspecified)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitDisplayed(By.text("Unspecified title")) { it.click() }
            // Confirm that clicking on the entry neither redirects to any other screen nor
            // collapses the group.
            waitAllTextDisplayed("Unspecified title")
        }
    }

    @Test
    fun entryListWithEntryGroup_unclickableDisabledEntry_hasContentDescription() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesConfigWithSourceWithInvalidIntent
        )
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.unspecified)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("List. OK. No info yet")) { it.click() }
            // Make sure that the content description is correctly set for the unclickable disabled
            // entries so that the talkback to works properly.
            waitDisplayed(By.desc("List item. Unspecified title. Unspecified summary"))
        }
    }

    @Test
    fun entryListWithEntryGroup_clickingAClickableDisabledEntry_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.unspecifiedDisabledWithTestActivityRedirect
        )

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitDisplayed(By.text("Clickable disabled title")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    fun entryListWithEntryGroup_clickableDisabledEntry_hasContentDescription() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.unspecifiedDisabledWithTestActivityRedirect
        )

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("List. OK. No info yet")) { it.click() }
            // Make sure that the content description is correctly set for the clickable disabled
            // entry so that the talkback to works properly.
            waitDisplayed(
                By.desc("List item. Clickable disabled title. Clickable disabled summary")
            )
        }
    }

    @Test
    fun entryListWithSingleSource_informationState_hasContentDescription() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        context.launchSafetyCenterActivity {
            // Verify content description for the individual entry
            waitDisplayed(By.desc("Ok title. Ok summary"))
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheDefaultEntry_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitDisplayed(By.text("OK"))
        }
    }

    @Test
    fun entryListWithSingleSource_clickingDefaultEntryImplicitIntent_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.implicitIntentSingleSourceConfig)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheUpdatedEntry_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("Ok title")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitDisplayed(By.text("Ok title"))
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheIconActionButton_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.informationWithIconAction
        )

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Information")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitDisplayed(By.text("Ok title"))
        }
    }

    @Test
    fun staticSource_clickingTheEntry_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.staticSourcesConfig)

        context.launchSafetyCenterActivity {
            waitDisplayed(
                By.text(context.getString(safetyCenterTestConfigs.staticSource1.titleResId))
            ) {
                it.click()
            }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.staticSource1.titleResId)
            )
        }
    }

    @Test
    fun issueCard_noAttribution_hasProperContentDescriptions() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceNoGroupTitleConfig)

        val issue = safetySourceTestData.recommendationGeneralIssue
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(issue)
        )

        context.launchSafetyCenterActivity { waitDisplayed(By.desc("Alert. ${issue.title}")) }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun issueCard_withAttribution_hasProperContentDescriptions() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val data = safetySourceTestData.informationWithIssueWithAttributionTitle
        val issue = data.issues[0]

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Alert. ${issue.attributionTitle}"))
        }
    }

    @Test
    fun issueCard_greenIssue_noDismissalConfirmationAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        context.launchSafetyCenterActivity {
            clickDismissIssueCard()

            waitSourceIssueNotDisplayed(safetySourceTestData.informationIssue)
            waitSourceDataDisplayed(safetySourceTestData.information)
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_dismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        context.launchSafetyCenterActivity {
            clickDismissIssueCard()
            waitAllTextDisplayed("Dismiss this alert?")
            clickConfirmDismissal()

            waitSourceIssueNotDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_afterRotation_dismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        context.launchSafetyCenterActivity {
            clickDismissIssueCard()
            waitAllTextDisplayed(
                "Dismiss this alert?",
                "Review your security and privacy settings any time to add more protection"
            )

            getUiDevice().rotate()

            waitAllTextDisplayed(
                "Dismiss this alert?",
                "Review your security and privacy settings any time to add more protection"
            )
            clickConfirmDismissal()

            waitSourceIssueNotDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_cancels() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        context.launchSafetyCenterActivity {
            clickDismissIssueCard()
            waitAllTextDisplayed("Dismiss this alert?")
            waitButtonDisplayed("Cancel") { it.click() }

            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_afterRotation_cancels() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        context.launchSafetyCenterActivity {
            clickDismissIssueCard()
            waitAllTextDisplayed("Dismiss this alert?")

            getUiDevice().rotate()

            waitAllTextDisplayed("Dismiss this alert?")
            waitButtonDisplayed("Cancel") { it.click() }

            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_resolveIssue_successConfirmationShown() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        // Set the initial data for the source
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingIssueWithSuccessMessage
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingActionWithSuccessMessage
            waitButtonDisplayed(action.label) {
                // Re-enable animations for this test as this is needed to show the success message.
                setAnimationsEnabled(true)
                it.click()
            }

            // Success message should show up if issue marked as resolved
            val successMessage = action.successMessage
            waitAllTextDisplayed(successMessage)
        }
    }

    @Test
    fun issueCard_resolveIssue_issueDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        // Set the initial data for the source
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingIssueWithSuccessMessage
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingActionWithSuccessMessage
            waitButtonDisplayed(action.label) { it.click() }

            // Wait for success message to go away, verify issue no longer displayed
            val successMessage = action.successMessage
            waitAllTextNotDisplayed(successMessage)
            waitSourceIssueNotDisplayed(
                safetySourceTestData.criticalResolvingIssueWithSuccessMessage
            )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun issueCard_resolveIssue_withDialogClickYes_resolves() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssueWithConfirmation
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingActionWithConfirmation
            waitButtonDisplayed(action.label) { it.click() }

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)
            waitButtonDisplayed(SafetySourceTestData.CONFIRMATION_YES) { it.click() }

            waitSourceIssueNotDisplayed(safetySourceTestData.criticalResolvingIssueWithConfirmation)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun issueCard_resolveIssue_withDialog_rotates_clickYes_resolves() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssueWithConfirmation
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingActionWithConfirmation
            waitButtonDisplayed(action.label) { it.click() }

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)

            getUiDevice().rotate()

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)
            waitButtonDisplayed(SafetySourceTestData.CONFIRMATION_YES) { it.click() }

            waitSourceIssueNotDisplayed(safetySourceTestData.criticalResolvingIssueWithConfirmation)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun issueCard_resolveIssue_withDialogClicksNo_cancels() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssueWithConfirmation
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingActionWithConfirmation
            waitButtonDisplayed(action.label) { it.click() }

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)
            waitButtonDisplayed(SafetySourceTestData.CONFIRMATION_NO) { it.click() }

            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingIssueWithConfirmation)
        }
    }

    @Test
    fun issueCard_resolveIssue_noSuccessMessage_noResolutionUiShown_issueDismisses() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        // Set the initial data for the source
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingAction
            waitButtonDisplayed(action.label) {
                // Re-enable animations for this test as this is needed to show the success message.
                setAnimationsEnabled(true)
                it.click()
            }

            waitSourceIssueNotDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_resolvingInflightIssueFailed_issueRemains() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        // Set the initial data for the source
        val data = safetySourceTestData.criticalWithResolvingGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        // Respond with an error when the action is triggered
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingAction
            waitButtonDisplayed(action.label) { it.click() }

            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_resolvingInFlightIssueTimesOut_issueRemains() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        // Set the initial data for the source
        val data = safetySourceTestData.criticalWithResolvingGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT

        // Set no data at all on the receiver, will ignore incoming call.

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceTestData.criticalResolvingAction
            waitButtonDisplayed(action.label) { it.click() }

            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_clickingNonResolvingActionButton_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val data = safetySourceTestData.criticalWithTestActivityRedirectIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        context.launchSafetyCenterActivity {
            val action = safetySourceTestData.testActivityRedirectAction
            waitButtonDisplayed(action.label) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun issueCard_withAttributionTitleSetBySource_displaysAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val data = safetySourceTestData.informationWithIssueWithAttributionTitle
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        context.launchSafetyCenterActivity { waitAllTextDisplayed("Attribution Title") }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun issueCard_attributionNotSetBySource_displaysGroupTitleAsAttribution() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val data = safetySourceTestData.recommendationWithGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        context.launchSafetyCenterActivity { waitAllTextDisplayed("OK") }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun issueCard_attributionNotSetBySourceAndGroupTitleNull_doesNotDisplayAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceNoGroupTitleConfig)

        val data = SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        safetyCenterTestHelper.setData(ISSUE_ONLY_ALL_OPTIONAL_ID, data)

        context.launchSafetyCenterActivity { waitAllTextNotDisplayed("Attribution Title", "OK") }
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun issueCard_attributionNotSetBySourceOnTiramisu_doesNotDisplayAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val data = safetySourceTestData.recommendationWithGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        context.launchSafetyCenterActivity { waitAllTextNotDisplayed("Attribution title", "OK") }
    }

    @Test
    fun launchActivity_fromQuickSettings_issuesExpanded() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        val bundle = Bundle()
        bundle.putBoolean(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, true)
        context.launchSafetyCenterActivity(bundle) {
            waitExpandedIssuesDisplayed(
                safetySourceTestData.criticalResolvingGeneralIssue,
                safetySourceTestData.recommendationGeneralIssue,
                safetySourceTestData.informationIssue
            )
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueAlreadyFirstIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_1)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            waitCollapsedIssuesDisplayed(
                safetySourceTestData.criticalResolvingGeneralIssue,
                safetySourceTestData.recommendationGeneralIssue,
                safetySourceTestData.informationIssue
            )
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueSamePriorityAsFirstIssue_reorderedFirstIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.criticalWithRedirectingIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            waitCollapsedIssuesDisplayed(
                safetySourceTestData.criticalRedirectingIssue,
                safetySourceTestData.criticalResolvingGeneralIssue,
                safetySourceTestData.informationIssue
            )
        }
    }

    @Test
    fun launchActivity_fromNotification_targetLowerPriorityAsFirstIssue_reorderedSecondIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, RECOMMENDATION_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceTestData.recommendationGeneralIssue)
            waitAllTextDisplayed(MORE_ISSUES_LABEL)
            waitSourceIssueNotDisplayed(safetySourceTestData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueNotFound() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            waitCollapsedIssuesDisplayed(
                safetySourceTestData.criticalResolvingGeneralIssue,
                safetySourceTestData.recommendationGeneralIssue,
                safetySourceTestData.informationIssue
            )
        }
    }

    @Test
    fun moreIssuesCard_underMaxShownIssues_noMoreIssuesCard() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
            waitAllTextNotDisplayed(MORE_ISSUES_LABEL)
        }
    }

    @Test
    fun moreIssuesCard_moreIssuesCardShown_additionalIssueCardsCollapsed() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        context.launchSafetyCenterActivity {
            waitCollapsedIssuesDisplayed(
                safetySourceTestData.criticalResolvingGeneralIssue,
                safetySourceTestData.recommendationGeneralIssue,
                safetySourceTestData.informationIssue
            )
        }
    }

    @Test
    fun moreIssuesCard_expandAdditionalIssueCards() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)

            clickMoreIssuesCard()

            waitExpandedIssuesDisplayed(
                safetySourceTestData.criticalResolvingGeneralIssue,
                safetySourceTestData.recommendationGeneralIssue,
                safetySourceTestData.informationIssue
            )
        }
    }

    @Test
    fun moreIssuesCard_rotation_cardsStillExpanded() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        context.launchSafetyCenterActivity {
            clickMoreIssuesCard()

            // Not checking that all the cards are correctly expanded here, as it is already covered
            // by other tests and makes this tests too slow otherwise. See b/288381777.
            // We still check that the middle card title is displayed though, as this helps ensure
            // the expansion did go through.
            waitAllTextDisplayed(safetySourceTestData.recommendationGeneralIssue.title)

            // Device rotation to trigger usage of savedinstancestate via config update
            getUiDevice().rotate()

            // Verify cards remain expanded
            waitExpandedIssuesDisplayed(
                safetySourceTestData.criticalResolvingGeneralIssue,
                safetySourceTestData.recommendationGeneralIssue,
                safetySourceTestData.informationIssue
            )
        }
    }

    @Test
    fun moreIssuesCard_withThreeIssues_showsTopIssuesAndMoreIssuesCard() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, RECOMMENDATION_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceTestData.recommendationGeneralIssue)
            waitAllTextDisplayed(MORE_ISSUES_LABEL)
            waitSourceIssueNotDisplayed(safetySourceTestData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_twoIssuesAlreadyShown_expandAdditionalIssueCards() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, RECOMMENDATION_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            waitSourceIssueNotDisplayed(safetySourceTestData.informationIssue)

            clickMoreIssuesCard()

            waitSourceIssueDisplayed(safetySourceTestData.informationIssue)
        }
    }

    @Test
    fun collapsedEntryGroup_expandsWhenClicked() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(
                SOURCE_ID_1,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitDisplayed(
                By.text(context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId))
            ) {
                it.click()
            }

            waitAllTextNotDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.summaryResId)
            )
            waitAllTextDisplayed(
                SAFETY_SOURCE_1_TITLE,
                SAFETY_SOURCE_1_SUMMARY,
                SAFETY_SOURCE_2_TITLE,
                SAFETY_SOURCE_2_SUMMARY
            )
        }
    }

    @Test
    fun expandedEntryGroup_collapsesWhenClicked() {
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(
                SOURCE_ID_1,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitDisplayed(
                By.text(context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId))
            ) {
                it.click()
            }

            waitDisplayed(
                By.text(context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId))
            ) {
                it.click()
            }

            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId)
            )
            waitAllTextNotDisplayed(SAFETY_SOURCE_1_TITLE, SAFETY_SOURCE_2_TITLE)
        }
    }

    @Test
    fun expandedEntryGroup_rotation_remainsExpanded() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)

        context.launchSafetyCenterActivity {
            waitDisplayed(
                By.text(context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId))
            ) {
                it.click()
            }

            getUiDevice().rotate()

            waitAllTextNotDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.summaryResId)
            )
            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSource1.titleResId),
                context.getString(safetyCenterTestConfigs.dynamicSource2.titleResId)
            )
        }
    }

    @Test
    fun expandedEntryGroup_otherGroupRemainsCollapsed() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(
                SOURCE_ID_1,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitDisplayed(
                By.text(context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.titleResId))
            ) {
                it.click()
            }

            waitAllTextNotDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup1.summaryResId)
            )
            waitAllTextDisplayed(
                context.getString(safetyCenterTestConfigs.dynamicSourceGroup3.summaryResId)
            )
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun launchSafetyCenter_enableSubpagesFlagOnT_stillShowsExpandAndCollapseEntries() {
        SafetyCenterFlags.showSubpages = true
        val sourceTestData = safetySourceTestData.information
        val config = safetyCenterTestConfigs.multipleSourceGroupsConfig
        with(safetyCenterTestHelper) {
            setConfig(config)
            setData(SOURCE_ID_1, sourceTestData)
            setData(SOURCE_ID_2, sourceTestData)
            setData(SOURCE_ID_3, sourceTestData)
            setData(SOURCE_ID_4, sourceTestData)
            setData(SOURCE_ID_5, sourceTestData)
        }
        val firstGroup = config.safetySourcesGroups.first()
        val lastGroup = config.safetySourcesGroups.last()

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(lastGroup.titleResId),
                context.getString(lastGroup.summaryResId)
            )

            waitDisplayed(By.text(context.getString(firstGroup.titleResId))) { it.click() }

            waitAllTextDisplayed(
                sourceTestData.status!!.title,
                sourceTestData.status!!.summary,
                context.getString(lastGroup.titleResId),
                context.getString(lastGroup.summaryResId),
            )
        }
    }

    @Test
    fun startStaticEntryActivity_noConfigToBeSettingsActivity_noExtraInBundle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.implicitIntentSingleSourceConfig)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitDisplayed(By.text("is_from_settings_homepage false"))
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    fun startStaticEntryActivity_withConfigToBeSettingsActivity_trueExtraInBundle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleStaticSettingsSourceConfig)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitDisplayed(By.text("is_from_settings_homepage true"))
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    fun launchActivity_openWithPrivacyControlsIntent_showsPrivacyControls() {
        context.launchSafetyCenterActivity(intentAction = PRIVACY_CONTROLS_ACTION) {
            waitPageTitleDisplayed("Privacy controls")
        }
    }

    @Test
    fun launchActivity_openWithPrivacyControlsIntentWithScDisabled_showsLegacyPrivacyPage() {
        // This test should technically run on T+ but we have to restrict it to V+ as b/286690307 is
        // causing a flake which was only fixed on master.
        assumeTrue(
            Build.VERSION.SDK_INT > UPSIDE_DOWN_CAKE || Build.VERSION.CODENAME == "VanillaIceCream"
        )
        safetyCenterTestHelper.setEnabled(false)

        context.launchSafetyCenterActivity(intentAction = PRIVACY_CONTROLS_ACTION) {
            waitDisplayed(By.pkg(context.getSettingsPackageName()))
            waitPageTitleDisplayed("Privacy")
        }
    }

    private companion object {
        const val EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY = "expand_issue_group_qs_fragment_key"
        const val SAFETY_SOURCE_1_TITLE = "Safety Source 1 Title"
        const val SAFETY_SOURCE_1_SUMMARY = "Safety Source 1 Summary"
        const val SAFETY_SOURCE_2_TITLE = "Safety Source 2 Title"
        const val SAFETY_SOURCE_2_SUMMARY = "Safety Source 2 Summary"
        const val SAFETY_SOURCE_3_TITLE = "Safety Source 3 Title"
        const val SAFETY_SOURCE_3_SUMMARY = "Safety Source 3 Summary"
        const val SAFETY_SOURCE_4_TITLE = "Safety Source 4 Title"
        const val SAFETY_SOURCE_4_SUMMARY = "Safety Source 4 Summary"
        const val SAFETY_SOURCE_5_TITLE = "Safety Source 5 Title"
        const val SAFETY_SOURCE_5_SUMMARY = "Safety Source 5 Summary"
        const val PRIVACY_CONTROLS_ACTION = "android.settings.PRIVACY_CONTROLS"
    }
}
