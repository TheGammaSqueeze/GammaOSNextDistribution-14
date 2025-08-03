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

package com.android.server.connectivity.mdns

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.android.net.module.util.ArrayTrackRecord
import com.android.server.connectivity.mdns.MdnsServiceCache.CacheKey
import com.android.server.connectivity.mdns.MdnsServiceCacheTest.ExpiredRecord.ExpiredEvent.ServiceRecordExpired
import com.android.server.connectivity.mdns.util.MdnsUtils
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

private const val SERVICE_NAME_1 = "service-instance-1"
private const val SERVICE_NAME_2 = "service-instance-2"
private const val SERVICE_NAME_3 = "service-instance-3"
private const val SERVICE_TYPE_1 = "_test1._tcp.local"
private const val SERVICE_TYPE_2 = "_test2._tcp.local"
private const val INTERFACE_INDEX = 999
private const val DEFAULT_TIMEOUT_MS = 2000L
private const val NO_CALLBACK_TIMEOUT_MS = 200L
private const val TEST_ELAPSED_REALTIME_MS = 123L
private const val DEFAULT_TTL_TIME_MS = 120000L

@DevSdkIgnoreRunner.MonitorThreadLeak
@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
class MdnsServiceCacheTest {
    private val socketKey = SocketKey(null /* network */, INTERFACE_INDEX)
    private val cacheKey1 = CacheKey(SERVICE_TYPE_1, socketKey)
    private val cacheKey2 = CacheKey(SERVICE_TYPE_2, socketKey)
    private val thread = HandlerThread(MdnsServiceCacheTest::class.simpleName)
    private val clock = mock(MdnsUtils.Clock::class.java)
    private val handler by lazy {
        Handler(thread.looper)
    }

    private class ExpiredRecord : MdnsServiceCache.ServiceExpiredCallback {
        val history = ArrayTrackRecord<ExpiredEvent>().newReadHead()

        sealed class ExpiredEvent {
            abstract val previousResponse: MdnsResponse
            abstract val newResponse: MdnsResponse?
            data class ServiceRecordExpired(
                    override val previousResponse: MdnsResponse,
                    override val newResponse: MdnsResponse?
            ) : ExpiredEvent()
        }

        override fun onServiceRecordExpired(
                previousResponse: MdnsResponse,
                newResponse: MdnsResponse?
        ) {
            history.add(ServiceRecordExpired(previousResponse, newResponse))
        }

        fun expectedServiceRecordExpired(
                serviceName: String,
                timeoutMs: Long = DEFAULT_TIMEOUT_MS
        ) {
            val event = history.poll(timeoutMs)
            assertNotNull(event)
            assertTrue(event is ServiceRecordExpired)
            assertEquals(serviceName, event.previousResponse.serviceInstanceName)
        }

        fun assertNoCallback() {
            val cb = history.poll(NO_CALLBACK_TIMEOUT_MS)
            assertNull("Expected no callback but got $cb", cb)
        }
    }

    @Before
    fun setUp() {
        thread.start()
    }

    @After
    fun tearDown() {
        thread.quitSafely()
        thread.join()
    }

    private fun makeFlags(isExpiredServicesRemovalEnabled: Boolean = false) =
            MdnsFeatureFlags.Builder()
                    .setIsExpiredServicesRemovalEnabled(isExpiredServicesRemovalEnabled)
                    .build()

