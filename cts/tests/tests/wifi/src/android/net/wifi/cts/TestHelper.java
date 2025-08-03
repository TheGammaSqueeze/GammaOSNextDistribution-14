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

package android.net.wifi.cts;

import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.net.ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.wifi.WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_UNKNOWN;
import static android.os.Process.myUid;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.app.UiAutomation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.WorkSource;
import android.support.test.uiautomator.UiDevice;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class to hold helper methods that are repeated across wifi CTS tests.
 */
public class TestHelper {
    private static final String TAG = "WifiTestHelper";

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final ConnectivityManager mConnectivityManager;
    private final UiDevice mUiDevice;

    private static final int DURATION_MILLIS = 10_000;
    private static final int DURATION_NETWORK_CONNECTION_MILLIS = 40_000;
    private static final int DURATION_SCREEN_TOGGLE_MILLIS = 2000;
    private static final int DURATION_UI_INTERACTION_MILLIS = 25_000;
    private static final int SCAN_RETRY_CNT_TO_FIND_MATCHING_BSSID = 3;
    private static List<ScanResult> sScanResults = null;

    public TestHelper(@NonNull Context context, @NonNull UiDevice uiDevice) {
        mContext = context;
        mWifiManager = context.getSystemService(WifiManager.class);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mUiDevice = uiDevice;
    }

