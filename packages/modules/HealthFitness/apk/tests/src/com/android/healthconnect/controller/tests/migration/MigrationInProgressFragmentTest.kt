package com.android.healthconnect.controller.tests.migration

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.migration.MigrationInProgressFragment
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
class MigrationInProgressFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun migrationInProgressFragment_displaysCorrectly() {
        launchFragment<MigrationInProgressFragment>()

        onView(withText("Integration in progress")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Health Connect is being integrated with the Android system." +
                        "\n\nIt may take some time while your data and permissions are being transferred."))
            .check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.MIGRATION_IN_PROGRESS_PAGE)
        verify(healthConnectLogger).logPageImpression()
    }
}
