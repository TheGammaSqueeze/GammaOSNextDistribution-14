/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net.wifi.aware.cts;

import static android.Manifest.permission.OVERRIDE_WIFI_CONFIG;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.wifi.aware.AwarePairingConfig.PAIRING_BOOTSTRAPPING_OPPORTUNISTIC;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256;
import static android.net.wifi.aware.IdentityChangedListener.CLUSTER_CHANGE_EVENT_JOINED;
import static android.net.wifi.aware.IdentityChangedListener.CLUSTER_CHANGE_EVENT_STARTED;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.annotation.NonNull;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.AwareParams;
import android.net.wifi.aware.AwareResources;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.ParcelablePeerHandle;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.ServiceDiscoveryInfo;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.cts.WifiBuildCompat;
import android.net.wifi.cts.WifiJUnit3TestBase;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.modules.utils.build.SdkLevel;
import com.android.wifi.flags.Flags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Wi-Fi Aware CTS test suite: single device testing. Performs tests on a single
 * device to validate Wi-Fi Aware.
 */
@AppModeFull(reason = "Cannot get WifiAwareManager in instant app mode")
public class SingleDeviceTest extends WifiJUnit3TestBase {
    private static final String TAG = "WifiAwareCtsTests";

    // wait for Wi-Fi Aware state changes & network requests callbacks
    private static final int WAIT_FOR_AWARE_CHANGE_SECS = 15; // 15 seconds
    private static final int WAIT_FOR_NETWORK_STATE_CHANGE_SECS = 25; // 25 seconds
    private static final int INTERVAL_BETWEEN_TESTS_SECS = 3; // 3 seconds
    private static final int WAIT_FOR_AWARE_INTERFACE_CREATION_SEC = 3; // 3 seconds
    private static final int MIN_DISTANCE_MM = 1 * 1000;
    private static final int MAX_DISTANCE_MM = 3 * 1000;
    private static final byte[] PMK_VALID = "01234567890123456789012345678901".getBytes();
    private static final int AVAILABLE_DATA_PATH_COUNT = 2;
    private static final int AVAILABLE_PUBLISH_SESSION_COUNT = 8;
    private static final int AVAILABLE_SUBSCRIBE_SESSION_COUNT = 8;

    private final Object mLock = new Object();
    private final HandlerThread mHandlerThread = new HandlerThread("SingleDeviceTest");
    private final Handler mHandler;
    private Boolean mWasVerboseLoggingEnabled;

    {
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    private WifiAwareManager mWifiAwareManager;
    private WifiManager mWifiManager;
    private WifiManager.WifiLock mWifiLock;
    private ConnectivityManager mConnectivityManager;

    // used to store any WifiAwareSession allocated during tests - will clean-up after tests
    private final List<WifiAwareSession> mSessions = new ArrayList<>();

    private static class WifiAwareStateBroadcastReceiver extends BroadcastReceiver {
        private final Object mLock = new Object();
        private CountDownLatch mBlocker = new CountDownLatch(1);
        private int mCountNumber = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED.equals(intent.getAction())) {
                synchronized(mLock) {
                    mCountNumber += 1;
                    mBlocker.countDown();
                    mBlocker = new CountDownLatch(1);
                }
            }
        }

