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

package com.android.car.docklib.task

import android.app.ActivityManager.RunningTaskInfo
import android.content.ComponentName
import android.content.Intent
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.DockInterface
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DockTaskStackChangeListenerTest {
    private val dockInterfaceMock = mock<DockInterface> {}
    private val runningTaskInfoMock = mock<RunningTaskInfo> {}
    private val intentMock = mock<Intent> {}

    @Test
    fun onTaskMovedToFront_taskNotForCurrentUser_appLaunchedNotCalled() {
        val currentUserId = 10
        val taskUserId = 11
        val taskComponentName = ComponentName("testPkgName", "testClassName")
        runningTaskInfoMock.userId = taskUserId
        runningTaskInfoMock.displayId = Display.DEFAULT_DISPLAY
        runningTaskInfoMock.baseActivity = taskComponentName

        val dockTaskStackChangeListener =
                DockTaskStackChangeListener(currentUserId, dockInterfaceMock)
        dockTaskStackChangeListener.onTaskMovedToFront(runningTaskInfoMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onTaskMovedToFront_taskNotForDefaultDisplay_appLaunchedNotCalled() {
        val currentUserId = 10
        val nonDefaultDisplay = Display.DEFAULT_DISPLAY + 2
        val taskComponentName = ComponentName("testPkgName", "testClassName")
        runningTaskInfoMock.userId = currentUserId
        runningTaskInfoMock.displayId = nonDefaultDisplay
        runningTaskInfoMock.baseActivity = taskComponentName

        val dockTaskStackChangeListener =
                DockTaskStackChangeListener(currentUserId, dockInterfaceMock)
        dockTaskStackChangeListener.onTaskMovedToFront(runningTaskInfoMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onTaskMovedToFront_taskMissingBaseActivityAndBaseIntentComponent_appLaunchedNotCalled() {
        val currentUserId = 10
        runningTaskInfoMock.userId = currentUserId
        runningTaskInfoMock.displayId = Display.DEFAULT_DISPLAY
        runningTaskInfoMock.baseActivity = null
        whenever(intentMock.component).thenReturn(null)
        runningTaskInfoMock.baseIntent = intentMock

        val dockTaskStackChangeListener =
                DockTaskStackChangeListener(currentUserId, dockInterfaceMock)
        dockTaskStackChangeListener.onTaskMovedToFront(runningTaskInfoMock)

        verifyNoMoreInteractions(dockInterfaceMock)
    }

    @Test
    fun onTaskMovedToFront_appLaunchedCalled() {
        val currentUserId = 10
        val taskComponentName = ComponentName("testPkgName", "testClassName")
        runningTaskInfoMock.userId = currentUserId
        runningTaskInfoMock.displayId = Display.DEFAULT_DISPLAY
        runningTaskInfoMock.baseActivity = taskComponentName

        val dockTaskStackChangeListener =
                DockTaskStackChangeListener(currentUserId, dockInterfaceMock)
        dockTaskStackChangeListener.onTaskMovedToFront(runningTaskInfoMock)

        verify(dockInterfaceMock).appLaunched(eq(taskComponentName))
    }
}
