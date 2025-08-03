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

package com.android.car.docklib.task

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.view.Display
import com.android.car.docklib.media.MediaUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TaskUtilsTest {
    private val runningTaskInfoMock = mock<ActivityManager.RunningTaskInfo> {}
    private val intentMock = mock<Intent> {}
    private val uriMock = mock<Uri> {}

    @Test
    fun getComponentName_taskMissingBaseActivityAndBaseIntentComponent_returnsNull() {
        val currentUserId = 10
        runningTaskInfoMock.userId = currentUserId
        runningTaskInfoMock.displayId = Display.DEFAULT_DISPLAY
        runningTaskInfoMock.baseActivity = null
        whenever(intentMock.component).thenReturn(null)
        runningTaskInfoMock.baseIntent = intentMock

        val ret = TaskUtils.getComponentName(runningTaskInfoMock)

        assertThat(ret).isNull()
    }

    @Test
    fun getComponentName_returnsComponent() {
        val currentUserId = 10
        val taskComponentName = ComponentName("testPkgName", "testClassName")
        runningTaskInfoMock.userId = currentUserId
        runningTaskInfoMock.displayId = Display.DEFAULT_DISPLAY
        runningTaskInfoMock.baseActivity = taskComponentName

        val ret = TaskUtils.getComponentName(runningTaskInfoMock)

        assertThat(ret).isEqualTo(taskComponentName)
    }

    @Test
    fun getComponentName_mediaComponent_returnsMediaComponent() {
        val mediaComponentName = ComponentName("testPkg", "testClass")
        val currentUserId = 10
        runningTaskInfoMock.userId = currentUserId
        runningTaskInfoMock.displayId = Display.DEFAULT_DISPLAY
        whenever(intentMock.component).doReturn(MediaUtils.CAR_MEDIA_ACTIVITY)
        whenever(intentMock.data).doReturn(uriMock)
        whenever(uriMock.scheme).doReturn(MediaUtils.CAR_MEDIA_DATA_SCHEME)
        whenever(uriMock.schemeSpecificPart).doReturn("/" + mediaComponentName.flattenToString())
        runningTaskInfoMock.baseIntent = intentMock

        val ret = TaskUtils.getComponentName(runningTaskInfoMock)

        assertThat(ret).isEqualTo(mediaComponentName)
    }
}
