package com.android.healthconnect.controller.tests.permissions.connectedapps.searchapps

import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel
import com.android.healthconnect.controller.permissions.connectedapps.searchapps.SearchAppsFragment
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME_3
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
class SearchAppsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: ConnectedAppsViewModel = Mockito.mock(ConnectedAppsViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        toggleAnimation(false)
    }

    @After
    fun teardown() {
        reset(healthConnectLogger)
        toggleAnimation(true)
    }

    @Test
    fun searchAppsFragment_isDisplayedCorrectly() {
        whenever(viewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                    ConnectedAppMetadata(TEST_APP_3, status = ConnectedAppStatus.INACTIVE)))
        }

        launchFragment<SearchAppsFragment>(Bundle())

        onView(withText("Allowed access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("Not allowed access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
        onView(withText("Inactive apps")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_3)).check(matches(isDisplayed()))
        onView(withText(R.string.connected_apps_text)).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.SEARCH_APPS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(AppPermissionsElement.CONNECTED_APP_BUTTON)
        verify(healthConnectLogger).logImpression(AppPermissionsElement.NOT_CONNECTED_APP_BUTTON)
        verify(healthConnectLogger).logImpression(AppPermissionsElement.INACTIVE_APP_BUTTON)
    }

    @Test
    fun noAppsAllowed_doesNotShowAllowedAccessSection() {
        whenever(viewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.DENIED),
                    ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                    ConnectedAppMetadata(TEST_APP_3, status = ConnectedAppStatus.INACTIVE)))
        }

        launchFragment<SearchAppsFragment>(Bundle())

        onView(withText("Allowed access")).check(doesNotExist())
        onView(withText("Not allowed access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
        onView(withText("Inactive apps")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_3)).check(matches(isDisplayed()))
        onView(withText(R.string.connected_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun noAppsDenied_doesNotShowAllowedAccessSection() {
        whenever(viewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_3, status = ConnectedAppStatus.ALLOWED)))
        }

        launchFragment<SearchAppsFragment>(Bundle())

        onView(withText("Allowed access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_3)).check(matches(isDisplayed()))
        onView(withText("Not allowed access")).check(doesNotExist())
        onView(withText("Inactive apps")).check(doesNotExist())
        onView(withText(R.string.connected_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun noApps_displaysEmptyState() {
        whenever(viewModel.connectedApps).then {
            MutableLiveData(emptyList<ConnectedAppMetadata>())
        }

        launchFragment<SearchAppsFragment>(Bundle())

        onView(withText("No Results")).check(matches(isDisplayed()))
    }
}
