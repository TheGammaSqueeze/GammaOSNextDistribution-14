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

package com.android.car.carlauncher.datasources

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherActivitiesDataSourceImplTest {

    private val scope = TestScope()
    private val bgDispatcher =
        StandardTestDispatcher(scope.testScheduler, name = "Background dispatcher")

    private val launcherActivities: List<LauncherActivityInfo> = listOf(mock(), mock())
    private var broadcastReceiverCallback: BroadcastReceiver? = null
    private val registerReceiverFun: (BroadcastReceiver, IntentFilter) -> Unit =
        { broadcastReceiver, _ ->
            broadcastReceiverCallback = broadcastReceiver
        }
    private val unregisterReceiverFun: (BroadcastReceiver) -> Unit = mock()
    private val myUserHandle: UserHandle = mock()
    private val launcherApps: LauncherApps = mock {
        on { getActivityList(null, myUserHandle) } doReturn launcherActivities
    }
    private val dataSource: LauncherActivitiesDataSource = LauncherActivitiesDataSourceImpl(
        launcherApps,
        registerReceiverFun,
        unregisterReceiverFun,
        myUserHandle,
        bgDispatcher
    )

    @Test
    fun testGetAllLauncherActivities() = scope.runTest {
        val listOfApps = dataSource.getAllLauncherActivities()

        assertEquals(listOfApps.size, 2)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOnPackagesChanged_broadcastReceived_shouldUpdateFlow() = scope.runTest {
        // reset the broadcastReceiverCallback to null
        broadcastReceiverCallback = null
        val flows = mutableListOf<String>()

        launch(StandardTestDispatcher(testScheduler)) {
            dataSource.getOnPackagesChanged().toList(flows)
        }
        advanceUntilIdle()
        // Make a fake change in packages broadcast event.
        val uri1 =
            mock<Uri> { on { schemeSpecificPart } doReturn BROADCAST_EXPECTED_PACKAGE_NAME_1 }
        val intent1: Intent = mock {
            on { data } doReturn uri1
        }
        broadcastReceiverCallback?.onReceive(mock(), intent1)
        advanceUntilIdle()
        // Make another fake broadcast event with different package name
        val uri2 =
            mock<Uri> { on { schemeSpecificPart } doReturn BROADCAST_EXPECTED_PACKAGE_NAME_2 }
        val intent2: Intent = mock {
            on { data } doReturn uri2
        }
        broadcastReceiverCallback?.onReceive(mock(), intent2)
        advanceUntilIdle()
        coroutineContext.cancelChildren()

        // BroadcastReceiver must been set after the producer call is trigger.
        assertNotNull(broadcastReceiverCallback)
        // Producer block sends an empty package immediately to the collector.
        assertEquals(flows[0], "")
        assertEquals(flows[1], BROADCAST_EXPECTED_PACKAGE_NAME_1)
        assertEquals(flows[2], BROADCAST_EXPECTED_PACKAGE_NAME_2)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOnPackagesChanged_scopeClosed_shouldCleanup() = scope.runTest {
        // reset the broadcastReceiverCallback to null
        broadcastReceiverCallback = null

        launch(StandardTestDispatcher(testScheduler)) {
            dataSource.getOnPackagesChanged().collect()
        }
        advanceUntilIdle()
        coroutineContext.cancelChildren()
        advanceUntilIdle()

        // close all child coroutines, this should close the scope.
        assertNotNull(broadcastReceiverCallback)
        broadcastReceiverCallback?.let {
            verify(unregisterReceiverFun).invoke(it)
        }
    }

    companion object {
        const val BROADCAST_EXPECTED_PACKAGE_NAME_1 = "com.test.example1"
        const val BROADCAST_EXPECTED_PACKAGE_NAME_2 = "com.test.example2"
    }
}
