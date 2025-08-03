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

package com.android.car.docklib.media

import android.app.ActivityManager.RunningTaskInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.media.MediaUtils.Companion.CAR_MEDIA_DATA_SCHEME
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MediaUtilsTest {
    private val uriMock = mock<Uri> {}
    private val intentMock = mock<Intent> {}
    private val runningTaskInfoMock = mock<RunningTaskInfo> {}
    private val packageManagerMock = mock<PackageManager> {}
    private val intentCaptor = argumentCaptor<Intent>()

    @Before
    fun setup() {
        runningTaskInfoMock.baseIntent = intentMock
    }

    @Test
    fun getMediaComponentName_noData_returnsNull() {
        whenever(intentMock.data).doReturn(null)

        val ret = MediaUtils.getMediaComponentName(runningTaskInfoMock)

        assertThat(ret).isNull()
    }

    @Test
    fun getMediaComponentName_incorrectDataScheme_returnsNull() {
        whenever(intentMock.data).doReturn(uriMock)
        whenever(uriMock.scheme).doReturn("MediaUtilsTestScheme")

        val ret = MediaUtils.getMediaComponentName(runningTaskInfoMock)

        assertThat(ret).isNull()
    }

    @Test
    fun getMediaComponentName_returnsCorrectComponent() {
        val componentName = ComponentName("testPkg", "testClass")
        whenever(intentMock.data).doReturn(uriMock)
        whenever(uriMock.scheme).doReturn(CAR_MEDIA_DATA_SCHEME)
        whenever(uriMock.schemeSpecificPart).doReturn("/" + componentName.flattenToString())

        val ret = MediaUtils.getMediaComponentName(runningTaskInfoMock)

        assertThat(ret).isEqualTo(componentName)
    }

    @Test
    fun fetchMediaServiceComponents_packageNotNull_queriesOnlyForThatPackage() {
        val pkg = "testPkg"

        MediaUtils.fetchMediaServiceComponents(packageManagerMock, pkg)

        verify(packageManagerMock).queryIntentServices(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.firstValue.getPackage()).isEqualTo(pkg)
    }
}
