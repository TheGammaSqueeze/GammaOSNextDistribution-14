/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.tests.navigation

import android.content.ComponentName
import android.content.Intent
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.makeMainActivity
import android.health.connect.HealthConnectManager.ACTION_HEALTH_HOME_SETTINGS
import android.health.connect.HealthConnectManager.ACTION_MANAGE_HEALTH_DATA
import android.health.connect.HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteRange.*
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel.AutoDeleteState
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.*
import com.android.healthconnect.controller.categories.HealthDataCategoryViewModel.CategoriesFragmentState.WithData
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.*
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.navigation.TrampolineActivity
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeDeviceInfoUtils
import com.android.healthconnect.controller.tests.utils.showOnboarding
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtilsModule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*

@HiltAndroidTest
@UninstallModules(DeviceInfoUtilsModule::class)
class TrampolineActivityTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val deviceInfoUtils: DeviceInfoUtils = FakeDeviceInfoUtils()
    @BindValue val migrationViewModel: MigrationViewModel = mock(MigrationViewModel::class.java)
    @BindValue val autoDeleteViewModel: AutoDeleteViewModel = mock(AutoDeleteViewModel::class.java)
    @BindValue
    val categoryViewModel: HealthDataCategoryViewModel =
        mock(HealthDataCategoryViewModel::class.java)
    @BindValue
    val appPermissionViewModel: AppPermissionViewModel = mock(AppPermissionViewModel::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setup() {
        hiltRule.inject()

        showOnboarding(context, show = false)
        (deviceInfoUtils as FakeDeviceInfoUtils).setHealthConnectAvailable(true)

        // Disable migration to show MainActivity and DataManagementActivity
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val writePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)
        val readPermission =
            HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.READ)
        whenever(appPermissionViewModel.appInfo).then { MutableLiveData(TEST_APP) }
        whenever(
                appPermissionViewModel.shouldNavigateToAppPermissionsFragment(
                    TEST_APP_PACKAGE_NAME))
            .then { true }
        whenever(appPermissionViewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(appPermissionViewModel.grantedPermissions).then {
            MutableLiveData(setOf(writePermission))
        }
        whenever(appPermissionViewModel.revokeAllPermissionsState).then {
            MutableLiveData(AppPermissionViewModel.RevokeAllState.NotStarted)
        }
        whenever(appPermissionViewModel.allAppPermissionsGranted).then { MediatorLiveData(false) }
        whenever(appPermissionViewModel.atLeastOnePermissionGranted).then { MediatorLiveData(true) }
        val accessDate = Instant.parse("2022-10-20T18:40:13.00Z")
        whenever(appPermissionViewModel.loadAccessDate(anyString())).thenReturn(accessDate)
    }

    @Test
    fun startingActivity_healthConnectNotAvailable_finishesActivity() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setHealthConnectAvailable(false)

        val scenario = launchActivityForResult<TrampolineActivity>(createStartIntent())

        onIdle()
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun startingActivity_noAction_finishesActivity() {
        val scenario = launchActivityForResult<TrampolineActivity>(createStartIntent("no_action"))

        onIdle()
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun homeSettingsAction_onboardingNotDone_redirectsToOnboarding() {
        showOnboarding(context, true)

        launchActivityForResult<TrampolineActivity>(createStartIntent())

        onIdle()
        onView(withId(R.id.onboarding)).check(matches(isDisplayed()))
    }

    @Test
    fun homeSettingsIntent_launchesMainActivity() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setHealthConnectAvailable(true)

        launchActivityForResult<TrampolineActivity>(createStartIntent(ACTION_HEALTH_HOME_SETTINGS))

        onIdle()
        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("Permissions and data")).check(matches(isDisplayed()))
    }

    @Test
    fun manageHealthDataIntent_launchesDataManagementActivity() {
        // setup data management screen.
        whenever(autoDeleteViewModel.storedAutoDeleteRange).then {
            MutableLiveData(AutoDeleteState.WithData(AUTO_DELETE_RANGE_NEVER))
        }
        whenever(categoryViewModel.categoriesData).then {
            MutableLiveData<CategoriesFragmentState>(WithData(emptyList()))
        }

        launchActivityForResult<TrampolineActivity>(createStartIntent(ACTION_MANAGE_HEALTH_DATA))

        onIdle()
        onView(withText("Browse data")).check(matches(isDisplayed()))
    }

    @Test
    fun manageHealthPermissions_launchesSettingsActivity() {
        launchActivityForResult<TrampolineActivity>(
            createStartIntent(ACTION_MANAGE_HEALTH_PERMISSIONS))

        onView(
                withText(
                    "Apps with this permission can read and write your" +
                        " health and fitness data."))
            .check(matches(isDisplayed()))
    }

    @Test
    fun manageHealthPermissions_withPackageName_launchesSettingsActivity() {
        val intent = createStartIntent(ACTION_MANAGE_HEALTH_PERMISSIONS)
        intent.putExtra(EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME)
        whenever(appPermissionViewModel.showDisableExerciseRouteEvent)
            .thenReturn(MediatorLiveData(AppPermissionViewModel.DisableExerciseRouteDialogEvent()))

        launchActivityForResult<TrampolineActivity>(intent)

        onIdle()
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
    }

    private fun createStartIntent(action: String = ACTION_HEALTH_HOME_SETTINGS): Intent {
        return makeMainActivity(ComponentName(context, TrampolineActivity::class.java))
            .addFlags(FLAG_ACTIVITY_NEW_TASK)
            .setAction(action)
    }
}
