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
import android.content.ComponentName
import android.content.ContentResolver
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import com.android.car.carlauncher.datasources.restricted.DisabledAppsDataSourceImpl.Companion.PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR
import junit.framework.TestCase.assertEquals
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class DisabledAppsDataSourceImplTest {

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
    fun testGetDisabledApps_shouldReturnMatchedDisabledApps() = scope.runTest {
        Settings.Secure.putString(
            contentResolver,
            CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
            EXPECTED_MATCH_DISABLED_PACKAGE_NAME_1 +
                    PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR +
                    EXPECTED_NO_MATCH_DISABLED_PACKAGE_NAME_3
        )
        val disabledAppsDataSource = DisabledAppsDataSourceImpl(
            contentResolver,
            packageManager,
            bgDispatcher
        )
        val flows = mutableListOf<List<ResolveInfo>>()

        launch(StandardTestDispatcher(testScheduler)) {
            disabledAppsDataSource.getDisabledApps().toList(flows)
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(1, flows.size)
        val actualMatchedPackages =
            flows[0].map { it.activityInfo.packageName }
        assertEquals(listOf(EXPECTED_MATCH_DISABLED_PACKAGE_NAME_1), actualMatchedPackages)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetDisabledApps_disabledAppsUpdated_shouldUpdateFlow() = scope.runTest {
        Settings.Secure.putString(
            contentResolver,
            CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
            EXPECTED_MATCH_DISABLED_PACKAGE_NAME_1 +
                    PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR +
                    EXPECTED_NO_MATCH_DISABLED_PACKAGE_NAME_3
        )
        val disabledAppsDataSource = DisabledAppsDataSourceImpl(
            contentResolver,
            packageManager,
            bgDispatcher
        )
        val flows = mutableListOf<List<ResolveInfo>>()

        launch(StandardTestDispatcher(testScheduler)) {
            disabledAppsDataSource.getDisabledApps().toList(flows)
        }

        advanceUntilIdle()
        // Changes in Disabled app while the client is still subscribed to the changes.
        Settings.Secure.putString(
            contentResolver,
            CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
            EXPECTED_MATCH_DISABLED_PACKAGE_NAME_1 +
                    PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR +
                    EXPECTED_NO_MATCH_DISABLED_PACKAGE_NAME_3 +
                    PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR +
                    EXPECTED_MATCH_DISABLED_PACKAGE_NAME_2
        )
        advanceUntilIdle()
        ShadowLooper.runUiThreadTasks()
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        assertEquals(2, flows.size)
        val actualUpdatedMatchedPackages =
            flows[1].map { it.activityInfo.packageName }
        assertEquals(
            listOf(
                EXPECTED_MATCH_DISABLED_PACKAGE_NAME_1,
                EXPECTED_MATCH_DISABLED_PACKAGE_NAME_2
            ),
            actualUpdatedMatchedPackages
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetDisabledApps_scopeClosed_shouldCleanUp() = scope.runTest {
        val contentResolverSpy = spy(contentResolver)
        val disabledAppsDataSource = DisabledAppsDataSourceImpl(
            contentResolverSpy,
            packageManager,
            bgDispatcher
        )

        launch(StandardTestDispatcher(testScheduler)) {
            disabledAppsDataSource.getDisabledApps().collect()
        }

        advanceUntilIdle()
        coroutineContext.cancelChildren()
        advanceUntilIdle()

        verify(contentResolverSpy).unregisterContentObserver(any())
    }

    companion object {
        // packageNames listed in Settings.Secure for disabled apps
        const val EXPECTED_MATCH_DISABLED_PACKAGE_NAME_1 = "com.test.example1"
        const val EXPECTED_MATCH_DISABLED_PACKAGE_NAME_2 = "com.test.example2"
        const val EXPECTED_NO_MATCH_DISABLED_PACKAGE_NAME_3 = "com.test.example3"

        // componentNames available for the packageManager to query
        val INSTALLED_COMPONENT_NAME_1 =
            ComponentName(EXPECTED_MATCH_DISABLED_PACKAGE_NAME_1, "ExampleClass1")
        val INSTALLED_COMPONENT_NAME_2 = ComponentName("com.test.example4", "ExampleClass2")
        val INSTALLED_COMPONENT_NAME_3 =
            ComponentName(EXPECTED_MATCH_DISABLED_PACKAGE_NAME_2, "ExampleClass3")
    }
}
