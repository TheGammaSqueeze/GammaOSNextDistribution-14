package com.android.healthconnect.controller.tests.migration

import android.content.Context
import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.ModuleUpdateRequiredFragment
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.google.common.truth.Truth
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
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
class ModuleUpdateRequiredFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue val navigationUtils: NavigationUtils = Mockito.mock(NavigationUtils::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        reset(healthConnectLogger)
    }

    @Test
    fun moduleUpdateRequiredFragment_displaysCorrectly() {
        launchFragment<ModuleUpdateRequiredFragment>()

        onView(withText("Update needed")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Health Connect is being integrated with the Android system so " +
                        "you can access it directly from your settings."))
            .check(matches(isDisplayed()))
        onView(withText("Before continuing, update your phone system."))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "If you\'ve already updated your phone system, " +
                        "try restarting your phone to continue the integration"))
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Update")).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1))
            .setPageId(PageName.MIGRATION_MODULE_UPDATE_NEEDED_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_UPDATE_NEEDED_CANCEL_BUTTON)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_UPDATE_NEEDED_UPDATE_BUTTON)
    }

    @Test
    fun moduleUpdateRequiredFragment_whenCancelButtonPressed_setsSharedPreferences() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        val scenario = launchFragment<ModuleUpdateRequiredFragment>(Bundle())
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Cancel")).perform(ViewActions.click())

        scenario.onActivity { activity ->
            val preferences =
                activity.getSharedPreferences("USER_ACTIVITY_TRACKER", Context.MODE_PRIVATE)
            Truth.assertThat(preferences.getBoolean("Module Update Seen", false)).isTrue()
        }
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_UPDATE_NEEDED_CANCEL_BUTTON)
    }

    @Test
    fun moduleUpdateRequiredFragment_whenUpdateButtonPressed_navigatesToSystemUpdate() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        launchFragment<ModuleUpdateRequiredFragment>(Bundle())
        onView(withText("Update")).check(matches(isDisplayed()))
        onView(withText("Update")).perform(ViewActions.click())

        verify(navigationUtils, times(1))
            .navigate(
                any(), eq(R.id.action_migrationModuleUpdateNeededFragment_to_systemUpdateActivity))
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_UPDATE_NEEDED_UPDATE_BUTTON)
    }

    @Test
    fun moduleUpdateRequiredFragment_whenNavigateToSystemUpdateFails_displaysCorrectly() {
        whenever(navigationUtils.navigate(any(), any())).thenThrow(RuntimeException("Exception"))
        launchFragment<ModuleUpdateRequiredFragment>(Bundle())
        onView(withText("Update")).check(matches(isDisplayed()))
        onView(withText("Update")).perform(ViewActions.click())

        onView(withText("Update needed")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Health Connect is being integrated with the Android system so " +
                        "you can access it directly from your settings."))
            .check(matches(isDisplayed()))
        onView(withText("Before continuing, update your phone system."))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "If you\'ve already updated your phone system, " +
                        "try restarting your phone to continue the integration"))
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Update")).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_UPDATE_NEEDED_UPDATE_BUTTON)
    }
}
