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

package com.android.permissioncontroller.tests.mocking.permission.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PermissionEvent
import com.android.permissioncontroller.permission.service.PermissionEventStorage
import com.android.permissioncontroller.permission.service.PermissionStorageTimeChangeReceiver
import com.android.permissioncontroller.permission.service.PermissionStorageTimeChangeReceiver.Companion.PREF_KEY_ELAPSED_REALTIME_SNAPSHOT
import com.android.permissioncontroller.permission.service.PermissionStorageTimeChangeReceiver.Companion.PREF_KEY_SYSTEM_TIME_SNAPSHOT
import com.android.permissioncontroller.permission.service.PermissionStorageTimeChangeReceiver.Companion.SNAPSHOT_UNINITIALIZED
import com.android.permissioncontroller.permission.utils.TimeSource
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class PermissionStorageTimeChangeReceiverTest {

    companion object {
        val application = mock(PermissionControllerApplication::class.java)
    }

    @Mock lateinit var context: Context

    @Mock lateinit var sharedPreferences: SharedPreferences

    @Mock lateinit var editor: SharedPreferences.Editor

    @Mock lateinit var packageManager: PackageManager

    @Mock lateinit var permissionEventStorage: PermissionEventStorage<out PermissionEvent>

    private val fakeTimeSource = FakeTimeSource()
    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File
    private lateinit var receiver: PermissionStorageTimeChangeReceiver

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(DeviceConfig::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        `when`(PermissionControllerApplication.get()).thenReturn(application)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        val context: Context = ApplicationProvider.getApplicationContext()
        filesDir = context.cacheDir
        `when`(application.filesDir).thenReturn(filesDir)
        `when`(DeviceConfig.getProperty(eq(DeviceConfig.NAMESPACE_PERMISSIONS), anyString()))
            .thenReturn(null)
        `when`(sharedPreferences.getLong(eq(PREF_KEY_SYSTEM_TIME_SNAPSHOT), anyLong()))
            .thenReturn(SNAPSHOT_UNINITIALIZED)
        `when`(sharedPreferences.getLong(eq(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT), anyLong()))
            .thenReturn(SNAPSHOT_UNINITIALIZED)

        receiver =
            spy(PermissionStorageTimeChangeReceiver(listOf(permissionEventStorage), fakeTimeSource))
    }

    @After
    fun finish() {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun onReceive_bootCompletedReceived_savesSnapshot() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify(editor).putLong(PREF_KEY_SYSTEM_TIME_SNAPSHOT, fakeTimeSource.currentTimeMillis)
        verify(editor).putLong(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT, fakeTimeSource.elapsedRealtime)
    }

    @Test
    fun onReceive_timeSetReceived_beforeBootCompleted_doesNothing() {
        fakeTimeSource.currentTimeMillis =
            fakeTimeSource.currentTimeMillis + TimeUnit.DAYS.toMillis(2)
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        verify(receiver, never()).onTimeChanged(anyLong())
        verifyZeroInteractions(editor)
    }

    @Test
    fun onReceive_unknownIntent_doesNothing() {
        receiver.onReceive(context, Intent(Intent.ACTION_MANAGE_PERMISSIONS))

        verify(receiver, never()).onTimeChanged(anyLong())
        verifyZeroInteractions(editor)
    }

    @Test
    fun onReceive_timeDiffBelowMinimum_doesNothing() {
        mockBootCompletedSnapshot()

        fakeTimeSource.currentTimeMillis =
            fakeTimeSource.currentTimeMillis - TimeUnit.SECONDS.toMillis(30)
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        verify(receiver, never()).onTimeChanged(anyLong())
    }

    @Test
    fun onReceive_timeChangedAboveMinimum_callsOnTimeChanged() {
        mockBootCompletedSnapshot()

        // in 3 days the time is set to one day from now (effectively set back by 2 days)
        fakeTimeSource.currentTimeMillis =
            fakeTimeSource.currentTimeMillis + TimeUnit.DAYS.toMillis(1)
        fakeTimeSource.elapsedRealtime = fakeTimeSource.elapsedRealtime + TimeUnit.DAYS.toMillis(3)
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        verify(receiver).onTimeChanged(-TimeUnit.DAYS.toMillis(2))
    }

    private fun mockBootCompletedSnapshot() {
        `when`(sharedPreferences.getLong(eq(PREF_KEY_SYSTEM_TIME_SNAPSHOT), anyLong()))
            .thenReturn(fakeTimeSource.currentTimeMillis)
        `when`(sharedPreferences.getLong(eq(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT), anyLong()))
            .thenReturn(fakeTimeSource.elapsedRealtime)
    }

    class FakeTimeSource(
        var currentTimeMillis: Long = System.currentTimeMillis(),
        var elapsedRealtime: Long = SystemClock.elapsedRealtime()
    ) : TimeSource {

        override fun currentTimeMillis(): Long {
            return currentTimeMillis
        }

        override fun elapsedRealtime(): Long {
            return elapsedRealtime
        }
    }
}
