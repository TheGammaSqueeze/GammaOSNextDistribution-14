/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.wifi.WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;

import static com.android.server.wifi.WifiShellCommand.SHELL_PACKAGE_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.NetworkRequest;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.os.Binder;
import android.os.PatternMatcher;
import android.os.Process;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.coex.CoexManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.util.Arrays;

/**
 * Unit tests for {@link com.android.server.wifi.WifiShellCommand}.
 */
@SmallTest
public class WifiShellCommandTest extends WifiBaseTest {
    private static final String TEST_PACKAGE = "com.android.test";

    @Mock WifiInjector mWifiInjector;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock ClientModeManager mPrimaryClientModeManager;
    @Mock WifiLockManager mWifiLockManager;
    @Mock WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiNative mWifiNative;
    @Mock CoexManager mCoexManager;
    @Mock HostapdHal mHostapdHal;
    @Mock WifiCountryCode mWifiCountryCode;
    @Mock WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock WifiServiceImpl mWifiService;
    @Mock WifiContext mContext;
    @Mock ConnectivityManager mConnectivityManager;
    @Mock WifiCarrierInfoManager mWifiCarrierInfoManager;
    @Mock WifiNetworkFactory mWifiNetworkFactory;
    @Mock WifiGlobals mWifiGlobals;
    @Mock WifiThreadRunner mWifiThreadRunner;
    @Mock ScanRequestProxy mScanRequestProxy;
    @Mock WifiDiagnostics mWifiDiagnostics;
    @Mock DeviceConfigFacade mDeviceConfig;
    @Mock WifiScanner mWifiScanner;
    WifiShellCommand mWifiShellCommand;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mPrimaryClientModeManager);
        when(mActiveModeWarden.getClientModeManagers())
                .thenReturn(Arrays.asList(mPrimaryClientModeManager));
        when(mWifiInjector.getWifiLockManager()).thenReturn(mWifiLockManager);
        when(mWifiInjector.getWifiNetworkSuggestionsManager())
                .thenReturn(mWifiNetworkSuggestionsManager);
        when(mWifiInjector.getWifiConfigManager()).thenReturn(mWifiConfigManager);
        when(mWifiInjector.getHostapdHal()).thenReturn(mHostapdHal);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getCoexManager()).thenReturn(mCoexManager);
        when(mWifiInjector.getWifiCountryCode()).thenReturn(mWifiCountryCode);
        when(mWifiInjector.getWifiLastResortWatchdog()).thenReturn(mWifiLastResortWatchdog);
        when(mWifiInjector.getWifiCarrierInfoManager()).thenReturn(mWifiCarrierInfoManager);
        when(mWifiInjector.getWifiNetworkFactory()).thenReturn(mWifiNetworkFactory);
        when(mWifiInjector.getScanRequestProxy()).thenReturn(mScanRequestProxy);
        when(mContext.getSystemService(ConnectivityManager.class)).thenReturn(mConnectivityManager);
        when(mWifiInjector.getWifiDiagnostics()).thenReturn(mWifiDiagnostics);
        when(mWifiInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfig);
        when(mContext.getSystemService(WifiScanner.class)).thenReturn(mWifiScanner);

        mWifiShellCommand = new WifiShellCommand(mWifiInjector, mWifiService, mContext,
                mWifiGlobals, mWifiThreadRunner);

        // by default emulate shell uid.
        BinderUtil.setUid(Process.SHELL_UID);
    }

    @After
    public void tearDown() throws Exception {
        validateMockitoUsage();
    }

    @Test
    public void testSetIpReachDisconnect() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-ipreach-disconnect", "enabled"});
        verify(mWifiGlobals).setIpReachabilityDisconnectEnabled(true);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-ipreach-disconnect", "disabled"});
        verify(mWifiGlobals).setIpReachabilityDisconnectEnabled(false);

        // invalid arg
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-ipreach-disconnect", "yes"});
        verifyNoMoreInteractions(mWifiGlobals);
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());
    }

    @Test
    public void testGetIpReachDisconnect() {
        when(mWifiGlobals.getIpReachabilityDisconnectEnabled()).thenReturn(true);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-ipreach-disconnect"});
        verify(mWifiGlobals).getIpReachabilityDisconnectEnabled();

        when(mWifiGlobals.getIpReachabilityDisconnectEnabled()).thenReturn(false);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-ipreach-disconnect"});
        verify(mWifiGlobals, times(2)).getIpReachabilityDisconnectEnabled();
    }

    @Test
    public void testSetPollRssiIntervalMsecs() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-poll-rssi-interval-msecs", "5"});
        verify(mWifiGlobals, never()).setPollRssiIntervalMillis(anyInt());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-poll-rssi-interval-msecs", "5"});
        verify(mWifiGlobals).setPollRssiIntervalMillis(5);

        // invalid arg
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-poll-rssi-interval-msecs", "0"});
        verifyNoMoreInteractions(mWifiGlobals);
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());
    }

    @Test
    public void testGetPollRssiIntervalMsecs() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-poll-rssi-interval-msecs"});
        verify(mWifiGlobals, never()).getPollRssiIntervalMillis();
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        when(mWifiGlobals.getPollRssiIntervalMillis()).thenReturn(5);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-poll-rssi-interval-msecs"});
        verify(mWifiGlobals).getPollRssiIntervalMillis();
    }

    @Test
    public void testForceHiPerfMode() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-hi-perf-mode", "enabled"});
        verify(mWifiLockManager, never()).forceHiPerfMode(anyBoolean());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-hi-perf-mode", "enabled"});
        verify(mWifiLockManager).forceHiPerfMode(true);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-hi-perf-mode", "disabled"});
        verify(mWifiLockManager).forceHiPerfMode(false);
    }

    @Test
    public void testAddFakeScans() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(new Binder(), new FileDescriptor(), new FileDescriptor(),
                new FileDescriptor(),
                new String[]{"add-fake-scan", "ssid", "80:01:02:03:04:05", "\"[ESS]\"", "2412",
                        "-55"});
        verify(mWifiNative, never()).addFakeScanDetail(any());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);
        String ssid = "ssid";
        String bssid = "80:01:02:03:04:05";
        String capabilities = "\"[ESS]\"";
        String freq = "2412";
        String dbm = "-55";
        mWifiShellCommand.exec(new Binder(), new FileDescriptor(), new FileDescriptor(),
                new FileDescriptor(),
                new String[]{"add-fake-scan", ssid, bssid, capabilities, freq, dbm});

        ArgumentCaptor<ScanDetail> scanDetailCaptor = ArgumentCaptor.forClass(ScanDetail.class);
        verify(mWifiNative).addFakeScanDetail(scanDetailCaptor.capture());
        ScanDetail sd = scanDetailCaptor.getValue();
        assertEquals(capabilities, sd.getScanResult().capabilities);
        assertEquals(ssid, sd.getSSID());
        assertEquals(bssid, sd.getBSSIDString());
        assertEquals(2412, sd.getScanResult().frequency);
        assertEquals(-55, sd.getScanResult().level);

        // Test with "hello world" SSID encoded in hexadecimal UTF-8
        String hexSsid = "68656c6c6f20776f726c64";
        mWifiShellCommand.exec(new Binder(), new FileDescriptor(), new FileDescriptor(),
                new FileDescriptor(),
                new String[]{"add-fake-scan", "-x", hexSsid, bssid, capabilities, freq, dbm});
        verify(mWifiNative, times(2)).addFakeScanDetail(scanDetailCaptor.capture());
        sd = scanDetailCaptor.getValue();
        assertEquals("hello world", sd.getScanResult().SSID);
    }

    @Test
    public void testForceLowLatencyMode() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-low-latency-mode", "enabled"});
        verify(mWifiLockManager, never()).forceLowLatencyMode(anyBoolean());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-low-latency-mode", "enabled"});
        verify(mWifiLockManager).forceLowLatencyMode(true);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-low-latency-mode", "disabled"});
        verify(mWifiLockManager).forceLowLatencyMode(false);
    }

    @Test
    public void testNetworkSuggestionsSetUserApproved() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-suggestions-set-user-approved", TEST_PACKAGE, "yes"});
        verify(mWifiNetworkSuggestionsManager, never()).setHasUserApprovedForApp(
                anyBoolean(), anyInt(), anyString());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-suggestions-set-user-approved", TEST_PACKAGE, "yes"});
        verify(mWifiNetworkSuggestionsManager).setHasUserApprovedForApp(
                eq(true), anyInt(), eq(TEST_PACKAGE));

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-suggestions-set-user-approved", TEST_PACKAGE, "no"});
        verify(mWifiNetworkSuggestionsManager).setHasUserApprovedForApp(
                eq(false), anyInt(), eq(TEST_PACKAGE));
    }

    @Test
    public void testNetworkSuggestionsHasUserApproved() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-suggestions-has-user-approved", TEST_PACKAGE});
        verify(mWifiNetworkSuggestionsManager, never()).hasUserApprovedForApp(anyString());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        when(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE))
                .thenReturn(true);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-suggestions-has-user-approved", TEST_PACKAGE});
        verify(mWifiNetworkSuggestionsManager).hasUserApprovedForApp(TEST_PACKAGE);

        when(mWifiNetworkSuggestionsManager.hasUserApprovedForApp(TEST_PACKAGE))
                .thenReturn(false);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-suggestions-has-user-approved", TEST_PACKAGE});
        verify(mWifiNetworkSuggestionsManager, times(2)).hasUserApprovedForApp(TEST_PACKAGE);
    }

    @Test
    public void testImsiProtectionExemptionsSetUserApproved() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"imsi-protection-exemption-set-user-approved-for-carrier", "5",
                        "yes"});
        verify(mWifiCarrierInfoManager, never()).setHasUserApprovedImsiPrivacyExemptionForCarrier(
                anyBoolean(), anyInt());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"imsi-protection-exemption-set-user-approved-for-carrier", "5",
                        "yes"});
        verify(mWifiCarrierInfoManager).setHasUserApprovedImsiPrivacyExemptionForCarrier(
                true, 5);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"imsi-protection-exemption-set-user-approved-for-carrier", "5",
                        "no"});
        verify(mWifiCarrierInfoManager).setHasUserApprovedImsiPrivacyExemptionForCarrier(
                false, 5);
    }

    @Test
    public void testImsiProtectionExemptionsHasUserApproved() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"imsi-protection-exemption-has-user-approved-for-carrier", "5"});
        verify(mWifiCarrierInfoManager, never()).hasUserApprovedImsiPrivacyExemptionForCarrier(
                anyInt());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(5))
                .thenReturn(true);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"imsi-protection-exemption-has-user-approved-for-carrier", "5"});
        verify(mWifiCarrierInfoManager).hasUserApprovedImsiPrivacyExemptionForCarrier(5);

        when(mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(5))
                .thenReturn(false);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"imsi-protection-exemption-has-user-approved-for-carrier", "5"});
        verify(mWifiCarrierInfoManager, times(2)).hasUserApprovedImsiPrivacyExemptionForCarrier(5);
    }

    @Test
    public void testNetworkRequestsSetUserApproved() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-requests-set-user-approved", TEST_PACKAGE, "yes"});
        verify(mWifiNetworkFactory, never()).setUserApprovedApp(
                anyString(), anyBoolean());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-requests-set-user-approved", TEST_PACKAGE, "yes"});
        verify(mWifiNetworkFactory).setUserApprovedApp(TEST_PACKAGE, true);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-requests-set-user-approved", TEST_PACKAGE, "no"});
        verify(mWifiNetworkFactory).setUserApprovedApp(TEST_PACKAGE, false);
    }

    @Test
    public void testNetworkRequestsHasUserApproved() {
        // not allowed for unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-requests-has-user-approved", TEST_PACKAGE});
        verify(mWifiNetworkFactory, never()).hasUserApprovedApp(anyString());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        when(mWifiNetworkFactory.hasUserApprovedApp(TEST_PACKAGE))
                .thenReturn(true);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-requests-has-user-approved", TEST_PACKAGE});
        verify(mWifiNetworkFactory).hasUserApprovedApp(TEST_PACKAGE);

        when(mWifiNetworkFactory.hasUserApprovedApp(TEST_PACKAGE))
                .thenReturn(false);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"network-requests-has-user-approved", TEST_PACKAGE});
        verify(mWifiNetworkFactory, times(2)).hasUserApprovedApp(TEST_PACKAGE);
    }

    @Test
    public void testSetCoexCellChannels() {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-coex-cell-channels"});
        verify(mCoexManager, never()).setMockCellChannels(any());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        // invalid arg
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-coex-cell-channel",
                        "invalid_band", "40", "2300_000", "2000", "2300000", "2000"});
        verify(mCoexManager, never()).setMockCellChannels(any());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        // invalid arg
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-coex-cell-channels",
                        "invalid_band", "40", "-2300000", "2000", "2300000", "2000"});
        verify(mCoexManager, never()).setMockCellChannels(any());
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-coex-cell-channels",
                        "lte", "40", "2300000", "2000", "2300000", "2000"});
        verify(mCoexManager, times(1)).setMockCellChannels(any());

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-coex-cell-channels",
                        "lte", "40", "2300000", "2000", "2300000", "2000",
                        "lte", "46", "5000000", "2000", "5000000", "2000",
                        "nr", "20", "700000", "2000", "700000", "2000"});
        verify(mCoexManager, times(2)).setMockCellChannels(any());

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-coex-cell-channels"});
        verify(mCoexManager, times(3)).setMockCellChannels(any());
    }

    @Test
    public void testResetCoexCellChannel() {
        assumeTrue(SdkLevel.isAtLeastS());
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"reset-coex-cell-channels"});
        verify(mCoexManager, never()).resetMockCellChannels();
        assertFalse(mWifiShellCommand.getErrPrintWriter().toString().isEmpty());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"reset-coex-cell-channels"});
        verify(mCoexManager).resetMockCellChannels();
    }

    @Test
    public void testStartSoftAp() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"start-softap", "ap1", "wpa2", "xyzabc321", "-b", "5"});
        ArgumentCaptor<SoftApConfiguration> softApConfigurationCaptor = ArgumentCaptor.forClass(
                SoftApConfiguration.class);
        verify(mWifiService).startTetheredHotspot(
                softApConfigurationCaptor.capture(), eq(SHELL_PACKAGE_NAME));
        assertEquals(SoftApConfiguration.BAND_5GHZ,
                softApConfigurationCaptor.getValue().getBand());
        assertEquals(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK,
                softApConfigurationCaptor.getValue().getSecurityType());
        assertEquals("ap1", softApConfigurationCaptor.getValue().getWifiSsid().getUtf8Text());
        assertEquals("xyzabc321", softApConfigurationCaptor.getValue().getPassphrase());
    }

    @Test
    public void testStopSoftAp() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"stop-softap"});
        verify(mWifiService).stopSoftAp();
    }

    @Test
    public void testStartLohs() {
        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"start-lohs", "ap1", "wpa2", "xyzabc321", "-b", "5"});
        ArgumentCaptor<SoftApConfiguration> softApConfigurationCaptor = ArgumentCaptor.forClass(
                SoftApConfiguration.class);
        verify(mWifiService).startLocalOnlyHotspot(any(), eq(SHELL_PACKAGE_NAME), any(),
                softApConfigurationCaptor.capture(), any());
        assertEquals(SoftApConfiguration.BAND_5GHZ,
                softApConfigurationCaptor.getValue().getBand());
        assertEquals(SoftApConfiguration.SECURITY_TYPE_WPA2_PSK,
                softApConfigurationCaptor.getValue().getSecurityType());
        assertEquals("ap1", softApConfigurationCaptor.getValue().getSsid());
        assertEquals("xyzabc321", softApConfigurationCaptor.getValue().getPassphrase());
    }

    @Test
    public void testStopLohs() {
        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"stop-lohs"});
        verify(mWifiService).stopLocalOnlyHotspot();
    }

    @Test
    public void testSetScanAlwaysAvailable() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-scan-always-available", "enabled"});
        verify(mWifiService).setScanAlwaysAvailable(true, SHELL_PACKAGE_NAME);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-scan-always-available", "disabled"});
        verify(mWifiService).setScanAlwaysAvailable(false, SHELL_PACKAGE_NAME);
    }

    @Test
    public void testAddSuggestionWithUntrusted() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-suggestion", "ssid1234", "open", "-u"});
        verify(mWifiService).addNetworkSuggestions(argThat(sL -> {
            return (sL.size() == 1)
                    && (sL.get(0).getSsid().equals("ssid1234"))
                    && (sL.get(0).isUntrusted());
        }), eq(SHELL_PACKAGE_NAME), any());
        verify(mConnectivityManager).requestNetwork(argThat(nR -> {
            return (nR.hasTransport(TRANSPORT_WIFI))
                    && (!nR.hasCapability(NET_CAPABILITY_TRUSTED));
        }), any(ConnectivityManager.NetworkCallback.class));

        when(mWifiService.getNetworkSuggestions(any()))
                .thenReturn(Arrays.asList(
                        new WifiNetworkSuggestion.Builder()
                                .setSsid("ssid1234")
                                .setUntrusted(true)
                                .build()));
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"remove-suggestion", "ssid1234"});
        verify(mWifiService).removeNetworkSuggestions(argThat(sL -> {
            return (sL.size() == 1)
                    && (sL.get(0).getSsid().equals("ssid1234"))
                    && (sL.get(0).isUntrusted());
        }), eq(SHELL_PACKAGE_NAME), eq(ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mConnectivityManager).unregisterNetworkCallback(
                any(ConnectivityManager.NetworkCallback.class));
    }

    @Test
    public void testAddSuggestionWithOemPaid() {
        assumeTrue(SdkLevel.isAtLeastS());

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-suggestion", "ssid1234", "open", "-o"});
        verify(mWifiService).addNetworkSuggestions(argThat(sL -> {
            return (sL.size() == 1)
                    && (sL.get(0).getSsid().equals("ssid1234"))
                    && (sL.get(0).isOemPaid());
        }), eq(SHELL_PACKAGE_NAME), any());
        verify(mConnectivityManager).requestNetwork(argThat(nR -> {
            return (nR.hasTransport(TRANSPORT_WIFI))
                    && (nR.hasCapability(NET_CAPABILITY_OEM_PAID));
        }), any(ConnectivityManager.NetworkCallback.class));

        when(mWifiService.getNetworkSuggestions(any()))
                .thenReturn(Arrays.asList(
                        new WifiNetworkSuggestion.Builder()
                                .setSsid("ssid1234")
                                .setOemPaid(true)
                                .build()));
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"remove-suggestion", "ssid1234"});
        verify(mWifiService).removeNetworkSuggestions(argThat(sL -> {
            return (sL.size() == 1)
                    && (sL.get(0).getSsid().equals("ssid1234"))
                    && (sL.get(0).isOemPaid());
        }), eq(SHELL_PACKAGE_NAME), eq(ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mConnectivityManager).unregisterNetworkCallback(
                any(ConnectivityManager.NetworkCallback.class));
    }

    @Test
    public void testAddSuggestionWithOemPrivate() {
        assumeTrue(SdkLevel.isAtLeastS());

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-suggestion", "ssid1234", "open", "-p"});
        verify(mWifiService).addNetworkSuggestions(argThat(sL -> {
            return (sL.size() == 1)
                    && (sL.get(0).getSsid().equals("ssid1234"))
                    && (sL.get(0).isOemPrivate());
        }), eq(SHELL_PACKAGE_NAME), any());
        verify(mConnectivityManager).requestNetwork(argThat(nR -> {
            return (nR.hasTransport(TRANSPORT_WIFI))
                    && (nR.hasCapability(NET_CAPABILITY_OEM_PRIVATE));
        }), any(ConnectivityManager.NetworkCallback.class));

        when(mWifiService.getNetworkSuggestions(any()))
                .thenReturn(Arrays.asList(
                        new WifiNetworkSuggestion.Builder()
                                .setSsid("ssid1234")
                                .setOemPrivate(true)
                                .build()));
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"remove-suggestion", "ssid1234"});
        verify(mWifiService).removeNetworkSuggestions(argThat(sL -> {
            return (sL.size() == 1)
                    && (sL.get(0).getSsid().equals("ssid1234"))
                    && (sL.get(0).isOemPrivate());
        }), eq(SHELL_PACKAGE_NAME), eq(ACTION_REMOVE_SUGGESTION_DISCONNECT));
        verify(mConnectivityManager).unregisterNetworkCallback(
                any(ConnectivityManager.NetworkCallback.class));
    }

    @Test
    public void testAddSuggestionWithNonPersistentMacRandomization() {
        // default
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-suggestion", "ssid1234", "open"});
        verify(mWifiService).addNetworkSuggestions(argThat(sL -> {
            return (sL.size() == 1)
                    && (sL.get(0).getSsid().equals("ssid1234"))
                    && (sL.get(0).getWifiConfiguration().macRandomizationSetting
                    == WifiConfiguration.RANDOMIZATION_PERSISTENT);
        }), eq(SHELL_PACKAGE_NAME), any());

        // using non-persistent MAC randomization.
        if (SdkLevel.isAtLeastS()) {
            mWifiShellCommand.exec(
                    new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                    new String[]{"add-suggestion", "ssid1234", "open", "-r"});
            verify(mWifiService).addNetworkSuggestions(argThat(sL -> {
                return (sL.size() == 1)
                        && (sL.get(0).getSsid().equals("ssid1234"))
                        && (sL.get(0).getWifiConfiguration().macRandomizationSetting
                        == WifiConfiguration.RANDOMIZATION_NON_PERSISTENT);
            }), eq(SHELL_PACKAGE_NAME), any());
        }
    }

    @Test
    public void testStatus() {
        when(mWifiService.getWifiEnabledState()).thenReturn(WIFI_STATE_ENABLED);

        // unrooted shell.
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"status"});
        verify(mWifiService).getWifiEnabledState();
        verify(mWifiService).isScanAlwaysAvailable();
        verify(mWifiService).getConnectionInfo(SHELL_PACKAGE_NAME, null);

        verify(mPrimaryClientModeManager, never()).getConnectionInfo();
        verify(mActiveModeWarden, never()).getClientModeManagers();

        // rooted shell.
        BinderUtil.setUid(Process.ROOT_UID);

        ClientModeManager additionalClientModeManager = mock(ClientModeManager.class);
        when(mActiveModeWarden.getClientModeManagers()).thenReturn(
                Arrays.asList(mPrimaryClientModeManager, additionalClientModeManager));

        WifiInfo wifiInfo = new WifiInfo();
        wifiInfo.setSupplicantState(SupplicantState.COMPLETED);
        when(mPrimaryClientModeManager.getConnectionInfo()).thenReturn(wifiInfo);
        when(additionalClientModeManager.getConnectionInfo()).thenReturn(wifiInfo);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"status"});
        verify(mActiveModeWarden).getClientModeManagers();
        verify(mPrimaryClientModeManager).getConnectionInfo();
        verify(mPrimaryClientModeManager).getCurrentNetwork();
        verify(additionalClientModeManager).getConnectionInfo();
        verify(additionalClientModeManager).getCurrentNetwork();
    }

    @Test
    public void testEnableEmergencyCallbackMode() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-callback-mode", "enabled"});
        verify(mActiveModeWarden, never()).emergencyCallbackModeChanged(anyBoolean());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-callback-mode", "enabled"});
        verify(mActiveModeWarden).emergencyCallbackModeChanged(true);
    }

    @Test
    public void testDisableEmergencyCallbackMode() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-callback-mode", "disabled"});
        verify(mActiveModeWarden, never()).emergencyCallbackModeChanged(anyBoolean());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-callback-mode", "disabled"});
        verify(mActiveModeWarden).emergencyCallbackModeChanged(false);
    }

    @Test
    public void testEnableEmergencyCallState() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-call-state", "enabled"});
        verify(mActiveModeWarden, never()).emergencyCallStateChanged(anyBoolean());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-call-state", "enabled"});
        verify(mActiveModeWarden).emergencyCallStateChanged(true);
    }

    @Test
    public void testDisableEmergencyCallState() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-call-state", "disabled"});
        verify(mActiveModeWarden, never()).emergencyCallStateChanged(anyBoolean());

        BinderUtil.setUid(Process.ROOT_UID);

        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"set-emergency-call-state", "disabled"});
        verify(mActiveModeWarden).emergencyCallStateChanged(false);
    }

    @Test
    public void testConnectNetworkWithNoneMacRandomization() {
        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"connect-network", "ssid1234", "open", "-r", "none"});
        verify(mWifiService).connect(argThat(wifiConfiguration -> {
            return (wifiConfiguration.SSID.equals("\"ssid1234\"")
                    && wifiConfiguration.macRandomizationSetting
                    == WifiConfiguration.RANDOMIZATION_NONE);
        }), eq(-1), any(), any(), any());
    }

    @Test
    public void testConnectNetworkWithNonPersistentMacRandomizationOnSAndAbove() {
        assumeTrue(SdkLevel.isAtLeastS());

        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"connect-network", "ssid1234", "open", "-r", "non_persistent"});
        verify(mWifiService).connect(argThat(wifiConfiguration -> {
            return (wifiConfiguration.SSID.equals("\"ssid1234\"")
                    && wifiConfiguration.macRandomizationSetting
                    == WifiConfiguration.RANDOMIZATION_NON_PERSISTENT);
        }), eq(-1), any(), any(), any());
    }

    @Test
    public void testConnectNetworkWithNonPersistentMacRandomizationOnR() {
        assumeFalse(SdkLevel.isAtLeastS());

        BinderUtil.setUid(Process.ROOT_UID);
        assertEquals(-1, mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"connect-network", "ssid1234", "open", "-r", "non_persistent"}));
    }

    @Test
    public void testConnectNetworkWithHexSsid() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"connect-network", "012345", "open", "-x"});
        verify(mWifiService, never()).connect(argThat(wifiConfiguration ->
                (wifiConfiguration.SSID.equals("012345"))), eq(-1), any(), any(), any());

        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"connect-network", "012345", "open", "-x"});
        verify(mWifiService).connect(argThat(wifiConfiguration ->
                (wifiConfiguration.SSID.equals("012345"))), eq(-1), any(), any(), any());
    }

    @Test
    public void testAddNetworkWithHexSsid() {
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-network", "012345", "open", "-x"});
        verify(mWifiService, never()).save(argThat(wifiConfiguration ->
                (wifiConfiguration.SSID.equals("012345"))), any());

        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-network", "012345", "open", "-x"});
        verify(mWifiService).save(argThat(wifiConfiguration ->
                (wifiConfiguration.SSID.equals("012345"))), any(), any());
    }

    @Test
    public void testEnableScanning() {
        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"enable-scanning", "enabled"});
        verify(mScanRequestProxy).enableScanning(true, false);
    }

    @Test
    public void testEnableScanningWithHiddenNetworkOption() {
        BinderUtil.setUid(Process.ROOT_UID);
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"enable-scanning", "enabled", "-h"});
        verify(mScanRequestProxy).enableScanning(true, true);
    }

    @Test
    public void testAddNetworkRequest() {
        BinderUtil.setUid(Process.ROOT_UID);
        final String testSsid = "ssid";
        final String testBssid = "80:01:02:03:04:05";
        final String testPassphrase = "password";

        // Open
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-request", testSsid, "open"});
        verify(mConnectivityManager).requestNetwork(eq(
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .removeCapability(NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                                .setSsid(testSsid)
                                .build())
                        .build()),
                (ConnectivityManager.NetworkCallback) any());

        // OWE
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-request", testSsid, "owe", testPassphrase});
        verify(mConnectivityManager).requestNetwork(eq(
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .removeCapability(NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                                .setSsid(testSsid)
                                .setIsEnhancedOpen(true)
                                .build())
                        .build()),
                (ConnectivityManager.NetworkCallback) any());

        // WPA2
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-request", testSsid, "wpa2", testPassphrase});
        verify(mConnectivityManager).requestNetwork(eq(
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .removeCapability(NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                                .setSsid(testSsid)
                                .setWpa2Passphrase(testPassphrase)
                                .build())
                        .build()),
                (ConnectivityManager.NetworkCallback) any());

        // WPA3
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-request", testSsid, "wpa3", testPassphrase});
        verify(mConnectivityManager).requestNetwork(eq(
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .removeCapability(NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                                .setSsid(testSsid)
                                .setWpa3Passphrase(testPassphrase)
                                .build())
                        .build()),
                (ConnectivityManager.NetworkCallback) any());

        // Test bssid flag
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-request", testSsid, "open", "-b", testBssid});
        verify(mConnectivityManager).requestNetwork(eq(
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .removeCapability(NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                                .setSsid(testSsid)
                                .setBssid(MacAddress.fromString(testBssid))
                                .build())
                        .build()),
                (ConnectivityManager.NetworkCallback) any());

        // Test glob flag
        mWifiShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"add-request", "-g", testSsid, "open"});
        verify(mConnectivityManager).requestNetwork(eq(
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .removeCapability(NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
                                .setSsidPattern(new PatternMatcher(
                                        testSsid, PatternMatcher.PATTERN_ADVANCED_GLOB))
                                .build())
                        .build()),
                (ConnectivityManager.NetworkCallback) any());
    }

    @Test
    public void testTakeBugreport() {
        when(mDeviceConfig.isInterfaceFailureBugreportEnabled()).thenReturn(true);
        mWifiShellCommand.exec(new Binder(), new FileDescriptor(), new FileDescriptor(),
                new FileDescriptor(), new String[]{"take-bugreport"});
        verify(mWifiDiagnostics).takeBugReport("Wifi bugreport test", "");
    }

    @Test
    public void testGetAllowedChannel() {
        assumeTrue(SdkLevel.isAtLeastS());
        BinderUtil.setUid(Process.ROOT_UID);
        doThrow(UnsupportedOperationException.class).when(mWifiService).getUsableChannels(
                eq(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ), eq(WifiAvailableChannel.OP_MODE_STA),
                eq(WifiAvailableChannel.FILTER_REGULATORY), eq(SHELL_PACKAGE_NAME), any());
        mWifiShellCommand.exec(new Binder(), new FileDescriptor(), new FileDescriptor(),
                new FileDescriptor(), new String[]{"get-allowed-channel", "-b",
                        String.valueOf(WifiScanner.WIFI_BAND_24_GHZ)});
        verify(mWifiScanner).getAvailableChannels(eq(WifiScanner.WIFI_BAND_24_GHZ));

        when(mWifiService.getUsableChannels(eq(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ),
                eq(WifiAvailableChannel.OP_MODE_STA), eq(WifiAvailableChannel.FILTER_REGULATORY),
                eq(SHELL_PACKAGE_NAME), any())).thenReturn(null);
        doThrow(IllegalArgumentException.class).when(mWifiService).getUsableChannels(
                eq(WifiScanner.WIFI_BAND_BOTH), anyInt(),
                eq(WifiAvailableChannel.FILTER_REGULATORY), eq(SHELL_PACKAGE_NAME), any());
        mWifiShellCommand.exec(new Binder(), new FileDescriptor(), new FileDescriptor(),
                new FileDescriptor(), new String[]{"get-allowed-channel", "-b",
                        String.valueOf(WifiScanner.WIFI_BAND_BOTH)});
        verify(mWifiService, never()).getUsableChannels(eq(WifiScanner.WIFI_BAND_BOTH),
                eq(WifiAvailableChannel.OP_MODE_SAP), eq(WifiAvailableChannel.FILTER_REGULATORY),
                eq(SHELL_PACKAGE_NAME), any());

        mWifiShellCommand.exec(new Binder(), new FileDescriptor(), new FileDescriptor(),
                new FileDescriptor(), new String[]{"get-allowed-channel", "-b",
                        String.valueOf(WifiScanner.WIFI_BAND_BOTH_WITH_DFS)});
        verify(mWifiService, times(6)).getUsableChannels(eq(WifiScanner.WIFI_BAND_BOTH_WITH_DFS),
                anyInt(), eq(WifiAvailableChannel.FILTER_REGULATORY), eq(SHELL_PACKAGE_NAME),
                any());
    }
}
