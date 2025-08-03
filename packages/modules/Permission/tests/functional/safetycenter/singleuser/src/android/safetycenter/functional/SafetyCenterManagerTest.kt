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

import android.Manifest.permission.MANAGE_SAFETY_CENTER
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_NONE
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.SafetySourceIssue
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil
import com.android.modules.utils.build.SdkLevel
import com.android.safetycenter.internaldata.SafetyCenterBundles
import com.android.safetycenter.internaldata.SafetyCenterBundles.ISSUES_TO_GROUPS_BUNDLE_KEY
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.resources.SafetyCenterResourcesApk
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.Coroutines.waitForWithTimeout
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ACTION_TEST_ACTIVITY
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ACTION_TEST_ACTIVITY_EXPORTED
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_DISABLED_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_HIDDEN_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_HIDDEN_WITH_SEARCH_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_IN_STATEFUL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_IN_STATELESS_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_OTHER_PACKAGE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_IN_STATELESS_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MIXED_STATEFUL_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MIXED_STATELESS_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MULTIPLE_SOURCES_GROUP_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MULTIPLE_SOURCES_GROUP_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_3
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_4
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_5
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_6
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_7
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.STATIC_IN_STATEFUL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SUMMARY_TEST_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withAttributionTitleInIssuesIfAtLeastU
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withDismissedIssuesIfAtLeastU
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withoutExtras
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.dismissSafetyCenterIssueWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.android.safetycenter.testing.SafetySourceTestData.Companion.INFORMATION_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.RECOMMENDATION_ISSUE_ID
import com.android.safetycenter.testing.SettingsPackage.getSettingsPackageName
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.google.common.base.Preconditions.checkState
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Functional tests for [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterResourcesApk = SafetyCenterResourcesApk.forTests(context)
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestData = SafetyCenterTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    private val safetyCenterStatusOk: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName("overall_severity_level_ok_title"),
                    safetyCenterResourcesApk.getStringByName("overall_severity_level_ok_summary")
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
                .build()

    private val safetyCenterStatusUnknownScanning: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName("scanning_title"),
                    safetyCenterResourcesApk.getStringByName("loading_summary")
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                .setRefreshStatus(REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
                .build()

    private val safetyCenterStatusOkOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName("overall_severity_level_ok_title"),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
                .build()

    private val safetyCenterStatusOkReviewOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_ok_review_title"
                    ),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
                .build()

    private val safetyCenterStatusOkReview: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_ok_review_title"
                    ),
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_ok_review_summary"
                    )
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
                .build()

    private val safetyCenterStatusGeneralRecommendationOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_safety_recommendation_title"
                    ),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
                .build()

    private val safetyCenterStatusAccountRecommendationOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_account_recommendation_title"
                    ),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
                .build()

    private val safetyCenterStatusDeviceRecommendationOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_device_recommendation_title"
                    ),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
                .build()

    private val safetyCenterStatusGeneralCriticalOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_critical_safety_warning_title"
                    ),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

    private val safetyCenterStatusGeneralCriticalTwoAlerts: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_critical_safety_warning_title"
                    ),
                    safetyCenterTestData.getAlertString(2)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

    private val safetyCenterStatusAccountCriticalOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_critical_account_warning_title"
                    ),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

    private val safetyCenterStatusAccountCriticalTwoAlerts: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_critical_account_warning_title"
                    ),
                    safetyCenterTestData.getAlertString(2)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

    private val safetyCenterStatusDeviceCriticalOneAlert: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_critical_device_warning_title"
                    ),
                    safetyCenterTestData.getAlertString(1)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

    private val safetyCenterStatusDeviceCriticalTwoAlerts: SafetyCenterStatus
        get() =
            SafetyCenterStatus.Builder(
                    safetyCenterResourcesApk.getStringByName(
                        "overall_severity_level_critical_device_warning_title"
                    ),
                    safetyCenterTestData.getAlertString(2)
                )
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

    private val safetyCenterEntryOrGroupRecommendation: SafetyCenterEntryOrGroup
        get() =
            SafetyCenterEntryOrGroup(
                safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
            )

    private val safetyCenterEntryOrGroupCritical: SafetyCenterEntryOrGroup
        get() =
            SafetyCenterEntryOrGroup(
                safetyCenterTestData.safetyCenterEntryCritical(SINGLE_SOURCE_ID)
            )

    private val safetyCenterEntryGroupMixedFromComplexConfig: SafetyCenterEntryOrGroup
        get() =
            SafetyCenterEntryOrGroup(
                SafetyCenterEntryGroup.Builder(MIXED_STATEFUL_GROUP_ID, "OK")
                    .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                    .setSummary(safetyCenterResourcesApk.getStringByName("group_unknown_summary"))
                    .setEntries(
                        listOf(
                            safetyCenterTestData.safetyCenterEntryDefault(DYNAMIC_IN_STATEFUL_ID),
                            SafetyCenterEntry.Builder(
                                    SafetyCenterTestData.entryId(STATIC_IN_STATEFUL_ID),
                                    "OK"
                                )
                                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                                .setSummary("OK")
                                .setPendingIntent(
                                    safetySourceTestData.createTestActivityRedirectPendingIntent(
                                        explicit = false
                                    )
                                )
                                .setSeverityUnspecifiedIconType(
                                    SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
                                )
                                .build()
                        )
                    )
                    .setSeverityUnspecifiedIconType(
                        SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                    )
                    .build()
            )

    private val safetyCenterStaticEntryGroupFromComplexConfig: SafetyCenterStaticEntryGroup
        get() =
            SafetyCenterStaticEntryGroup(
                "OK",
                listOf(
                    SafetyCenterStaticEntry.Builder("OK")
                        .setPendingIntent(
                            safetySourceTestData.createTestActivityRedirectPendingIntent(
                                explicit = false
                            )
                        )
                        .build(),
                    SafetyCenterStaticEntry.Builder("OK")
                        .setSummary("OK")
                        .setPendingIntent(
                            safetySourceTestData.createTestActivityRedirectPendingIntent(
                                explicit = false
                            )
                        )
                        .build()
                )
            )

    private val safetyCenterStaticEntryGroupMixedFromComplexConfig: SafetyCenterStaticEntryGroup
        get() =
            SafetyCenterStaticEntryGroup(
                "OK",
                listOf(
                    SafetyCenterStaticEntry.Builder("OK")
                        .setSummary("OK")
                        .setPendingIntent(
                            safetySourceTestData.createTestActivityRedirectPendingIntent()
                        )
                        .build(),
                    SafetyCenterStaticEntry.Builder("OK")
                        .setSummary("OK")
                        .setPendingIntent(
                            safetySourceTestData.createTestActivityRedirectPendingIntent(
                                explicit = false
                            )
                        )
                        .build()
                )
            )

    private val safetyCenterStaticEntryGroupMixedUpdatedFromComplexConfig:
        SafetyCenterStaticEntryGroup
        get() =
            SafetyCenterStaticEntryGroup(
                "OK",
                listOf(
                    SafetyCenterStaticEntry.Builder("Unspecified title")
                        .setSummary("Unspecified summary")
                        .setPendingIntent(
                            safetySourceTestData.createTestActivityRedirectPendingIntent()
                        )
                        .build(),
                    SafetyCenterStaticEntry.Builder("OK")
                        .setSummary("OK")
                        .setPendingIntent(
                            safetySourceTestData.createTestActivityRedirectPendingIntent(
                                explicit = false
                            )
                        )
                        .build()
                )
            )

    private val safetyCenterDataFromConfigScanning: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusUnknownScanning,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryDefault(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataFromConfig: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryDefault(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataUnspecified: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryUnspecified(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataOk: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryOk(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataOkWithIconAction: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData
                            .safetyCenterEntryOkBuilder(SINGLE_SOURCE_ID)
                            .setIconAction(
                                ICON_ACTION_TYPE_INFO,
                                safetySourceTestData.createTestActivityRedirectPendingIntent()
                            )
                            .build()
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataUnknownScanningWithError: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusUnknownScanning,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryError(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataUnknownReviewError: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryError(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataOkOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusOkOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryOk(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataOkReviewCriticalEntry: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusOkReview,
                emptyList(),
                listOf(safetyCenterEntryOrGroupCritical),
                emptyList()
            )

    private val safetyCenterDataOkReviewRecommendationEntry: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusOkReview,
                emptyList(),
                listOf(safetyCenterEntryOrGroupRecommendation),
                emptyList()
            )

    private val safetyCenterDataOkReviewOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusOkReviewOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
                listOf(safetyCenterEntryOrGroupCritical),
                emptyList()
            )

    private val safetyCenterDataGeneralRecommendationOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusGeneralRecommendationOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataGeneralRecommendationAlertWithConfirmation: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusGeneralRecommendationOneAlert,
                listOf(
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        SINGLE_SOURCE_ID,
                        confirmationDialog = true
                    )
                ),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataAccountRecommendationOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusAccountRecommendationOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataDeviceRecommendationOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusDeviceRecommendationOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
                    )
                ),
                emptyList()
            )

    private val safetyCenterDataGeneralCriticalOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusGeneralCriticalOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
                listOf(safetyCenterEntryOrGroupCritical),
                emptyList()
            )

    private val safetyCenterDataAccountCriticalOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusAccountCriticalOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
                listOf(safetyCenterEntryOrGroupCritical),
                emptyList()
            )

    private val safetyCenterDataDeviceCriticalOneAlert: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusDeviceCriticalOneAlert,
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
                listOf(safetyCenterEntryOrGroupCritical),
                emptyList()
            )

    private val safetyCenterDataCriticalOneAlertInFlight: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterStatusGeneralCriticalOneAlert,
                listOf(
                    safetyCenterTestData.safetyCenterIssueCritical(
                        SINGLE_SOURCE_ID,
                        isActionInFlight = true
                    )
                ),
                listOf(safetyCenterEntryOrGroupCritical),
                emptyList()
            )

    private val safetyCenterDataOkReviewOneDismissedAlertInFlight: SafetyCenterData
        get() =
            SafetyCenterData(
                    safetyCenterStatusOkReview,
                    emptyList(),
                    listOf(safetyCenterEntryOrGroupCritical),
                    emptyList()
                )
                .withDismissedIssuesIfAtLeastU(
                    listOf(
                        safetyCenterTestData.safetyCenterIssueCritical(
                            SINGLE_SOURCE_ID,
                            isActionInFlight = true
                        )
                    )
                )

    private val safetyCenterDataFromComplexConfig: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                            .setSummary(
                                safetyCenterResourcesApk.getStringByName("group_unknown_summary")
                            )
                            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                            .setEntries(
                                listOf(
                                    safetyCenterTestData.safetyCenterEntryDefault(
                                        DYNAMIC_BAREBONE_ID
                                    ),
                                    safetyCenterTestData
                                        .safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                        .setEnabled(false)
                                        .build(),
                                    safetyCenterTestData
                                        .safetyCenterEntryDefaultBuilder(DYNAMIC_DISABLED_ID)
                                        .setPendingIntent(null)
                                        .setEnabled(false)
                                        .build(),
                                    safetyCenterTestData
                                        .safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
                                        .setPendingIntent(null)
                                        .setEnabled(false)
                                        .build()
                                )
                            )
                            .build()
                    ),
                    safetyCenterEntryGroupMixedFromComplexConfig
                ),
                listOf(
                    safetyCenterStaticEntryGroupFromComplexConfig,
                    safetyCenterStaticEntryGroupMixedFromComplexConfig
                )
            )

    private val safetyCenterDataFromComplexConfigUpdated: SafetyCenterData
        get() =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusCritical(6),
                listOf(
                    safetyCenterTestData.safetyCenterIssueCritical(
                        DYNAMIC_BAREBONE_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueCritical(
                        ISSUE_ONLY_BAREBONE_ID,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        DYNAMIC_DISABLED_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    )
                ),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    safetyCenterTestData.safetyCenterEntryCritical(
                                        DYNAMIC_BAREBONE_ID
                                    ),
                                    safetyCenterTestData
                                        .safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                        .setEnabled(false)
                                        .build(),
                                    safetyCenterTestData.safetyCenterEntryRecommendation(
                                        DYNAMIC_DISABLED_ID
                                    ),
                                    safetyCenterTestData.safetyCenterEntryUnspecified(
                                        DYNAMIC_HIDDEN_ID,
                                        pendingIntent = null
                                    ),
                                    safetyCenterTestData.safetyCenterEntryOk(
                                        DYNAMIC_HIDDEN_WITH_SEARCH_ID
                                    ),
                                    safetyCenterTestData
                                        .safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
                                        .setPendingIntent(null)
                                        .setEnabled(false)
                                        .build()
                                )
                            )
                            .build()
                    ),
                    safetyCenterEntryGroupMixedFromComplexConfig
                ),
                listOf(
                    safetyCenterStaticEntryGroupFromComplexConfig,
                    safetyCenterStaticEntryGroupMixedUpdatedFromComplexConfig
                )
            )

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)

    @Test
    fun getSafetySourceData_differentPackageWithManageSafetyCenterPermission_returnsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val data =
            callWithShellPermissionIdentity(MANAGE_SAFETY_CENTER) {
                safetyCenterManager.getSafetySourceData(DYNAMIC_OTHER_PACKAGE_ID)
            }

        assertThat(data).isNull()
    }

    @Test
    fun refreshSafetySources_timeout_marksSafetySourceAsError() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val safetyCenterBeforeTimeout = listener.receiveSafetyCenterData()
        assertThat(safetyCenterBeforeTimeout).isEqualTo(safetyCenterDataFromConfigScanning)
        val safetyCenterDataAfterTimeout = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterTimeout).isEqualTo(safetyCenterDataUnknownReviewError)
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_timeout_keepsShowingErrorUntilClearedBySource() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )
        val scanningData = listener.receiveSafetyCenterData()
        checkState(scanningData == safetyCenterDataFromConfigScanning)
        val initialData = listener.receiveSafetyCenterData()
        checkState(initialData == safetyCenterDataUnknownReviewError)

        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val safetyCenterDataWhenTryingAgain = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataWhenTryingAgain)
            .isEqualTo(safetyCenterDataUnknownScanningWithError)
        val safetyCenterDataWhenFinishingRefresh = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataWhenFinishingRefresh).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun refreshSafetySources_timeout_doesntSetErrorForBackgroundRefreshes() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(REFRESH_REASON_OTHER)

        val safetyCenterDataAfterTimeout =
            listener.waitForSafetyCenterRefresh(withErrorEntry = false)
        assertThat(safetyCenterDataAfterTimeout).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_notifiesUiDuringRescan() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val status1 = listener.receiveSafetyCenterData().status
        assertThat(status1.refreshStatus).isEqualTo(REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
        assertThat(status1.title.toString())
            .isEqualTo(safetyCenterResourcesApk.getStringByName("scanning_title"))
        assertThat(status1.summary.toString())
            .isEqualTo(safetyCenterResourcesApk.getStringByName("loading_summary"))
        val status2 = listener.receiveSafetyCenterData().status
        assertThat(status2.refreshStatus).isEqualTo(REFRESH_STATUS_NONE)
        assertThat(status2).isEqualTo(safetyCenterStatusOk)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_notifiesUiWithFetch() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val status1 = listener.receiveSafetyCenterData().status
        assertThat(status1.refreshStatus).isEqualTo(REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
        assertThat(status1.title.toString())
            .isEqualTo(safetyCenterResourcesApk.getStringByName("scanning_title"))
        assertThat(status1.summary.toString())
            .isEqualTo(safetyCenterResourcesApk.getStringByName("loading_summary"))
        val status2 = listener.receiveSafetyCenterData().status
        assertThat(status2.refreshStatus).isEqualTo(REFRESH_STATUS_NONE)
        assertThat(status2).isEqualTo(safetyCenterStatusOk)
    }

    @Test
    fun refreshSafetySources_pageOpenRefreshWithPreExistingData_notifiesUiWithExistingTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val status1 = listener.receiveSafetyCenterData().status
        assertThat(status1.refreshStatus).isEqualTo(REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
        assertThat(status1.title.toString()).isEqualTo(safetyCenterStatusOk.title.toString())
        assertThat(status1.summary.toString())
            .isEqualTo(safetyCenterResourcesApk.getStringByName("loading_summary"))
        val status2 = listener.receiveSafetyCenterData().status
        assertThat(status2.refreshStatus).isEqualTo(REFRESH_STATUS_NONE)
        assertThat(status2).isEqualTo(safetyCenterStatusOk)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_allowedByFlag_broadcastSent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.noPageOpenConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetyCenterFlags.overrideRefreshOnPageOpenSources = setOf(SINGLE_SOURCE_ID)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.informationWithIssue)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.informationWithIssue)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_allowedByFlagLater_broadcastSentLater() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.noPageOpenConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.informationWithIssue)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                timeout = TIMEOUT_SHORT
            )
        }
        val apiSafetySourceDataBeforeSettingFlag =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        SafetyCenterFlags.overrideRefreshOnPageOpenSources = setOf(SINGLE_SOURCE_ID)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        val apiSafetySourceDataAfterSettingFlag =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceDataBeforeSettingFlag).isEqualTo(safetySourceTestData.information)
        assertThat(apiSafetySourceDataAfterSettingFlag)
            .isEqualTo(safetySourceTestData.informationWithIssue)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_noDataForSource_broadcastSent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.noPageOpenConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun getSafetyCenterData_withoutDataProvided_returnsDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun getSafetyCenterData_withoutDataExplicitIntentConfig_defaultEntryHasExplicitIntent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedExplicitPendingIntent =
            SafetySourceTestData.createRedirectPendingIntent(
                context,
                Intent(ACTION_TEST_ACTIVITY).setPackage(context.packageName)
            )
        val defaultEntryPendingIntent =
            apiSafetyCenterData.entriesOrGroups.firstOrNull()?.entry?.pendingIntent
        val defaultEntryIntentFilterEqualsToExplicitIntent =
            callWithShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT") {
                expectedExplicitPendingIntent.intentFilterEquals(defaultEntryPendingIntent)
            }
        assertThat(defaultEntryIntentFilterEqualsToExplicitIntent).isTrue()
    }

    @Test
    fun getSafetyCenterData_withoutDataImplicitIntentConfig_defaultEntryHasImplicitIntent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.implicitIntentSingleSourceConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedImplicitPendingIntent =
            SafetySourceTestData.createRedirectPendingIntent(
                context,
                Intent(ACTION_TEST_ACTIVITY_EXPORTED)
            )
        val defaultEntryPendingIntent =
            apiSafetyCenterData.entriesOrGroups.firstOrNull()?.entry?.pendingIntent
        val defaultEntryIntentFilterEqualsToImplicitIntent =
            callWithShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT") {
                expectedImplicitPendingIntent.intentFilterEquals(defaultEntryPendingIntent)
            }
        assertThat(defaultEntryIntentFilterEqualsToImplicitIntent).isTrue()
    }

    @Test
    fun getSafetyCenterData_withStaticImplicitResolving_implicitStaticEntry() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.staticSourcesConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedImplicitPendingIntent =
            SafetySourceTestData.createRedirectPendingIntent(
                context,
                Intent(ACTION_TEST_ACTIVITY_EXPORTED)
            )
        val staticEntryPendingIntent =
            apiSafetyCenterData.staticEntryGroups
                .firstOrNull()
                ?.staticEntries
                ?.firstOrNull()
                ?.pendingIntent
        val staticEntryIntentFilterEqualsToImplicitIntent =
            callWithShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT") {
                expectedImplicitPendingIntent.intentFilterEquals(staticEntryPendingIntent)
            }
        assertThat(staticEntryIntentFilterEqualsToImplicitIntent).isTrue()
    }

    @Test
    fun getSafetyCenterData_withStaticImplicitNotExported_explicitStaticEntryUsingCallerPackage() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.singleStaticImplicitIntentNotExportedConfig
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedExplicitPendingIntent =
            SafetySourceTestData.createRedirectPendingIntent(
                context,
                Intent(ACTION_TEST_ACTIVITY).setPackage(context.packageName)
            )
        val staticEntryPendingIntent =
            apiSafetyCenterData.staticEntryGroups
                .firstOrNull()
                ?.staticEntries
                ?.firstOrNull()
                ?.pendingIntent
        val staticEntryIntentFilterEqualsToExplicitIntent =
            callWithShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT") {
                expectedExplicitPendingIntent.intentFilterEquals(staticEntryPendingIntent)
            }
        assertThat(staticEntryIntentFilterEqualsToExplicitIntent).isTrue()
    }

    @Test
    fun getSafetyCenterData_withStaticNotResolving_noStaticEntry() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleStaticInvalidIntentConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData.staticEntryGroups).isEmpty()
    }

    @Test
    fun getSafetyCenterData_withComplexConfigWithoutDataProvided_returnsDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    fun getSafetyCenterData_withOnlyHiddenSourcesWithoutDataProvided_returnsNoGroups() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.hiddenOnlyConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData)
            .isEqualTo(
                SafetyCenterData(safetyCenterStatusOk, emptyList(), emptyList(), emptyList())
            )
    }

    @Test
    fun getSafetyCenterData_withSomeDataProvided_returnsDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataUnspecified)
    }

    @Test
    fun getSafetyCenterData_withIconAction_returnsDataWithIconAction() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.informationWithIconAction
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkWithIconAction)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_attributionTitleProvidedBySource_returnsIssueWithAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.informationWithIssueWithAttributionTitle
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            safetyCenterDataOkOneAlert.withAttributionTitleInIssuesIfAtLeastU("Attribution Title")
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_attributionTitleNotProvided_returnsGroupTitleAsAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            safetyCenterDataOkOneAlert.withAttributionTitleInIssuesIfAtLeastU("OK")
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_attributionNotSetAndGroupTitleNull_returnsNullAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceNoGroupTitleConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterStatusGeneralRecommendationOneAlert,
                listOf(
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        attributionTitle = null
                    )
                ),
                emptyList(),
                emptyList()
            )
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getSafetyCenterData_attributionNotSetBySourceOnTiramisu_returnsNullAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkOneAlert)
    }

    @Test
    fun getSafetyCenterData_withUpdatedData_returnsUpdatedData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val previousApiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotEqualTo(previousApiSafetyCenterData)
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_reportError_returnsUnknownReviewErrorStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataUnknownReviewError)
    }

    @Test
    fun getSafetyCenterData_withInformationIssue_returnsOverallStatusOkWithOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalStatusAndInfoIssue_returnsOverallStatusOkReviewOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithInformationIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkReviewOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationGeneralIssue_returnsGeneralRecommendationOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithGeneralIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralRecommendationOneAlert)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withActionConfirmation_returnsRecommendationWithActionConfirmation() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithIssueWithActionConfirmation
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData)
            .isEqualTo(safetyCenterDataGeneralRecommendationAlertWithConfirmation)
    }

    @Test
    fun getSafetyCenterData_withRecommendationAccountIssue_returnsAccountRecommendationOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataAccountRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationDeviceIssue_returnsDeviceRecommendationOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithDeviceIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataDeviceRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalGeneralIssue_returnsOverallStatusGeneralCriticalOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalAccountIssue_returnsOverallStatusAccountCriticalOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingAccountIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataAccountCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalDeviceIssue_returnsOverallStatusDeviceCriticalOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataDeviceCriticalOneAlert)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withRecommendationDataIssue_returnsDataRecommendationStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultRecommendationIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DATA)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_data_recommendation_title",
                    OVERALL_SEVERITY_LEVEL_RECOMMENDATION
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withCriticalDataIssue_returnsDataCriticalStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultCriticalResolvingIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DATA)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_critical_data_warning_title",
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withRecommendationPasswordsIssue_returnsDataRecommendationStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultRecommendationIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_passwords_recommendation_title",
                    OVERALL_SEVERITY_LEVEL_RECOMMENDATION
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withCriticalPasswordsIssue_returnsDataCriticalStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultCriticalResolvingIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_critical_passwords_warning_title",
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withRecommendationPersonalIssue_returnsDataRecommendationStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultRecommendationIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PERSONAL_SAFETY)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_personal_recommendation_title",
                    OVERALL_SEVERITY_LEVEL_RECOMMENDATION
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withCriticalPersonalIssue_returnsDataCriticalStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultCriticalResolvingIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PERSONAL_SAFETY)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_critical_personal_warning_title",
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_infoStatusTipFirstIssueSingleTip_infoStatusWithTipSummary() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultInformationIssueBuilder()
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(safetyCenterTestData.safetyCenterStatusTips(numTipIssues = 1))
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_infoStatusTipFirstIssueMultiTips_infoStatusWithTipsSummary() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_1")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_2")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_3")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_4")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_5")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .build(),
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(safetyCenterTestData.safetyCenterStatusTips(numTipIssues = 3))
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_infoStatusActionFirstIssueSingleAction_infoStatusWithActionSummary() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultInformationIssueBuilder()
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(safetyCenterTestData.safetyCenterStatusActionsTaken(numAutomaticIssues = 1))
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_infoStatusActionFirstIssueMultiActions_infoStatusWithActionsSummary() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_1")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_2")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_3")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_4")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_5")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
                    .build(),
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(safetyCenterTestData.safetyCenterStatusActionsTaken(numAutomaticIssues = 2))
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_infoStatusManualFirstIssueSingleManual_infoStatusWithAlertSummary() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultInformationIssueBuilder()
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
                    .build(),
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusNAlerts(
                    "overall_severity_level_ok_title",
                    OVERALL_SEVERITY_LEVEL_OK,
                    numAlerts = 1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_infoStatusManualFirstIssueMultiManual_infoStatusWithAlertsSummary() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_1")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_2")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_TIP)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_3")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
                    .build(),
                safetySourceTestData
                    .defaultInformationIssueBuilder("id_4")
                    .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_AUTOMATIC)
                    .build(),
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusNAlerts(
                    "overall_severity_level_ok_title",
                    OVERALL_SEVERITY_LEVEL_OK,
                    numAlerts = 2
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_withStaticEntryGroups_hasStaticEntriesToIdsMapping() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.staticSourcesConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(
                SafetyCenterBundles.getStaticEntryId(
                    apiSafetyCenterData,
                    apiSafetyCenterData.staticEntryGroups[0].staticEntries[0]
                )
            )
            .isEqualTo(
                SafetyCenterEntryId.newBuilder()
                    .setSafetySourceId("test_static_source_id_1")
                    .setUserId(UserHandle.myUserId())
                    .build()
            )
        assertThat(
                SafetyCenterBundles.getStaticEntryId(
                    apiSafetyCenterData,
                    apiSafetyCenterData.staticEntryGroups[1].staticEntries[0]
                )
            )
            .isEqualTo(
                SafetyCenterEntryId.newBuilder()
                    .setSafetySourceId("test_static_source_id_2")
                    .setUserId(UserHandle.myUserId())
                    .build()
            )
    }

    @Test
    fun getSafetyCenterData_singleSourceIssues_returnsOverallStatusBasedOnHigherSeverityIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssueAndRecommendationIssue
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusDeviceCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_multipleSourcesIssues_returnsOverallStatusBasedOnHigherSeverityIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.recommendationWithAccountIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusDeviceCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_multipleIssues_returnsOverallStatusBasedOnConfigOrdering() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusGeneralCriticalTwoAlerts)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesOfSameSeverities_issueOfFirstSourceInConfigShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesInDifferentSourceGroups_topIssueRelevantForBothGroups() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1 and source group MULTIPLE_SOURCES_GROUP_ID_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1 and source group MULTIPLE_SOURCES_GROUP_ID_2
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val issueId = apiSafetyCenterData.issues.first().id
        val issueToGroupBelonging =
            apiSafetyCenterData.extras.getBundle(ISSUES_TO_GROUPS_BUNDLE_KEY)

        assertThat(issueToGroupBelonging!!.getStringArrayList(issueId))
            .containsExactly(MULTIPLE_SOURCES_GROUP_ID_1, MULTIPLE_SOURCES_GROUP_ID_2)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesInSameSourceGroups_topIssueRelevantForThatGroup() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1 and source group MULTIPLE_SOURCES_GROUP_ID_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1 and source group MULTIPLE_SOURCES_GROUP_ID_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val issueId = apiSafetyCenterData.issues.first().id
        val issueToGroupBelonging =
            apiSafetyCenterData.extras.getBundle(ISSUES_TO_GROUPS_BUNDLE_KEY)

        assertThat(issueToGroupBelonging!!.getStringArrayList(issueId))
            .containsExactly(MULTIPLE_SOURCES_GROUP_ID_1)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_noDuplicateIssues_noGroupBelongingSpecified() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1 and source group MULTIPLE_SOURCES_GROUP_ID_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3 and source group MULTIPLE_SOURCES_GROUP_ID_2
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val issueToGroupBelonging =
            apiSafetyCenterData.extras.getBundle(ISSUES_TO_GROUPS_BUNDLE_KEY)

        assertThat(issueToGroupBelonging).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_differentDuplicationId_bothIssuesShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("different")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                ),
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_differentDuplicationGroup_bothIssuesShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                ),
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_6,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_threeDuplicateIssues_onlyOneIssueShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_4,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesOfDifferentSeverities_moreSevereIssueShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_multipleDuplicationsOfIssues_correctlyDeduplicated() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("A")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("A")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_2
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("A")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    SOURCE_ID_3,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    SOURCE_ID_4,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesBothDismissed_topOneShownAsDismissed() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_5, RECOMMENDATION_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues).isEmpty()
        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesLowerSeverityOneDismissed_topOneShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_5, RECOMMENDATION_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
        assertThat(apiSafetyCenterDismissedIssues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesHigherSeverityOneDismissed_topOneShownAsDismissed() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues).isEmpty()
        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_dupIssuesLowerPrioritySameSeverityOneDismissed_topShownAsDismissed() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_5, CRITICAL_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues).isEmpty()
        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_dupIssuesTopOneDismissedThenDisappears_bottomOneReemergesTimely() {
        SafetyCenterFlags.tempHiddenIssueResurfaceDelay = Duration.ZERO
        SafetyCenterFlags.resurfaceIssueMaxCounts = mapOf(SEVERITY_LEVEL_CRITICAL_WARNING to 99L)
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(SEVERITY_LEVEL_CRITICAL_WARNING to RESURFACE_DELAY)
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )
        safetyCenterTestHelper.setData(SOURCE_ID_1, SafetySourceTestData.issuesOnly())

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfaced =
                safetyCenterManager
                    .getSafetyCenterDataWithPermission()
                    .issues
                    .contains(
                        safetyCenterTestData.safetyCenterIssueCritical(
                            SOURCE_ID_5,
                            groupId = MULTIPLE_SOURCES_GROUP_ID_2
                        )
                    )
            hasResurfaced
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_dupsOfDiffSeveritiesTopOneDismissedThenGone_bottomOneReemergesTimely() {
        SafetyCenterFlags.tempHiddenIssueResurfaceDelay = Duration.ZERO
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 0L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )
        safetyCenterTestHelper.setData(SOURCE_ID_1, SafetySourceTestData.issuesOnly())

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfaced =
                safetyCenterManager
                    .getSafetyCenterDataWithPermission()
                    .issues
                    .contains(
                        safetyCenterTestData.safetyCenterIssueRecommendation(
                            SOURCE_ID_5,
                            groupId = MULTIPLE_SOURCES_GROUP_ID_2
                        )
                    )
            hasResurfaced
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesLowerOneResurfaces_lowerOneStillFilteredOut() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 99L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to RESURFACE_DELAY.multipliedBy(100)
            )
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
                val hasResurfaced =
                    safetyCenterManager
                        .getSafetyCenterDataWithPermission()
                        .issues
                        .contains(
                            safetyCenterTestData.safetyCenterIssueRecommendation(
                                SOURCE_ID_5,
                                groupId = MULTIPLE_SOURCES_GROUP_ID_2
                            )
                        )
                hasResurfaced
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_duplicateIssuesTopOneResurfaces_topOneShown() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 99L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY.multipliedBy(100),
                SEVERITY_LEVEL_CRITICAL_WARNING to RESURFACE_DELAY
            )
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfaced =
                safetyCenterManager
                    .getSafetyCenterDataWithPermission()
                    .issues
                    .contains(
                        safetyCenterTestData.safetyCenterIssueCritical(
                            SOURCE_ID_1,
                            groupId = MULTIPLE_SOURCES_GROUP_ID_1
                        )
                    )
            hasResurfaced
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun getSafetyCenterData_dupIssuesTopOneResolved_bottomOneReemergesAfterTemporaryHiddenPeriod() {
        SafetyCenterFlags.tempHiddenIssueResurfaceDelay = RESURFACE_DELAY
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setData(SOURCE_ID_1, SafetySourceTestData.issuesOnly())

        val apiSafetyCenterIssues = listener.receiveSafetyCenterData().issues

        assertThat(apiSafetyCenterIssues).isEmpty()

        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfaced =
                safetyCenterManager
                    .getSafetyCenterDataWithPermission()
                    .issues
                    .contains(
                        safetyCenterTestData.safetyCenterIssueCritical(
                            SOURCE_ID_5,
                            groupId = MULTIPLE_SOURCES_GROUP_ID_2
                        )
                    )
            hasResurfaced
        }
    }

    @Test
    fun getSafetyCenterData_criticalDeviceIssues_returnsOverallStatusBasedOnAddIssueCallOrder() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData
                .defaultCriticalDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultCriticalResolvingIssueBuilder("critical issue num 1")
                        .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT)
                        .build()
                )
                .addIssue(
                    safetySourceTestData
                        .defaultCriticalResolvingIssueBuilder("critical issue num 2")
                        .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
                        .build()
                )
                .build()
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusAccountCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_withComplexConfigWithSomeDataProvided_returnsDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)
        safetyCenterTestHelper.setData(
            DYNAMIC_BAREBONE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            DYNAMIC_DISABLED_ID,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setData(
            DYNAMIC_HIDDEN_WITH_SEARCH_ID,
            safetySourceTestData.information
        )
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_BAREBONE_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.criticalResolvingGeneralIssue)
        )
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        )
        safetyCenterTestHelper.setData(
            DYNAMIC_IN_STATELESS_ID,
            safetySourceTestData.unspecifiedWithIssue
        )
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_IN_STATELESS_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData.withoutExtras())
            .isEqualTo(safetyCenterDataFromComplexConfigUpdated)
    }

    @Test
    fun getSafetyCenterData_withCriticalEntriesAsMax_usesCriticalSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                entrySummary = "recommendation"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING,
                entrySummary = "critical 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING,
                entrySummary = "critical 2"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("critical 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getSafetyCenterData_withRecommendationEntriesAsMax_usesRecommendationSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                entrySummary = "recommendation 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                entrySummary = "recommendation 2"
            )
        )
        // SOURCE_ID_7 leave as an UNKNOWN dynamic entry
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("recommendation 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
    }

    @Test
    fun getSafetyCenterData_withInformationWithIssueEntriesAsMax_usesInformationSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue 1",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue 2",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 3"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("information with issue 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withInformationEntriesAsMax_usesDefaultSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 3"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 3"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 4"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("OK")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withUnspecifiedEntriesAsMax_usesDefaultSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 3"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 4"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 6"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 7"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("OK")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
    }

    @Test
    fun getSafetyCenterData_withMultipleErrorEntries_usesSingularErrorSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_2,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        // SOURCE_ID_5 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information"
            )
        )
        // SOURCE_ID_7 leave as an UNKNOWN dynamic entry
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo(safetyCenterTestData.getRefreshErrorString(1))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withSingleErrorEntry_usesSingularErrorSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        // SOURCE_ID_2 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        // SOURCE_ID_5 leave as an UNKNOWN dynamic entry
        // SOURCE_ID_6 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo(safetyCenterTestData.getRefreshErrorString(1))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withUnknownEntries_usesUnknownSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        // SOURCE_ID_1 leave as an UNKNOWN dynamic entry
        // SOURCE_ID_2 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information"
            )
        )
        // SOURCE_ID_6 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified with issue",
                withIssue = true
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary)
            .isEqualTo(safetyCenterResourcesApk.getStringByName("group_unknown_summary"))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withAndroidLockScreenGroup_returnsCombinedTitlesAsSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.androidLockScreenSourcesConfig)

        val initialGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(initialGroup.summary)
            .isEqualTo(safetyCenterResourcesApk.getStringByName("group_unknown_summary"))
        assertThat(initialGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)

        safetyCenterTestHelper.setData(DYNAMIC_BAREBONE_ID, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setData(DYNAMIC_DISABLED_ID, safetySourceTestData.information)

        val partialGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(partialGroup.summary).isEqualTo("Unspecified title, Ok title")
        assertThat(partialGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)

        safetyCenterTestHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceTestData.information)

        val fullGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(fullGroup.summary).isEqualTo("Unspecified title, Ok title, Ok title")
        assertThat(fullGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)

        safetyCenterTestHelper.setData(
            DYNAMIC_DISABLED_ID,
            safetySourceTestData.informationWithIssue
        )

        val informationWithIssueGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(informationWithIssueGroup.summary).isEqualTo("Ok summary")
        assertThat(informationWithIssueGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_returnsDefaultData() {
        safetyCenterTestHelper.setEnabled(false)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(SafetyCenterTestData.DEFAULT)
    }

    @Test
    fun getSafetyCenterData_defaultDataWithIncorrectIntent_returnsDisabledEntries() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceInvalidIntentConfig)

        val safetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData
                            .safetyCenterEntryDefaultBuilder(SINGLE_SOURCE_ID)
                            .setPendingIntent(null)
                            .setEnabled(false)
                            .build()
                    )
                ),
                emptyList()
            )
        assertThat(safetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun getSafetyCenterData_withInvalidDefaultIntent_shouldReturnUnspecifiedSeverityLevel() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceInvalidIntentConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.informationWithNullIntent
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData
                            .safetyCenterEntryOkBuilder(SINGLE_SOURCE_ID)
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                            .setPendingIntent(null)
                            .setEnabled(false)
                            .build()
                    )
                ),
                emptyList()
            )
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWithSafetyCenterDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val listener = safetyCenterTestHelper.addListener(skipInitialData = false)

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnSafetySourceData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataChanges() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataCleared() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun dismissSafetyCenterIssue_existing_callsListenerAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewCriticalEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataFromListener).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun dismissSafetyCenterIssue_issueShowsUpAgainIfSourceStopsSendingItAtLeastOnce() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        val safetyCenterDataAfterSourceStopsSendingDismissedIssue =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourceStopsSendingDismissedIssue)
            .isEqualTo(safetyCenterDataOk)

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        val safetyCenterDataAfterSourcePushesDismissedIssueAgain =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourcePushesDismissedIssueAgain)
            .isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun dismissSafetyCenterIssue_existingWithDifferentIssueType_callsListenerAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                issueTypeId = "some_other_issue_type_id"
            )
        )

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewCriticalEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataFromListener).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun dismissSafetyCenterIssue_withDismissPendingIntent_callsDismissPendingIntentAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationDismissPendingIntentIssue
        )
        SafetySourceReceiver.setResponse(
            Request.DismissIssue(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID)
        )

        val safetyCenterDataAfterDismissal = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewRecommendationEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataAfterDismissal).isEqualTo(expectedSafetyCenterData)
        val safetyCenterDataAfterSourceHandledDismissal = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourceHandledDismissal).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun dismissSafetyCenterIssue_errorDispatchPendingIntent_doesntCallErrorListenerAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val recommendationDismissPendingIntentIssue =
            safetySourceTestData.recommendationDismissPendingIntentIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, recommendationDismissPendingIntentIssue)
        recommendationDismissPendingIntentIssue.issues.first().onDismissPendingIntent!!.cancel()
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID)
        )

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        val exectedSafetyCenterData =
            safetyCenterDataOkReviewRecommendationEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataFromListener).isEqualTo(exectedSafetyCenterData)
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withEmptyMaxCountMap_doesNotResurface() {
        SafetyCenterFlags.resurfaceIssueMaxCounts = emptyMap()
        SafetyCenterFlags.resurfaceIssueDelays = mapOf(SEVERITY_LEVEL_INFORMATION to Duration.ZERO)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataOkOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, INFORMATION_ISSUE_ID)
        )

        val expectedSafetyCenterData =
            safetyCenterDataOk.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID))
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() !=
                        expectedSafetyCenterData
                hasResurfaced
            }
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withZeroMaxCount_doesNotResurface() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 99L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to Duration.ZERO,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataOkOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, INFORMATION_ISSUE_ID)
        )

        val expectedSafetyCenterData =
            safetyCenterDataOk.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID))
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() !=
                        expectedSafetyCenterData
                hasResurfaced
            }
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withTwoMaxCount_resurfacesTwice() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 0L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 2L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataDeviceCriticalOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val apiSafetyCenterDataResurface = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterDataResurface == safetyCenterDataDeviceCriticalOneAlert)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val apiSafetyCenterDataResurfaceAgain =
            safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterDataResurfaceAgain == safetyCenterDataDeviceCriticalOneAlert)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        val expectedSafetyCenterData =
            safetyCenterDataOkReviewCriticalEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID))
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() !=
                        expectedSafetyCenterData

                hasResurfaced
            }
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withNonZeroMaxCountAndNonZeroDelay_resurfacesAfterDelay() {
        // We cannot rely on a listener in this test to assert on the API content at all times!
        // The listener will not receive an update when a dismissed issue resurfaces, and it will
        // not receive an update after subsequent dismissals because as far as the listener cache is
        // concerned the dismissed issue never resurfaced. This is working as intended.
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 0L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithDeviceIssue
        )
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataDeviceRecommendationOneAlert)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID)
        )

        val safetyCenterDataAfterDismissal = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewRecommendationEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataAfterDismissal).isEqualTo(expectedSafetyCenterData)
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfacedExactly =
                safetyCenterManager.getSafetyCenterDataWithPermission() ==
                    safetyCenterDataDeviceRecommendationOneAlert
            hasResurfacedExactly
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_callsListenerWithInFlightActionAndExecutes() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_unmarkInFlightWhenResolveActionError() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction)
            .isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesApk.getStringByName("resolving_action_error")
                )
            )
    }

    @Test
    fun executeSafetyCenterIssueAction_sourceDoesNotRespond_timesOutAndCallsListener() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction)
            .isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesApk.getStringByName("resolving_action_error")
                )
            )
    }

    @Test
    fun executeSafetyCenterIssueAction_tryAgainAfterTimeout_callsListenerWithInFlightAndExecutes() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_LONG
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_allowsDismissedIssuesToExecuteActions() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        val issueId = SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(issueId)
        val safetyCenterDataAfterDismissal = listener.receiveSafetyCenterData()
        checkState(safetyCenterDataAfterDismissal.status == safetyCenterStatusOkReview)

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            issueId,
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        if (SdkLevel.isAtLeastU()) {
            // On U+, the dismissed issue is marked as "in-flight" before resolving.
            val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
            assertThat(safetyCenterDataFromListenerDuringResolveAction)
                .isEqualTo(safetyCenterDataOkReviewOneDismissedAlertInFlight)
        }
        // On T, the dismissed issue is never shown, so only the status will change after
        // resolution.
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_idsDontMatch_canStillResolve() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID + "invalid",
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
        }

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun refreshSafetySources_forSettingsSources_providesDataToSafetyCenter() {
        val settingsSourcesWithEntries =
            safetyCenterManager.getSafetyCenterConfigWithPermission().getDynamicSettingsSources()
        assumeFalse(settingsSourcesWithEntries.isEmpty())
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_RESCAN_BUTTON_CLICK)

        // Wait until some data is provided by all the Settings sources that have a user-visible
        // entry. This will exclude the hidden-by-default entries (unless some data ends up being
        // provided for them).
        waitForWithTimeout {
            val data = listener.receiveSafetyCenterData()
            val entries =
                data.entriesOrGroups.flatMap {
                    val entry = it.entry
                    if (entry != null) {
                        listOf(entry)
                    } else {
                        it.entryGroup!!.entries
                    }
                }
            val visibleSettingsEntries =
                settingsSourcesWithEntries.mapNotNull { settingsSource ->
                    entries.find { entry ->
                        val entrySourceId =
                            SafetyCenterIds.entryIdFromString(entry.id).safetySourceId
                        entrySourceId == settingsSource.id
                    }
                }
            val visibleSettingEntriesHaveData =
                visibleSettingsEntries.all { it.severityLevel != ENTRY_SEVERITY_LEVEL_UNKNOWN }

            visibleSettingEntriesHaveData
        }
    }

    @Test
    fun beforeAnyDataSet_noLastUpdatedTimestamps() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val lastUpdated = dumpLastUpdated()
        assertThat(lastUpdated).isEmpty()
    }

    @Test
    fun setSafetySourceData_setsLastUpdatedTimestamp() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        val lastUpdated = dumpLastUpdated()
        val key = lastUpdated.keys.find { it.contains(SINGLE_SOURCE_ID) }
        assertThat(key).isNotNull()
        assertThat(lastUpdated[key]).isNotNull()
    }

    @Test
    fun setSafetySourceData_twice_updatesLastUpdatedTimestamp() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        val initialEntry = dumpLastUpdated().entries.find { it.key.contains(SINGLE_SOURCE_ID) }
        assertThat(initialEntry).isNotNull()

        Thread.sleep(1) // Ensure uptime millis will actually be different
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        val updatedValue = dumpLastUpdated()[initialEntry!!.key]
        assertThat(updatedValue).isNotNull()
        assertThat(updatedValue).isNotEqualTo(initialEntry.value)
    }

    @Test
    fun setSafetySourceError_setsLastUpdatedTimestamp() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )

        val lastUpdated = dumpLastUpdated()
        val key = lastUpdated.keys.find { it.contains(SINGLE_SOURCE_ID) }
        assertThat(key).isNotNull()
        assertThat(lastUpdated[key]).isNotNull()
    }

    @Test
    fun setSafetySourceData_dynamicHiddenWithIssueOnlyData_allowed() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.hiddenSourceConfig)
        val expectedData = SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)

        safetyCenterTestHelper.setData(DYNAMIC_HIDDEN_ID, expectedData)

        val actualData = safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_HIDDEN_ID)
        assertThat(actualData).isEqualTo(expectedData)
    }

    private fun dumpLastUpdated(): Map<String, String> {
        val dump = SystemUtil.runShellCommand("dumpsys safety_center data")
        return dump
            .linesAfter { it.contains("LAST UPDATED") }
            .map { line -> Regex("""\[\d+] (.+) -> (\d+)""").matchEntire(line.trim()) }
            .takeWhile { it != null }
            .associate { matchResult -> matchResult!!.groupValues[1] to matchResult.groupValues[2] }
    }

    private fun String.linesAfter(predicate: (String) -> Boolean): List<String> =
        split('\n').dropWhile { !predicate(it) }.drop(1)

    private fun SafetyCenterData.getGroup(groupId: String): SafetyCenterEntryGroup =
        entriesOrGroups.first { it.entryGroup?.id == groupId }.entryGroup!!

    private fun SafetyCenterConfig?.getDynamicSettingsSources(): List<SafetySource> {
        if (this == null) {
            return emptyList()
        }
        return safetySourcesGroups
            .flatMap { it.safetySources }
            .filter {
                it.type == SAFETY_SOURCE_TYPE_DYNAMIC &&
                    it.packageName == context.getSettingsPackageName()
            }
    }

    companion object {
        private val RESURFACE_DELAY = Duration.ofMillis(500)
        // Wait 1.5 times the RESURFACE_DELAY before asserting whether an issue has or has not
        // resurfaced. Use a constant additive error buffer if we increase the delay considerably.
        private val RESURFACE_TIMEOUT = RESURFACE_DELAY.multipliedBy(3).dividedBy(2)
        // Check more than once during a RESURFACE_DELAY before asserting whether an issue has or
        // has not resurfaced. Use a different check logic (focused at the expected resurface time)
        // if we increase the delay considerably.
        private val RESURFACE_CHECK = RESURFACE_DELAY.dividedBy(4)
    }
}
