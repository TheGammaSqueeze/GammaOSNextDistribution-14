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

package android.safetycenter.functional.multiusers

import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.Manifest.permission.INTERACT_ACROSS_USERS_FULL
import android.app.PendingIntent
import android.content.Context
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasCloneProfile
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.harrier.annotations.EnsureHasPrivateProfile
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.types.OptionalBoolean.TRUE
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.safetycenter.resources.SafetyCenterResourcesApk
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.NotificationCharacteristics
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_DISABLED_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_HIDDEN_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_IN_STATELESS_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_ALL_PROFILE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_IN_STATELESS_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MIXED_STATELESS_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ALL_PROFILE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.STATIC_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.STATIC_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.STATIC_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withoutExtras
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestRule
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.android.safetycenter.testing.SafetySourceTestData.Companion.ISSUE_TYPE_ID
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.android.safetycenter.testing.SupportsSafetyCenterRule
import com.android.safetycenter.testing.TestNotificationListener
import com.android.safetycenter.testing.UiTestHelper.waitAllTextDisplayed
import com.android.safetycenter.testing.UiTestHelper.waitAllTextNotDisplayed
import com.google.common.base.Preconditions.checkState
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Functional tests for our APIs and UI on a device with multiple users. e.g. with a managed or
 * additional user(s).
 */
@LargeTest
@RunWith(BedsteadJUnit4::class)
class SafetyCenterMultiUsersTest {

