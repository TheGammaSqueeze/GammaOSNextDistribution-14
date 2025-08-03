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
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.UserHandle.USER_NULL
import android.os.UserManager
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PERIODIC
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_DEVICE
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_GENERAL
import android.safetycenter.cts.testing.FakeExecutor
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.resources.SafetyCenterResourcesApk
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.executeSafetyCenterIssueActionWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterEnabledChangedReceiver
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_IN_STATELESS_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_OTHER_PACKAGE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.PACKAGE_CERT_HASH_INVALID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SAMPLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_3
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.STATIC_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestListener
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.dismissSafetyCenterIssueWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithoutReceiverPermissionAndWait
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.android.safetycenter.testing.SafetySourceTestData.Companion.RECOMMENDATION_ISSUE_ID
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.google.common.base.Preconditions.checkState
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterResourcesApk = SafetyCenterResourcesApk.forTests(context)
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2) val safetyCenterTestRule = SafetyCenterTestRule(safetyCenterTestHelper)

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsTrue() {
        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isTrue()
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
    fun setSafetySourceData_validId_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.unspecified
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_twice_replacesValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        val dataToSet = safetySourceTestData.criticalWithResolvingGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_null_clearsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_sourceInStatelessGroupUnspecified_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val dataToSet = safetySourceTestData.unspecified
        safetyCenterTestHelper.setData(DYNAMIC_IN_STATELESS_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_IN_STATELESS_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_staticId_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(STATIC_BAREBONE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_differentPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_OTHER_PACKAGE_ID,
                    safetySourceTestData.unspecified
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_wronglySignedPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceWithFakeCert)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Invalid signature for package " + context.packageName)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_wronglySignedPackageButAllowedByFlag_isAllowed() {
        SafetyCenterFlags.allowedAdditionalPackageCerts =
            mapOf(context.packageName to setOf(safetyCenterTestConfigs.packageCertHash))
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceWithFakeCert)

        val dataToSet = safetySourceTestData.unspecified
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun setSafetySourceData_invalidPackageCertificate_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceWithInvalidCert)

        val thrown =
            assertFailsWith(IllegalStateException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Failed to parse signing certificate: " + PACKAGE_CERT_HASH_INVALID)
    }

    @Test
    fun setSafetySourceData_sourceInStatelessGroupNotUnspecified_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_IN_STATELESS_ID,
                    safetySourceTestData.information
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .matches(
                "Safety source: $DYNAMIC_IN_STATELESS_ID is in a (stateless|rigid) group but " +
                    "specified a severity level: $SEVERITY_LEVEL_INFORMATION"
            )
    }

    @Test
    fun setSafetySourceData_nullUnknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_nullStaticId_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(STATIC_BAREBONE_ID, safetySourceData = null)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_nullDifferentPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(DYNAMIC_OTHER_PACKAGE_ID, safetySourceData = null)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    fun setSafetySourceData_issueOnlyWithStatus_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    ISSUE_ONLY_BAREBONE_ID,
                    safetySourceTestData.unspecified
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected status for issue only safety source: $ISSUE_ONLY_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_dynamicWithIssueOnly_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_BAREBONE_ID,
                    SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Missing status for dynamic safety source: $DYNAMIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevUnspecified_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val dataToSet = safetySourceTestData.unspecified
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformation_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val dataToSet = safetySourceTestData.information
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformationWithIssue_throwsException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.informationWithIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_INFORMATION
                        }, for issue in safety source: $SINGLE_SOURCE_ID"
            )
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevCritical_throwsException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.criticalWithResolvingGeneralIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                        }, for safety source: $SINGLE_SOURCE_ID"
            )
    }

    @Test
    fun setSafetySourceData_withMaxSevRecommendation_met() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val dataToSet = safetySourceTestData.recommendationWithGeneralIssue
        safetyCenterTestHelper.setData(DYNAMIC_ALL_OPTIONAL_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_ALL_OPTIONAL_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevRecommendation_notMet() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_ALL_OPTIONAL_ID,
                    safetySourceTestData.criticalWithResolvingGeneralIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                        }, for safety source: $DYNAMIC_ALL_OPTIONAL_ID"
            )
    }

    @Test
    fun setSafetySourceData_issueOnlyWithMaxSevRecommendation_met() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val dataToSet =
            SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        safetyCenterTestHelper.setData(ISSUE_ONLY_ALL_OPTIONAL_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(ISSUE_ONLY_ALL_OPTIONAL_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_issueOnlyWithMaxSevRecommendation_notMet() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    ISSUE_ONLY_ALL_OPTIONAL_ID,
                    SafetySourceTestData.issuesOnly(
                        safetySourceTestData.criticalResolvingGeneralIssue
                    )
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                        }, for issue in safety source: $ISSUE_ONLY_ALL_OPTIONAL_ID"
            )
    }

    @Test
    fun setSafetySourceData_withEmptyCategoryAllowlists_met() {
        SafetyCenterFlags.issueCategoryAllowlists = emptyMap()
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.recommendationWithAccountIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMissingAllowlistForCategory_met() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_DEVICE to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_GENERAL to setOf(SAMPLE_SOURCE_ID)
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.recommendationWithAccountIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withAllowlistedSourceForCategory_met() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_ACCOUNT to setOf(SINGLE_SOURCE_ID, SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_DEVICE to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_GENERAL to setOf(SAMPLE_SOURCE_ID)
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.recommendationWithAccountIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withEmptyAllowlistForCategory_notMet() {
        SafetyCenterFlags.issueCategoryAllowlists = mapOf(ISSUE_CATEGORY_ACCOUNT to emptySet())
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.recommendationWithAccountIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected issue category: $ISSUE_CATEGORY_ACCOUNT, for issue in safety source: " +
                    SINGLE_SOURCE_ID
            )
    }

    @Test
    fun setSafetySourceData_withoutSourceInAllowlistForCategory_notMet() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_ACCOUNT to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_DEVICE to setOf(SINGLE_SOURCE_ID)
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.recommendationWithAccountIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected issue category: $ISSUE_CATEGORY_ACCOUNT, for issue in safety source: " +
                    SINGLE_SOURCE_ID
            )
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_doesntSetData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceTestData.unspecified,
            EVENT_SOURCE_STATE_CHANGED
        )

        safetyCenterTestHelper.setEnabled(true)
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
    fun getSafetySourceData_validId_noData_returnsNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun getSafetySourceData_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun getSafetySourceData_staticId_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(STATIC_BAREBONE_ID)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun getSafetySourceData_differentPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_OTHER_PACKAGE_ID)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    fun getSafetySourceData_withFlagDisabled_returnsNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setEnabled(false)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun getSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.getSafetySourceData(SINGLE_SOURCE_ID)
        }
    }

    @Test
    fun reportSafetySourceError_changesSafetyCenterDataButDoesntCallErrorListener() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )

        listener.receiveSafetyCenterData()
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun reportSafetySourceError_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    SINGLE_SOURCE_ID,
                    SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
                )
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun reportSafetySourceError_staticId_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    STATIC_BAREBONE_ID,
                    SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun reportSafetySourceError_differentPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    DYNAMIC_OTHER_PACKAGE_ID,
                    SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    fun reportSafetySourceError_withFlagDisabled_doesntCallListener() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

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
    fun safetyCenterEnabledChanged_whenImplicitReceiverHasPermission_receiverCalled() {
        // Implicit broadcast is only sent to system user.
        assumeTrue(context.getSystemService(UserManager::class.java)!!.isSystemUser)
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)

        val receiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
        enabledChangedReceiver.unregister()
    }

    @Test
    fun safetyCenterEnabledChanged_whenImplicitReceiverDoesntHavePermission_receiverNotCalled() {
        // Implicit broadcast is only sent to system user.
        assumeTrue(context.getSystemService(UserManager::class.java)!!.isSystemUser)
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.setSafetyCenterEnabledWithoutReceiverPermissionAndWait(false)
        }
        enabledChangedReceiver.unregister()
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverHasPermission_receiverCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val receiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
    }

    @Test
    fun safetyCenterEnabledChanged_valueDoesntChange_receiverNotCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                true,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverDoesntHavePermission_receiverNotCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithoutReceiverPermissionAndWait(false)
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverNotInConfig_receiverNotCalled() {
        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_sourceSendsRescanData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
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
    fun refreshSafetySources_allowsRefreshingInAForegroundService() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.runInForegroundService = true
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
    fun refreshSafetySources_reasonPageOpen_noConditionsMet_noBroadcastSent() {
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

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_allowedByConfig_broadcastSent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
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
    fun refreshSafetySources_whenSourceClearsData_sourceSendsNullData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetySourceReceiver.setResponse(Request.Rescan(SINGLE_SOURCE_ID), Response.ClearData)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesInConfig_multipleSourcesSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        SafetySourceReceiver.apply {
            setResponse(
                Request.Rescan(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
            )
            setResponse(
                Request.Rescan(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.information)
            )
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isNull()
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesOnPageOpen_onlyUpdatesAllowedSources() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.information)
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.information)
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.informationWithIssue)
            )
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isNull()
        // SOURCE_ID_3 doesn't support refresh on page open.
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_whenReceiverDoesntHavePermission_sourceDoesntSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithoutReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK
            )
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_whenSourceNotInConfig_sourceDoesntSendData() {
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                timeout = TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_sendsBroadcastId() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val lastReceivedBroadcastId =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK
            )

        assertThat(lastReceivedBroadcastId).isNotNull()
    }

    @Test
    fun refreshSafetySources_sendsDifferentBroadcastIdsOnEachMethodCall() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        val broadcastId1 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK
            )
        val broadcastId2 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK
            )

        assertThat(broadcastId1).isNotEqualTo(broadcastId2)
    }

    @Test
    fun refreshSafetySources_repliesWithWrongBroadcastId_doesntCompleteRefresh() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information, overrideBroadcastId = "invalid")
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        // Because wrong ID, refresh hasn't finished. Wait for timeout.
        listener.waitForSafetyCenterRefresh(withErrorEntry = true)
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)

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
    fun refreshSafetySources_refreshAfterSuccessfulRefresh_completesSuccessfully() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_refreshAfterFailedRefresh_completesSuccessfully() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(Request.Rescan(SINGLE_SOURCE_ID), Response.Error)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_waitForPreviousRefreshToTimeout_completesSuccessfully() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData1).isNull()
        // Wait for the ongoing refresh to timeout.
        listener.waitForSafetyCenterRefresh(withErrorEntry = true)
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_withoutAllowingPreviousRefreshToTimeout_completesSuccessfully() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
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
    fun refreshSafetySources_withTrackedSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.setResponse(
                Request.Rescan(sourceId),
                Response.SetData(safetySourceTestData.information)
            )
        }
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        listener.waitForSafetyCenterRefresh(withErrorEntry = true)
    }

    @Test
    fun refreshSafetySources_withUntrackedSourceThatTimesOut_doesNotTimeOut() {
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.setResponse(
                Request.Rescan(sourceId),
                Response.SetData(safetySourceTestData.information)
            )
        }
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        listener.waitForSafetyCenterRefresh(withErrorEntry = false)
    }

    @Test
    fun refreshSafetySources_withMultipleUntrackedSourcesThatTimeOut_doesNotTimeOut() {
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1, SOURCE_ID_2)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // SOURCE_ID_1 and SOURCE_ID_2 will timeout
        SafetySourceReceiver.setResponse(
            Request.Rescan(SOURCE_ID_3),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        listener.waitForSafetyCenterRefresh(withErrorEntry = false)
    }

    @Test
    fun refreshSafetySources_withEmptyUntrackedSourceConfigAndSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        // SINGLE_SOURCE_ID will timeout
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        listener.waitForSafetyCenterRefresh(withErrorEntry = true)
    }

    @Test
    fun refreshSafetySources_withTrackedSourceThatHasNoReceiver_doesNotTimeOut() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceOtherPackageConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            // In this case a refresh isn't even started because there is only a single source
            // without a receiver.
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withFlagDisabled_doesntRefreshSources() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                timeout = TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_withInvalidRefreshSeason_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.refreshSafetySourcesWithPermission(143201)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected refresh reason: 143201")
    }

    @Test
    fun refreshSafetySources_withRefreshReasonOther_backgroundRefreshDeniedSourcesDoNotSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // All three sources have data
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_2),
                Response.SetData(safetySourceTestData.information)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.information)
            )
        }
        // But sources 1 and 3 should not be refreshed in background
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SOURCE_ID_1, SOURCE_ID_3)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(REFRESH_REASON_OTHER)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isNull()
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceTestData.information)
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isNull()
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_noBackgroundRefreshSourceSendsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonButtonClicked_noBackgroundRefreshSourceSendsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun refreshSafetySources_withRefreshReasonPeriodic_noBackgroundRefreshSourceDoesNotSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PERIODIC,
                timeout = TIMEOUT_SHORT
            )
        }

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun refreshSafetySources_withRefreshReasonPeriodic_backgroundRefreshSourceSendsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PERIODIC
        )

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun refreshSafetySources_withSafetySourceIds_onlySpecifiedSourcesSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.information)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_2),
                Response.SetData(safetySourceTestData.information)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.information)
            )
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN,
            safetySourceIds = listOf(SOURCE_ID_1, SOURCE_ID_2)
        )

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceTestData.information)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceTestData.information)
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun refreshSafetySources_withEmptySafetySourceIds_noSourcesSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                safetySourceIds = emptyList(),
                timeout = TIMEOUT_SHORT,
            )
        }

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isNull()
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun refreshSafetySources_versionLessThanU_throws() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)

        assertFails {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                safetySourceIds = listOf(SOURCE_ID_1, SOURCE_ID_3)
            )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE)
    fun refreshSafetySources_withSafetySourceIds_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_PAGE_OPEN, listOf())
        }
    }

    @Test
    fun refreshSafetySources_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
    }

    @Test
    fun getSafetyCenterConfig_withFlagEnabled_isNotNull() {
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNotNull()
    }

    @Test
    fun getSafetyCenterConfig_withFlagDisabled_isNotNull() {
        safetyCenterTestHelper.setEnabled(false)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNotNull()
    }

    @Test
    fun getSafetyCenterConfig_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterConfig }
    }

    @Test
    fun getSafetyCenterData_withoutDataProvided_isNotNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotNull()
    }

    @Test
    fun getSafetyCenterData_withSomeDataProvided_returnsDifferentData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val previousApiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotEqualTo(previousApiSafetyCenterData)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_isNotNull() {
        safetyCenterTestHelper.setEnabled(false)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotNull()
    }

    @Test
    fun getSafetyCenterData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterData }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnInit() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val listener = safetyCenterTestHelper.addListener(skipInitialData = false)

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnSafetySourceData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isNotNull()
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

        assertThat(safetyCenterDataFromListener).isNotNull()
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

        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataStaysNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataDoesntChange() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        val dataToSet = safetySourceTestData.information
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_oneShot_doesntDeadlock() {
        val listener = SafetyCenterTestListener()
        val oneShotListener =
            object : OnSafetyCenterDataChangedListener {
                override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {
                    safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(this)
                    listener.onSafetyCenterDataChanged(safetyCenterData)
                }
            }
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            oneShotListener
        )

        // Check that we don't deadlock when using a one-shot listener. This is because adding the
        // listener could call it while holding a lock; which would cause a deadlock if the listener
        // wasn't oneway.
        listener.receiveSafetyCenterData()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withFlagDisabled_listenerNotCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

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
    fun removeOnSafetyCenterDataChangedListener_listenerRemovedNotCalledOnSafetySourceData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener1 = safetyCenterTestHelper.addListener()
        val listener2 = safetyCenterTestHelper.addListener()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener2)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        listener1.receiveSafetyCenterData()
        assertFailsWith(TimeoutCancellationException::class) {
            listener2.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNeverCalledAfterRemoving() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val fakeExecutor = FakeExecutor()
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor,
            listener
        )
        fakeExecutor.getNextTask().run()
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val callListenerTask = fakeExecutor.getNextTask()
        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        // Simulate the submitted task being run *after* the remove call completes. Our API should
        // guard against this raciness, as users of this class likely don't expect their listener to
        // be called after calling #removeOnSafetyCenterDataChangedListener.
        callListenerTask.run()

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withFlagDisabled_removesListener() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)

        safetyCenterTestHelper.setEnabled(true)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        // Listener is removed as a side effect of the ENABLED_CHANGED broadcast.
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = safetyCenterTestHelper.addListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withDismissPendingIntent_callsDismissPendingIntent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationDismissPendingIntentIssue
        )
        val apiSafetySourceDataBeforeDismissal =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        checkState(
            apiSafetySourceDataBeforeDismissal ==
                safetySourceTestData.recommendationDismissPendingIntentIssue
        )
        SafetySourceReceiver.setResponse(
            Request.DismissIssue(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.dismissSafetyCenterIssueWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID)
        )

        val apiSafetySourceDataAfterDismissal =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceDataAfterDismissal).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun dismissSafetyCenterIssue_nonExisting_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, "some_unknown_id")
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_alreadyDismissed_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_dismissedWithDifferentIssueType_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                issueTypeId = "some_other_issue_type_id"
            )
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withFlagDisabled_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_invalidId_throwsIllegalArgumentException() {
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.dismissSafetyCenterIssueWithPermission("bleh")
        }
    }

    @Test
    fun dismissSafetyCenterIssue_invalidUser_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyCenterIssueWithPermission(
                SafetyCenterTestData.issueId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    userId = USER_NULL
                )
            )
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyCenterIssue("bleh")
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_executes() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val apiSafetySourceDataBeforeExecution =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        checkState(
            apiSafetySourceDataBeforeExecution ==
                safetySourceTestData.criticalWithResolvingGeneralIssue
        )
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

        val apiSafetySourceDataAfterExecution =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceDataAfterExecution).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_errorWithDispatchingRedirectingAction() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val criticalWithRedirectingIssue = safetySourceTestData.criticalWithRedirectingIssue
        criticalWithRedirectingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, criticalWithRedirectingIssue)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesApk.getStringByName("redirecting_error")
                )
            )
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_errorWithDispatchingResolvingAction() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val criticalWithResolvingIssue = safetySourceTestData.criticalWithResolvingGeneralIssue
        criticalWithResolvingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, criticalWithResolvingIssue)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesApk.getStringByName("resolving_action_error")
                )
            )
    }

    @Test
    // This test runs the default no-op implementation of OnSafetyCenterDataChangedListener#onError
    // for code coverage purposes.
    fun executeSafetyCenterIssueAction_errorWithDispatchingOnDefaultErrorListener() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val criticalWithRedirectingIssue = safetySourceTestData.criticalWithRedirectingIssue
        criticalWithRedirectingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, criticalWithRedirectingIssue)
        val fakeExecutor = FakeExecutor()
        val listener =
            object : OnSafetyCenterDataChangedListener {
                override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {}
            }
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor,
            listener
        )
        fakeExecutor.getNextTask().run()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )
        fakeExecutor.getNextTask().run()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
    }

    @Test
    fun executeSafetyCenterIssueAction_nonExisting_doesntCallListenerOrExecute() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val listener = safetyCenterTestHelper.addListener()

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
    fun executeSafetyCenterIssueAction_alreadyInFlight_doesntCallListenerOrExecute() {
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
    fun executeSafetyCenterIssueAction_withFlagDisabled_doesntCallListenerOrExecute() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

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
    fun executeSafetyCenterIssueAction_invalidId_throwsIllegalArgumentException() {
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermission("barf", "burgh")
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_issueIdDoesNotMatch_throwsErrorAndDoesNotResolveIssue() {
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

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_actionIdDoesNotMatch_doesNotResolveIssue() {
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

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID + "invalid"
                ),
                TIMEOUT_SHORT
            )
        }

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_sourceIdsDontMatch_throwsIllegalArgumentException() {
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SOURCE_ID_1,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID
                )
            )
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_invalidUser_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
                SafetyCenterTestData.issueId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    userId = USER_NULL
                ),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID,
                    userId = USER_NULL
                )
            )
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueAction("bleh", "blah")
        }
    }

    @Test
    fun clearAllSafetySourceDataForTests_clearsAllSafetySourceData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        val data1AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(data1AfterClearing).isNull()
        val data2AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(data2AfterClearing).isNull()
    }

    @Test
    fun clearAllSafetySourceDataForTests_withFlagDisabled_clearsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        safetyCenterTestHelper.setEnabled(true)
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        // Data is cleared as a side effect of the ENABLED_CHANGED broadcast.
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun clearAllSafetySourceDataForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearAllSafetySourceDataForTests()
        }
    }

    @Test
    fun setSafetyCenterConfigForTests_setsConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isEqualTo(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun setSafetyCenterConfigForTests_withFlagDisabled_doesntSetConfig() {
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )

        safetyCenterTestHelper.setEnabled(true)
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNotEqualTo(safetyCenterTestConfigs.singleSourceConfig)
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
    fun clearSafetyCenterConfigForTests_clearsConfigSetForTests_doesntSetConfigToNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNotNull()
        assertThat(config).isNotEqualTo(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withFlagDisabled_doesntClearConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        safetyCenterTestHelper.setEnabled(true)
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isEqualTo(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearSafetyCenterConfigForTests()
        }
    }
}
