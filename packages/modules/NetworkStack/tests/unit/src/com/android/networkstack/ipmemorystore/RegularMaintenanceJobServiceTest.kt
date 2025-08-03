/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.networkstack.ipmemorystore

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.Context
import android.net.ipmemorystore.IOnStatusListener
import android.net.ipmemorystore.Status
import android.os.Looper
import android.testing.AndroidTestingRunner
import android.util.Log
import androidx.test.filters.SmallTest
import com.android.networkstack.ipmemorystore.RegularMaintenanceJobService.InterruptMaintenance
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.staticFunctions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TIMEOUT_MS = 2_000L

@RunWith(AndroidTestingRunner::class)
@SmallTest
class RegularMaintenanceJobServiceTest {
    private val mockContext = mock(Context::class.java)
    private val mockJobScheduler = mock(JobScheduler::class.java)
    private val mockIPMS = mock(IpMemoryStoreService::class.java)
    private val jobService = InstrumentedRegularMaintenanceJobService()

    private class InstrumentedRegularMaintenanceJobService : RegularMaintenanceJobService() {
        // Store calls to jobFinished, which takes 2 arguments : params of the job to finish,
        // and whether to reschedule
        val events = ArrayBlockingQueue<Pair<JobParameters, Boolean>>(1 /* capacity */)
        override fun getMainLooper() = mock(Looper::class.java)
        override fun callJobFinished(params: JobParameters, reschedule: Boolean) {
            events.offer(params to reschedule)
        }
    }

    @Before
    fun setUp() {
        doReturn(mockJobScheduler)
                .`when`(mockContext).getSystemService(Context.JOB_SCHEDULER_SERVICE)
        // At this time JobService.onBind ignores its argument but must be called for it to work
        // because JobService uses this method call to initialize some internal structures.
        jobService.onBind(null)
    }

    private fun startMaintenance(): Triple<JobParameters, IOnStatusListener, InterruptMaintenance> {
        val jobId = 12

        RegularMaintenanceJobService.schedule(mockContext, mockIPMS)
        val jobInfoCaptor = ArgumentCaptor.forClass(JobInfo::class.java)
        verify(mockJobScheduler).schedule(jobInfoCaptor.capture())
        val jobInfo = jobInfoCaptor.value
        assertTrue(jobInfo.isRequireCharging)
        assertTrue(jobInfo.isRequireBatteryNotLow)
        assertTrue(jobInfo.isRequireDeviceIdle)
        assertTrue(jobInfo.isPeriodic)
        // AOSP sets the maintenance to once every 24h. It should be infrequent enough to not
        // take up a lot of battery but also not so infrequent that it never gets done
        assertTrue(jobInfo.intervalMillis in TimeUnit.HOURS.toMillis(12)..TimeUnit.DAYS.toMillis(5))

        val params = mock(JobParameters::class.java)
        doReturn(jobId).`when`(params).jobId
        assertTrue(jobService.onStartJob(params))

        val listenerCaptor = ArgumentCaptor.forClass(IOnStatusListener::class.java)
        val imCaptor = ArgumentCaptor.forClass(InterruptMaintenance::class.java)
        verify(mockIPMS).fullMaintenance(listenerCaptor.capture(), imCaptor.capture())
        assertEquals(imCaptor.value.jobId, jobId)
        assertFalse(imCaptor.value.isInterrupted)
        return Triple(params, listenerCaptor.value, imCaptor.value)
    }

    private fun cleanupMaintenance(params: JobParameters) {
        RegularMaintenanceJobService.unschedule(mockContext)
        verify(mockJobScheduler).cancel(anyInt())
        // This test may be compiled against the public SDK which prevents it from using
        // Log#setWtfHandler. In this case there is no way to test that Log.wtf has been
        // called if the onStartJob is called again on the same instance of the service.
        // Only do this if the method is available.
        if (Log::class.staticFunctions.any { it.name == "setWtfHandler" }) {
            val call = CompletableFuture<String>()
            val oldHandler = Log.setWtfHandler { _, what, _ -> call.complete(what.message) }
            assertFalse(jobService.onStartJob(params))
            assertNotNull(call.get())
            Log.setWtfHandler(oldHandler)
        }
    }

    @Test
    fun testOnStopJob() {
        val (params, _, im) = startMaintenance()
        assertTrue(jobService.onStopJob(params))
        assertTrue(im.isInterrupted)
        cleanupMaintenance(params)
    }

    // Helper method to test completing maintenance with different statuses just in case, but
    // in practice the only difference between success and other cases is that failures emit a
    // log message.
    private fun testListenToMaintenance(status: Int) {
        val (params, listener, _) = startMaintenance()
        assertTrue(listener.getInterfaceVersion() > 0)
        assertNotNull(listener.getInterfaceHash())
        assertNull(listener.asBinder())
        listener.onComplete(Status(status).toParcelable())
        val (completeParams, reschedule) = jobService.events.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        assertEquals(completeParams.jobId, params.jobId)
        if (Status.SUCCESS == status) {
            assertFalse(reschedule)
        } else {
            assertTrue(reschedule)
        }
        cleanupMaintenance(params)
    }

    @Test fun testListenToMaintenanceSuccess() = testListenToMaintenance(Status.SUCCESS)
    @Test fun testListenToMaintenanceFailure() = testListenToMaintenance(Status.ERROR_GENERIC)
    // There is very little point in testing for each error status, as they all behave the same
}
