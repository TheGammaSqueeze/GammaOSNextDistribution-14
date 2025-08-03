package com.android.healthconnect.controller.tests.utils

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.utils.AppStoreUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class AppStoreUtilTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var appStoreUtils: AppStoreUtils
    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        hiltRule.inject()
    }

    @Test
    fun getAppStoreLink_validPackage_returnsCorrectIntent() {
        // skip the test on AOSP devices
        if (!deviceInfoUtils.isPlayStoreAvailable(context)) {
            return
        }

        val intent = appStoreUtils.getAppStoreLink(TEST_APP_PACKAGE_NAME)

        assertThat(intent).isNotNull()
        assertThat(intent!!.action).isEqualTo("android.intent.action.SHOW_APP_INFO")
        assertThat(intent.extras?.get("android.intent.extra.PACKAGE_NAME"))
            .isEqualTo(TEST_APP_PACKAGE_NAME)
    }
}
