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
package com.android.server.wifi.p2p;

import static com.android.net.module.util.Inet4AddressUtils.intToInet4AddressHTL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.hardware.wifi.common.OuiKeyedData;
import android.hardware.wifi.supplicant.P2pClientEapolIpAddressInfo;
import android.hardware.wifi.supplicant.P2pDeviceFoundEventParams;
import android.hardware.wifi.supplicant.P2pGoNegotiationReqEventParams;
import android.hardware.wifi.supplicant.P2pGroupStartedEventParams;
import android.hardware.wifi.supplicant.P2pInvitationEventParams;
import android.hardware.wifi.supplicant.P2pPeerClientDisconnectedEventParams;
import android.hardware.wifi.supplicant.P2pPeerClientJoinedEventParams;
import android.hardware.wifi.supplicant.P2pProvDiscStatusCode;
import android.hardware.wifi.supplicant.P2pProvisionDiscoveryCompletedEventParams;
import android.hardware.wifi.supplicant.P2pStatusCode;
import android.hardware.wifi.supplicant.WpsConfigMethods;
import android.hardware.wifi.supplicant.WpsDevPasswordId;
import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.net.wifi.p2p.nsd.WifiP2pServiceResponse;
import android.os.PersistableBundle;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStatus;
import com.android.server.wifi.util.HalAidlUtil;
import com.android.server.wifi.util.NativeUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Unit tests for SupplicantP2pIfaceCallbackAidlImpl
 */
@SmallTest
public class SupplicantP2pIfaceCallbackAidlImplTest extends WifiBaseTest {
    private String mIface = "test_p2p0";
    private String mGroupIface = "test_p2p-p2p0-3";
    private WifiP2pMonitor mMonitor;
    private SupplicantP2pIfaceCallbackAidlImpl mDut;

    private byte[] mDeviceAddressInvalid1 = { 0x00 };
    private byte[] mDeviceAddressInvalid2 = { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66 };
    private byte[] mDeviceAddress1Bytes = { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 };
    private String mDeviceAddress1String = "00:11:22:33:44:55";
    private byte[] mDeviceAddress2Bytes = { 0x01, 0x12, 0x23, 0x34, 0x45, 0x56 };
    private String mDeviceAddress2String = "01:12:23:34:45:56";
    private byte[] mDeviceInfoBytes = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
    private static final byte[] DEVICE_ADDRESS = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
    private static final String DEVICE_ADDRESS_STR = "00:01:02:03:04:05";
    private static final int TEST_NETWORK_ID = 9;
    private static final int TEST_GROUP_FREQUENCY = 5400;
    private static final int DEFAULT_SERVICE_VERSION = 2;
    private byte[] mTestPrimaryDeviceTypeBytes = { 0x00, 0x01, 0x02, -1, 0x04, 0x05, 0x06, 0x07 };
    private String mTestPrimaryDeviceTypeString = "1-02FF0405-1543";
    private String mTestDeviceName = "test device name";
    private short mTestConfigMethods = 0x1234;
    private byte mTestCapabilities = 123;
    private int mTestGroupCapabilities = 456;

    private class SupplicantP2pIfaceCallbackImplSpy extends SupplicantP2pIfaceCallbackAidlImpl {
        SupplicantP2pIfaceCallbackImplSpy(String iface, WifiP2pMonitor monitor,
                int serviceVersion) {
            super(iface, monitor, serviceVersion);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mMonitor = mock(WifiP2pMonitor.class);
        initializeDut(DEFAULT_SERVICE_VERSION);
    }

    private void initializeDut(int serviceVersion) {
        mDut = new SupplicantP2pIfaceCallbackImplSpy(mIface, mMonitor, serviceVersion);
    }

