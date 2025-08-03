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

import com.android.healthconnect.controller.permissions.api.RevokeAllHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel
import com.android.healthconnect.controller.permissions.connectedapps.ILoadHealthPermissionApps
import com.android.healthconnect.controller.permissions.connectedapps.searchapps.SearchHealthPermissionApps
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.OLD_TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeHealthPermissionAppsUseCase
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class ConnectedAppsViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    private val loadHealthPermissionApps: ILoadHealthPermissionApps =
        FakeHealthPermissionAppsUseCase()
    private val revokeAllHealthPermissionsUseCase: RevokeAllHealthPermissionsUseCase = mock()
    private lateinit var searchPermissionApps: SearchHealthPermissionApps
    private lateinit var viewModel: ConnectedAppsViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        searchPermissionApps = SearchHealthPermissionApps(Dispatchers.Main)
        viewModel =
            ConnectedAppsViewModel(
                loadHealthPermissionApps,
                searchPermissionApps,
                revokeAllHealthPermissionsUseCase,
                Dispatchers.Main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun setAlertDialogStatus_updatesDialogStatus() = runTest {
        val testObserver = TestObserver<Boolean>()
        viewModel.alertDialogActive.observeForever(testObserver)
        viewModel.setAlertDialogStatus(true)
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        assertThat(actual).isTrue()
    }

    @Test
    fun loadConnectedApps_loadsConnectedApps() = runTest {
        (loadHealthPermissionApps as FakeHealthPermissionAppsUseCase).updateList(
            listOf(
                ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                ConnectedAppMetadata(OLD_TEST_APP, status = ConnectedAppStatus.NEEDS_UPDATE)))
        val testObserver = TestObserver<List<ConnectedAppMetadata>>()
        viewModel.connectedApps.observeForever(testObserver)
        viewModel.loadConnectedApps()
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        assertThat(actual)
            .containsExactlyElementsIn(
                listOf(
                    ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                    ConnectedAppMetadata(OLD_TEST_APP, status = ConnectedAppStatus.NEEDS_UPDATE)))
    }

    @Test
    fun searchConnectedApps_noMatchingApp_returnsEmptyList() = runTest {
        (loadHealthPermissionApps as FakeHealthPermissionAppsUseCase).updateList(
            listOf(
                ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                ConnectedAppMetadata(OLD_TEST_APP, status = ConnectedAppStatus.NEEDS_UPDATE)))
        val testObserver = TestObserver<List<ConnectedAppMetadata>>()
        viewModel.connectedApps.observeForever(testObserver)
        viewModel.searchConnectedApps("Some app")
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        assertThat(actual).isEmpty()
    }

    @Test
    fun searchConnectedApps_matchingApp_returnsApp() = runTest {
        (loadHealthPermissionApps as FakeHealthPermissionAppsUseCase).updateList(
            listOf(
                ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                ConnectedAppMetadata(OLD_TEST_APP, status = ConnectedAppStatus.NEEDS_UPDATE)))
        val testObserver = TestObserver<List<ConnectedAppMetadata>>()
        viewModel.connectedApps.observeForever(testObserver)
        viewModel.searchConnectedApps("Connect")
        advanceUntilIdle()

        val actual = testObserver.getLastValue()
        assertThat(actual)
            .containsExactlyElementsIn(
                listOf(
                    ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                ))
    }

    @Test
    fun disconnectAllApps_returnsTrueWhenSuccess() = runTest {
        (loadHealthPermissionApps as FakeHealthPermissionAppsUseCase).updateList(
            listOf(
                ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED),
                ConnectedAppMetadata(TEST_APP_2, status = ConnectedAppStatus.DENIED),
                ConnectedAppMetadata(OLD_TEST_APP, status = ConnectedAppStatus.NEEDS_UPDATE)))

        assertThat(
                viewModel.disconnectAllApps(
                    listOf(ConnectedAppMetadata(TEST_APP, status = ConnectedAppStatus.ALLOWED))))
            .isTrue()
    }
}
