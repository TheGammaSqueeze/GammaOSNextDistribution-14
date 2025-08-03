/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissions.connectedapps.LoadHealthPermissionApps
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus
import com.android.healthconnect.controller.tests.utils.OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.di.FakeGetContributorAppInfoUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeGetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeQueryRecentAccessLogsUseCase
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@ExperimentalCoroutinesApi
@HiltAndroidTest
class LoadHealthPermissionAppsTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context

    private val healthPermissionReader: HealthPermissionReader =
        Mockito.mock(HealthPermissionReader::class.java)
    private val loadGrantedHealthPermissionsUseCase = FakeGetGrantedHealthPermissionsUseCase()
    private val getContributorAppInfoUseCase = FakeGetContributorAppInfoUseCase()
    private val queryRecentAccessLogsUseCase = FakeQueryRecentAccessLogsUseCase()
    @Inject lateinit var appInfoReader: AppInfoReader

    private lateinit var loadHealthPermissionApps: LoadHealthPermissionApps

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        loadHealthPermissionApps =
            LoadHealthPermissionApps(
                healthPermissionReader,
                loadGrantedHealthPermissionsUseCase,
                getContributorAppInfoUseCase,
                queryRecentAccessLogsUseCase,
                appInfoReader,
                Dispatchers.Main)
    }

    @After
    fun tearDown() {
        loadGrantedHealthPermissionsUseCase.reset()
        getContributorAppInfoUseCase.reset()
        queryRecentAccessLogsUseCase.reset()
    }

    @Test
    fun appsWithHealthPermissions_correctlyCategorisedAsAllowedOrDenied() = runTest {
        // appsWithHealthPermissions
        whenever(healthPermissionReader.getAppsWithHealthPermissions())
            .thenReturn(listOf(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2))
        loadGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf("PERM_1", "PERM_2"))
        loadGrantedHealthPermissionsUseCase.updateData(TEST_APP_PACKAGE_NAME_2, listOf())

        // appsWithData
        getContributorAppInfoUseCase.setAppInfo(emptyMap())

        // recentAccess
        queryRecentAccessLogsUseCase.recentAccessMap(emptyMap())

        // old permission apps
        whenever(healthPermissionReader.getAppsWithOldHealthPermissions()).thenReturn(listOf())

        val connectedAppsList = loadHealthPermissionApps.invoke()
        val testAppMetadata = appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME)
        val testApp2Metadata = appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME_2)
        assertThat(connectedAppsList)
            .containsExactlyElementsIn(
                listOf(
                    ConnectedAppMetadata(
                        testAppMetadata, status = ConnectedAppStatus.ALLOWED, null),
                    ConnectedAppMetadata(
                        testApp2Metadata, status = ConnectedAppStatus.DENIED, null)))
    }

    @Test
    fun appsWithData_butNoHealthPermissions_correctlyCategorisedAsInactive() = runTest {
        // appsWithHealthPermissions
        whenever(healthPermissionReader.getAppsWithHealthPermissions())
            .thenReturn(listOf(TEST_APP_PACKAGE_NAME))
        loadGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf("PERM_1", "PERM_2"))
        val testAppMetadata = appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME)
        val testApp2Metadata = appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME_2)

        // appsWithData
        getContributorAppInfoUseCase.setAppInfo(mapOf(TEST_APP_PACKAGE_NAME_2 to testApp2Metadata))

        // recentAccess
        queryRecentAccessLogsUseCase.recentAccessMap(emptyMap())

        // old permission apps
        whenever(healthPermissionReader.getAppsWithOldHealthPermissions()).thenReturn(listOf())

        val connectedAppsList = loadHealthPermissionApps.invoke()
        assertThat(connectedAppsList)
            .containsExactlyElementsIn(
                listOf(
                    ConnectedAppMetadata(
                        testAppMetadata, status = ConnectedAppStatus.ALLOWED, null),
                    ConnectedAppMetadata(
                        testApp2Metadata, status = ConnectedAppStatus.INACTIVE, null)))
    }

    @Test
    fun appsWithOldHealthPermissions_withoutNewPermissions_correctlyCategorisedAsNeedsUpdate() =
        runTest {
            // appsWithHealthPermissions
            whenever(healthPermissionReader.getAppsWithHealthPermissions())
                .thenReturn(listOf(TEST_APP_PACKAGE_NAME))
            loadGrantedHealthPermissionsUseCase.updateData(TEST_APP_PACKAGE_NAME, listOf())
            val testAppMetadata = appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME)
            val oldTestAppMetadata =
                appInfoReader.getAppMetadata(OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME)

            // appsWithData
            getContributorAppInfoUseCase.setAppInfo(emptyMap())

            // recentAccess
            queryRecentAccessLogsUseCase.recentAccessMap(emptyMap())

            // old permission apps
            whenever(healthPermissionReader.getAppsWithOldHealthPermissions())
                .thenReturn(listOf(OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME))

            val connectedAppsList = loadHealthPermissionApps.invoke()
            assertThat(connectedAppsList)
                .containsExactlyElementsIn(
                    listOf(
                        ConnectedAppMetadata(
                            testAppMetadata, status = ConnectedAppStatus.DENIED, null),
                        ConnectedAppMetadata(
                            oldTestAppMetadata, status = ConnectedAppStatus.NEEDS_UPDATE, null)))
        }

    @Test
    fun appsWithOldHealthPermissions_andNewPermissions_correctlyCategorisedAsAllowed() = runTest {
        // appsWithHealthPermissions
        whenever(healthPermissionReader.getAppsWithHealthPermissions())
            .thenReturn(listOf(TEST_APP_PACKAGE_NAME, OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME))
        loadGrantedHealthPermissionsUseCase.updateData(TEST_APP_PACKAGE_NAME, listOf("PERM_1"))
        loadGrantedHealthPermissionsUseCase.updateData(
            OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME, listOf("PERM_1"))
        val testAppMetadata = appInfoReader.getAppMetadata(TEST_APP_PACKAGE_NAME)
        val oldTestAppMetadata = appInfoReader.getAppMetadata(OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME)

        // appsWithData
        getContributorAppInfoUseCase.setAppInfo(emptyMap())

        // recentAccess
        queryRecentAccessLogsUseCase.recentAccessMap(emptyMap())

        // old permission apps
        whenever(healthPermissionReader.getAppsWithOldHealthPermissions())
            .thenReturn(listOf(OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME))

        val connectedAppsList = loadHealthPermissionApps.invoke()
        assertThat(connectedAppsList)
            .containsExactlyElementsIn(
                listOf(
                    ConnectedAppMetadata(
                        testAppMetadata, status = ConnectedAppStatus.ALLOWED, null),
                    ConnectedAppMetadata(
                        oldTestAppMetadata, status = ConnectedAppStatus.ALLOWED, null)))
    }
}
