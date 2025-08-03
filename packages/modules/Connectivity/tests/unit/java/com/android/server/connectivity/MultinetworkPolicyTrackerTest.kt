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

package com.android.server.connectivity

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager.MULTIPATH_PREFERENCE_HANDOVER
import android.net.ConnectivityManager.MULTIPATH_PREFERENCE_PERFORMANCE
import android.net.ConnectivityManager.MULTIPATH_PREFERENCE_RELIABILITY
import android.net.ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI
import android.net.ConnectivitySettingsManager.NETWORK_METERED_MULTIPATH_PREFERENCE
import android.os.Build
import android.os.Handler
import android.os.test.TestLooper
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.test.mock.MockContentResolver
import androidx.test.filters.SmallTest
import com.android.connectivity.resources.R
import com.android.internal.util.test.FakeSettingsProvider
import com.android.modules.utils.build.SdkLevel
import com.android.server.connectivity.MultinetworkPolicyTracker.ActiveDataSubscriptionIdListener
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.any
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

const val HANDLER_TIMEOUT_MS = 400

/**
 * Tests for [MultinetworkPolicyTracker].
 *
 * Build, install and run with:
 * atest FrameworksNetTest:MultinetworkPolicyTrackerTest
 */
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class MultinetworkPolicyTrackerTest {
    private val resources = mock(Resources::class.java).also {
        doReturn(0).`when`(it).getInteger(R.integer.config_networkAvoidBadWifi)
        doReturn(0).`when`(it).getInteger(R.integer.config_activelyPreferBadWifi)
    }
    private val telephonyManager = mock(TelephonyManager::class.java)
    private val subscriptionManager = mock(SubscriptionManager::class.java).also {
        doReturn(null).`when`(it).getActiveSubscriptionInfo(anyInt())
    }
    private val resolver = MockContentResolver().apply {
        addProvider(Settings.AUTHORITY, FakeSettingsProvider()) }
    private val context = mock(Context::class.java).also {
        doReturn(Context.TELEPHONY_SERVICE).`when`(it)
                .getSystemServiceName(TelephonyManager::class.java)
        doReturn(telephonyManager).`when`(it).getSystemService(Context.TELEPHONY_SERVICE)
        if (it.getSystemService(TelephonyManager::class.java) == null) {
            // Test is using mockito extended
            doCallRealMethod().`when`(it).getSystemService(TelephonyManager::class.java)
        }
        doReturn(subscriptionManager).`when`(it)
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
        doReturn(resolver).`when`(it).contentResolver
        doReturn(resources).`when`(it).resources
        doReturn(it).`when`(it).createConfigurationContext(any())
        Settings.Global.putString(resolver, NETWORK_AVOID_BAD_WIFI, "1")
        ConnectivityResources.setResourcesContextForTest(it)
    }
    private val csLooper = TestLooper()
    private val handler = Handler(csLooper.looper)
    private val trackerDependencies = MultinetworkPolicyTrackerTestDependencies(resources)
    private val tracker = MultinetworkPolicyTracker(context, handler,
            null /* avoidBadWifiCallback */, trackerDependencies)

    private fun assertMultipathPreference(preference: Int) {
        Settings.Global.putString(resolver, NETWORK_METERED_MULTIPATH_PREFERENCE,
                preference.toString())
        tracker.updateMeteredMultipathPreference()
        assertEquals(preference, tracker.meteredMultipathPreference)
    }

    @Before
    fun setUp() {
        tracker.start()
    }

    @After
    fun tearDown() {
        ConnectivityResources.setResourcesContextForTest(null)
    }

    @Test
    fun testUpdateMeteredMultipathPreference() {
        assertMultipathPreference(MULTIPATH_PREFERENCE_HANDOVER)
        assertMultipathPreference(MULTIPATH_PREFERENCE_RELIABILITY)
        assertMultipathPreference(MULTIPATH_PREFERENCE_PERFORMANCE)
    }

    @Test
    fun testUpdateAvoidBadWifi() {
        doReturn(0).`when`(resources).getInteger(R.integer.config_activelyPreferBadWifi)
        Settings.Global.putString(resolver, NETWORK_AVOID_BAD_WIFI, "0")
        assertTrue(tracker.updateAvoidBadWifi())
        assertFalse(tracker.avoidBadWifi)

        doReturn(1).`when`(resources).getInteger(R.integer.config_networkAvoidBadWifi)
        assertTrue(tracker.updateAvoidBadWifi())
        assertTrue(tracker.avoidBadWifi)

        if (SdkLevel.isAtLeastU()) {
            // On U+, the system always prefers bad wifi.
            assertTrue(tracker.activelyPreferBadWifi)
        } else {
            assertFalse(tracker.activelyPreferBadWifi)
        }

        doReturn(1).`when`(resources).getInteger(R.integer.config_activelyPreferBadWifi)
        if (SdkLevel.isAtLeastU()) {
            // On U+, this didn't change the setting
            assertFalse(tracker.updateAvoidBadWifi())
        } else {
            // On T-, this must have changed the setting
            assertTrue(tracker.updateAvoidBadWifi())
        }
        // In all cases, now the system actively prefers bad wifi
        assertTrue(tracker.activelyPreferBadWifi)

        // Remaining tests are only useful on T-, which support both the old and new mode.
        if (SdkLevel.isAtLeastU()) return

        doReturn(0).`when`(resources).getInteger(R.integer.config_activelyPreferBadWifi)
        assertTrue(tracker.updateAvoidBadWifi())
        assertFalse(tracker.activelyPreferBadWifi)

        // Simulate update of device config
        trackerDependencies.putConfigActivelyPreferBadWifi(1)
        csLooper.dispatchAll()
        assertTrue(tracker.activelyPreferBadWifi)
    }

    @Test
    fun testOnActiveDataSubscriptionIdChanged() {
        val testSubId = 1000
        val subscriptionInfo = SubscriptionInfo(testSubId, ""/* iccId */, 1/* iccId */,
                "TMO"/* displayName */, "TMO"/* carrierName */, 1/* nameSource */, 1/* iconTint */,
                "123"/* number */, 1/* roaming */, null/* icon */, "310"/* mcc */, "210"/* mnc */,
                ""/* countryIso */, false/* isEmbedded */, null/* nativeAccessRules */,
                "1"/* cardString */)
        doReturn(subscriptionInfo).`when`(subscriptionManager).getActiveSubscriptionInfo(testSubId)

        // Modify avoidBadWifi and meteredMultipathPreference settings value and local variables in
        // MultinetworkPolicyTracker should be also updated after subId changed.
        Settings.Global.putString(resolver, NETWORK_AVOID_BAD_WIFI, "0")
        Settings.Global.putString(resolver, NETWORK_METERED_MULTIPATH_PREFERENCE,
                MULTIPATH_PREFERENCE_PERFORMANCE.toString())

        assertTrue(tracker.avoidBadWifi)

        val listenerCaptor = ArgumentCaptor.forClass(
                ActiveDataSubscriptionIdListener::class.java)
        verify(telephonyManager, times(1))
                .registerTelephonyCallback(any(), listenerCaptor.capture())
        val listener = listenerCaptor.value
        listener.onActiveDataSubscriptionIdChanged(testSubId)

        // Check if avoidBadWifi and meteredMultipathPreference values have been updated.
        assertFalse(tracker.avoidBadWifi)
        assertEquals(MULTIPATH_PREFERENCE_PERFORMANCE, tracker.meteredMultipathPreference)
    }
}
