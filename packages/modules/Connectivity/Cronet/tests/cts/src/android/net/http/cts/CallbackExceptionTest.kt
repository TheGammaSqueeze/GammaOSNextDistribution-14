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
import android.net.http.CallbackException
import android.net.http.HttpEngine
import android.net.http.cts.util.HttpCtsTestServer
import android.net.http.cts.util.TestUrlRequestCallback
import android.net.http.cts.util.TestUrlRequestCallback.FailureType
import android.net.http.cts.util.TestUrlRequestCallback.ResponseStep
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class CallbackExceptionTest {

    @Test
    fun testCallbackException_returnsInputParameters() {
        val message = "failed"
        val cause = Throwable("exception")
        val callbackException = object : CallbackException(message, cause) {}

        assertEquals(message, callbackException.message)
        assertSame(cause, callbackException.cause)
    }

    @Test
    fun testCallbackException_thrownFromUrlRequest() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val server = HttpCtsTestServer(context)
        val httpEngine = HttpEngine.Builder(context).build()
        val callback = TestUrlRequestCallback()
        callback.setFailure(FailureType.THROW_SYNC, ResponseStep.ON_RESPONSE_STARTED)
        val request = httpEngine
            .newUrlRequestBuilder(server.successUrl, callback.executor, callback)
            .build()

        request.start()
        callback.blockForDone()

        assertTrue(request.isDone)
        assertIs<CallbackException>(callback.mError)
        server.shutdown()
        httpEngine.shutdown()
    }
}
