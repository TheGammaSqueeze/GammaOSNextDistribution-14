/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.StatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Handler;
import android.os.test.TestLooper;
import android.util.StatsEvent;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.WifiPermissionsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

@SmallTest
public class WifiPulledAtomLoggerTest extends WifiBaseTest {
    public static final int TEST_INVALID_ATOM_ID = -1;

    public static final int WIFI_VERSION_NUMBER = 340899999;
    private WifiPulledAtomLogger mWifiPulledAtomLogger;
    private MockitoSession mSession;
    private TestLooper mLooper;
    @Mock private StatsManager mStatsManager;
    @Mock private Context mContext;
    @Mock private WifiInjector mWifiInjector;
    @Mock private PackageManager mPackageManager;
    @Mock private WifiSettingsStore mWifiSettingsStore;
    @Mock private WifiConfigManager mWifiConfigManager;
    @Mock private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock private PasspointManager mPasspointManager;
    @Mock private SsidTranslator mSsidTranslator;
    @Mock private WifiConfiguration mWifiConfiguration;
    @Captor ArgumentCaptor<StatsManager.StatsPullAtomCallback> mPullAtomCallbackArgumentCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mWifiInjector.getWifiSettingsStore()).thenReturn(mWifiSettingsStore);
        mWifiPulledAtomLogger = new WifiPulledAtomLogger(mStatsManager,
                new Handler(mLooper.getLooper()), mContext, mWifiInjector);

        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(WifiStatsLog.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void testNullStatsManagerNoCrash() {
        mWifiPulledAtomLogger = new WifiPulledAtomLogger(null,
                new Handler(mLooper.getLooper()), mContext, mWifiInjector);
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_MODULE_INFO);
    }

    @Test
    public void testSetCallbackWithInvalidPull() {
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_MODULE_INFO);
        verify(mStatsManager).setPullAtomCallback(eq(WifiStatsLog.WIFI_MODULE_INFO), any(), any(),
                mPullAtomCallbackArgumentCaptor.capture());

        assertNotNull(mPullAtomCallbackArgumentCaptor.getValue());
        // Invalid ATOM ID should get skipped
        assertEquals(StatsManager.PULL_SKIP, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(TEST_INVALID_ATOM_ID, new ArrayList<>()));
    }

    @Test
    public void testWifiMainlineVersionPull_builtFromSource() {
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_MODULE_INFO);
        verify(mStatsManager).setPullAtomCallback(eq(WifiStatsLog.WIFI_MODULE_INFO), any(), any(),
                mPullAtomCallbackArgumentCaptor.capture());

        assertNotNull(mPullAtomCallbackArgumentCaptor.getValue());

        List<StatsEvent> data = new ArrayList<>();
        // Test with null PackageManager - the atom pull should be skipped
        when(mContext.getPackageManager()).thenReturn(null);
        assertEquals(StatsManager.PULL_SKIP, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WifiStatsLog.WIFI_MODULE_INFO, data));
        assertEquals(0, data.size());

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        List<PackageInfo> packageInfos = new ArrayList<>();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = WifiPulledAtomLogger.WIFI_BUILD_FROM_SOURCE_PACKAGE_NAME;
        packageInfo.setLongVersionCode(WIFI_VERSION_NUMBER);
        packageInfos.add(packageInfo);
        when(mPackageManager.getInstalledPackages(anyInt())).thenReturn(packageInfos);
        assertEquals(StatsManager.PULL_SUCCESS, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WifiStatsLog.WIFI_MODULE_INFO, data));
        assertEquals(1, data.size());
        ExtendedMockito.verify(() -> WifiStatsLog.buildStatsEvent(
                WifiStatsLog.WIFI_MODULE_INFO,
                WIFI_VERSION_NUMBER,
                WifiStatsLog.WIFI_MODULE_INFO__BUILD_TYPE__TYPE_BUILT_FROM_SOURCE));
    }

    @Test
    public void testWifiMainlineVersionPull_preBuilt() {
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_MODULE_INFO);
        verify(mStatsManager).setPullAtomCallback(eq(WifiStatsLog.WIFI_MODULE_INFO), any(), any(),
                mPullAtomCallbackArgumentCaptor.capture());

        assertNotNull(mPullAtomCallbackArgumentCaptor.getValue());

        List<StatsEvent> data = new ArrayList<>();
        List<PackageInfo> packageInfos = new ArrayList<>();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "com.google.android.wifi";
        packageInfo.setLongVersionCode(WIFI_VERSION_NUMBER);
        packageInfos.add(packageInfo);
        when(mPackageManager.getInstalledPackages(anyInt())).thenReturn(packageInfos);
        assertEquals(StatsManager.PULL_SUCCESS, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WifiStatsLog.WIFI_MODULE_INFO, data));
        assertEquals(1, data.size());
        ExtendedMockito.verify(() -> WifiStatsLog.buildStatsEvent(
                WifiStatsLog.WIFI_MODULE_INFO,
                WIFI_VERSION_NUMBER,
                WifiStatsLog.WIFI_MODULE_INFO__BUILD_TYPE__TYPE_PREBUILT));
    }

    @Test
    public void testWifiSettingsPull() {
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_SETTING_INFO);
        verify(mStatsManager).setPullAtomCallback(eq(WifiStatsLog.WIFI_SETTING_INFO), any(), any(),
                mPullAtomCallbackArgumentCaptor.capture());
        assertNotNull(mPullAtomCallbackArgumentCaptor.getValue());

        when(mWifiInjector.getFrameworkFacade()).thenReturn(mock(FrameworkFacade.class));
        when(mWifiInjector.getWakeupController()).thenReturn(mock(WakeupController.class));
        when(mWifiInjector.getOpenNetworkNotifier()).thenReturn(mock(OpenNetworkNotifier.class));
        when(mWifiInjector.getWifiPermissionsUtil()).thenReturn(mock(WifiPermissionsUtil.class));

        // Verify that all 8 settings were retrieved.
        List<StatsEvent> data = new ArrayList<>();
        assertEquals(StatsManager.PULL_SUCCESS, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WifiStatsLog.WIFI_SETTING_INFO, data));
        assertEquals(8, data.size());
    }

    @Test
    public void testWifiComplexSettingsPull_valid() {
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_COMPLEX_SETTING_INFO);
        verify(mStatsManager).setPullAtomCallback(eq(WifiStatsLog.WIFI_COMPLEX_SETTING_INFO),
                any(), any(), mPullAtomCallbackArgumentCaptor.capture());
        assertNotNull(mPullAtomCallbackArgumentCaptor.getValue());

        // Framework and atom should indicate DBS AP mode.
        int frameworkMode = WifiManager.WIFI_MULTI_INTERNET_MODE_DBS_AP;
        int atomMode = WifiStatsLog
                .WIFI_COMPLEX_SETTING_INFO__MULTI_INTERNET_MODE__MULTI_INTERNET_MODE_DBS_AP;
        when(mWifiSettingsStore.getWifiMultiInternetMode()).thenReturn(frameworkMode);

        List<StatsEvent> data = new ArrayList<>();
        assertEquals(StatsManager.PULL_SUCCESS, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WifiStatsLog.WIFI_COMPLEX_SETTING_INFO, data));
        assertEquals(1, data.size());
        ExtendedMockito.verify(() -> WifiStatsLog.buildStatsEvent(
                WifiStatsLog.WIFI_COMPLEX_SETTING_INFO, atomMode));
    }

    @Test
    public void testWifiComplexSettingsPull_invalid() {
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_COMPLEX_SETTING_INFO);
        verify(mStatsManager).setPullAtomCallback(eq(WifiStatsLog.WIFI_COMPLEX_SETTING_INFO),
                any(), any(), mPullAtomCallbackArgumentCaptor.capture());
        assertNotNull(mPullAtomCallbackArgumentCaptor.getValue());

        // Invalid WifiMultiInternetMode value should cause the pull to fail.
        when(mWifiSettingsStore.getWifiMultiInternetMode()).thenReturn(9999);
        assertEquals(StatsManager.PULL_SKIP, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WifiStatsLog.WIFI_COMPLEX_SETTING_INFO, new ArrayList<>()));
    }

    @Test
    public void testWifiConfiguredNetworkInfoPull() {
        mWifiPulledAtomLogger.setPullAtomCallback(WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO);
        verify(mStatsManager).setPullAtomCallback(eq(WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO),
                any(), any(), mPullAtomCallbackArgumentCaptor.capture());
        assertNotNull(mPullAtomCallbackArgumentCaptor.getValue());

        when(mWifiConfiguration.getNetworkKey()).thenReturn("someKey");
        when(mWifiConfiguration.getNetworkSelectionStatus()).thenReturn(
                mock(WifiConfiguration.NetworkSelectionStatus.class));
        when(mWifiConfiguration.isPasspoint()).thenReturn(false);

        WifiNetworkSuggestion mockSuggestion = mock(WifiNetworkSuggestion.class);
        when(mockSuggestion.getWifiConfiguration()).thenReturn(mWifiConfiguration);

        when(mWifiInjector.getSsidTranslator()).thenReturn(mSsidTranslator);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getWifiNetworkSuggestionsManager())
                .thenReturn(mWifiNetworkSuggestionsManager);
        when(mWifiInjector.getPasspointManager()).thenReturn(mPasspointManager);

        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(Arrays.asList(mWifiConfiguration));
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(new HashSet<>(Arrays.asList(mockSuggestion)));
        when(mWifiNetworkSuggestionsManager
                .getAllPasspointScanOptimizationSuggestionNetworks(anyBoolean()))
                .thenReturn(Arrays.asList(mWifiConfiguration));
        when(mPasspointManager.getWifiConfigsForPasspointProfiles(anyBoolean()))
                .thenReturn(Arrays.asList(mWifiConfiguration));

        List<StatsEvent> data = new ArrayList<>();
        assertEquals(StatsManager.PULL_SUCCESS, mPullAtomCallbackArgumentCaptor.getValue()
                .onPullAtom(WifiStatsLog.WIFI_CONFIGURED_NETWORK_INFO, data));
        assertEquals(4, data.size());
    }
}
