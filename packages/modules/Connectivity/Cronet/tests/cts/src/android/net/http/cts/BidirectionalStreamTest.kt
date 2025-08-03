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

package android.net.http.cts

import android.content.Context
import android.net.http.BidirectionalStream
import android.net.http.HttpEngine
import android.net.http.cts.util.TestBidirectionalStreamCallback
import android.net.http.cts.util.TestBidirectionalStreamCallback.ResponseStep
import android.net.http.cts.util.assumeOKStatusCode
import android.net.http.cts.util.skipIfNoInternetConnection
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.SkipPresubmit
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertEquals
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.After
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.runner.RunWith

private const val URL = "https://source.android.com"

/**
 * This tests uses a non-hermetic server. Instead of asserting, assume the next callback. This way,
 * if the request were to fail, the test would just be skipped instead of failing.
 */
@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class BidirectionalStreamTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val callback = TestBidirectionalStreamCallback()
    private val httpEngine = HttpEngine.Builder(context).build()
    private var stream: BidirectionalStream? = null

    @Before
    fun setUp() {
        skipIfNoInternetConnection(context)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        httpEngine.shutdown()
    }

    private fun createBidirectionalStreamBuilder(url: String): BidirectionalStream.Builder {
        return httpEngine.newBidirectionalStreamBuilder(url, callback.executor, callback)
    }

    @Test
    @Throws(Exception::class)
    @SkipPresubmit(reason = "b/293141085 Confirm non-flaky and move to presubmit after SLO")
    fun testBidirectionalStream_GetStream_CompletesSuccessfully() {
        stream = createBidirectionalStreamBuilder(URL).setHttpMethod("GET").build()
        stream!!.start()
        // We call to a real server and hence the server may not be reachable, cancel this stream
        // and rethrow the exception before tearDown,
        // otherwise shutdown would fail with active request error.
        try {
            callback.assumeCallback(ResponseStep.ON_SUCCEEDED)
        } catch (e: AssumptionViolatedException) {
            stream!!.cancel()
            callback.blockForDone()
            throw e
        }

        val info = callback.mResponseInfo
        assumeOKStatusCode(info)
        MatcherAssert.assertThat(
            "Received byte count must be > 0", info.receivedByteCount, Matchers.greaterThan(0L))
        assertEquals("h2", info.negotiatedProtocol)
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_getHttpMethod() {
        val builder = createBidirectionalStreamBuilder(URL)
        val method = "GET"

        builder.setHttpMethod(method)
        stream = builder.build()
        assertThat(stream!!.getHttpMethod()).isEqualTo(method)
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_hasTrafficStatsTag() {
        val builder = createBidirectionalStreamBuilder(URL)

        builder.setTrafficStatsTag(10)
        stream = builder.build()
        assertThat(stream!!.hasTrafficStatsTag()).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_getTrafficStatsTag() {
        val builder = createBidirectionalStreamBuilder(URL)
        val trafficStatsTag = 10

        builder.setTrafficStatsTag(trafficStatsTag)
        stream = builder.build()
        assertThat(stream!!.getTrafficStatsTag()).isEqualTo(trafficStatsTag)
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_hasTrafficStatsUid() {
        val builder = createBidirectionalStreamBuilder(URL)

        builder.setTrafficStatsUid(10)
        stream = builder.build()
        assertThat(stream!!.hasTrafficStatsUid()).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_getTrafficStatsUid() {
        val builder = createBidirectionalStreamBuilder(URL)
        val trafficStatsUid = 10

        builder.setTrafficStatsUid(trafficStatsUid)
        stream = builder.build()
        assertThat(stream!!.getTrafficStatsUid()).isEqualTo(trafficStatsUid)
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_getHeaders_asList() {
        val builder = createBidirectionalStreamBuilder(URL)
        val expectedHeaders = mapOf(
          "Authorization" to "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
          "Max-Forwards" to "10",
          "X-Client-Data" to "random custom header content").entries.toList()

        for (header in expectedHeaders) {
            builder.addHeader(header.key, header.value)
        }

        stream = builder.build()
        assertThat(stream!!.getHeaders().getAsList()).containsAtLeastElementsIn(expectedHeaders)
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_getHeaders_asMap() {
        val builder = createBidirectionalStreamBuilder(URL)
        val expectedHeaders = mapOf(
          "Authorization" to listOf("Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
          "Max-Forwards" to listOf("10"),
          "X-Client-Data" to listOf("random custom header content"))

        for (header in expectedHeaders) {
            builder.addHeader(header.key, header.value.get(0))
        }

        stream = builder.build()
        assertThat(stream!!.getHeaders().getAsMap()).containsAtLeastEntriesIn(expectedHeaders)
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_getPriority() {
        val builder = createBidirectionalStreamBuilder(URL)
        val priority = BidirectionalStream.STREAM_PRIORITY_LOW

        builder.setPriority(priority)
        stream = builder.build()
        assertThat(stream!!.getPriority()).isEqualTo(priority)
    }

    @Test
    @Throws(Exception::class)
    fun testBidirectionalStream_isDelayRequestHeadersUntilFirstFlushEnabled() {
        val builder = createBidirectionalStreamBuilder(URL)

        builder.setDelayRequestHeadersUntilFirstFlushEnabled(true)
        stream = builder.build()
        assertThat(stream!!.isDelayRequestHeadersUntilFirstFlushEnabled()).isTrue()
    }
}
