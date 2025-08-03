/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.settings.applications.appinfo

import android.car.drivingstate.CarUxRestrictions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.settings.common.FragmentController
import com.android.car.settings.common.PreferenceController.AVAILABLE
import com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE
import com.android.car.settings.common.PreferenceControllerTestUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppAllServicesPreferenceControllerTest {
    private val mockFragmentController = mock<FragmentController>()
    private val mockPackageManager = mock<PackageManager>()
    private val mockResolveInfo = mock<ResolveInfo>()
    private val mockLocationManager = mock<LocationManager>()
    private val context = spy(ApplicationProvider.getApplicationContext<Context>()) {
        on { packageManager } doReturn mockPackageManager
        on { getSystemService(LocationManager::class.java) } doReturn mockLocationManager
    }
    private val carUxRestrictions = CarUxRestrictions.Builder(
        /* reqOpt = */ true,
        CarUxRestrictions.UX_RESTRICTIONS_BASELINE,
        /* time = */ 0
    ).build()
    private val controller = AppAllServicesPreferenceController(
        context,
        preferenceKey,
        mockFragmentController,
        carUxRestrictions
    )

    @Test
    fun getAvailabilityStatus_shouldReturnAvailable() {
        controller.setPackageName(primaryPackageName)
        val intent = getFeaturesIntent(primaryPackageName)
        whenever(
            mockPackageManager.resolveActivity(matchIntent(intent), /* flags = */ eq(0))
        ) doReturn mockResolveInfo
        whenever(
            mockLocationManager.isProviderPackage(null, primaryPackageName, null)
        ) doReturn true

        PreferenceControllerTestUtil.assertAvailability(controller.availabilityStatus, AVAILABLE)
    }

    @Test
    fun getAvailabilityStatus_isNotLocationProvider_shouldReturnConditionallyUnavailable() {
        controller.setPackageName(primaryPackageName)
        val intent = getFeaturesIntent(primaryPackageName)
        whenever(
            mockPackageManager.resolveActivity(matchIntent(intent), /* flags = */ eq(0))
        ) doReturn mockResolveInfo
        whenever(
            mockLocationManager.isProviderPackage(null, primaryPackageName, null)
        ) doReturn false

        PreferenceControllerTestUtil.assertAvailability(
            controller.availabilityStatus,
            CONDITIONALLY_UNAVAILABLE
        )
    }

    @Test
    fun getAvailabilityStatus_canNotHandleIntent_shouldReturnConditionallyUnavailable() {
        controller.setPackageName(primaryPackageName)
        val intent = getFeaturesIntent(secondaryPackageName)
        // Trying to match with a package that cannot handle the intent
        whenever(
            mockPackageManager.resolveActivity(matchIntent(intent), /* flags = */ eq(0))
        ) doReturn mockResolveInfo
        whenever(
            mockLocationManager.isProviderPackage(null, primaryPackageName, null)
        ) doReturn true

        PreferenceControllerTestUtil.assertAvailability(
            controller.availabilityStatus,
            CONDITIONALLY_UNAVAILABLE
        )
    }

    @Test
    fun getAvailabilityStatus_shouldReturnConditionallyUnavailable() {
        controller.setPackageName(primaryPackageName)
        val intent = getFeaturesIntent(secondaryPackageName)
        whenever(
            mockPackageManager.resolveActivity(matchIntent(intent), /* flags = */ eq(0))
        ) doReturn mockResolveInfo
        whenever(
            mockLocationManager.isProviderPackage(null, primaryPackageName, null)
        ) doReturn false

        PreferenceControllerTestUtil.assertAvailability(
            controller.availabilityStatus,
            CONDITIONALLY_UNAVAILABLE
        )
    }

    @Test
    fun canPackageHandleIntent_nullPackageInfo_shouldNotCrash() {
        controller.setPackageName(null)

        // no crash
        PreferenceControllerTestUtil.assertAvailability(
            controller.availabilityStatus,
            CONDITIONALLY_UNAVAILABLE
        )
    }

    private fun getFeaturesIntent(pkgName: String?): Intent {
        return Intent(Intent.ACTION_VIEW_APP_FEATURES).apply {
            `package` = pkgName
        }
    }

    private fun matchIntent(incoming: Intent): Intent {
        return argThat(IntentEqualityMatcher(incoming))
    }

    private class IntentEqualityMatcher (private val baseIntent: Intent) : ArgumentMatcher<Intent> {
        override fun matches(argument: Intent?): Boolean {
            return baseIntent.`package` == argument?.`package` &&
                baseIntent.action == argument?.action
        }
    }

    private companion object {
        const val primaryPackageName = "Package1"
        const val secondaryPackageName = "Package2"
        const val preferenceKey = "pref_key"
    }
}
