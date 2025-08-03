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

package android.net

import android.net.ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE
import android.net.ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_AVOID
import android.net.ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_IGNORE
import android.net.ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_PROMPT
import android.net.ConnectivitySettingsManager.CONNECTIVITY_RELEASE_PENDING_INTENT_DELAY_MS
import android.net.ConnectivitySettingsManager.DATA_ACTIVITY_TIMEOUT_MOBILE
import android.net.ConnectivitySettingsManager.DATA_ACTIVITY_TIMEOUT_WIFI
import android.net.ConnectivitySettingsManager.DNS_RESOLVER_MAX_SAMPLES
import android.net.ConnectivitySettingsManager.DNS_RESOLVER_MIN_SAMPLES
import android.net.ConnectivitySettingsManager.DNS_RESOLVER_SAMPLE_VALIDITY_SECONDS
import android.net.ConnectivitySettingsManager.DNS_RESOLVER_SUCCESS_THRESHOLD_PERCENT
import android.net.ConnectivitySettingsManager.MOBILE_DATA_ALWAYS_ON
import android.net.ConnectivitySettingsManager.NETWORK_SWITCH_NOTIFICATION_DAILY_LIMIT
import android.net.ConnectivitySettingsManager.NETWORK_SWITCH_NOTIFICATION_RATE_LIMIT_MILLIS
import android.net.ConnectivitySettingsManager.PRIVATE_DNS_DEFAULT_MODE
import android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF
import android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC
import android.net.ConnectivitySettingsManager.WIFI_ALWAYS_REQUESTED
import android.net.ConnectivitySettingsManager.getCaptivePortalMode
import android.net.ConnectivitySettingsManager.getConnectivityKeepPendingIntentDuration
import android.net.ConnectivitySettingsManager.getDnsResolverSampleRanges
import android.net.ConnectivitySettingsManager.getDnsResolverSampleValidityDuration
import android.net.ConnectivitySettingsManager.getDnsResolverSuccessThresholdPercent
import android.net.ConnectivitySettingsManager.getIngressRateLimitInBytesPerSecond
import android.net.ConnectivitySettingsManager.getMobileDataActivityTimeout
import android.net.ConnectivitySettingsManager.getMobileDataAlwaysOn
import android.net.ConnectivitySettingsManager.getNetworkSwitchNotificationMaximumDailyCount
import android.net.ConnectivitySettingsManager.getNetworkSwitchNotificationRateDuration
import android.net.ConnectivitySettingsManager.getPrivateDnsDefaultMode
import android.net.ConnectivitySettingsManager.getWifiAlwaysRequested
import android.net.ConnectivitySettingsManager.getWifiDataActivityTimeout
import android.net.ConnectivitySettingsManager.setCaptivePortalMode
import android.net.ConnectivitySettingsManager.setConnectivityKeepPendingIntentDuration
import android.net.ConnectivitySettingsManager.setDnsResolverSampleRanges
import android.net.ConnectivitySettingsManager.setDnsResolverSampleValidityDuration
import android.net.ConnectivitySettingsManager.setDnsResolverSuccessThresholdPercent
import android.net.ConnectivitySettingsManager.setIngressRateLimitInBytesPerSecond
import android.net.ConnectivitySettingsManager.setMobileDataActivityTimeout
import android.net.ConnectivitySettingsManager.setMobileDataAlwaysOn
import android.net.ConnectivitySettingsManager.setNetworkSwitchNotificationMaximumDailyCount
import android.net.ConnectivitySettingsManager.setNetworkSwitchNotificationRateDuration
import android.net.ConnectivitySettingsManager.setPrivateDnsDefaultMode
import android.net.ConnectivitySettingsManager.setWifiAlwaysRequested
import android.net.ConnectivitySettingsManager.setWifiDataActivityTimeout
import android.os.Build
import android.platform.test.annotations.AppModeFull
import android.provider.Settings
import android.util.Range
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import com.android.net.module.util.ConnectivitySettingsUtils.getPrivateDnsModeAsString
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.util.Objects
import kotlin.test.assertFailsWith

/**
 * Tests for [ConnectivitySettingsManager].
 *
 * Build, install and run with:
 * atest android.net.ConnectivitySettingsManagerTest
 */
