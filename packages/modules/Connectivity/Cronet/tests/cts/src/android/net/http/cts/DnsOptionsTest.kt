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

import android.net.http.DnsOptions
import android.net.http.DnsOptions.DNS_OPTION_ENABLED
import android.net.http.DnsOptions.DNS_OPTION_UNSPECIFIED
import android.os.Build
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class DnsOptionsTest {

    @Test
    fun testDnsOptions_defaultValues() {
        val options = DnsOptions.Builder().build()

        assertEquals(DNS_OPTION_UNSPECIFIED, options.persistHostCache)
        assertNull(options.persistHostCachePeriod)
        assertEquals(DNS_OPTION_UNSPECIFIED, options.staleDns)
        assertNull(options.staleDnsOptions)
        assertEquals(DNS_OPTION_UNSPECIFIED, options.useHttpStackDnsResolver)
        assertEquals(DNS_OPTION_UNSPECIFIED,
                options.preestablishConnectionsToStaleDnsResults)
    }

    @Test
    fun testDnsOptions_persistHostCache_returnSetValue() {
        val options = DnsOptions.Builder()
                .setPersistHostCache(DNS_OPTION_ENABLED)
                .build()

        assertEquals(DNS_OPTION_ENABLED, options.persistHostCache)
    }

    @Test
    fun testDnsOptions_persistHostCachePeriod_returnSetValue() {
        val period = Duration.ofMillis(12345)
        val options = DnsOptions.Builder().setPersistHostCachePeriod(period).build()

        assertEquals(period, options.persistHostCachePeriod)
    }

    @Test
    fun testDnsOptions_enableStaleDns_returnSetValue() {
        val options = DnsOptions.Builder()
                .setStaleDns(DNS_OPTION_ENABLED)
                .build()

        assertEquals(DNS_OPTION_ENABLED, options.staleDns)
    }

    @Test
    fun testDnsOptions_useHttpStackDnsResolver_returnsSetValue() {
        val options = DnsOptions.Builder()
                .setUseHttpStackDnsResolver(DNS_OPTION_ENABLED)
                .build()

        assertEquals(DNS_OPTION_ENABLED, options.useHttpStackDnsResolver)
    }

    @Test
    fun testDnsOptions_preestablishConnectionsToStaleDnsResults_returnsSetValue() {
        val options = DnsOptions.Builder()
                .setPreestablishConnectionsToStaleDnsResults(DNS_OPTION_ENABLED)
                .build()

        assertEquals(DNS_OPTION_ENABLED,
                options.preestablishConnectionsToStaleDnsResults)
    }

    @Test
    fun testDnsOptions_setStaleDnsOptions_returnsSetValues() {
        val staleOptions = DnsOptions.StaleDnsOptions.Builder()
                .setAllowCrossNetworkUsage(DNS_OPTION_ENABLED)
                .setFreshLookupTimeout(Duration.ofMillis(1234))
                .build()
        val options = DnsOptions.Builder()
                .setStaleDns(DNS_OPTION_ENABLED)
                .setStaleDnsOptions(staleOptions)
                .build()

        assertEquals(DNS_OPTION_ENABLED, options.staleDns)
        assertEquals(staleOptions, options.staleDnsOptions)
    }

    @Test
    fun testStaleDnsOptions_defaultValues() {
        val options = DnsOptions.StaleDnsOptions.Builder().build()

        assertEquals(DNS_OPTION_UNSPECIFIED, options.allowCrossNetworkUsage)
        assertNull(options.freshLookupTimeout)
        assertNull(options.maxExpiredDelay)
        assertEquals(DNS_OPTION_UNSPECIFIED, options.useStaleOnNameNotResolved)
    }

    @Test
    fun testStaleDnsOptions_allowCrossNetworkUsage_returnsSetValue() {
        val options = DnsOptions.StaleDnsOptions.Builder()
                .setAllowCrossNetworkUsage(DNS_OPTION_ENABLED).build()

        assertEquals(DNS_OPTION_ENABLED, options.allowCrossNetworkUsage)
    }

    @Test
    fun testStaleDnsOptions_freshLookupTimeout_returnsSetValue() {
        val duration = Duration.ofMillis(12345)
        val options = DnsOptions.StaleDnsOptions.Builder().setFreshLookupTimeout(duration).build()

        assertNotNull(options.freshLookupTimeout)
        assertEquals(duration, options.freshLookupTimeout!!)
    }

    @Test
    fun testStaleDnsOptions_useStaleOnNameNotResolved_returnsSetValue() {
        val options = DnsOptions.StaleDnsOptions.Builder()
                .setUseStaleOnNameNotResolved(DNS_OPTION_ENABLED)
                .build()

        assertEquals(DNS_OPTION_ENABLED, options.useStaleOnNameNotResolved)
    }

    @Test
    fun testStaleDnsOptions_maxExpiredDelayMillis_returnsSetValue() {
        val duration = Duration.ofMillis(12345)
        val options = DnsOptions.StaleDnsOptions.Builder().setMaxExpiredDelay(duration).build()

        assertNotNull(options.maxExpiredDelay)
        assertEquals(duration, options.maxExpiredDelay!!)
    }
}
