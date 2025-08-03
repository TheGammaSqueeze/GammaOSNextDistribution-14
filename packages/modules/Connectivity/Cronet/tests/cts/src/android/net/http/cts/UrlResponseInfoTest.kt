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
import android.net.http.HttpEngine
import android.net.http.cts.util.HttpCtsTestServer
import android.net.http.cts.util.TestUrlRequestCallback
import android.net.http.cts.util.TestUrlRequestCallback.ResponseStep
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class UrlResponseInfoTest {

    @Test
    fun testUrlResponseInfo_apisReturnCorrectInfo() {
        // start the engine and send a request
        val context: Context = ApplicationProvider.getApplicationContext()
        val server = HttpCtsTestServer(context)
        val httpEngine = HttpEngine.Builder(context).build()
        val callback = TestUrlRequestCallback()
        val url = server.successUrl
        val request = httpEngine.newUrlRequestBuilder(url, callback.executor, callback).build()

        request.start()
        callback.expectCallback(ResponseStep.ON_SUCCEEDED)

        val info = callback.mResponseInfo
        assertFalse(info.headers.asList.isEmpty())
        assertEquals(200, info.httpStatusCode)
        assertTrue(info.receivedByteCount > 0)
        assertEquals(url, info.url)
        assertEquals(listOf(url), info.urlChain)
        assertFalse(info.wasCached())

        // TODO Current test server does not set these values. Uncomment when we use one that does.
        // assertEquals("OK", info.httpStatusText)
        // assertEquals("http/1.1", info.negotiatedProtocol)

        // cronet defaults to port 0 when no proxy is specified.
        // This is not a behaviour we want to enforce since null is reasonable too.
        // assertEquals(":0", info.proxyServer)

        server.shutdown()
        httpEngine.shutdown()
    }
}
