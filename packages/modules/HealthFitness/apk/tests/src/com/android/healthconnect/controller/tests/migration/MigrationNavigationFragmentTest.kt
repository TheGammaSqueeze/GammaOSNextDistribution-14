package com.android.healthconnect.controller.tests.migration

import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationNavigationFragment
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.NavigationUtils
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class MigrationNavigationFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue val navigationUtils: NavigationUtils = Mockito.mock(NavigationUtils::class.java)
    @BindValue
    val migrationViewModel: MigrationViewModel = Mockito.mock(MigrationViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun whenMigrationFragmentStateLoading_showsLoading() {
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.Loading)
        }

        launchFragment<MigrationNavigationFragment>()
        onView(withId(R.id.progress_indicator)).check(matches(isDisplayed()))
    }

    @Test
    fun whenMigrationFragmentStateError_showsError() {
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.Error)
        }

        launchFragment<MigrationNavigationFragment>()
        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun whenMigrationStateAllowedNotStarted_navigatesToMigrationPausedFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_NOT_STARTED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_migrationPausedFragment))
    }

    @Test
    fun whenMigrationStateAllowedPaused_navigatesToMigrationPausedFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_PAUSED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_migrationPausedFragment))
    }

    @Test
    fun whenMigrationStateAppUpdateRequired_navigatesToAppUpdateRequiredFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(
                any(),
                eq(R.id.action_migrationNavigationFragment_to_migrationAppUpdateNeededFragment))
    }

    @Test
    fun whenMigrationStateModuleUpdateRequired_navigatesToModuleUpdateRequiredFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.MODULE_UPGRADE_REQUIRED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(
                any(),
                eq(R.id.action_migrationNavigationFragment_to_migrationModuleUpdateNeededFragment))
    }

    @Test
    fun whenMigrationStateInProgress_navigatesToMigrationInProgressFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IN_PROGRESS,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(
                any(), eq(R.id.action_migrationNavigationFragment_to_migrationInProgressFragment))
    }

    @Test
    fun whenMigrationStateCompleteIdle_navigatesToHomeFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.COMPLETE_IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_homeFragment))
    }

    @Test
    fun whenMigrationStateComplete_navigatesToHomeFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.COMPLETE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_homeFragment))
    }

    @Test
    fun whenMigrationStateIdle_navigatesToHomeFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_homeFragment))
    }

    @Test
    fun whenMigrationStateAllowedMigratorDisabled_navigatesToHomeFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_MIGRATOR_DISABLED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_homeFragment))
    }

    @Test
    fun whenMigrationStateUnknown_navigatesToHomeFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.UNKNOWN,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_homeFragment))
    }

    @Test
    fun whenDataRestoreInProgress_navigatesToDataRestoreInProgressScreen() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(
                any(), eq(R.id.action_migrationNavigationFragment_to_dataRestoreInProgressFragment))
    }

    @Test
    fun whenDataRestorePending_navigatesToHomeFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.PENDING,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_homeFragment))
    }

    @Test
    fun whenDataRestoreError_navigatesToHomeFragment() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData<MigrationViewModel.MigrationFragmentState>(
                MigrationViewModel.MigrationFragmentState.WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_FETCHING_DATA)))
        }

        launchFragment<MigrationNavigationFragment>()
        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationNavigationFragment_to_homeFragment))
    }
}