    companion object {
        @JvmField @ClassRule @Rule val deviceState: DeviceState = DeviceState()
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterResourcesApk = SafetyCenterResourcesApk.forTests(context)
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestData = SafetyCenterTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    private var inQuietMode = false

    private val primaryProfileOnlyIssues: List<SafetyCenterIssue>
        get() =
            listOf(
                safetyCenterTestData.safetyCenterIssueCritical(
                    DYNAMIC_BAREBONE_ID,
                    groupId = DYNAMIC_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueCritical(
                    ISSUE_ONLY_BAREBONE_ID,
                    attributionTitle = null,
                    groupId = ISSUE_ONLY_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    DYNAMIC_DISABLED_ID,
                    groupId = DYNAMIC_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    ISSUE_ONLY_ALL_OPTIONAL_ID,
                    attributionTitle = null,
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
            )

    private val dynamicBareboneDefault: SafetyCenterEntry
        get() = safetyCenterTestData.safetyCenterEntryDefault(DYNAMIC_BAREBONE_ID)

    private val dynamicBareboneUpdated: SafetyCenterEntry
        get() = safetyCenterTestData.safetyCenterEntryCritical(DYNAMIC_BAREBONE_ID)

    private val dynamicDisabledDefault: SafetyCenterEntry
        get() =
            safetyCenterTestData
                .safetyCenterEntryDefaultBuilder(DYNAMIC_DISABLED_ID)
                .setPendingIntent(null)
                .setEnabled(false)
                .build()

    private val dynamicDisabledUpdated: SafetyCenterEntry
        get() = safetyCenterTestData.safetyCenterEntryRecommendation(DYNAMIC_DISABLED_ID)

    private val dynamicDisabledForWorkDefaultBuilder: SafetyCenterEntry.Builder
        get() =
            safetyCenterTestData
                .safetyCenterEntryDefaultBuilder(
                    DYNAMIC_DISABLED_ID,
                    userId = deviceState.workProfile().id(),
                    title = "Paste"
                )
                .setPendingIntent(null)
                .setEnabled(false)

    private val dynamicDisabledForWorkDefault: SafetyCenterEntry
        get() = dynamicDisabledForWorkDefaultBuilder.build()

    private val dynamicDisabledForWorkPausedUpdated: SafetyCenterEntry
        get() =
            safetyCenterTestData
                .safetyCenterEntryDefaultBuilder(
                    DYNAMIC_DISABLED_ID,
                    deviceState.workProfile().id(),
                    title = "Ok title for Work",
                    pendingIntent = null
                )
                .setSummary(
                    safetyCenterResourcesApk.getStringByName("work_profile_paused"),
                )
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                .setEnabled(false)
                .build()

    private val dynamicDisabledForWorkUpdated: SafetyCenterEntry
        get() = safetyCenterEntryOkForWork(DYNAMIC_DISABLED_ID, deviceState.workProfile().id())

    private val dynamicHiddenUpdated: SafetyCenterEntry
        get() =
            safetyCenterTestData.safetyCenterEntryUnspecified(
                DYNAMIC_HIDDEN_ID,
                pendingIntent = null
            )

    private val dynamicHiddenForWorkUpdated: SafetyCenterEntry
        get() = safetyCenterEntryOkForWork(DYNAMIC_HIDDEN_ID, deviceState.workProfile().id())

    private val dynamicHiddenForWorkPausedUpdated
        get() =
            safetyCenterTestData
                .safetyCenterEntryDefaultBuilder(
                    DYNAMIC_HIDDEN_ID,
                    deviceState.workProfile().id(),
                    title = "Ok title for Work",
                    pendingIntent = null
                )
                .setSummary(
                    safetyCenterResourcesApk.getStringByName("work_profile_paused"),
                )
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                .setEnabled(false)
                .build()

    private val dynamicDisabledForPrivateUpdated: SafetyCenterEntry
        get() =
            safetyCenterEntryOkForPrivate(DYNAMIC_DISABLED_ID, deviceState.privateProfile().id())

    private val dynamicHiddenForPrivateUpdated: SafetyCenterEntry
        get() = safetyCenterEntryOkForPrivate(DYNAMIC_HIDDEN_ID, deviceState.privateProfile().id())

    private val staticGroupBuilder =
        SafetyCenterEntryGroup.Builder(STATIC_GROUP_ID, "OK")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
            .setSummary("OK")

    private val staticBarebone =
        safetyCenterTestData
            .safetyCenterEntryDefaultStaticBuilder(STATIC_BAREBONE_ID)
            .setSummary(null)
            .build()

    private val staticAllOptional =
        safetyCenterTestData.safetyCenterEntryDefaultStaticBuilder(STATIC_ALL_OPTIONAL_ID).build()

    private val staticAllOptionalForWorkBuilder
        get() =
            safetyCenterTestData
                .safetyCenterEntryDefaultStaticBuilder(
                    STATIC_ALL_OPTIONAL_ID,
                    userId = deviceState.workProfile().id(),
                    title = "Paste"
                )
                .setPendingIntent(
                    createTestActivityRedirectPendingIntentForUser(
                        deviceState.workProfile().userHandle(),
                        explicit = false
                    )
                )

    private val staticAllOptionalForWork
        get() = staticAllOptionalForWorkBuilder.build()

    private val staticAllOptionalForWorkPaused
        get() =
            staticAllOptionalForWorkBuilder
                .setSummary(safetyCenterResourcesApk.getStringByName("work_profile_paused"))
                .setEnabled(false)
                .build()

    private val staticAllOptionalForPrivateBuilder
        get() =
            safetyCenterTestData
                .safetyCenterEntryDefaultStaticBuilder(
                    STATIC_ALL_OPTIONAL_ID,
                    userId = deviceState.privateProfile().id(),
                    title = "Unknown"
                )
                .setPendingIntent(
                    createTestActivityRedirectPendingIntentForUser(
                        deviceState.privateProfile().userHandle(),
                        explicit = false
                    )
                )

    private val staticAllOptionalForPrivate
        get() = staticAllOptionalForPrivateBuilder.build()

    private fun createStaticEntry(explicit: Boolean = true): SafetyCenterStaticEntry =
        SafetyCenterStaticEntry.Builder("OK")
            .setSummary("OK")
            .setPendingIntent(
                safetySourceTestData.createTestActivityRedirectPendingIntent(explicit)
            )
            .build()

    private val staticEntryUpdated: SafetyCenterStaticEntry
        get() =
            SafetyCenterStaticEntry.Builder("Unspecified title")
                .setSummary("Unspecified summary")
                .setPendingIntent(safetySourceTestData.createTestActivityRedirectPendingIntent())
                .build()

    private fun staticEntryForWorkBuilder(title: CharSequence = "Paste", explicit: Boolean = true) =
        SafetyCenterStaticEntry.Builder(title)
            .setSummary("OK")
            .setPendingIntent(
                createTestActivityRedirectPendingIntentForUser(
                    deviceState.workProfile().userHandle(),
                    explicit
                )
            )

    private fun staticEntryForPrivateBuilder(
        title: CharSequence = "Unknown",
        explicit: Boolean = true
    ) =
        SafetyCenterStaticEntry.Builder(title)
            .setSummary("OK")
            .setPendingIntent(
                createTestActivityRedirectPendingIntentForUser(
                    deviceState.privateProfile().userHandle(),
                    explicit
                )
            )

    private fun createStaticEntryForWork(explicit: Boolean = true): SafetyCenterStaticEntry =
        staticEntryForWorkBuilder(explicit = explicit).build()

    private fun createStaticEntryForPrivate(explicit: Boolean = true): SafetyCenterStaticEntry =
        staticEntryForPrivateBuilder(explicit = explicit).build()

    private fun createStaticEntryForWorkPaused(): SafetyCenterStaticEntry =
        staticEntryForWorkBuilder(explicit = false)
            .setSummary(safetyCenterResourcesApk.getStringByName("work_profile_paused"))
            .build()

    private val staticEntryForWorkPausedUpdated: SafetyCenterStaticEntry
        get() =
            staticEntryForWorkBuilder(title = "Unspecified title for Work")
                .setSummary(safetyCenterResourcesApk.getStringByName("work_profile_paused"))
                .build()

    private val staticEntryForWorkUpdated: SafetyCenterStaticEntry
        get() =
            SafetyCenterStaticEntry.Builder("Unspecified title for Work")
                .setSummary("Unspecified summary")
                .setPendingIntent(safetySourceTestData.createTestActivityRedirectPendingIntent())
                .build()

    private val staticEntryForPrivateUpdated: SafetyCenterStaticEntry
        get() =
            SafetyCenterStaticEntry.Builder("Unspecified title for Private")
                .setSummary("Unspecified summary")
                .setPendingIntent(safetySourceTestData.createTestActivityRedirectPendingIntent())
                .build()

    private val safetyCenterDataForAdditionalUser
        get() =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryDefault(
                            SINGLE_SOURCE_ALL_PROFILE_ID,
                            deviceState.additionalUser().id(),
                            pendingIntent =
                                createTestActivityRedirectPendingIntentForUser(
                                    deviceState.additionalUser().userHandle()
                                )
                        )
                    )
                ),
                emptyList()
            )

