/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.permissions.connectedapps.settings

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.pm.ActivityInfo
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel.DisableExerciseRouteDialogEvent
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel.RevokeAllState
import com.android.healthconnect.controller.permissions.app.SettingsManageAppPermissionsFragment
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.logging.DataRestoreElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class SettingsManageAppPermissionsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val viewModel: AppPermissionViewModel = mock()
    @BindValue val navigationUtils: NavigationUtils = mock()
    @BindValue val migrationViewModel: MigrationViewModel = mock()
    @BindValue val additionalAccessViewModel: AdditionalAccessViewModel = mock()
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        hiltRule.inject()

        whenever(viewModel.revokeAllPermissionsState).then {
            MutableLiveData(RevokeAllState.NotStarted)
        }
        whenever(viewModel.appPermissions).then { MutableLiveData(emptyList<HealthPermission>()) }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(emptySet<HealthPermission>())
        }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(false) }
        whenever(viewModel.atLeastOnePermissionGranted).then { MediatorLiveData(true) }
        val accessDate = Instant.parse("2022-10-20T18:40:13.00Z")
        whenever(viewModel.loadAccessDate(Mockito.anyString())).thenReturn(accessDate)
        whenever(viewModel.showDisableExerciseRouteEvent)
            .thenReturn(MediatorLiveData(DisableExerciseRouteDialogEvent()))
        whenever(viewModel.appInfo).then {
            MutableLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo)))
        }

        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(AdditionalAccessViewModel.State())
        }

        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        // disable animations
        toggleAnimation(false)
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
        // enable animations
        toggleAnimation(true)
    }

    @Test
    fun fragment_starts() {
        val writePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)
        val readPermission =
            HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(writePermission)) }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("Allow all")).check(matches(isDisplayed()))
        onView(withText("Allowed to read")).check(matches(isDisplayed()))
        onView(withText("Allowed to write")).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.MANAGE_PERMISSIONS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(PermissionsElement.ALLOW_ALL_SWITCH)
        verify(healthConnectLogger, times(2)).logImpression(PermissionsElement.PERMISSION_SWITCH)
    }

    @Test
    fun doesNotShowWriteHeader_whenNoWritePermissions() {
        val readPermission =
            HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.READ)
        whenever(viewModel.appPermissions).then { MutableLiveData(listOf(readPermission)) }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(readPermission)) }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("Allow all")).check(matches(isDisplayed()))
        onView(withText("Allowed to read")).check(matches(isDisplayed()))
        onView(withText("Allowed to write")).check(doesNotExist())
    }

    @Test
    fun doesNotShowReadHeader_whenNoReadPermissions() {
        val writePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)
        whenever(viewModel.appPermissions).then { MutableLiveData(listOf(writePermission)) }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(writePermission)) }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("Allow all")).check(matches(isDisplayed()))
        onView(withText("Allowed to read")).check(doesNotExist())
        onView(withText("Allowed to write")).check(matches(isDisplayed()))
    }

    @Test
    fun unsupportedPackage_grantedPermissionsNotLoaded_onOrientationChange() {
        val readStepsPermission =
            HealthPermission(HealthPermissionType.STEPS, PermissionsAccessType.READ)
        val writeSleepPermission =
            HealthPermission(HealthPermissionType.SLEEP, PermissionsAccessType.WRITE)

        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(readStepsPermission, writeSleepPermission))
        }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(writeSleepPermission, readStepsPermission))
        }
        whenever(viewModel.isPackageSupported(TEST_APP_PACKAGE_NAME)).then { false }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("Allow all")).check(matches(isDisplayed()))
        onView(withText("Sleep")).check(matches(isDisplayed()))
        onView(withText("Steps")).check(matches(isDisplayed()))
        onView(withText("Sleep")).perform(click())
        onView(withText("Sleep")).check(matches(not(isChecked())))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onIdle()
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(RecyclerViewActions.scrollToLastPosition<RecyclerView.ViewHolder>())
        onIdle()
        onView(withText("Sleep")).perform(scrollTo()).check(matches(not(isChecked())))
    }

    @Test
    fun unsupportedPackage_doesNotShowFooter() {
        val readStepsPermission =
            HealthPermission(HealthPermissionType.STEPS, PermissionsAccessType.READ)
        val writeSleepPermission =
            HealthPermission(HealthPermissionType.SLEEP, PermissionsAccessType.WRITE)

        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(readStepsPermission, writeSleepPermission))
        }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(writeSleepPermission, readStepsPermission))
        }
        whenever(viewModel.isPackageSupported(TEST_APP_PACKAGE_NAME)).then { false }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(RecyclerViewActions.scrollToLastPosition<RecyclerView.ViewHolder>())
        onView(
                withText(
                    "$TEST_APP_NAME can read data added after October 20, 2022" +
                        "\n\n" +
                        "Data you share with $TEST_APP_NAME is covered by their privacy policy"))
            .check(doesNotExist())
        onView(withText("Read privacy policy")).check(doesNotExist())
    }

    @Test
    fun supportedPackage_showsFooter() {
        val writePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)
        val readPermission =
            HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(writePermission)) }
        whenever(viewModel.isPackageSupported(TEST_APP_PACKAGE_NAME)).then { true }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(
                withText(
                    "$TEST_APP_NAME can read data added after October 20, 2022" +
                        "\n\n" +
                        "Data you share with $TEST_APP_NAME is covered by their privacy policy"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Read privacy policy")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun additionalAccessState_notValid_hidesAdditionalAccess() {
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(AdditionalAccessViewModel.State())
        }

        launchFragment<SettingsManageAppPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withText(R.string.additional_access_label)).check(doesNotExist())
    }

    @Test
    fun additionalAccessState_valid_showsAdditionalAccess() {
        val validState =
            AdditionalAccessViewModel.State(
                exerciseRoutePermissionUIState = PermissionUiState.ASK_EVERY_TIME,
                exercisePermissionUIState = PermissionUiState.ASK_EVERY_TIME)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(validState)
        }

        launchFragment<SettingsManageAppPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withText(R.string.additional_access_label)).check(matches(isDisplayed()))
    }

    @Test
    fun additionalAccessState_onClick_navigatesToAdditionalAccessFragment() {
        val validState =
            AdditionalAccessViewModel.State(
                exerciseRoutePermissionUIState = PermissionUiState.ASK_EVERY_TIME,
                exercisePermissionUIState = PermissionUiState.ASK_EVERY_TIME)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(validState)
        }

        launchFragment<SettingsManageAppPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))
        onView(withText(R.string.additional_access_label)).perform(click())

        verify(navigationUtils)
            .navigate(
                fragment = any(),
                action = eq(R.id.action_manageAppFragment_to_additionalAccessFragment),
                bundle = any())
    }

    @Test
    fun whenMigrationPending_showsMigrationPendingDialog() {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val writePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)
        val readPermission =
            HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(writePermission)) }
        whenever(viewModel.isPackageSupported(TEST_APP_PACKAGE_NAME)).then { true }

        launchFragment<SettingsManageAppPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(
                withText(
                    "Health Connect is ready to be integrated with your Android system. If you give $TEST_APP_NAME access now, some features may not work until integration is complete."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        // TODO (b/322495982) check navigation to Migration activity
        onView(withText("Start integration")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Continue")).inRoot(isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CONTINUE_BUTTON)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CANCEL_BUTTON)

        onView(withText("Continue")).inRoot(isDialog()).perform(click())
        onView(withText("Continue")).check(doesNotExist())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_PENDING_DIALOG_CONTINUE_BUTTON)
    }

    @Test
    fun whenMigrationInProgress_showsMigrationInProgressDialog() {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IN_PROGRESS,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IN_PROGRESS,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val writePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)
        val readPermission =
            HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(writePermission)) }
        whenever(viewModel.isPackageSupported(TEST_APP_PACKAGE_NAME)).then { true }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(
                withText(
                    "Health Connect is being integrated with the Android system.\n\nYou'll get a notification when the process is complete and you can use $TEST_APP_NAME with Health Connect."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_BUTTON)

        // Needed to makes sure activity has finished
        Thread.sleep(2_000)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun whenRestoreInProgress_showsRestoreInProgressDialog() {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val writePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)
        val readPermission =
            HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(writePermission)) }
        whenever(viewModel.isPackageSupported(TEST_APP_PACKAGE_NAME)).then { true }

        val scenario =
            launchFragment<SettingsManageAppPermissionsFragment>(
                bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME))

        onView(withText("Health Connect restore in progress"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "Health Connect is restoring data and permissions. This may take some time to complete."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_BUTTON)

        // Needed to makes sure activity has finished
        Thread.sleep(2_000)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }
}
