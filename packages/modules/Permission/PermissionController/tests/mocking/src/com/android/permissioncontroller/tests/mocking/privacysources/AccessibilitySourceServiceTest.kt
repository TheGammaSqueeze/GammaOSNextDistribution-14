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

package com.android.permissioncontroller.tests.mocking.privacysources

import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.privacysources.AccessibilityJobService
import com.android.permissioncontroller.privacysources.AccessibilitySourceService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/**
 * Unit tests for internal [AccessibilitySourceService]
 *
 * <p> Does not test notification as there are conflicts with being able to mock NotificationManager
 * and PendingIntent.getBroadcast requiring a valid context. Notifications are tested in the CTS
 * integration tests
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class AccessibilitySourceServiceTest {

    @Mock lateinit var jobService: AccessibilityJobService
    private lateinit var context: Context
    private lateinit var mockitoSession: MockitoSession
    private lateinit var accessibilitySourceService: AccessibilitySourceService
    private var shouldCancel = false
    private lateinit var sharedPref: SharedPreferences

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()

        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        accessibilitySourceService = runWithShellPermissionIdentity {
            AccessibilitySourceService(context)
        }
        sharedPref = accessibilitySourceService.getSharedPreference()
        sharedPref.edit().clear().apply()
    }

    @After
    fun cleanup() {
        shouldCancel = false
        mockitoSession.finishMocking()
        sharedPref.edit().clear().apply()
    }

    @Test
    fun processAccessibilityJobWithCancellation() {
        shouldCancel = true
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                accessibilitySourceService.processAccessibilityJob(jobParameters, jobService) {
                    shouldCancel
                }
            }
        }
        verify(jobService).jobFinished(jobParameters, true)
    }

    @Test
    fun markServiceAsNotified() {
        val a11yService = ComponentName("com.test.package", "AccessibilityService")
        runBlocking { accessibilitySourceService.markServiceAsNotified(a11yService) }

        val storedServices = getNotifiedServices()
        assertThat(storedServices.size).isEqualTo(1)
        assertThat(storedServices.iterator().next()).isEqualTo(a11yService.flattenToShortString())
    }

    @Test
    fun markAsNotifiedWithSecondComponent() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package2", "TestClass2")

        var notifiedServices = runBlocking {
            accessibilitySourceService.markServiceAsNotified(testComponent)
            getNotifiedServices()
        }
        assertThat(notifiedServices.size).isEqualTo(1)
        assertThat(notifiedServices.iterator().next())
            .isEqualTo(testComponent.flattenToShortString())

        notifiedServices = runBlocking {
            accessibilitySourceService.markServiceAsNotified(testComponent2)
            getNotifiedServices()
        }
        assertThat(notifiedServices.size).isEqualTo(2)
        val expectedServices = listOf(testComponent, testComponent2)
        expectedServices.forEach {
            assertThat(notifiedServices.contains(it.flattenToShortString())).isTrue()
        }
    }
    @Test
    fun removeNotifiedService() {
        val a11yService = ComponentName("com.test.package", "AccessibilityService")
        val a11yService2 = ComponentName("com.test.package", "AccessibilityService2")
        val a11yService3 = ComponentName("com.test.package", "AccessibilityService3")
        val allServices = listOf(a11yService, a11yService2, a11yService3)

        val notifiedServices = runBlocking {
            allServices.forEach { accessibilitySourceService.markServiceAsNotified(it) }
            accessibilitySourceService.removeFromNotifiedServices(a11yService2)
            getNotifiedServices()
        }
        val expectedServices = listOf(a11yService, a11yService3)
        assertThat(notifiedServices.size).isEqualTo(2)
        expectedServices.forEach {
            assertThat(notifiedServices.contains(it.flattenToShortString())).isTrue()
        }
    }

    @Test
    fun removePackageState() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package", "TestClass2")
        val testComponent3 = ComponentName("com.test.package2", "TestClass3")
        val testComponents = listOf(testComponent, testComponent2, testComponent3)

        val notifiedServices = runBlocking {
            testComponents.forEach { accessibilitySourceService.markServiceAsNotified(it) }
            accessibilitySourceService.removePackageState(testComponent.packageName)
            getNotifiedServices()
        }

        assertThat(notifiedServices.size).isEqualTo(1)
        assertThat(notifiedServices.contains(testComponent3.flattenToShortString())).isTrue()
    }

    @Test
    fun removePackageStateWithMultipleServicePerPackage() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package", "TestClass2")
        val testComponents = listOf(testComponent, testComponent2)

        val notifiedServices = runBlocking {
            testComponents.forEach { accessibilitySourceService.markServiceAsNotified(it) }
            accessibilitySourceService.removePackageState(testComponent.packageName)
            getNotifiedServices()
        }

        assertThat(notifiedServices).isEmpty()
    }

    @Test
    fun removePackageState_noPreviouslyNotifiedPackage() {
        val testComponent = ComponentName("com.test.package", "TestClass")

        // Get the initial list of Components
        val initialComponents = getNotifiedServices()

        // Verify no components are present
        assertThat(initialComponents).isEmpty()

        // Forget about test package, and get the resulting list of Components
        // Filter to the component that match the test component
        val updatedComponents = runWithShellPermissionIdentity {
            runBlocking {
                // Verify this should not fail!
                accessibilitySourceService.removePackageState(testComponent.packageName)
                getNotifiedServices()
            }
        }

        // Verify no components are present
        assertThat(updatedComponents).isEmpty()
    }

    private fun getNotifiedServices(): MutableSet<String> {
        return sharedPref.getStringSet(
            AccessibilitySourceService.KEY_ALREADY_NOTIFIED_SERVICES,
            mutableSetOf<String>()
        )!!
    }

    private fun <R> runWithShellPermissionIdentity(block: () -> R): R {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        uiAutomation.adoptShellPermissionIdentity()
        try {
            return block()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }
}
