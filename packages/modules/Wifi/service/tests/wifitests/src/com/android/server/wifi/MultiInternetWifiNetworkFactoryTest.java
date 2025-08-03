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

package com.android.server.wifi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.Context;
import android.net.MacAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.PatternMatcher;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.LocalLog;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.WifiPermissionsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.MultiInternetWifiNetworkFactory}.
 */
@SmallTest
public class MultiInternetWifiNetworkFactoryTest extends WifiBaseTest {
    private static final int TEST_UID = 4556;
    private static final String TEST_PACKAGE_NAME = "com.test";
    private static final String TEST_SSID = "TEST_AP1";
    private static final String TEST_BSSID = "aa:bb:cc:dd:ee:ff";
    private static final WorkSource TEST_WORKSOURCE = new WorkSource(TEST_UID, TEST_PACKAGE_NAME);

    @Mock WifiConnectivityManager mWifiConnectivityManager;
    @Mock Context mContext;
    @Mock WifiPermissionsUtil mWifiPermissionsUtil;
    @Mock AlarmManager mAlarmManager;
    @Mock MultiInternetManager mMultiInternetManager;
    @Mock FrameworkFacade mFrameworkFacade;
    private LocalLog mLocalLog;
    private NetworkCapabilities mNetworkCapabilities;
    private TestLooper mLooper;
    private NetworkRequest mNetworkRequest2G;
    private NetworkRequest mNetworkRequest5G;

    private MultiInternetWifiNetworkFactory mMultiInternetWifiNetworkFactory;

