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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.BasePermissionEventStorage
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class BasePermissionEventStorageTest {

    companion object {
        private val application = Mockito.mock(PermissionControllerApplication::class.java)

        private val TEST_MAX_DATA_AGE = TimeUnit.DAYS.toMillis(7)
        private const val TEST_FILE_NAME = "test_file.xml"
        private const val MAP_PACKAGE_NAME = "package.test.map"
    }

    private val jan12020 = Date(2020, 0, 1).time
    private val jan22020 = Date(2020, 0, 2).time

    private val musicEvent = TestPermissionEvent("package.test.music", jan12020)
    private val mapEvent = TestPermissionEvent(MAP_PACKAGE_NAME, jan12020, /* id */ 1)
    private val mapEventSameKey = TestPermissionEvent(MAP_PACKAGE_NAME, jan22020, /* id */ 1)
    private val mapEventDifferentKey = TestPermissionEvent(MAP_PACKAGE_NAME, jan12020, /* id */ 2)
    private val parkingEvent = TestPermissionEvent("package.test.parking", jan22020)
    private val podcastEvent = TestPermissionEvent("package.test.podcast", jan22020)

    @Mock lateinit var jobScheduler: JobScheduler

    @Mock lateinit var existingJob: JobInfo

    private lateinit var context: Context
    private lateinit var storage: BasePermissionEventStorage<TestPermissionEvent>
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
        `when`(PermissionControllerApplication.get()).thenReturn(application)
        context = ApplicationProvider.getApplicationContext()
        filesDir = context.cacheDir
        `when`(application.filesDir).thenReturn(filesDir)
        `when`(jobScheduler.schedule(any())).thenReturn(JobScheduler.RESULT_SUCCESS)
    }

    private fun init() {
        storage = TestPermissionEventStorage(context, jobScheduler)
    }

    @After
    fun cleanup() = runBlocking {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()

        storage.clearEvents()
    }

    @Test
    fun loadEvents_noData_returnsEmptyList() {
        init()
        runBlocking { assertThat(storage.loadEvents()).isEmpty() }
    }

    @Test
    fun storeEvent_singleEvent_writeSuccessAndReturnOnLoad() {
        init()
        runBlocking {
            assertThat(storage.storeEvent(mapEvent)).isTrue()
            assertThat(storage.loadEvents()).containsExactly(mapEvent)
        }
    }

    @Test
    fun storeEvent_multipleEVents_returnedOrderedByMostRecentlyAdded() {
        init()
        runBlocking {
            storage.storeEvent(mapEvent)
            storage.storeEvent(musicEvent)
            storage.storeEvent(parkingEvent)
            storage.storeEvent(podcastEvent)
            assertThat(storage.loadEvents())
                .containsExactly(musicEvent, mapEvent, parkingEvent, podcastEvent)
        }
    }

    @Test
    fun storeEvent_uniqueForPrimaryKey() {
        init()
        runBlocking {
            storage.storeEvent(mapEvent)
            storage.storeEvent(mapEventSameKey)
            assertThat(storage.loadEvents()).containsExactly(mapEventSameKey)
        }
    }

    @Test
    fun storeEvent_ignoresExactDuplicates() {
        init()
        runBlocking {
            storage.storeEvent(mapEvent)
            storage.storeEvent(mapEvent)
            assertThat(storage.loadEvents()).containsExactly(mapEvent)
        }
    }

    @Test
    fun clearEvents_clearsExistingData() {
        init()
        runBlocking {
            storage.storeEvent(mapEvent)
            storage.clearEvents()
            assertThat(storage.loadEvents()).isEmpty()
        }
    }

    @Test
    fun removeEventsForPackage_removesEvents() {
        init()
        runBlocking {
            storage.storeEvent(mapEvent)
            storage.storeEvent(musicEvent)
            storage.storeEvent(mapEventDifferentKey)
            storage.removeEventsForPackage(MAP_PACKAGE_NAME)
            assertThat(storage.loadEvents()).containsExactly(musicEvent)
        }
    }

    @Test
    fun removeOldData_removesOnlyOldData() {
        init()
        val todayEvent = parkingEvent.copy(eventTime = System.currentTimeMillis())
        val sixDaysAgoEvent =
            podcastEvent.copy(eventTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6))
        val eightDaysAgoEvent =
            parkingEvent.copy(eventTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))
        runBlocking {
            storage.storeEvent(eightDaysAgoEvent)
            storage.storeEvent(sixDaysAgoEvent)
            storage.storeEvent(todayEvent)
            storage.removeOldData()

            assertThat(storage.loadEvents()).containsExactly(todayEvent, sixDaysAgoEvent)
        }
    }

    @Test
    fun updateEventsBySystemTimeDelta_oneDayForward_shiftsData() {
        init()
        runBlocking {
            storage.storeEvent(musicEvent)
            storage.updateEventsBySystemTimeDelta(TimeUnit.DAYS.toMillis(1))

            assertThat(storage.loadEvents())
                .containsExactly(
                    musicEvent.copy(eventTime = musicEvent.eventTime + TimeUnit.DAYS.toMillis(1))
                )
        }
    }

    @Test
    fun updateEventsBySystemTimeDelta_oneDayBackward_shiftsData() {
        init()
        runBlocking {
            storage.storeEvent(musicEvent)
            storage.updateEventsBySystemTimeDelta(-TimeUnit.DAYS.toMillis(1))

            assertThat(storage.loadEvents())
                .containsExactly(
                    musicEvent.copy(eventTime = musicEvent.eventTime - TimeUnit.DAYS.toMillis(1))
                )
        }
    }

    private class TestPermissionEventStorage(context: Context, jobScheduler: JobScheduler) :
        BasePermissionEventStorage<TestPermissionEvent>(context, jobScheduler) {
        lateinit var fakeDiskStore: List<TestPermissionEvent>

        override fun serialize(stream: OutputStream, events: List<TestPermissionEvent>) {
            fakeDiskStore = events
        }

        override fun parse(inputStream: InputStream): List<TestPermissionEvent> {
            // Don't bother using the actual input strean and just return the in-memory store
            return fakeDiskStore
        }

        override fun getDatabaseFileName(): String {
            return TEST_FILE_NAME
        }

        override fun getMaxDataAgeMs(): Long {
            return TEST_MAX_DATA_AGE
        }

        override fun hasTheSamePrimaryKey(
            first: TestPermissionEvent,
            second: TestPermissionEvent
        ): Boolean {
            // use package name and id as primary key
            return first.packageName == second.packageName && first.id == second.id
        }

        override fun TestPermissionEvent.copyWithTimeDelta(timeDelta: Long): TestPermissionEvent {
            return this.copy(eventTime = this.eventTime + timeDelta)
        }
    }
}
