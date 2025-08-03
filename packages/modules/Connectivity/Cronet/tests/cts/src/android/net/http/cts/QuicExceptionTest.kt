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

import android.net.http.QuicException
import android.os.Build
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class QuicExceptionTest {

    @Test
    fun testQuicException_returnsInputParameters() {
        val message = "failed"
        val cause = Throwable("thrown")
        val quicException =
            object : QuicException(message, cause) {
                override fun getErrorCode() = 0
                override fun isImmediatelyRetryable() = false
            }

        assertEquals(message, quicException.message)
        assertEquals(cause, quicException.cause)
    }

    // TODO: add test for QuicException triggered from HttpEngine
}
