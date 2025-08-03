package com.android.healthconnect.controller.tests.utils

import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class DeviceInfoUtilsTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun test_correctHcGetStartedLinkIsPassedForTheActionViewIntent() {
        assertThat(
                InstrumentationRegistry.getInstrumentation()
                    .context
                    .getResources()
                    .getString(R.string.hc_get_started_link))
            .isEqualTo("https://support.google.com/android?p=get_started_hc")
    }
}
