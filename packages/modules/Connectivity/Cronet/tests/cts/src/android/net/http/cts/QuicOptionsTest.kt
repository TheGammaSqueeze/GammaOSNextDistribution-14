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

import android.net.http.QuicOptions
import android.os.Build
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class QuicOptionsTest {
    @Test
    fun testQuicOptions_defaultValues() {
        val quicOptions = QuicOptions.Builder().build()
        assertThat(quicOptions.allowedQuicHosts).isEmpty()
        assertThat(quicOptions.handshakeUserAgent).isNull()
        assertThat(quicOptions.idleConnectionTimeout).isNull()
        assertFalse(quicOptions.hasInMemoryServerConfigsCacheSize())
        assertFailsWith(IllegalStateException::class) {
            quicOptions.inMemoryServerConfigsCacheSize
        }
    }

    @Test
    fun testQuicOptions_quicHostAllowlist_returnsAddedValues() {
        val quicOptions = QuicOptions.Builder()
                .addAllowedQuicHost("foo")
                .addAllowedQuicHost("bar")
                .addAllowedQuicHost("foo")
                .addAllowedQuicHost("baz")
                .build()
        assertThat(quicOptions.allowedQuicHosts)
                .containsExactly("foo", "bar", "baz")
                .inOrder()
    }

    @Test
    fun testQuicOptions_idleConnectionTimeout_returnsSetValue() {
        val timeout = Duration.ofMinutes(10)
        val quicOptions = QuicOptions.Builder()
                .setIdleConnectionTimeout(timeout)
                .build()
        assertThat(quicOptions.idleConnectionTimeout)
                .isEqualTo(timeout)
    }

    @Test
    fun testQuicOptions_inMemoryServerConfigsCacheSize_returnsSetValue() {
        val quicOptions = QuicOptions.Builder()
                .setInMemoryServerConfigsCacheSize(42)
                .build()
        assertTrue(quicOptions.hasInMemoryServerConfigsCacheSize())
        assertThat(quicOptions.inMemoryServerConfigsCacheSize)
                .isEqualTo(42)
    }

    @Test
    fun testQuicOptions_handshakeUserAgent_returnsSetValue() {
        val userAgent = "test"
        val quicOptions = QuicOptions.Builder()
            .setHandshakeUserAgent(userAgent)
            .build()
        assertThat(quicOptions.handshakeUserAgent)
            .isEqualTo(userAgent)
    }
}
