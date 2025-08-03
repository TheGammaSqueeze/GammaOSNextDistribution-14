/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.net.wifi.cts;

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDiscoveryConfig;
import android.net.wifi.p2p.WifiP2pExtListenParams;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ExternalApproverRequestListener;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.os.WorkSource;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.wifi.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
public class ConcurrencyTest extends WifiJUnit4TestBase {
    private static Context sContext;
    private static boolean sShouldRunTest;

    private static class MySync {
        static final int P2P_STATE = 1;
        static final int DISCOVERY_STATE = 2;
        static final int NETWORK_INFO = 3;
        static final int LISTEN_STATE = 4;

        public BitSet pendingSync = new BitSet();

        public int expectedP2pState = WifiP2pManager.WIFI_P2P_STATE_DISABLED;
        public int expectedDiscoveryState;
        public NetworkInfo expectedNetworkInfo;
        public int expectedListenState;
    }

    private static class MyResponse {
        public boolean valid = false;

        public boolean success;
        public int failureReason;
        public int p2pState;
        public int discoveryState;
        public int listenState;
        public NetworkInfo networkInfo;
        public WifiP2pInfo p2pInfo;
        public String deviceName;
        public WifiP2pGroupList persistentGroups;
        public WifiP2pGroup group = new WifiP2pGroup();

        // External approver
        public boolean isAttached;
        public boolean isDetached;
        public int detachReason;
        public MacAddress targetPeer;

        public void reset() {
            valid = false;

            networkInfo = null;
            p2pInfo = null;
            deviceName = null;
            persistentGroups = null;
            group = null;

            isAttached = false;
            isDetached = false;
            targetPeer = null;
        }
    }

    private static WifiManager sWifiManager;
    private static WifiP2pManager sWifiP2pManager;
    private static WifiP2pManager.Channel sWifiP2pChannel;
    private static final MySync MY_SYNC = new MySync();
    private static final MyResponse MY_RESPONSE = new MyResponse();
    private static boolean sWasVerboseLoggingEnabled;
    private WifiP2pConfig mTestWifiP2pPeerConfig;
    private static boolean sWasWifiEnabled;
    private static boolean sWasScanThrottleEnabled;
    private final Object mLock = new Object();