    /**
     * Setup the mocks.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastS());

        mLocalLog = new LocalLog(512);
        mLooper = new TestLooper();
        mNetworkCapabilities = new NetworkCapabilities();
        mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        mNetworkCapabilities.setRequestorUid(TEST_UID);
        mNetworkCapabilities.setRequestorPackageName(TEST_PACKAGE_NAME);
        final WifiNetworkSpecifier specifier2G = new WifiNetworkSpecifier.Builder()
                .setBand(ScanResult.WIFI_BAND_24_GHZ)
                .build();
        final WifiNetworkSpecifier specifier5G = new WifiNetworkSpecifier.Builder()
                .setBand(ScanResult.WIFI_BAND_5_GHZ)
                .build();

        mMultiInternetWifiNetworkFactory = new MultiInternetWifiNetworkFactory(
                mLooper.getLooper(), mContext, mNetworkCapabilities, mFrameworkFacade,
                mAlarmManager, mWifiPermissionsUtil, mMultiInternetManager,
                mWifiConnectivityManager, mLocalLog);

        mNetworkRequest2G = new NetworkRequest.Builder()
                .setCapabilities(mNetworkCapabilities)
                .setNetworkSpecifier(specifier2G)
                .build();
        mNetworkRequest5G = new NetworkRequest.Builder()
                .setCapabilities(mNetworkCapabilities)
                .setNetworkSpecifier(specifier5G)
                .build();
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void testIsWifiMultiInternetRequest() {
        // Test when caller is no settings
        assertFalse(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                mNetworkRequest2G, false));
        assertFalse(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                mNetworkRequest5G, false));
        mNetworkRequest2G.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mNetworkRequest5G.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        assertTrue(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                mNetworkRequest2G, false));
        assertTrue(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                mNetworkRequest5G, false));
        final NetworkRequest networkRequestNoBandSpecifier = new NetworkRequest.Builder()
                .setCapabilities(mNetworkCapabilities)
                .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                        .setBssid(MacAddress.fromString(TEST_BSSID))
                        .build())
                .build();
        assertFalse(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                networkRequestNoBandSpecifier, false));
        final NetworkRequest networkRequestSSIDSpecifier = new NetworkRequest.Builder()
                .setCapabilities(mNetworkCapabilities)
                .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                    .setSsidPattern(new PatternMatcher(TEST_SSID,
                            PatternMatcher.PATTERN_ADVANCED_GLOB))
                    .setBand(ScanResult.WIFI_BAND_5_GHZ)
                    .build())
                .build();
        assertFalse(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                networkRequestSSIDSpecifier, false));
        final NetworkRequest networkRequestBSSIDSpecifier = new NetworkRequest.Builder()
                .setCapabilities(mNetworkCapabilities)
                .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                    .setBssid(MacAddress.fromString(TEST_BSSID))
                    .setBand(ScanResult.WIFI_BAND_5_GHZ)
                    .build())
                .build();
        networkRequestBSSIDSpecifier.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        assertFalse(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                networkRequestBSSIDSpecifier, false));

        // test when the caller is settings
        assertTrue(MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(
                networkRequestBSSIDSpecifier, true));
    }

    /**
     * Validates handling of needNetworkFor.
     */
    @Test
    public void testMultiInternetHandleNetworkRequest() {
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()).thenReturn(true);
        mNetworkRequest2G.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);

        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest2G);
        // First network request should notify MultiInternetManager.
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_24_GHZ, null, TEST_WORKSOURCE);

        // Subsequent ones should do nothing.
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest2G);
        verifyNoMoreInteractions(mWifiConnectivityManager);
    }

    /**
     * Validates handling of needNetworkFor.
     */
    @Test
    public void testMultiInternetHandleNetworkRequestFromSettingsWithBssid() {
        when(mWifiPermissionsUtil.checkNetworkSettingsPermission(anyInt())).thenReturn(true);
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()).thenReturn(true);
        final NetworkRequest networkRequestBSSIDSpecifier = new NetworkRequest.Builder()
                .setCapabilities(mNetworkCapabilities)
                .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                        .setBssid(MacAddress.fromString(TEST_BSSID))
                        .setBand(ScanResult.WIFI_BAND_5_GHZ)
                        .build())
                .build();
        networkRequestBSSIDSpecifier.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);

        mMultiInternetWifiNetworkFactory.needNetworkFor(networkRequestBSSIDSpecifier);
        // First network request should notify MultiInternetManager.
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_5_GHZ, TEST_BSSID, TEST_WORKSOURCE);

        // Subsequent ones send it to MultiInternetManagerAgain since it's from Settings.
        mMultiInternetWifiNetworkFactory.needNetworkFor(networkRequestBSSIDSpecifier);
        verify(mMultiInternetManager, times(2)).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_5_GHZ, TEST_BSSID, TEST_WORKSOURCE);
    }

    /**
     * Validates handling of needNetworkFor of dual bands.
     */
    @Test
    public void testMultiInternetHandleDualBandNetworkRequest() {
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()).thenReturn(true);
        mNetworkRequest2G.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mNetworkRequest5G.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);

        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest2G);
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest5G);
        // First network request should notify MultiInternetManager.
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_24_GHZ, null, TEST_WORKSOURCE);
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_5_GHZ, null, TEST_WORKSOURCE);

        reset(mMultiInternetManager);
        // Subsequent ones should do nothing.
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest2G);
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest5G);
        verify(mMultiInternetManager, never()).setMultiInternetConnectionWorksource(anyInt(),
                anyObject(), anyObject());
        verifyNoMoreInteractions(mWifiConnectivityManager);
    }

    /**
     * Validates handling of releaseNetwork.
     */
    @Test
    public void testHandleMultiInternetNetworkRelease() {
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()).thenReturn(true);
        mNetworkRequest2G.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        // Release network without a corresponding request should be ignored.
        mMultiInternetWifiNetworkFactory.releaseNetworkFor(mNetworkRequest2G);

        // Now request & then release the network request
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest2G);
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_24_GHZ, null, TEST_WORKSOURCE);

        mMultiInternetWifiNetworkFactory.releaseNetworkFor(mNetworkRequest2G);
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_24_GHZ, null, null);
    }

    /**
     * Validates handling of releaseNetwork after multiple network requests.
     */
    @Test
    public void testHandleMultiInternetNetworkReleaseWithMultiRequests() {
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()).thenReturn(true);
        mNetworkRequest5G.networkCapabilities.addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        // Release network without a corresponding request should be ignored.
        mMultiInternetWifiNetworkFactory.releaseNetworkFor(mNetworkRequest5G);

        // Now request & then release the network request
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest5G);
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_5_GHZ, null, TEST_WORKSOURCE);

        // Now request the network again for 2 times.
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest5G);
        mMultiInternetWifiNetworkFactory.needNetworkFor(mNetworkRequest5G);

        mMultiInternetWifiNetworkFactory.releaseNetworkFor(mNetworkRequest5G);
        verify(mMultiInternetManager, never()).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_5_GHZ, null, null);
        mMultiInternetWifiNetworkFactory.releaseNetworkFor(mNetworkRequest5G);
        mMultiInternetWifiNetworkFactory.releaseNetworkFor(mNetworkRequest5G);
        verify(mMultiInternetManager).setMultiInternetConnectionWorksource(
                ScanResult.WIFI_BAND_5_GHZ, null, null);
    }
}
