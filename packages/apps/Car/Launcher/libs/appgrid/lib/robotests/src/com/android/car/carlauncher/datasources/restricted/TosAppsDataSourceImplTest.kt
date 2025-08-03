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

package com.android.car.carlauncher.datasources.restricted

import android.car.settings.CarSettings
import android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED
import android.content.ComponentName
import android.content.ContentResolver
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.ContentObserver
import android.provider.Settings
import com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl.Companion.TOS_ACCEPTED
import com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl.Companion.TOS_DISABLED_APPS_SEPARATOR
import com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl.Companion.TOS_NOT_ACCEPTED
import com.android.car.carlauncher.datasources.restricted.TosDataSourceImpl.Companion.TOS_UNINITIALIZED
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class TosAppsDataSourceImplTest {

    private val scope = TestScope()
    private val bgDispatcher =
        StandardTestDispatcher(scope.testScheduler, name = "Background dispatcher")

    private val packageManager: PackageManager = mock {
        on {
            queryIntentActivities(
                any(), any<PackageManager.ResolveInfoFlags>()
            )
        } doReturn listOf(
            getResolveInfo(INSTALLED_COMPONENT_NAME_1),
            getResolveInfo(INSTALLED_COMPONENT_NAME_2),
            getResolveInfo(INSTALLED_COMPONENT_NAME_3),
        )
    }

    private val contentResolver: ContentResolver =
        RuntimeEnvironment.getApplication().contentResolver

    /**
     * Returns a mocked ResolveInfo
     * @param componentName packageName + className of the mocked [ActivityInfo]
     */
    private fun getResolveInfo(componentName: ComponentName): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = componentName.packageName
                name = componentName.className
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetTosState_tosAccepted_shouldReturnBlockAppsAsFalse() = scope.runTest {
        Settings.Secure.putString(
            contentResolver,
            KEY_USER_TOS_ACCEPTED,
            TOS_ACCEPTED
        )
        val tosDataSource = TosDataSourceImpl(contentResolver, packageManager, bgDispatcher)
        val flows = mutableListOf<TosState>()

        launch(StandardTestDispatcher(testScheduler)) {
            tosDataSource.getTosState().toList(flows)
        }
        advanceUntilIdle()
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()

        assertEquals(1, flows.size)
        assertEquals(false, flows[0].shouldBlockTosApps)
        assertTrue(flows[0].restrictedApps.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetTosState_tosUnInitialized_shouldReturnBlockAppsAsFalse() = scope.runTest {
        Settings.Secure.putString(
            contentResolver,
            KEY_USER_TOS_ACCEPTED,
            TOS_UNINITIALIZED
        )
        val tosDataSource = TosDataSourceImpl(contentResolver, packageManager, bgDispatcher)
        val flows = mutableListOf<TosState>()

        launch(StandardTestDispatcher(testScheduler)) {
            tosDataSource.getTosState().toList(flows)
        }
        advanceUntilIdle()
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(1, flows.size)
        assertEquals(false, flows[0].shouldBlockTosApps)
        assertTrue(flows[0].restrictedApps.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetTosState_tosNotAccepted_shouldReturnBlockAppsAsTrue() = scope.runTest {
        Settings.Secure.putString(
            contentResolver,
            KEY_USER_TOS_ACCEPTED,
            TOS_NOT_ACCEPTED
        )
        Settings.Secure.putString(
            contentResolver,
            CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS,
            "$EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_1$TOS_DISABLED_APPS_SEPARATOR" +
                    "$EXPECTED_NO_MATCH_TOS_DISABLED_PACKAGE_NAME_3$TOS_DISABLED_APPS_SEPARATOR" +
                    EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_2
        )
        val tosDataSource = TosDataSourceImpl(contentResolver, packageManager, bgDispatcher)
        val flows = mutableListOf<TosState>()

        launch(StandardTestDispatcher(testScheduler)) {
            tosDataSource.getTosState().toList(flows)
        }
        advanceUntilIdle()
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(1, flows.size)
        assertEquals(true, flows[0].shouldBlockTosApps)
        val actualTosDisabledApps = flows[0].restrictedApps.map { it.activityInfo.packageName }
        assertEquals(
            listOf(
                EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_1,
                EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_2
            ),
            actualTosDisabledApps
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetTosState_tosChangedToAccepted_shouldUnregisterObserver() = scope.runTest {
        val contentResolverSpy = spy(contentResolver)
        Settings.Secure.putString(
            contentResolverSpy,
            KEY_USER_TOS_ACCEPTED,
            TOS_NOT_ACCEPTED
        )
        Settings.Secure.putString(
            contentResolverSpy,
            CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS,
            "$EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_1$TOS_DISABLED_APPS_SEPARATOR" +
                    "$EXPECTED_NO_MATCH_TOS_DISABLED_PACKAGE_NAME_3$TOS_DISABLED_APPS_SEPARATOR" +
                    EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_2
        )
        val tosDataSource = TosDataSourceImpl(contentResolverSpy, packageManager, bgDispatcher)
        val flows = mutableListOf<TosState>()

        launch(StandardTestDispatcher(testScheduler)) {
            tosDataSource.getTosState().toList(flows)
        }
        advanceUntilIdle()
        // Tos state changed to Accepted
        Settings.Secure.putString(
            contentResolverSpy,
            KEY_USER_TOS_ACCEPTED,
            TOS_ACCEPTED
        )
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(2, flows.size)
        // Initially shouldBlockTosApps is expected to be true.
        assertEquals(true, flows[0].shouldBlockTosApps)
        // After change in TOS state to Accepted shouldBlockTosApps is expected to be false.
        assertEquals(false, flows[1].shouldBlockTosApps)
        val actualChangedTosDisabledApps = flows[1].restrictedApps.map {
            it.activityInfo.packageName
        }
        assertTrue(actualChangedTosDisabledApps.isEmpty())
        verify(contentResolverSpy).unregisterContentObserver(any())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetTosState_tosChangedToNotAccepted_shouldNotUnregisterObserver() = scope.runTest {
        val contentResolverSpy = spy(contentResolver)
        Settings.Secure.putString(
            contentResolverSpy,
            KEY_USER_TOS_ACCEPTED,
            TOS_UNINITIALIZED
        )
        val tosDataSource = TosDataSourceImpl(contentResolverSpy, packageManager, bgDispatcher)
        val flows = mutableListOf<TosState>()

        launch(StandardTestDispatcher(testScheduler)) {
            tosDataSource.getTosState().toList(flows)
        }
        advanceUntilIdle()
        // Tos state changed to Not_Accepted
        Settings.Secure.putString(
            contentResolverSpy,
            KEY_USER_TOS_ACCEPTED,
            TOS_NOT_ACCEPTED
        )
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        // Tos state updates the list of blocked apps.
        Settings.Secure.putString(
            contentResolverSpy,
            CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS,
            "$EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_1$TOS_DISABLED_APPS_SEPARATOR" +
                    "$EXPECTED_NO_MATCH_TOS_DISABLED_PACKAGE_NAME_3$TOS_DISABLED_APPS_SEPARATOR" +
                    EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_2
        )
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        // Three updates: 1-UnInitialized, 2-NotAccepted, 3-BlockedAppsChanged.
        assertEquals(3, flows.size)
        // Initially shouldBlockTosApps is expected to be false in NotInitialized state.
        assertEquals(false, flows[0].shouldBlockTosApps)
        assertTrue(flows[0].restrictedApps.isEmpty())
        // After change in TOS state to NotAccepted shouldBlockTosApps is expected to be true.
        assertEquals(true, flows[1].shouldBlockTosApps)
        // Since the list of blocked apps is also updated we will received another update.
        assertEquals(true, flows[2].shouldBlockTosApps)
        val actualChangedTosDisabledApps = flows[2].restrictedApps.map {
            it.activityInfo.packageName
        }
        // Updates the list of blocked apps.
        assertEquals(
            listOf(
                EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_1,
                EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_2
            ),
            actualChangedTosDisabledApps
        )
        // We should not unregister the contentObservers.
        verify(contentResolverSpy, never()).unregisterContentObserver(any())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetTosState_scopeClosed_shouldCleanUp() = scope.runTest {
        val contentResolverSpy = spy(contentResolver)
        Settings.Secure.putString(
            contentResolverSpy,
            KEY_USER_TOS_ACCEPTED,
            TOS_UNINITIALIZED
        )
        val tosDataSource = TosDataSourceImpl(contentResolverSpy, packageManager, bgDispatcher)

        launch(StandardTestDispatcher(testScheduler)) {
            tosDataSource.getTosState().collect()
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()
        advanceUntilIdle()

        val observerCapture = ArgumentCaptor.forClass(ContentObserver::class.java)
        // registers same observer for two uri.
        verify(contentResolverSpy, times(2))
            .registerContentObserver(any(), any(), observerCapture.capture())
        // After scope it closed it unregisters the content observer.
        verify(contentResolverSpy).unregisterContentObserver(observerCapture.value)
    }

    companion object {
        // packageNames listed in Settings.Secure for tos disabled apps
        private const val EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_1 = "com.test.example1"
        private const val EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_2 = "com.test.example2"
        private const val EXPECTED_NO_MATCH_TOS_DISABLED_PACKAGE_NAME_3 = "com.test.example3"

        // componentNames available for the packageManager to query
        private val INSTALLED_COMPONENT_NAME_1 =
            ComponentName(EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_1, "ExampleClass1")
        private val INSTALLED_COMPONENT_NAME_2 =
            ComponentName("com.test.example4", "ExampleClass2")
        private val INSTALLED_COMPONENT_NAME_3 =
            ComponentName(EXPECTED_MATCH_TOS_DISABLED_PACKAGE_NAME_2, "ExampleClass3")
    }
}