    @get:Rule(order = 1) val supportsSafetyCenterRule = SupportsSafetyCenterRule(context)
    @get:Rule(order = 2)
    val safetyCenterTestRule =
        SafetyCenterTestRule(safetyCenterTestHelper, withNotifications = true)
    @get:Rule(order = 3) val disableAnimationRule = DisableAnimationRule()
    @get:Rule(order = 4) val freezeRotationRule = FreezeRotationRule()

    @Before
    fun setRefreshTimeoutsBeforeTest() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
    }

    @After
    fun resetQuietModeAfterTest() {
        setQuietMode(false)
    }

    @Test
    @EnsureHasWorkProfile
    fun getSafetyCenterData_withProfileOwner_hasWorkPolicyInfo() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.workPolicyInfoConfig)

        findWorkPolicyInfo()
    }

    @Test
    @EnsureHasDeviceOwner
    fun getSafetyCenterData_withDeviceOwner_hasWorkPolicyInfo() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.workPolicyInfoConfig)

        findWorkPolicyInfo()
    }

    @Test
    @EnsureHasWorkProfile
    fun launchActivity_withQuietModeEnabled_hasWorkPolicyInfo() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.workPolicyInfoConfig)

        setQuietMode(true)

        findWorkPolicyInfo()
    }

    @Test
    @EnsureHasNoWorkProfile
    @EnsureHasNoDeviceOwner
    fun launchActivity_withoutWorkProfileOrDeviceOwner_doesntHaveWorkPolicyInfo() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.workPolicyInfoConfig)

        context.launchSafetyCenterActivity { waitAllTextNotDisplayed("Your work policy info") }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withQuietModeEnabled_dataIsNotCleared() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )

        setQuietMode(true)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )

        assertThat(apiSafetySourceDataForWork).isEqualTo(dataForWork)
    }

    @Test
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    fun getSafetySourceData_afterAdditionalUserRemoved_returnsNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val additionalUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.additionalUser().userHandle())
        val dataForAdditionalUser = safetySourceTestData.information
        additionalUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForAdditionalUser
        )
        checkState(
            additionalUserSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            ) == dataForAdditionalUser
        )

        deviceState.additionalUser().remove()

        assertThat(
                additionalUserSafetyCenterManager
                    .getSafetySourceDataWithInteractAcrossUsersPermission(
                        SINGLE_SOURCE_ALL_PROFILE_ID
                    )
            )
            .isNull()
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withoutInteractAcrossUserPermission_shouldThrowError() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)

        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        assertFailsWith(SecurityException::class) {
            managedSafetyCenterManager.getSafetySourceData(SINGLE_SOURCE_ALL_PROFILE_ID)
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withoutAppInstalledForWorkProfile_shouldReturnNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )
        TestApis.packages().find(context.packageName).uninstall(deviceState.workProfile())

        val safetySourceData =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )

        assertThat(safetySourceData).isNull()
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withRemovedProfile_shouldReturnNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )
        deviceState.workProfile().remove()

        val safetySourceData =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )

        assertThat(safetySourceData).isNull()
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withProfileInQuietMode_shouldReturnData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )
        setQuietMode(true)

        val safetySourceData =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )

        assertThat(safetySourceData).isEqualTo(dataForWork)
    }

    @Test
    @EnsureHasNoWorkProfile
    fun getSafetyCenterData_withComplexConfigWithoutWorkProfile_returnsPrimaryDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexAllProfileConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
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
                            .setEntries(listOf(dynamicBareboneDefault, dynamicDisabledDefault))
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    ),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(listOf(staticBarebone, staticAllOptional))
                            .build()
                    )
                ),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(createStaticEntry(), createStaticEntry(explicit = false))
                    )
                )
            )
        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithoutDataProvided_returnsDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexAllProfileConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
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
                            .setEntries(
                                listOf(
                                    dynamicBareboneDefault,
                                    dynamicDisabledDefault,
                                    dynamicDisabledForWorkDefault
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    ),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(staticBarebone, staticAllOptional, staticAllOptionalForWork)
                            )
                            .build()
                    )
                ),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            createStaticEntry(),
                            createStaticEntryForWork(),
                            createStaticEntry(explicit = false),
                            createStaticEntryForWork(explicit = false)
                        )
                    )
                )
            )
        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithPrimaryDataProvided_returnsPrimaryDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexAllProfileConfig)
        updatePrimaryProfileSources()

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusCritical(6),
                primaryProfileOnlyIssues,
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkDefault,
                                    dynamicHiddenUpdated
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    ),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(staticBarebone, staticAllOptional, staticAllOptionalForWork)
                            )
                            .build()
                    )
                ),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            staticEntryUpdated,
                            createStaticEntryForWork(),
                            createStaticEntry(explicit = false),
                            createStaticEntryForWork(explicit = false)
                        )
                    )
                )
            )
        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithExtraWorkOnlyWithAllDataProvided_returnsAllDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexAllProfileConfig)
        updatePrimaryProfileSources()
        updateWorkProfileSources()

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val managedUserId = deviceState.workProfile().id()
        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusCritical(11),
                listOf(
                    safetyCenterTestData.safetyCenterIssueCritical(
                        DYNAMIC_BAREBONE_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueCritical(
                        ISSUE_ONLY_BAREBONE_ID,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        DYNAMIC_DISABLED_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_DISABLED_ID,
                        managedUserId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_HIDDEN_ID,
                        managedUserId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        managedUserId,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        managedUserId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        managedUserId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    )
                ),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkUpdated,
                                    dynamicHiddenUpdated,
                                    dynamicHiddenForWorkUpdated
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    ),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(staticBarebone, staticAllOptional, staticAllOptionalForWork)
                            )
                            .build()
                    )
                ),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            staticEntryUpdated,
                            staticEntryForWorkUpdated,
                            createStaticEntry(explicit = false),
                            createStaticEntryForWork(explicit = false)
                        )
                    )
                )
            )
        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @RequiresFlagsDisabled(com.android.permission.flags.Flags.FLAG_PRIVATE_PROFILE_SUPPORTED)
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPrivateProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithPrivateProfileDisallowedWithAllDataProvided_returnsAllDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexAllProfileConfig)
        updatePrimaryProfileSources()
        updateWorkProfileSources()
        updatePrivateProfileSources()

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val managedUserId = deviceState.workProfile().id()
        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusCritical(11),
                listOf(
                    safetyCenterTestData.safetyCenterIssueCritical(
                        DYNAMIC_BAREBONE_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueCritical(
                        ISSUE_ONLY_BAREBONE_ID,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        DYNAMIC_DISABLED_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_DISABLED_ID,
                        managedUserId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_HIDDEN_ID,
                        managedUserId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        managedUserId,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        managedUserId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        managedUserId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    )
                ),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkUpdated,
                                    dynamicHiddenUpdated,
                                    dynamicHiddenForWorkUpdated
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    ),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(staticBarebone, staticAllOptional, staticAllOptionalForWork)
                            )
                            .build()
                    )
                ),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            staticEntryUpdated,
                            staticEntryForWorkUpdated,
                            createStaticEntry(explicit = false),
                            createStaticEntryForWork(explicit = false)
                        )
                    )
                )
            )
        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    // TODO(b/286539356) add the os feature flag requirement when available.
    @Test
    @RequiresFlagsEnabled(com.android.permission.flags.Flags.FLAG_PRIVATE_PROFILE_SUPPORTED)
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPrivateProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithAllDataProvided_returnsAllDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexAllProfileConfig)
        updatePrimaryProfileSources()
        updateWorkProfileSources()
        updatePrivateProfileSources()

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val managedUserId = deviceState.workProfile().id()
        val privateProfileId = deviceState.privateProfile().id()
        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusCritical(11),
                listOf(
                    safetyCenterTestData.safetyCenterIssueCritical(
                        DYNAMIC_BAREBONE_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueCritical(
                        ISSUE_ONLY_BAREBONE_ID,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        DYNAMIC_DISABLED_ID,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_DISABLED_ID,
                        managedUserId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_HIDDEN_ID,
                        managedUserId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        managedUserId,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        managedUserId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        managedUserId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_DISABLED_ID,
                        privateProfileId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_HIDDEN_ID,
                        privateProfileId,
                        groupId = DYNAMIC_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        privateProfileId,
                        attributionTitle = null,
                        groupId = ISSUE_ONLY_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        DYNAMIC_IN_STATELESS_ID,
                        privateProfileId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    ),
                    safetyCenterTestData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_STATELESS_ID,
                        privateProfileId,
                        groupId = MIXED_STATELESS_GROUP_ID
                    )
                ),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkUpdated,
                                    dynamicDisabledForPrivateUpdated,
                                    dynamicHiddenUpdated,
                                    dynamicHiddenForWorkUpdated,
                                    dynamicHiddenForPrivateUpdated
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    ),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(
                                    staticBarebone,
                                    staticAllOptional,
                                    staticAllOptionalForWork,
                                    staticAllOptionalForPrivate
                                )
                            )
                            .build()
                    )
                ),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            staticEntryUpdated,
                            staticEntryForWorkUpdated,
                            staticEntryForPrivateUpdated,
                            createStaticEntry(explicit = false),
                            createStaticEntryForWork(explicit = false),
                            createStaticEntryForPrivate(explicit = false)
                        )
                    )
                )
            )
        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withQuietMode_shouldHaveWorkProfilePausedSummaryAndNoWorkIssues() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexAllProfileConfig)
        updatePrimaryProfileSources()
        updateWorkProfileSources()
        setQuietMode(true)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusCritical(6),
                primaryProfileOnlyIssues,
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkPausedUpdated,
                                    dynamicHiddenUpdated,
                                    dynamicHiddenForWorkPausedUpdated,
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    ),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(
                                    staticBarebone,
                                    staticAllOptional,
                                    staticAllOptionalForWorkPaused
                                )
                            )
                            .build()
                    )
                ),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            staticEntryUpdated,
                            staticEntryForWorkPausedUpdated,
                            createStaticEntry(explicit = false),
                            createStaticEntryForWorkPaused()
                        )
                    )
                )
            )
        assertThat(apiSafetyCenterData.withoutExtras()).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withDataForDifferentUserProfileGroup_shouldBeUnaffected() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val dataForPrimaryUser = safetySourceTestData.information
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, dataForPrimaryUser)
        val dataForPrimaryUserWorkProfile = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForPrimaryUserWorkProfile
        )

        val additionalUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.additionalUser().userHandle())
        val apiSafetyCenterDataForAdditionalUser =
            additionalUserSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission()

        assertThat(apiSafetyCenterDataForAdditionalUser)
            .isEqualTo(safetyCenterDataForAdditionalUser)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_afterManagedProfileRemoved_returnsDefaultData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val safetyCenterDataWithWorkProfile =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(SINGLE_SOURCE_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                            .setSummary(
                                safetyCenterResourcesApk.getStringByName("group_unknown_summary")
                            )
                            .setEntries(
                                listOf(
                                    safetyCenterTestData.safetyCenterEntryDefault(
                                        SINGLE_SOURCE_ALL_PROFILE_ID
                                    ),
                                    safetyCenterTestData.safetyCenterEntryDefault(
                                        SINGLE_SOURCE_ALL_PROFILE_ID,
                                        deviceState.workProfile().id(),
                                        title = "Paste",
                                        pendingIntent =
                                            createTestActivityRedirectPendingIntentForUser(
                                                deviceState.workProfile().userHandle()
                                            )
                                    )
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    )
                ),
                emptyList()
            )
        checkState(
            safetyCenterManager.getSafetyCenterDataWithPermission() ==
                safetyCenterDataWithWorkProfile
        )
        checkState(
            managedSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission() ==
                safetyCenterDataWithWorkProfile
        )

        deviceState.workProfile().remove()

        val safetyCenterDataForPrimaryUser =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryDefault(SINGLE_SOURCE_ALL_PROFILE_ID)
                    )
                ),
                emptyList()
            )
        assertThat(safetyCenterManager.getSafetyCenterDataWithPermission())
            .isEqualTo(safetyCenterDataForPrimaryUser)
        assertThat(
                managedSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission()
            )
            .isEqualTo(SafetyCenterTestData.DEFAULT)
    }

    @Test
    @RequiresFlagsEnabled(com.android.permission.flags.Flags.FLAG_PRIVATE_PROFILE_SUPPORTED)
    @EnsureHasPrivateProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_afterPrivateProfileRemoved_returnsDefaultData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val privateSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.privateProfile().userHandle())
        val safetyCenterDataWithPrivateProfile =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(SINGLE_SOURCE_GROUP_ID, "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                            .setSummary(
                                safetyCenterResourcesApk.getStringByName("group_unknown_summary")
                            )
                            .setEntries(
                                listOf(
                                    safetyCenterTestData.safetyCenterEntryDefault(
                                        SINGLE_SOURCE_ALL_PROFILE_ID
                                    ),
                                    safetyCenterTestData.safetyCenterEntryDefault(
                                        SINGLE_SOURCE_ALL_PROFILE_ID,
                                        deviceState.privateProfile().id(),
                                        title = "Unknown",
                                        pendingIntent =
                                            createTestActivityRedirectPendingIntentForUser(
                                                deviceState.privateProfile().userHandle()
                                            )
                                    )
                                )
                            )
                            .setSeverityUnspecifiedIconType(
                                SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
                            )
                            .build()
                    )
                ),
                emptyList()
            )

        checkState(
            safetyCenterManager.getSafetyCenterDataWithPermission() ==
                safetyCenterDataWithPrivateProfile
        )
        checkState(
            privateSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission() ==
                safetyCenterDataWithPrivateProfile
        )

        deviceState.privateProfile().remove()

        val safetyCenterDataForPrimaryUser =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData.safetyCenterEntryDefault(SINGLE_SOURCE_ALL_PROFILE_ID)
                    )
                ),
                emptyList()
            )
        assertThat(safetyCenterManager.getSafetyCenterDataWithPermission())
            .isEqualTo(safetyCenterDataForPrimaryUser)
        assertThat(
                privateSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission()
            )
            .isEqualTo(SafetyCenterTestData.DEFAULT)
    }

    @Test
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_afterAdditionalUserRemoved_returnsDefaultData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val additionalUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.additionalUser().userHandle())
        checkState(
            additionalUserSafetyCenterManager
                .getSafetyCenterDataWithInteractAcrossUsersPermission() ==
                safetyCenterDataForAdditionalUser
        )

        deviceState.additionalUser().remove()

        assertThat(
                additionalUserSafetyCenterManager
                    .getSafetyCenterDataWithInteractAcrossUsersPermission()
            )
            .isEqualTo(SafetyCenterTestData.DEFAULT)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_primaryProfileIssueOnlySource_shouldNotBeAbleToSetDataToWorkProfile() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWork =
            SafetySourceTestData.issuesOnly(safetySourceTestData.criticalResolvingGeneralIssue)

        assertFailsWith(IllegalArgumentException::class) {
            managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
                ISSUE_ONLY_ALL_OPTIONAL_ID,
                dataForWork
            )
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_withoutInteractAcrossUserPermission_shouldThrowError() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())

        assertFailsWith(SecurityException::class) {
            managedSafetyCenterManager.setSafetySourceData(
                SINGLE_SOURCE_ALL_PROFILE_ID,
                dataForWork,
                EVENT_SOURCE_STATE_CHANGED
            )
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_withoutAppInstalledForWorkProfile_shouldNoOp() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        TestApis.packages().find(context.packageName).uninstall(deviceState.workProfile())

        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )

        val safetySourceData =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )
        assertThat(safetySourceData).isNull()
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_withRemovedProfile_shouldNoOp() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        deviceState.workProfile().remove()

        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )

        val safetySourceData =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )
        assertThat(safetySourceData).isNull()
    }

    @Test
    @EnsureHasCloneProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_withUnsupportedProfile_shouldNoOp() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val dataForClone = safetySourceTestData.informationWithIssueForWork
        val clonedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.cloneProfile().userHandle())

        clonedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForClone
        )

        val safetySourceData =
            clonedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )
        assertThat(safetySourceData).isNull()
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_withProfileInQuietMode_shouldSetData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        setQuietMode(true)

        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )

        val safetySourceData =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )
        assertThat(safetySourceData).isEqualTo(dataForWork)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_issuesOnlySourceWithWorkProfile_shouldBeAbleToSetData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceAllProfileConfig)

        val dataForPrimaryUser =
            SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        safetyCenterTestHelper.setData(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID, dataForPrimaryUser)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWorkProfile =
            SafetySourceTestData.issuesOnly(safetySourceTestData.criticalResolvingGeneralIssue)
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_ALL_PROFILE_SOURCE_ID,
            dataForWorkProfile
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                ISSUE_ONLY_ALL_PROFILE_SOURCE_ID
            )
        assertThat(apiSafetySourceData).isEqualTo(dataForPrimaryUser)
        assertThat(apiSafetySourceDataForWork).isEqualTo(dataForWorkProfile)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_primaryProfileSource_shouldNotBeAbleToSetDataToWorkProfile() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        assertFailsWith(IllegalArgumentException::class) {
            managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ID,
                dataForWork
            )
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_sourceWithWorkProfile_shouldBeAbleToSetData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)

        val dataForPrimaryUser = safetySourceTestData.information
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, dataForPrimaryUser)
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ALL_PROFILE_ID)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID
            )
        assertThat(apiSafetySourceData).isEqualTo(dataForPrimaryUser)
        assertThat(apiSafetySourceDataForWork).isEqualTo(dataForWork)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_notificationsAllowed_workProfile_sendsNotification() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceAllProfileConfig)
        SafetyCenterFlags.notificationsEnabled = true
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ALL_PROFILE_ID)
        SafetyCenterFlags.immediateNotificationBehaviorIssues =
            setOf("$SINGLE_SOURCE_ALL_PROFILE_ID/$ISSUE_TYPE_ID")
        val dataForWork = safetySourceTestData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())

        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID,
            dataForWork
        )

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                title = "Information issue title",
                text = "Information issue summary",
                actions = listOf("Review")
            )
        )
    }

    @Test
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    fun setSafetySourceData_forStoppedUser_shouldSetData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        deviceState.additionalUser().stop()

        val dataForPrimaryUser = safetySourceTestData.unspecified
        val additionalUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.additionalUser().userHandle())
        additionalUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ID,
            dataForPrimaryUser
        )

        val apiSafetySourceData =
            additionalUserSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ID
            )
        assertThat(apiSafetySourceData).isEqualTo(dataForPrimaryUser)
    }

    @Test
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    fun setSafetySourceData_forBothPrimaryAdditionalUser_shouldSetData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataForPrimaryUser = safetySourceTestData.information
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataForPrimaryUser)
        val dataForAdditionalUser = safetySourceTestData.unspecified
        val additionalUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.additionalUser().userHandle())
        additionalUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ID,
            dataForAdditionalUser
        )

        val apiSafetySourceDataForPrimaryUser =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        val apiSafetySourceDataForAdditionalUser =
            additionalUserSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ID
            )
        assertThat(apiSafetySourceDataForPrimaryUser).isEqualTo(dataForPrimaryUser)
        assertThat(apiSafetySourceDataForAdditionalUser).isEqualTo(dataForAdditionalUser)
    }

    @Test
    @EnsureHasAdditionalUser(installInstrumentedApp = TRUE)
    fun setSafetySourceData_forAdditionalUser_shouldNotAffectDataForPrimaryUser() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataForAdditionalUser = safetySourceTestData.unspecified
        val additionalUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.additionalUser().userHandle())
        additionalUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ID,
            dataForAdditionalUser
        )

        val apiSafetySourceDataForPrimaryUser =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceDataForPrimaryUser).isEqualTo(null)
    }

    private fun findWorkPolicyInfo() {
        context.launchSafetyCenterActivity {
            waitAllTextDisplayed("Your work policy info", "Settings managed by your IT admin")
        }
    }

    private fun getSafetyCenterManagerForUser(userHandle: UserHandle): SafetyCenterManager {
        val contextForUser = getContextForUser(userHandle)
        return contextForUser.getSystemService(SafetyCenterManager::class.java)!!
    }

    private fun getContextForUser(userHandle: UserHandle): Context {
        return callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            context.createContextAsUser(userHandle, 0)
        }
    }

    private fun createTestActivityRedirectPendingIntentForUser(
        user: UserHandle,
        explicit: Boolean = true
    ): PendingIntent {
        return callWithShellPermissionIdentity(INTERACT_ACROSS_USERS) {
            SafetySourceTestData.createRedirectPendingIntent(
                getContextForUser(user),
                SafetySourceTestData.createTestActivityIntent(getContextForUser(user), explicit)
            )
        }
    }

    private fun SafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
        id: String
    ): SafetySourceData? =
        callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            getSafetySourceDataWithPermission(id)
        }

    private fun SafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
        id: String,
        dataToSet: SafetySourceData,
        safetyEvent: SafetyEvent = EVENT_SOURCE_STATE_CHANGED
    ) =
        callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            setSafetySourceDataWithPermission(id, dataToSet, safetyEvent)
        }

    private fun SafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission():
        SafetyCenterData =
        callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            getSafetyCenterDataWithPermission()
        }

    private fun setQuietMode(enableQuietMode: Boolean) {
        if (inQuietMode == enableQuietMode) {
            return
        }
        if (enableQuietMode) {
            deviceState.workProfile().setQuietMode(true)
        } else {
            // This is needed to ensure the refresh broadcast doesn't leak onto other tests.
            disableQuietModeAndWaitForRefreshToComplete()
        }
        inQuietMode = enableQuietMode
    }

    private fun disableQuietModeAndWaitForRefreshToComplete() {
        val listener = safetyCenterTestHelper.addListener()
        deviceState.workProfile().setQuietMode(false)
        listener.waitForSafetyCenterRefresh()
    }

    private fun safetyCenterEntryOkForWork(sourceId: String, managedUserId: Int) =
        safetyCenterTestData
            .safetyCenterEntryOkBuilder(sourceId, managedUserId, title = "Ok title for Work")
            .build()

    private fun safetyCenterEntryOkForPrivate(sourceId: String, managedUserId: Int) =
        safetyCenterTestData
            .safetyCenterEntryOkBuilder(sourceId, managedUserId, title = "Ok title for Private")
            .build()

    private fun updatePrimaryProfileSources() {
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
    }

    private fun updateWorkProfileSources() {
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_DISABLED_ID,
            safetySourceTestData.informationWithIssueForWork
        )
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_HIDDEN_ID,
            safetySourceTestData.informationWithIssueForWork
        )
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
        )
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_IN_STATELESS_ID,
            safetySourceTestData.unspecifiedWithIssueForWork
        )
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_IN_STATELESS_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
        )
    }

    private fun updatePrivateProfileSources() {
        val privateSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.privateProfile().userHandle())
        privateSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_DISABLED_ID,
            safetySourceTestData.informationWithIssueForPrivate
        )
        privateSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_HIDDEN_ID,
            safetySourceTestData.informationWithIssueForPrivate
        )
        privateSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
        )
        privateSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_IN_STATELESS_ID,
            safetySourceTestData.unspecifiedWithIssueForPrivate
        )
        privateSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_IN_STATELESS_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
        )
    }
}
