/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PermissionChange
import com.android.permissioncontroller.permission.service.PermissionChangeStorageImpl
import com.android.permissioncontroller.permission.utils.Utils
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class PermissionChangeStorageImplTest {
    companion object {
        private val application = Mockito.mock(PermissionControllerApplication::class.java)

        private const val MAP_PACKAGE_NAME = "package.test.map"
        private const val FIVE_HOURS_MS = 5 * 60 * 60 * 1000
    }

    private val jan12020 = Date(2020, 0, 1).time

    private val mapChange = PermissionChange(MAP_PACKAGE_NAME, jan12020)

    @Mock lateinit var jobScheduler: JobScheduler

    private lateinit var context: Context
    private lateinit var storage: PermissionChangeStorageImpl
    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(DeviceConfig::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        Mockito.`when`(PermissionControllerApplication.get()).thenReturn(application)
        context = ApplicationProvider.getApplicationContext()
        filesDir = context.cacheDir
        Mockito.`when`(application.filesDir).thenReturn(filesDir)
        Mockito.`when`(jobScheduler.schedule(Mockito.any())).thenReturn(JobScheduler.RESULT_SUCCESS)

        storage = PermissionChangeStorageImpl(context, jobScheduler)
    }

    @After
    fun cleanup() = runBlocking {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()

        storage.clearEvents()
    }

    @Test
    fun serialize_dataCanBeParsed() {
        val outStream = ByteArrayOutputStream()
        storage.serialize(outStream, listOf(mapChange))

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        assertThat(storage.parse(inStream)).containsExactly(mapChange)
    }

    @Test
    fun serialize_roundsTimeDownToDate() {
        val laterInTheDayGrant = mapChange.copy(eventTime = (mapChange.eventTime + FIVE_HOURS_MS))
        val outStream = ByteArrayOutputStream()
        storage.serialize(outStream, listOf(laterInTheDayGrant))

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        assertThat(storage.parse(inStream)).containsExactly(mapChange)
    }

    @Test
    fun serialize_exactTimeDataCanBeParsed() {
        Mockito.`when`(
                DeviceConfig.getBoolean(
                    ArgumentMatchers.eq(DeviceConfig.NAMESPACE_PERMISSIONS),
                    ArgumentMatchers.eq(Utils.PROPERTY_PERMISSION_CHANGES_STORE_EXACT_TIME),
                    ArgumentMatchers.anyBoolean()
                )
            )
            .thenReturn(true)

        val outStream = ByteArrayOutputStream()
        storage.serialize(outStream, listOf(mapChange))

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        assertThat(storage.parse(inStream)).containsExactly(mapChange)
    }

    @Test
    fun serialize_afterStoresExactTimeChangedToTrue_canBeParsed() {
        val outStream = ByteArrayOutputStream()
        storage.serialize(outStream, listOf(mapChange))

        Mockito.`when`(
                DeviceConfig.getBoolean(
                    ArgumentMatchers.eq(DeviceConfig.NAMESPACE_PERMISSIONS),
                    ArgumentMatchers.eq(Utils.PROPERTY_PERMISSION_CHANGES_STORE_EXACT_TIME),
                    ArgumentMatchers.anyBoolean()
                )
            )
            .thenReturn(true)

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        assertThat(storage.parse(inStream)).containsExactly(mapChange)
    }

    @Test
    fun serialize_afterStoresExactTimeChangedToFalse_roundsTimeDownToDate() {
        Mockito.`when`(
                DeviceConfig.getBoolean(
                    ArgumentMatchers.eq(DeviceConfig.NAMESPACE_PERMISSIONS),
                    ArgumentMatchers.eq(Utils.PROPERTY_PERMISSION_CHANGES_STORE_EXACT_TIME),
                    ArgumentMatchers.anyBoolean()
                )
            )
            .thenReturn(true)

        val laterInTheDayEvent = mapChange.copy(eventTime = (mapChange.eventTime + FIVE_HOURS_MS))
        val outStream = ByteArrayOutputStream()
        storage.serialize(outStream, listOf(laterInTheDayEvent))

        Mockito.`when`(
                DeviceConfig.getBoolean(
                    ArgumentMatchers.eq(DeviceConfig.NAMESPACE_PERMISSIONS),
                    ArgumentMatchers.eq(Utils.PROPERTY_PERMISSION_CHANGES_STORE_EXACT_TIME),
                    ArgumentMatchers.anyBoolean()
                )
            )
            .thenReturn(false)

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        assertThat(storage.parse(inStream)).containsExactly(mapChange)
    }
}
