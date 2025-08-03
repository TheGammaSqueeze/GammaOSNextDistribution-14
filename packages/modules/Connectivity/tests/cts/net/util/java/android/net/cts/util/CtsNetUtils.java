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

package android.net.cts.util;

import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;

import static com.android.compatibility.common.util.PropertyUtil.getFirstApiLevel;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.NetworkRequest;
import android.net.TestNetworkManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.system.Os;
import android.system.OsConstants;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.ConnectivitySettingsUtils;
import com.android.testutils.ConnectUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CtsNetUtils {
    private static final String TAG = CtsNetUtils.class.getSimpleName();

    // Redefine this flag here so that IPsec code shipped in a mainline module can build on old
    // platforms before FEATURE_IPSEC_TUNNEL_MIGRATION API is released.
    // TODO: b/275378783 Remove this flag and use the platform API when it is available.
    private static final String FEATURE_IPSEC_TUNNEL_MIGRATION =
            "android.software.ipsec_tunnel_migration";

    private static final int SOCKET_TIMEOUT_MS = 10_000;
    private static final int PRIVATE_DNS_PROBE_MS = 1_000;

    private static final int PRIVATE_DNS_SETTING_TIMEOUT_MS = 10_000;
    private static final int CONNECTIVITY_CHANGE_TIMEOUT_SECS = 30;

    private static final String PRIVATE_DNS_MODE_OPPORTUNISTIC = "opportunistic";
    private static final String PRIVATE_DNS_MODE_STRICT = "hostname";
    public static final int HTTP_PORT = 80;
    public static final String TEST_HOST = "connectivitycheck.gstatic.com";
    public static final String HTTP_REQUEST =
            "GET /generate_204 HTTP/1.0\r\n" +
                    "Host: " + TEST_HOST + "\r\n" +
                    "Connection: keep-alive\r\n\r\n";
    // Action sent to ConnectivityActionReceiver when a network callback is sent via PendingIntent.
    public static final String NETWORK_CALLBACK_ACTION =
            "ConnectivityManagerTest.NetworkCallbackAction";

    private final IBinder mBinder = new Binder();
    private final Context mContext;
    private final ConnectivityManager mCm;
    private final ContentResolver mCR;
    private final WifiManager mWifiManager;
    private TestNetworkCallback mCellNetworkCallback;
    private int mOldPrivateDnsMode = 0;
    private String mOldPrivateDnsSpecifier;

    public CtsNetUtils(Context context) {
        mContext = context;
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mCR = context.getContentResolver();
    }

    /** Checks if FEATURE_IPSEC_TUNNELS is enabled on the device */
    public boolean hasIpsecTunnelsFeature() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_IPSEC_TUNNELS)
                || getFirstApiLevel() >= Build.VERSION_CODES.Q;
    }

    /** Checks if FEATURE_IPSEC_TUNNEL_MIGRATION is enabled on the device */
    public boolean hasIpsecTunnelMigrateFeature() {
        return mContext.getPackageManager().hasSystemFeature(FEATURE_IPSEC_TUNNEL_MIGRATION);
    }

    /**
     * Sets the given appop using shell commands
     *
     * <p>Expects caller to hold the shell permission identity.
     */
    public void setAppopPrivileged(int appop, boolean allow) {
        final String opName = AppOpsManager.opToName(appop);
        for (final String pkg : new String[] {"com.android.shell", mContext.getPackageName()}) {
            final String cmd =
                    String.format(
                            "appops set %s %s %s",
                            pkg, // Package name
                            opName, // Appop
                            (allow ? "allow" : "deny")); // Action
            SystemUtil.runShellCommand(cmd);
        }
    }

    /** Sets up a test network using the provided interface name */
    public TestNetworkCallback setupAndGetTestNetwork(String ifname) throws Exception {
        // Build a network request
        final NetworkRequest nr =
                new NetworkRequest.Builder()
                        .clearCapabilities()
                        .addTransportType(TRANSPORT_TEST)
                        .setNetworkSpecifier(ifname)
                        .build();

        final TestNetworkCallback cb = new TestNetworkCallback();
        mCm.requestNetwork(nr, cb);

        // Setup the test network after network request is filed to prevent Network from being
        // reaped due to no requests matching it.
        mContext.getSystemService(TestNetworkManager.class).setupTestNetwork(ifname, mBinder);

        return cb;
    }

    /**
     * Toggle Wi-Fi off and on, waiting for the {@link ConnectivityManager#CONNECTIVITY_ACTION}
     * broadcast in both cases.
     */
    public void reconnectWifiAndWaitForConnectivityAction() throws Exception {
        assertTrue(mWifiManager.isWifiEnabled());
        Network wifiNetwork = getWifiNetwork();
        // Ensure system default network is WIFI because it's expected in disconnectFromWifi()
        expectNetworkIsSystemDefault(wifiNetwork);
        disconnectFromWifi(wifiNetwork, true /* expectLegacyBroadcast */);
        connectToWifi(true /* expectLegacyBroadcast */);
    }

    /**
     * Turn Wi-Fi off, then back on and make sure it connects, if it is supported.
     */
    public void reconnectWifiIfSupported() throws Exception {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            return;
        }
        disableWifi();
        ensureWifiConnected();
    }

    /**
     * Turn cell data off, then back on and make sure it connects, if it is supported.
     */
    public void reconnectCellIfSupported() throws Exception {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }
        setMobileDataEnabled(false);
        setMobileDataEnabled(true);
    }

    public Network expectNetworkIsSystemDefault(Network network)
            throws Exception {
        final CompletableFuture<Network> future = new CompletableFuture();
        final NetworkCallback cb = new NetworkCallback() {
            @Override
            public void onAvailable(Network n) {
                if (n.equals(network)) future.complete(network);
            }
        };

        try {
            mCm.registerDefaultNetworkCallback(cb);
            return future.get(CONNECTIVITY_CHANGE_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("Timed out waiting for system default network to switch"
                    + " to network " + network + ". Current default network is network "
                    + mCm.getActiveNetwork(), e);
        } finally {
            mCm.unregisterNetworkCallback(cb);
        }
    }

    /**
     * Enable WiFi and wait for it to become connected to a network.
     *
     * This method expects to receive a legacy broadcast on connect, which may not be sent if the
     * network does not become default or if it is not the first network.
     */
    public Network connectToWifi() {
        return connectToWifi(true /* expectLegacyBroadcast */);
    }

    /**
     * Enable WiFi and wait for it to become connected to a network.
     *
     * A network is considered connected when a {@link NetworkRequest} with TRANSPORT_WIFI
     * receives a {@link NetworkCallback#onAvailable(Network)} callback.
     */
    public Network ensureWifiConnected() {
        return connectToWifi(false /* expectLegacyBroadcast */);
    }

    /**
     * Enable WiFi and wait for it to become connected to a network.
     *
     * @param expectLegacyBroadcast Whether to check for a legacy CONNECTIVITY_ACTION connected
     *                              broadcast. The broadcast is typically not sent if the network
     *                              does not become the default network, and is not the first
     *                              network to appear.
     * @return The network that was newly connected.
     */
    private Network connectToWifi(boolean expectLegacyBroadcast) {
        ConnectivityActionReceiver receiver = new ConnectivityActionReceiver(
                mCm, ConnectivityManager.TYPE_WIFI, NetworkInfo.State.CONNECTED);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        try {
            final Network network = new ConnectUtil(mContext).ensureWifiConnected();
            if (expectLegacyBroadcast) {
                assertTrue("CONNECTIVITY_ACTION not received after connecting to " + network,
                        receiver.waitForState());
            }
            return network;
        } catch (InterruptedException ex) {
            throw new AssertionError("connectToWifi was interrupted", ex);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Disable WiFi and wait for it to become disconnected from the network.
     *
     * This method expects to receive a legacy broadcast on disconnect, which may not be sent if the
     * network was not default, or was not the first network.
     *
     * @param wifiNetworkToCheck If non-null, a network that should be disconnected. This network
     *                           is expected to be able to establish a TCP connection to a remote
     *                           server before disconnecting, and to have that connection closed in
     *                           the process.
     */
    public void disconnectFromWifi(Network wifiNetworkToCheck) {
        disconnectFromWifi(wifiNetworkToCheck, true /* expectLegacyBroadcast */);
    }

    /**
     * Disable WiFi and wait for it to become disconnected from the network.
     *
     * @param wifiNetworkToCheck If non-null, a network that should be disconnected. This network
     *                           is expected to be able to establish a TCP connection to a remote
     *                           server before disconnecting, and to have that connection closed in
     *                           the process.
     */
    public void ensureWifiDisconnected(Network wifiNetworkToCheck) {
        disconnectFromWifi(wifiNetworkToCheck, false /* expectLegacyBroadcast */);
    }

    /**
     * Disable WiFi and wait for the connection info to be cleared.
     */
    public void disableWifi() throws Exception {
        SystemUtil.runShellCommand("svc wifi disable");
        PollingCheck.check(
                "Wifi not disconnected! Current network is not null "
                        + mWifiManager.getConnectionInfo().getNetworkId(),
                TimeUnit.SECONDS.toMillis(CONNECTIVITY_CHANGE_TIMEOUT_SECS),
                () -> ShellIdentityUtils.invokeWithShellPermissions(
                        () -> mWifiManager.getConnectionInfo().getNetworkId()) == -1);
    }

    /**
     * Disable WiFi and wait for it to become disconnected from the network.
     *
     * @param wifiNetworkToCheck If non-null, a network that should be disconnected. This network
     *                           is expected to be able to establish a TCP connection to a remote
     *                           server before disconnecting, and to have that connection closed in
     *                           the process.
     * @param expectLegacyBroadcast Whether to check for a legacy CONNECTIVITY_ACTION disconnected
     *                              broadcast. The broadcast is typically not sent if the network
     *                              was not the default network and not the first network to appear.
     *                              The check will always be skipped if the device was not connected
     *                              to wifi in the first place.
     */
    private void disconnectFromWifi(Network wifiNetworkToCheck, boolean expectLegacyBroadcast) {
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), callback);

        ConnectivityActionReceiver receiver = new ConnectivityActionReceiver(
                mCm, ConnectivityManager.TYPE_WIFI, NetworkInfo.State.DISCONNECTED);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        final WifiInfo wifiInfo = runAsShell(NETWORK_SETTINGS,
                () -> mWifiManager.getConnectionInfo());
        final boolean wasWifiConnected = wifiInfo != null && wifiInfo.getNetworkId() != -1;
        // Assert that we can establish a TCP connection on wifi.
        Socket wifiBoundSocket = null;
        if (wifiNetworkToCheck != null) {
            assertTrue("Cannot check network " + wifiNetworkToCheck + ": wifi is not connected",
                    wasWifiConnected);
            final NetworkCapabilities nc = mCm.getNetworkCapabilities(wifiNetworkToCheck);
            assertNotNull("Network " + wifiNetworkToCheck + " is not connected", nc);
            try {
                wifiBoundSocket = getBoundSocket(wifiNetworkToCheck, TEST_HOST, HTTP_PORT);
                testHttpRequest(wifiBoundSocket);
            } catch (IOException e) {
                fail("HTTP request before wifi disconnected failed with: " + e);
            }
        }

        try {
            if (wasWifiConnected) {
                // Make sure the callback is registered before turning off WiFi.
                callback.waitForAvailable();
            }
            SystemUtil.runShellCommand("svc wifi disable");
            if (wasWifiConnected) {
                // Ensure we get both an onLost callback and a CONNECTIVITY_ACTION.
                assertNotNull("Did not receive onLost callback after disabling wifi",
                        callback.waitForLost());
                if (expectLegacyBroadcast) {
                    assertTrue("Wifi failed to reach DISCONNECTED state.", receiver.waitForState());
                }
            }
        } catch (InterruptedException ex) {
            fail("disconnectFromWifi was interrupted");
        } finally {
            mCm.unregisterNetworkCallback(callback);
            mContext.unregisterReceiver(receiver);
        }

        // Check that the socket is closed when wifi disconnects.
        if (wifiBoundSocket != null) {
            try {
                testHttpRequest(wifiBoundSocket);
                fail("HTTP request should not succeed after wifi disconnects");
            } catch (IOException expected) {
                assertEquals(Os.strerror(OsConstants.ECONNABORTED), expected.getMessage());
            }
        }
    }

    public Network getWifiNetwork() {
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), callback);
        Network network = null;
        try {
            network = callback.waitForAvailable();
        } catch (InterruptedException e) {
            fail("NetworkCallback wait was interrupted.");
        } finally {
            mCm.unregisterNetworkCallback(callback);
        }
        assertNotNull("Cannot find Network for wifi. Is wifi connected?", network);
        return network;
    }

    public Network connectToCell() throws InterruptedException {
        if (cellConnectAttempted()) {
            mCm.unregisterNetworkCallback(mCellNetworkCallback);
        }
        NetworkRequest cellRequest = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        mCellNetworkCallback = new TestNetworkCallback();
        mCm.requestNetwork(cellRequest, mCellNetworkCallback);
        final Network cellNetwork = mCellNetworkCallback.waitForAvailable();
        assertNotNull("Cell network not available. " +
                "Please ensure the device has working mobile data.", cellNetwork);
        return cellNetwork;
    }

    public void disconnectFromCell() {
        if (!cellConnectAttempted()) {
            throw new IllegalStateException("Cell connection not attempted");
        }
        mCm.unregisterNetworkCallback(mCellNetworkCallback);
        mCellNetworkCallback = null;
    }

    public boolean cellConnectAttempted() {
        return mCellNetworkCallback != null;
    }

    public void tearDown() {
        if (cellConnectAttempted()) {
            disconnectFromCell();
        }
    }

    private NetworkRequest makeWifiNetworkRequest() {
        return new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
    }

    public void testHttpRequest(Socket s) throws IOException {
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        final byte[] requestBytes = HTTP_REQUEST.getBytes("UTF-8");
        byte[] responseBytes = new byte[4096];
        out.write(requestBytes);
        in.read(responseBytes);
        final String response = new String(responseBytes, "UTF-8");
        assertTrue("Received unexpected response: " + response,
                response.startsWith("HTTP/1.0 204 No Content\r\n"));
    }

    private Socket getBoundSocket(Network network, String host, int port) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        Socket s = network.getSocketFactory().createSocket();
        try {
            s.setSoTimeout(SOCKET_TIMEOUT_MS);
            s.connect(addr, SOCKET_TIMEOUT_MS);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        return s;
    }

    public void storePrivateDnsSetting() {
        mOldPrivateDnsMode = ConnectivitySettingsUtils.getPrivateDnsMode(mContext);
        mOldPrivateDnsSpecifier = ConnectivitySettingsUtils.getPrivateDnsHostname(mContext);
    }

    public void restorePrivateDnsSetting() throws InterruptedException {
        if (mOldPrivateDnsMode == 0) {
            fail("restorePrivateDnsSetting without storing settings first");
        }

        if (mOldPrivateDnsMode != ConnectivitySettingsUtils.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
            // Also restore hostname even if the value is not used since private dns is not in
            // the strict mode to prevent setting being changed after test.
            ConnectivitySettingsUtils.setPrivateDnsHostname(mContext, mOldPrivateDnsSpecifier);
            ConnectivitySettingsUtils.setPrivateDnsMode(mContext, mOldPrivateDnsMode);
            return;
        }
        // restore private DNS setting
        // In case of invalid setting, set to opportunistic to avoid a bad state and fail
        if (TextUtils.isEmpty(mOldPrivateDnsSpecifier)) {
            ConnectivitySettingsUtils.setPrivateDnsMode(mContext,
                    ConnectivitySettingsUtils.PRIVATE_DNS_MODE_OPPORTUNISTIC);
            fail("Invalid private DNS setting: no hostname specified in strict mode");
        }
        setPrivateDnsStrictMode(mOldPrivateDnsSpecifier);

        // There might be a race before private DNS setting is applied and the next test is
        // running. So waiting private DNS to be validated can reduce the flaky rate of test.
        awaitPrivateDnsSetting("restorePrivateDnsSetting timeout",
                mCm.getActiveNetwork(),
                mOldPrivateDnsSpecifier, true /* requiresValidatedServer */);
    }

    public void setPrivateDnsStrictMode(String server) {
        // To reduce flake rate, set PRIVATE_DNS_SPECIFIER before PRIVATE_DNS_MODE. This ensures
        // that if the previous private DNS mode was not strict, the system only sees one
        // EVENT_PRIVATE_DNS_SETTINGS_CHANGED event instead of two.
        ConnectivitySettingsUtils.setPrivateDnsHostname(mContext, server);
        final int mode = ConnectivitySettingsUtils.getPrivateDnsMode(mContext);
        // If current private DNS mode is strict, we only need to set PRIVATE_DNS_SPECIFIER.
        if (mode != ConnectivitySettingsUtils.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
            ConnectivitySettingsUtils.setPrivateDnsMode(mContext,
                    ConnectivitySettingsUtils.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);
        }
    }

    /**
     * Waiting for the new private DNS setting to be validated.
     * This method is helpful when the new private DNS setting is configured and ensure the new
     * setting is applied and workable. It can also reduce the flaky rate when the next test is
     * running.
     *
     * @param msg A message that will be printed when the validation of private DNS is timeout.
     * @param network A network which will apply the new private DNS setting.
     * @param server The hostname of private DNS.
     * @param requiresValidatedServer A boolean to decide if it's needed to wait private DNS to be
     *                                 validated or not.
     * @throws InterruptedException If the thread is interrupted.
     */
    public void awaitPrivateDnsSetting(@NonNull String msg, @NonNull Network network,
            @Nullable String server, boolean requiresValidatedServer) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final NetworkRequest request = new NetworkRequest.Builder().clearCapabilities().build();
        final NetworkCallback callback = new NetworkCallback() {
            @Override
            public void onLinkPropertiesChanged(Network n, LinkProperties lp) {
                Log.i(TAG, "Link properties of network " + n + " changed to " + lp);
                if (requiresValidatedServer && lp.getValidatedPrivateDnsServers().isEmpty()) {
                    return;
                }
                Log.i(TAG, "Set private DNS server to " + server);
                if (network.equals(n) && Objects.equals(server, lp.getPrivateDnsServerName())) {
                    latch.countDown();
                }
            }
        };
        mCm.registerNetworkCallback(request, callback);
        assertTrue(msg, latch.await(PRIVATE_DNS_SETTING_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        mCm.unregisterNetworkCallback(callback);
        // Wait some time for NetworkMonitor's private DNS probe to complete. If we do not do
        // this, then the test could complete before the NetworkMonitor private DNS probe
        // completes. This would result in tearDown disabling private DNS, and the NetworkMonitor
        // private DNS probe getting stuck because there are no longer any private DNS servers to
        // query. This then results in the next test not being able to change the private DNS
        // setting within the timeout, because the NetworkMonitor thread is blocked in the
        // private DNS probe. There is no way to know when the probe has completed: because the
        // network is likely already validated, there is no callback that we can listen to, so
        // just sleep.
        if (requiresValidatedServer) {
            Thread.sleep(PRIVATE_DNS_PROBE_MS);
        }
    }

    /**
     * Get all testable Networks with internet capability.
     */
    public Network[] getTestableNetworks() {
        final ArrayList<Network> testableNetworks = new ArrayList<Network>();
        for (Network network : mCm.getAllNetworks()) {
            final NetworkCapabilities nc = mCm.getNetworkCapabilities(network);
            if (nc != null
                    && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                testableNetworks.add(network);
            }
        }

        assertTrue("This test requires that at least one public Internet-providing"
                        + " network be connected. Please ensure that the device is connected to"
                        + " a network.",
                testableNetworks.size() >= 1);
        return testableNetworks.toArray(new Network[0]);
    }

    /**
     * Enables or disables the mobile data and waits for the state to change.
     *
     * @param enabled - true to enable, false to disable the mobile data.
     */
    public void setMobileDataEnabled(boolean enabled) throws InterruptedException {
        final TelephonyManager tm =  mContext.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET)
                .build();
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.requestNetwork(request, callback);

        try {
            if (!enabled) {
                assertNotNull("Cannot disable mobile data unless mobile data is connected",
                        callback.waitForAvailable());
            }

            if (SdkLevel.isAtLeastS()) {
                runAsShell(MODIFY_PHONE_STATE, () -> tm.setDataEnabledForReason(
                        TelephonyManager.DATA_ENABLED_REASON_USER, enabled));
            } else {
                runAsShell(MODIFY_PHONE_STATE, () -> tm.setDataEnabled(enabled));
            }
            if (enabled) {
                assertNotNull("Enabling mobile data did not connect mobile data",
                        callback.waitForAvailable());
            } else {
                assertNotNull("Disabling mobile data did not disconnect mobile data",
                        callback.waitForLost());
            }

        } finally {
            mCm.unregisterNetworkCallback(callback);
        }
    }

    /**
     * Receiver that captures the last connectivity change's network type and state. Recognizes
     * both {@code CONNECTIVITY_ACTION} and {@code NETWORK_CALLBACK_ACTION} intents.
     */
    public static class ConnectivityActionReceiver extends BroadcastReceiver {

        private final CountDownLatch mReceiveLatch = new CountDownLatch(1);

        private final int mNetworkType;
        private final NetworkInfo.State mNetState;
        private final ConnectivityManager mCm;

        public ConnectivityActionReceiver(ConnectivityManager cm, int networkType,
                NetworkInfo.State netState) {
            this.mCm = cm;
            mNetworkType = networkType;
            mNetState = netState;
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            NetworkInfo networkInfo = null;

            // When receiving ConnectivityManager.CONNECTIVITY_ACTION, the NetworkInfo parcelable
            // is stored in EXTRA_NETWORK_INFO. With a NETWORK_CALLBACK_ACTION, the Network is
            // sent in EXTRA_NETWORK and we need to ask the ConnectivityManager for the NetworkInfo.
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                networkInfo = intent.getExtras()
                        .getParcelable(ConnectivityManager.EXTRA_NETWORK_INFO);
                assertNotNull("ConnectivityActionReceiver expected EXTRA_NETWORK_INFO",
                        networkInfo);
            } else if (NETWORK_CALLBACK_ACTION.equals(action)) {
                Network network = intent.getExtras()
                        .getParcelable(ConnectivityManager.EXTRA_NETWORK);
                assertNotNull("ConnectivityActionReceiver expected EXTRA_NETWORK", network);
                networkInfo = this.mCm.getNetworkInfo(network);
                if (networkInfo == null) {
                    // When disconnecting, it seems like we get an intent sent with an invalid
                    // Network; that is, by the time we call ConnectivityManager.getNetworkInfo(),
                    // it is invalid. Ignore these.
                    Log.i(TAG, "ConnectivityActionReceiver NETWORK_CALLBACK_ACTION ignoring "
                            + "invalid network");
                    return;
                }
            } else {
                fail("ConnectivityActionReceiver received unxpected intent action: " + action);
            }

            assertNotNull("ConnectivityActionReceiver didn't find NetworkInfo", networkInfo);
            int networkType = networkInfo.getType();
            State networkState = networkInfo.getState();
            Log.i(TAG, "Network type: " + networkType + " state: " + networkState);
            if (networkType == mNetworkType && networkInfo.getState() == mNetState) {
                mReceiveLatch.countDown();
            }
        }

        public boolean waitForState() throws InterruptedException {
            return mReceiveLatch.await(CONNECTIVITY_CHANGE_TIMEOUT_SECS, TimeUnit.SECONDS);
        }
    }

    /**
     * Callback used in testRegisterNetworkCallback that allows caller to block on
     * {@code onAvailable}.
     */
    public static class TestNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final ConditionVariable mAvailableCv = new ConditionVariable(false);
        private final CountDownLatch mLostLatch = new CountDownLatch(1);
        private final CountDownLatch mUnavailableLatch = new CountDownLatch(1);

        public Network currentNetwork;
        public Network lastLostNetwork;

        /**
         * Wait for a network to be available.
         *
         * If onAvailable was previously called but was followed by onLost, this will wait for the
         * next available network.
         */
        public Network waitForAvailable() throws InterruptedException {
            final long timeoutMs = TimeUnit.SECONDS.toMillis(CONNECTIVITY_CHANGE_TIMEOUT_SECS);
            while (mAvailableCv.block(timeoutMs)) {
                final Network n = currentNetwork;
                if (n != null) return n;
                Log.w(TAG, "onAvailable called but network was lost before it could be returned."
                        + " Waiting for the next call to onAvailable.");
            }
            return null;
        }

        public Network waitForLost() throws InterruptedException {
            return mLostLatch.await(CONNECTIVITY_CHANGE_TIMEOUT_SECS, TimeUnit.SECONDS)
                    ? lastLostNetwork : null;
        }

        public boolean waitForUnavailable() throws InterruptedException {
            return mUnavailableLatch.await(2, TimeUnit.SECONDS);
        }

        @Override
        public void onAvailable(Network network) {
            Log.i(TAG, "CtsNetUtils TestNetworkCallback onAvailable " + network);
            currentNetwork = network;
            mAvailableCv.open();
        }

        @Override
        public void onLost(Network network) {
            Log.i(TAG, "CtsNetUtils TestNetworkCallback onLost " + network);
            lastLostNetwork = network;
            if (network.equals(currentNetwork)) {
                mAvailableCv.close();
                currentNetwork = null;
            }
            mLostLatch.countDown();
        }

        @Override
        public void onUnavailable() {
            mUnavailableLatch.countDown();
        }
    }
}
