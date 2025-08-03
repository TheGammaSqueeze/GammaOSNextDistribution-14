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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.hardware.wifi.hostapd.ApInfo;
import android.hardware.wifi.hostapd.BandMask;
import android.hardware.wifi.hostapd.ChannelBandwidth;
import android.hardware.wifi.hostapd.ClientInfo;
import android.hardware.wifi.hostapd.DebugLevel;
import android.hardware.wifi.hostapd.EncryptionType;
import android.hardware.wifi.hostapd.FrequencyRange;
import android.hardware.wifi.hostapd.Generation;
import android.hardware.wifi.hostapd.HostapdStatusCode;
import android.hardware.wifi.hostapd.IHostapd;
import android.hardware.wifi.hostapd.IHostapdCallback;
import android.hardware.wifi.hostapd.Ieee80211ReasonCode;
import android.hardware.wifi.hostapd.IfaceParams;
import android.hardware.wifi.hostapd.NetworkParams;
import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApConfiguration.Builder;
import android.net.wifi.WifiManager;
import android.net.wifi.util.PersistableBundleUtils;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.test.TestLooper;
import android.util.SparseIntArray;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.ApConfigUtil;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit tests for HostapdHal
 */
@SmallTest
public class HostapdHalAidlImpTest extends WifiBaseTest {
    private static final String IFACE_NAME = "mock-wlan0";
    private static final String IFACE_NAME_1 = "mock-wlan1";
    private static final String NETWORK_SSID = "test-ssid";
    private static final String NETWORK_PSK = "test-psk";
    private static final String TEST_CLIENT_MAC = "11:22:33:44:55:66";
    private static final String TEST_AP_INSTANCE = "instance-wlan0";
    private static final String TEST_AP_INSTANCE_2 = "instance-wlan1";
    private static final int TEST_FREQ_24G = 2412;
    private static final int TEST_FREQ_5G = 5745;
    private static final int TEST_BANDWIDTH = ChannelBandwidth.BANDWIDTH_20;
    private static final int TEST_GENERATION = Generation.WIFI_STANDARD_11N;

    private final int mBand256G = SoftApConfiguration.BAND_2GHZ | SoftApConfiguration.BAND_5GHZ
            | SoftApConfiguration.BAND_6GHZ;

    private @Mock Context mContext;
    private @Mock IHostapd mIHostapdMock;
    private @Mock IBinder mServiceBinderMock;
    private @Mock WifiNative.HostapdDeathEventHandler mHostapdHalDeathHandler;
    private @Mock WifiNative.SoftApHalCallback mSoftApHalCallback;
    private @Mock WifiNative.SoftApHalCallback mSoftApHalCallback1;

    private IHostapdCallback mIHostapdCallback;
    private MockResources mResources;

    private TestLooper mLooper = new TestLooper();
    private HostapdHalAidlImp mHostapdHal;
    private ArgumentCaptor<DeathRecipient> mHostapdDeathCaptor =
            ArgumentCaptor.forClass(DeathRecipient.class);
    private ArgumentCaptor<IfaceParams> mIfaceParamsCaptor =
            ArgumentCaptor.forClass(IfaceParams.class);
    private ArgumentCaptor<NetworkParams> mNetworkParamsCaptor =
            ArgumentCaptor.forClass(NetworkParams.class);

    private class HostapdHalSpy extends HostapdHalAidlImp {
        private IBinder mServiceBinderOverride;
        HostapdHalSpy() {
            super(mContext, new Handler(mLooper.getLooper()));
        }

        public void setServiceBinderOverride(IBinder serviceBinderOverride) {
            mServiceBinderOverride = serviceBinderOverride;
        }

        @Override
        protected IHostapd getHostapdMockable() {
            return mIHostapdMock;
        }

        @Override
        protected IBinder getServiceBinderMockable() {
            return mServiceBinderOverride == null ? mServiceBinderMock : mServiceBinderOverride;
        }

        @Override
        public boolean initialize() {
            return true;
        }
    }

