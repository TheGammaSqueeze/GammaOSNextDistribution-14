/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.tests.mocking.hibernation

import android.app.usage.UsageStatsManager
import android.apphibernation.AppHibernationManager
import android.content.Context
import android.content.Context.APP_HIBERNATION_SERVICE
import android.content.Context.USAGE_STATS_SERVICE
import android.os.Build
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.hibernation.v31.HibernationController
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.ContextCompat
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/** Unit tests for [HibernationController]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
class HibernationControllerTest {
    companion object {
        val application = mock(PermissionControllerApplication::class.java)

        const val USER_ID = 0
        const val PACKAGE_NAME_1 = "package_1"
        const val PACKAGE_NAME_2 = "package_2"

        const val TEST_UNUSED_THRESHOLD = 10000000L
        const val TEST_MOCK_DELAY = 1000000L
    }

    private var mockitoSession: MockitoSession? = null

    @Mock lateinit var context: Context
    @Mock lateinit var appHibernationManager: AppHibernationManager
    @Mock lateinit var usageStatsManager: UsageStatsManager

    lateinit var filesDir: File

    lateinit var hibernationController: HibernationController

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        whenever(PermissionControllerApplication.get()).thenReturn(application)
        filesDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir()
        whenever(application.filesDir).thenReturn(filesDir)

        doReturn(context).`when`(context).createContextAsUser(any(), anyInt())
        doReturn(appHibernationManager).`when`(context).getSystemService(APP_HIBERNATION_SERVICE)
        doReturn(usageStatsManager).`when`(context).getSystemService(USAGE_STATS_SERVICE)

        hibernationController =
            HibernationController(context, TEST_UNUSED_THRESHOLD, true /* targetsPreS */)
    }

    @After
    fun finish() {
        mockitoSession?.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun testHibernateApps_hibernatesAppsForUser() {
        // GIVEN a list of unused apps
        val userPackages = listOf(makePackageInfo(PACKAGE_NAME_1), makePackageInfo(PACKAGE_NAME_2))
        val map = mapOf(UserHandle.of(USER_ID) to userPackages)
        // WHEN the controller hibernates the apps
        val hibernatedApps = hibernationController.hibernateApps(map)

        // THEN the apps are hibernated for the user
        for (pkg in userPackages) {
            assertTrue(hibernatedApps.contains(pkg.packageName to UserHandle.of(USER_ID)))
            verify(appHibernationManager).setHibernatingForUser(pkg.packageName, true)
        }
    }

    @Test
    fun testHibernateApps_globallyUnusedAppIsGloballyHibernated() {
        // GIVEN an app that is globally unused (i.e. unused at a package level)
        val userPackages = listOf(makePackageInfo(PACKAGE_NAME_1), makePackageInfo(PACKAGE_NAME_2))
        val map = mapOf(UserHandle.of(USER_ID) to userPackages)
        whenever(usageStatsManager.getLastTimeAnyComponentUsed(PACKAGE_NAME_1))
            .thenReturn(System.currentTimeMillis() - (TEST_UNUSED_THRESHOLD + TEST_MOCK_DELAY))

        // WHEN the controller hibernates the apps
        hibernationController.hibernateApps(map)

        // THEN the app was hibernated globally
        verify(appHibernationManager).setHibernatingGlobally(PACKAGE_NAME_1, true)
    }

    @Test
    fun testHibernateApps_globallyUsedAppIsNotGloballyHibernated() {
        // GIVEN an app that has been used globally (i.e. used at a package level)
        val userPackages = listOf(makePackageInfo(PACKAGE_NAME_1), makePackageInfo(PACKAGE_NAME_2))
        val map = mapOf(UserHandle.of(USER_ID) to userPackages)
        whenever(usageStatsManager.getLastTimeAnyComponentUsed(PACKAGE_NAME_1))
            .thenReturn(System.currentTimeMillis() - (TEST_UNUSED_THRESHOLD - TEST_MOCK_DELAY))

        // WHEN the controller hibernates the apps
        hibernationController.hibernateApps(map)

        // THEN the app was NOT hibernated globally
        verify(appHibernationManager, never()).setHibernatingGlobally(PACKAGE_NAME_1, true)
    }

    private fun makePackageInfo(packageName: String): LightPackageInfo {
        return LightPackageInfo(
            packageName,
            emptyList(),
            emptyList(),
            emptyList(),
            0 /* uid */,
            Build.VERSION_CODES.CUR_DEVELOPMENT,
            false /* isInstantApp */,
            true /* enabled */,
            0 /* appFlags */,
            0 /* firstInstallTime */,
            0 /* lastUpdateTime */,
            false /* areAttributionsUserVisible */,
            emptyMap() /* attributionTagsToLabels */,
            ContextCompat.DEVICE_ID_DEFAULT
        )
    }
}
