/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.jobscheduler.cts;

import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.Manifest;
import android.annotation.NonNull;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicyManager;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkingHelper implements AutoCloseable {
    private static final String TAG = "JsNetworkingUtils";

    private static final String RESTRICT_BACKGROUND_GET_CMD =
            "cmd netpolicy get restrict-background";
    private static final String RESTRICT_BACKGROUND_ON_CMD =
            "cmd netpolicy set restrict-background true";
    private static final String RESTRICT_BACKGROUND_OFF_CMD =
            "cmd netpolicy set restrict-background false";

    private final Context mContext;
    private final Instrumentation mInstrumentation;

    private final ConnectivityManager mConnectivityManager;
    private final WifiManager mWifiManager;

    /** Whether the device running these tests supports WiFi. */
    private final boolean mHasWifi;
    /** Whether the device running these tests supports ethernet. */
    private final boolean mHasEthernet;
    /** Whether the device running these tests supports telephony. */
    private final boolean mHasTelephony;

    private final boolean mInitialAirplaneModeState;
    private final boolean mInitialDataSaverState;
    private final String mInitialLocationMode;
    private final boolean mInitialWiFiState;
    private String mInitialWiFiMeteredState;
    private String mInitialWiFiSSID;

    NetworkingHelper(@NonNull Instrumentation instrumentation, @NonNull Context context)
            throws Exception {
        mContext = context;
        mInstrumentation = instrumentation;

        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mWifiManager = context.getSystemService(WifiManager.class);

        PackageManager packageManager = mContext.getPackageManager();
        mHasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI);
        mHasEthernet = packageManager.hasSystemFeature(PackageManager.FEATURE_ETHERNET);
        mHasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        mInitialAirplaneModeState = isAirplaneModeOn();
        mInitialDataSaverState = isDataSaverEnabled();
        mInitialLocationMode = Settings.Secure.getString(
                mContext.getContentResolver(), Settings.Secure.LOCATION_MODE);
        mInitialWiFiState = mHasWifi && isWifiEnabled();

        ensureSavedWifiNetwork();
    }

    /** Ensures that the device has a wifi network saved if it has the wifi feature. */
    private void ensureSavedWifiNetwork() throws Exception {
        if (!mHasWifi) {
            return;
        }
        final List<WifiConfiguration> savedNetworks =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mWifiManager, WifiManager::getConfiguredNetworks);
        assertFalse("Need at least one saved wifi network", savedNetworks.isEmpty());

        setWifiState(true);
        if (mInitialWiFiSSID == null) {
            mInitialWiFiSSID = getWifiSSID();
            mInitialWiFiMeteredState = getWifiMeteredStatus(mInitialWiFiSSID);
        }
    }

    // Returns "true", "false", or "none".
    private String getWifiMeteredStatus(String ssid) {
        // Interestingly giving the SSID as an argument to list wifi-networks
        // only works iff the network in question has the "false" policy.
        // Also unfortunately runShellCommand does not pass the command to the interpreter
        // so it's not possible to | grep the ssid.
        final String command = "cmd netpolicy list wifi-networks";
        final String policyString = SystemUtil.runShellCommand(command);

        final Matcher m = Pattern.compile(ssid + ";(true|false|none)",
                Pattern.MULTILINE | Pattern.UNIX_LINES).matcher(policyString);
        if (!m.find()) {
            fail("Unexpected format from cmd netpolicy (when looking for " + ssid + "): "
                    + policyString);
        }
        return m.group(1);
    }

    @NonNull
    private String getWifiSSID() throws Exception {
        // Location needs to be enabled to get the WiFi information.
        setLocationMode(String.valueOf(Settings.Secure.LOCATION_MODE_ON));
        final AtomicReference<String> ssid = new AtomicReference<>();
        SystemUtil.runWithShellPermissionIdentity(
                () -> ssid.set(mWifiManager.getConnectionInfo().getSSID()),
                Manifest.permission.ACCESS_FINE_LOCATION);
        return unquoteSSID(ssid.get());
    }

    boolean hasCellularNetwork() throws Exception {
        if (!mHasTelephony) {
            Log.d(TAG, "Telephony feature not found");
            return false;
        }

        if (isAirplaneModeOn()) {
            // Shortcut. When mHasTelephony=true, setAirplaneMode makes sure the cellular network
            // is connected before returning. Thus, if we turn airplane mode off and the wait
            // succeeds, we can assume there's a cellular network.
            setAirplaneMode(false);
            return true;
        }

        Network[] networks = mConnectivityManager.getAllNetworks();
        for (Network network : networks) {
            if (mConnectivityManager.getNetworkCapabilities(network)
                    .hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true;
            }
        }

        Log.d(TAG, "Cellular network not found");
        return false;
    }

    boolean hasEthernetConnection() {
        if (!mHasEthernet) return false;
        Network[] networks = mConnectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities networkCapabilities =
                    mConnectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities != null
                    && networkCapabilities.hasTransport(TRANSPORT_ETHERNET)) {
                return true;
            }
        }
        return false;
    }

    boolean hasWifiFeature() {
        return mHasWifi;
    }

    boolean isAirplaneModeOn() throws Exception {
        final String output = SystemUtil.runShellCommand(mInstrumentation,
                "cmd connectivity airplane-mode").trim();
        return "enabled".equals(output);
    }

    boolean isDataSaverEnabled() throws Exception {
        return SystemUtil
                .runShellCommand(mInstrumentation, RESTRICT_BACKGROUND_GET_CMD)
                .contains("enabled");
    }

    boolean isWiFiConnected() {
        if (!mWifiManager.isWifiEnabled()) {
            return false;
        }
        final Network network = mConnectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        final NetworkCapabilities networkCapabilities =
                mConnectivityManager.getNetworkCapabilities(network);
        return networkCapabilities != null
                && networkCapabilities.hasTransport(TRANSPORT_WIFI)
                && networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED);
    }

    boolean isWifiEnabled() {
        return mWifiManager.isWifiEnabled();
    }

    /**
     * Tries to set all network statuses to {@code enabled}.
     * However, this does not support ethernet connections.
     * Confirm that {@link #hasEthernetConnection()} returns false before relying on this.
     */
    void setAllNetworksEnabled(boolean enabled) throws Exception {
        if (mHasWifi) {
            setWifiState(enabled);
        }
        setAirplaneMode(!enabled);
    }

    void setAirplaneMode(boolean on) throws Exception {
        if (isAirplaneModeOn() == on) {
            return;
        }
        final CallbackAsserter airplaneModeBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        SystemUtil.runShellCommand(mInstrumentation,
                "cmd connectivity airplane-mode " + (on ? "enable" : "disable"));
        airplaneModeBroadcastAsserter.assertCalled("Didn't get airplane mode changed broadcast",
                15 /* 15 seconds */);
        if (!on && mHasWifi) {
            // Try to trigger some network connection.
            setWifiState(true);
        }
        waitUntil("Airplane mode didn't change to " + (on ? " on" : " off"), 60 /* seconds */,
                () -> {
                    // Airplane mode only affects the cellular network. If the device doesn't
                    // support cellular, then we can only check that the airplane mode toggle is on.
                    if (!mHasTelephony) {
                        return on == isAirplaneModeOn();
                    }
                    if (on) {
                        Network[] networks = mConnectivityManager.getAllNetworks();
                        for (Network network : networks) {
                            NetworkCapabilities networkCapabilities =
                                    mConnectivityManager.getNetworkCapabilities(network);
                            if (networkCapabilities != null && networkCapabilities
                                    .hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                                return false;
                            }
                        }
                        return true;
                    } else {
                        return mConnectivityManager.getActiveNetwork() != null;
                    }
                });
        // Wait some time for the network changes to propagate. Can't use
        // waitUntil(isAirplaneModeOn() == on) because the response quickly gives the new
        // airplane mode status even though the network changes haven't propagated all the way to
        // JobScheduler.
        Thread.sleep(5000);
    }

    /**
     * Sets Data Saver to the desired on/off state.
     */
    void setDataSaverEnabled(boolean enabled) throws Exception {
        SystemUtil.runShellCommand(mInstrumentation,
                enabled ? RESTRICT_BACKGROUND_ON_CMD : RESTRICT_BACKGROUND_OFF_CMD);
        final NetworkPolicyManager networkPolicyManager =
                mContext.getSystemService(NetworkPolicyManager.class);
        waitUntil("Data saver " + (enabled ? "not enabled" : "still enabled"),
                () -> enabled == SystemUtil.runWithShellPermissionIdentity(
                        () -> networkPolicyManager.getRestrictBackground(),
                        Manifest.permission.MANAGE_NETWORK_POLICY));
    }

    private void setLocationMode(String mode) throws Exception {
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, mode);
        final LocationManager locationManager = mContext.getSystemService(LocationManager.class);
        final boolean wantEnabled = !String.valueOf(Settings.Secure.LOCATION_MODE_OFF).equals(mode);
        waitUntil("Location " + (wantEnabled ? "not enabled" : "still enabled"),
                () -> wantEnabled == locationManager.isLocationEnabled());
    }

    void setWifiMeteredState(boolean metered) throws Exception {
        if (metered) {
            // Make sure unmetered cellular networks don't interfere.
            setAirplaneMode(true);
            setWifiState(true);
        }
        final String ssid = getWifiSSID();
        setWifiMeteredState(ssid, metered ? "true" : "false");
    }

    // metered should be "true", "false" or "none"
    private void setWifiMeteredState(String ssid, String metered) {
        if (metered.equals(getWifiMeteredStatus(ssid))) {
            return;
        }
        SystemUtil.runShellCommand("cmd netpolicy set metered-network " + ssid + " " + metered);
        assertEquals(getWifiMeteredStatus(ssid), metered);
    }

    /**
     * Set Wifi connection to specific state, and block until we've verified
     * that we are in the state.
     * Taken from {@link android.net.http.cts.ApacheHttpClientTest}.
     */
    void setWifiState(final boolean enable) throws Exception {
        if (!mHasWifi) {
            Log.w(TAG, "Tried to change wifi state when device doesn't have wifi feature");
            return;
        }
        if (enable != isWiFiConnected()) {
            NetworkRequest nr = new NetworkRequest.Builder().clearCapabilities().build();
            NetworkCapabilities nc = new NetworkCapabilities.Builder()
                    .addTransportType(TRANSPORT_WIFI)
                    .addCapability(NET_CAPABILITY_VALIDATED)
                    .build();
            NetworkTracker tracker = new NetworkTracker(nc, enable, mConnectivityManager);
            mConnectivityManager.registerNetworkCallback(nr, tracker);

            if (enable) {
                SystemUtil.runShellCommand("svc wifi enable");
                waitUntil("Failed to enable Wifi", 30 /* seconds */,
                        this::isWifiEnabled);
                //noinspection deprecation
                SystemUtil.runWithShellPermissionIdentity(mWifiManager::reconnect,
                        android.Manifest.permission.NETWORK_SETTINGS);
            } else {
                SystemUtil.runShellCommand("svc wifi disable");
            }

            tracker.waitForStateChange();

            assertEquals("Wifi must be " + (enable ? "connected to" : "disconnected from")
                    + " an access point for this test.", enable, isWiFiConnected());

            mConnectivityManager.unregisterNetworkCallback(tracker);
        }
    }

    void tearDown() throws Exception {
        // Restore initial restrict background data usage policy
        setDataSaverEnabled(mInitialDataSaverState);

        // Ensure that we leave WiFi in its previous state.
        if (mHasWifi) {
            if (mInitialWiFiSSID != null) {
                setWifiMeteredState(mInitialWiFiSSID, mInitialWiFiMeteredState);
            }
            if (mWifiManager.isWifiEnabled() != mInitialWiFiState) {
                try {
                    setWifiState(mInitialWiFiState);
                } catch (AssertionError e) {
                    // Don't fail the test just because wifi state wasn't set in tearDown.
                    Log.e(TAG, "Failed to return wifi state to " + mInitialWiFiState, e);
                }
            }
        }

        // Restore initial airplane mode status. Do it after setting wifi in case wifi was
        // originally metered.
        if (isAirplaneModeOn() != mInitialAirplaneModeState) {
            setAirplaneMode(mInitialAirplaneModeState);
        }

        setLocationMode(mInitialLocationMode);
    }

    @Override
    public void close() throws Exception {
        tearDown();
    }

    private String unquoteSSID(String ssid) {
        // SSID is returned surrounded by quotes if it can be decoded as UTF-8.
        // Otherwise it's guaranteed not to start with a quote.
        if (ssid.charAt(0) == '"') {
            return ssid.substring(1, ssid.length() - 1);
        } else {
            return ssid;
        }
    }

    static class NetworkTracker extends ConnectivityManager.NetworkCallback {
        private static final int MSG_CHECK_ACTIVE_NETWORK = 1;
        private final ConnectivityManager mConnectivityManager;

        private final CountDownLatch mReceiveLatch = new CountDownLatch(1);

        private final NetworkCapabilities mExpectedCapabilities;

        private final boolean mExpectedConnected;

        private final Handler mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_CHECK_ACTIVE_NETWORK) {
                    checkActiveNetwork();
                }
            }
        };

        NetworkTracker(NetworkCapabilities expectedCapabilities, boolean expectedConnected,
                ConnectivityManager cm) {
            mExpectedCapabilities = expectedCapabilities;
            mExpectedConnected = expectedConnected;
            mConnectivityManager = cm;
        }

        @Override
        public void onAvailable(Network network) {
            // Available doesn't mean it's the active network. We need to check that separately.
            checkActiveNetwork();
        }

        @Override
        public void onLost(Network network) {
            checkActiveNetwork();
        }

        boolean waitForStateChange() throws InterruptedException {
            checkActiveNetwork();
            return mReceiveLatch.await(60, TimeUnit.SECONDS);
        }

        private void checkActiveNetwork() {
            mHandler.removeMessages(MSG_CHECK_ACTIVE_NETWORK);
            if (mReceiveLatch.getCount() == 0) {
                return;
            }

            Network activeNetwork = mConnectivityManager.getActiveNetwork();
            if (mExpectedConnected) {
                if (activeNetwork != null && mExpectedCapabilities.satisfiedByNetworkCapabilities(
                        mConnectivityManager.getNetworkCapabilities(activeNetwork))) {
                    mReceiveLatch.countDown();
                } else {
                    mHandler.sendEmptyMessageDelayed(MSG_CHECK_ACTIVE_NETWORK, 5000);
                }
            } else {
                if (activeNetwork == null
                        || !mExpectedCapabilities.satisfiedByNetworkCapabilities(
                        mConnectivityManager.getNetworkCapabilities(activeNetwork))) {
                    mReceiveLatch.countDown();
                } else {
                    mHandler.sendEmptyMessageDelayed(MSG_CHECK_ACTIVE_NETWORK, 5000);
                }
            }
        }
    }
}
