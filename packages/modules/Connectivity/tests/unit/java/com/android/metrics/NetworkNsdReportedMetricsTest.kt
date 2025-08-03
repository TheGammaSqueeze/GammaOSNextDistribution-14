/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.metrics

import android.os.Build
import android.stats.connectivity.MdnsQueryResult
import android.stats.connectivity.NsdEventType
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class NetworkNsdReportedMetricsTest {
    private val deps = mock(NetworkNsdReportedMetrics.Dependencies::class.java)
    private val random = mock(Random::class.java)

    @Before
    fun setUp() {
        doReturn(random).`when`(deps).makeRandomGenerator()
    }

    @Test
    fun testReportServiceRegistrationSucceeded() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceRegistrationSucceeded(true /* isLegacy */, transactionId, durationMs)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertTrue(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_REGISTER, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_REGISTERED, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
        }
    }

    @Test
    fun testReportServiceRegistrationFailed() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceRegistrationFailed(false /* isLegacy */, transactionId, durationMs)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertFalse(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_REGISTER, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_REGISTRATION_FAILED, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
        }
    }

    @Test
    fun testReportServiceUnregistration() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val repliedRequestsCount = 25
        val sentPacketCount = 50
        val conflictDuringProbingCount = 2
        val conflictAfterProbingCount = 1
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceUnregistration(true /* isLegacy */, transactionId, durationMs,
                repliedRequestsCount, sentPacketCount, conflictDuringProbingCount,
                conflictAfterProbingCount)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertTrue(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_REGISTER, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_UNREGISTERED, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
            assertEquals(repliedRequestsCount, it.repliedRequestsCount)
            assertEquals(sentPacketCount, it.sentPacketCount)
            assertEquals(conflictDuringProbingCount, it.conflictDuringProbingCount)
            assertEquals(conflictAfterProbingCount, it.conflictAfterProbingCount)
        }
    }

    @Test
    fun testReportServiceDiscoveryStarted() {
        val clientId = 99
        val transactionId = 100
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceDiscoveryStarted(true /* isLegacy */, transactionId)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertTrue(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_DISCOVER, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_DISCOVERY_STARTED, it.queryResult)
        }
    }

    @Test
    fun testReportServiceDiscoveryFailed() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceDiscoveryFailed(false /* isLegacy */, transactionId, durationMs)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertFalse(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_DISCOVER, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_DISCOVERY_FAILED, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
        }
    }

    @Test
    fun testReportServiceDiscoveryStop() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val foundCallbackCount = 100
        val lostCallbackCount = 49
        val servicesCount = 75
        val sentQueryCount = 150
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceDiscoveryStop(true /* isLegacy */, transactionId, durationMs,
                foundCallbackCount, lostCallbackCount, servicesCount, sentQueryCount)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertTrue(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_DISCOVER, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_DISCOVERY_STOP, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
            assertEquals(foundCallbackCount, it.foundCallbackCount)
            assertEquals(lostCallbackCount, it.lostCallbackCount)
            assertEquals(servicesCount, it.foundServiceCount)
            assertEquals(durationMs, it.eventDurationMillisec)
            assertEquals(sentQueryCount, it.sentQueryCount)
        }
    }

    @Test
    fun testReportServiceResolved() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val sentQueryCount = 0
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceResolved(true /* isLegacy */, transactionId, durationMs,
                true /* isServiceFromCache */, sentQueryCount)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertTrue(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_RESOLVE, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_RESOLVED, it.queryResult)
            assertTrue(it.isKnownService)
            assertEquals(durationMs, it.eventDurationMillisec)
            assertEquals(sentQueryCount, it.sentQueryCount)
        }
    }

    @Test
    fun testReportServiceResolutionFailed() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceResolutionFailed(false /* isLegacy */, transactionId, durationMs)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertFalse(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_RESOLVE, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_RESOLUTION_FAILED, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
        }
    }

    @Test
    fun testReportServiceResolutionStop() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceResolutionStop(true /* isLegacy */, transactionId, durationMs)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertTrue(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_RESOLVE, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_RESOLUTION_STOP, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
        }
    }

    @Test
    fun testReportServiceInfoCallbackRegistered() {
        val clientId = 99
        val transactionId = 100
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceInfoCallbackRegistered(transactionId)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertFalse(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_SERVICE_INFO_CALLBACK, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_INFO_CALLBACK_REGISTERED, it.queryResult)
        }
    }

    @Test
    fun testReportServiceInfoCallbackRegistrationFailed() {
        val clientId = 99
        val transactionId = 100
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceInfoCallbackRegistrationFailed(transactionId)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertFalse(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_SERVICE_INFO_CALLBACK, it.type)
            assertEquals(
                    MdnsQueryResult.MQR_SERVICE_INFO_CALLBACK_REGISTRATION_FAILED, it.queryResult)
        }
    }

    @Test
    fun testReportServiceInfoCallbackUnregistered() {
        val clientId = 99
        val transactionId = 100
        val durationMs = 10L
        val updateCallbackCount = 100
        val lostCallbackCount = 10
        val sentQueryCount = 150
        val metrics = NetworkNsdReportedMetrics(clientId, deps)
        metrics.reportServiceInfoCallbackUnregistered(transactionId, durationMs,
                updateCallbackCount, lostCallbackCount, false /* isServiceFromCache */,
                sentQueryCount)

        val eventCaptor = ArgumentCaptor.forClass(NetworkNsdReported::class.java)
        verify(deps).statsWrite(eventCaptor.capture())
        eventCaptor.value.let {
            assertFalse(it.isLegacy)
            assertEquals(clientId, it.clientId)
            assertEquals(transactionId, it.transactionId)
            assertEquals(NsdEventType.NET_SERVICE_INFO_CALLBACK, it.type)
            assertEquals(MdnsQueryResult.MQR_SERVICE_INFO_CALLBACK_UNREGISTERED, it.queryResult)
            assertEquals(durationMs, it.eventDurationMillisec)
            assertEquals(updateCallbackCount, it.foundCallbackCount)
            assertEquals(lostCallbackCount, it.lostCallbackCount)
            assertFalse(it.isKnownService)
            assertEquals(sentQueryCount, it.sentQueryCount)
        }
    }
}