    private void mockApInfoChangedAndVerify(String ifaceName, int numOfApInfo,
            IHostapdCallback mockHostapdCallback,
            WifiNative.SoftApHalCallback mockSoftApHalCallback) throws Exception {
        // Trigger on info changed.
        ApInfo apInfo = new ApInfo();
        apInfo.ifaceName = ifaceName;
        apInfo.apIfaceInstance = TEST_AP_INSTANCE;
        apInfo.freqMhz = TEST_FREQ_24G;
        apInfo.channelBandwidth = TEST_BANDWIDTH;
        apInfo.generation = TEST_GENERATION;
        apInfo.apIfaceInstanceMacAddress = MacAddress.fromString(TEST_CLIENT_MAC).toByteArray();
        if (numOfApInfo == 1) {
            mockHostapdCallback.onApInstanceInfoChanged(apInfo);
            verify(mockSoftApHalCallback).onInfoChanged(eq(TEST_AP_INSTANCE), eq(TEST_FREQ_24G),
                    eq(mHostapdHal.mapHalChannelBandwidthToSoftApInfo(TEST_BANDWIDTH)),
                    eq(mHostapdHal.mapHalGenerationToWifiStandard(TEST_GENERATION)),
                    eq(MacAddress.fromString(TEST_CLIENT_MAC)), anyList());
        } else if (numOfApInfo == 2) {
            apInfo.apIfaceInstance = TEST_AP_INSTANCE_2;
            apInfo.freqMhz = TEST_FREQ_5G;
            mockHostapdCallback.onApInstanceInfoChanged(apInfo);
            verify(mockSoftApHalCallback).onInfoChanged(eq(TEST_AP_INSTANCE_2), eq(TEST_FREQ_5G),
                    eq(mHostapdHal.mapHalChannelBandwidthToSoftApInfo(TEST_BANDWIDTH)),
                    eq(mHostapdHal.mapHalGenerationToWifiStandard(TEST_GENERATION)),
                    eq(MacAddress.fromString(TEST_CLIENT_MAC)), anyList());
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, false);
        mResources.setBoolean(R.bool.config_wifi_softap_ieee80211ac_supported, false);
        mResources.setBoolean(R.bool.config_wifiSoftapIeee80211axSupported, false);
        mResources.setBoolean(R.bool.config_wifiSoftap6ghzSupported, false);
        mResources.setBoolean(R.bool.config_wifiSoftapAcsIncludeDfs, false);
        mResources.setString(R.string.config_wifiSoftap2gChannelList, "");
        mResources.setString(R.string.config_wifiSoftap5gChannelList, "");
        mResources.setString(R.string.config_wifiSoftap6gChannelList, "");

        when(mContext.getResources()).thenReturn(mResources);
        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());
        doNothing().when(mIHostapdMock).removeAccessPoint(any());
        mHostapdHal = new HostapdHalSpy();
    }

    /**
     * Sunny day scenario for HostapdHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testInitialize_success() throws Exception {
        executeAndValidateInitializationSequence(true);
    }

    /**
     * Failure scenario for HostapdHal initialization
     */
    @Test
    public void testInitialize_registerException() throws Exception {
        executeAndValidateInitializationSequence(false);
    }

    /**
     * Verifies the hostapd death handling.
     */
    @Test
    public void testDeathHandling() throws Exception {
        executeAndValidateInitializationSequence(true);
        mHostapdHal.registerDeathHandler(mHostapdHalDeathHandler);
        mHostapdDeathCaptor.getValue().binderDied();
        mLooper.dispatchAll();
        verify(mHostapdHalDeathHandler).onDeath();
    }

    /**
     * Verifies the hostapd death handling ignored with stale death recipient.
     */
    @Test
    public void testDeathHandlingIgnore() throws Exception {
        executeAndValidateInitializationSequence(true);
        mHostapdHal.registerDeathHandler(mHostapdHalDeathHandler);
        DeathRecipient old = mHostapdDeathCaptor.getValue();
        // Start the HAL again
        reset(mServiceBinderMock);
        reset(mIHostapdMock);
        // Initialize and start hostapd daemon with different service binder
        ((HostapdHalSpy) mHostapdHal).setServiceBinderOverride(new Binder());
        assertTrue(mHostapdHal.initialize());
        assertTrue(mHostapdHal.startDaemon());
        // The old binder died should be ignored.
        old.binderDied();
        mLooper.dispatchAll();
        verify(mHostapdHalDeathHandler, never()).onDeath();
    }

    /**
     * Verifies the successful addition of access point.
     */
    @Test
    public void testAddAccessPointSuccess_Psk_Band2G() throws Exception {
        // TODO(b/195971074) : Parameterize the unit tests for addAccessPoint
        executeAndValidateInitializationSequence(true);
        final int apChannel = 6;

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configurationBuilder.setChannel(apChannel, SoftApConfiguration.BAND_2GHZ);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertEquals(BandMask.BAND_2_GHZ,
                mIfaceParamsCaptor.getValue().channelParams[0].bandMask);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].acsShouldExcludeDfs);
        assertEquals(apChannel, mIfaceParamsCaptor.getValue().channelParams[0].channel);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA2, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /**
     * Verifies the successful addition of access point.
     */
    @Test
    public void testAddAccessPointSuccess_Open_Band5G() throws Exception {
        executeAndValidateInitializationSequence(true);
        final int apChannel = 149;

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(true);
        configurationBuilder.setChannel(apChannel, SoftApConfiguration.BAND_5GHZ);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertEquals(BandMask.BAND_5_GHZ,
                mIfaceParamsCaptor.getValue().channelParams[0].bandMask);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].acsShouldExcludeDfs);
        assertEquals(apChannel, mIfaceParamsCaptor.getValue().channelParams[0].channel);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertTrue(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.NONE, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /**
     * Verifies the successful addition of access point.
     */
    @Test
    public void testAddAccessPointSuccess_Psk_Band5G_Hidden() throws Exception {
        executeAndValidateInitializationSequence(true);
        final int apChannel = 149;

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(true);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configurationBuilder.setChannel(apChannel, SoftApConfiguration.BAND_5GHZ);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertEquals(BandMask.BAND_5_GHZ,
                mIfaceParamsCaptor.getValue().channelParams[0].bandMask);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].acsShouldExcludeDfs);
        assertEquals(apChannel, mIfaceParamsCaptor.getValue().channelParams[0].channel);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertTrue(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA2, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /**
     * Verifies the successful addition of access point.
     */
    @Test
    public void testAddAccessPointSuccess_Psk_Band2G_WithACS() throws Exception {
        // Enable ACS in the config.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);
        final int apChannel = 6;

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configurationBuilder.setChannel(apChannel, SoftApConfiguration.BAND_2GHZ);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertEquals(BandMask.BAND_2_GHZ,
                mIfaceParamsCaptor.getValue().channelParams[0].bandMask);
        assertEquals(apChannel, mIfaceParamsCaptor.getValue().channelParams[0].channel);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].acsShouldExcludeDfs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA2, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /**
     * Verifies the successful addition of access point.
     */
    @Test
    public void testAddAccessPointSuccess_Psk_Band2G_WithIeee80211AC() throws Exception {
        // Enable ACS & 80211AC in the config.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mResources.setBoolean(R.bool.config_wifi_softap_ieee80211ac_supported, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);
        final int apChannel = 6;

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configurationBuilder.setChannel(apChannel, SoftApConfiguration.BAND_2GHZ);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertEquals(BandMask.BAND_2_GHZ,
                mIfaceParamsCaptor.getValue().channelParams[0].bandMask);
        assertEquals(apChannel, mIfaceParamsCaptor.getValue().channelParams[0].channel);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].acsShouldExcludeDfs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA2, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /**
     * Verifies the successful addition of access point.
     */
    @Test
    public void testAddAccessPointSuccess_Psk_BandAny_WithACS() throws Exception {
        // Enable ACS in the config.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configurationBuilder.setBand(mBand256G);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertEquals(mBand256G,
                mIfaceParamsCaptor.getValue().channelParams[0].bandMask);
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].acsShouldExcludeDfs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA2, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /**
     * Verifies the successful addition of access point.
     */
    @Test
    public void testAddAccessPointSuccess_Psk_WithoutACS() throws Exception {
        // Disable ACS in the config.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, false);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configurationBuilder.setBand(mBand256G);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA2, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /**
     * Verifies the failure handling in addition of access point.
     */
    @Test
    public void testAddAccessPointFailure() throws Exception {
        executeAndValidateInitializationSequence(true);
        doThrow(new RemoteException()).when(mIHostapdMock).addAccessPoint(any(), any());

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(true);
        configurationBuilder.setChannel(6, SoftApConfiguration.BAND_2GHZ);

        assertFalse(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());
    }

    /**
     * Verifies the failure handling in addition of access point.
     */
    @Test
    public void testAddAccessPointRemoteException() throws Exception {
        executeAndValidateInitializationSequence(true);
        doThrow(new RemoteException()).when(mIHostapdMock).addAccessPoint(any(), any());

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(true);
        configurationBuilder.setChannel(6, SoftApConfiguration.BAND_2GHZ);

        assertFalse(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());
    }

    /**
     * Verifies the successful removal of access point.
     */
    @Test
    public void testRemoveAccessPointSuccess() throws Exception {
        executeAndValidateInitializationSequence(true);

        assertTrue(mHostapdHal.removeAccessPoint(IFACE_NAME));
        verify(mIHostapdMock).removeAccessPoint(any());
    }

    /**
     * Verifies service specific exception handling in removal of access point.
     */
    @Test
    public void testRemoveAccessPointServiceException() throws Exception {
        executeAndValidateInitializationSequence(true);
        doThrow(new ServiceSpecificException(HostapdStatusCode.FAILURE_UNKNOWN))
                .when(mIHostapdMock).removeAccessPoint(any());

        assertFalse(mHostapdHal.removeAccessPoint(IFACE_NAME));
        verify(mIHostapdMock).removeAccessPoint(any());
    }

    /**
     * Verifies remote exception handling in removal of access point.
     */
    @Test
    public void testRemoveAccessPointRemoteException() throws Exception {
        executeAndValidateInitializationSequence(true);
        doThrow(new RemoteException()).when(mIHostapdMock).removeAccessPoint(any());

        assertFalse(mHostapdHal.removeAccessPoint(IFACE_NAME));
        verify(mIHostapdMock).removeAccessPoint(any());
    }

    /**
     * Verifies the handling of onFailure callback from hostapd.
     */
    @Test
    public void testOnFailureCallbackHandling() throws Exception {
        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setBand(SoftApConfiguration.BAND_2GHZ);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        // Trigger on failure.
        mIHostapdCallback.onFailure(IFACE_NAME, IFACE_NAME);
        verify(mSoftApHalCallback).onFailure();

        // Now remove the access point and ensure that the callback is no longer handled.
        reset(mSoftApHalCallback);
        assertTrue(mHostapdHal.removeAccessPoint(IFACE_NAME));
        mIHostapdCallback.onFailure(IFACE_NAME, IFACE_NAME);
        verify(mSoftApHalCallback, never()).onFailure();
    }

    /**
     * Calls initialize(), mocking various callback answers and verifying flow
     */
    private void executeAndValidateInitializationSequence(
            boolean shouldSucceed) throws Exception {
        doNothing().when(mIHostapdMock).setDebugParams(anyInt());
        if (!shouldSucceed) {
            doAnswer(new MockAnswerUtil.AnswerWithArguments() {
                public void answer(IHostapdCallback cb) throws RemoteException {
                    throw new RemoteException();
                }
            }).when(mIHostapdMock).registerCallback(any(IHostapdCallback.class));
        } else {
            doAnswer(new MockAnswerUtil.AnswerWithArguments() {
                public void answer(IHostapdCallback cb) throws RemoteException {
                    mIHostapdCallback = cb;
                }
            }).when(mIHostapdMock).registerCallback(any(IHostapdCallback.class));
        }

        // Initialize and start hostapd daemon
        assertTrue(mHostapdHal.initialize());
        assertTrue(mHostapdHal.startDaemon() == shouldSucceed);

        // Verify initialization sequence
        verify(mIHostapdMock).getInterfaceVersion();
        verify(mServiceBinderMock).linkToDeath(mHostapdDeathCaptor.capture(), anyInt());
        verify(mIHostapdMock).registerCallback(any(IHostapdCallback.class));
        assertEquals(shouldSucceed, mHostapdHal.isInitializationComplete());
    }

    /**
     * Verifies the service initialization success but setDebugParams failed.
     */
    @Test
    public void testServiceInitializationSetDebugParamFailed() throws Exception {
        // Throw an exception when calling setDebugParams
        doThrow(new RemoteException()).when(mIHostapdMock).setDebugParams(anyInt());

        // Initialize and start hostapd daemon
        assertTrue(mHostapdHal.initialize());
        assertFalse(mHostapdHal.startDaemon());
        assertFalse(mHostapdHal.isInitializationComplete());

        // Verify initialization sequence
        verify(mIHostapdMock).getInterfaceVersion();
        verify(mServiceBinderMock).linkToDeath(mHostapdDeathCaptor.capture(), anyInt());
        // Should never call register callback.
        verify(mIHostapdMock, never()).registerCallback(any(IHostapdCallback.class));
    }

    /**
     * Verifies the successful execute forceClientDisconnect.
     */
    @Test
    public void testForceClientDisconnectSuccess() throws Exception {
        executeAndValidateInitializationSequence(true);
        MacAddress test_client = MacAddress.fromString("da:a1:19:0:0:0");
        ArgumentCaptor<byte[]> macAddrCaptor = ArgumentCaptor.forClass(byte[].class);
        doNothing().when(mIHostapdMock).forceClientDisconnect(
                anyString(), macAddrCaptor.capture(), anyInt());

        assertTrue(mHostapdHal.forceClientDisconnect(IFACE_NAME, test_client,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER));
        verify(mIHostapdMock).forceClientDisconnect(eq(IFACE_NAME), any(byte[].class),
                eq(Ieee80211ReasonCode.WLAN_REASON_PREV_AUTH_NOT_VALID));
        assertTrue(test_client.equals(MacAddress.fromBytes(macAddrCaptor.getValue())));
    }

    /**
     * Verifies the failure handling in forceClientDisconnect.
     */
    @Test
    public void testForceClientDisconnectFailureDueToInvalidArg() throws Exception {
        executeAndValidateInitializationSequence(true);
        MacAddress test_client = MacAddress.fromString("da:a1:19:0:0:0");
        ArgumentCaptor<byte[]> macAddrCaptor = ArgumentCaptor.forClass(byte[].class);
        doNothing().when(mIHostapdMock).forceClientDisconnect(
                anyString(), macAddrCaptor.capture(), anyInt());

        try {
            mHostapdHal.forceClientDisconnect(IFACE_NAME, test_client, -1);
            fail();
        } catch (IllegalArgumentException e) {
            // Expect this exception to be thrown
        }
    }

    /**
     * Verifies the service specific exception handling in forceClientDisconnect.
     */
    @Test
    public void testforceClientDisconnectServiceException() throws Exception {
        executeAndValidateInitializationSequence(true);
        MacAddress test_client = MacAddress.fromString("da:a1:19:0:0:0");
        ArgumentCaptor<byte[]> macAddrCaptor = ArgumentCaptor.forClass(byte[].class);
        doThrow(new ServiceSpecificException(HostapdStatusCode.FAILURE_CLIENT_UNKNOWN))
                .when(mIHostapdMock).forceClientDisconnect(
                anyString(), macAddrCaptor.capture(), anyInt());

        assertFalse(mHostapdHal.forceClientDisconnect(IFACE_NAME, test_client,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER));
        verify(mIHostapdMock).forceClientDisconnect(eq(IFACE_NAME), any(byte[].class),
                eq(Ieee80211ReasonCode.WLAN_REASON_PREV_AUTH_NOT_VALID));
        assertTrue(test_client.equals(MacAddress.fromBytes(macAddrCaptor.getValue())));
    }

    /**
     * Verifies remote exception handling in forceClientDisconnect.
     */
    @Test
    public void testforceClientDisconnectRemoteException() throws Exception {
        executeAndValidateInitializationSequence(true);
        MacAddress test_client = MacAddress.fromString("da:a1:19:0:0:0");
        ArgumentCaptor<byte[]> macAddrCaptor = ArgumentCaptor.forClass(byte[].class);
        doThrow(new RemoteException()).when(mIHostapdMock).forceClientDisconnect(
                anyString(), macAddrCaptor.capture(), anyInt());

        assertFalse(mHostapdHal.forceClientDisconnect(IFACE_NAME, test_client,
                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER));
        verify(mIHostapdMock).forceClientDisconnect(eq(IFACE_NAME), any(byte[].class),
                eq(Ieee80211ReasonCode.WLAN_REASON_PREV_AUTH_NOT_VALID));
        assertTrue(test_client.equals(MacAddress.fromBytes(macAddrCaptor.getValue())));
    }

    /**
     * Verifies the setting of log level.
     */
    @Test
    public void testSetLogLevel() throws Exception {
        executeAndValidateInitializationSequence(true);
        doNothing().when(mIHostapdMock).setDebugParams(anyInt());

        mHostapdHal.enableVerboseLogging(false, true);
        verify(mIHostapdMock, atLeastOnce())
                .setDebugParams(eq(DebugLevel.DEBUG));

        mHostapdHal.enableVerboseLogging(false, false);
        verify(mIHostapdMock, atLeastOnce())
                .setDebugParams(eq(DebugLevel.INFO));
    }

    /**
     * Verifies the successful addition of access point with SAE.
     */
    @Test
    public void testAddAccessPointSuccess_SAE_WithoutACS() throws Exception {
        // Disable ACS in the config.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, false);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        configurationBuilder.setBand(mBand256G);

        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA3_SAE,
                mNetworkParamsCaptor.getValue().encryptionType);
        assertEquals(NETWORK_PSK, mNetworkParamsCaptor.getValue().passphrase);
    }

    /**
     * Verifies the successful addition of access point with SAE Transition.
     */
    @Test
    public void testAddAccessPointSuccess_SAE_Transition_WithoutACS() throws Exception {
        // Disable ACS in the config.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, false);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION);
        configurationBuilder.setBand(mBand256G);

        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA3_SAE_TRANSITION,
                mNetworkParamsCaptor.getValue().encryptionType);
        assertEquals(NETWORK_PSK, mNetworkParamsCaptor.getValue().passphrase);
    }

    /**
     * Verifies the successful addition of access point when ACS is allowed to include DFS channels.
     */
    @Test
    public void testAddAccessPointSuccess_WithACS_IncludeDFSChannels() throws Exception {
        // Enable ACS in the config.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mResources.setBoolean(R.bool.config_wifiSoftapAcsIncludeDfs, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK);
        configurationBuilder.setBand(mBand256G);

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertEquals(mBand256G,
                mIfaceParamsCaptor.getValue().channelParams[0].bandMask);
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[0].acsShouldExcludeDfs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA2, mNetworkParamsCaptor.getValue().encryptionType);
    }

    /*
     * Sunny day scenario for HostapdHal initialization
     * Asserts successful initialization
     */
    @Test
    public void testHostapdCallbackEvent() throws Exception {
        executeAndValidateInitializationSequence(true);
        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setBand(SoftApConfiguration.BAND_2GHZ);

        doNothing().when(mIHostapdMock).addAccessPoint(any(), any());
        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());
        // Register SoftApManager callback
        mHostapdHal.registerApCallback(IFACE_NAME, mSoftApHalCallback);

        // Add second AP to test that the callbacks are triggered for the correct iface.
        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME_1,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback1.onFailure()));
        mHostapdHal.registerApCallback(IFACE_NAME_1, mSoftApHalCallback1);

        // Trigger on failure.
        mIHostapdCallback.onFailure(IFACE_NAME, IFACE_NAME);
        verify(mSoftApHalCallback).onFailure();
        verify(mSoftApHalCallback1, never()).onFailure();

        // Trigger on info changed and verify.
        mockApInfoChangedAndVerify(IFACE_NAME, 1, mIHostapdCallback, mSoftApHalCallback);
        verify(mSoftApHalCallback1, never()).onInfoChanged(anyString(), anyInt(), anyInt(),
                anyInt(), any(), anyList());

        // Trigger on client connected.
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.ifaceName = IFACE_NAME;
        clientInfo.apIfaceInstance = TEST_AP_INSTANCE;
        clientInfo.clientAddress = MacAddress.fromString(TEST_CLIENT_MAC).toByteArray();
        clientInfo.isConnected = true;
        mIHostapdCallback.onConnectedClientsChanged(clientInfo);
        verify(mSoftApHalCallback).onConnectedClientsChanged(eq(TEST_AP_INSTANCE),
                eq(MacAddress.fromString(TEST_CLIENT_MAC)), eq(true));
        verify(mSoftApHalCallback1, never()).onConnectedClientsChanged(
                anyString(), any(), anyBoolean());
    }

    /**
     * Verifies the successful addition of access point with SAE with metered indication.
     */
    @Test
    public void testAddAccessPointSuccess_WithMeteredSAE() throws Exception {
        boolean isMetered = true;
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        configurationBuilder.setBand(mBand256G);

        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), isMetered,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA3_SAE,
                mNetworkParamsCaptor.getValue().encryptionType);
        assertEquals(NETWORK_PSK, mNetworkParamsCaptor.getValue().passphrase);
        assertTrue(mNetworkParamsCaptor.getValue().isMetered);
    }

    /**
     * Verifies the successful addition of access point with SAE with non metered indication.
     */
    @Test
    public void testAddAccessPointSuccess_WithNonMeteredSAE() throws Exception {
        boolean isMetered = false;
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        configurationBuilder.setBand(mBand256G);

        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), isMetered,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA3_SAE,
                mNetworkParamsCaptor.getValue().encryptionType);
        assertEquals(NETWORK_PSK, mNetworkParamsCaptor.getValue().passphrase);
        assertFalse(mNetworkParamsCaptor.getValue().isMetered);
    }

    /**
     * Verifies the successful addition of access point with Dual channel config.
     */
    @Test
    public void testAddAccessPointSuccess_DualBandConfig() throws Exception {
        assumeTrue(SdkLevel.isAtLeastS()); // dual band supported on S.
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);

        // Enable ACS and set available channels in the config.
        final String acsChannelStr2g = "1,6,11-13";
        final String acsChannelStr5g = "40";
        final String acsChannelStr6g = "";
        mResources.setString(R.string.config_wifiSoftap2gChannelList, acsChannelStr2g);
        mResources.setString(R.string.config_wifiSoftap5gChannelList, acsChannelStr5g);
        mResources.setString(R.string.config_wifiSoftap6gChannelList, acsChannelStr6g);

        FrequencyRange freqRange1 = new FrequencyRange();
        FrequencyRange freqRange2 = new FrequencyRange();
        FrequencyRange freqRange3 = new FrequencyRange();

        freqRange1.startMhz = freqRange1.endMhz = ApConfigUtil.convertChannelToFrequency(
                1, SoftApConfiguration.BAND_2GHZ);
        freqRange2.startMhz = freqRange2.endMhz = ApConfigUtil.convertChannelToFrequency(
                6, SoftApConfiguration.BAND_2GHZ);
        freqRange3.startMhz = ApConfigUtil.convertChannelToFrequency(
                11, SoftApConfiguration.BAND_2GHZ);
        freqRange3.endMhz = ApConfigUtil.convertChannelToFrequency(13,
                SoftApConfiguration.BAND_2GHZ);
        ArrayList<FrequencyRange> acsFreqRanges = new ArrayList<>();
        acsFreqRanges.add(freqRange1);
        acsFreqRanges.add(freqRange2);
        acsFreqRanges.add(freqRange3);

        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        SparseIntArray dual_channels = new SparseIntArray(2);
        dual_channels.put(SoftApConfiguration.BAND_5GHZ, 149);
        dual_channels.put(SoftApConfiguration.BAND_2GHZ, 0);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        configurationBuilder.setChannels(dual_channels);

        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AX);

        // 2.4G band, ACS case.
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);
        assertEquals(mIfaceParamsCaptor.getValue().channelParams[0].bandMask,
                BandMask.BAND_2_GHZ);
        assertEquals(mIfaceParamsCaptor.getValue().channelParams[0].channel, 0);
        assertEquals(mIfaceParamsCaptor.getValue().channelParams[0]
                .acsChannelFreqRangesMhz.length, acsFreqRanges.size());
        for (int i = 0; i < acsFreqRanges.size(); i++) {
            assertEquals(mIfaceParamsCaptor.getValue().channelParams[0]
                    .acsChannelFreqRangesMhz[i].startMhz, acsFreqRanges.get(i).startMhz);
            assertEquals(mIfaceParamsCaptor.getValue().channelParams[0]
                    .acsChannelFreqRangesMhz[i].endMhz, acsFreqRanges.get(i).endMhz);
        }

        // 5G band, specific channel.
        assertFalse(mIfaceParamsCaptor.getValue().channelParams[1].enableAcs);
        assertEquals(mIfaceParamsCaptor.getValue().channelParams[1].bandMask,
                BandMask.BAND_5_GHZ);
        assertEquals(mIfaceParamsCaptor.getValue().channelParams[1].channel, 149);

        // No acsChannelFreqRangesMh
        assertEquals(0,
                mIfaceParamsCaptor.getValue().channelParams[1].acsChannelFreqRangesMhz.length);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA3_SAE,
                mNetworkParamsCaptor.getValue().encryptionType);
        assertEquals(NETWORK_PSK, mNetworkParamsCaptor.getValue().passphrase);
    }

    /**
     * Verifies the successful addition of access point with metered SAE indication on the 80211ax
     * supported device.
     */
    @Test
    public void testAddAccessPointSuccess_WithMeteredSAEOn11AXSupportedDevice()
            throws Exception {
        boolean isMetered = true;
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mResources.setBoolean(R.bool.config_wifiSoftapIeee80211axSupported, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        configurationBuilder.setBand(mBand256G);

        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), isMetered,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AX);
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(EncryptionType.WPA3_SAE,
                mNetworkParamsCaptor.getValue().encryptionType);
        assertEquals(NETWORK_PSK, mNetworkParamsCaptor.getValue().passphrase);
        assertTrue(mNetworkParamsCaptor.getValue().isMetered);
    }

    /**
     * Verifies the successful addition of access point with metered SAE indication on the 80211ax
     * supported device but 80211ax is disabled in configuration.
     */
    @Test
    public void testAddAccessPointSuccess_WithMeteredSAEOn11AXSupportedDeviceBut11AXDisabled()
            throws Exception {
        assumeTrue(SdkLevel.isAtLeastS()); // setIeee80211axEnabled() added on Android S.
        boolean isMetered = true;
        mResources.setBoolean(R.bool.config_wifi_softap_acs_supported, true);
        mResources.setBoolean(R.bool.config_wifiSoftapIeee80211axSupported, true);
        mHostapdHal = new HostapdHalSpy();

        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setHiddenSsid(false);
        configurationBuilder.setPassphrase(NETWORK_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE);
        configurationBuilder.setBand(mBand256G);
        configurationBuilder.setIeee80211axEnabled(false);

        doNothing().when(mIHostapdMock).addAccessPoint(
                mIfaceParamsCaptor.capture(), mNetworkParamsCaptor.capture());

        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), isMetered,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        assertEquals(IFACE_NAME, mIfaceParamsCaptor.getValue().name);
        assertTrue(mIfaceParamsCaptor.getValue().hwModeParams.enable80211N);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AC);
        assertFalse(mIfaceParamsCaptor.getValue().hwModeParams.enable80211AX);
        assertTrue(mIfaceParamsCaptor.getValue().channelParams[0].enableAcs);

        assertEquals(NETWORK_SSID,
                NativeUtil.stringFromByteArray(mNetworkParamsCaptor.getValue().ssid));
        assertFalse(mNetworkParamsCaptor.getValue().isHidden);
        assertEquals(android.hardware.wifi.hostapd.EncryptionType.WPA3_SAE,
                mNetworkParamsCaptor.getValue().encryptionType);
        assertEquals(NETWORK_PSK, mNetworkParamsCaptor.getValue().passphrase);
        assertTrue(mNetworkParamsCaptor.getValue().isMetered);
    }

    /**
     * Verifies the onFailure event in bridged mode.
     */
    @Test
    public void testHostapdCallbackOnFailureEventInBridgedMode() throws Exception {
        assumeTrue(SdkLevel.isAtLeastT());
        executeAndValidateInitializationSequence(true);
        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        configurationBuilder.setBands(new int[] {SoftApConfiguration.BAND_2GHZ,
                SoftApConfiguration.BAND_5GHZ});

        doNothing().when(mIHostapdMock).addAccessPoint(any(), any());
        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());

        // Register SoftApManager callback
        mHostapdHal.registerApCallback(IFACE_NAME, mSoftApHalCallback);

        // Trigger on info changed and verify.
        mockApInfoChangedAndVerify(IFACE_NAME, 2, mIHostapdCallback, mSoftApHalCallback);

        // Trigger on failure from first instance.
        mIHostapdCallback.onFailure(IFACE_NAME, TEST_AP_INSTANCE);
        verify(mSoftApHalCallback).onInstanceFailure(TEST_AP_INSTANCE);

        // Trigger on failure from second instance.
        mIHostapdCallback.onFailure(IFACE_NAME, TEST_AP_INSTANCE_2);
        verify(mSoftApHalCallback).onFailure();

    }

    /**
     * Verifies that SoftApConfigurations containing OEM-specific vendor data
     * are handled currently in addAccessPoint.
     */
    @Test
    public void testAddAccessPointWithVendorData() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mIHostapdMock.getInterfaceVersion()).thenReturn(2);
        executeAndValidateInitializationSequence(true);

        Builder configurationBuilder = new SoftApConfiguration.Builder();
        configurationBuilder.setSsid(NETWORK_SSID);
        doNothing().when(mIHostapdMock).addAccessPoint(mIfaceParamsCaptor.capture(), any());

        // SoftApConfig does not contain vendor data.
        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock).addAccessPoint(any(), any());
        assertNull(mIfaceParamsCaptor.getValue().vendorData);

        int oui = 0x00114477;
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("fieldKey", "someStringValue");
        OuiKeyedData frameworkData = new OuiKeyedData.Builder(oui, bundle).build();
        configurationBuilder.setVendorData(Arrays.asList(frameworkData));

        // SoftApConfig contains vendor data.
        assertTrue(mHostapdHal.addAccessPoint(IFACE_NAME,
                configurationBuilder.build(), true,
                () -> mSoftApHalCallback.onFailure()));
        verify(mIHostapdMock, times(2)).addAccessPoint(any(), any());
        android.hardware.wifi.common.OuiKeyedData[] halDataList =
                mIfaceParamsCaptor.getValue().vendorData;
        assertEquals(1, halDataList.length);
        assertEquals(oui, halDataList[0].oui);
        assertTrue(PersistableBundleUtils.isEqual(bundle, halDataList[0].vendorData));
    }
}
