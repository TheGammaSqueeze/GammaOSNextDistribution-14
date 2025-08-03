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
package android.tethering.test;

import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.Manifest.permission.TETHER_PRIVILEGED;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_DUN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.TetheringManager.TETHER_ERROR_ENTITLEMENT_UNKNOWN;
import static android.net.TetheringManager.TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.cts.util.CtsTetheringUtils.isAnyIfaceMatch;

import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TetheringInterface;
import android.net.TetheringManager;
import android.net.TetheringManager.OnTetheringEntitlementResultListener;
import android.net.TetheringManager.TetheringInterfaceRegexps;
import android.net.TetheringManager.TetheringRequest;
import android.net.cts.util.CtsNetUtils;
import android.net.cts.util.CtsNetUtils.TestNetworkCallback;
import android.net.cts.util.CtsTetheringUtils;
import android.net.cts.util.CtsTetheringUtils.StartTetheringCallback;
import android.net.cts.util.CtsTetheringUtils.TestTetheringEventCallback;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.ResultReceiver;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.ParcelUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class TetheringManagerTest {

    private Context mContext;

    private ConnectivityManager mCm;
    private TetheringManager mTM;
    private WifiManager mWm;
    private PackageManager mPm;

    private TetherChangeReceiver mTetherChangeReceiver;
    private CtsNetUtils mCtsNetUtils;
    private CtsTetheringUtils mCtsTetheringUtils;

    private static final int DEFAULT_TIMEOUT_MS = 60_000;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTM = (TetheringManager) mContext.getSystemService(Context.TETHERING_SERVICE);
        mWm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mPm = mContext.getPackageManager();
        mCtsNetUtils = new CtsNetUtils(mContext);
        mCtsTetheringUtils = new CtsTetheringUtils(mContext);
        mTetherChangeReceiver = new TetherChangeReceiver();
        final IntentFilter filter = new IntentFilter(
                TetheringManager.ACTION_TETHER_STATE_CHANGED);
        final Intent intent = mContext.registerReceiver(mTetherChangeReceiver, filter);
        if (intent != null) mTetherChangeReceiver.onReceive(null, intent);
    }

    @After
    public void tearDown() throws Exception {
        mCtsTetheringUtils.stopAllTethering();
        mContext.unregisterReceiver(mTetherChangeReceiver);
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        private class TetherState {
            final ArrayList<String> mAvailable;
            final ArrayList<String> mActive;
            final ArrayList<String> mErrored;

            TetherState(Intent intent) {
                mAvailable = intent.getStringArrayListExtra(
                        TetheringManager.EXTRA_AVAILABLE_TETHER);
                mActive = intent.getStringArrayListExtra(
                        TetheringManager.EXTRA_ACTIVE_TETHER);
                mErrored = intent.getStringArrayListExtra(
                        TetheringManager.EXTRA_ERRORED_TETHER);
            }
        }

        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TetheringManager.ACTION_TETHER_STATE_CHANGED)) {
                mResult.add(new TetherState(intent));
            }
        }

        public final LinkedBlockingQueue<TetherState> mResult = new LinkedBlockingQueue<>();

        // Expects that tethering reaches the desired state.
        // - If active is true, expects that tethering is enabled on at least one interface
        //   matching ifaceRegexs.
        // - If active is false, expects that tethering is disabled on all the interfaces matching
        //   ifaceRegexs.
        // Fails if any interface matching ifaceRegexs becomes errored.
        public void expectTethering(final boolean active, final String[] ifaceRegexs) {
            while (true) {
                final TetherState state = pollAndAssertNoError(DEFAULT_TIMEOUT_MS, ifaceRegexs);
                assertNotNull("Did not receive expected state change, active: " + active, state);

                if (isIfaceActive(ifaceRegexs, state) == active) return;
            }
        }

        private TetherState pollAndAssertNoError(final int timeout, final String[] ifaceRegexs) {
            final TetherState state = pollTetherState(timeout);
            assertNoErroredIfaces(state, ifaceRegexs);
            return state;
        }

        private TetherState pollTetherState(final int timeout) {
            try {
                return mResult.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                fail("No result after " + timeout + " ms");
                return null;
            }
        }

        private boolean isIfaceActive(final String[] ifaceRegexs, final TetherState state) {
            return isAnyIfaceMatch(ifaceRegexs, state.mActive);
        }

        private void assertNoErroredIfaces(final TetherState state, final String[] ifaceRegexs) {
            if (state == null || state.mErrored == null) return;

            if (isAnyIfaceMatch(ifaceRegexs, state.mErrored)) {
                fail("Found failed tethering interfaces: " + Arrays.toString(state.mErrored.toArray()));
            }
        }
    }

    @Test
    public void testStartTetheringWithStateChangeBroadcast() throws Exception {
        final TestTetheringEventCallback tetherEventCallback =
                mCtsTetheringUtils.registerTetheringEventCallback();
        try {
            tetherEventCallback.assumeWifiTetheringSupported(mContext);
            tetherEventCallback.expectNoTetheringActive();

            final String[] wifiRegexs = mTM.getTetherableWifiRegexs();
            mCtsTetheringUtils.startWifiTethering(tetherEventCallback);

            mTetherChangeReceiver.expectTethering(true /* active */, wifiRegexs);

            mCtsTetheringUtils.stopWifiTethering(tetherEventCallback);
            mTetherChangeReceiver.expectTethering(false /* active */, wifiRegexs);
        } finally {
            mCtsTetheringUtils.unregisterTetheringEventCallback(tetherEventCallback);
        }

    }

    @Test
    public void testTetheringRequest() {
        final TetheringRequest tr = new TetheringRequest.Builder(TETHERING_WIFI).build();
        assertEquals(TETHERING_WIFI, tr.getTetheringType());
        assertNull(tr.getLocalIpv4Address());
        assertNull(tr.getClientStaticIpv4Address());
        assertFalse(tr.isExemptFromEntitlementCheck());
        assertTrue(tr.getShouldShowEntitlementUi());

        final LinkAddress localAddr = new LinkAddress("192.168.24.5/24");
        final LinkAddress clientAddr = new LinkAddress("192.168.24.100/24");
        final TetheringRequest tr2 = new TetheringRequest.Builder(TETHERING_USB)
                .setStaticIpv4Addresses(localAddr, clientAddr)
                .setExemptFromEntitlementCheck(true)
                .setShouldShowEntitlementUi(false).build();

        assertEquals(localAddr, tr2.getLocalIpv4Address());
        assertEquals(clientAddr, tr2.getClientStaticIpv4Address());
        assertEquals(TETHERING_USB, tr2.getTetheringType());
        assertTrue(tr2.isExemptFromEntitlementCheck());
        assertFalse(tr2.getShouldShowEntitlementUi());
    }

    @Test
    public void testTetheringRequestParcelable() {
        final LinkAddress localAddr = new LinkAddress("192.168.24.5/24");
        final LinkAddress clientAddr = new LinkAddress("192.168.24.100/24");
        final TetheringRequest unparceled = new TetheringRequest.Builder(TETHERING_USB)
                .setStaticIpv4Addresses(localAddr, clientAddr)
                .setExemptFromEntitlementCheck(true)
                .setShouldShowEntitlementUi(false).build();
        final TetheringRequest parceled = ParcelUtils.parcelingRoundTrip(unparceled);
        assertEquals(unparceled.getTetheringType(), parceled.getTetheringType());
        assertEquals(unparceled.getConnectivityScope(), parceled.getConnectivityScope());
        assertEquals(unparceled.getLocalIpv4Address(), parceled.getLocalIpv4Address());
        assertEquals(unparceled.getClientStaticIpv4Address(),
                parceled.getClientStaticIpv4Address());
        assertEquals(unparceled.isExemptFromEntitlementCheck(),
                parceled.isExemptFromEntitlementCheck());
        assertEquals(unparceled.getShouldShowEntitlementUi(),
                parceled.getShouldShowEntitlementUi());
    }

    @Test
    public void testRegisterTetheringEventCallback() throws Exception {
        final TestTetheringEventCallback tetherEventCallback =
                mCtsTetheringUtils.registerTetheringEventCallback();

        try {
            tetherEventCallback.assumeWifiTetheringSupported(mContext);
            tetherEventCallback.expectNoTetheringActive();

            final TetheringInterface tetheredIface =
                    mCtsTetheringUtils.startWifiTethering(tetherEventCallback);

            assertNotNull(tetheredIface);
            final String wifiTetheringIface = tetheredIface.getInterface();

            mCtsTetheringUtils.stopWifiTethering(tetherEventCallback);

            try {
                final int ret = runAsShell(TETHER_PRIVILEGED, () -> mTM.tether(wifiTetheringIface));
                // There is no guarantee that the wifi interface will be available after disabling
                // the hotspot, so don't fail the test if the call to tether() fails.
                if (ret == TETHER_ERROR_NO_ERROR) {
                    // If calling #tether successful, there is a callback to tell the result of
                    // tethering setup.
                    tetherEventCallback.expectErrorOrTethered(
                            new TetheringInterface(TETHERING_WIFI, wifiTetheringIface));
                }
            } finally {
                runAsShell(TETHER_PRIVILEGED, () -> mTM.untether(wifiTetheringIface));
            }
        } finally {
            mCtsTetheringUtils.unregisterTetheringEventCallback(tetherEventCallback);
        }
    }

    @Test
    public void testGetTetherableInterfaceRegexps() {
        final TestTetheringEventCallback tetherEventCallback =
                mCtsTetheringUtils.registerTetheringEventCallback();
        tetherEventCallback.assumeTetheringSupported();

        final TetheringInterfaceRegexps tetherableRegexs =
                tetherEventCallback.getTetheringInterfaceRegexps();
        final List<String> wifiRegexs = tetherableRegexs.getTetherableWifiRegexs();
        final List<String> usbRegexs = tetherableRegexs.getTetherableUsbRegexs();
        final List<String> btRegexs = tetherableRegexs.getTetherableBluetoothRegexs();

        assertEquals(wifiRegexs, Arrays.asList(mTM.getTetherableWifiRegexs()));
        assertEquals(usbRegexs, Arrays.asList(mTM.getTetherableUsbRegexs()));
        assertEquals(btRegexs, Arrays.asList(mTM.getTetherableBluetoothRegexs()));

        //Verify that any regex name should only contain in one array.
        wifiRegexs.forEach(s -> assertFalse(usbRegexs.contains(s)));
        wifiRegexs.forEach(s -> assertFalse(btRegexs.contains(s)));
        usbRegexs.forEach(s -> assertFalse(btRegexs.contains(s)));

        mCtsTetheringUtils.unregisterTetheringEventCallback(tetherEventCallback);
    }

    @Test
    public void testStopAllTethering() throws Exception {
        final TestTetheringEventCallback tetherEventCallback =
                mCtsTetheringUtils.registerTetheringEventCallback();
        try {
            tetherEventCallback.assumeWifiTetheringSupported(mContext);

            // TODO: start ethernet tethering here when TetheringManagerTest is moved to
            // TetheringIntegrationTest.

            mCtsTetheringUtils.startWifiTethering(tetherEventCallback);

            mCtsTetheringUtils.stopAllTethering();
            tetherEventCallback.expectNoTetheringActive();
        } finally {
            mCtsTetheringUtils.unregisterTetheringEventCallback(tetherEventCallback);
        }
    }

    @Test
    public void testEnableTetheringPermission() throws Exception {
        final StartTetheringCallback startTetheringCallback = new StartTetheringCallback();
        mTM.startTethering(new TetheringRequest.Builder(TETHERING_WIFI).build(),
                c -> c.run() /* executor */, startTetheringCallback);
        startTetheringCallback.expectTetheringFailed(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
    }

    private class EntitlementResultListener implements OnTetheringEntitlementResultListener {
        private final CompletableFuture<Integer> future = new CompletableFuture<>();

        @Override
        public void onTetheringEntitlementResult(int result) {
            future.complete(result);
        }

        public int get(long timeout, TimeUnit unit) throws Exception {
            return future.get(timeout, unit);
        }

    }

    private void assertEntitlementResult(final Consumer<EntitlementResultListener> functor,
            final int expect) throws Exception {
        runAsShell(TETHER_PRIVILEGED, () -> {
            final EntitlementResultListener listener = new EntitlementResultListener();
            functor.accept(listener);

            assertEquals(expect, listener.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        });
    }

    private boolean isTetheringSupported() {
        return runAsShell(TETHER_PRIVILEGED, () -> mTM.isTetheringSupported());
    }

    @Test
    public void testRequestLatestEntitlementResult() throws Exception {
        assumeTrue(isTetheringSupported());
        assumeTrue(mPm.hasSystemFeature(FEATURE_TELEPHONY));
        // Verify that requestLatestTetheringEntitlementResult() can get entitlement
        // result(TETHER_ERROR_ENTITLEMENT_UNKNOWN due to invalid downstream type) via listener.
        assertEntitlementResult(listener -> mTM.requestLatestTetheringEntitlementResult(
                TETHERING_WIFI_P2P, false, c -> c.run(), listener),
                TETHER_ERROR_ENTITLEMENT_UNKNOWN);

        // Verify that requestLatestTetheringEntitlementResult() can get entitlement
        // result(TETHER_ERROR_ENTITLEMENT_UNKNOWN due to invalid downstream type) via receiver.
        assertEntitlementResult(listener -> mTM.requestLatestTetheringEntitlementResult(
                TETHERING_WIFI_P2P,
                new ResultReceiver(null /* handler */) {
                    @Override
                    public void onReceiveResult(int resultCode, Bundle resultData) {
                        listener.onTetheringEntitlementResult(resultCode);
                    }
                }, false),
                TETHER_ERROR_ENTITLEMENT_UNKNOWN);

        // Do not request TETHERING_WIFI entitlement result if TETHERING_WIFI is not available.
        assumeTrue(mTM.getTetherableWifiRegexs().length > 0);

        // Verify that null listener will cause IllegalArgumentException.
        try {
            mTM.requestLatestTetheringEntitlementResult(
                    TETHERING_WIFI, false, c -> c.run(), null);
        } catch (IllegalArgumentException expect) { }

        // Override carrier config to ignore entitlement check.
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL, false);
        overrideCarrierConfig(bundle);

        // Verify that requestLatestTetheringEntitlementResult() can get entitlement
        // result TETHER_ERROR_NO_ERROR due to provisioning bypassed.
        assertEntitlementResult(listener -> mTM.requestLatestTetheringEntitlementResult(
                TETHERING_WIFI, false, c -> c.run(), listener), TETHER_ERROR_NO_ERROR);

        // Reset carrier config.
        overrideCarrierConfig(null);
    }

    private void overrideCarrierConfig(PersistableBundle bundle) {
        final CarrierConfigManager configManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        final int subId = SubscriptionManager.getDefaultSubscriptionId();
        runAsShell(MODIFY_PHONE_STATE, () -> configManager.overrideConfig(subId, bundle));
    }

    private boolean isTetheringApnRequired() {
        final TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        return runAsShell(MODIFY_PHONE_STATE, () -> tm.isTetheringApnRequired());

    }

    @Test
    public void testTetheringUpstream() throws Exception {
        assumeTrue(mPm.hasSystemFeature(FEATURE_TELEPHONY));
        final TestTetheringEventCallback tetherEventCallback =
                mCtsTetheringUtils.registerTetheringEventCallback();

        boolean previousWifiEnabledState = false;

        try {
            tetherEventCallback.assumeWifiTetheringSupported(mContext);
            tetherEventCallback.expectNoTetheringActive();

            previousWifiEnabledState = mWm.isWifiEnabled();
            if (previousWifiEnabledState) {
                mCtsNetUtils.ensureWifiDisconnected(null);
            }

            final TestNetworkCallback networkCallback = new TestNetworkCallback();
            Network activeNetwork = null;
            try {
                mCm.registerDefaultNetworkCallback(networkCallback);
                activeNetwork = networkCallback.waitForAvailable();
            } finally {
                mCm.unregisterNetworkCallback(networkCallback);
            }

            assertNotNull("No active network. Please ensure the device has working mobile data.",
                    activeNetwork);
            final NetworkCapabilities activeNetCap = mCm.getNetworkCapabilities(activeNetwork);

            // If active nework is ETHERNET, tethering may not use cell network as upstream.
            assumeFalse(activeNetCap.hasTransport(TRANSPORT_ETHERNET));

            assertTrue(activeNetCap.hasTransport(TRANSPORT_CELLULAR));

            mCtsTetheringUtils.startWifiTethering(tetherEventCallback);

            final int expectedCap = isTetheringApnRequired()
                    ? NET_CAPABILITY_DUN : NET_CAPABILITY_INTERNET;
            final Network network = tetherEventCallback.getCurrentValidUpstream();
            final NetworkCapabilities netCap = mCm.getNetworkCapabilities(network);
            assertTrue(netCap.hasTransport(TRANSPORT_CELLULAR));
            assertTrue(netCap.hasCapability(expectedCap));

            mCtsTetheringUtils.stopWifiTethering(tetherEventCallback);
        } finally {
            mCtsTetheringUtils.unregisterTetheringEventCallback(tetherEventCallback);
            if (previousWifiEnabledState) {
                mCtsNetUtils.connectToWifi();
            }
        }
    }
}