        boolean waitForStateChange() throws InterruptedException {
            CountDownLatch blocker;
            synchronized (mLock) {
                mCountNumber--;
                if (mCountNumber >= 0) {
                    return true;
                }
                blocker = mBlocker;
            }
            return blocker.await(WAIT_FOR_AWARE_CHANGE_SECS, TimeUnit.SECONDS);
        }
    }

    private static class WifiAwareResourcesBroadcastReceiver extends BroadcastReceiver {
        private final Object mLock = new Object();
        private CountDownLatch mBlocker = new CountDownLatch(1);
        private int mCountNumber = 0;
        private AwareResources mResources = null;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiAwareManager.ACTION_WIFI_AWARE_RESOURCE_CHANGED.equals(intent.getAction())) {
                synchronized (mLock) {
                    mCountNumber += 1;
                    mBlocker.countDown();
                    mBlocker = new CountDownLatch(1);
                    mResources = intent.getParcelableExtra(WifiAwareManager.EXTRA_AWARE_RESOURCES);
                }
            }
        }

        boolean waitForStateChange() throws InterruptedException {
            CountDownLatch blocker;
            synchronized (mLock) {
                mCountNumber--;
                if (mCountNumber >= 0) {
                    return true;
                }
                blocker = mBlocker;
            }
            return blocker.await(WAIT_FOR_AWARE_CHANGE_SECS, TimeUnit.SECONDS);
        }

        public AwareResources getResources() {
            return mResources;
        }
    }

    private class AttachCallbackTest extends AttachCallback {
        static final int ATTACHED = 0;
        static final int ATTACH_FAILED = 1;
        static final int ERROR = 2; // no callback: timeout, interruption
        static final int TERMINATE = 3;

        private CountDownLatch mBlocker = new CountDownLatch(1);
        private int mCallbackCalled = ERROR; // garbage init
        private WifiAwareSession mSession = null;

        @Override
        public void onAttached(WifiAwareSession session) {
            mCallbackCalled = ATTACHED;
            mSession = session;
            synchronized (mLock) {
                mSessions.add(session);
            }
            mBlocker.countDown();
        }

        @Override
        public void onAttachFailed() {
            mCallbackCalled = ATTACH_FAILED;
            mBlocker.countDown();
        }

        @Override
        public void onAwareSessionTerminated() {
            synchronized (mLock) {
                mSessions.remove(mSession);
            }
            mCallbackCalled = TERMINATE;
            mSession = null;
            mBlocker.countDown();
        }

        /**
         * Waits for any of the callbacks to be called - or an error (timeout, interruption).
         * Returns one of the ATTACHED, ATTACH_FAILED, or ERROR values.
         */
        int waitForAnyCallback() {
            try {
                boolean noTimeout = mBlocker.await(WAIT_FOR_AWARE_CHANGE_SECS, TimeUnit.SECONDS);
                mBlocker = new CountDownLatch(1);
                if (noTimeout) {
                    return mCallbackCalled;
                } else {
                    return ERROR;
                }
            } catch (InterruptedException e) {
                return ERROR;
            }
        }

        /**
         * Access the session created by a callback. Only useful to be called after calling
         * waitForAnyCallback() and getting the ATTACHED code back.
         */
        WifiAwareSession getSession() {
            return mSession;
        }
    }

    private static class IdentityChangedListenerTest extends IdentityChangedListener {
        private final CountDownLatch mBlockerIdentityCallback = new CountDownLatch(1);
        private final CountDownLatch mBlockerClusterIdCallback = new CountDownLatch(1);
        private byte[] mMac = null;
        private MacAddress mClusterId = null;
        private int mClusterEventType = -1;

        @Override
        public void onIdentityChanged(byte[] mac) {
            mMac = mac;
            mBlockerIdentityCallback.countDown();
        }

        @Override
        public void onClusterIdChanged(int clusterEventType, MacAddress clusterId) {
            super.onClusterIdChanged(clusterEventType, clusterId);
            mClusterId = clusterId;
            mClusterEventType = clusterEventType;
            mBlockerClusterIdCallback.countDown();
        }

        /**
         * Waits for the listener callback to be called - or an error (timeout, interruption).
         * Returns true on callback called, false on error (timeout, interruption).
         */
        boolean waitForIdentityListener() {
            try {
                return mBlockerIdentityCallback.await(WAIT_FOR_AWARE_CHANGE_SECS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * Waits for the listener callback to be called - or an error (timeout, interruption).
         * Returns true on callback called, false on error (timeout, interruption).
         */
        boolean waitForClusterIdListener() {
            try {
                return mBlockerClusterIdCallback.await(WAIT_FOR_AWARE_CHANGE_SECS,
                        TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * Returns the MAC address of the discovery interface supplied to the triggered callback.
         */
        byte[] getMac() {
            return mMac;
        }

        /**
         * Returns the clusterId of the cluster changes supplied to the triggered callback.
         */
        MacAddress getClusterId() {
            return mClusterId;
        }

        /**
         * Returns the clusterEventType of the cluster changes supplied to the triggered callback.
         */
        int getClusterEventType() {
            return mClusterEventType;
        }
    }

    private static class DiscoverySessionCallbackTest extends DiscoverySessionCallback {
        static final int ON_PUBLISH_STARTED = 0;
        static final int ON_SUBSCRIBE_STARTED = 1;
        static final int ON_SESSION_CONFIG_UPDATED = 2;
        static final int ON_SESSION_CONFIG_FAILED = 3;
        static final int ON_SESSION_TERMINATED = 4;
        static final int ON_SERVICE_DISCOVERED = 5;
        static final int ON_MESSAGE_SEND_SUCCEEDED = 6;
        static final int ON_MESSAGE_SEND_FAILED = 7;
        static final int ON_MESSAGE_RECEIVED = 8;
        static final int ON_SESSION_DISCOVERED_LOST = 9;
        static final int ON_SESSION_SUSPEND_SUCCEEDED = 10;
        static final int ON_SESSION_SUSPEND_FAILED = 11;
        static final int ON_SESSION_RESUME_SUCCEEDED = 12;
        static final int ON_SESSION_RESUME_FAILED = 13;
        static final int ON_PAIRING_SETUP_SUCCEEDED = 14;
        static final int ON_PAIRING_SETUP_FAILED = 15;
        static final int ON_PAIRING_SETUP_REQUEST_RECEIVED = 16;
        static final int ON_PAIRING_VERIFICATION_SUCCEEDED = 17;
        static final int ON_PAIRING_VERIFICATION_FAILED = 18;
        static final int ON_BOOTSTRAPPING_SUCCEEDED = 19;
        static final int ON_BOOTSTRAPPING_FAILED = 20;

        private final Object mLocalLock = new Object();
        private final ArrayDeque<Integer> mCallbackQueue = new ArrayDeque<>();

        private CountDownLatch mBlocker;
        private int mCurrentWaitForCallback;

        private PublishDiscoverySession mPublishDiscoverySession;
        private SubscribeDiscoverySession mSubscribeDiscoverySession;

        private void processCallback(int callback) {
            synchronized (mLocalLock) {
                if (mBlocker != null && mCurrentWaitForCallback == callback) {
                    mBlocker.countDown();
                } else {
                    mCallbackQueue.addLast(callback);
                }
            }
        }

        @Override
        public void onPublishStarted(PublishDiscoverySession session) {
            super.onPublishStarted(session);
            mPublishDiscoverySession = session;
            processCallback(ON_PUBLISH_STARTED);
        }

        @Override
        public void onSubscribeStarted(SubscribeDiscoverySession session) {
            super.onSubscribeStarted(session);
            mSubscribeDiscoverySession = session;
            processCallback(ON_SUBSCRIBE_STARTED);
        }

        @Override
        public void onSessionConfigUpdated() {
            super.onSessionConfigUpdated();
            processCallback(ON_SESSION_CONFIG_UPDATED);
        }

        @Override
        public void onSessionConfigFailed() {
            super.onSessionConfigFailed();
            processCallback(ON_SESSION_CONFIG_FAILED);
        }

        @Override
        public void onSessionTerminated() {
            super.onSessionTerminated();
            processCallback(ON_SESSION_TERMINATED);
        }

        @Override
        public void onSessionSuspendSucceeded() {
            super.onSessionSuspendSucceeded();
            processCallback(ON_SESSION_SUSPEND_SUCCEEDED);
        }

        @Override
        public void onSessionSuspendFailed(int reason) {
            super.onSessionSuspendFailed(reason);
            processCallback(ON_SESSION_SUSPEND_FAILED);
        }

        @Override
        public void onSessionResumeSucceeded() {
            super.onSessionResumeSucceeded();
            processCallback(ON_SESSION_RESUME_SUCCEEDED);
        }

        @Override
        public void onSessionResumeFailed(int reason) {
            super.onSessionResumeFailed(reason);
            processCallback(ON_SESSION_RESUME_FAILED);
        }

        @Override
        public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
                List<byte[]> matchFilter) {
            super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);
            processCallback(ON_SERVICE_DISCOVERED);
        }

        @Override
        public void onServiceDiscovered(ServiceDiscoveryInfo info) {
            super.onServiceDiscovered(info);
            if (isVendorDataSupported()) {
                assertNotNull(info.getVendorData());
            }
            processCallback(ON_SERVICE_DISCOVERED);
        }

        @Override
        public void onMessageSendSucceeded(int messageId) {
            super.onMessageSendSucceeded(messageId);
            processCallback(ON_MESSAGE_SEND_SUCCEEDED);
        }

        @Override
        public void onMessageSendFailed(int messageId) {
            super.onMessageSendFailed(messageId);
            processCallback(ON_MESSAGE_SEND_FAILED);
        }

        @Override
        public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
            super.onMessageReceived(peerHandle, message);
            processCallback(ON_MESSAGE_RECEIVED);
        }

        @Override
        public void onServiceLost(PeerHandle peerHandle, int reason) {
            super.onServiceLost(peerHandle, reason);
            processCallback(ON_SESSION_DISCOVERED_LOST);
        }

        @Override
        public void onPairingSetupRequestReceived(@NonNull PeerHandle peerHandle, int requestId) {
            super.onPairingSetupRequestReceived(peerHandle, requestId);
            processCallback(ON_PAIRING_SETUP_REQUEST_RECEIVED);
        }

        @Override
        public void onPairingSetupSucceeded(@NonNull PeerHandle peerHandle,
                @NonNull String alias) {
            super.onPairingSetupSucceeded(peerHandle, alias);
            processCallback(ON_PAIRING_SETUP_SUCCEEDED);

        }

        @Override
        public void onPairingSetupFailed(@NonNull PeerHandle peerHandle) {
            super.onPairingSetupFailed(peerHandle);
            processCallback(ON_PAIRING_SETUP_FAILED);
        }

        @Override
        public void onPairingVerificationSucceed(@NonNull PeerHandle peerHandle,
                @NonNull String alias) {
            super.onPairingVerificationSucceed(peerHandle, alias);
            processCallback(ON_PAIRING_VERIFICATION_SUCCEEDED);
        }

        @Override
        public void onPairingVerificationFailed(@NonNull PeerHandle peerHandle) {
            super.onPairingVerificationFailed(peerHandle);
            processCallback(ON_PAIRING_VERIFICATION_FAILED);
        }

        @Override
        public void onBootstrappingSucceeded(@NonNull PeerHandle peerHandle, int method) {
            super.onBootstrappingSucceeded(peerHandle, method);
            processCallback(ON_BOOTSTRAPPING_SUCCEEDED);
        }

        @Override
        public void onBootstrappingFailed(@NonNull PeerHandle peerHandle) {
            super.onBootstrappingFailed(peerHandle);
            processCallback(ON_BOOTSTRAPPING_FAILED);
        }

        /**
         * Wait for the specified callback - any of the ON_* constants. Returns a true
         * on success (specified callback triggered) or false on failure (timed-out or
         * interrupted while waiting for the requested callback).
         *
         * Note: other callbacks happening while while waiting for the specified callback will
         * be queued.
         */
        boolean waitForCallback(int callback) {
            return waitForCallback(callback, WAIT_FOR_AWARE_CHANGE_SECS);
        }

        /**
         * Wait for the specified callback - any of the ON_* constants. Returns a true
         * on success (specified callback triggered) or false on failure (timed-out or
         * interrupted while waiting for the requested callback).
         *
         * Same as waitForCallback(int callback) execpt that allows specifying a custom timeout.
         * The default timeout is a short value expected to be sufficient for all behaviors which
         * should happen relatively quickly. Specifying a custom timeout should only be done for
         * those cases which are known to take a specific longer period of time.
         *
         * Note: other callbacks happening while while waiting for the specified callback will
         * be queued.
         */
        boolean waitForCallback(int callback, int timeoutSec) {
            synchronized (mLocalLock) {
                boolean found = mCallbackQueue.remove(callback);
                if (found) {
                    return true;
                }

                mCurrentWaitForCallback = callback;
                mBlocker = new CountDownLatch(1);
            }

            try {
                return mBlocker.await(timeoutSec, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        /**
         * Indicates whether the specified callback (any of the ON_* constants) has already
         * happened and in the queue. Useful when the order of events is important.
         */
        boolean hasCallbackAlreadyHappened(int callback) {
            synchronized (mLocalLock) {
                return mCallbackQueue.contains(callback);
            }
        }

        /**
         * Returns the last created publish discovery session.
         */
        PublishDiscoverySession getPublishDiscoverySession() {
            PublishDiscoverySession session = mPublishDiscoverySession;
            mPublishDiscoverySession = null;
            return session;
        }

        /**
         * Returns the last created subscribe discovery session.
         */
        SubscribeDiscoverySession getSubscribeDiscoverySession() {
            SubscribeDiscoverySession session = mSubscribeDiscoverySession;
            mSubscribeDiscoverySession = null;
            return session;
        }
    }

    private static class NetworkCallbackTest extends ConnectivityManager.NetworkCallback {
        private final CountDownLatch mBlocker = new CountDownLatch(1);

        @Override
        public void onUnavailable() {
            mBlocker.countDown();
        }

        /**
         * Wait for the onUnavailable() callback to be triggered. Returns true if triggered,
         * otherwise (timed-out, interrupted) returns false.
         */
        boolean waitForOnUnavailable() {
            try {
                return mBlocker.await(WAIT_FOR_NETWORK_STATE_CHANGE_SECS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        assertTrue("Wi-Fi Aware requires Location to be Enabled",
                ((LocationManager) getContext().getSystemService(
                        Context.LOCATION_SERVICE)).isLocationEnabled());

        mWifiAwareManager = (WifiAwareManager) getContext().getSystemService(
                Context.WIFI_AWARE_SERVICE);
        assertNotNull("Wi-Fi Aware Manager", mWifiAwareManager);

        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        assertNotNull("Wi-Fi Manager", mWifiManager);

        // turn on verbose logging for tests
        mWasVerboseLoggingEnabled = ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.isVerboseLoggingEnabled());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setVerboseLoggingEnabled(true));

        // Turn on Wi-Fi
        mWifiLock = mWifiManager.createWifiLock(TAG);
        mWifiLock.acquire();
        if (!mWifiManager.isWifiEnabled()) {
            ShellIdentityUtils.invokeWithShellPermissions(() -> mWifiManager.setWifiEnabled(true));
        }

        mConnectivityManager = (ConnectivityManager) getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
        assertNotNull("Connectivity Manager", mConnectivityManager);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        WifiAwareStateBroadcastReceiver receiver = new WifiAwareStateBroadcastReceiver();
        mContext.registerReceiver(receiver, intentFilter);
        if (!mWifiAwareManager.isAvailable()) {
            assertTrue("Timeout waiting for Wi-Fi Aware to change status",
                    receiver.waitForStateChange());
            assertTrue("Wi-Fi Aware is not available (should be)", mWifiAwareManager.isAvailable());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            super.tearDown();
            return;
        }

        synchronized (mLock) {
            for (WifiAwareSession session : mSessions) {
                // no damage from destroying twice (i.e. ok if test cleaned up after itself already)
                session.close();
            }
            mSessions.clear();
        }

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiManager.setVerboseLoggingEnabled(mWasVerboseLoggingEnabled));

        super.tearDown();
        Thread.sleep(INTERVAL_BETWEEN_TESTS_SECS * 1000);
    }

    /**
     * Validate:
     * - Characteristics are available
     * - Characteristics values are legitimate. Not in the CDD. However, the tested values are
     *   based on the Wi-Fi Aware protocol.
     */
    public void testCharacteristics() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        assertNotNull("Wi-Fi Aware characteristics are null", characteristics);
        assertEquals("Service Name Length", characteristics.getMaxServiceNameLength(), 255);
        assertTrue("Service Specific Information Length",
                characteristics.getMaxServiceSpecificInfoLength() >= 255);
        assertEquals("Match Filter Length", characteristics.getMaxMatchFilterLength(), 255);
        assertNotEquals("Cipher suites", characteristics.getSupportedCipherSuites(), 0);
        assertTrue("Max number of NDP", characteristics.getNumberOfSupportedDataPaths() > 0);
        assertTrue("Max number of NDI", characteristics.getNumberOfSupportedDataInterfaces() > 0);
        assertTrue("Max number of Publish sessions",
                characteristics.getNumberOfSupportedPublishSessions() > 0);
        assertTrue("Max number of Subscribe sessions",
                characteristics.getNumberOfSupportedSubscribeSessions() > 0);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            ShellIdentityUtils.invokeWithShellPermissions(() ->
                    mWifiAwareManager.enableInstantCommunicationMode(true));
            assertEquals(mWifiAwareManager.isInstantCommunicationModeEnabled(),
                    characteristics.isInstantCommunicationModeSupported());
            ShellIdentityUtils.invokeWithShellPermissions(() ->
                    mWifiAwareManager.enableInstantCommunicationMode(false));
        }
        if (characteristics.isAwarePairingSupported()) {
            assertTrue(((characteristics.getSupportedPairingCipherSuites()
                    & WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128) != 0)
                    || ((characteristics.getSupportedPairingCipherSuites()
                    & WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256) != 0));
        }
    }

    /**
     * Validate:
     * - AwareResources are available
     * - AwareResources values are legitimate. When no resources are used, the value should equal to
     *   the capability.
     */
    public void testAvailableAwareResources() {
        if (!(TestUtils.shouldTestWifiAware(getContext())
                && WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext()))) {
            return;
        }
        AwareResources resources = mWifiAwareManager.getAvailableAwareResources();
        assertNotNull("Available aware resources are null", resources);
        assertTrue(resources.getAvailableDataPathsCount() > 0);
        assertTrue(resources.getAvailablePublishSessionsCount() > 0);
        assertTrue(resources.getAvailableSubscribeSessionsCount() > 0);
    }

    /**
     * Validate that on Wi-Fi Aware availability change we get a broadcast + the API returns
     * correct status.
     */
    public void testAvailabilityStatusChange() throws Exception {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);

        // 1. Disable Wi-Fi
        WifiAwareStateBroadcastReceiver receiver1 = new WifiAwareStateBroadcastReceiver();
        mContext.registerReceiver(receiver1, intentFilter);
        ShellIdentityUtils.invokeWithShellPermissions(() -> mWifiManager.setWifiEnabled(false));

        assertTrue("Timeout waiting for Wi-Fi Aware to change status",
                receiver1.waitForStateChange());
        // Interface down event may happen before Wifi State change. In that case, Aware available
        // state will keep true for a short time.
        if (mWifiAwareManager.isAvailable()) {
            assertTrue("Timeout waiting for Wi-Fi Aware to change status",
                    receiver1.waitForStateChange());
        }
        assertFalse("Wi-Fi Aware is available (should not be)", mWifiAwareManager.isAvailable());

        // 2. Enable Wi-Fi
        WifiAwareStateBroadcastReceiver receiver2 = new WifiAwareStateBroadcastReceiver();
        mContext.registerReceiver(receiver2, intentFilter);
        ShellIdentityUtils.invokeWithShellPermissions(() -> mWifiManager.setWifiEnabled(true));

        assertTrue("Timeout waiting for Wi-Fi Aware to change status",
                receiver2.waitForStateChange());
        assertTrue("Wi-Fi Aware is not available (should be)", mWifiAwareManager.isAvailable());
    }

    /**
     * Validate that can attach to Wi-Fi Aware.
     */
    public void testAttachNoIdentity() throws InterruptedException {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        AttachCallbackTest callback = attachAndGetCallback();
        callback.getSession().close();
        callback.waitForAnyCallback();
        assertNull(callback.getSession());
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            Thread.sleep(WAIT_FOR_AWARE_INTERFACE_CREATION_SEC * 1000);
            assertFalse(mWifiAwareManager.isDeviceAttached());
        }
    }

    /**
     * Verify that {@link WifiAwareManager#attach(ConfigRequest, Handler, AttachCallback,
     * IdentityChangedListener)} can be called successfully.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    public void testAttachDiscoveryWithConfigRequest() throws Exception {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            OuiKeyedData vendorDataElement =
                    new OuiKeyedData.Builder(0x00aabbcc, new PersistableBundle()).build();
            List<OuiKeyedData> vendorData = Arrays.asList(vendorDataElement);
            ConfigRequest configRequest = new ConfigRequest.Builder()
                    .setVendorData(vendorData)
                    .build();
            assertEquals(vendorData, configRequest.getVendorData());

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            AttachCallbackTest attachCb = new AttachCallbackTest();
            IdentityChangedListenerTest identityL = new IdentityChangedListenerTest();
            mWifiAwareManager.attach(configRequest, executor, attachCb, identityL);

            assertEquals("Attach callback state", AttachCallbackTest.ATTACHED,
                    attachCb.waitForAnyCallback());
            WifiAwareSession session = attachCb.getSession();
            assertNotNull("Attach callback session", session);
            session.close();
        });
    }

    /**
     * Validate that can attach to Wi-Fi Aware and get identity information. Use the identity
     * information to validate that MAC address changes on every attach.
     *
     * Note: relies on no other entity using Wi-Fi Aware during the CTS test. Since if it is used
     * then the attach/destroy will not correspond to enable/disable and will not result in a new
     * MAC address being generated.
     */
    public void testAttachDiscoveryAddressChanges() throws InterruptedException {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        final int numIterations = 10;
        Set<TestUtils.MacWrapper> macs = new HashSet<>();

        for (int i = 0; i < numIterations; ++i) {
            Thread.sleep(1000);
            AttachCallbackTest attachCb = new AttachCallbackTest();
            IdentityChangedListenerTest identityL = new IdentityChangedListenerTest();
            mWifiAwareManager.attach(attachCb, identityL, mHandler);
            assertEquals("Wi-Fi Aware attach: iteration " + i, AttachCallbackTest.ATTACHED,
                    attachCb.waitForAnyCallback());
            assertTrue("Wi-Fi Aware attach: iteration " + i, identityL.waitForClusterIdListener());
            assertTrue("Wi-Fi Aware attach: iteration " + i, identityL.waitForIdentityListener());

            WifiAwareSession session = attachCb.getSession();
            assertNotNull("Wi-Fi Aware session: iteration " + i, session);

            MacAddress clusterId = identityL.getClusterId();
            assertNotNull("Wi-Fi Aware cluster ID: iteration " + i, clusterId);
            int clusterEventType = identityL.getClusterEventType();
            if (clusterEventType != CLUSTER_CHANGE_EVENT_STARTED
                    && clusterEventType != CLUSTER_CHANGE_EVENT_JOINED) {
                fail("Wi-Fi Aware cluster event type: iteration " + i
                        + ", invalid cluster event type");
            }
            byte[] mac = identityL.getMac();
            assertNotNull("Wi-Fi Aware discovery MAC: iteration " + i, mac);

            session.close();

            macs.add(new TestUtils.MacWrapper(mac));
        }

        assertEquals("", numIterations, macs.size());
    }

    /**
     * Validate a successful publish discovery session lifetime: publish, update publish, destroy.
     */
    public void testPublishDiscoverySuccess() throws Exception {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiAwareManager.ACTION_WIFI_AWARE_RESOURCE_CHANGED);
        WifiAwareResourcesBroadcastReceiver receiver = new WifiAwareResourcesBroadcastReceiver();
        mContext.registerReceiver(receiver, intentFilter);
        final String serviceName = "PublishName";

        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                serviceName).build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();
        int numOfAllPublishSessions = mWifiAwareManager
                .getAvailableAwareResources().getAvailablePublishSessionsCount();

        // 1. publish
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        PublishDiscoverySession discoverySession = discoveryCb.getPublishDiscoverySession();
        assertNotNull("Publish session", discoverySession);
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));
        assertEquals(numOfAllPublishSessions - 1, mWifiAwareManager
                    .getAvailableAwareResources().getAvailablePublishSessionsCount());
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            assertTrue("Time out waiting for resource change", receiver.waitForStateChange());
            assertEquals(numOfAllPublishSessions - 1, receiver.getResources()
                    .getAvailablePublishSessionsCount());
        }

        // 2. update-publish
        PublishConfig.Builder configBuilder = new PublishConfig.Builder()
                .setServiceName(serviceName)
                .setServiceSpecificInfo("extras".getBytes());
        List<OuiKeyedData> vendorData = generateOuiKeyedDataList(5);
        if (isVendorDataSupported()) {
            configBuilder.setVendorData(vendorData);
        }
        publishConfig = configBuilder.build();
        if (isVendorDataSupported()) {
            assertEquals(vendorData, publishConfig.getVendorData());
        }
        discoverySession.updatePublish(publishConfig);
        assertTrue("Publish update", discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_CONFIG_UPDATED));

        // 3. destroy
        assertFalse("Publish not terminated", discoveryCb.hasCallbackAlreadyHappened(
                DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
        discoverySession.close();

        // 4. try update post-destroy: should time-out waiting for cb
        discoverySession.updatePublish(publishConfig);
        assertFalse("Publish update post destroy", discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_CONFIG_UPDATED));
        assertEquals(numOfAllPublishSessions, mWifiAwareManager
                .getAvailableAwareResources().getAvailablePublishSessionsCount());
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            assertTrue("Time out waiting for resource change", receiver.waitForStateChange());
            assertEquals(numOfAllPublishSessions, receiver.getResources()
                    .getAvailablePublishSessionsCount());
            session.close();
        }
    }

    /**
     * Validate that publish with a Time To Live (TTL) setting expires within the specified
     * time (and validates that the terminate callback is triggered).
     */
    public void testPublishLimitedTtlSuccess() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        final String serviceName = "PublishName";
        final int ttlSec = 5;

        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                serviceName).setTtlSec(ttlSec).setTerminateNotificationEnabled(true).build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

        // 1. publish
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        PublishDiscoverySession discoverySession = discoveryCb.getPublishDiscoverySession();
        assertNotNull("Publish session", discoverySession);

        // 2. wait for terminate within 'ttlSec'.
        assertTrue("Publish terminated",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_SESSION_TERMINATED,
                        ttlSec + 5));

        // 3. try update post-termination: should time-out waiting for cb
        publishConfig = new PublishConfig.Builder().setServiceName(
                serviceName).setServiceSpecificInfo("extras".getBytes()).build();
        discoverySession.updatePublish(publishConfig);
        assertFalse("Publish update post terminate", discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_CONFIG_UPDATED));

        session.close();
    }

    /**
     * Validate successful publish session with security config.
     */
    public void testPublishWithSecurityConfig() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        final String serviceName = "PublishName";
        final String passphrase = "SomePassword";
        final byte[] pmk = "01234567890123456789012345678901".getBytes();
        final byte[] pmkId = "0123456789012345".getBytes();


        WifiAwareSession session = attachAndGetSession();
        WifiAwareDataPathSecurityConfig securityConfig = new WifiAwareDataPathSecurityConfig
                .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128)
                .setPskPassphrase(passphrase)
                .build();
        assertEquals(passphrase, securityConfig.getPskPassphrase());
        assertEquals(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                securityConfig.getCipherSuite());
        assertNull(securityConfig.getPmkId());
        assertNull(securityConfig.getPmk());

        PublishConfig.Builder builder = new PublishConfig.Builder()
                .setServiceName(serviceName)
                .setDataPathSecurityConfig(securityConfig);
        PublishConfig publishConfig = builder.build();
        assertEquals(securityConfig, publishConfig.getSecurityConfig());
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

        // 1. publish
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        PublishDiscoverySession discoverySession = discoveryCb.getPublishDiscoverySession();
        assertNotNull("Publish session", discoverySession);
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));

        // 2. update to PK cipher suite
        if ((mWifiAwareManager.getCharacteristics().getSupportedCipherSuites()
                & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128) != 0) {
            securityConfig = new WifiAwareDataPathSecurityConfig
                    .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128)
                    .setPmk(pmk)
                    .setPmkId(pmkId)
                    .build();
            publishConfig = new PublishConfig.Builder()
                    .setServiceName(serviceName)
                    .setDataPathSecurityConfig(securityConfig)
                    .build();
            discoverySession.updatePublish(publishConfig);
            assertTrue("Publish update", discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_CONFIG_UPDATED));
        }

        // 3. destroy
        assertFalse("Publish not terminated", discoveryCb.hasCallbackAlreadyHappened(
                DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
        discoverySession.close();
        session.close();
    }

    /**
     * Validate success publish with instant communacation enabled.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testPublishWithInstantCommunicationModeSuccess() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (!characteristics.isInstantCommunicationModeSupported()) {
            return;
        }
        final String serviceName = "PublishName";
        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder()
                .setServiceName(serviceName)
                .setInstantCommunicationModeEnabled(true, WifiScanner.WIFI_BAND_24_GHZ)
                .build();
        assertEquals(WifiScanner.WIFI_BAND_24_GHZ, publishConfig.getInstantCommunicationBand());
        assertTrue(publishConfig.isInstantCommunicationModeEnabled());

        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

        // 1. publish
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        PublishDiscoverySession discoverySession = discoveryCb.getPublishDiscoverySession();
        assertNotNull("Publish session", discoverySession);
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));

        // 2. destroy
        assertFalse("Publish not terminated", discoveryCb.hasCallbackAlreadyHappened(
                DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
        discoverySession.close();
        session.close();
    }

    /**
     * Validate successful publish with a suspendable session when device supports suspension.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testPublishSuccessWithSuspendableSession() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (!characteristics.isSuspensionSupported()) {
            return;
        }
        final String serviceName = "PublishName";

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            WifiAwareSession session = attachAndGetSession();

            PublishConfig publishConfig = new PublishConfig.Builder()
                    .setServiceName(serviceName)
                    .setSuspendable(true)
                    .build();
            assertTrue(publishConfig.isSuspendable());

            DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

            // 1. publish
            session.publish(publishConfig, discoveryCb, mHandler);
            assertTrue("Publish started",
                    discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
            PublishDiscoverySession discoverySession = discoveryCb.getPublishDiscoverySession();
            assertNotNull("Publish session", discoverySession);
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));

            // 2. destroy
            assertFalse("Publish not terminated", discoveryCb.hasCallbackAlreadyHappened(
                    DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
            discoverySession.close();
            session.close();
        });
    }

    /**
     * Validate failure to publish with a suspendable session when device doesn't support
     * suspension.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testPublishFailureWithSuspendableSession() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (characteristics.isSuspensionSupported()) {
            return;
        }
        final String serviceName = "PublishName";

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            WifiAwareSession session = attachAndGetSession();

            assertThrows(IllegalArgumentException.class, () -> {
                PublishConfig publishConfig = new PublishConfig.Builder()
                        .setServiceName(serviceName)
                        .setSuspendable(true)
                        .build();
                assertTrue(publishConfig.isSuspendable());

                DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();
                session.publish(publishConfig, discoveryCb, mHandler);
            });

            session.close();
        });
    }

    /**
     * Validate successful suspend/resume with a publish session.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @ApiTest(apis = {"android.net.wifi.aware.DiscoverySession#suspend",
            "android.net.wifi.aware.DiscoverySession#resume"})
    public void testSuspendResumeFailWithoutNdpOnPublishSession() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (!characteristics.isSuspensionSupported()) {
            return;
        }
        final String serviceName = "PublishName";

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            WifiAwareSession session = attachAndGetSession();

            PublishConfig publishConfig = new PublishConfig.Builder()
                    .setServiceName(serviceName)
                    .setSuspendable(true)
                    .build();
            assertTrue(publishConfig.isSuspendable());

            DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

            // 1. publish
            session.publish(publishConfig, discoveryCb, mHandler);
            assertTrue("Publish started",
                    discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
            PublishDiscoverySession discoverySession = discoveryCb.getPublishDiscoverySession();
            assertNotNull("Publish session", discoverySession);
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));

            // 2. suspend
            discoverySession.suspend();
            assertTrue(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_SUSPEND_FAILED));

            // 3. resume
            discoverySession.resume();
            assertTrue(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_RESUME_FAILED));

            // 4. destroy
            assertFalse("Publish not terminated", discoveryCb.hasCallbackAlreadyHappened(
                    DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
            discoverySession.close();

            // 5. try suspend/resume post-destroy: should throw exception
            assertThrows(IllegalStateException.class, discoverySession::suspend);
            assertThrows(IllegalStateException.class, discoverySession::resume);

            session.close();
        });
    }

    private static boolean isVendorDataSupported() {
        return SdkLevel.isAtLeastV() && Flags.androidVWifiApi();
    }

    private static OuiKeyedData generateOuiKeyedData(int oui) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("stringKey", "stringValue");
        bundle.putInt("intKey", 789);
        return new OuiKeyedData.Builder(oui, bundle).build();
    }

    private static List<OuiKeyedData> generateOuiKeyedDataList(int size) {
        List<OuiKeyedData> dataList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            dataList.add(generateOuiKeyedData(i + 1));
        }
        return dataList;
    }

    /**
     * Validate a successful subscribe discovery session lifetime: subscribe, update subscribe,
     * destroy.
     */
    public void testSubscribeDiscoverySuccess() throws Exception {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiAwareManager.ACTION_WIFI_AWARE_RESOURCE_CHANGED);
        WifiAwareResourcesBroadcastReceiver receiver = new WifiAwareResourcesBroadcastReceiver();
        mContext.registerReceiver(receiver, intentFilter);
        final String serviceName = "SubscribeName";

        WifiAwareSession session = attachAndGetSession();

        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(
                serviceName).build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();
        int numOfAllSubscribeSessions = mWifiAwareManager
                .getAvailableAwareResources().getAvailableSubscribeSessionsCount();
        // 1. subscribe
        session.subscribe(subscribeConfig, discoveryCb, mHandler);
        assertTrue("Subscribe started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_SUBSCRIBE_STARTED));
        SubscribeDiscoverySession discoverySession = discoveryCb.getSubscribeDiscoverySession();
        assertNotNull("Subscribe session", discoverySession);
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));
        assertEquals(numOfAllSubscribeSessions - 1, mWifiAwareManager
                .getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            assertTrue("Time out waiting for resource change", receiver.waitForStateChange());
            assertEquals(numOfAllSubscribeSessions - 1, receiver.getResources()
                    .getAvailableSubscribeSessionsCount());
        }

        // 2. update-subscribe
        boolean rttSupported = getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_WIFI_RTT);
        SubscribeConfig.Builder builder = new SubscribeConfig.Builder().setServiceName(
                    serviceName).setServiceSpecificInfo("extras".getBytes());

        if (rttSupported) {
            builder.setMinDistanceMm(MIN_DISTANCE_MM);
        }

        List<OuiKeyedData> vendorData = generateOuiKeyedDataList(5);
        if (isVendorDataSupported()) {
            builder.setVendorData(vendorData);
        }

        subscribeConfig = builder.build();
        if (isVendorDataSupported()) {
            assertEquals(vendorData, subscribeConfig.getVendorData());
        }

        discoverySession.updateSubscribe(subscribeConfig);
        assertTrue("Subscribe update", discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_CONFIG_UPDATED));

        // 3. destroy
        assertFalse("Subscribe not terminated", discoveryCb.hasCallbackAlreadyHappened(
                DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
        discoverySession.close();

        // 4. try update post-destroy: should time-out waiting for cb
        discoverySession.updateSubscribe(subscribeConfig);
        assertFalse("Subscribe update post destroy", discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_CONFIG_UPDATED));
        assertEquals(numOfAllSubscribeSessions, mWifiAwareManager
                .getAvailableAwareResources().getAvailableSubscribeSessionsCount());
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            assertTrue("Time out waiting for resource change", receiver.waitForStateChange());
            assertEquals(numOfAllSubscribeSessions, receiver.getResources()
                    .getAvailableSubscribeSessionsCount());
        }

        session.close();
    }

    /**
     * Validate success subscribe with instant communication enabled.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public void testSubscribeWithInstantCommunicationModeSuccess() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (!characteristics.isInstantCommunicationModeSupported()) {
            return;
        }
        final String serviceName = "SubscribeName";
        WifiAwareSession session = attachAndGetSession();

        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                .setServiceName(serviceName)
                .setInstantCommunicationModeEnabled(true, WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        assertEquals(WifiScanner.WIFI_BAND_24_GHZ, subscribeConfig.getInstantCommunicationBand());
        assertTrue(subscribeConfig.isInstantCommunicationModeEnabled());

        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

        // 1. subscribe
        session.subscribe(subscribeConfig, discoveryCb, mHandler);
        assertTrue("Subscribe started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_SUBSCRIBE_STARTED));
        SubscribeDiscoverySession discoverySession = discoveryCb.getSubscribeDiscoverySession();
        assertNotNull("Subscribe session", discoverySession);
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
        assertFalse(discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));

        // 2. destroy
        assertFalse("Subscribe not terminated", discoveryCb.hasCallbackAlreadyHappened(
                DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
        discoverySession.close();
        session.close();
    }

    /**
     * Validate that subscribe with a Time To Live (TTL) setting expires within the specified
     * time (and validates that the terminate callback is triggered).
     */
    public void testSubscribeLimitedTtlSuccess() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        final String serviceName = "SubscribeName";
        final int ttlSec = 5;

        WifiAwareSession session = attachAndGetSession();

        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(
                serviceName).setTtlSec(ttlSec).setTerminateNotificationEnabled(true).build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

        // 1. subscribe
        session.subscribe(subscribeConfig, discoveryCb, mHandler);
        assertTrue("Subscribe started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_SUBSCRIBE_STARTED));
        SubscribeDiscoverySession discoverySession = discoveryCb.getSubscribeDiscoverySession();
        assertNotNull("Subscribe session", discoverySession);

        // 2. wait for terminate within 'ttlSec'.
        assertTrue("Subscribe terminated",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_SESSION_TERMINATED,
                        ttlSec + 5));

        // 3. try update post-termination: should time-out waiting for cb
        subscribeConfig = new SubscribeConfig.Builder().setServiceName(
                serviceName).setServiceSpecificInfo("extras".getBytes()).build();
        discoverySession.updateSubscribe(subscribeConfig);
        assertFalse("Subscribe update post terminate", discoveryCb.waitForCallback(
                DiscoverySessionCallbackTest.ON_SESSION_CONFIG_UPDATED));

        session.close();
    }

    /**
     * Validate successful subscribe with a suspendable session when device supports suspension.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testSubscribeSuccessWithSuspendableSession() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (!characteristics.isSuspensionSupported()) {
            return;
        }
        final String serviceName = "SubscribeName";

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            WifiAwareSession session = attachAndGetSession();

            SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                    .setServiceName(serviceName)
                    .setSuspendable(true)
                    .build();

            assertTrue(subscribeConfig.isSuspendable());

            DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

            // 1. subscribe
            session.subscribe(subscribeConfig, discoveryCb, mHandler);
            assertTrue("Subscribe started",
                    discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_SUBSCRIBE_STARTED));
            SubscribeDiscoverySession discoverySession = discoveryCb.getSubscribeDiscoverySession();
            assertNotNull("Subscribe session", discoverySession);
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));

            // 2. destroy
            assertFalse("Subscribe not terminated", discoveryCb.hasCallbackAlreadyHappened(
                    DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
            discoverySession.close();
            session.close();
        });
    }

    /**
     * Validate failure to subscribe with a suspendable session when device doesn't support
     * suspension.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testSubscribeFailureWithSuspendableSession() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (characteristics.isSuspensionSupported()) {
            return;
        }
        final String serviceName = "SubscribeName";

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            WifiAwareSession session = attachAndGetSession();

            assertThrows(IllegalArgumentException.class, () -> {
                SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                        .setServiceName(serviceName)
                        .setSuspendable(true)
                        .build();

                assertTrue(subscribeConfig.isSuspendable());

                DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();
                session.subscribe(subscribeConfig, discoveryCb, mHandler);
            });

            session.close();
        });
    }

    /**
     * Validate successful suspend/resume with a subscribe session.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @ApiTest(apis = {"android.net.wifi.aware.DiscoverySession#suspend",
            "android.net.wifi.aware.DiscoverySession#resume"})
    public void testSuspendResumeFailWithoutNdpOnSubscribeSession() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (!characteristics.isSuspensionSupported()) {
            return;
        }
        final String serviceName = "SubscribeName";

        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            WifiAwareSession session = attachAndGetSession();

            SubscribeConfig subscribeConfig = new SubscribeConfig.Builder()
                    .setServiceName(serviceName)
                    .setSuspendable(true)
                    .build();

            assertTrue(subscribeConfig.isSuspendable());

            DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

            // 1. subscribe
            session.subscribe(subscribeConfig, discoveryCb, mHandler);
            assertTrue("Subscribe started",
                    discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_SUBSCRIBE_STARTED));
            SubscribeDiscoverySession discoverySession = discoveryCb.getSubscribeDiscoverySession();
            assertNotNull("Subscribe session", discoverySession);
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SERVICE_DISCOVERED));
            assertFalse(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_DISCOVERED_LOST));

            // 2. suspend
            discoverySession.suspend();
            assertTrue(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_SUSPEND_FAILED));

            // 3. resume
            discoverySession.resume();
            assertTrue(discoveryCb.waitForCallback(
                    DiscoverySessionCallbackTest.ON_SESSION_RESUME_FAILED));

            // 4. destroy
            assertFalse("Subscribe not terminated", discoveryCb.hasCallbackAlreadyHappened(
                    DiscoverySessionCallbackTest.ON_SESSION_TERMINATED));
            discoverySession.close();

            // 5. try suspend/resume post-destroy: should throw exception
            assertThrows(IllegalStateException.class, discoverySession::suspend);
            assertThrows(IllegalStateException.class, discoverySession::resume);

            session.close();
        });
    }

    /**
     * Test the send message flow. Since testing single device cannot send to a real peer -
     * validate that sending to a bogus peer fails.
     */
    public void testSendMessageFail() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }

        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                "ValidName").build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();

        // 1. publish
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        PublishDiscoverySession discoverySession = discoveryCb.getPublishDiscoverySession();
        assertNotNull("Publish session", discoverySession);

        // 2. send a message with a null peer-handle - expect exception
        try {
            discoverySession.sendMessage(null, -1290, "some message".getBytes());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // empty
        }

        discoverySession.close();
        session.close();
    }

    /**
     * Request an Aware data-path (open) as a Responder with an arbitrary peer MAC address. Validate
     * that receive an onUnavailable() callback.
     */
    public void testDataPathOpenOutOfBandFail() throws InterruptedException {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        MacAddress mac = MacAddress.fromString("00:01:02:03:04:05");

        // 1. initialize Aware: only purpose is to make sure it is available for OOB data-path
        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                "ValidName").build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        Thread.sleep(WAIT_FOR_AWARE_INTERFACE_CREATION_SEC * 1000);

        // 2. request an AWARE network
        NetworkCallbackTest networkCb = new NetworkCallbackTest();
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(
                session.createNetworkSpecifierOpen(
                        WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR,
                        mac.toByteArray())).build();
        mConnectivityManager.requestNetwork(nr, networkCb);
        assertTrue("OnUnavailable not received", networkCb.waitForOnUnavailable());

        session.close();
    }

    /**
     * Request an Aware data-path (encrypted with Passphrase) as a Responder with an arbitrary peer
     * MAC address.
     * Validate that receive an onUnavailable() callback.
     */
    public void testDataPathPassphraseOutOfBandFail() throws InterruptedException {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        MacAddress mac = MacAddress.fromString("00:01:02:03:04:05");

        // 1. initialize Aware: only purpose is to make sure it is available for OOB data-path
        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                "ValidName").build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        Thread.sleep(WAIT_FOR_AWARE_INTERFACE_CREATION_SEC * 1000);

        // 2. request an AWARE network
        NetworkCallbackTest networkCb = new NetworkCallbackTest();
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(
                session.createNetworkSpecifierPassphrase(
                        WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, mac.toByteArray(),
                        "abcdefghihk")).build();
        mConnectivityManager.requestNetwork(nr, networkCb);
        assertTrue("OnUnavailable not received", networkCb.waitForOnUnavailable());

        session.close();
    }

    /**
     * Request an Aware data-path (encrypted with PMK) as a Responder with an arbitrary peer MAC
     * address.
     * Validate that receive an onUnavailable() callback.
     */
    public void testDataPathPmkOutOfBandFail() throws InterruptedException {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        MacAddress mac = MacAddress.fromString("00:01:02:03:04:05");

        // 1. initialize Aware: only purpose is to make sure it is available for OOB data-path
        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                "ValidName").build();
        DiscoverySessionCallbackTest discoveryCb = new DiscoverySessionCallbackTest();
        session.publish(publishConfig, discoveryCb, mHandler);
        assertTrue("Publish started",
                discoveryCb.waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        Thread.sleep(WAIT_FOR_AWARE_INTERFACE_CREATION_SEC * 1000);

        // 2. request an AWARE network
        NetworkCallbackTest networkCb = new NetworkCallbackTest();
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(
                session.createNetworkSpecifierPmk(
                        WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, mac.toByteArray(),
                        PMK_VALID)).build();
        mConnectivityManager.requestNetwork(nr, networkCb);
        assertTrue("OnUnavailable not received", networkCb.waitForOnUnavailable());

        session.close();
    }

    /**
     * Test WifiAwareNetworkSpecifier.
     */
    public void testWifiAwareNetworkSpecifier() {
        DiscoverySession session = mock(DiscoverySession.class);
        PeerHandle handle = mock(PeerHandle.class);
        WifiAwareNetworkSpecifier networkSpecifier =
                new WifiAwareNetworkSpecifier.Builder(session, handle).build();
        assertFalse(networkSpecifier.canBeSatisfiedBy(null));
        assertTrue(networkSpecifier.canBeSatisfiedBy(networkSpecifier));

        WifiAwareNetworkSpecifier anotherNetworkSpecifier =
                new WifiAwareNetworkSpecifier.Builder(session, handle).setPmk(PMK_VALID).build();
        assertFalse(networkSpecifier.canBeSatisfiedBy(anotherNetworkSpecifier));
    }

    /**
     * Test ParcelablePeerHandle parcel.
     */
    public void testParcelablePeerHandle() {
        PeerHandle peerHandle = mock(PeerHandle.class);
        ParcelablePeerHandle parcelablePeerHandle = new ParcelablePeerHandle(peerHandle);
        Parcel parcelW = Parcel.obtain();
        parcelablePeerHandle.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        ParcelablePeerHandle rereadParcelablePeerHandle =
                ParcelablePeerHandle.CREATOR.createFromParcel(parcelR);

        assertEquals(parcelablePeerHandle, rereadParcelablePeerHandle);
        assertEquals(parcelablePeerHandle.hashCode(), rereadParcelablePeerHandle.hashCode());
    }

    /**
     * Test AwareResources constructor function.
     */
    public void testAwareResourcesConstructor() {
        AwareResources awareResources = new AwareResources(AVAILABLE_DATA_PATH_COUNT,
                AVAILABLE_PUBLISH_SESSION_COUNT, AVAILABLE_SUBSCRIBE_SESSION_COUNT);
        assertEquals(AVAILABLE_DATA_PATH_COUNT, awareResources.getAvailableDataPathsCount());
        assertEquals(AVAILABLE_PUBLISH_SESSION_COUNT, awareResources
                .getAvailablePublishSessionsCount());
        assertEquals(AVAILABLE_SUBSCRIBE_SESSION_COUNT, awareResources
                .getAvailableSubscribeSessionsCount());
    }

    /**
     * Verify setAwareParams works when have permission
     */
    public void testAwareParams() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        AwareParams params = new AwareParams();
        params.setDiscoveryWindowWakeInterval24Ghz(5);
        params.setDiscoveryWindowWakeInterval5Ghz(5);
        params.setDiscoveryBeaconIntervalMillis(50);
        params.setDwEarlyTerminationEnabled(true);
        params.setMacRandomizationIntervalSeconds(1000);
        params.setNumSpatialStreamsInDiscovery(1);
        assertEquals(5, params.getDiscoveryWindowWakeInterval24Ghz());
        assertEquals(5, params.getDiscoveryWindowWakeInterval5Ghz());
        assertEquals(50, params.getDiscoveryBeaconIntervalMillis());
        assertEquals(1000, params.getMacRandomizationIntervalSeconds());
        assertEquals(1, params.getNumSpatialStreamsInDiscovery());
        assertTrue(params.isDwEarlyTerminationEnabled());
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> mWifiAwareManager.setAwareParams(params)
            );
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> mWifiAwareManager.setAwareParams(null)
            );
        }
    }

    /**
     * Verify Aware pairing config class.
     */
    public void testAwarePairingConfig() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        boolean pairingSupported = mWifiAwareManager.getCharacteristics().isAwarePairingSupported();
        AwarePairingConfig config = new AwarePairingConfig.Builder()
                .setPairingCacheEnabled(true)
                .setPairingSetupEnabled(true)
                .setPairingVerificationEnabled(true)
                .setBootstrappingMethods(PAIRING_BOOTSTRAPPING_OPPORTUNISTIC)
                .build();
        assertTrue(config.isPairingCacheEnabled());
        assertTrue(config.isPairingSetupEnabled());
        assertTrue(config.isPairingVerificationEnabled());
        assertEquals(PAIRING_BOOTSTRAPPING_OPPORTUNISTIC, config.getBootstrappingMethods());

        if (!ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }

        WifiAwareSession session = attachAndGetSession();

        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                "ValidName").setPairingConfig(config).build();
        assertEquals(config, publishConfig.getPairingConfig());
        DiscoverySessionCallbackTest discoveryCb1 = new DiscoverySessionCallbackTest();
        // Should send exception when pairing is not supported
        if (!pairingSupported) {
            assertThrows(IllegalArgumentException.class, () ->
                    session.publish(publishConfig, discoveryCb1, mHandler));
        } else {
            session.publish(publishConfig, discoveryCb1, mHandler);
            assertTrue("Publish started", discoveryCb1
                    .waitForCallback(DiscoverySessionCallbackTest.ON_PUBLISH_STARTED));
        }

        DiscoverySessionCallbackTest discoveryCb2 = new DiscoverySessionCallbackTest();
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(
                "ValidName").setPairingConfig(config).build();
        assertEquals(config, subscribeConfig.getPairingConfig());
        // Should send exception when pairing is not supported
        if (!pairingSupported) {
            assertThrows(IllegalArgumentException.class, () ->
                    session.subscribe(subscribeConfig, discoveryCb2, mHandler));
        } else {
            session.subscribe(subscribeConfig, discoveryCb2, mHandler);
            assertTrue("Subscribe started", discoveryCb2
                    .waitForCallback(DiscoverySessionCallbackTest.ON_SUBSCRIBE_STARTED));
        }
    }

    public void testAttachOffload() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            boolean hasPermission = mContext.checkCallingOrSelfPermission(OVERRIDE_WIFI_CONFIG)
                    == PERMISSION_GRANTED;
            // Attach offload session
            final AttachCallbackTest attachCb = new AttachCallbackTest();
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            if (!hasPermission) {
                assertThrows(SecurityException.class, () ->
                        mWifiAwareManager.attachOffload(executor, attachCb));
                return;
            }
            mWifiAwareManager.attachOffload(executor, attachCb);
            int cbCalled = attachCb.waitForAnyCallback();
            assertEquals("Wi-Fi Aware attach", AttachCallbackTest.ATTACHED, cbCalled);
            // Attach a normal session offload session should be terminated
            attachAndGetCallback();
            cbCalled = attachCb.waitForAnyCallback();
            assertEquals("Wi-Fi Aware session terminate", AttachCallbackTest.TERMINATE, cbCalled);
            assertNull(attachCb.getSession());
            // Attach offload again, should fail.
            final AttachCallbackTest attachCb1 = new AttachCallbackTest();

            mWifiAwareManager.attachOffload(executor, attachCb1);
            cbCalled = attachCb1.waitForAnyCallback();
            assertEquals("Wi-Fi Aware attach", AttachCallbackTest.ATTACH_FAILED, cbCalled);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Verify setAwareParams throw exception without permission
     */
    public void testAwareParamsWithoutPermission() {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        assertThrows(SecurityException.class, () -> mWifiAwareManager.setAwareParams(null));
    }

    /**
     * Verify {@link WifiAwareManager#setOpportunisticModeEnabled(boolean)} and
     * {@link WifiAwareManager#isOpportunisticModeEnabled(Executor, Consumer)}
     */
    public void testSetOpportunistic() throws InterruptedException {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        AtomicBoolean enabled = new AtomicBoolean(false);
        Consumer<Boolean> result = value -> {
            synchronized (mLock) {
                enabled.set(value);
                mLock.notify();
            }
        };
        try {
            mWifiAwareManager.setOpportunisticModeEnabled(true);
            mWifiAwareManager.isOpportunisticModeEnabled(
                    Executors.newSingleThreadScheduledExecutor(),
                    result);
            synchronized (mLock) {
                mLock.wait(WAIT_FOR_AWARE_CHANGE_SECS * 1000);
            }
            assertTrue(enabled.get());
            attachAndGetSession();
            if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                return;
            }
            AtomicBoolean called = new AtomicBoolean(false);
            AtomicBoolean canBeCreated = new AtomicBoolean(false);
            AtomicReference<Set<WifiManager.InterfaceCreationImpact>>
                    interfacesWhichWillBeDeleted = new AtomicReference<>(null);
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> mWifiManager.reportCreateInterfaceImpact(
                            WifiManager.WIFI_INTERFACE_TYPE_DIRECT, true,
                            Executors.newSingleThreadScheduledExecutor(),
                            (canBeCreatedLocal, interfacesWhichWillBeDeletedLocal) -> {
                                synchronized (mLock) {
                                    canBeCreated.set(canBeCreatedLocal);
                                    called.set(true);
                                    interfacesWhichWillBeDeleted
                                            .set(interfacesWhichWillBeDeletedLocal);
                                    mLock.notify();
                                }
                            }));
            synchronized (mLock) {
                mLock.wait(WAIT_FOR_AWARE_CHANGE_SECS * 1000);
            }
            assertTrue(called.get());
            if (canBeCreated.get()) {
                for (WifiManager.InterfaceCreationImpact entry
                        : interfacesWhichWillBeDeleted.get()) {
                    int interfaceType = entry.getInterfaceType();
                    assertEquals(WifiManager.WIFI_INTERFACE_TYPE_AWARE, interfaceType);
                    Set<String> packages = entry.getPackages();
                    assertTrue(packages.isEmpty());
                }
            }
        } finally {
            mWifiAwareManager.setOpportunisticModeEnabled(false);
        }
    }

    public void testSetMasterPreference() throws InterruptedException  {
        if (!TestUtils.shouldTestWifiAware(getContext())) {
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            AtomicInteger mp = new AtomicInteger(-1);
            Consumer<Integer> result = value -> {
                mp.set(value);
                mLock.notify();
            };
            Executor executor = Executors.newSingleThreadScheduledExecutor();
            WifiAwareSession session = attachAndGetSession();
            if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                // Shell doesn't have permission before T.
                assertThrows(SecurityException.class, () -> session
                        .getMasterPreference(executor, result));
                assertThrows(SecurityException.class, () -> session
                        .setMasterPreference(254));
                return;
            }
            session.getMasterPreference(executor, result);
            synchronized (mLock) {
                mLock.wait(WAIT_FOR_AWARE_CHANGE_SECS * 1000);
            }
            assertEquals(0, mp.get());
            session.setMasterPreference(254);
            session.getMasterPreference(executor, result);
            synchronized (mLock) {
                mLock.wait(WAIT_FOR_AWARE_CHANGE_SECS * 1000);
            }
            assertEquals(254, mp.get());
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    // local utilities

    private WifiAwareSession attachAndGetSession() {
        AttachCallbackTest attachCb = new AttachCallbackTest();
        mWifiAwareManager.attach(attachCb, mHandler);
        int cbCalled = attachCb.waitForAnyCallback();
        assertEquals("Wi-Fi Aware attach", AttachCallbackTest.ATTACHED, cbCalled);

        WifiAwareSession session = attachCb.getSession();
        assertNotNull("Wi-Fi Aware session", session);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            assertTrue(mWifiAwareManager.isDeviceAttached());
        }

        return session;
    }

    // local utilities

    private AttachCallbackTest attachAndGetCallback() {
        AttachCallbackTest attachCb = new AttachCallbackTest();
        mWifiAwareManager.attach(attachCb, mHandler);
        int cbCalled = attachCb.waitForAnyCallback();
        assertEquals("Wi-Fi Aware attach", AttachCallbackTest.ATTACHED, cbCalled);
        return attachCb;
    }
}
