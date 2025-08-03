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

import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.PersistedStoragePackageUninstalledReceiver
import com.android.permissioncontroller.tests.mocking.permission.data.FakeEventStorage
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class PersistedStoragePackageUninstalledReceiverTest {

    companion object {
        val application = Mockito.mock(PermissionControllerApplication::class.java)
    }

    private val musicEvent = TestPermissionEvent("package.test.music", Date(2020, 0, 1).time)

    @Mock lateinit var context: Context

    @Mock lateinit var packageManager: PackageManager

    @Mock lateinit var jobScheduler: JobScheduler

    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File
    private lateinit var permissionEventStorage: FakeEventStorage<TestPermissionEvent>
    private lateinit var receiver: PersistedStoragePackageUninstalledReceiver

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        `when`(PermissionControllerApplication.get()).thenReturn(application)
        val context: Context = ApplicationProvider.getApplicationContext()
        filesDir = context.cacheDir
        `when`(application.filesDir).thenReturn(filesDir)

        permissionEventStorage = spy(FakeEventStorage())
        receiver =
            spy(
                PersistedStoragePackageUninstalledReceiver(
                    listOf(permissionEventStorage),
                    Dispatchers.Main.immediate
                )
            )
    }

    @After
    fun finish() {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun onReceive_unsupportedAction_doesNothing() {
        val intent = Intent(Intent.ACTION_PACKAGES_SUSPENDED)

        receiver.onReceive(context, intent)

        verifyZeroInteractions(permissionEventStorage)
    }

    @Test
    fun onReceive_clearAction_removesEventsForPackage() {
        runBlocking {
            permissionEventStorage.storeEvent(musicEvent)
            assertThat(permissionEventStorage.loadEvents().isNotEmpty())
        }
        val intent = Intent(Intent.ACTION_PACKAGE_DATA_CLEARED)
        intent.data = Uri.parse(musicEvent.packageName)

        receiver.onReceive(context, intent)

        runBlocking { assertThat(permissionEventStorage.loadEvents().isEmpty()) }
    }
}
