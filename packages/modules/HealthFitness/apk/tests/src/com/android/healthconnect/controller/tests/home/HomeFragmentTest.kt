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
package com.android.healthconnect.controller.tests.home

import android.content.Context
import android.health.connect.HealthDataCategory
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.home.HomeFragment
import com.android.healthconnect.controller.home.HomeFragmentViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.WithData
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.recentaccess.RecentAccessEntry
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel.RecentAccessState
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.logging.DataRestoreElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HomePageElement
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.RecentAccessElement
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltAndroidTest
class HomeFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context

    @BindValue
    val homeFragmentViewModel: HomeFragmentViewModel =
        Mockito.mock(HomeFragmentViewModel::class.java)

    @BindValue
    val recentAccessViewModel: RecentAccessViewModel =
        Mockito.mock(RecentAccessViewModel::class.java)

    @BindValue
    val migrationViewModel: MigrationViewModel = Mockito.mock(MigrationViewModel::class.java)

    @BindValue val timeSource = TestTimeSource
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Inject lateinit var fakeFeatureUtils: FeatureUtils
    private lateinit var navHostController: TestNavHostController
    @BindValue val navigationUtils: NavigationUtils = Mockito.mock(NavigationUtils::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(false)
        navHostController = TestNavHostController(context)

        // disable animations
        toggleAnimation(false)
    }

    @After
    fun teardown() {
        timeSource.reset()
        // enable animations
        toggleAnimation(true)
        reset(healthConnectLogger)
    }

    @Test
    fun appPermissions_navigatesToConnectedApps() {
        setupFragmentForNavigation()
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("App permissions")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.connectedAppsFragment)
    }

    @Test
    fun dataAndAccess_navigatesToDataAndAccess() {
        setupFragmentForNavigation()
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.data_activity)
    }

    @Test
    fun seeAllRecentAccess_navigatesToRecentAccess() {
        setupFragmentForNavigation()
        onView(withText("See all recent access")).check(matches(isDisplayed()))
        onView(withText("See all recent access")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.recentAccessFragment)
    }

    @Test
    fun recentAccessApp_navigatesToConnectedAppFragment() {
        setupFragmentForNavigation()
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.connectedAppFragment)
    }

    @Test
    fun manageData_navigatesToManageData() {
        setupFragmentForNavigation()
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Manage data")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.manageDataFragment)
    }

    @Test
    fun homeFragmentLogging_impressionsLogged() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))

        timeSource.setIs24Hour(true)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle())

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.HOME_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(HomePageElement.APP_PERMISSIONS_BUTTON)
        verify(healthConnectLogger).logImpression(HomePageElement.DATA_AND_ACCESS_BUTTON)
        verify(healthConnectLogger).logImpression(HomePageElement.SEE_ALL_RECENT_ACCESS_BUTTON)
        verify(healthConnectLogger).logImpression(RecentAccessElement.RECENT_ACCESS_ENTRY_BUTTON)
    }

    @Test
    fun whenRecentAccessApps_showsRecentAccessApps() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))

        timeSource.setIs24Hour(true)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your phone, and control which apps can access it"))
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("None")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(doesNotExist())

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("18:40")).check(matches(isDisplayed()))
        onView(withText("See all recent access")).check(matches(isDisplayed()))
    }

    @Test
    fun whenRecentAccessApps_in12HourFormat_showsCorrectTime() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))

        timeSource.setIs24Hour(false)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("6:40 PM")).check(matches(isDisplayed()))
        onView(withText("See all recent access")).check(matches(isDisplayed()))
    }

    @Test
    fun test_HomeFragment_withNoRecentAccessApps() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED)))
        }
        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your phone, and control which apps can access it"))
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("2 apps have access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(doesNotExist())

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("No apps recently accessed Health\u00A0Connect"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun whenOneAppConnected_showsOneAppHasPermissions() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED)))
        }

        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your phone, and control which apps can access it"))
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("1 app has access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(doesNotExist())
    }

    @Test
    fun whenOneAppConnected_oneAppNotConnected_showsCorrectSummary() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.DENIED)))
        }

        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your phone, and control which apps can access it"))
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("1 of 2 apps have access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(doesNotExist())
    }

    @Test
    fun whenNewAppPriorityFlagOn_showsManageDataButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED)))
        }
        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your phone, and control which apps can access it"))
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("2 apps have access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("No apps recently accessed Health\u00A0Connect"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun whenNewInformationArchitectureFlagOn_showsManageDataButton() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewInformationArchitectureEnabled(true)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED)))
        }
        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your phone, and control which apps can access it"))
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("2 apps have access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("No apps recently accessed Health\u00A0Connect"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun whenMigrationStatePending_showsMigrationBanner() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_PAUSED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED)))
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Resume integration")).check(matches(isDisplayed()))
        onView(withText("Tap to continue integrating Health Connect with the Android system."))
            .check(matches(isDisplayed()))
        onView(withText("Continue")).check(matches(isDisplayed()))

        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_RESUME_BANNER)
        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_RESUME_BANNER_BUTTON)

        onView(withText("Continue")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.migrationActivity)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(MigrationElement.MIGRATION_RESUME_BANNER_BUTTON)
    }

    @Test
    @Ignore("b/327170886")
    fun whenDataRestoreStatePending_showsRestoreBanner() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.PENDING,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED)))
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Update needed")).check(matches(isDisplayed()))
        onView(withText("Before continuing restoring your data, update your phone system."))
            .check(matches(isDisplayed()))
        onView(withText("Update now")).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(DataRestoreElement.RESTORE_PENDING_BANNER)
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_PENDING_BANNER_UPDATE_BUTTON)

        onView(withText("Update now")).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.systemUpdateActivity)
        verify(healthConnectLogger)
            .logInteraction(DataRestoreElement.RESTORE_PENDING_BANNER_UPDATE_BUTTON)
    }

    @Test
    fun whenMigrationStateComplete_showsDialog() {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.WHATS_NEW_DIALOG_SEEN, false)
        editor.apply()

        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.COMPLETE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED)))
        }
        val scenario = launchFragment<HomeFragment>(Bundle())

        onView(withText("What's new")).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        onView(
                withText(
                    "You can now access Health Connect directly from your settings. Uninstall the Health Connect app any time to free up storage space."))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_DONE_DIALOG_CONTAINER)
        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_DONE_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).perform(click())
        verify(healthConnectLogger).logInteraction(MigrationElement.MIGRATION_DONE_DIALOG_BUTTON)

        scenario.onActivity { activity ->
            val preferences =
                activity.getSharedPreferences("USER_ACTIVITY_TRACKER", Context.MODE_PRIVATE)
            assertThat(preferences.getBoolean("Whats New Seen", false)).isTrue()
        }
    }

    @Test
    fun whenMigrationStateNotComplete_showsDialog() {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.MIGRATION_NOT_COMPLETE_DIALOG_SEEN, false)
        editor.apply()

        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_ERROR,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED)))
        }
        val scenario = launchFragment<HomeFragment>(Bundle())

        onView(withText("Health Connect integration didn't complete"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("You'll get a notification when it becomes available again."))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_BUTTON)

        scenario.onActivity { activity ->
            val preferences =
                activity.getSharedPreferences("USER_ACTIVITY_TRACKER", Context.MODE_PRIVATE)
            assertThat(preferences.getBoolean("Migration Not Complete Seen", false)).isTrue()
        }
    }

    private fun setupFragmentForNavigation() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewInformationArchitectureEnabled(true)
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                dataTypesWritten =
                mutableSetOf(
                    HealthDataCategory.ACTIVITY.uppercaseTitle(),
                    HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                mutableSetOf(
                    HealthDataCategory.SLEEP.uppercaseTitle(),
                    HealthDataCategory.NUTRITION.uppercaseTitle()))

        timeSource.setIs24Hour(true)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeFragmentViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
    }
}