    public void turnScreenOn() throws Exception {
        mUiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE_MILLIS);
    }

    public void turnScreenOff() throws Exception {
        mUiDevice.executeShellCommand("input keyevent KEYCODE_SLEEP");
        // Since the screen on/off intent is ordered, they will not be sent right now.
        Thread.sleep(DURATION_SCREEN_TOGGLE_MILLIS);
    }

    private static class TestScanResultsCallback extends WifiManager.ScanResultsCallback {
        private final CountDownLatch mCountDownLatch;
        public boolean onAvailableCalled = false;

        TestScanResultsCallback() {
            mCountDownLatch = new CountDownLatch(1);
        }

        public void await() throws InterruptedException {
            mCountDownLatch.await(DURATION_MILLIS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onScanResultsAvailable() {
            onAvailableCalled = true;
            mCountDownLatch.countDown();
        }
    }

    /**
     * Find the first saved network available in the scan results with specific capabilities
     * support.
     *
     * @param wifiManager WifiManager service
     * @param savedNetworks List of saved networks on the device.
     * @param capabilities Network capabilities to be matched. This parameter is ignored if set
     *                     to 0. See AP_CAPABILITIES_BIT_XXX for the supported capabilities.
     * @return WifiConfiguration for the network
     */
    public static WifiConfiguration findFirstAvailableSavedNetwork(@NonNull WifiManager wifiManager,
            @NonNull List<WifiConfiguration> savedNetworks,
            long capabilities) {
        if (savedNetworks.isEmpty()) return null;
        List<WifiConfiguration> matchingNetworks = new ArrayList<>();
        Map<Integer, List<WifiConfiguration>> networksMap =
                findMatchingSavedNetworksWithBssidByBand(wifiManager, savedNetworks,
                        capabilities, 1);
        for (List<WifiConfiguration> configs : networksMap.values()) {
            matchingNetworks.addAll(configs);
        }
        if (matchingNetworks.isEmpty()) return null;
        return matchingNetworks.get(0);
    }

    /**
     * Loops through all the saved networks available in the scan results. Returns a list of
     * WifiConfiguration with the matching bssid filled in {@link WifiConfiguration#BSSID}.
     *
     * Note:
     * a) If there are more than 2 networks with the same SSID, but different credential type, then
     * this matching may pick the wrong one.
     *
     * @param wifiManager   WifiManager service
     * @param savedNetworks List of saved networks on the device.
     * @return List of WifiConfiguration with matching bssid.
     */
    public static List<WifiConfiguration> findMatchingSavedNetworksWithBssid(
            @NonNull WifiManager wifiManager, @NonNull List<WifiConfiguration> savedNetworks,
            int numberOfApRequested) {
        if (savedNetworks.isEmpty()) return Collections.emptyList();
        List<WifiConfiguration> matchingNetworksWithBssids = new ArrayList<>();
        Map<Integer, List<WifiConfiguration>> networksMap =
                findMatchingSavedNetworksWithBssidByBand(wifiManager, savedNetworks,
                        0, numberOfApRequested);
        for (List<WifiConfiguration> configs : networksMap.values()) {
            matchingNetworksWithBssids.addAll(configs);
        }
        return matchingNetworksWithBssids;
    }

    public static final long AP_CAPABILITY_BIT_WIFI7 = 1 << 0;
    public static final long AP_CAPABILITY_BIT_TWT_RESPONDER = 1 << 1;

    /**
     * Check whether scan result matches with the capabilities provided.
     *
     * @param scanResult Scan result
     * @param capabilities Capabilities to be matched. See AP_CAPABILITY_BIT_XXX for the
     *                     available bits.
     * @return true if the scan result matches or capabilities is 0 , otherwise false.
     */
    public static boolean isMatchedScanResult(ScanResult scanResult, long capabilities) {
        if (capabilities == 0) return true;
        if ((capabilities & AP_CAPABILITY_BIT_WIFI7) == AP_CAPABILITY_BIT_WIFI7 && (
                scanResult.getWifiStandard()
                        != ScanResult.WIFI_STANDARD_11BE)) {
            return false;
        }
        if ((capabilities & AP_CAPABILITY_BIT_TWT_RESPONDER) == AP_CAPABILITY_BIT_TWT_RESPONDER
                && !scanResult.isTwtResponder()) {
            return false;
        }
        return true;
    }

    /**
     * Loops through all the saved networks available in the scan results. Returns a map of lists of
     * WifiConfiguration with the matching bssid filled in {@link WifiConfiguration#BSSID}.
     *
     * Note:
     * a) If there are more than 2 networks with the same SSID, but different credential type, then
     * this matching may pick the wrong one.
     *
     * @param wifiManager   WifiManager service
     * @param savedNetworks List of saved networks on the device.
     * @param  capabilities AP capabilities to be matched. See AP_CAPABILITY_BIT_XXX for the
     *                     supported capabilities. This parameter is ignored if set to 0.
     *
     * @param numberOfApRequested Number of APs requested
     * @return Map from band to the list of WifiConfiguration with matching bssid.
     */
    public static Map<Integer, List<WifiConfiguration>> findMatchingSavedNetworksWithBssidByBand(
            @NonNull WifiManager wifiManager, @NonNull List<WifiConfiguration> savedNetworks,
            long capabilities, int numberOfApRequested) {
        if (savedNetworks.isEmpty()) return Collections.emptyMap();
        Set<String> bssidSet = new HashSet<>();
        Map<Integer, List<WifiConfiguration>> matchingNetworksWithBssids = new ArrayMap<>();
        for (int i = 0; i < SCAN_RETRY_CNT_TO_FIND_MATCHING_BSSID; i++) {
            int count = 0;
            // Trigger a scan to get fresh scan results.
            TestScanResultsCallback scanResultsCallback = new TestScanResultsCallback();
            try {
                wifiManager.registerScanResultsCallback(
                        Executors.newSingleThreadExecutor(), scanResultsCallback);
                wifiManager.startScan(new WorkSource(myUid()));
                // now wait for callback
                scanResultsCallback.await();
            } catch (InterruptedException e) {
            } finally {
                wifiManager.unregisterScanResultsCallback(scanResultsCallback);
            }
            sScanResults = wifiManager.getScanResults();
            if (sScanResults == null || sScanResults.isEmpty()) continue;
            for (ScanResult scanResult : sScanResults) {
                if (!isMatchedScanResult(scanResult, capabilities) || bssidSet.contains(
                        scanResult.BSSID)) {
                    continue;
                }
                WifiConfiguration matchingNetwork = savedNetworks.stream()
                        .filter(network -> TextUtils.equals(
                                scanResult.SSID, WifiInfo.sanitizeSsid(network.SSID)))
                        .findAny()
                        .orElse(null);
                if (matchingNetwork != null) {
                    // make a copy in case we have 2 bssid's for the same network.
                    WifiConfiguration matchingNetworkCopy = new WifiConfiguration(matchingNetwork);
                    matchingNetworkCopy.BSSID = scanResult.BSSID;
                    bssidSet.add(scanResult.BSSID);
                    List<WifiConfiguration> bandConfigs =
                            matchingNetworksWithBssids.computeIfAbsent(
                                    scanResult.getBand(), k -> new ArrayList<>());
                    bandConfigs.add(matchingNetworkCopy);
                }
            }
            if (bssidSet.size() >= numberOfApRequested
                    && !matchingNetworksWithBssids.isEmpty()) break;
        }
        return matchingNetworksWithBssids;
    }

    /**
     * Convert the provided saved network to a corresponding suggestion builder.
     */
    public static WifiNetworkSuggestion.Builder
            createSuggestionBuilderWithCredentialFromSavedNetworkWithBssid(
            @NonNull WifiConfiguration network) {
        WifiNetworkSuggestion.Builder suggestionBuilder = new WifiNetworkSuggestion.Builder()
                .setSsid(WifiInfo.sanitizeSsid(network.SSID))
                .setBssid(MacAddress.fromString(network.BSSID));
        if (network.preSharedKey != null) {
            if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                suggestionBuilder.setWpa2Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
            } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                suggestionBuilder.setWpa3Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
            } else {
                fail("Unsupported security type found in saved networks");
            }
        } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.OWE)) {
            suggestionBuilder.setIsEnhancedOpen(true);
        } else if (!network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
            fail("Unsupported security type found in saved networks");
        }
        suggestionBuilder.setIsHiddenSsid(network.hiddenSSID);
        return suggestionBuilder;
    }


    /**
     * Convert the provided saved network to a corresponding specifier builder.
     */
    public static WifiNetworkSpecifier.Builder createSpecifierBuilderWithCredentialFromSavedNetwork(
            @NonNull WifiConfiguration network, boolean useChannel) {
        WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder()
                .setSsid(WifiInfo.sanitizeSsid(network.SSID));
        if (network.preSharedKey != null) {
            if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
                specifierBuilder.setWpa2Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
            } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) {
                specifierBuilder.setWpa3Passphrase(WifiInfo.sanitizeSsid(network.preSharedKey));
            } else {
                fail("Unsupported security type found in saved networks");
            }
        } else if (network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.OWE)) {
            specifierBuilder.setIsEnhancedOpen(true);
        } else if (!network.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
            fail("Unsupported security type found in saved networks");
        }
        specifierBuilder.setIsHiddenSsid(network.hiddenSSID);
        if (sScanResults != null && useChannel) {
            Optional<ScanResult> matchedResult = sScanResults
                    .stream()
                    .filter(scanResult -> TextUtils.equals(scanResult.SSID,
                            WifiInfo.sanitizeSsid(network.SSID))
                            && TextUtils.equals(scanResult.BSSID, network.BSSID)).findAny();
            matchedResult.ifPresent(
                    scanResult -> specifierBuilder.setPreferredChannelsFrequenciesMhz(
                            new int[]{scanResult.frequency}));
        }
        return specifierBuilder;
    }

    /**
     * Convert the provided saved network to a corresponding specifier builder.
     */
    public static WifiNetworkSpecifier.Builder
            createSpecifierBuilderWithCredentialFromSavedNetworkWithBssid(
            @NonNull WifiConfiguration network) {
        return createSpecifierBuilderWithCredentialFromSavedNetwork(network, false)
                .setBssid(MacAddress.fromString(network.BSSID));
    }

    private static class TestLocalOnlyListener implements WifiManager
            .LocalOnlyConnectionFailureListener {
        private CountDownLatch mBlocker;
        public boolean onFailureCalled = false;
        public int failureReason = STATUS_LOCAL_ONLY_CONNECTION_FAILURE_UNKNOWN;
        TestLocalOnlyListener() {
            mBlocker = new CountDownLatch(1);
        }

        @Override
        public void onConnectionFailed(
                @androidx.annotation.NonNull WifiNetworkSpecifier wifiNetworkSpecifier,
                int failureReason) {
            mBlocker.countDown();
            onFailureCalled = true;
        }

        public boolean await(long timeout) throws Exception {
            return mBlocker.await(timeout, TimeUnit.MILLISECONDS);
        }

    }

    public static class TestNetworkCallback extends ConnectivityManager.NetworkCallback {
        private CountDownLatch mBlocker;
        public boolean onAvailableCalled = false;
        public boolean onUnavailableCalled = false;
        public boolean onLostCalled = false;
        public NetworkCapabilities networkCapabilities;

        TestNetworkCallback() {
            mBlocker = new CountDownLatch(1);
        }

        TestNetworkCallback(int flags) {
            super(flags);
            mBlocker = new CountDownLatch(1);
        }

        public boolean await(long timeout) throws Exception {
            return mBlocker.await(timeout, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onAvailable(Network network) {
            Log.i(TAG, "onAvailable " + network);
            onAvailableCalled = true;
        }

        @Override
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {
            Log.i(TAG, "onCapabilitiesChanged " + network);
            this.networkCapabilities = networkCapabilities;
            mBlocker.countDown();
        }

        @Override
        public void onUnavailable() {
            Log.i(TAG, "onUnavailable ");
            onUnavailableCalled = true;
            mBlocker.countDown();
        }

        @Override
        public void onLost(Network network) {
            onLostCalled = true;
            mBlocker.countDown();
        }

        boolean waitForAnyCallback(int timeout) {
            try {
                boolean noTimeout = mBlocker.await(timeout, TimeUnit.MILLISECONDS);
                mBlocker = new CountDownLatch(1);
                return noTimeout;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    private static TestNetworkCallback createTestNetworkCallback() {
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            // flags for NetworkCallback only introduced in S.
            return new TestNetworkCallback(FLAG_INCLUDE_LOCATION_INFO);
        } else {
            return new TestNetworkCallback();
        }
    }

    @NonNull
    private WifiInfo getWifiInfo(@NonNull NetworkCapabilities networkCapabilities) {
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            // WifiInfo in transport info, only available in S.
            return (WifiInfo) networkCapabilities.getTransportInfo();
        } else {
            return mWifiManager.getConnectionInfo();
        }
    }

    private static void assertConnectionEquals(@NonNull WifiConfiguration network,
            @NonNull WifiInfo wifiInfo) {
        assertThat(network.SSID).isEqualTo(wifiInfo.getSSID());
        assertThat(network.BSSID).isEqualTo(wifiInfo.getBSSID());
    }

    private static class TestActionListener implements WifiManager.ActionListener {
        private final CountDownLatch mCountDownLatch;
        public boolean onSuccessCalled = false;
        public boolean onFailedCalled = false;

        TestActionListener(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() {
            onSuccessCalled = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(int reason) {
            onFailedCalled = true;
            mCountDownLatch.countDown();
        }
    }

    /**
     * Triggers connection to one of the saved networks using {@link WifiManager#connect(
     * WifiConfiguration, WifiManager.ActionListener)}
     *
     * @param network saved network from the device to use for the connection.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithConnect(
            @NonNull WifiConfiguration network) throws Exception {
        CountDownLatch countDownLatchAl = new CountDownLatch(1);
        TestActionListener actionListener = new TestActionListener(countDownLatchAl);
        TestNetworkCallback testNetworkCallback = createTestNetworkCallback();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            // File a callback for wifi network.
            mConnectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            // Needed to ensure that the restricted concurrent connection does not
                            // match this request.
                            .addForbiddenCapability(NET_CAPABILITY_OEM_PAID)
                            .addForbiddenCapability(NET_CAPABILITY_OEM_PRIVATE)
                            .build(),
                    testNetworkCallback);
            // Trigger the connection.
            mWifiManager.connect(network, actionListener);
            // now wait for action listener callback
            assertThat(countDownLatchAl.await(
                    DURATION_NETWORK_CONNECTION_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            // check if we got the success callback
            assertThat(actionListener.onSuccessCalled).isTrue();

            // Wait for connection to complete & ensure we are connected to the saved network.
            assertThat(testNetworkCallback.waitForAnyCallback(DURATION_NETWORK_CONNECTION_MILLIS))
                    .isTrue();
            assertThat(testNetworkCallback.onAvailableCalled).isTrue();
            final WifiInfo wifiInfo = getWifiInfo(testNetworkCallback.networkCapabilities);
            assertConnectionEquals(network, wifiInfo);
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                // User connections should always be primary.
                assertThat(wifiInfo.isPrimary()).isTrue();
            }
        } catch (Throwable e /* catch assertions & exceptions */) {
            // Unregister the network callback in case of any failure (since we don't end up
            // returning the network callback to the caller).
            try {
                mConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            } catch (IllegalArgumentException ie) { }
            throw e;
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        return testNetworkCallback;
    }

    /**
     * Tests the entire connection success flow using the provided suggestion.
     *
     * Note: The caller needs to invoke this after acquiring shell identity.
     *
     * @param network saved network from the device to use for the connection.
     * @param suggestion suggestion to use for the connection.
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param restrictedNetworkCapabilities Whether this connection should be restricted with
     *                                    the provided capability.
     *
     * @param isRestricted whether the suggestion is for a restricted network
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithSuggestionWithShellIdentity(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @NonNull ScheduledExecutorService executorService,
            @NonNull Set<Integer> restrictedNetworkCapabilities,
            boolean isRestricted) throws Exception {
        return testConnectionFlowWithSuggestionInternal(
                network, suggestion, executorService, restrictedNetworkCapabilities, true,
                isRestricted);
    }

    /**
     * Tests the entire connection success flow using the provided suggestion.
     *
     * Note: The helper method drops the shell identity, so don't use this if the caller already
     * adopted shell identity.
     *
     * @param network saved network from the device to use for the connection.
     * @param suggestion suggestion to use for the connection.
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param restrictedNetworkCapabilities Whether this connection should be restricted with
     *                                    the provided capability.
     *
     * @param isRestricted whether the suggestion is for a restricted network
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithSuggestion(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @NonNull ScheduledExecutorService executorService,
            @NonNull Set<Integer> restrictedNetworkCapabilities,
            boolean isRestricted) throws Exception {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(NETWORK_SETTINGS, CONNECTIVITY_INTERNAL);
            return testConnectionFlowWithSuggestionWithShellIdentity(
                    network, suggestion, executorService, restrictedNetworkCapabilities,
                    isRestricted);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests the connection failure flow using the provided suggestion.
     *
     * @param network saved network from the device to use for the connection.
     * @param suggestion suggestion to use for the connection.
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param restrictedNetworkCapabilities Whether this connection should be restricted with
     *                                    the provided capability.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFailureFlowWithSuggestion(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @NonNull ScheduledExecutorService executorService,
            @NonNull Set<Integer> restrictedNetworkCapabilities) throws Exception {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(NETWORK_SETTINGS, CONNECTIVITY_INTERNAL);
            return testConnectionFlowWithSuggestionInternal(
                    network, suggestion, executorService, restrictedNetworkCapabilities, false,
                    false/* restrictedNetwork */);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Tests the entire connection success/failure flow using the provided suggestion.
     *
     * @param network saved network from the device to use for the connection.
     * @param suggestion suggestion to use for the connection.
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param restrictedNetworkCapabilities Whether this connection should be restricted with
     *                                    the provided capability.
     * @param expectConnectionSuccess Whether to expect connection success or not.
     *
     * @param isRestricted whether the suggestion is for a restricted network
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    private ConnectivityManager.NetworkCallback testConnectionFlowWithSuggestionInternal(
            WifiConfiguration network, WifiNetworkSuggestion suggestion,
            @NonNull ScheduledExecutorService executorService,
            @NonNull Set<Integer> restrictedNetworkCapabilities,
            boolean expectConnectionSuccess, boolean isRestricted) throws Exception {
        // File the network request & wait for the callback.
        TestNetworkCallback testNetworkCallback = createTestNetworkCallback();
        try {
            // File a request for restricted (oem paid) wifi network.
            NetworkRequest.Builder nrBuilder = new NetworkRequest.Builder()
                    .addTransportType(TRANSPORT_WIFI)
                    .addCapability(NET_CAPABILITY_INTERNET);
            if (restrictedNetworkCapabilities.isEmpty() && !isRestricted) {
                // If not a restricted connection, a network callback is sufficient.
                mConnectivityManager.registerNetworkCallback(
                        nrBuilder.build(), testNetworkCallback);
            } else {
                for (Integer restrictedNetworkCapability : restrictedNetworkCapabilities) {
                    nrBuilder.addCapability(restrictedNetworkCapability);
                }
                nrBuilder.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
                mConnectivityManager.requestNetwork(nrBuilder.build(), testNetworkCallback);
            }
            // Add wifi network suggestion.
            assertThat(mWifiManager.addNetworkSuggestions(Arrays.asList(suggestion)))
                    .isEqualTo(WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS);
            // Wait for the request to reach the wifi stack before kick-start periodic scans.
            Thread.sleep(100);
            // Step: Trigger scans periodically to trigger network selection quicker.
            executorService.scheduleAtFixedRate(() -> {
                if (!mWifiManager.startScan()) {
                    Log.w(TAG, "Failed to trigger scan");
                }
            }, 0, DURATION_MILLIS, TimeUnit.MILLISECONDS);
            if (expectConnectionSuccess) {
                // now wait for connection to complete and wait for callback
                assertThat(testNetworkCallback
                        .waitForAnyCallback(DURATION_NETWORK_CONNECTION_MILLIS)).isTrue();
                assertThat(testNetworkCallback.onAvailableCalled).isTrue();
                final WifiInfo wifiInfo = getWifiInfo(testNetworkCallback.networkCapabilities);
                assertConnectionEquals(network, wifiInfo);
                assertThat(wifiInfo.isTrusted()).isTrue();
                assertThat(wifiInfo.isRestricted()).isEqualTo(isRestricted);
                WifiInfo redact = wifiInfo
                        .makeCopy(NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION);
                assertThat(wifiInfo.getInformationElements()).isNotNull();
                assertThat(redact.getInformationElements()).isNull();
                assertThat(redact.getApplicableRedactions()).isEqualTo(
                        NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION
                                | NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS
                                | NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS);
                if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                    // If STA concurrency for restricted connection is supported, this should not
                    // be the primary connection.
                    if (!restrictedNetworkCapabilities.isEmpty()
                            && mWifiManager.isStaConcurrencyForRestrictedConnectionsSupported()) {
                        assertThat(wifiInfo.isPrimary()).isFalse();
                    } else {
                        assertThat(wifiInfo.isPrimary()).isTrue();
                    }
                }
            } else {
                // now wait for connection to timeout.
                assertThat(testNetworkCallback
                        .waitForAnyCallback(DURATION_NETWORK_CONNECTION_MILLIS)).isFalse();
            }
        } catch (Throwable e /* catch assertions & exceptions */) {
            try {
                mConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            } catch (IllegalArgumentException ie) { }
            throw e;
        } finally {
            executorService.shutdown();
        }
        return testNetworkCallback;
    }

    private static class TestNetworkRequestMatchCallback implements
            WifiManager.NetworkRequestMatchCallback {
        private final Object mLock;

        public boolean onRegistrationCalled = false;
        public boolean onAbortCalled = false;
        public boolean onMatchCalled = false;
        public boolean onConnectSuccessCalled = false;
        public boolean onConnectFailureCalled = false;
        public WifiManager.NetworkRequestUserSelectionCallback userSelectionCallback = null;
        public List<ScanResult> matchedScanResults = null;

        TestNetworkRequestMatchCallback(Object lock) {
            mLock = lock;
        }

        @Override
        public void onUserSelectionCallbackRegistration(
                WifiManager.NetworkRequestUserSelectionCallback userSelectionCallback) {
            Log.d(TAG, "onUserSelectionCallbackRegistration");
            synchronized (mLock) {
                onRegistrationCalled = true;
                this.userSelectionCallback = userSelectionCallback;
                mLock.notify();
            }
        }

        @Override
        public void onAbort() {
            Log.d(TAG, "onAbort");
            synchronized (mLock) {
                onAbortCalled = true;
                mLock.notify();
            }
        }

        @Override
        public void onMatch(List<ScanResult> scanResults) {
            Log.d(TAG, "onMatch");
            synchronized (mLock) {
                // This can be invoked multiple times. So, ignore after the first one to avoid
                // disturbing the rest of the test sequence.
                if (onMatchCalled) return;
                onMatchCalled = true;
                matchedScanResults = scanResults;
                mLock.notify();
            }
        }

        @Override
        public void onUserSelectionConnectSuccess(WifiConfiguration config) {
            Log.d(TAG, "onUserSelectionConnectSuccess");
            synchronized (mLock) {
                onConnectSuccessCalled = true;
                mLock.notify();
            }
        }

        @Override
        public void onUserSelectionConnectFailure(WifiConfiguration config) {
            Log.d(TAG, "onUserSelectionConnectFailure");
            synchronized (mLock) {
                onConnectFailureCalled = true;
                mLock.notify();
            }
        }
    }

    private void handleUiInteractions(WifiConfiguration network, boolean shouldUserReject) {
        // can't use CountDownLatch since there are many callbacks expected and CountDownLatch
        // cannot be reset.
        // TODO(b/177591382): Use ArrayBlockingQueue/LinkedBlockingQueue
        Object uiLock = new Object();
        TestNetworkRequestMatchCallback networkRequestMatchCallback =
                new TestNetworkRequestMatchCallback(uiLock);
        try {
            // 1. Wait for registration callback.
            synchronized (uiLock) {
                try {
                    mWifiManager.registerNetworkRequestMatchCallback(
                            Executors.newSingleThreadExecutor(), networkRequestMatchCallback);
                    uiLock.wait(DURATION_UI_INTERACTION_MILLIS);
                } catch (InterruptedException e) {
                }
            }
            assertThat(networkRequestMatchCallback.onRegistrationCalled).isTrue();
            assertThat(networkRequestMatchCallback.userSelectionCallback).isNotNull();

            // 2. Wait for matching scan results
            synchronized (uiLock) {
                if (!networkRequestMatchCallback.onMatchCalled) {
                    try {
                        uiLock.wait(DURATION_UI_INTERACTION_MILLIS);
                    } catch (InterruptedException e) {
                    }
                }
            }
            assertThat(networkRequestMatchCallback.onMatchCalled).isTrue();
            assertThat(networkRequestMatchCallback.matchedScanResults).isNotNull();
            assertThat(networkRequestMatchCallback.matchedScanResults.size()).isAtLeast(1);

            // 3. Trigger connection to one of the matched networks or reject the request.
            if (shouldUserReject) {
                networkRequestMatchCallback.userSelectionCallback.reject();
            } else {
                networkRequestMatchCallback.userSelectionCallback.select(network);
            }

            // 4. Wait for connection success or abort.
            synchronized (uiLock) {
                try {
                    uiLock.wait(DURATION_UI_INTERACTION_MILLIS);
                } catch (InterruptedException e) {
                }
            }
            if (shouldUserReject) {
                assertThat(networkRequestMatchCallback.onAbortCalled).isTrue();
            } else {
                assertThat(networkRequestMatchCallback.onConnectSuccessCalled).isTrue();
            }
        } finally {
            mWifiManager.unregisterNetworkRequestMatchCallback(networkRequestMatchCallback);
        }
    }

    /**
     * Tests the entire connection flow using the provided specifier,
     *
     * Note: The caller needs to invoke this after acquiring shell identity.
     *
     * @param specifier Specifier to use for network request.
     * @param shouldUserReject Whether to simulate user rejection or not.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithSpecifierWithShellIdentity(
            WifiConfiguration network, WifiNetworkSpecifier specifier, boolean shouldUserReject)
            throws Exception {
        // File the network request & wait for the callback.
        TestNetworkCallback testNetworkCallback = createTestNetworkCallback();
        TestLocalOnlyListener localOnlyListener = new TestLocalOnlyListener();
        mWifiManager.addLocalOnlyConnectionFailureListener(Executors.newSingleThreadExecutor(),
                localOnlyListener);
        AtomicBoolean uiVerified = new AtomicBoolean(false);
        // Fork a thread to handle the UI interactions.
        Thread uiThread = new Thread(() -> {
            try {
                handleUiInteractions(network, shouldUserReject);
                uiVerified.set(true);
            } catch (Throwable e /* catch assertions & exceptions */) {
                try {
                    mConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
                } catch (IllegalArgumentException ie) { }
                Log.e(TAG, "handleUiInteractions failed: " + e);
            }
        });

        try {
            // File a request for wifi network.
            mConnectivityManager.requestNetwork(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .setNetworkSpecifier(specifier)
                            .build(),
                    testNetworkCallback);
            // Wait for the request to reach the wifi stack before kick-starting the UI
            // interactions.
            Thread.sleep(1_000);
            // Start the UI interactions.
            uiThread.start();
            assertThat(localOnlyListener.await(DURATION_MILLIS)).isFalse();
            // now wait for callback
            assertThat(testNetworkCallback.waitForAnyCallback(DURATION_NETWORK_CONNECTION_MILLIS))
                    .isTrue();
            if (shouldUserReject) {
                assertThat(testNetworkCallback.onUnavailableCalled).isTrue();
            } else {
                assertThat(testNetworkCallback.onAvailableCalled).isTrue();
                final WifiInfo wifiInfo = getWifiInfo(testNetworkCallback.networkCapabilities);
                assertConnectionEquals(network, wifiInfo);
                if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                    // If STA concurrency for local only connection is supported, this should not
                    // be the primary connection.
                    if (mWifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported()) {
                        assertThat(wifiInfo.isPrimary()).isFalse();
                        assertConnectionEquals(network, mWifiManager.getConnectionInfo());
                    } else {
                        assertThat(wifiInfo.isPrimary()).isTrue();
                    }
                }
                assertThat(localOnlyListener.onFailureCalled).isFalse();
            }
        } catch (Throwable e /* catch assertions & exceptions */) {
            try {
                mConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            } catch (IllegalArgumentException ie) { }
            throw e;
        }
        try {
            // Ensure that the UI interaction thread has completed.
            uiThread.join(DURATION_UI_INTERACTION_MILLIS);
        } catch (InterruptedException e) {
            try {
                mConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            } catch (IllegalArgumentException ie) { }
            fail("UI interaction interrupted");
        }
        assertThat(uiVerified.get()).isTrue();
        mWifiManager.removeLocalOnlyConnectionFailureListener(localOnlyListener);
        return testNetworkCallback;
    }

    /**
     * Tests the entire connection flow using the provided specifier.
     *
     * Note: The helper method drops the shell identity, so don't use this if the caller already
     * adopted shell identity.
     *
     * @param specifier Specifier to use for network request.
     * @param shouldUserReject Whether to simulate user rejection or not.
     *
     * @return NetworkCallback used for the connection (can be used by client to release the
     * connection.
     */
    public ConnectivityManager.NetworkCallback testConnectionFlowWithSpecifier(
            WifiConfiguration network, WifiNetworkSpecifier specifier, boolean shouldUserReject)
            throws Exception {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(NETWORK_SETTINGS);
            return testConnectionFlowWithSpecifierWithShellIdentity(
                    network, specifier, shouldUserReject);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Returns the number of wifi connections visible at the networking layer.
     */
    public long getNumWifiConnections() {
        Network[] networks = mConnectivityManager.getAllNetworks();
        return Arrays.stream(networks)
                .filter(n -> mConnectivityManager.getNetworkCapabilities(n) != null
                        && mConnectivityManager.getNetworkCapabilities(n)
                                .hasTransport(TRANSPORT_WIFI))
                .count();
    }

    /**
     * Registers a network callback for internet connectivity via wifi and asserts that a network
     * is available within {@link #DURATION_NETWORK_CONNECTION_MILLIS}.
     *
     * @throws Exception
     */
    public void assertWifiInternetConnectionAvailable() throws Exception {
        TestNetworkCallback testNetworkCallback = createTestNetworkCallback();
        try {
            // File a callback for wifi network.
            NetworkRequest.Builder builder = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NET_CAPABILITY_INTERNET);
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                // Needed to ensure that the restricted concurrent connection does not
                // match this request.
                builder.addForbiddenCapability(NET_CAPABILITY_OEM_PAID)
                        .addForbiddenCapability(NET_CAPABILITY_OEM_PRIVATE);
            }
            mConnectivityManager.registerNetworkCallback(builder.build(), testNetworkCallback);
            // Wait for connection to complete & ensure we are connected to some network capable
            // of providing internet access.
            assertThat(testNetworkCallback.waitForAnyCallback(DURATION_NETWORK_CONNECTION_MILLIS))
                    .isTrue();
            assertThat(testNetworkCallback.onAvailableCalled).isTrue();
        } finally {
            mConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
        }
    }

    public static int getBandFromFrequency(final int freqMHz) {
        if (freqMHz < 1000) {
            return ScanResult.UNSPECIFIED;
        } else if (freqMHz < 4000) { // getFrequency is in WifiInfo.FREQUENCY_UNITS = MHz
            return ScanResult.WIFI_BAND_24_GHZ;
        } else if (freqMHz < 5900) {
            // 5GHz band stops at 5885MHz, 6GHz band starts at 5955. See android.net.wifi.ScanResult
            return ScanResult.WIFI_BAND_5_GHZ;
        } else if (freqMHz < 10_000) {
            return ScanResult.WIFI_BAND_6_GHZ;
        } else if (freqMHz < 71_000) {
            // 60 GHz band stops at 70_200
            return ScanResult.WIFI_BAND_60_GHZ;
        } else {
            return ScanResult.UNSPECIFIED;
        }
    }

    /**
     * Create a network request for specified band in a network specifier.
     */
    private NetworkRequest createNetworkRequestForInternet(int band) {
        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .clearCapabilities()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(TRANSPORT_WIFI)
                .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                        .setBand(band).build())
                .build();
        return networkRequest;
    }

    /**
     * Check if a wifi network info is as expected for multi internet connections.
     * @return the WifiInfo of the network.
     */
    private WifiInfo checkWifiNetworkInfo(TestNetworkCallback testNetworkCallback,
            int band) {
        if (testNetworkCallback.networkCapabilities == null) {
            return null;
        }
        WifiInfo wifiInfo = getWifiInfo(testNetworkCallback.networkCapabilities);
        assertTrue(wifiInfo.isTrusted());
        assertFalse(wifiInfo.isRestricted());
        WifiInfo redact = wifiInfo
                .makeCopy(NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION);
        assertNotNull(wifiInfo.getInformationElements());
        assertNull(redact.getInformationElements());
        assertEquals(NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION
                        | NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS
                        | NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS,
                        redact.getApplicableRedactions());
        assertEquals(band, getBandFromFrequency(wifiInfo.getFrequency()));

        return wifiInfo;
    }

    /**
     * Tests the entire connection success/failure flow using the provided suggestion.
     *
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param expectConnectionSuccess Whether to expect connection success or not.
     */
    private void testMultiInternetConnectionFlowInternal(
            @NonNull ScheduledExecutorService executorService,
            boolean expectConnectionSuccess) throws Exception {
        // File the network request & wait for the callback.
        TestNetworkCallback testNetworkCallback2G = createTestNetworkCallback();
        TestNetworkCallback testNetworkCallback5G = createTestNetworkCallback();
        final NetworkRequest networkRequest2G = createNetworkRequestForInternet(
                ScanResult.WIFI_BAND_24_GHZ);
        final NetworkRequest networkRequest5G = createNetworkRequestForInternet(
                ScanResult.WIFI_BAND_5_GHZ);
         // Make sure wifi is connected to primary after wifi enabled with saved network.
        PollingCheck.check("Wifi not connected", DURATION_NETWORK_CONNECTION_MILLIS,
                () -> getNumWifiConnections() > 0);
        try {
            // Request both 2G and 5G wifi networks.
            mConnectivityManager.requestNetwork(networkRequest2G, testNetworkCallback2G);
            mConnectivityManager.requestNetwork(networkRequest5G, testNetworkCallback5G);
            // Wait for the request to reach the wifi stack before kick-start periodic scans.
            Thread.sleep(200);
            boolean band2gFound = false;
            boolean band5gFound = false;
            // now wait for connection to complete and wait for callback
            WifiInfo primaryInfo = null;
            WifiInfo secondaryInfo = null;
            if (testNetworkCallback2G.await(DURATION_NETWORK_CONNECTION_MILLIS)) {
                WifiInfo info2g = checkWifiNetworkInfo(testNetworkCallback2G,
                        ScanResult.WIFI_BAND_24_GHZ);
                if (info2g != null) {
                    if (info2g.isPrimary()) {
                        primaryInfo = info2g;
                    } else {
                        secondaryInfo = info2g;
                    }
                    band2gFound = true;
                }
            }
            if (testNetworkCallback5G.await(DURATION_NETWORK_CONNECTION_MILLIS)) {
                WifiInfo info5g = checkWifiNetworkInfo(testNetworkCallback5G,
                        ScanResult.WIFI_BAND_5_GHZ);
                if (info5g != null) {
                    if (info5g.isPrimary()) {
                        primaryInfo = info5g;
                    } else {
                        secondaryInfo = info5g;
                    }
                    band5gFound = true;
                }
            }
            if (expectConnectionSuccess) {
                // Ensure both primary and non-primary networks are created.
                assertTrue("Network not found on 2g", band2gFound);
                assertTrue("Network not found on 5g", band5gFound);
                assertFalse("Network unavailable on 2g", testNetworkCallback2G.onUnavailableCalled);
                assertFalse("Network unavailable on 5g", testNetworkCallback5G.onUnavailableCalled);
                assertNotNull("No primary network info", primaryInfo);
                assertNotNull("No secondary network info", secondaryInfo);
                assertFalse("Primary and secondary networks are same",
                        primaryInfo.equals(secondaryInfo));
                // Ensure that there are 2 wifi connections available for apps.
                assertEquals("Expecting 2 Wifi networks", 2, getNumWifiConnections());
                // Check if the networks meets the expected requested multi internet state
                int mode = mWifiManager.getStaConcurrencyForMultiInternetMode();
                if (mode == mWifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP) {
                    // Multi AP state allows connecting to same network or multi APs in other
                    // networks, with different BSSIDs.
                    assertFalse("Can not connect to same bssid" + primaryInfo.getBSSID()
                            + " / " + secondaryInfo.getBSSID(),
                            TextUtils.equals(primaryInfo.getBSSID(), secondaryInfo.getBSSID()));
                } else if (mode == mWifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP) {
                    assertTrue("NETWORK_DBS mode can only connect to the same SSID but got "
                            + primaryInfo.getSSID() + " / " + secondaryInfo.getSSID(),
                            TextUtils.equals(primaryInfo.getSSID(), secondaryInfo.getSSID()));
                    assertEquals("NETWORK_DBS mode can only connect to the same network Id but got"
                            + primaryInfo.getNetworkId() + " / " + secondaryInfo.getNetworkId(),
                            primaryInfo.getNetworkId(), secondaryInfo.getNetworkId());
                    assertEquals("NETWORK_DBS mode can only connect to same security type but got"
                            + primaryInfo.getCurrentSecurityType() + " / "
                            + secondaryInfo.getCurrentSecurityType(),
                            primaryInfo.getCurrentSecurityType(),
                            secondaryInfo.getCurrentSecurityType());
                } else {
                    fail("Invalid multi internet mode " + mode);
                }
            } else {
                // Ensure no band specified wifi connection is created.
                assertTrue(testNetworkCallback2G.onUnavailableCalled
                        || testNetworkCallback5G.onUnavailableCalled);
                // Only one wifi network
                assertEquals("There should be only one wifi network but got "
                        + getNumWifiConnections(), 1, getNumWifiConnections());
            }
        } finally {
            mConnectivityManager.unregisterNetworkCallback(testNetworkCallback2G);
            mConnectivityManager.unregisterNetworkCallback(testNetworkCallback5G);
            executorService.shutdown();
        }
    }

    /**
     * Tests the entire connection success flow using the provided suggestion.
     *
     * Note: The caller needs to invoke this after acquiring shell identity.
     *
     * @param executorService Excutor service to run scan periodically (to trigger connection).
     * @param expectConnectionSuccess Whether to expect connection success or not.
     */
    public void testMultiInternetConnectionFlowWithShellIdentity(
            @NonNull ScheduledExecutorService executorService,
            boolean expectConnectionSuccess) throws Exception {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(NETWORK_SETTINGS, CONNECTIVITY_INTERNAL);
            testMultiInternetConnectionFlowInternal(
                    executorService, expectConnectionSuccess);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