    private static final String TAG = "ConcurrencyTest";
    private static final int TIMEOUT_MS = 15000;
    private static final int WAIT_MS = 100;
    private static final int DURATION = 5000;
    private static final int TEST_OUI = 0x00C82ADD; // Google OUI
    private static final BroadcastReceiver RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                synchronized (MY_SYNC) {
                    MY_SYNC.pendingSync.set(MySync.P2P_STATE);
                    MY_SYNC.expectedP2pState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED);
                    Log.d(TAG, "Get WIFI_P2P_STATE_CHANGED_ACTION: "
                            + MY_SYNC.expectedP2pState);
                    MY_SYNC.notify();
                }
            } else if (action.equals(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)) {
                synchronized (MY_SYNC) {
                    MY_SYNC.pendingSync.set(MySync.DISCOVERY_STATE);
                    MY_SYNC.expectedDiscoveryState = intent.getIntExtra(
                            WifiP2pManager.EXTRA_DISCOVERY_STATE,
                            WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                    Log.d(TAG, "Get WIFI_P2P_STATE_CHANGED_ACTION: "
                            + MY_SYNC.expectedDiscoveryState);
                    MY_SYNC.notify();
                }
            } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                synchronized (MY_SYNC) {
                    MY_SYNC.pendingSync.set(MySync.NETWORK_INFO);
                    MY_SYNC.expectedNetworkInfo = (NetworkInfo) intent.getExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO, null);
                    Log.d(TAG, "Get WIFI_P2P_CONNECTION_CHANGED_ACTION: "
                            + MY_SYNC.expectedNetworkInfo);
                    MY_SYNC.notify();
                }
            } else if (action.equals(WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED)) {
                synchronized (MY_SYNC) {
                    MY_SYNC.pendingSync.set(MySync.LISTEN_STATE);
                    MY_SYNC.expectedListenState = intent.getIntExtra(
                            WifiP2pManager.EXTRA_LISTEN_STATE,
                            WifiP2pManager.WIFI_P2P_LISTEN_STOPPED);
                    MY_SYNC.notify();
                }
            }
        }
    };

    private static WifiP2pManager.ActionListener sActionListener =
            new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() {
            synchronized (MY_RESPONSE) {
                MY_RESPONSE.valid = true;
                MY_RESPONSE.success = true;
                MY_RESPONSE.notify();
            }
        }

        @Override
        public void onFailure(int reason) {
            synchronized (MY_RESPONSE) {
                Log.d(TAG, "failure reason: " + reason);
                MY_RESPONSE.valid = true;
                MY_RESPONSE.success = false;
                MY_RESPONSE.failureReason = reason;
                MY_RESPONSE.notify();
            }
        }
    };

    private final HandlerThread mHandlerThread = new HandlerThread("WifiP2pConcurrencyTest");
    protected final Executor mExecutor;
    {
        mHandlerThread.start();
        mExecutor = new HandlerExecutor(new Handler(mHandlerThread.getLooper()));
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(sContext)
                && !WifiFeature.isP2pSupported(sContext)) {
            // skip the test if WiFi && p2p are not supported
            return;
        }
        if (!WifiFeature.isWifiSupported(sContext)) {
            assertThat(WifiFeature.isP2pSupported(sContext)).isFalse();
        }
        if (!WifiFeature.isP2pSupported(sContext)) {
            return;
        }
        if (!hasLocationFeature()) {
            Log.d(TAG, "Skipping test as location is not supported");
            return;
        }
        sShouldRunTest = true;
        sWifiManager = (WifiManager) sContext.getSystemService(Context.WIFI_SERVICE);
        assertThat(sWifiManager).isNotNull();

        // turn on verbose logging for tests
        sWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(true));
        sWasScanThrottleEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.isScanThrottleEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(false));
        sWasWifiEnabled = sWifiManager.isWifiEnabled();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED);
        intentFilter.setPriority(999);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            sContext.registerReceiver(RECEIVER, intentFilter, RECEIVER_NOT_EXPORTED);
        } else {
            sContext.registerReceiver(RECEIVER, intentFilter);
        }
        if (sWasWifiEnabled) {
            // Clean the possible P2P enabled broadcast from other test case.
            waitForBroadcasts(MySync.P2P_STATE);
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(false));
            PollingCheck.check("Wifi not disabled", DURATION, () -> !sWifiManager.isWifiEnabled());
            // Make sure WifiP2P is disabled
            waitForBroadcasts(MySync.P2P_STATE);
            assertThat(WifiP2pManager.WIFI_P2P_STATE_DISABLED).isEqualTo(MY_SYNC.expectedP2pState);
        }
        synchronized (MY_SYNC) {
            MY_SYNC.expectedP2pState = WifiP2pManager.WIFI_P2P_STATE_DISABLED;
            MY_SYNC.expectedDiscoveryState = WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED;
            MY_SYNC.expectedNetworkInfo = null;
            MY_SYNC.expectedListenState = WifiP2pManager.WIFI_P2P_LISTEN_STOPPED;
            MY_SYNC.pendingSync.clear();
            resetResponse(MY_RESPONSE);
        }
        setupWifiP2p();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) {
            return;
        }
        sContext.unregisterReceiver(RECEIVER);
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setVerboseLoggingEnabled(sWasVerboseLoggingEnabled));
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> sWifiManager.setScanThrottleEnabled(sWasScanThrottleEnabled));
        if (sWasWifiEnabled) {
            enableWifi();
        }
    }


    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);

        // Clean all the state
        synchronized (MY_SYNC) {
            MY_SYNC.expectedP2pState = WifiP2pManager.WIFI_P2P_STATE_DISABLED;
            MY_SYNC.expectedDiscoveryState = WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED;
            MY_SYNC.expectedNetworkInfo = null;
            MY_SYNC.expectedListenState = WifiP2pManager.WIFI_P2P_LISTEN_STOPPED;
            MY_SYNC.pendingSync.clear();
            resetResponse(MY_RESPONSE);
        }

        // for general connect command
        mTestWifiP2pPeerConfig = new WifiP2pConfig();
        mTestWifiP2pPeerConfig.deviceAddress = "aa:bb:cc:dd:ee:ff";
    }

    @After
    public void tearDown() throws Exception {
        if (!sShouldRunTest) {
            return;
        }
        removeAllPersistentGroups();
        sWifiP2pManager.removeGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
    }

    private static boolean waitForBroadcasts(List<Integer> waitSyncList) {
        synchronized (MY_SYNC) {
            long timeout = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < timeout) {
                List<Integer> handledSyncList = waitSyncList.stream()
                        .filter(w -> MY_SYNC.pendingSync.get(w))
                        .collect(Collectors.toList());
                handledSyncList.forEach(w -> MY_SYNC.pendingSync.clear(w));
                waitSyncList.removeAll(handledSyncList);
                if (waitSyncList.isEmpty()) {
                    break;
                }
                try {
                    MY_SYNC.wait(WAIT_MS);
                } catch (InterruptedException e) { }
            }
            if (!waitSyncList.isEmpty()) {
                Log.i(TAG, "Missing broadcast: " + waitSyncList);
            }
            return waitSyncList.isEmpty();
        }
    }

    private static boolean waitForBroadcasts(int waitSingleSync) {
        return waitForBroadcasts(
                new LinkedList<Integer>(Arrays.asList(waitSingleSync)));
    }

    private NetworkInfo.DetailedState waitForNextNetworkState() {
        waitForBroadcasts(MySync.NETWORK_INFO);
        assertThat(MY_SYNC.expectedNetworkInfo).isNotNull();
        return MY_SYNC.expectedNetworkInfo.getDetailedState();
    }

    private boolean waitForConnectedNetworkState() {
        // The possible orders of network states are:
        // * IDLE > CONNECTING > CONNECTED for lazy initialization
        // * DISCONNECTED > CONNECTING > CONNECTED for previous group removal
        // * CONNECTING > CONNECTED
        NetworkInfo.DetailedState state = waitForNextNetworkState();
        if (state == NetworkInfo.DetailedState.IDLE
                || state == NetworkInfo.DetailedState.DISCONNECTED) {
            state = waitForNextNetworkState();
        }
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)
                && state == NetworkInfo.DetailedState.CONNECTING) {
            state = waitForNextNetworkState();
        }
        return state == NetworkInfo.DetailedState.CONNECTED;
    }

    private static boolean waitForServiceResponse(MyResponse waitResponse) {
        synchronized (waitResponse) {
            long timeout = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < timeout) {
                try {
                    waitResponse.wait(WAIT_MS);
                } catch (InterruptedException e) { }

                if (waitResponse.valid) {
                    return true;
                }
            }
            return false;
        }
    }


    // Returns true if the device has location feature.
    private static boolean hasLocationFeature() {
        return sContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION);
    }

    private static void resetResponse(MyResponse responseObj) {
        synchronized (responseObj) {
            responseObj.reset();
        }
    }

    /*
     * Enables Wifi and block until connection is established.
     */
    private static void enableWifi() throws Exception {
        if (!sWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.setWifiEnabled(true));
            PollingCheck.check("Wifi not enabled", DURATION, () -> sWifiManager.isWifiEnabled());
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.startScan(new WorkSource(myUid())));
            ConnectivityManager cm =
                    (ConnectivityManager) sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            final CountDownLatch latch = new CountDownLatch(1);
            ConnectivityManager.NetworkCallback networkCallback =
                    new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    latch.countDown();
                }
            };
            cm.registerNetworkCallback(request, networkCallback);
            assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            cm.unregisterNetworkCallback(networkCallback);
            Thread.sleep(15_000);
        }
    }

    private static void removeAllPersistentGroups() {
        WifiP2pGroupList persistentGroups = getPersistentGroups();
        assertNotNull(persistentGroups);
        for (WifiP2pGroup group: persistentGroups.getGroupList()) {
            resetResponse(MY_RESPONSE);
            ShellIdentityUtils.invokeWithShellPermissions(() -> {
                sWifiP2pManager.deletePersistentGroup(sWifiP2pChannel,
                        group.getNetworkId(),
                        sActionListener);
                assertTrue(waitForServiceResponse(MY_RESPONSE));
                assertTrue(MY_RESPONSE.success);
            });
        }
        persistentGroups = getPersistentGroups();
        assertNotNull(persistentGroups);
        assertEquals(0, persistentGroups.getGroupList().size());
    }

    private static void setupWifiP2p() {
        try {
            enableWifi();
        } catch (Exception e) {
            Log.d(TAG, "Enable Wifi got exception:" + e.getMessage());
        }

        assertThat(sWifiManager.isWifiEnabled()).isTrue();

        sWifiP2pManager = (WifiP2pManager) sContext.getSystemService(Context.WIFI_P2P_SERVICE);
        sWifiP2pChannel = sWifiP2pManager.initialize(
                sContext, sContext.getMainLooper(), null);

        assertThat(sWifiP2pManager).isNotNull();
        assertThat(sWifiP2pChannel).isNotNull();

        assertThat(waitForBroadcasts(MySync.P2P_STATE)).isTrue();

        assertThat(WifiP2pManager.WIFI_P2P_STATE_ENABLED).isEqualTo(MY_SYNC.expectedP2pState);
        removeAllPersistentGroups();
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#requestP2pState"})
    @Test
    public void testConcurrency() {
        sWifiP2pManager.requestP2pState(sWifiP2pChannel, new WifiP2pManager.P2pStateListener() {
            @Override
            public void onP2pStateAvailable(int state) {
                synchronized (MY_RESPONSE) {
                    MY_RESPONSE.valid = true;
                    MY_RESPONSE.p2pState = state;
                    MY_RESPONSE.notify();
                }
            }
        });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_STATE_ENABLED, MY_RESPONSE.p2pState);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#requestDiscoveryState",
            "android.net.wifi.p2p.WifiP2pManager#discoverPeers",
            "android.net.wifi.p2p.WifiP2pManager#stopPeerDiscovery"})
    @Test
    public void testRequestDiscoveryState() {
        sWifiP2pManager.requestDiscoveryState(
                sWifiP2pChannel, new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED, MY_RESPONSE.discoveryState);

        // If there is any saved network and this device is connecting to this saved network,
        // p2p discovery might be blocked during DHCP provision.
        int retryCount = 3;
        while (retryCount > 0) {
            resetResponse(MY_RESPONSE);
            sWifiP2pManager.discoverPeers(sWifiP2pChannel, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            if (MY_RESPONSE.success
                    || MY_RESPONSE.failureReason != WifiP2pManager.BUSY) {
                break;
            }
            Log.w(TAG, "Discovery is blocked, try again!");
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {}
            retryCount--;
        }
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForBroadcasts(MySync.DISCOVERY_STATE));

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDiscoveryState(sWifiP2pChannel,
                new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED, MY_RESPONSE.discoveryState);

        sWifiP2pManager.stopPeerDiscovery(sWifiP2pChannel, null);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#requestNetworkInfo",
            "android.net.wifi.p2p.WifiP2pManager#createGroup",
            "android.net.wifi.p2p.WifiP2pManager#removeGroup"})
    @Test
    public void testRequestNetworkInfo() {
        sWifiP2pManager.requestNetworkInfo(sWifiP2pChannel,
                new WifiP2pManager.NetworkInfoListener() {
                    @Override
                    public void onNetworkInfoAvailable(NetworkInfo info) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.networkInfo = info;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertNotNull(MY_RESPONSE.networkInfo);

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.createGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);

        assertTrue(waitForConnectedNetworkState());

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestNetworkInfo(sWifiP2pChannel,
                new WifiP2pManager.NetworkInfoListener() {
                    @Override
                    public void onNetworkInfoAvailable(NetworkInfo info) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.networkInfo = info;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertNotNull(MY_RESPONSE.networkInfo);
        assertEquals(NetworkInfo.DetailedState.CONNECTED,
                MY_RESPONSE.networkInfo.getDetailedState());

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestConnectionInfo(sWifiP2pChannel,
                new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.p2pInfo = new WifiP2pInfo(info);
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertNotNull(MY_RESPONSE.p2pInfo);
        assertTrue(MY_RESPONSE.p2pInfo.groupFormed);
        assertTrue(MY_RESPONSE.p2pInfo.isGroupOwner);

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestGroupInfo(sWifiP2pChannel,
                new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.group = new WifiP2pGroup(group);
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertNotNull(MY_RESPONSE.group);
        assertNotEquals(0, MY_RESPONSE.group.getFrequency());
        assertTrue(MY_RESPONSE.group.getNetworkId() >= 0);

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.removeGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForBroadcasts(MySync.NETWORK_INFO));
        assertNotNull(MY_SYNC.expectedNetworkInfo);
        assertEquals(NetworkInfo.DetailedState.DISCONNECTED,
                MY_SYNC.expectedNetworkInfo.getDetailedState());
    }

    private String getDeviceName() {
        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDeviceInfo(sWifiP2pChannel,
                new WifiP2pManager.DeviceInfoListener() {
                    @Override
                    public void onDeviceInfoAvailable(WifiP2pDevice wifiP2pDevice) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.deviceName = wifiP2pDevice.deviceName;
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        return MY_RESPONSE.deviceName;
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pGroup#setVendorData"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testWifiP2pGroupSetAndGetVendorData() {
        List<OuiKeyedData> vendorData = createTestOuiKeyedDataList(5);
        WifiP2pGroup group = new WifiP2pGroup();
        group.setVendorData(vendorData);
        assertTrue(vendorData.equals(group.getVendorData()));
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#setDeviceName"})
    @Test
    public void testSetDeviceName() {
        String testDeviceName = "test";
        String originalDeviceName = getDeviceName();
        assertNotNull(originalDeviceName);

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.setDeviceName(
                    sWifiP2pChannel, testDeviceName, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        String currentDeviceName = getDeviceName();
        assertEquals(testDeviceName, currentDeviceName);

        // restore the device name at the end
        resetResponse(MY_RESPONSE);
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.setDeviceName(
                    sWifiP2pChannel, originalDeviceName, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });
    }

    private static WifiP2pGroupList getPersistentGroups() {
        resetResponse(MY_RESPONSE);
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.requestPersistentGroupInfo(sWifiP2pChannel,
                    new WifiP2pManager.PersistentGroupInfoListener() {
                        @Override
                        public void onPersistentGroupInfoAvailable(WifiP2pGroupList groups) {
                            synchronized (MY_RESPONSE) {
                                MY_RESPONSE.persistentGroups = groups;
                                MY_RESPONSE.valid = true;
                                MY_RESPONSE.notify();
                            }
                        }
                    });
            assertTrue(waitForServiceResponse(MY_RESPONSE));
        });
        return MY_RESPONSE.persistentGroups;
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#requestPersistentGroupInfo",
            "android.net.wifi.p2p.WifiP2pManager#factoryReset"})
    @Test
    public void testPersistentGroupOperation() {
        sWifiP2pManager.createGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);

        assertTrue(waitForConnectedNetworkState());

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.removeGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForBroadcasts(MySync.NETWORK_INFO));
        assertNotNull(MY_SYNC.expectedNetworkInfo);
        assertEquals(NetworkInfo.DetailedState.DISCONNECTED,
                MY_SYNC.expectedNetworkInfo.getDetailedState());

        WifiP2pGroupList persistentGroups = getPersistentGroups();
        assertNotNull(persistentGroups);
        assertEquals(1, persistentGroups.getGroupList().size());

        resetResponse(MY_RESPONSE);
        final int firstNetworkId = persistentGroups.getGroupList().get(0).getNetworkId();
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.deletePersistentGroup(sWifiP2pChannel,
                    firstNetworkId,
                    sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        persistentGroups = getPersistentGroups();
        assertNotNull(persistentGroups);
        assertEquals(0, persistentGroups.getGroupList().size());

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.createGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForConnectedNetworkState());

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.removeGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForBroadcasts(MySync.NETWORK_INFO));
        assertNotNull(MY_SYNC.expectedNetworkInfo);
        assertEquals(NetworkInfo.DetailedState.DISCONNECTED,
                MY_SYNC.expectedNetworkInfo.getDetailedState());

        resetResponse(MY_RESPONSE);
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.factoryReset(sWifiP2pChannel, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        persistentGroups = getPersistentGroups();
        assertNotNull(persistentGroups);
        assertEquals(0, persistentGroups.getGroupList().size());
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#setWifiP2pChannels",
            "android.net.wifi.p2p.WifiP2pManager#startListening",
            "android.net.wifi.p2p.WifiP2pManager#stopListening"})
    @Test
    public void testP2pListening() {
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.setWifiP2pChannels(sWifiP2pChannel, 6, 11, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        resetResponse(MY_RESPONSE);
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.startListening(sWifiP2pChannel, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.stopListening(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#setWifiP2pChannels",
            "android.net.wifi.p2p.WifiP2pManager#startListening",
            "android.net.wifi.p2p.WifiP2pManager#stopListening"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testP2pListeningWithParameters() {
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.setWifiP2pChannels(sWifiP2pChannel, 6, 11, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        List<OuiKeyedData> vendorData = createTestOuiKeyedDataList(5);
        WifiP2pExtListenParams extListenParams =
                new WifiP2pExtListenParams.Builder().setVendorData(vendorData).build();
        assertTrue(vendorData.equals(extListenParams.getVendorData()));

        resetResponse(MY_RESPONSE);
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.startListening(sWifiP2pChannel, extListenParams, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.stopListening(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#setServiceResponseListener",
            "android.net.wifi.p2p.WifiP2pManager#addLocalService",
            "android.net.wifi.p2p.WifiP2pManager#clearLocalServices",
            "android.net.wifi.p2p.WifiP2pManager#removeLocalService"})
    @Test
    public void testP2pService() {
        // This only store the listener to the WifiP2pManager internal variable, nothing to fail.
        sWifiP2pManager.setServiceResponseListener(sWifiP2pChannel,
                new WifiP2pManager.ServiceResponseListener() {
                    @Override
                    public void onServiceAvailable(
                            int protocolType, byte[] responseData, WifiP2pDevice srcDevice) {
                    }
                });

        List<String> services = new ArrayList<String>();
        services.add("urn:schemas-upnp-org:service:AVTransport:1");
        services.add("urn:schemas-upnp-org:service:ConnectionManager:1");
        WifiP2pServiceInfo rendererService = WifiP2pUpnpServiceInfo.newInstance(
                "6859dede-8574-59ab-9332-123456789011",
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                services);
        sWifiP2pManager.addLocalService(sWifiP2pChannel,
                rendererService,
                sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.removeLocalService(sWifiP2pChannel,
                rendererService,
                sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.clearLocalServices(sWifiP2pChannel,
                sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#removeClient"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testRemoveClient() {

        if (!sWifiP2pManager.isGroupClientRemovalSupported()) return;

        sWifiP2pManager.createGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);

        assertTrue(waitForConnectedNetworkState());

        resetResponse(MY_RESPONSE);
        MacAddress peerMacAddress = MacAddress.fromString(mTestWifiP2pPeerConfig.deviceAddress);
        sWifiP2pManager.removeClient(
                sWifiP2pChannel, peerMacAddress, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#discoverPeers"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testDiscoverPeersOnSpecificFreq() {
        if (!sWifiP2pManager.isChannelConstrainedDiscoverySupported()) return;

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDiscoveryState(
                sWifiP2pChannel, new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED, MY_RESPONSE.discoveryState);

        // If there is any saved network and this device is connecting to this saved network,
        // p2p discovery might be blocked during DHCP provision.
        int retryCount = 3;
        while (retryCount > 0) {
            resetResponse(MY_RESPONSE);
            sWifiP2pManager.discoverPeersOnSpecificFrequency(sWifiP2pChannel,
                    2412, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            if (MY_RESPONSE.success
                    || MY_RESPONSE.failureReason != WifiP2pManager.BUSY) {
                break;
            }
            Log.w(TAG, "Discovery is blocked, try again!");
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) { }
            retryCount--;
        }
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForBroadcasts(MySync.DISCOVERY_STATE));

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDiscoveryState(sWifiP2pChannel,
                new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED, MY_RESPONSE.discoveryState);

        sWifiP2pManager.stopPeerDiscovery(sWifiP2pChannel, null);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#discoverPeersOnSocialChannels"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testDiscoverPeersOnSocialChannelsOnly() {

        if (!sWifiP2pManager.isChannelConstrainedDiscoverySupported()) return;

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDiscoveryState(
                sWifiP2pChannel, new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED, MY_RESPONSE.discoveryState);

        // If there is any saved network and this device is connecting to this saved network,
        // p2p discovery might be blocked during DHCP provision.
        int retryCount = 3;
        while (retryCount > 0) {
            resetResponse(MY_RESPONSE);
            sWifiP2pManager.discoverPeersOnSocialChannels(sWifiP2pChannel, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            if (MY_RESPONSE.success
                    || MY_RESPONSE.failureReason != WifiP2pManager.BUSY) {
                break;
            }
            Log.w(TAG, "Discovery is blocked, try again!");
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) { }
            retryCount--;
        }
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForBroadcasts(MySync.DISCOVERY_STATE));

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDiscoveryState(sWifiP2pChannel,
                new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED, MY_RESPONSE.discoveryState);

        sWifiP2pManager.stopPeerDiscovery(sWifiP2pChannel, null);
    }

    private static OuiKeyedData createTestOuiKeyedData(int oui) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("stringFieldKey", "stringData");
        bundle.putInt("intFieldKey", 789);
        return new OuiKeyedData.Builder(oui, bundle).build();
    }

    private static List<OuiKeyedData> createTestOuiKeyedDataList(int size) {
        List<OuiKeyedData> ouiKeyedDataList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ouiKeyedDataList.add(createTestOuiKeyedData(TEST_OUI));
        }
        return ouiKeyedDataList;
    }

    /**
     * Test that we can trigger a P2P scan using
     * {@link WifiP2pManager#startPeerDiscovery(
     * WifiP2pManager.Channel, WifiP2pDiscoveryConfig, WifiP2pManager.ActionListener)}
     */
    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#startPeerDiscovery"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testStartPeerDiscovery() {
        if (!sWifiP2pManager.isChannelConstrainedDiscoverySupported()) return;

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDiscoveryState(
                sWifiP2pChannel, new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED, MY_RESPONSE.discoveryState);

        WifiP2pDiscoveryConfig discoveryConfig = new WifiP2pDiscoveryConfig.Builder(
                WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ)
                .setFrequencyMhz(2412)
                .setVendorData(createTestOuiKeyedDataList(5))
                .build();

        // If there is any saved network and this device is connecting to this saved network,
        // p2p discovery might be blocked during DHCP provision.
        int retryCount = 3;
        while (retryCount > 0) {
            resetResponse(MY_RESPONSE);
            sWifiP2pManager.startPeerDiscovery(sWifiP2pChannel,
                    discoveryConfig, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            if (MY_RESPONSE.success
                    || MY_RESPONSE.failureReason != WifiP2pManager.BUSY) {
                break;
            }
            Log.w(TAG, "Discovery is blocked, try again!");
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) { }
            retryCount--;
        }
        assertTrue(MY_RESPONSE.success);
        assertTrue(waitForBroadcasts(MySync.DISCOVERY_STATE));

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.requestDiscoveryState(sWifiP2pChannel,
                new WifiP2pManager.DiscoveryStateListener() {
                    @Override
                    public void onDiscoveryStateAvailable(int state) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.discoveryState = state;
                            MY_RESPONSE.notify();
                        }
                    }
                });
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED, MY_RESPONSE.discoveryState);

        sWifiP2pManager.stopPeerDiscovery(sWifiP2pChannel, null);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pConfig.Builder#setGroupClientIpProvisioningMode"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testP2pConnectDoesNotThrowExceptionWhenGroupOwnerIpv6IsNotProvided() {

        if (sWifiP2pManager.isGroupOwnerIPv6LinkLocalAddressProvided()) {
            return;
        }
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString("aa:bb:cc:dd:ee:ff"))
                .setGroupClientIpProvisioningMode(
                        GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL)
                .build();
        sWifiP2pManager.connect(sWifiP2pChannel, config, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertFalse(MY_RESPONSE.success);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pConfig.Builder#setVendorData"})
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testP2pConnectDoesNotThrowExceptionWithVendorData() {
        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(TEST_OUI, new PersistableBundle()).build();
        List<OuiKeyedData> vendorData = Arrays.asList(vendorDataElement);
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString("aa:bb:cc:dd:ee:ff"))
                .build();
        config.setVendorData(vendorData);
        sWifiP2pManager.connect(sWifiP2pChannel, config, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertFalse(MY_RESPONSE.success);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#setVendorElements"})
    @Test
    public void testP2pSetVendorElements() {

        if (!sWifiP2pManager.isSetVendorElementsSupported()) return;

        // Vendor-Specific EID is 221.
        List<ScanResult.InformationElement> ies = new ArrayList<>(Arrays.asList(
                new ScanResult.InformationElement(221, 0,
                        new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 4})));
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.setVendorElements(sWifiP2pChannel, ies, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.discoverPeers(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
    }

    /** Test IEs whose size is greater than the maximum allowed size. */
    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager"
            + "#getP2pMaxAllowedVendorElementsLengthBytes"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testP2pSetVendorElementsOverMaximumAllowedSize() {

        if (!sWifiP2pManager.isSetVendorElementsSupported()) return;

        List<ScanResult.InformationElement> ies = new ArrayList<>();
        ies.add(new ScanResult.InformationElement(221, 0,
                new byte[WifiP2pManager.getP2pMaxAllowedVendorElementsLengthBytes() + 1]));
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            try {
                sWifiP2pManager.setVendorElements(sWifiP2pChannel, ies, sActionListener);
                fail("Should raise IllegalArgumentException");
            } catch (IllegalArgumentException ex) {
                // expected
                return;
            }
        });
    }

    /** Test that external approver APIs. */
    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#addExternalApprover",
            "android.net.wifi.p2p.WifiP2pManager#setConnectionRequestResult",
            "android.net.wifi.p2p.WifiP2pManager#removeExternalApprover"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testP2pExternalApprover() {
        final MacAddress peer = MacAddress.fromString("11:22:33:44:55:66");
        ExternalApproverRequestListener listener =
                new ExternalApproverRequestListener() {
                    @Override
                    public void onAttached(MacAddress deviceAddress) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.targetPeer = deviceAddress;
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.isAttached = true;
                            MY_RESPONSE.notify();
                        }
                    }
                    @Override
                    public void onDetached(MacAddress deviceAddress, int reason) {
                        synchronized (MY_RESPONSE) {
                            MY_RESPONSE.targetPeer = deviceAddress;
                            MY_RESPONSE.detachReason = reason;
                            MY_RESPONSE.valid = true;
                            MY_RESPONSE.isDetached = true;
                            MY_RESPONSE.notify();
                        }
                    }
                    @Override
                    public void onConnectionRequested(int requestType, WifiP2pConfig config,
                            WifiP2pDevice device) {
                    }
                    @Override
                    public void onPinGenerated(MacAddress deviceAddress, String pin) {
                    }
            };

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiP2pManager.addExternalApprover(sWifiP2pChannel, peer, listener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.isAttached);
            assertFalse(MY_RESPONSE.isDetached);
            assertEquals(peer, MY_RESPONSE.targetPeer);

            // Just ignore the result as there is no real incoming request.
            sWifiP2pManager.setConnectionRequestResult(sWifiP2pChannel, peer,
                    WifiP2pManager.CONNECTION_REQUEST_ACCEPT, null);
            sWifiP2pManager.setConnectionRequestResult(sWifiP2pChannel, peer,
                    WifiP2pManager.CONNECTION_REQUEST_ACCEPT, "12345678", null);

            resetResponse(MY_RESPONSE);
            sWifiP2pManager.removeExternalApprover(sWifiP2pChannel, peer, null);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.isDetached);
            assertFalse(MY_RESPONSE.isAttached);
            assertEquals(peer, MY_RESPONSE.targetPeer);
            assertEquals(ExternalApproverRequestListener.APPROVER_DETACH_REASON_REMOVE,
                    MY_RESPONSE.detachReason);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }

    }

    /** Test setWfdInfo() API. */
    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#setWfdInfo"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testP2pSetWfdInfo() {
        WifiP2pWfdInfo info = new WifiP2pWfdInfo();
        info.setEnabled(true);
        info.setDeviceType(WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE);
        info.setSessionAvailable(true);
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            sWifiP2pManager.setWfdInfo(sWifiP2pChannel, info, sActionListener);
            assertTrue(waitForServiceResponse(MY_RESPONSE));
            assertTrue(MY_RESPONSE.success);
        });
    }

    /**
     * Tests {@link WifiP2pManager#getListenState(WifiP2pManager.Channel, Executor, Consumer)}
     */
    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#getListenState"})
    @Test
    public void testGetListenState() {
        Consumer<Integer> testListenStateListener = new Consumer<Integer>() {
            @Override
            public void accept(Integer state) {
                synchronized (MY_RESPONSE) {
                    MY_RESPONSE.valid = true;
                    MY_RESPONSE.listenState = state.intValue();
                    MY_RESPONSE.notify();
                }
            }
        };

        sWifiP2pManager.getListenState(sWifiP2pChannel, mExecutor, testListenStateListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STOPPED, MY_RESPONSE.listenState);

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.startListening(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(waitForBroadcasts(MySync.LISTEN_STATE));

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.getListenState(sWifiP2pChannel, mExecutor, testListenStateListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STARTED, MY_RESPONSE.listenState);

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.stopListening(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(waitForBroadcasts(MySync.LISTEN_STATE));

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.getListenState(sWifiP2pChannel, mExecutor, testListenStateListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STOPPED, MY_RESPONSE.listenState);
    }

    @ApiTest(apis = {"android.net.wifi.WifiP2pManager#getListenState"})
    @Test
    public void testWpsInfo() {
        WpsInfo info = new WpsInfo();
        assertEquals(WpsInfo.INVALID, info.setup);
        assertNull(info.BSSID);
        assertNull(info.pin);
        WpsInfo infoCopy = new WpsInfo(info);
        assertEquals(WpsInfo.INVALID, infoCopy.setup);
        assertNull(infoCopy.BSSID);
        assertNull(infoCopy.pin);
    }

    /**
     * Tests that we can properly get/set fields in {@link WifiP2pDiscoveryConfig}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testWifiP2pDiscoveryConfig() {
        int scanType = WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ;
        int frequencyMhz = 2600;
        WifiP2pDiscoveryConfig config = new WifiP2pDiscoveryConfig.Builder(scanType)
                .setFrequencyMhz(frequencyMhz)
                .build();
        assertEquals(scanType, config.getScanType());
        assertEquals(frequencyMhz, config.getFrequencyMhz());
    }

    /**
     * Tests that we can properly get/set fields in {@link WifiP2pDiscoveryConfig},
     * including the Vendor Data.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testWifiP2pDiscoveryConfigWithVendorData() {
        int scanType = WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ;
        int frequencyMhz = 2600;
        List<OuiKeyedData> vendorData = createTestOuiKeyedDataList(5);
        WifiP2pDiscoveryConfig config = new WifiP2pDiscoveryConfig.Builder(scanType)
                .setFrequencyMhz(frequencyMhz)
                .setVendorData(vendorData)
                .build();
        assertEquals(scanType, config.getScanType());
        assertEquals(frequencyMhz, config.getFrequencyMhz());
        assertTrue(vendorData.equals(config.getVendorData()));
    }

    /**
     * Tests that we can properly set/get vendor data in {@link WifiP2pDevice}.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testWifiP2pDeviceWithVendorData() {
        WifiP2pDevice device = new WifiP2pDevice();
        List<OuiKeyedData> vendorData = createTestOuiKeyedDataList(5);
        device.setVendorData(vendorData);
        assertEquals(vendorData, device.getVendorData());
    }

    private static class TestWifiP2pListener implements WifiP2pManager.WifiP2pListener {
        final Object mP2pListenerLock;
        int mListenState = -1;
        boolean mP2pGroupCreating = false;
        boolean mP2pGroupRemoved = false;
        WifiP2pInfo mP2pInfo = null;
        WifiP2pGroup mP2pGroup = null;

        TestWifiP2pListener(Object lock) {
            mP2pListenerLock = lock;
        }

        public int getListenState() {
            synchronized (mP2pListenerLock) {
                return mListenState;
            }
        }

        public boolean getP2pGroupCreating() {
            synchronized (mP2pListenerLock) {
                return mP2pGroupCreating;
            }
        }

        public WifiP2pInfo getP2pInfo() {
            synchronized (mP2pListenerLock) {
                return mP2pInfo;
            }
        }

        public WifiP2pGroup getP2pGroup() {
            synchronized (mP2pListenerLock) {
                return mP2pGroup;
            }
        }

        @Override
        public void onListenStateChanged(boolean started) {
            synchronized (mP2pListenerLock) {
                mListenState = started ? WifiP2pManager.WIFI_P2P_LISTEN_STARTED
                        : WifiP2pManager.WIFI_P2P_LISTEN_STOPPED;
                mP2pListenerLock.notify();
            }
        }

        @Override
        public void onGroupCreated(@NonNull WifiP2pInfo wifiP2pInfo,
                @NonNull WifiP2pGroup wifiP2pGroup) {
            synchronized (mP2pListenerLock) {
                mP2pInfo = wifiP2pInfo;
                mP2pGroup = wifiP2pGroup;
                mP2pListenerLock.notify();
            }
        }

        @Override
        public void onGroupCreating() {
            synchronized (mP2pListenerLock) {
                mP2pGroupCreating = true;
                mP2pListenerLock.notify();
            }
        }

        @Override
        public void onGroupRemoved() {
            synchronized (mP2pListenerLock) {
                mP2pGroupRemoved = true;
                mP2pListenerLock.notify();
            }
        }
    }

    private void waitForP2pListenerCallbackCalled(TestWifiP2pListener p2pListener) {
        synchronized (p2pListener.mP2pListenerLock) {
            long timeout = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < timeout) {
                try {
                    p2pListener.mP2pListenerLock.wait(WAIT_MS);
                } catch (InterruptedException e) { }
            }
        }
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#registerWifiP2pListener",
            "android.net.wifi.p2p.WifiP2pManager#unregisterWifiP2pListener"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testWifiP2pListenerListenStateChanged() {
        TestWifiP2pListener p2pListener = new TestWifiP2pListener(mLock);

        sWifiP2pManager.registerWifiP2pListener(mExecutor, p2pListener);
        resetResponse(MY_RESPONSE);
        sWifiP2pManager.startListening(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        waitForP2pListenerCallbackCalled(p2pListener);
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STARTED, p2pListener.getListenState());

        resetResponse(MY_RESPONSE);
        sWifiP2pManager.stopListening(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        waitForP2pListenerCallbackCalled(p2pListener);
        assertEquals(WifiP2pManager.WIFI_P2P_LISTEN_STOPPED, p2pListener.getListenState());

        sWifiP2pManager.unregisterWifiP2pListener(p2pListener);
    }

    @ApiTest(apis = {"android.net.wifi.p2p.WifiP2pManager#registerWifiP2pListener",
            "android.net.wifi.p2p.WifiP2pManager#unregisterWifiP2pListener"})
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testWifiP2pListenerGroupCreated() {
        TestWifiP2pListener p2pListener = new TestWifiP2pListener(mLock);

        sWifiP2pManager.registerWifiP2pListener(mExecutor, p2pListener);
        resetResponse(MY_RESPONSE);
        sWifiP2pManager.createGroup(sWifiP2pChannel, sActionListener);
        assertTrue(waitForServiceResponse(MY_RESPONSE));
        assertTrue(MY_RESPONSE.success);
        waitForP2pListenerCallbackCalled(p2pListener);
        assertTrue(p2pListener.getP2pGroupCreating());
        assertTrue(p2pListener.getP2pInfo().groupFormed);
        assertNotNull(p2pListener.getP2pGroup());

        sWifiP2pManager.unregisterWifiP2pListener(p2pListener);
    }
}
