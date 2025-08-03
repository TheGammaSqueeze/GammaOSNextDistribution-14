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
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCES_GROUP_ID
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySourcesGroup
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.android.safetycenter.resources.SafetyCenterResourcesApk
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.openPageAndExit
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MULTIPLE_SOURCES_GROUP_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_3
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_4
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_5
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.UiTestHelper.MORE_ISSUES_LABEL
import com.android.safetycenter.testing.UiTestHelper.clickConfirmDismissal
import com.android.safetycenter.testing.UiTestHelper.clickDismissIssueCard
import com.android.safetycenter.testing.UiTestHelper.clickMoreIssuesCard
import com.android.safetycenter.testing.UiTestHelper.clickOpenSubpage
import com.android.safetycenter.testing.UiTestHelper.clickSubpageBrandChip
import com.android.safetycenter.testing.UiTestHelper.resetRotation
import com.android.safetycenter.testing.UiTestHelper.rotate
import com.android.safetycenter.testing.UiTestHelper.setAnimationsEnabled
import com.android.safetycenter.testing.UiTestHelper.waitAllTextDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitAllTextNotDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitButtonDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitCollapsedIssuesDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitExpandedIssuesDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitGroupShownOnHomepage
import com.android.safetycenter.testing.UiTestHelper.waitPageTitleDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitPageTitleNotDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitSourceIssueDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitSourceIssueNotDisplayed
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Functional tests for generic subpages in Safety Center. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterSubpagesTest {

    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterResourcesApk = SafetyCenterResourcesApk.forTests(context)

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)
    @get:Rule(order = 3) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 4) val freezeRotationRule = FreezeRotationRule()

    @Before
    fun enableSubpagesBeforeTest() {
        SafetyCenterFlags.showSubpages = true
    }

    @After
    fun resetRotationAfterTest() {
        UiAutomatorUtils2.getUiDevice().resetRotation()
    }

    @Test
    fun launchSafetyCenter_withSubpagesIntentExtra_showsSubpageTitle() {
        val config = safetyCenterTestConfigs.multipleSourceGroupsConfig
        val sourceGroup = config.safetySourcesGroups.first()!!
        safetyCenterTestHelper.setConfig(config)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, MULTIPLE_SOURCES_GROUP_ID_1)

        context.launchSafetyCenterActivity(extras) {
            waitPageTitleDisplayed(context.getString(sourceGroup.titleResId))
        }
    }

    @Test
    fun launchSafetyCenter_withSubpagesIntentExtraButFlagDisabled_showsHomepageTitle() {
        SafetyCenterFlags.showSubpages = false
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, MULTIPLE_SOURCES_GROUP_ID_1)

        context.launchSafetyCenterActivity(extras) { waitPageTitleDisplayed("Security & privacy") }
    }

    @Test
    fun launchSafetyCenter_withNonExistingGroupID_opensHomepageAsFallback() {
        val config = safetyCenterTestConfigs.multipleSourceGroupsConfig
        val sourceGroup = config.safetySourcesGroups.first()!!
        safetyCenterTestHelper.setConfig(config)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, "non_existing_group_id")

        context.launchSafetyCenterActivity(extras) {
            waitPageTitleNotDisplayed(context.getString(sourceGroup.titleResId))
            waitPageTitleDisplayed("Security & privacy")
        }
    }

    @Test
    fun launchSafetyCenter_withMultipleGroups_showsHomepageEntries() {
        val sourceTestData = safetySourceTestData.information
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(SOURCE_ID_1, sourceTestData)
            setData(SOURCE_ID_2, sourceTestData)
            setData(SOURCE_ID_3, sourceTestData)
            setData(SOURCE_ID_4, sourceTestData)
            setData(SOURCE_ID_5, sourceTestData)
        }
        val firstGroup =
            safetyCenterTestConfigs.multipleSourceGroupsConfig.safetySourcesGroups.first()
        val lastGroup =
            safetyCenterTestConfigs.multipleSourceGroupsConfig.safetySourcesGroups.last()

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(firstGroup.titleResId),
                context.getString(firstGroup.summaryResId),
                context.getString(lastGroup.titleResId),
                context.getString(lastGroup.summaryResId)
            )

            openPageAndExit(context.getString(lastGroup.titleResId)) {
                waitPageTitleDisplayed(context.getString(lastGroup.titleResId))
                waitAllTextNotDisplayed(context.getString(lastGroup.summaryResId))
            }
        }
    }

    @Test
    fun launchSafetyCenter_withMultipleGroupsButFlagDisabled_showsExpandAndCollapseEntries() {
        SafetyCenterFlags.showSubpages = false
        val sourceTestData = safetySourceTestData.information
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(SOURCE_ID_1, sourceTestData)
            setData(SOURCE_ID_2, sourceTestData)
            setData(SOURCE_ID_3, sourceTestData)
            setData(SOURCE_ID_4, sourceTestData)
            setData(SOURCE_ID_5, sourceTestData)
        }
        val firstGroup =
            safetyCenterTestConfigs.multipleSourceGroupsConfig.safetySourcesGroups.first()
        val lastGroup =
            safetyCenterTestConfigs.multipleSourceGroupsConfig.safetySourcesGroups.last()

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(firstGroup.titleResId),
                context.getString(firstGroup.summaryResId),
                context.getString(lastGroup.titleResId)
            )
            waitDisplayed(By.text(context.getString(lastGroup.summaryResId))) { it.click() }

            // Verifying that the group is expanded and sources are displayed
            waitAllTextDisplayed(sourceTestData.status!!.title, sourceTestData.status!!.summary)
            waitAllTextNotDisplayed(context.getString(lastGroup.summaryResId))
        }
    }

    @Test
    fun launchSafetyCenter_redirectBackFromSubpage_showsHomepageEntries() {
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourceGroupsConfig)
            setData(SOURCE_ID_1, safetySourceTestData.information)
            setData(SOURCE_ID_2, safetySourceTestData.information)
        }
        val firstGroup =
            safetyCenterTestConfigs.multipleSourceGroupsConfig.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            waitGroupShownOnHomepage(context, firstGroup)

            openPageAndExit(context.getString(firstGroup.titleResId)) {
                waitPageTitleDisplayed(context.getString(firstGroup.titleResId))
                waitAllTextNotDisplayed(context.getString(firstGroup.summaryResId))
            }

            waitGroupShownOnHomepage(context, firstGroup)
        }
    }

    @Test
    fun entryListWithMultipleSources_clickingOnHomepageEntry_showsSubpageEntries() {
        with(safetyCenterTestHelper) {
            setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
            setData(
                SOURCE_ID_1,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceTestData.buildSafetySourceDataWithSummary(
                    severityLevel = SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY
                )
            )
        }
        val firstGroup = safetyCenterTestConfigs.multipleSourcesConfig.safetySourcesGroups[0]
        val secondGroup = safetyCenterTestConfigs.multipleSourcesConfig.safetySourcesGroups[1]

        context.launchSafetyCenterActivity {
            // Verifying that subpage entries of the first group are displayed
            openPageAndExit(context.getString(firstGroup.titleResId)) {
                waitAllTextNotDisplayed(context.getString(firstGroup.summaryResId))
                waitAllTextDisplayed(
                    SAFETY_SOURCE_1_TITLE,
                    SAFETY_SOURCE_1_SUMMARY,
                    SAFETY_SOURCE_2_TITLE,
                    SAFETY_SOURCE_2_SUMMARY
                )
            }

            // Verifying that subpage entries of the second group are displayed
            openPageAndExit(context.getString(secondGroup.titleResId)) {
                waitAllTextNotDisplayed(context.getString(secondGroup.summaryResId))
                waitAllTextDisplayed(SAFETY_SOURCE_3_TITLE, SAFETY_SOURCE_3_SUMMARY)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingOnSubpageEntry_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitDisplayed(By.text(context.getString(source.titleResId))) { it.click() }
                waitButtonDisplayed("Exit test activity") { it.click() }
                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheInfoIcon_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val sourceTestData = safetySourceTestData.informationWithIconAction
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceTestData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitDisplayed(By.desc("Information")) { it.click() }
                waitButtonDisplayed("Exit test activity") { it.click() }
                waitAllTextDisplayed(sourceTestData.status!!.title, sourceTestData.status!!.summary)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheGearIcon_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val sourceTestData = safetySourceTestData.informationWithGearIconAction
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceTestData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitDisplayed(By.desc("Settings")) { it.click() }
                waitButtonDisplayed("Exit test activity") { it.click() }
                waitAllTextDisplayed(sourceTestData.status!!.title, sourceTestData.status!!.summary)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingAnUnclickableDisabledEntry_doesNothing() {
        val config = safetyCenterTestConfigs.singleSourceInvalidIntentConfig
        safetyCenterTestHelper.setConfig(config)
        val sourceTestData = safetySourceTestData.informationWithNullIntent
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceTestData)
        val sourcesGroup = config.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitDisplayed(By.text(sourceTestData.status!!.title.toString())) { it.click() }

                // Verifying that clicking on the entry doesn't redirect to any other screen
                waitAllTextDisplayed(sourceTestData.status!!.title, sourceTestData.status!!.summary)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingAClickableDisabledEntry_redirectsToDifferentScreen() {
        val config = safetyCenterTestConfigs.singleSourceConfig
        safetyCenterTestHelper.setConfig(config)
        val sourceTestData = safetySourceTestData.unspecifiedDisabledWithTestActivityRedirect
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceTestData)
        val sourcesGroup = config.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitDisplayed(By.text(sourceTestData.status!!.title.toString())) { it.click() }

                waitButtonDisplayed("Exit test activity") { it.click() }
            }
        }
    }

    @Test
    fun entryListWithSingleSource_updateSafetySourceData_displayedDataIsUpdated() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
            }

            SafetySourceReceiver.setResponse(
                Request.Refresh(SINGLE_SOURCE_ID),
                Response.SetData(
                    safetySourceTestData.buildSafetySourceDataWithSummary(
                        severityLevel = SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION,
                        entryTitle = "Updated title",
                        entrySummary = "Updated summary"
                    )
                )
            )

            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitAllTextNotDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
                waitAllTextDisplayed("Updated title", "Updated summary")
            }
        }
    }

    @Test
    fun entryListWithSingleSource_updateSafetySourceDataAndRotate_displayedDataIsNotUpdated() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )

                SafetySourceReceiver.setResponse(
                    Request.Refresh(SINGLE_SOURCE_ID),
                    Response.SetData(
                        safetySourceTestData.buildSafetySourceDataWithSummary(
                            severityLevel = SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION,
                            entryTitle = "Updated title",
                            entrySummary = "Updated summary"
                        )
                    )
                )
                UiAutomatorUtils2.getUiDevice().rotate()

                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
                waitAllTextNotDisplayed("Updated title", "Updated summary")
            }
        }
    }

    @Test
    fun issueCard_withMultipleGroups_onlyRelevantSubpageHasIssueCard() {
        /* The default attribution title for an issue card is same as the entry group title on the
         * homepage. This causes test flakiness as UiAutomator is unable to choose from duplicate
         * strings. To address that, an issue with a different attribution title is used here. */
        val sourceData = safetySourceTestData.informationWithIssueWithAttributionTitle
        val issue = sourceData.issues[0]

        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        val firstGroup = safetyCenterTestConfigs.multipleSourcesConfig.safetySourcesGroups[0]
        val secondGroup = safetyCenterTestConfigs.multipleSourcesConfig.safetySourcesGroups[1]
        safetyCenterTestHelper.setData(SOURCE_ID_3, sourceData)

        context.launchSafetyCenterActivity {
            // Verify that homepage has the issue card
            waitSourceIssueDisplayed(issue)

            // Verify that irrelevant subpage doesn't have the issue card
            openPageAndExit(context.getString(firstGroup.titleResId)) {
                waitSourceIssueNotDisplayed(issue)
            }
            // Verify that relevant subpage has the issue card
            openPageAndExit(context.getString(secondGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
            }
        }
    }

    @Test
    fun issueCard_updateSafetySourceData_subpageDisplaysUpdatedIssue() {
        val initialDataToDisplay = safetySourceTestData.informationWithIssueWithAttributionTitle
        val updatedDataToDisplay = safetySourceTestData.criticalWithIssueWithAttributionTitle

        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, initialDataToDisplay)

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(initialDataToDisplay.issues[0])

                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, updatedDataToDisplay)

                waitSourceIssueDisplayed(updatedDataToDisplay.issues[0])
            }
        }
    }

    @Test
    fun issueCard_resolveIssueOnSubpage_issueDismisses() {
        val sourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val issue = sourceData.issues[0]
        val action = issue.actions[0]

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
                waitButtonDisplayed(action.label) { it.click() }

                // Wait for success message to go away, verify issue no longer displayed
                waitAllTextNotDisplayed(action.successMessage)
                waitSourceIssueNotDisplayed(issue)
            }
        }
    }

    @Test
    fun issueCard_confirmDismissalOnSubpage_dismissesIssue() {
        val sourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val issue = sourceData.issues[0]

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
                clickDismissIssueCard()

                waitAllTextDisplayed("Dismiss this alert?")
                clickConfirmDismissal()

                waitSourceIssueNotDisplayed(issue)
            }
        }
    }

    @Test
    fun issueCard_dismissOnSubpageWithRotation_cancellationPersistsIssue() {
        val sourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val issue = sourceData.issues[0]

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
                clickDismissIssueCard()
                waitAllTextDisplayed("Dismiss this alert?")

                UiAutomatorUtils2.getUiDevice().rotate()

                waitAllTextDisplayed("Dismiss this alert?")
                waitButtonDisplayed("Cancel") { it.click() }
                waitSourceIssueDisplayed(issue)
            }
        }
    }

    @Test
    fun moreIssuesCard_expandOnSubpage_showsAdditionalCard() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        val sourcesGroup = safetyCenterTestConfigs.multipleSourcesConfig.safetySourcesGroups.first()
        val firstSourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        val secondSourceData = safetySourceTestData.informationWithIssueWithAttributionTitle
        safetyCenterTestHelper.setData(SOURCE_ID_1, firstSourceData)
        safetyCenterTestHelper.setData(SOURCE_ID_2, secondSourceData)

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitCollapsedIssuesDisplayed(firstSourceData.issues[0], secondSourceData.issues[0])

                clickMoreIssuesCard()

                waitExpandedIssuesDisplayed(firstSourceData.issues[0], secondSourceData.issues[0])
            }
        }
    }

    @Test
    fun dismissedIssuesCard_expandWithOnlyDismissedIssues_showsAdditionalCard() {
        val sourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val issue = sourceData.issues[0]

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
                clickDismissIssueCard()
                waitAllTextDisplayed("Dismiss this alert?")
                clickConfirmDismissal()
                waitSourceIssueNotDisplayed(issue)

                waitDisplayed(By.text("Dismissed alerts")) { it.click() }

                waitAllTextDisplayed("Dismissed alerts")
                waitSourceIssueDisplayed(issue)
            }
        }
    }

    @Test
    fun dismissedIssuesCard_collapseWithOnlyDismissedIssues_hidesAdditionalCard() {
        val sourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val issue = sourceData.issues[0]

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
                clickDismissIssueCard()
                waitAllTextDisplayed("Dismiss this alert?")
                clickConfirmDismissal()
                waitSourceIssueNotDisplayed(issue)

                waitDisplayed(By.text("Dismissed alerts")) { it.click() }
                waitSourceIssueDisplayed(issue)

                waitDisplayed(By.text("Dismissed alerts")) { it.click() }
                waitSourceIssueNotDisplayed(issue)
            }
        }
    }

    @Test
    fun dismissedIssuesCard_resolveIssue_successConfirmationShown() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithAttributionTitle
            )
        prepareActionResponse(Response.ClearData)

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
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
    fun dismissedIssuesCard_resolveIssue_issueDismisses() {
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithAttributionTitle
            )
        prepareActionResponse(Response.ClearData)

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) { it.click() }

            // Wait for success message to go away, verify issue no longer displayed
            val successMessage = action.successMessage
            waitAllTextNotDisplayed(successMessage)
            waitSourceIssueNotDisplayed(issue)
        }
    }

    @Test
    fun dismissedIssuesCard_resolveIssue_withDialogClickYes_resolves() {
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithConfirmationWithAttributionTitle
            )
        prepareActionResponse(Response.ClearData)

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) { it.click() }

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)
            waitButtonDisplayed(SafetySourceTestData.CONFIRMATION_YES) { it.click() }

            waitSourceIssueNotDisplayed(issue)
        }
    }

    @Test
    fun dismissedIssuesCard_resolveIssue_withDialog_rotates_clickYes_resolves() {
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithConfirmationWithAttributionTitle
            )
        prepareActionResponse(Response.ClearData)

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) { it.click() }

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)

            UiAutomatorUtils2.getUiDevice().rotate()

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)
            waitButtonDisplayed(SafetySourceTestData.CONFIRMATION_YES) { it.click() }

            waitSourceIssueNotDisplayed(issue)
        }
    }

    @Test
    fun dismissedIssuesCard_resolveIssue_withDialogClicksNo_cancels() {
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithConfirmationWithAttributionTitle
            )

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) { it.click() }

            waitAllTextDisplayed(SafetySourceTestData.CONFIRMATION_TITLE)
            waitButtonDisplayed(SafetySourceTestData.CONFIRMATION_NO) { it.click() }

            waitSourceIssueDisplayed(issue)
        }
    }

    @Test
    fun dismissedIssuesCard_resolveIssue_noSuccessMessage_noResolutionUiShown_issueDismisses() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithAttributionTitle
            )
        prepareActionResponse(Response.ClearData)

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) {
                // Re-enable animations for this test as this is needed to show the success message.
                setAnimationsEnabled(true)
                it.click()
            }

            waitSourceIssueNotDisplayed(issue)
        }
    }

    @Test
    fun dismissedIssuesCard_resolvingInflightIssueFailed_issueRemains() {
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithAttributionTitle
            )
        prepareActionResponse(Response.Error)

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) { it.click() }

            waitSourceIssueDisplayed(issue)
        }
    }

    @Test
    fun dismissedIssuesCard_resolvingInFlightIssueTimesOut_issueRemains() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithIssueWithAttributionTitle
            )

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) { it.click() }

            waitSourceIssueDisplayed(issue)
        }
    }

    @Test
    fun dismissedIssuesCard_clickingNonResolvingActionButton_redirectsToDifferentScreen() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.criticalWithTestActivityRedirectWithAttributionTitle
            )

        checkOnDismissedIssue(sourcesGroup, issue) {
            val action = issue.actions[0]
            waitButtonDisplayed(action.label) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    fun dismissedIssuesCard_doesntShowGreenCards() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val (sourcesGroup, issue) =
            prepareSingleSourceGroupWithIssue(
                safetySourceTestData.informationWithIssueWithAttributionTitle
            )
        val safetyCenterIssueId = SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, issue.id)
        safetyCenterTestHelper.dismissSafetyCenterIssue(safetyCenterIssueId)

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitAllTextNotDisplayed("Dismissed alerts")
                waitSourceIssueNotDisplayed(issue)
            }
        }
    }

    @Test
    fun moreIssuesCard_expandWithDismissedIssues_showsAdditionalCards() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesInSingleGroupConfig)

        val firstSourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        val secondSourceData = safetySourceTestData.informationWithIssueWithAttributionTitle
        val thirdSourceData = safetySourceTestData.informationWithIssueWithAttributionTitle

        safetyCenterTestHelper.setData(SOURCE_ID_1, firstSourceData)
        safetyCenterTestHelper.setData(SOURCE_ID_2, secondSourceData)
        safetyCenterTestHelper.setData(SOURCE_ID_3, thirdSourceData)

        val sourcesGroup =
            safetyCenterTestConfigs.multipleSourcesInSingleGroupConfig.safetySourcesGroups.first()
        val issue = firstSourceData.issues[0]

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
                clickDismissIssueCard()
                waitAllTextDisplayed("Dismiss this alert?")
                clickConfirmDismissal()
                waitSourceIssueNotDisplayed(issue)

                clickMoreIssuesCard()

                waitAllTextDisplayed(MORE_ISSUES_LABEL)
                waitAllTextDisplayed("Dismissed alerts")
                waitSourceIssueDisplayed(issue)
            }
        }
    }

    @Test
    fun moreIssuesCard_collapseWithDismissedIssues_hidesAdditionalCards() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesInSingleGroupConfig)

        val firstSourceData = safetySourceTestData.criticalWithIssueWithAttributionTitle
        val secondSourceData = safetySourceTestData.informationWithIssueWithAttributionTitle
        val thirdSourceData = safetySourceTestData.informationWithIssueWithAttributionTitle

        safetyCenterTestHelper.setData(SOURCE_ID_1, firstSourceData)
        safetyCenterTestHelper.setData(SOURCE_ID_2, secondSourceData)
        safetyCenterTestHelper.setData(SOURCE_ID_3, thirdSourceData)

        val sourcesGroup =
            safetyCenterTestConfigs.multipleSourcesInSingleGroupConfig.safetySourcesGroups.first()
        val issue = firstSourceData.issues[0]

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitSourceIssueDisplayed(issue)
                clickDismissIssueCard()
                clickConfirmDismissal()
                waitSourceIssueNotDisplayed(issue)

                clickMoreIssuesCard()
                waitSourceIssueDisplayed(issue)

                clickMoreIssuesCard()
                waitAllTextDisplayed(MORE_ISSUES_LABEL)
                waitAllTextNotDisplayed("Dismissed alerts")
                waitSourceIssueNotDisplayed(issue)
            }
        }
    }

    @Test
    fun brandChip_openSubpageFromHomepage_homepageReopensOnClick() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            waitGroupShownOnHomepage(context, sourcesGroup)

            clickOpenSubpage(context, sourcesGroup)
            waitPageTitleDisplayed(context.getString(sourcesGroup.titleResId))
            waitAllTextNotDisplayed(context.getString(sourcesGroup.summaryResId))

            clickSubpageBrandChip()
            waitGroupShownOnHomepage(context, sourcesGroup)
        }
    }

    @Test
    fun brandChip_openSubpageFromIntent_homepageOpensOnClick() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, sourcesGroup.id)

        context.launchSafetyCenterActivity(extras) {
            waitPageTitleDisplayed(context.getString(sourcesGroup.titleResId))
            waitAllTextNotDisplayed(context.getString(sourcesGroup.summaryResId))

            clickSubpageBrandChip()
            waitGroupShownOnHomepage(context, sourcesGroup)
        }
    }

    @Test
    fun settingsSearch_openWithGenericIntentExtra_showsGenericSubpage() {
        val config = safetyCenterTestConfigs.multipleSourcesConfig
        safetyCenterTestHelper.setConfig(config)
        val sourcesGroup = config.safetySourcesGroups.first()
        val source = sourcesGroup.safetySources.first()
        val preferenceKey = "${source.id}_personal"
        val extras = Bundle()
        extras.putString(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY, preferenceKey)

        context.launchSafetyCenterActivity(extras) {
            waitPageTitleDisplayed(context.getString(sourcesGroup.titleResId))
            waitAllTextDisplayed(
                context.getString(source.titleResId),
                context.getString(source.summaryResId)
            )
        }
    }

    @Test
    fun settingsSearch_openWithInvalidKey_showsHomepage() {
        val config = safetyCenterTestConfigs.singleSourceConfig
        val sourcesGroup = config.safetySourcesGroups.first()
        safetyCenterTestHelper.setConfig(config)
        val extras = Bundle()
        extras.putString(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY, "invalid_preference_key")

        context.launchSafetyCenterActivity(extras) {
            waitPageTitleDisplayed("Security & privacy")
            waitGroupShownOnHomepage(context, sourcesGroup)
        }
    }

    @Test
    fun footerSummary_openGenericSubpageHavingFooter_showsExpectedText() {
        val config = safetyCenterTestConfigs.singleSourceConfig
        val sourcesGroup = config.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()
        safetyCenterTestHelper.setConfig(config)

        context.launchSafetyCenterActivity {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId),
                    safetyCenterResourcesApk.getStringByName("test_single_source_group_id_footer")
                )
            }
        }
    }

    private fun prepareSingleSourceGroupWithIssue(
        sourceData: SafetySourceData
    ): Pair<SafetySourcesGroup, SafetySourceIssue> {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, sourceData)
        val sourcesGroup = safetyCenterTestConfigs.singleSourceConfig.safetySourcesGroups.first()
        val issue = sourceData.issues[0]
        return sourcesGroup to issue
    }

    private fun prepareActionResponse(actionResponse: Response) {
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), actionResponse)
    }

    private fun checkOnDismissedIssue(
        sourcesGroup: SafetySourcesGroup,
        issue: SafetySourceIssue,
        block: () -> Unit
    ) {
        val safetyCenterIssueId = SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, issue.id)
        safetyCenterTestHelper.dismissSafetyCenterIssue(safetyCenterIssueId)

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            openPageAndExit(context.getString(sourcesGroup.titleResId)) {
                waitDisplayed(By.text("Dismissed alerts")) { it.click() }
                waitSourceIssueDisplayed(issue)

                block()
            }
        }
    }

    companion object {
        private const val SAFETY_SOURCE_1_TITLE = "Safety Source 1 Title"
        private const val SAFETY_SOURCE_1_SUMMARY = "Safety Source 1 Summary"
        private const val SAFETY_SOURCE_2_TITLE = "Safety Source 2 Title"
        private const val SAFETY_SOURCE_2_SUMMARY = "Safety Source 2 Summary"
        private const val SAFETY_SOURCE_3_TITLE = "Safety Source 3 Title"
        private const val SAFETY_SOURCE_3_SUMMARY = "Safety Source 3 Summary"
        private const val EXTRA_SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
    }
}
