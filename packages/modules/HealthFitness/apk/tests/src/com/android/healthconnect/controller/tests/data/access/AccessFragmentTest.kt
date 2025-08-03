/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.data.access

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.access.AccessFragment
import com.android.healthconnect.controller.data.access.AccessViewModel
import com.android.healthconnect.controller.data.access.AccessViewModel.AccessScreenState
import com.android.healthconnect.controller.data.access.AccessViewModel.AccessScreenState.WithData
import com.android.healthconnect.controller.data.access.AppAccessState
import com.android.healthconnect.controller.dataaccess.HealthDataAccessFragment
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
class AccessFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val viewModel: AccessViewModel = Mockito.mock(AccessViewModel::class.java)
    private lateinit var navHostController: TestNavHostController
    private lateinit var context : Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @Test
    fun dataAccessFragment_noSections_noneDisplayed() {
        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessViewModel.AccessScreenState>(WithData(emptyMap()))
        }
        launchFragment<HealthDataAccessFragment>(distanceBundle())

        onView(withText("Can read distance")).check(doesNotExist())
        onView(withText("Can write distance")).check(doesNotExist())
        onView(withText("Inactive apps")).check(doesNotExist())
        onView(
                withText(
                    "These apps can no longer write distance, but still have data stored in Health\u00A0Connect"))
            .check(doesNotExist())
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("See all entries")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Delete this data")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun dataAccessFragment_readSection_isDisplayed() {
        val map =
            mapOf(
                AppAccessState.Read to listOf(AppMetadata("package1", "appName1", null)),
                AppAccessState.Write to emptyList(),
                AppAccessState.Inactive to emptyList())
        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessScreenState>(WithData(map))
        }
        launchFragment<HealthDataAccessFragment>(distanceBundle())

        onView(withText("Can read distance")).check(matches(isDisplayed()))
        onView(withText("Can write distance")).check(doesNotExist())
        onView(withText("Inactive apps")).check(doesNotExist())
        onView(
                withText(
                    "These apps can no longer write distance, but still have data stored in Health\u00A0Connect"))
            .check(doesNotExist())
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("See all entries")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Delete this data")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun dataAccessFragment_readAndWriteSections_isDisplayed() {
        val map =
            mapOf(
                AppAccessState.Read to listOf(AppMetadata("package1", "appName1", null)),
                AppAccessState.Write to listOf(AppMetadata("package1", "appName1", null)),
                AppAccessState.Inactive to emptyList())
        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessScreenState>(WithData(map))
        }
        launchFragment<HealthDataAccessFragment>(distanceBundle())

        onView(withText("Can read distance")).check(matches(isDisplayed()))
        onView(withText("Can write distance")).check(matches(isDisplayed()))
        onView(withText("Inactive apps")).check(doesNotExist())
        onView(
                withText(
                    "These apps can no longer write distance, but still have data stored in Health\u00A0Connect"))
            .check(doesNotExist())
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("See all entries")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Delete this data")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun dataAccessFragment_inactiveSection_isDisplayed() {
        val map =
            mapOf(
                AppAccessState.Read to emptyList(),
                AppAccessState.Write to emptyList(),
                AppAccessState.Inactive to listOf(AppMetadata("package1", "appName1", null)))
        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessViewModel.AccessScreenState>(WithData(map))
        }
        launchFragment<HealthDataAccessFragment>(distanceBundle())

        onView(withText("Can read distance")).check(doesNotExist())
        onView(withText("Can write distance")).check(doesNotExist())
        onView(withText("Inactive apps")).check(matches(isDisplayed()))
        onView(
                withText(
                    "These apps can no longer write distance, but still have data stored in Health\u00A0Connect"))
            .check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("See all entries")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Delete this data")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun dataAccessFragment_loadingState_showsLoading() {
        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessScreenState>(AccessScreenState.Loading)
        }
        launchFragment<HealthDataAccessFragment>(distanceBundle())
        onView(withId(R.id.progress_indicator)).check(matches(isDisplayed()))
    }

    @Test
    fun dataAccessFragment_withData_hidesLoading() {
        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessScreenState>(WithData(emptyMap()))
        }
        launchFragment<HealthDataAccessFragment>(distanceBundle())
        onView(withId(R.id.progress_indicator)).check(matches(not(isDisplayed())))
    }

    @Test
    fun dataAccessFragment_withError_showError() {
        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessScreenState>(AccessScreenState.Error)
        }
        launchFragment<HealthDataAccessFragment>(distanceBundle())
        onView(withId(R.id.progress_indicator)).check(matches(not(isDisplayed())))
        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun whenAppNameClicked_navigatesToConnectedApp() {
        val map =
                mapOf(
                        AppAccessState.Read to listOf(TEST_APP),
                        AppAccessState.Write to emptyList(),
                        AppAccessState.Inactive to emptyList())

        whenever(viewModel.appMetadataMap).then {
            MutableLiveData<AccessScreenState>(WithData(map))
        }

        launchFragment<AccessFragment>(distanceBundle()) {
            navHostController.setGraph(R.navigation.data_nav_graph_new_ia)
            navHostController.setCurrentDestination(R.id.entriesAndAccessFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.connectedAppFragment)
    }

    private fun distanceBundle(): Bundle {
        val bundle = Bundle()
        bundle.putSerializable(PERMISSION_TYPE_KEY, HealthPermissionType.DISTANCE)
        return bundle
    }
}
