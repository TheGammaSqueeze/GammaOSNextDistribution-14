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
package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.content.Intent
import android.content.Intent.*
import androidx.core.os.bundleOf
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.preference.PreferenceCategory
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel.RevokeAllState.NotStarted
import com.android.healthconnect.controller.permissions.app.ConnectedAppFragment
import com.android.healthconnect.controller.permissions.app.HealthPermissionStatus
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.DISTANCE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.EXERCISE
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType.READ
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType.WRITE
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.TestActivity
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.safeEq
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.AppAccessElement
import com.android.healthconnect.controller.utils.logging.DisconnectAppDialogElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.settingslib.widget.MainSwitchPreference
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.kotlin.mock

@HiltAndroidTest
class ConnectedAppFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils

    @BindValue val viewModel: AppPermissionViewModel = mock()
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()
    @BindValue val healthPermissionReader: HealthPermissionReader = mock()
    @BindValue val additionalAccessViewModel: AdditionalAccessViewModel = mock()
    private lateinit var navHostController: TestNavHostController

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        navHostController = TestNavHostController(context)
        hiltRule.inject()
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewInformationArchitectureEnabled(false)

        whenever(viewModel.revokeAllPermissionsState).then { MutableLiveData(NotStarted) }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(false) }
        whenever(viewModel.atLeastOnePermissionGranted).then { MediatorLiveData(true) }
        whenever(viewModel.showDisableExerciseRouteEvent)
            .thenReturn(MediatorLiveData(AppPermissionViewModel.DisableExerciseRouteDialogEvent()))
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(emptySet<HealthPermission>())
        }
        val accessDate = Instant.parse("2022-10-20T18:40:13.00Z")
        whenever(viewModel.loadAccessDate(Mockito.anyString())).thenReturn(accessDate)
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
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

        // disable animations
        toggleAnimation(false)
        Intents.init()
    }

    @After
    fun teardown() {
        reset(healthConnectLogger)
        // enable animations
        toggleAnimation(true)
        Intents.release()
    }

    @Test
    fun test_noPermissions() {
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf<HealthPermissionStatus>())
        }

        val scenario =
            launchFragment<ConnectedAppFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as ConnectedAppFragment
            val readCategory =
                fragment.preferenceScreen.findPreference("read_permission_category")
                    as PreferenceCategory?
            val writeCategory =
                fragment.preferenceScreen.findPreference("write_permission_category")
                    as PreferenceCategory?
            assertThat(readCategory?.preferenceCount).isEqualTo(0)
            assertThat(writeCategory?.preferenceCount).isEqualTo(0)
        }
        onView(withText("See app data")).check(doesNotExist())
    }

    @Test
    fun test_readPermission() {
        val permission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then { MutableLiveData(listOf(permission)) }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(permission)) }

        val scenario =
            launchFragment<ConnectedAppFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as ConnectedAppFragment
            val readCategory =
                fragment.preferenceScreen.findPreference("read_permission_category")
                    as PreferenceCategory?
            val writeCategory =
                fragment.preferenceScreen.findPreference("write_permission_category")
                    as PreferenceCategory?
            assertThat(readCategory?.preferenceCount).isEqualTo(1)
            assertThat(writeCategory?.preferenceCount).isEqualTo(0)
        }
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("See app data")).check(doesNotExist())
        onView(withText("Delete app data")).check(matches(isDisplayed()))
    }

    @Test
    fun test_writePermission() {
        val permission = HealthPermission(EXERCISE, WRITE)
        whenever(viewModel.appPermissions).then { MutableLiveData(listOf(permission)) }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(permission)) }

        val scenario =
            launchFragment<ConnectedAppFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as ConnectedAppFragment
            val readCategory =
                fragment.preferenceScreen.findPreference("read_permission_category")
                    as PreferenceCategory?
            val writeCategory =
                fragment.preferenceScreen.findPreference("write_permission_category")
                    as PreferenceCategory?
            assertThat(readCategory?.preferenceCount).isEqualTo(0)
            assertThat(writeCategory?.preferenceCount).isEqualTo(1)
        }
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("See app data")).check(doesNotExist())
    }

    @Test
    fun test_readAndWritePermission() {
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(writePermission)) }

        val scenario =
            launchFragment<ConnectedAppFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as ConnectedAppFragment
            val readCategory =
                fragment.preferenceScreen.findPreference("read_permission_category")
                    as PreferenceCategory?
            val writeCategory =
                fragment.preferenceScreen.findPreference("write_permission_category")
                    as PreferenceCategory?
            assertThat(readCategory?.preferenceCount).isEqualTo(1)
            assertThat(writeCategory?.preferenceCount).isEqualTo(1)
        }
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("See app data")).check(doesNotExist())

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.APP_ACCESS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        // TODO (b/325680041) investigate why these are not active
        verify(healthConnectLogger, times(2))
            .logImpression(AppAccessElement.PERMISSION_SWITCH_INACTIVE)
        verify(healthConnectLogger)
            .logImpression(AppAccessElement.ALLOW_ALL_PERMISSIONS_SWITCH_INACTIVE)
    }

    @Test
    fun test_allowAllToggleOn_whenAllPermissionsOn() {
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(writePermission, readPermission))
        }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(true) }

        val scenario =
            launchFragment<ConnectedAppFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as ConnectedAppFragment
            val mainSwitchPreference =
                fragment.preferenceScreen.findPreference("allow_all_preference")
                    as MainSwitchPreference?

            assertThat(mainSwitchPreference?.isChecked).isTrue()
        }
        // TODO (b/325680041) investigate why not active
        verify(healthConnectLogger)
            .logImpression(AppAccessElement.ALLOW_ALL_PERMISSIONS_SWITCH_INACTIVE)
        onView(withText("See app data")).check(doesNotExist())
    }

    @Test
    fun test_allowAllToggleOff_whenAtLeastOnePermissionOff() {
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(false) }

        val scenario =
            launchFragment<ConnectedAppFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as ConnectedAppFragment

            val mainSwitchPreference =
                fragment.preferenceScreen.findPreference("allow_all_preference")
                    as MainSwitchPreference?

            assertThat(mainSwitchPreference?.isChecked).isFalse()
        }
    }

    @Test
    fun allowAll_toggleOff_showsDisconnectDialog() {
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(true) }
        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))
        onView(withText("Allow all")).perform(click())

        onView(withText("Remove all permissions?")).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CANCEL_BUTTON)
        verify(healthConnectLogger)
            .logImpression(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CONFIRM_BUTTON)
        verify(healthConnectLogger)
            .logImpression(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_DELETE_CHECKBOX)

        onView(withText("See app data")).check(doesNotExist())
    }

    @Test
    fun allowAll_toggleOff_onDialogRemoveAllClicked_disconnectAllPermissions() {
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(writePermission, readPermission))
        }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(true) }
        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))
        onView(withText("Allow all")).perform(click())

        onView(withText("Remove all")).perform(click())
        verify(healthConnectLogger)
            .logInteraction(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CONFIRM_BUTTON)

        onView(withText("Exercise")).check(matches(not(isChecked())))
        onView(withText("Distance")).check(matches(not(isChecked())))
        onView(withText("See app data")).check(doesNotExist())
    }

    @Test
    fun allowAll_toggleOff_deleteDataSelected_onDialogRemoveAllClicked_deleteIsCalled() {
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(writePermission, readPermission))
        }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(true) }
        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))
        onView(withText("Allow all")).perform(click())

        onView(withId(R.id.dialog_checkbox)).perform(click())
        onView(withText("Remove all")).perform(click())
        verify(healthConnectLogger)
            .logInteraction(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CONFIRM_BUTTON)
        verify(healthConnectLogger)
            .logInteraction(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_DELETE_CHECKBOX)

        verify(viewModel).deleteAppData(safeEq(TEST_APP_PACKAGE_NAME), safeEq(TEST_APP_NAME))
    }

    @Test
    fun test_footerWithGrantTime_isDisplayed() {
        val permission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then { MutableLiveData(listOf(permission)) }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(setOf(permission)) }
        whenever(healthPermissionReader.isRationalIntentDeclared(TEST_APP_PACKAGE_NAME))
            .thenReturn(true)
        whenever(healthPermissionReader.getApplicationRationaleIntent(TEST_APP_PACKAGE_NAME))
            .thenReturn(Intent())
        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(
                withText(
                    "$TEST_APP_NAME can read data added after October 20, 2022" +
                        "\n\n" +
                        "To manage other Android permissions this app can " +
                        "access, go to Settings > Apps" +
                        "\n\n" +
                        "Data you share with $TEST_APP_NAME is covered by their privacy policy"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Read privacy policy")).perform(scrollTo()).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppAccessElement.PRIVACY_POLICY_LINK)
    }

    @Test
    fun footerWithoutGrantTime_isDisplayed() {
        val permission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then { MutableLiveData(listOf(permission)) }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData<Set<HealthPermission>>(setOf())
        }
        whenever(viewModel.atLeastOnePermissionGranted).then { MediatorLiveData(false) }
        whenever(healthPermissionReader.isRationalIntentDeclared(TEST_APP_PACKAGE_NAME))
            .thenReturn(true)
        whenever(healthPermissionReader.getApplicationRationaleIntent(TEST_APP_PACKAGE_NAME))
            .thenReturn(Intent())
        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(
                withText(
                    "To manage other Android permissions this app can " +
                        "access, go to Settings > Apps" +
                        "\n\n" +
                        "Data you share with $TEST_APP_NAME is covered by their privacy policy"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Read privacy policy")).perform(scrollTo()).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppAccessElement.PRIVACY_POLICY_LINK)
    }

    // TODO unignore when stable
    @Test
    @Ignore
    fun whenClickOnPrivacyPolicyLink_startsRationaleActivity() {
        val rationaleAction = "android.intent.action.VIEW_PERMISSION_USAGE"
        val permission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then { MutableLiveData(listOf(permission)) }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData<Set<HealthPermission>>(setOf())
        }
        whenever(viewModel.atLeastOnePermissionGranted).then { MediatorLiveData(false) }
        whenever(healthPermissionReader.isRationalIntentDeclared(TEST_APP_PACKAGE_NAME))
            .thenReturn(true)
        whenever(healthPermissionReader.getApplicationRationaleIntent(TEST_APP_PACKAGE_NAME))
            .thenReturn(Intent(rationaleAction))

        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(
                withText(
                    "To manage other Android permissions this app can " +
                        "access, go to Settings > Apps" +
                        "\n\n" +
                        "Data you share with $TEST_APP_NAME is covered by their privacy policy"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Read privacy policy")).perform(scrollTo()).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppAccessElement.PRIVACY_POLICY_LINK)

        onView(withText("Read privacy policy")).perform(scrollTo()).perform(click())
        intended(hasAction(rationaleAction))
    }

    @Test
    fun seeAppData_isEnabled_buttonDisplayed() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewInformationArchitectureEnabled(true)
        val writePermission = HealthPermission(EXERCISE, WRITE)
        val readPermission = HealthPermission(DISTANCE, READ)
        whenever(viewModel.appPermissions).then {
            MutableLiveData(listOf(writePermission, readPermission))
        }
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(writePermission, readPermission))
        }
        whenever(viewModel.allAppPermissionsGranted).then { MediatorLiveData(true) }
        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))
        onView(withText("See app data")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Delete app data")).check(doesNotExist())
    }

    @Test
    fun additionalAccessState_notValid_hidesAdditionalAccess() {
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(AdditionalAccessViewModel.State())
        }

        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

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

        launchFragment<ConnectedAppFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(withText(R.string.additional_access_label))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
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

        launchFragment<ConnectedAppFragment>(
            bundleOf(
                EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME)) {
                navHostController.setGraph(R.navigation.nav_graph)
                navHostController.setCurrentDestination(R.id.connectedAppFragment)
                Navigation.setViewNavController(requireView(), navHostController)
            }
        onView(withText(R.string.additional_access_label)).perform(scrollTo()).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.additionalAccessFragment)
    }
}