    private fun <T> runningOnHandlerAndReturn(functor: (() -> T)): T {
        val future = CompletableFuture<T>()
        handler.post {
            future.complete(functor())
        }
        return future.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun addOrUpdateService(
            serviceCache: MdnsServiceCache,
            cacheKey: CacheKey,
            service: MdnsResponse
    ): Unit = runningOnHandlerAndReturn { serviceCache.addOrUpdateService(cacheKey, service) }

    private fun removeService(
            serviceCache: MdnsServiceCache,
            serviceName: String,
            cacheKey: CacheKey
    ): Unit = runningOnHandlerAndReturn { serviceCache.removeService(serviceName, cacheKey) }

    private fun getService(
            serviceCache: MdnsServiceCache,
            serviceName: String,
            cacheKey: CacheKey
    ): MdnsResponse? = runningOnHandlerAndReturn {
        serviceCache.getCachedService(serviceName, cacheKey)
    }

    private fun getServices(
            serviceCache: MdnsServiceCache,
            cacheKey: CacheKey
    ): List<MdnsResponse> = runningOnHandlerAndReturn { serviceCache.getCachedServices(cacheKey) }

    private fun registerServiceExpiredCallback(
            serviceCache: MdnsServiceCache,
            cacheKey: CacheKey,
            callback: MdnsServiceCache.ServiceExpiredCallback
    ) = runningOnHandlerAndReturn {
        serviceCache.registerServiceExpiredCallback(cacheKey, callback)
    }

    @Test
    fun testAddAndRemoveService() {
        val serviceCache = MdnsServiceCache(thread.looper, makeFlags(), clock)
        addOrUpdateService(serviceCache, cacheKey1, createResponse(SERVICE_NAME_1, SERVICE_TYPE_1))
        var response = getService(serviceCache, SERVICE_NAME_1, cacheKey1)
        assertNotNull(response)
        assertEquals(SERVICE_NAME_1, response.serviceInstanceName)
        removeService(serviceCache, SERVICE_NAME_1, cacheKey1)
        response = getService(serviceCache, SERVICE_NAME_1, cacheKey1)
        assertNull(response)
    }

    @Test
    fun testGetCachedServices_multipleServiceTypes() {
        val serviceCache = MdnsServiceCache(thread.looper, makeFlags(), clock)
        addOrUpdateService(serviceCache, cacheKey1, createResponse(SERVICE_NAME_1, SERVICE_TYPE_1))
        addOrUpdateService(serviceCache, cacheKey1, createResponse(SERVICE_NAME_2, SERVICE_TYPE_1))
        addOrUpdateService(serviceCache, cacheKey2, createResponse(SERVICE_NAME_2, SERVICE_TYPE_2))

        val responses1 = getServices(serviceCache, cacheKey1)
        assertEquals(2, responses1.size)
        assertTrue(responses1.stream().anyMatch { response ->
            response.serviceInstanceName == SERVICE_NAME_1
        })
        assertTrue(responses1.any { response ->
            response.serviceInstanceName == SERVICE_NAME_2
        })
        val responses2 = getServices(serviceCache, cacheKey2)
        assertEquals(1, responses2.size)
        assertTrue(responses2.any { response ->
            response.serviceInstanceName == SERVICE_NAME_2
        })

        removeService(serviceCache, SERVICE_NAME_2, cacheKey1)
        val responses3 = getServices(serviceCache, cacheKey1)
        assertEquals(1, responses3.size)
        assertTrue(responses3.any { response ->
            response.serviceInstanceName == SERVICE_NAME_1
        })
        val responses4 = getServices(serviceCache, cacheKey2)
        assertEquals(1, responses4.size)
        assertTrue(responses4.any { response ->
            response.serviceInstanceName == SERVICE_NAME_2
        })
    }

    @Test
    fun testServiceExpiredAndSendCallbacks() {
        val serviceCache = MdnsServiceCache(
                thread.looper, makeFlags(isExpiredServicesRemovalEnabled = true), clock)
        // Register service expired callbacks
        val callback1 = ExpiredRecord()
        val callback2 = ExpiredRecord()
        registerServiceExpiredCallback(serviceCache, cacheKey1, callback1)
        registerServiceExpiredCallback(serviceCache, cacheKey2, callback2)

        doReturn(TEST_ELAPSED_REALTIME_MS).`when`(clock).elapsedRealtime()

        // Add multiple services with different ttl time.
        addOrUpdateService(serviceCache, cacheKey1, createResponse(SERVICE_NAME_1, SERVICE_TYPE_1,
                DEFAULT_TTL_TIME_MS))
        addOrUpdateService(serviceCache, cacheKey1, createResponse(SERVICE_NAME_2, SERVICE_TYPE_1,
                DEFAULT_TTL_TIME_MS + 20L))
        addOrUpdateService(serviceCache, cacheKey2, createResponse(SERVICE_NAME_3, SERVICE_TYPE_2,
                DEFAULT_TTL_TIME_MS + 10L))

        // Check the service expiration immediately. Should be no callback.
        assertEquals(2, getServices(serviceCache, cacheKey1).size)
        assertEquals(1, getServices(serviceCache, cacheKey2).size)
        callback1.assertNoCallback()
        callback2.assertNoCallback()

        // Simulate the case where the response is after TTL then check expired services.
        // Expect SERVICE_NAME_1 expired.
        doReturn(TEST_ELAPSED_REALTIME_MS + DEFAULT_TTL_TIME_MS).`when`(clock).elapsedRealtime()
        assertEquals(1, getServices(serviceCache, cacheKey1).size)
        assertEquals(1, getServices(serviceCache, cacheKey2).size)
        callback1.expectedServiceRecordExpired(SERVICE_NAME_1)
        callback2.assertNoCallback()

        // Simulate the case where the response is after TTL then check expired services.
        // Expect SERVICE_NAME_3 expired.
        doReturn(TEST_ELAPSED_REALTIME_MS + DEFAULT_TTL_TIME_MS + 11L)
                .`when`(clock).elapsedRealtime()
        assertEquals(1, getServices(serviceCache, cacheKey1).size)
        assertEquals(0, getServices(serviceCache, cacheKey2).size)
        callback1.assertNoCallback()
        callback2.expectedServiceRecordExpired(SERVICE_NAME_3)
    }

    @Test
    fun testRemoveExpiredServiceWhenGetting() {
        val serviceCache = MdnsServiceCache(
                thread.looper, makeFlags(isExpiredServicesRemovalEnabled = true), clock)

        doReturn(TEST_ELAPSED_REALTIME_MS).`when`(clock).elapsedRealtime()
        addOrUpdateService(serviceCache, cacheKey1,
                createResponse(SERVICE_NAME_1, SERVICE_TYPE_1, 1L /* ttlTime */))
        doReturn(TEST_ELAPSED_REALTIME_MS + 2L).`when`(clock).elapsedRealtime()
        assertNull(getService(serviceCache, SERVICE_NAME_1, cacheKey1))

        addOrUpdateService(serviceCache, cacheKey2,
                createResponse(SERVICE_NAME_2, SERVICE_TYPE_2, 3L /* ttlTime */))
        doReturn(TEST_ELAPSED_REALTIME_MS + 4L).`when`(clock).elapsedRealtime()
        assertEquals(0, getServices(serviceCache, cacheKey2).size)
    }

    @Test
    fun testInsertResponseAndSortList() {
        val responses = ArrayList<MdnsResponse>()
        val response1 = createResponse(SERVICE_NAME_1, SERVICE_TYPE_1, 100L /* ttlTime */)
        MdnsServiceCache.insertResponseAndSortList(responses, response1, TEST_ELAPSED_REALTIME_MS)
        assertEquals(1, responses.size)
        assertEquals(response1, responses[0])

        val response2 = createResponse(SERVICE_NAME_2, SERVICE_TYPE_1, 50L /* ttlTime */)
        MdnsServiceCache.insertResponseAndSortList(responses, response2, TEST_ELAPSED_REALTIME_MS)
        assertEquals(2, responses.size)
        assertEquals(response2, responses[0])
        assertEquals(response1, responses[1])

        val response3 = createResponse(SERVICE_NAME_3, SERVICE_TYPE_1, 75L /* ttlTime */)
        MdnsServiceCache.insertResponseAndSortList(responses, response3, TEST_ELAPSED_REALTIME_MS)
        assertEquals(3, responses.size)
        assertEquals(response2, responses[0])
        assertEquals(response3, responses[1])
        assertEquals(response1, responses[2])

        val response4 = createResponse("service-instance-4", SERVICE_TYPE_1, 125L /* ttlTime */)
        MdnsServiceCache.insertResponseAndSortList(responses, response4, TEST_ELAPSED_REALTIME_MS)
        assertEquals(4, responses.size)
        assertEquals(response2, responses[0])
        assertEquals(response3, responses[1])
        assertEquals(response1, responses[2])
        assertEquals(response4, responses[3])
    }

    private fun createResponse(
            serviceInstanceName: String,
            serviceType: String,
            ttlTime: Long = 120000L
    ): MdnsResponse {
        val serviceName = "$serviceInstanceName.$serviceType".split(".").toTypedArray()
        val response = MdnsResponse(
                0 /* now */, "$serviceInstanceName.$serviceType".split(".").toTypedArray(),
                socketKey.interfaceIndex, socketKey.network)

        // Set PTR record
        val pointerRecord = MdnsPointerRecord(
                serviceType.split(".").toTypedArray(),
                TEST_ELAPSED_REALTIME_MS /* receiptTimeMillis */,
                false /* cacheFlush */,
                ttlTime /* ttlMillis */,
                serviceName)
        response.addPointerRecord(pointerRecord)

        // Set SRV record.
        val serviceRecord = MdnsServiceRecord(
                serviceName,
                TEST_ELAPSED_REALTIME_MS /* receiptTimeMillis */,
                false /* cacheFlush */,
                ttlTime /* ttlMillis */,
                0 /* servicePriority */,
                0 /* serviceWeight */,
                12345 /* port */,
                arrayOf("hostname"))
        response.serviceRecord = serviceRecord
        return response
    }
}