    /**
     * Sunny day scenario for onDeviceFound call.
     */
    @Test
    public void testOnDeviceFound_success() throws Exception {
        byte[] fakePrimaryDeviceTypeBytes = { 0x00, 0x01, 0x02, -1, 0x04, 0x05, 0x06, 0x07 };
        String fakePrimaryDeviceTypeString = "1-02FF0405-1543";
        String fakeDeviceName = "test device name";
        short fakeConfigMethods = 0x1234;
        byte fakeCapabilities = 123;
        int fakeGroupCapabilities = 456;

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pDevice device) {
                // NOTE: mDeviceAddress1Bytes seems to be ignored by
                // legacy implementation of WifiP2pDevice.
                assertEquals(iface, mIface);
                assertEquals(device.deviceName, fakeDeviceName);
                assertEquals(device.primaryDeviceType, fakePrimaryDeviceTypeString);
                assertEquals(device.deviceCapability, fakeCapabilities);
                assertEquals(device.groupCapability, fakeGroupCapabilities);
                assertEquals(device.wpsConfigMethodsSupported, fakeConfigMethods);
                assertEquals(device.deviceAddress, mDeviceAddress2String);
                assertEquals(device.status, WifiP2pDevice.AVAILABLE);
            }
        }).when(mMonitor).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                null);

        // Make sure we issued a broadcast each time.
        verify(mMonitor, times(2)).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));
    }

    private static OuiKeyedData generateHalOuiKeyedData(int oui) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("stringKey", "stringValue");
        bundle.putInt("intKey", 789);

        OuiKeyedData data = new OuiKeyedData();
        data.oui = oui;
        data.vendorData = bundle;
        return data;
    }

    private static OuiKeyedData[] generateHalOuiKeyedDataList(int size) {
        OuiKeyedData[] dataList = new OuiKeyedData[size];
        for (int i = 0; i < size; i++) {
            dataList[i] = generateHalOuiKeyedData(i + 1);
        }
        return dataList;
    }

    /**
     * Sunny day scenario for testOnDeviceFoundWithParams call.
     */
    @Test
    public void testOnDeviceFoundWithParams_success() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        initializeDut(3 /* serviceVersion */);
        byte[] fakePrimaryDeviceTypeBytes = { 0x00, 0x01, 0x02, -1, 0x04, 0x05, 0x06, 0x07 };
        String fakePrimaryDeviceTypeString = "1-02FF0405-1543";
        String fakeDeviceName = "test device name";
        short fakeConfigMethods = 0x1234;
        byte fakeCapabilities = 123;
        int fakeGroupCapabilities = 456;
        OuiKeyedData[] halVendorData = generateHalOuiKeyedDataList(5);

        P2pDeviceFoundEventParams params = new P2pDeviceFoundEventParams();
        params.srcAddress = mDeviceAddress1Bytes;
        params.p2pDeviceAddress = mDeviceAddress2Bytes;
        params.primaryDeviceType = fakePrimaryDeviceTypeBytes;
        params.deviceName = fakeDeviceName;
        params.configMethods = fakeConfigMethods;
        params.deviceCapabilities = fakeCapabilities;
        params.groupCapabilities = fakeGroupCapabilities;
        params.wfdDeviceInfo = mDeviceInfoBytes;
        params.vendorData = halVendorData;

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pDevice device) {
                // NOTE: mDeviceAddress1Bytes seems to be ignored by
                // legacy implementation of WifiP2pDevice.
                assertEquals(iface, mIface);
                assertEquals(device.deviceName, fakeDeviceName);
                assertEquals(device.primaryDeviceType, fakePrimaryDeviceTypeString);
                assertEquals(device.deviceCapability, fakeCapabilities);
                assertEquals(device.groupCapability, fakeGroupCapabilities);
                assertEquals(device.wpsConfigMethodsSupported, fakeConfigMethods);
                assertEquals(device.deviceAddress, mDeviceAddress2String);
                assertEquals(device.status, WifiP2pDevice.AVAILABLE);
                assertEquals(device.getVendorData(),
                        HalAidlUtil.halToFrameworkOuiKeyedDataList(halVendorData));
            }
        }).when(mMonitor).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFoundWithParams(params);

        params.wfdDeviceInfo = null;
        mDut.onDeviceFoundWithParams(params);

        // Make sure we issued a broadcast each time.
        verify(mMonitor, times(2)).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));
    }

    /**
     * Sunny day scenario for onDeviceFound call with sign bit set in bytes.
     */
    @Test
    public void testOnDeviceFoundWithSignBitInDeviceInfoBytesSuccess() throws Exception {
        byte[] fakePrimaryDeviceTypeBytes = { 0x00, 0x01, 0x02, -1, 0x04, 0x05, 0x06, 0x07 };
        String fakePrimaryDeviceTypeString = "1-02FF0405-1543";
        String fakeDeviceName = "test device name";
        short fakeConfigMethods = 0x1234;
        byte fakeCapabilities = 123;
        int fakeGroupCapabilities = 456;
        byte[] fakeDevInfoBytes = { (byte) 0x80, 0x01, (byte) 0xC0, 0x03, (byte) 0xFF, 0x05 };

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                fakeDevInfoBytes);

        ArgumentCaptor<WifiP2pDevice> deviceCaptor = ArgumentCaptor.forClass(WifiP2pDevice.class);
        verify(mMonitor).broadcastP2pDeviceFound(eq(mIface), deviceCaptor.capture());

        WifiP2pDevice device = deviceCaptor.getValue();
        assertEquals(fakeDeviceName, device.deviceName);
        assertEquals(fakePrimaryDeviceTypeString, device.primaryDeviceType);
        assertEquals(fakeCapabilities, device.deviceCapability);
        assertEquals(fakeGroupCapabilities, device.groupCapability);
        assertEquals(fakeConfigMethods, device.wpsConfigMethodsSupported);
        assertEquals(mDeviceAddress2String, device.deviceAddress);
        assertEquals(WifiP2pDevice.AVAILABLE, device.status);

        assertNotNull(device.wfdInfo);
        // WifiP2pWfdInfo.mDeviceInfo won't be returned as the raw value, skip it.
        assertEquals(((fakeDevInfoBytes[2] & 0xFF) << 8) + fakeDevInfoBytes[3],
                device.wfdInfo.getControlPort());
        assertEquals(((fakeDevInfoBytes[4] & 0xFF) << 8) + fakeDevInfoBytes[5],
                device.wfdInfo.getMaxThroughput());

        // Make sure we issued a broadcast each time.
        verify(mMonitor).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));
    }

    /**
     * Failing scenarios for onDeviceFound call.
     */
    @Test
    public void testOnDeviceFound_invalidArguments() throws Exception {
        byte[] fakePrimaryDeviceTypeBytes = { 0x0, 0x01, 0x02, -1, 0x04, 0x05, 0x06, 0x07 };
        String fakeDeviceName = "test device name";
        short fakeConfigMethods = 0x1234;
        byte fakeCapabilities = 123;
        int fakeGroupCapabilities = 456;

        mDut.onDeviceFound(
                mDeviceAddress2Bytes, null,
                fakePrimaryDeviceTypeBytes,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);
        verify(mMonitor, never()).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                null,
                fakeDeviceName, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);
        verify(mMonitor, never()).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                fakePrimaryDeviceTypeBytes,
                null, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);
        verify(mMonitor, never()).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddressInvalid1,
                fakePrimaryDeviceTypeBytes,
                null, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);
        verify(mMonitor, never()).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceFound(
                mDeviceAddress1Bytes, mDeviceAddressInvalid2,
                fakePrimaryDeviceTypeBytes,
                null, fakeConfigMethods,
                fakeCapabilities, fakeGroupCapabilities,
                mDeviceInfoBytes);
        verify(mMonitor, never()).broadcastP2pDeviceFound(
                anyString(), any(WifiP2pDevice.class));
    }

    /**
     * Sunny day scenario for onDeviceLost call.
     */
    @Test
    public void testOnDeviceLost_success() throws Exception {
        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pDevice device) {
                assertEquals(iface, mIface);
                assertEquals(device.deviceAddress, mDeviceAddress1String);
                assertEquals(device.status, WifiP2pDevice.UNAVAILABLE);
            }
        }).when(mMonitor).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceLost(mDeviceAddress1Bytes);

        // Make sure we issued a broadcast each time.
        verify(mMonitor, times(1)).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));
    }

    /**
     * Failing scenarios for onDeviceLost call.
     */
    @Test
    public void testOnDeviceLost_invalidArguments() throws Exception {
        mDut.onDeviceLost(null);
        verify(mMonitor, never()).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceLost(mDeviceAddressInvalid1);
        verify(mMonitor, never()).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));

        mDut.onDeviceLost(mDeviceAddressInvalid2);
        verify(mMonitor, never()).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));
    }

    /**
     * Sunny day scenario for onGoNegotiationRequest call.
     */
    @Test
    public void testOnGoNegotiationRequest_success() throws Exception {
        HashSet<Integer> setups = new HashSet<Integer>();

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pConfig config) {
                assertEquals(iface, mIface);
                assertNotNull(config.wps);
                setups.add(config.wps.setup);
                assertEquals(config.deviceAddress, mDeviceAddress1String);
            }
        }).when(mMonitor).broadcastP2pGoNegotiationRequest(
                anyString(), any(WifiP2pConfig.class));

        mDut.onGoNegotiationRequest(mDeviceAddress1Bytes, WpsDevPasswordId.USER_SPECIFIED);
        assertTrue(setups.contains(WpsInfo.DISPLAY));

        mDut.onGoNegotiationRequest(mDeviceAddress1Bytes, WpsDevPasswordId.PUSHBUTTON);
        assertTrue(setups.contains(WpsInfo.PBC));

        mDut.onGoNegotiationRequest(mDeviceAddress1Bytes, WpsDevPasswordId.REGISTRAR_SPECIFIED);
        assertTrue(setups.contains(WpsInfo.KEYPAD));

        // Invalid should default to PBC
        setups.clear();
        mDut.onGoNegotiationRequest(mDeviceAddress1Bytes, 0xffff);
        assertTrue(setups.contains(WpsInfo.PBC));
    }

    /**
     * Failing scenarios for onGoNegotiationRequest call.
     */
    @Test
    public void testOnGoNegotiationRequest_invalidArguments() throws Exception {
        mDut.onGoNegotiationRequest(null, 0);
        verify(mMonitor, never()).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));

        mDut.onGoNegotiationRequest(mDeviceAddressInvalid1, 0);
        verify(mMonitor, never()).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));

        mDut.onGoNegotiationRequest(mDeviceAddressInvalid2, 0);
        verify(mMonitor, never()).broadcastP2pDeviceLost(
                anyString(), any(WifiP2pDevice.class));
    }

    private static OuiKeyedData[] createTestVendorData() {
        int oui = 0x0033aabb;
        OuiKeyedData vendorDataElement = new OuiKeyedData();
        vendorDataElement.oui = oui;
        vendorDataElement.vendorData = new PersistableBundle();
        return new OuiKeyedData[]{vendorDataElement};
    }

    /**
     * Sunny day scenario for onGoNegotiationRequestWithParams call.
     */
    @Test
    public void testOnGoNegotiationRequestWithParams_success() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        initializeDut(3 /* serviceVersion */);
        HashSet<Integer> setups = new HashSet<Integer>();

        P2pGoNegotiationReqEventParams params = new P2pGoNegotiationReqEventParams();
        params.srcAddress = mDeviceAddress1Bytes;
        params.passwordId = WpsDevPasswordId.USER_SPECIFIED;
        params.vendorData = createTestVendorData();

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pConfig config) {
                assertEquals(iface, mIface);
                assertNotNull(config.wps);
                setups.add(config.wps.setup);
                assertEquals(config.deviceAddress, mDeviceAddress1String);
                assertEquals(params.vendorData[0].oui, config.getVendorData().get(0).getOui());
            }
        }).when(mMonitor).broadcastP2pGoNegotiationRequest(
                anyString(), any(WifiP2pConfig.class));

        mDut.onGoNegotiationRequestWithParams(params);
        assertTrue(setups.contains(WpsInfo.DISPLAY));

        params.passwordId = WpsDevPasswordId.PUSHBUTTON;

        mDut.onGoNegotiationRequestWithParams(params);
        assertTrue(setups.contains(WpsInfo.PBC));

        params.passwordId = WpsDevPasswordId.REGISTRAR_SPECIFIED;

        mDut.onGoNegotiationRequestWithParams(params);
        assertTrue(setups.contains(WpsInfo.KEYPAD));

        params.passwordId = 0xffff;

        // Invalid should default to PBC
        setups.clear();
        mDut.onGoNegotiationRequestWithParams(params);
        assertTrue(setups.contains(WpsInfo.PBC));
    }

    /**
     * Sunny day scenario for onGroupStarted call.
     */
    @Test
    public void testOnGroupStarted_success() throws Exception {
        String fakeName = "group name";
        String fakePassphrase = "secret";
        byte[] fakeSsidBytesList = new byte[] {0x30, 0x31, 0x32, 0x33};
        String fakeSsidString = "0123";
        HashSet<String> passwords = new HashSet<String>();

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pGroup group) {
                assertEquals(iface, mIface);
                assertNotNull(group.getOwner());
                assertEquals(group.getOwner().deviceAddress, mDeviceAddress1String);
                assertEquals(group.getNetworkId(), WifiP2pGroup.NETWORK_ID_PERSISTENT);
                passwords.add(group.getPassphrase());
                assertEquals(group.getInterface(), fakeName);
                assertEquals(group.getNetworkName(), fakeSsidString);
            }
        }).when(mMonitor).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));

        mDut.onGroupStarted(
                fakeName, true, fakeSsidBytesList, 1, null, fakePassphrase,
                mDeviceAddress1Bytes, true);
        assertTrue(passwords.contains(fakePassphrase));

        mDut.onGroupStarted(
                fakeName, true, fakeSsidBytesList, 1, null, null,
                mDeviceAddress1Bytes, true);
        assertTrue(passwords.contains(null));

        verify(mMonitor, times(2)).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));
    }

    /**
     * Failing scenarios for onGroupStarted call.
     */
    @Test
    public void testOnGroupStarted_invalidArguments() throws Exception {
        String fakeName = "group name";
        String fakePassphrase = "secret";
        byte[] fakeSsidBytesList = new byte[] {0x30, 0x31, 0x32, 0x33};

        mDut.onGroupStarted(
                null, true, fakeSsidBytesList, 1, null, fakePassphrase,
                mDeviceAddress1Bytes, true);
        verify(mMonitor, never()).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));

        mDut.onGroupStarted(
                fakeName, true, null, 1, null, fakePassphrase,
                mDeviceAddress1Bytes, true);
        verify(mMonitor, never()).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));

        mDut.onGroupStarted(
                fakeName, true, fakeSsidBytesList, 1, null, fakePassphrase,
                null, true);
        verify(mMonitor, never()).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));
    }

    /**
     * Success scenario for onGroupStartedWithParams call.
     */
    @Test
    public void testOnGroupStartedWithParams_success() throws Exception {
        String fakeName = "group name";
        String fakePassphrase = "secret";
        byte[] fakeSsidBytesList = new byte[] {0x30, 0x31, 0x32, 0x33};
        String fakeSsidString = "0123";
        HashSet<String> passwords = new HashSet<>();
        byte[] fakeMacAddress = new byte[] {0x40, 0x41, 0x42, 0x43, 0x44, 0x45};
        int ipAddressClient = 0xc831a8c0;
        int ipAddressGo = 0x0131a8c0;
        int ipAddressMask = 0x00ffffff;

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pGroup group) {
                assertEquals(iface, mIface);
                assertNotNull(group.getOwner());
                assertEquals(group.getOwner().deviceAddress, mDeviceAddress1String);
                assertEquals(group.getNetworkId(), WifiP2pGroup.NETWORK_ID_PERSISTENT);
                passwords.add(group.getPassphrase());
                assertEquals(group.getInterface(), fakeName);
                assertEquals(group.getNetworkName(), fakeSsidString);
                assertEquals(group.interfaceAddress, fakeMacAddress);
                if (!group.isGroupOwner()) {
                    assertEquals(group.p2pClientEapolIpInfo.mIpAddressClient,
                            intToInet4AddressHTL(ipAddressClient));
                    assertEquals(group.p2pClientEapolIpInfo.mIpAddressGo,
                            intToInet4AddressHTL(ipAddressGo));
                    assertEquals(group.p2pClientEapolIpInfo.mIpAddressMask,
                            intToInet4AddressHTL(ipAddressMask));
                }
            }
        }).when(mMonitor).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));

        P2pGroupStartedEventParams params1 = new P2pGroupStartedEventParams();
        params1.groupInterfaceName = fakeName;
        params1.isGroupOwner = false;
        params1.ssid = fakeSsidBytesList;
        params1.frequencyMHz = 1;
        params1.passphrase = fakePassphrase;
        params1.goDeviceAddress = mDeviceAddress1Bytes;
        params1.goInterfaceAddress = fakeMacAddress;
        params1.isPersistent = true;
        params1.isP2pClientEapolIpAddressInfoPresent = true;
        params1.p2pClientIpInfo = new P2pClientEapolIpAddressInfo();
        params1.p2pClientIpInfo.ipAddressClient = ipAddressClient;
        params1.p2pClientIpInfo.ipAddressGo = ipAddressGo;
        params1.p2pClientIpInfo.ipAddressMask = ipAddressMask;

        mDut.onGroupStartedWithParams(params1);
        assertTrue(passwords.contains(fakePassphrase));

        P2pGroupStartedEventParams params2 = new P2pGroupStartedEventParams();
        params2.groupInterfaceName = fakeName;
        params2.isGroupOwner = true;
        params2.ssid = fakeSsidBytesList;
        params2.frequencyMHz = 1;
        params2.goDeviceAddress = mDeviceAddress1Bytes;
        params2.goInterfaceAddress = fakeMacAddress;
        params2.isPersistent = true;
        mDut.onGroupStartedWithParams(params2);
        assertTrue(passwords.contains(null));

        verify(mMonitor, times(2)).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));
    }

    /**
     * Sunny day scenario for onGroupStartedWithParams call when the parameters
     * include vendor data.
     */
    @Test
    public void testOnGroupStartedWithParams_vendorData_success() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        initializeDut(3 /* serviceVersion */);
        String fakeName = "group name";
        String fakePassphrase = "secret";
        byte[] fakeSsidBytesList = new byte[] {0x30, 0x31, 0x32, 0x33};
        String fakeSsidString = "0123";
        HashSet<String> passwords = new HashSet<String>();
        OuiKeyedData[] halVendorData = generateHalOuiKeyedDataList(5);

        P2pGroupStartedEventParams params = new P2pGroupStartedEventParams();
        params.groupInterfaceName = fakeName;
        params.isGroupOwner = true;
        params.ssid = fakeSsidBytesList;
        params.frequencyMHz = 1;
        params.psk = null;
        params.passphrase = fakePassphrase;
        params.isPersistent = true;
        params.goDeviceAddress = mDeviceAddress1Bytes;
        params.isP2pClientEapolIpAddressInfoPresent = true;
        params.vendorData = halVendorData;

        doAnswer(new AnswerWithArguments() {
            public void answer(String iface, WifiP2pGroup group) {
                assertEquals(iface, mIface);
                assertNotNull(group.getOwner());
                assertEquals(group.getOwner().deviceAddress, mDeviceAddress1String);
                assertEquals(group.getNetworkId(), WifiP2pGroup.NETWORK_ID_PERSISTENT);
                passwords.add(group.getPassphrase());
                assertEquals(group.getInterface(), fakeName);
                assertEquals(group.getNetworkName(), fakeSsidString);
                assertEquals(group.getVendorData(),
                        HalAidlUtil.halToFrameworkOuiKeyedDataList(halVendorData));
            }
        }).when(mMonitor).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));

        mDut.onGroupStartedWithParams(params);
        assertTrue(passwords.contains(fakePassphrase));

        params.passphrase = null;
        mDut.onGroupStartedWithParams(params);
        assertTrue(passwords.contains(null));

        verify(mMonitor, times(2)).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));
    }

    /**
     * Failing scenarios for onGroupStartedWithParams call.
     */
    @Test
    public void testOnGroupStartedWithParams_invalidArguments()
            throws Exception {
        String fakeName = "group name";
        String fakePassphrase = "secret";
        byte[] fakeSsidBytesList = new byte[] {0x30, 0x31, 0x32, 0x33};
        byte[] fakeMacAddress = new byte[] {0x40, 0x41, 0x42, 0x43, 0x44, 0x45};

        P2pGroupStartedEventParams params1 = new P2pGroupStartedEventParams();
        params1.isGroupOwner = true;
        params1.ssid = fakeSsidBytesList;
        params1.frequencyMHz = 1;
        params1.passphrase = fakePassphrase;
        params1.goDeviceAddress = mDeviceAddress1Bytes;
        params1.goInterfaceAddress = fakeMacAddress;
        params1.isPersistent = true;
        mDut.onGroupStartedWithParams(params1);
        verify(mMonitor, never()).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));

        P2pGroupStartedEventParams params2 = new P2pGroupStartedEventParams();
        params2.groupInterfaceName = fakeName;
        params2.isGroupOwner = true;
        params2.frequencyMHz = 1;
        params1.passphrase = fakePassphrase;
        params2.goDeviceAddress = mDeviceAddress1Bytes;
        params2.goInterfaceAddress = fakeMacAddress;
        params2.isPersistent = true;
        mDut.onGroupStartedWithParams(params2);
        verify(mMonitor, never()).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));

        P2pGroupStartedEventParams params3 = new P2pGroupStartedEventParams();
        params3.groupInterfaceName = fakeName;
        params3.isGroupOwner = true;
        params3.ssid = fakeSsidBytesList;
        params3.frequencyMHz = 1;
        params3.passphrase = fakePassphrase;
        params3.goInterfaceAddress = fakeMacAddress;
        params3.isPersistent = true;
        mDut.onGroupStartedWithParams(params3);
        verify(mMonitor, never()).broadcastP2pGroupStarted(
                anyString(), any(WifiP2pGroup.class));
    }

    /**
     * Test provision discovery callback.
     */
    @Test
    public void testOnProvisionDiscoveryCompleted() throws Exception {
        byte[] p2pDeviceAddr = DEVICE_ADDRESS;
        boolean isRequest = false;
        byte status = P2pProvDiscStatusCode.SUCCESS;
        short configMethods = WpsConfigMethods.DISPLAY;
        String generatedPin = "12345678";

        ArgumentCaptor<WifiP2pProvDiscEvent> discEventCaptor =
                ArgumentCaptor.forClass(WifiP2pProvDiscEvent.class);
        mDut.onProvisionDiscoveryCompleted(
                p2pDeviceAddr, isRequest, status, configMethods, generatedPin);
        verify(mMonitor).broadcastP2pProvisionDiscoveryEnterPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.ENTER_PIN, discEventCaptor.getValue().event);

        configMethods = WpsConfigMethods.KEYPAD;
        mDut.onProvisionDiscoveryCompleted(
                p2pDeviceAddr, isRequest, status, configMethods, generatedPin);
        verify(mMonitor).broadcastP2pProvisionDiscoveryShowPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.SHOW_PIN, discEventCaptor.getValue().event);
        assertEquals(generatedPin, discEventCaptor.getValue().pin);

        isRequest = true;
        configMethods = WpsConfigMethods.KEYPAD;
        mDut.onProvisionDiscoveryCompleted(
                p2pDeviceAddr, isRequest, status, configMethods, generatedPin);
        verify(mMonitor, times(2)).broadcastP2pProvisionDiscoveryEnterPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.ENTER_PIN, discEventCaptor.getValue().event);

        configMethods = WpsConfigMethods.DISPLAY;
        mDut.onProvisionDiscoveryCompleted(
                p2pDeviceAddr, isRequest, status, configMethods, generatedPin);
        verify(mMonitor, times(2)).broadcastP2pProvisionDiscoveryShowPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.SHOW_PIN, discEventCaptor.getValue().event);
        assertEquals(generatedPin, discEventCaptor.getValue().pin);

        isRequest = false;
        configMethods = WpsConfigMethods.PUSHBUTTON;
        mDut.onProvisionDiscoveryCompleted(
                p2pDeviceAddr, isRequest, status, configMethods, generatedPin);
        verify(mMonitor).broadcastP2pProvisionDiscoveryPbcResponse(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.PBC_RSP, discEventCaptor.getValue().event);

        isRequest = true;
        mDut.onProvisionDiscoveryCompleted(
                p2pDeviceAddr, isRequest, status, configMethods, generatedPin);
        verify(mMonitor).broadcastP2pProvisionDiscoveryPbcRequest(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.PBC_REQ, discEventCaptor.getValue().event);
    }

    /**
     * Test provision discovery completed callback.
     */
    @Test
    public void testOnProvisionDiscoveryCompletedEvent() throws Exception {
        P2pProvisionDiscoveryCompletedEventParams params =
                new P2pProvisionDiscoveryCompletedEventParams();
        params.p2pDeviceAddress = DEVICE_ADDRESS;
        params.isRequest = false;
        params.status = P2pProvDiscStatusCode.SUCCESS;
        params.configMethods = WpsConfigMethods.DISPLAY;
        params.generatedPin = "12345678";
        params.groupInterfaceName = null;

        ArgumentCaptor<WifiP2pProvDiscEvent> discEventCaptor =
                ArgumentCaptor.forClass(WifiP2pProvDiscEvent.class);
        mDut.onProvisionDiscoveryCompletedEvent(params);
        verify(mMonitor).broadcastP2pProvisionDiscoveryEnterPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.ENTER_PIN, discEventCaptor.getValue().event);

        params.configMethods = WpsConfigMethods.KEYPAD;
        mDut.onProvisionDiscoveryCompletedEvent(params);
        verify(mMonitor).broadcastP2pProvisionDiscoveryShowPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.SHOW_PIN, discEventCaptor.getValue().event);
        assertEquals("12345678", discEventCaptor.getValue().pin);

        params.isRequest = true;
        params.configMethods = WpsConfigMethods.KEYPAD;
        mDut.onProvisionDiscoveryCompletedEvent(params);
        verify(mMonitor, times(2)).broadcastP2pProvisionDiscoveryEnterPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.ENTER_PIN, discEventCaptor.getValue().event);

        params.configMethods = WpsConfigMethods.DISPLAY;
        mDut.onProvisionDiscoveryCompletedEvent(params);
        verify(mMonitor, times(2)).broadcastP2pProvisionDiscoveryShowPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.SHOW_PIN, discEventCaptor.getValue().event);
        assertEquals("12345678", discEventCaptor.getValue().pin);

        params.isRequest = false;
        params.configMethods = WpsConfigMethods.PUSHBUTTON;
        mDut.onProvisionDiscoveryCompletedEvent(params);
        verify(mMonitor).broadcastP2pProvisionDiscoveryPbcResponse(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.PBC_RSP, discEventCaptor.getValue().event);

        params.isRequest = true;
        params.groupInterfaceName = "group name";
        mDut.onProvisionDiscoveryCompletedEvent(params);
        verify(mMonitor).broadcastP2pProvisionDiscoveryPbcRequest(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.PBC_REQ, discEventCaptor.getValue().event);
    }

    /**
     * Test provision discovery event callback when the event contains vendor data.
     */
    @Test
    public void testOnProvisionDiscoveryCompletedEvent_vendorData() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        initializeDut(3 /* serviceVersion */);
        OuiKeyedData[] halVendorData = generateHalOuiKeyedDataList(5);
        List<android.net.wifi.OuiKeyedData> frameworkVendorData =
                HalAidlUtil.halToFrameworkOuiKeyedDataList(halVendorData);

        P2pProvisionDiscoveryCompletedEventParams params =
                new P2pProvisionDiscoveryCompletedEventParams();
        params.p2pDeviceAddress = DEVICE_ADDRESS;
        params.isRequest = false;
        params.status = P2pProvDiscStatusCode.SUCCESS;
        params.configMethods = WpsConfigMethods.DISPLAY;
        params.generatedPin = "12345678";
        params.vendorData = halVendorData;

        ArgumentCaptor<WifiP2pProvDiscEvent> discEventCaptor =
                ArgumentCaptor.forClass(WifiP2pProvDiscEvent.class);
        mDut.onProvisionDiscoveryCompletedEvent(params);
        verify(mMonitor).broadcastP2pProvisionDiscoveryEnterPin(
                anyString(), discEventCaptor.capture());
        assertEquals(WifiP2pProvDiscEvent.ENTER_PIN, discEventCaptor.getValue().event);
        assertEquals(frameworkVendorData, discEventCaptor.getValue().getVendorData());
    }

    private void verifyProvisionDiscoveryFailureEvent(
            int halStatus, int expectedStatus) throws Exception {
        byte[] p2pDeviceAddr = DEVICE_ADDRESS;
        boolean isRequest = false;
        byte status = (byte) halStatus;
        short configMethods = WpsConfigMethods.DISPLAY;
        String generatedPin = "12345678";

        ArgumentCaptor<WifiP2pProvDiscEvent> discEventCaptor =
                ArgumentCaptor.forClass(WifiP2pProvDiscEvent.class);
        mDut.onProvisionDiscoveryCompleted(
                p2pDeviceAddr, isRequest, status, configMethods, generatedPin);
        verify(mMonitor).broadcastP2pProvisionDiscoveryFailure(eq(mIface),
                eq(expectedStatus), discEventCaptor.capture());
        WifiP2pProvDiscEvent event = discEventCaptor.getValue();
        assertEquals(DEVICE_ADDRESS_STR, event.device.deviceAddress);
    }

    /**
     * Test provision discovery callback for timeout.
     */
    @Test
    public void testOnProvisionDiscoveryTimeout() throws Exception {
        verifyProvisionDiscoveryFailureEvent(
                P2pProvDiscStatusCode.TIMEOUT,
                WifiP2pMonitor.PROV_DISC_STATUS_TIMEOUT);
    }

    /**
     * Test provision discovery callback for rejection.
     */
    @Test
    public void testOnProvisionDiscoveryRejection() throws Exception {
        verifyProvisionDiscoveryFailureEvent(
                P2pProvDiscStatusCode.REJECTED,
                WifiP2pMonitor.PROV_DISC_STATUS_REJECTED);
    }

    /**
     * Test provision discovery callback for joining timeout.
     */
    @Test
    public void testOnProvisionDiscoveryJoinTimeout() throws Exception {
        verifyProvisionDiscoveryFailureEvent(
                P2pProvDiscStatusCode.TIMEOUT_JOIN,
                WifiP2pMonitor.PROV_DISC_STATUS_TIMEOUT_JOIN);
    }

    /**
     * Test provision discovery callback for information unavailable
     */
    @Test
    public void testOnProvisionDiscoveryInfoUnavailable() throws Exception {
        verifyProvisionDiscoveryFailureEvent(
                P2pProvDiscStatusCode.INFO_UNAVAILABLE,
                WifiP2pMonitor.PROV_DISC_STATUS_INFO_UNAVAILABLE);
    }

    /**
     * Test staAuth with device address, should trigger ApStaConnected broadcast
     */
    @Test
    public void testStaAuth_success() {
        // Trigger onStaAuthorized callback, ensure wifimonitor broadcast is sent with WifiP2pDevice
        // using the p2pDeviceAddress
        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        mDut.onStaAuthorized(mDeviceAddress1Bytes, mDeviceAddress2Bytes);
        verify(mMonitor).broadcastP2pApStaConnected(any(String.class), p2pDeviceCaptor.capture());
        assertEquals(mDeviceAddress2String, p2pDeviceCaptor.getValue().deviceAddress);
    }

    /**
     * Test staAuth without device address, should trigger ApStaConnected broadcast using srcAddress
     */
    @Test
    public void testStaAuth_noDeviceAddress_success() {
        // Trigger onStaAuthorized callback, using a zero'd p2pDeviceAddress, ensure wifimonitor
        // broadcast is sent with WifiP2pDevice using the srcAddress
        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        mDut.onStaAuthorized(mDeviceAddress1Bytes, NativeUtil.ANY_MAC_BYTES);
        verify(mMonitor).broadcastP2pApStaConnected(any(String.class), p2pDeviceCaptor.capture());
        assertEquals(mDeviceAddress1String, p2pDeviceCaptor.getValue().deviceAddress);
    }

    /**
     * Test onPeerClientJoined
     */
    @Test
    public void testOnPeerClientJoined() {
        assumeTrue(SdkLevel.isAtLeastV());
        initializeDut(3 /* serviceVersion */);
        OuiKeyedData[] halVendorData = generateHalOuiKeyedDataList(5);
        List<android.net.wifi.OuiKeyedData> frameworkVendorData =
                HalAidlUtil.halToFrameworkOuiKeyedDataList(halVendorData);

        P2pPeerClientJoinedEventParams params = new P2pPeerClientJoinedEventParams();
        params.clientInterfaceAddress = mDeviceAddress1Bytes;
        params.clientDeviceAddress = mDeviceAddress2Bytes;
        params.vendorData = halVendorData;
        params.clientIpAddress = 0xc831a8c0;

        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        mDut.onPeerClientJoined(params);
        verify(mMonitor).broadcastP2pApStaConnected(eq(mIface), p2pDeviceCaptor.capture());
        assertEquals(mDeviceAddress2String, p2pDeviceCaptor.getValue().deviceAddress);
        assertEquals(MacAddress.fromBytes(mDeviceAddress1Bytes),
                p2pDeviceCaptor.getValue().getInterfaceMacAddress());
        assertEquals(frameworkVendorData, p2pDeviceCaptor.getValue().getVendorData());
    }

    // TLVS hex data encoded as a hex string.
    // Taken directly from an observed supplicant service response event
    private static final String SERV_DISC_RESP_TLVS = "1d00010100076578616d706c650b5f6166706f766572"
            + "746370c00c001001001e000101000b5f6166706f766572746370c00c000c01074578616d706c65c0273c"
            + "00010100096d797072696e746572045f697070c00c00100109747874766572733d311a70646c3d617070"
            + "6c69636174696f6e2f706f73747363726970741900010100045f697070c00c000c01094d795072696e74"
            + "6572c0275f000201000a757569643a36383539646564652d383537342d353961622d393333322d313233"
            + "3435363738393031313a3a75726e3a736368656d61732d75706e702d6f72673a736572766963653a436f"
            + "6e6e656374696f6e4d616e616765723a3159000201000a757569643a36383539646564652d383537342d"
            + "353961622d393333322d3132333435363738393031313a3a75726e3a736368656d61732d75706e702d6f"
            + "72673a736572766963653a41565472616e73706f72743a315a000201000a757569643a36383539646564"
            + "652d383537342d353961622d393333322d3132333435363738393031313a3a75726e3a736368656d6173"
            + "2d75706e702d6f72673a6465766963653a4d6564696152656e64657265723a313e000201000a75756964"
            + "3a36383539646564652d383537342d353961622d393333322d3132333435363738393031313a3a75706e"
            + "703a726f6f746465766963652d000201000a757569643a36383539646564652d383537342d353961622d"
            + "393333322d313233343536373839303131";

    /**
     * Pretty basic onServiceDiscoveryResponse callback test.
     * Mocks the callback event, passing some observed real data to it, and ensures that it returns
     * a non-null WifiP2pServiceResponse list.
     */
    @Test
    public void testOnServiceDiscoveryResponseCompleted_success() throws Exception {
        byte[] tlvs = hexStr2Bin(SERV_DISC_RESP_TLVS);
        ArgumentCaptor<List<WifiP2pServiceResponse>> respListCaptor =
                ArgumentCaptor.forClass(List.class);
        mDut.onServiceDiscoveryResponse(
                mDeviceAddress1Bytes,
                (char) 10 /* unused updateIndicator value */,
                tlvs);
        verify(mMonitor).broadcastP2pServiceDiscoveryResponse(anyString(),
                respListCaptor.capture());
        assertNotNull(respListCaptor.getValue());
        assertNotNull(respListCaptor.getValue());
    }

    /**
     * Test onFindStopped event should trigger P2pFindStopped broadcast.
     */
    @Test
    public void testOnFindStopped() {
        mDut.onFindStopped();
        verify(mMonitor).broadcastP2pFindStopped(eq(mIface));
    }

    /**
     * Test onGoNegotiationCompleted sunny case.
     */
    @Test
    public void testOnGoNegotiationCompletedSuccess() {
        mDut.onGoNegotiationCompleted(P2pStatusCode.SUCCESS);
        verify(mMonitor).broadcastP2pGoNegotiationSuccess(eq(mIface));
    }

    /**
     * Test onGoNegotiationCompleted failure cases.
     */
    @Test
    public void testOnGoNegotiationCompletedFailureUnavailable() {
        mDut.onGoNegotiationCompleted(P2pStatusCode.FAIL_INFO_CURRENTLY_UNAVAILABLE);
        verify(mMonitor).broadcastP2pGoNegotiationFailure(
                eq(mIface), eq(P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE));
    }

    /**
     * Test onGroupFormationSuccess should trigger P2pGroupFormationSuccess broadcast.
     */
    @Test
    public void testOnGroupFormationSuccess() {
        mDut.onGroupFormationSuccess();
        verify(mMonitor).broadcastP2pGroupFormationSuccess(eq(mIface));
    }

    /**
     * Test onGroupFormationFailure should trigger P2pGroupFormationFailure broadcast.
     */
    @Test
    public void testOnGroupFormationFailure() {
        mDut.onGroupFormationFailure("failure-reason");
        verify(mMonitor).broadcastP2pGroupFormationFailure(eq(mIface), eq("failure-reason"));
    }

    /**
     * Test onGroupRemoved should trigger P2pGroupRemoved broadcast for Group Owner.
     */
    @Test
    public void testOnGroupRemovedForGroupOwner() {
        mDut.onGroupRemoved(mGroupIface, true);
        ArgumentCaptor<WifiP2pGroup> groupCaptor = ArgumentCaptor.forClass(WifiP2pGroup.class);
        verify(mMonitor).broadcastP2pGroupRemoved(eq(mIface), groupCaptor.capture());
        assertEquals(mGroupIface, groupCaptor.getValue().getInterface());
        assertEquals(true, groupCaptor.getValue().isGroupOwner());
    }

    /**
     * Test onGroupRemoved should trigger P2pGroupRemoved broadcast for Group Client.
     */
    @Test
    public void testOnGroupRemovedForGroupClient() {
        mDut.onGroupRemoved(mGroupIface, false);

        ArgumentCaptor<WifiP2pGroup> groupCaptor = ArgumentCaptor.forClass(WifiP2pGroup.class);
        verify(mMonitor).broadcastP2pGroupRemoved(eq(mIface), groupCaptor.capture());
        assertEquals(mGroupIface, groupCaptor.getValue().getInterface());
        assertEquals(false, groupCaptor.getValue().isGroupOwner());
    }

    /**
     * Test onInvitationReceived should trigger P2pInvitationReceived broadcast.
     */
    @Test
    public void testOnInvitationReceived() {
        mDut.onInvitationReceived(
                mDeviceAddress1Bytes,
                mDeviceAddress2Bytes,
                DEVICE_ADDRESS,
                TEST_NETWORK_ID,
                TEST_GROUP_FREQUENCY);

        ArgumentCaptor<WifiP2pGroup> groupCaptor = ArgumentCaptor.forClass(WifiP2pGroup.class);
        verify(mMonitor).broadcastP2pInvitationReceived(eq(mIface), groupCaptor.capture());

        WifiP2pGroup group = groupCaptor.getValue();
        assertEquals(TEST_NETWORK_ID, group.getNetworkId());
        assertEquals(mDeviceAddress2String, group.getOwner().deviceAddress);
    }

    /**
     * Test testOnInvitationReceivedWithParams should trigger P2pInvitationReceived broadcast.
     */
    @Test
    public void testOnInvitationReceivedWithParams() {
        assumeTrue(SdkLevel.isAtLeastV());
        initializeDut(3 /* serviceVersion */);

        P2pInvitationEventParams params = new P2pInvitationEventParams();
        params.srcAddress = mDeviceAddress1Bytes;
        params.goDeviceAddress = mDeviceAddress2Bytes;
        params.bssid = DEVICE_ADDRESS;
        params.persistentNetworkId = TEST_NETWORK_ID;
        params.operatingFrequencyMHz = TEST_GROUP_FREQUENCY;
        params.vendorData = createTestVendorData();

        mDut.onInvitationReceivedWithParams(params);

        ArgumentCaptor<WifiP2pGroup> groupCaptor = ArgumentCaptor.forClass(WifiP2pGroup.class);
        verify(mMonitor).broadcastP2pInvitationReceived(eq(mIface), groupCaptor.capture());

        WifiP2pGroup group = groupCaptor.getValue();
        assertEquals(TEST_NETWORK_ID, group.getNetworkId());
        assertEquals(mDeviceAddress2String, group.getOwner().deviceAddress);
        assertEquals(params.vendorData[0].oui, group.getVendorData().get(0).getOui());
    }

    /**
     * Test onInvitationResult should trigger P2pInvitationResult broadcast.
     */
    @Test
    public void testOnInvitationResult() {
        mDut.onInvitationResult(mDeviceAddress1Bytes, P2pStatusCode.SUCCESS);
        verify(mMonitor).broadcastP2pInvitationResult(eq(mIface), eq(P2pStatus.SUCCESS));
    }

    /**
     * Test onStaDeauthorized should trigger P2pApStaDisconnected broadcast.
     */
    @Test
    public void testOnStaDeauthorized() {
        mDut.onStaDeauthorized(mDeviceAddress1Bytes, mDeviceAddress2Bytes);

        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        verify(mMonitor).broadcastP2pApStaDisconnected(eq(mIface), p2pDeviceCaptor.capture());
        assertEquals(mDeviceAddress2String, p2pDeviceCaptor.getValue().deviceAddress);
    }

    /**
     * Test onP2pApStaDisconnected callback
     */
    @Test
    public void testOnPeerClientDisconnected() {
        assumeTrue(SdkLevel.isAtLeastV());
        initializeDut(3 /* serviceVersion */);
        OuiKeyedData[] halVendorData = generateHalOuiKeyedDataList(5);
        List<android.net.wifi.OuiKeyedData> frameworkVendorData =
                HalAidlUtil.halToFrameworkOuiKeyedDataList(halVendorData);

        P2pPeerClientDisconnectedEventParams params = new P2pPeerClientDisconnectedEventParams();
        params.clientInterfaceAddress = mDeviceAddress1Bytes;
        params.clientDeviceAddress = mDeviceAddress2Bytes;
        params.vendorData = halVendorData;

        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        mDut.onPeerClientDisconnected(params);
        verify(mMonitor).broadcastP2pApStaDisconnected(eq(mIface), p2pDeviceCaptor.capture());
        assertEquals(mDeviceAddress2String, p2pDeviceCaptor.getValue().deviceAddress);
        assertEquals(MacAddress.fromBytes(mDeviceAddress1Bytes),
                p2pDeviceCaptor.getValue().getInterfaceMacAddress());
        assertEquals(frameworkVendorData, p2pDeviceCaptor.getValue().getVendorData());
    }

    /**
     * Test the sunny case of onDeviceFoundWithVendorElements.
     */
    @Test
    public void testOnDeviceFoundWithVendorElements() throws Exception {
        byte[] testVsieBytes = {
                (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3, 0x0,
                (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3, 0x1};
        ArrayList<ScanResult.InformationElement> expectedVsieList = new ArrayList<>();
        expectedVsieList.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[]{0x1, 0x2, 0x3, 0x0}));
        expectedVsieList.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[]{0x1, 0x2, 0x3, 0x1}));
        mDut.onDeviceFoundWithVendorElements(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                mTestPrimaryDeviceTypeBytes,
                mTestDeviceName, mTestConfigMethods,
                mTestCapabilities, mTestGroupCapabilities,
                mDeviceInfoBytes, null, testVsieBytes);
        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        verify(mMonitor).broadcastP2pDeviceFound(eq(mIface), p2pDeviceCaptor.capture());

        assertInformationElementListEquals(
                expectedVsieList, p2pDeviceCaptor.getValue().getVendorElements());
    }

    /**
     * Test onDeviceFoundWithVendorElements with non-vendor specific information elements.
     */
    @Test
    public void testOnDeviceFoundWithVendorElementsWithNonVsie() throws Exception {
        byte[] testVsieBytes = {
                ScanResult.InformationElement.EID_SSID, 4, 0x1, 0x2, 0x3, 0x0,
                (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3, 0x1};
        // The first non-vendor specific ie is omitted.
        ArrayList<ScanResult.InformationElement> expectedVsieList = new ArrayList<>();
        expectedVsieList.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[]{0x1, 0x2, 0x3, 0x1}));

        mDut.onDeviceFoundWithVendorElements(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                mTestPrimaryDeviceTypeBytes,
                mTestDeviceName, mTestConfigMethods,
                mTestCapabilities, mTestGroupCapabilities,
                mDeviceInfoBytes, null, testVsieBytes);
        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        verify(mMonitor).broadcastP2pDeviceFound(eq(mIface), p2pDeviceCaptor.capture());

        assertInformationElementListEquals(
                expectedVsieList, p2pDeviceCaptor.getValue().getVendorElements());
    }

    /**
     * Test onDeviceFoundWithVendorElements with malformed specific information elements.
     */
    @Test
    public void testOnDeviceFoundWithVendorElementsWithMalformedVsie() throws Exception {
        byte[] testVsieBytes = {
                (byte) (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3, 0x0,
                (byte) (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3, 0x1,
                (byte) (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3};
        // The last one is omitted.
        ArrayList<ScanResult.InformationElement> expectedVsieList = new ArrayList<>();
        expectedVsieList.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[]{0x1, 0x2, 0x3, 0x0}));
        expectedVsieList.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[]{0x1, 0x2, 0x3, 0x1}));

        mDut.onDeviceFoundWithVendorElements(
                mDeviceAddress1Bytes, mDeviceAddress2Bytes,
                mTestPrimaryDeviceTypeBytes,
                mTestDeviceName, mTestConfigMethods,
                mTestCapabilities, mTestGroupCapabilities,
                mDeviceInfoBytes, null, testVsieBytes);
        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        verify(mMonitor).broadcastP2pDeviceFound(eq(mIface), p2pDeviceCaptor.capture());

        assertInformationElementListEquals(
                expectedVsieList, p2pDeviceCaptor.getValue().getVendorElements());
    }

    /**
     * Test a successful call to testOnDeviceFoundWithParams.
     */
    @Test
    public void testOnDeviceFoundWithParams() throws Exception {
        byte[] testVsieBytes = {
                (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3, 0x0,
                (byte) ScanResult.InformationElement.EID_VSA, 4, 0x1, 0x2, 0x3, 0x1};
        ArrayList<ScanResult.InformationElement> expectedVsieList = new ArrayList<>();
        expectedVsieList.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[]{0x1, 0x2, 0x3, 0x0}));
        expectedVsieList.add(new ScanResult.InformationElement(
                ScanResult.InformationElement.EID_VSA, 0, new byte[]{0x1, 0x2, 0x3, 0x1}));

        P2pDeviceFoundEventParams params = new P2pDeviceFoundEventParams();
        params.srcAddress = mDeviceAddress1Bytes;
        params.p2pDeviceAddress = mDeviceAddress2Bytes;
        params.primaryDeviceType = mTestPrimaryDeviceTypeBytes;
        params.deviceName = mTestDeviceName;
        params.configMethods = mTestConfigMethods;
        params.deviceCapabilities = mTestCapabilities;
        params.groupCapabilities = mTestGroupCapabilities;
        params.wfdDeviceInfo = mDeviceInfoBytes;
        params.wfdR2DeviceInfo = null;
        params.vendorElemBytes = testVsieBytes;
        params.vendorData = null;

        mDut.onDeviceFoundWithParams(params);
        ArgumentCaptor<WifiP2pDevice> p2pDeviceCaptor =
                ArgumentCaptor.forClass(WifiP2pDevice.class);
        verify(mMonitor).broadcastP2pDeviceFound(eq(mIface), p2pDeviceCaptor.capture());

        assertInformationElementListEquals(
                expectedVsieList, p2pDeviceCaptor.getValue().getVendorElements());
    }

    /**
     * Helper function for comparing InformationElement lists.
     *
     * InformationElement equals() is implemented in S. List equals() cannot
     * work in preS.
     */
    private void assertInformationElementListEquals(List<ScanResult.InformationElement> a,
            List<ScanResult.InformationElement> b) {
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.size(), b.size());

        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).id, b.get(i).id);
            assertEquals(a.get(i).idExt, b.get(i).idExt);
            assertTrue(Arrays.equals(a.get(i).bytes, b.get(i).bytes));
        }
    }

    /**
     * Converts hex string to byte array.
     *
     * @param hex hex string. if invalid, return null.
     * @return binary data.
     */
    private static byte[] hexStr2Bin(String hex) {
        int sz = hex.length() / 2;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < sz; i++) {
            try {
                b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            } catch (Exception e) {
                return null;
            }
        }
        return b;
    }
}