@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.R)
@SmallTest
@AppModeFull(reason = "WRITE_SECURE_SETTINGS permission can't be granted to instant apps")
class ConnectivitySettingsManagerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private val resolver = context.contentResolver

    private val defaultDuration = Duration.ofSeconds(0L)
    private val testTime1 = 5L
    private val testTime2 = 10L
    private val settingsTypeGlobal = "global"
    private val settingsTypeSecure = "secure"

    /*** Reset setting value or delete setting if the setting was not existed before testing. */
    private fun resetSettings(names: Array<String>, type: String, values: Array<String?>) {
        for (i in names.indices) {
            if (Objects.equals(values[i], null)) {
                instrumentation.uiAutomation.executeShellCommand(
                        "settings delete $type ${names[i]}")
            } else {
                if (settingsTypeSecure.equals(type)) {
                    Settings.Secure.putString(resolver, names[i], values[i])
                } else {
                    Settings.Global.putString(resolver, names[i], values[i])
                }
            }
        }
    }

    fun <T> testIntSetting(
        names: Array<String>,
        type: String,
        value1: T,
        value2: T,
        getter: () -> T,
        setter: (value: T) -> Unit,
        testIntValues: IntArray
    ) {
        val originals: Array<String?> = Array(names.size) { i ->
            if (settingsTypeSecure.equals(type)) {
                Settings.Secure.getString(resolver, names[i])
            } else {
                Settings.Global.getString(resolver, names[i])
            }
        }

        try {
            for (i in names.indices) {
                if (settingsTypeSecure.equals(type)) {
                    Settings.Secure.putString(resolver, names[i], testIntValues[i].toString())
                } else {
                    Settings.Global.putString(resolver, names[i], testIntValues[i].toString())
                }
            }
            assertEquals(value1, getter())

            setter(value2)
            assertEquals(value2, getter())
        } finally {
            resetSettings(names, type, originals)
        }
    }

    @Test
    fun testMobileDataActivityTimeout() {
        testIntSetting(names = arrayOf(DATA_ACTIVITY_TIMEOUT_MOBILE), type = settingsTypeGlobal,
                value1 = Duration.ofSeconds(testTime1), value2 = Duration.ofSeconds(testTime2),
                getter = { getMobileDataActivityTimeout(context, defaultDuration) },
                setter = { setMobileDataActivityTimeout(context, it) },
                testIntValues = intArrayOf(testTime1.toInt()))
    }

    @Test
    fun testWifiDataActivityTimeout() {
        testIntSetting(names = arrayOf(DATA_ACTIVITY_TIMEOUT_WIFI), type = settingsTypeGlobal,
                value1 = Duration.ofSeconds(testTime1), value2 = Duration.ofSeconds(testTime2),
                getter = { getWifiDataActivityTimeout(context, defaultDuration) },
                setter = { setWifiDataActivityTimeout(context, it) },
                testIntValues = intArrayOf(testTime1.toInt()))
    }

    @Test
    fun testDnsResolverSampleValidityDuration() {
        testIntSetting(names = arrayOf(DNS_RESOLVER_SAMPLE_VALIDITY_SECONDS),
                type = settingsTypeGlobal, value1 = Duration.ofSeconds(testTime1),
                value2 = Duration.ofSeconds(testTime2),
                getter = { getDnsResolverSampleValidityDuration(context, defaultDuration) },
                setter = { setDnsResolverSampleValidityDuration(context, it) },
                testIntValues = intArrayOf(testTime1.toInt()))

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setDnsResolverSampleValidityDuration(context, Duration.ofSeconds(-1L)) }
    }

    @Test
    fun testDnsResolverSuccessThresholdPercent() {
        testIntSetting(names = arrayOf(DNS_RESOLVER_SUCCESS_THRESHOLD_PERCENT),
                type = settingsTypeGlobal, value1 = 5, value2 = 10,
                getter = { getDnsResolverSuccessThresholdPercent(context, 0 /* def */) },
                setter = { setDnsResolverSuccessThresholdPercent(context, it) },
                testIntValues = intArrayOf(5))

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setDnsResolverSuccessThresholdPercent(context, -1) }
        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setDnsResolverSuccessThresholdPercent(context, 120) }
    }

    @Test
    fun testDnsResolverSampleRanges() {
        testIntSetting(names = arrayOf(DNS_RESOLVER_MIN_SAMPLES, DNS_RESOLVER_MAX_SAMPLES),
                type = settingsTypeGlobal, value1 = Range(1, 63), value2 = Range(2, 62),
                getter = { getDnsResolverSampleRanges(context) },
                setter = { setDnsResolverSampleRanges(context, it) },
                testIntValues = intArrayOf(1, 63))

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setDnsResolverSampleRanges(context, Range(-1, 62)) }
        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setDnsResolverSampleRanges(context, Range(2, 65)) }
    }

    @Test
    fun testNetworkSwitchNotificationMaximumDailyCount() {
        testIntSetting(names = arrayOf(NETWORK_SWITCH_NOTIFICATION_DAILY_LIMIT),
                type = settingsTypeGlobal, value1 = 5, value2 = 15,
                getter = { getNetworkSwitchNotificationMaximumDailyCount(context, 0 /* def */) },
                setter = { setNetworkSwitchNotificationMaximumDailyCount(context, it) },
                testIntValues = intArrayOf(5))

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setNetworkSwitchNotificationMaximumDailyCount(context, -1) }
    }

    @Test
    fun testNetworkSwitchNotificationRateDuration() {
        testIntSetting(names = arrayOf(NETWORK_SWITCH_NOTIFICATION_RATE_LIMIT_MILLIS),
                type = settingsTypeGlobal, value1 = Duration.ofMillis(testTime1),
                value2 = Duration.ofMillis(testTime2),
                getter = { getNetworkSwitchNotificationRateDuration(context, defaultDuration) },
                setter = { setNetworkSwitchNotificationRateDuration(context, it) },
                testIntValues = intArrayOf(testTime1.toInt()))

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setNetworkSwitchNotificationRateDuration(context, Duration.ofMillis(-1L)) }
    }

    @Test
    fun testCaptivePortalMode() {
        testIntSetting(names = arrayOf(CAPTIVE_PORTAL_MODE), type = settingsTypeGlobal,
                value1 = CAPTIVE_PORTAL_MODE_AVOID, value2 = CAPTIVE_PORTAL_MODE_PROMPT,
                getter = { getCaptivePortalMode(context, CAPTIVE_PORTAL_MODE_IGNORE) },
                setter = { setCaptivePortalMode(context, it) },
                testIntValues = intArrayOf(CAPTIVE_PORTAL_MODE_AVOID))

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setCaptivePortalMode(context, 5 /* mode */) }
    }

    @Test
    fun testPrivateDnsDefaultMode() {
        val original = Settings.Global.getString(resolver, PRIVATE_DNS_DEFAULT_MODE)

        try {
            val mode = getPrivateDnsModeAsString(PRIVATE_DNS_MODE_OPPORTUNISTIC)
            Settings.Global.putString(resolver, PRIVATE_DNS_DEFAULT_MODE, mode)
            assertEquals(mode, getPrivateDnsDefaultMode(context))

            setPrivateDnsDefaultMode(context, PRIVATE_DNS_MODE_OFF)
            assertEquals(getPrivateDnsModeAsString(PRIVATE_DNS_MODE_OFF),
                    getPrivateDnsDefaultMode(context))
        } finally {
            resetSettings(names = arrayOf(PRIVATE_DNS_DEFAULT_MODE), type = settingsTypeGlobal,
                    values = arrayOf(original))
        }

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setPrivateDnsDefaultMode(context, -1) }
    }

    @Test
    fun testConnectivityKeepPendingIntentDuration() {
        testIntSetting(names = arrayOf(CONNECTIVITY_RELEASE_PENDING_INTENT_DELAY_MS),
                type = settingsTypeSecure, value1 = Duration.ofMillis(testTime1),
                value2 = Duration.ofMillis(testTime2),
                getter = { getConnectivityKeepPendingIntentDuration(context, defaultDuration) },
                setter = { setConnectivityKeepPendingIntentDuration(context, it) },
                testIntValues = intArrayOf(testTime1.toInt()))

        assertFailsWith<IllegalArgumentException>("Expect fail but argument accepted.") {
            setConnectivityKeepPendingIntentDuration(context, Duration.ofMillis(-1L)) }
    }

    @Test
    fun testMobileDataAlwaysOn() {
        testIntSetting(names = arrayOf(MOBILE_DATA_ALWAYS_ON), type = settingsTypeGlobal,
                value1 = false, value2 = true,
                getter = { getMobileDataAlwaysOn(context, true /* def */) },
                setter = { setMobileDataAlwaysOn(context, it) },
                testIntValues = intArrayOf(0))
    }

    @Test
    fun testWifiAlwaysRequested() {
        testIntSetting(names = arrayOf(WIFI_ALWAYS_REQUESTED), type = settingsTypeGlobal,
                value1 = false, value2 = true,
                getter = { getWifiAlwaysRequested(context, true /* def */) },
                setter = { setWifiAlwaysRequested(context, it) },
                testIntValues = intArrayOf(0))
    }

    @ConnectivityModuleTest // get/setIngressRateLimitInBytesPerSecond was added via module update
    @Test
    fun testInternetNetworkRateLimitInBytesPerSecond() {
        val defaultRate = getIngressRateLimitInBytesPerSecond(context)
        val testRate = 1000L
        setIngressRateLimitInBytesPerSecond(context, testRate)
        assertEquals(testRate, getIngressRateLimitInBytesPerSecond(context))

        setIngressRateLimitInBytesPerSecond(context, defaultRate)
        assertEquals(defaultRate, getIngressRateLimitInBytesPerSecond(context))

        assertFailsWith<IllegalArgumentException>("Expected failure, but setting accepted") {
            setIngressRateLimitInBytesPerSecond(context, -10)
        }
    }
}