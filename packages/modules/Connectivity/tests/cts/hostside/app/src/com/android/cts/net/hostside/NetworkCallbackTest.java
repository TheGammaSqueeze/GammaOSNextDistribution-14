/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.net.hostside;

import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP_SLEEPING;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED;

import static com.android.cts.net.hostside.NetworkPolicyTestUtils.canChangeActiveNetworkMeteredness;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.getActiveNetworkCapabilities;
import static com.android.cts.net.hostside.NetworkPolicyTestUtils.setRestrictBackground;
import static com.android.cts.net.hostside.Property.BATTERY_SAVER_MODE;
import static com.android.cts.net.hostside.Property.DATA_SAVER_MODE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.cts.util.CtsNetUtils;
import android.os.SystemClock;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NetworkCallbackTest extends AbstractRestrictBackgroundNetworkTestCase {
    private Network mNetwork;
    private final TestNetworkCallback mTestNetworkCallback = new TestNetworkCallback();
    private CtsNetUtils mCtsNetUtils;
    private static final String GOOGLE_PRIVATE_DNS_SERVER = "dns.google";

    @Rule
    public final MeterednessConfigurationRule mMeterednessConfiguration
            = new MeterednessConfigurationRule();

    enum CallbackState {
        NONE,
        AVAILABLE,
        LOST,
        BLOCKED_STATUS,
        CAPABILITIES
    }

    private static class CallbackInfo {
        public final CallbackState state;
        public final Network network;
        public final Object arg;

        CallbackInfo(CallbackState s, Network n, Object o) {
            state = s; network = n; arg = o;
        }

        public String toString() {
            return String.format("%s (%s) (%s)", state, network, arg);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CallbackInfo)) return false;
            // Ignore timeMs, since it's unpredictable.
            final CallbackInfo other = (CallbackInfo) o;
            return (state == other.state) && Objects.equals(network, other.network)
                    && Objects.equals(arg, other.arg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, network, arg);
        }
    }

    private class TestNetworkCallback extends INetworkCallback.Stub {
        private static final int TEST_CONNECT_TIMEOUT_MS = 30_000;
        private static final int TEST_CALLBACK_TIMEOUT_MS = 5_000;

        private final LinkedBlockingQueue<CallbackInfo> mCallbacks = new LinkedBlockingQueue<>();

        protected void setLastCallback(CallbackState state, Network network, Object o) {
            mCallbacks.offer(new CallbackInfo(state, network, o));
        }

        CallbackInfo nextCallback(int timeoutMs) {
            CallbackInfo cb = null;
            try {
                cb = mCallbacks.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            if (cb == null) {
                fail("Did not receive callback after " + timeoutMs + "ms");
            }
            return cb;
        }

        CallbackInfo expectCallback(CallbackState state, Network expectedNetwork, Object o) {
            final CallbackInfo expected = new CallbackInfo(state, expectedNetwork, o);
            final CallbackInfo actual = nextCallback(TEST_CALLBACK_TIMEOUT_MS);
            assertEquals("Unexpected callback:", expected, actual);
            return actual;
        }

        @Override
        public void onAvailable(Network network) {
            setLastCallback(CallbackState.AVAILABLE, network, null);
        }

        @Override
        public void onLost(Network network) {
            setLastCallback(CallbackState.LOST, network, null);
        }

        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            setLastCallback(CallbackState.BLOCKED_STATUS, network, blocked);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities cap) {
            setLastCallback(CallbackState.CAPABILITIES, network, cap);
        }

        public Network expectAvailableCallbackAndGetNetwork() {
            final CallbackInfo cb = nextCallback(TEST_CONNECT_TIMEOUT_MS);
            if (cb.state != CallbackState.AVAILABLE) {
                fail("Network is not available. Instead obtained the following callback :" + cb);
            }
            return cb.network;
        }

        public void drainAndWaitForIdle() {
            try {
                do {
                    mCallbacks.drainTo(new ArrayList<>());
                } while (mCallbacks.poll(TEST_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS) != null);
            } catch (InterruptedException ie) {
                Log.e(TAG, "Interrupted while draining callback queue", ie);
                Thread.currentThread().interrupt();
            }
        }

        public void expectBlockedStatusCallback(Network expectedNetwork, boolean expectBlocked) {
            expectCallback(CallbackState.BLOCKED_STATUS, expectedNetwork, expectBlocked);
        }

        public void expectBlockedStatusCallbackEventually(Network expectedNetwork,
                boolean expectBlocked) {
            final long deadline = System.currentTimeMillis() + TEST_CALLBACK_TIMEOUT_MS;
            do {
                final CallbackInfo cb = nextCallback((int) (deadline - System.currentTimeMillis()));
                if (cb.state == CallbackState.BLOCKED_STATUS
                        && cb.network.equals(expectedNetwork)) {
                    assertEquals(expectBlocked, cb.arg);
                    return;
                }
            } while (System.currentTimeMillis() <= deadline);
            fail("Didn't receive onBlockedStatusChanged()");
        }

        public void expectCapabilitiesCallbackEventually(Network expectedNetwork, boolean hasCap,
                int cap) {
            final long deadline = System.currentTimeMillis() + TEST_CALLBACK_TIMEOUT_MS;
            do {
                final CallbackInfo cb = nextCallback((int) (deadline - System.currentTimeMillis()));
                if (cb.state != CallbackState.CAPABILITIES
                        || !expectedNetwork.equals(cb.network)
                        || (hasCap != ((NetworkCapabilities) cb.arg).hasCapability(cap))) {
                    Log.i("NetworkCallbackTest#expectCapabilitiesCallback",
                            "Ignoring non-matching callback : " + cb);
                    continue;
                }
                // Found a match, return
                return;
            } while (System.currentTimeMillis() <= deadline);
            fail("Didn't receive the expected callback to onCapabilitiesChanged(). Check the "
                    + "log for a list of received callbacks, if any.");
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(canChangeActiveNetworkMeteredness());

        registerBroadcastReceiver();

        removeRestrictBackgroundWhitelist(mUid);
        removeRestrictBackgroundBlacklist(mUid);
        assertRestrictBackgroundChangedReceived(0);

        // Initial state
        setBatterySaverMode(false);
        setRestrictBackground(false);
        setAppIdle(false);

        // Get transports of the active network, this has to be done before changing meteredness,
        // since wifi will be disconnected when changing from non-metered to metered.
        final NetworkCapabilities networkCapabilities = getActiveNetworkCapabilities();

        // Mark network as metered.
        mMeterednessConfiguration.configureNetworkMeteredness(true);

        // Register callback, copy the capabilities from the active network to expect the "original"
        // network before disconnecting, but null out some fields to prevent over-specified.
        registerNetworkCallback(new NetworkRequest.Builder()
                .setCapabilities(networkCapabilities.setTransportInfo(null))
                .removeCapability(NET_CAPABILITY_NOT_METERED)
                .setSignalStrength(SIGNAL_STRENGTH_UNSPECIFIED).build(), mTestNetworkCallback);
        // Wait for onAvailable() callback to ensure network is available before the test
        // and store the default network.
        mNetwork = mTestNetworkCallback.expectAvailableCallbackAndGetNetwork();
        // Check that the network is metered.
        mTestNetworkCallback.expectCapabilitiesCallbackEventually(mNetwork,
                false /* hasCapability */, NET_CAPABILITY_NOT_METERED);
        mTestNetworkCallback.drainAndWaitForIdle();

        // Before Android T, DNS queries over private DNS should be but are not restricted by Power
        // Saver or Data Saver. The issue is fixed in mainline update and apps can no longer request
        // DNS queries when its network is restricted by Power Saver. The fix takes effect backwards
        // starting from Android T. But for Data Saver, the fix is not backward compatible since
        // there are some platform changes involved. It is only available on devices that a specific
        // trunk flag is enabled.
        //
        // This test can not only verify that the network traffic from apps is blocked at the right
        // time, but also verify whether it is correctly blocked at the DNS stage, or at a later
        // socket connection stage.
        if (SdkLevel.isAtLeastT()) {
            // Enable private DNS
            mCtsNetUtils = new CtsNetUtils(mContext);
            mCtsNetUtils.storePrivateDnsSetting();
            mCtsNetUtils.setPrivateDnsStrictMode(GOOGLE_PRIVATE_DNS_SERVER);
            mCtsNetUtils.awaitPrivateDnsSetting(
                    "NetworkCallbackTest wait private DNS setting timeout", mNetwork,
                    GOOGLE_PRIVATE_DNS_SERVER, true);
        }
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        setRestrictBackground(false);
        setBatterySaverMode(false);
        unregisterNetworkCallback();

        if (SdkLevel.isAtLeastT() && (mCtsNetUtils != null)) {
            mCtsNetUtils.restorePrivateDnsSetting();
        }
    }

    @RequiredProperties({DATA_SAVER_MODE})
    @Test
    public void testOnBlockedStatusChanged_dataSaver() throws Exception {
        try {
            // Enable restrict background
            setRestrictBackground(true);
            // TODO: Verify expectedUnavailableError when aconfig support mainline.
            // (see go/aconfig-in-mainline-problems)
            assertBackgroundNetworkAccess(false);
            assertNetworkAccessBlockedByBpf(true, mUid, true /* metered */);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, true);

            // Add to whitelist
            addRestrictBackgroundWhitelist(mUid);
            assertBackgroundNetworkAccess(true);
            assertNetworkAccessBlockedByBpf(false, mUid, true /* metered */);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, false);

            // Remove from whitelist
            removeRestrictBackgroundWhitelist(mUid);
            // TODO: Verify expectedUnavailableError when aconfig support mainline.
            assertBackgroundNetworkAccess(false);
            assertNetworkAccessBlockedByBpf(true, mUid, true /* metered */);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, true);
        } finally {
            mMeterednessConfiguration.resetNetworkMeteredness();
        }

        // Set to non-metered network
        mMeterednessConfiguration.configureNetworkMeteredness(false);
        mTestNetworkCallback.expectCapabilitiesCallbackEventually(mNetwork,
                true /* hasCapability */, NET_CAPABILITY_NOT_METERED);
        try {
            assertBackgroundNetworkAccess(true);
            assertNetworkAccessBlockedByBpf(false, mUid, false /* metered */);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, false);

            // Disable restrict background, should not trigger callback
            setRestrictBackground(false);
            assertBackgroundNetworkAccess(true);
            assertNetworkAccessBlockedByBpf(false, mUid, false /* metered */);
        } finally {
            mMeterednessConfiguration.resetNetworkMeteredness();
        }
    }

    @RequiredProperties({BATTERY_SAVER_MODE})
    @Test
    public void testOnBlockedStatusChanged_powerSaver() throws Exception {
        try {
            // Enable Power Saver
            setBatterySaverMode(true);
            if (SdkLevel.isAtLeastT()) {
                assertProcessStateBelow(PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
                assertNetworkAccess(false, "java.net.UnknownHostException");
            } else {
                assertBackgroundNetworkAccess(false);
            }
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, true);
            assertNetworkAccessBlockedByBpf(true, mUid, true /* metered */);

            // Disable Power Saver
            setBatterySaverMode(false);
            assertBackgroundNetworkAccess(true);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, false);
            assertNetworkAccessBlockedByBpf(false, mUid, true /* metered */);
        } finally {
            mMeterednessConfiguration.resetNetworkMeteredness();
        }

        // Set to non-metered network
        mMeterednessConfiguration.configureNetworkMeteredness(false);
        mTestNetworkCallback.expectCapabilitiesCallbackEventually(mNetwork,
                true /* hasCapability */, NET_CAPABILITY_NOT_METERED);
        try {
            // Enable Power Saver
            setBatterySaverMode(true);
            if (SdkLevel.isAtLeastT()) {
                assertProcessStateBelow(PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
                assertNetworkAccess(false, "java.net.UnknownHostException");
            } else {
                assertBackgroundNetworkAccess(false);
            }
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, true);
            assertNetworkAccessBlockedByBpf(true, mUid, false /* metered */);

            // Disable Power Saver
            setBatterySaverMode(false);
            assertBackgroundNetworkAccess(true);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, false);
            assertNetworkAccessBlockedByBpf(false, mUid, false /* metered */);
        } finally {
            mMeterednessConfiguration.resetNetworkMeteredness();
        }
    }

    @Test
    public void testOnBlockedStatusChanged_default() throws Exception {
        assumeTrue("Feature not enabled", isNetworkBlockedForTopSleepingAndAbove());

        try {
            assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
            assertNetworkAccess(false, null);
            assertNetworkAccessBlockedByBpf(true, mUid, true /* metered */);

            launchActivity();
            assertTopState();
            assertNetworkAccess(true, null);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, false);
            assertNetworkAccessBlockedByBpf(false, mUid, true /* metered */);

            finishActivity();
            assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
            SystemClock.sleep(PROCESS_STATE_TRANSITION_DELAY_MS);
            assertNetworkAccess(false, null);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, true);
            assertNetworkAccessBlockedByBpf(true, mUid, true /* metered */);

        } finally {
            mMeterednessConfiguration.resetNetworkMeteredness();
        }

        // Set to non-metered network
        mMeterednessConfiguration.configureNetworkMeteredness(false);
        mTestNetworkCallback.expectCapabilitiesCallbackEventually(mNetwork,
                true /* hasCapability */, NET_CAPABILITY_NOT_METERED);
        try {
            assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
            assertNetworkAccess(false, null);
            assertNetworkAccessBlockedByBpf(true, mUid, false /* metered */);

            launchActivity();
            assertTopState();
            assertNetworkAccess(true, null);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, false);
            assertNetworkAccessBlockedByBpf(false, mUid, false /* metered */);

            finishActivity();
            assertProcessStateBelow(PROCESS_STATE_TOP_SLEEPING);
            SystemClock.sleep(PROCESS_STATE_TRANSITION_DELAY_MS);
            assertNetworkAccess(false, null);
            mTestNetworkCallback.expectBlockedStatusCallbackEventually(mNetwork, true);
            assertNetworkAccessBlockedByBpf(true, mUid, false /* metered */);
        } finally {
            mMeterednessConfiguration.resetNetworkMeteredness();
        }
    }

    // TODO: 1. test against VPN lockdown.
    //       2. test against multiple networks.
}
