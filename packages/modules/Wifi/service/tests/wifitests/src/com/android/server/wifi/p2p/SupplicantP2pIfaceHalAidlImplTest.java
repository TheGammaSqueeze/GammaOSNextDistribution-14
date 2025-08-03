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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyByte;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.hardware.wifi.supplicant.FreqRange;
import android.hardware.wifi.supplicant.ISupplicant;
import android.hardware.wifi.supplicant.ISupplicantP2pIface;
import android.hardware.wifi.supplicant.ISupplicantP2pIfaceCallback;
import android.hardware.wifi.supplicant.ISupplicantP2pNetwork;
import android.hardware.wifi.supplicant.IfaceInfo;
import android.hardware.wifi.supplicant.MacAddress;
import android.hardware.wifi.supplicant.MiracastMode;
import android.hardware.wifi.supplicant.P2pConnectInfo;
import android.hardware.wifi.supplicant.P2pExtListenInfo;
import android.hardware.wifi.supplicant.P2pFrameTypeMask;
import android.hardware.wifi.supplicant.SupplicantStatusCode;
import android.hardware.wifi.supplicant.WpsProvisionMethod;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pExtListenParams;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.text.TextUtils;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.util.NativeUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for SupplicantP2pIfaceHalAidlImpl
 */
@SmallTest
public class SupplicantP2pIfaceHalAidlImplTest extends WifiBaseTest {
    private SupplicantP2pIfaceHalAidlImpl mDut;
    private IBinder.DeathRecipient mDeathRecipient;
    private @Mock ISupplicant mISupplicantMock;
    private @Mock ISupplicantP2pIface mISupplicantP2pIfaceMock;
    private @Mock ISupplicantP2pNetwork mISupplicantP2pNetworkMock;
    private @Mock WifiP2pMonitor mWifiMonitor;
    private @Mock WifiInjector mWifiInjector;
    private @Mock IBinder mServiceBinderMock;
    private @Mock WifiSettingsConfigStore mWifiSettingsConfigStore;
    private @Mock WifiNative.SupplicantDeathEventHandler mSupplicantHalDeathHandler;

    private ArgumentCaptor<IBinder.DeathRecipient> mSupplicantDeathCaptor =
            ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

    final int mServiceVersion = 2;
    final String mIfaceName = "virtual_interface_name";
    final String mSsid = "\"SSID\"";
    final byte[] mSsidBytes = {'S', 'S', 'I', 'D'};
    final String mPeerMacAddress = "00:11:22:33:44:55";
    final byte[] mPeerMacAddressBytes = { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55 };
    final String mGroupOwnerMacAddress = "01:12:23:34:45:56";
    final byte[] mGroupOwnerMacAddressBytes = { 0x01, 0x12, 0x23, 0x34, 0x45, 0x56 };
    final String mInvalidMacAddress1 = "00:11:22:33:44";
    final String mInvalidMacAddress2 = ":::::";
    final String mInvalidMacAddress3 = "invalid";
    final byte[] mInvalidMacAddressBytes1 = null;
    final byte[] mInvalidMacAddressBytes2 = { };
    final byte[] mInvalidMacAddressBytes3 = { 0x00, 0x01, 0x02, 0x03, 0x04 };
    final byte[] mInvalidMacAddressBytes4 = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06 };
    HashSet<String> mInvalidMacAddresses = new HashSet<String>(Arrays.asList(
            mInvalidMacAddress1, mInvalidMacAddress2,
            mInvalidMacAddress3));

    final String mInvalidService1 = null;
    final String mInvalidService2 = "service";
    final String mValidServiceRequestString = "30313233";
    final byte[] mValidServiceRequestBytes = { 0x30, 0x31, 0x32, 0x33 };
    final String mInvalidServiceRequestString = "not a hex string";
    final String mInvalidUpnpService1 = "upnp";
    final String mInvalidUpnpService2 = "upnp 1";
    final String mInvalidUpnpService3 = "upnp invalid_number name";
    final String mInvalidBonjourService1 = "bonjour";
    final String mInvalidBonjourService2 = "bonjour 123456";
    final String mInvalidBonjourService3 = "bonjour invalid_hex 123456";
    final String mInvalidBonjourService4 = "bonjour 123456 invalid_hex";
    final String mValidUpnpService = "upnp 10 serviceName";
    final int mValidUpnpServiceVersion = 16;
    final String mValidUpnpServiceName = "serviceName";
    final String mValidBonjourService = "bonjour 30313233 34353637";
    final byte[] mValidBonjourServiceRequest = {'0', '1', '2', '3'};
    final byte[] mValidBonjourServiceResponse = {'4', '5', '6', '7'};

    // variables for groupAdd with config
    final String mNetworkName = "DIRECT-xy-Hello";
    final String mPassphrase = "12345678";
    final int mGroupOwnerBand = WifiP2pConfig.GROUP_OWNER_BAND_5GHZ;
    final boolean mIsPersistent = false;

    private class SupplicantP2pIfaceHalSpy extends SupplicantP2pIfaceHalAidlImpl {
        SupplicantP2pIfaceHalSpy() {
            super(mWifiMonitor, mWifiInjector);
        }

        @Override
        protected ISupplicant getSupplicantMockable() {
            return mISupplicantMock;
        }

        @Override
        protected IBinder getServiceBinderMockable() {
            return mServiceBinderMock;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new SupplicantP2pIfaceHalSpy();
        setCachedServiceVersion(mServiceVersion);
    }

    /**
     * Sunny day scenario for SupplicantP2pIfaceHal initialization.
     * Asserts successful initialization.
     */
    @Test
    public void testInitialize_success() throws Exception {
        executeAndValidateInitializationSequence(false, false);
    }

    /**
     * Tests the initialization flow when a RemoteException is thrown by addP2pInterface.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_remoteExceptionFailure() throws Exception {
        executeAndValidateInitializationSequence(true, false);
    }

    /**
     * Tests the initialization flow when a null interface is returned by addP2pInterface.
     * Ensures initialization fails.
     */
    @Test
    public void testInitialize_nullInterfaceFailure() throws Exception {
        executeAndValidateInitializationSequence(false, true);
    }

    /**
     * Ensures that we reject the addition of a second iface.
     */
    @Test
    public void testDuplicateSetupIface_Fails() throws Exception {
        executeAndValidateInitializationSequence(false, false);

        // Trying setting up the p2p interface again & ensure it fails.
        assertFalse(mDut.setupIface(mIfaceName));
        verifyNoMoreInteractions(mISupplicantMock);
    }

    /**
     * Sunny day scenario for SupplicantStaIfaceHal teardown.
     * Asserts successful teardown.
     */
    @Test
    public void testTeardown_success() throws Exception {
        executeAndValidateInitializationSequence(false, false);

        doNothing().when(mISupplicantMock).removeInterface(any(IfaceInfo.class));
        assertTrue(mDut.teardownIface(mIfaceName));
        verify(mISupplicantMock).removeInterface(any(IfaceInfo.class));
    }

    /**
     * Ensures that we reject removal of a non-existent iface.
     */
    @Test
    public void testInvalidTeardownInterface_Fails() throws Exception {
        assertFalse(mDut.teardownIface(mIfaceName));
        verifyNoMoreInteractions(mISupplicantMock);
    }

    /**
     * Verify misordered supplicant death case.
     */
    @Test
    public void testMisorderedSupplicantDeathHandling() throws Exception {
        doAnswer(new AnswerWithArguments() {
            public void answer(IBinder.DeathRecipient cb, int flags) throws RemoteException {
                mDeathRecipient = cb;
            }
        }).when(mServiceBinderMock).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        executeAndValidateInitializationSequence(false, false);
        mDeathRecipient.binderDied();
        assertFalse(mDut.teardownIface(mIfaceName));
    }

    /**
     * Sunny day scenario for getName()
     */
    @Test
    public void testGetName_success() throws Exception {
        doReturn(mIfaceName).when(mISupplicantP2pIfaceMock).getName();

        // Default value when service is not initialized.
        assertNull(mDut.getName());
        executeAndValidateInitializationSequence(false, false);
        assertEquals(mIfaceName, mDut.getName());
    }

    /**
     * Verify that getName returns null, if HAL call did not succeed
     */
    @Test
    public void testGetName_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).getName();
        assertNull(mDut.getName());
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that getName disconnects and returns null, if HAL throws a remote exception.
     */
    @Test
    public void testGetName_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).getName();
        assertNull(mDut.getName());
        // Check that service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for find(int)
     */
    @Test
    public void testFind_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).find(anyInt());
        // Default value when service is not yet initialized.
        assertFalse(mDut.find(1));

        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.find(1));
        verify(mISupplicantP2pIfaceMock).find(eq(1));
        assertFalse(mDut.find(-1));
        verify(mISupplicantP2pIfaceMock, never()).find(eq(-1));
    }

    /**
     * Verify that find(int) returns false, if HAL call did not succeed.
     */
    @Test
    public void testFind_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).find(anyInt());
        assertFalse(mDut.find(1));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for find with scan type, {@link WifiP2pManager#WIFI_P2P_SCAN_FULL}.
     */
    @Test
    public void testFindFullScan_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).find(anyInt());
        // Default value when service is not yet initialized.
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_FULL,
                              WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, 1));

        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_FULL,
                             WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, 1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_FULL,
                              WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, -1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_FULL, 2412, -1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_FULL, -1, 1));
    }

    /**
     * Verify that find with scan type, {@link WifiP2pManager#WIFI_P2P_SCAN_FULL}, returns false,
     * if HAL call did not succeed.
     */
    @Test
    public void testFindFullScan_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).find(anyInt());
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_FULL,
                              WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, 1));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for findOnSocialChannels()
     */
    @Test
    public void testFindSocialOnly_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).findOnSocialChannels(anyInt());
        // Default value when service is not yet initialized.
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SOCIAL,
                              WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, 1));

        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SOCIAL,
                             WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, 1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SOCIAL,
                              WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, -1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SOCIAL, 2412, -1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SOCIAL, -1, 1));
    }

    /**
     * Verify that findOnSocialChannels() returns false, if HAL call did not succeed.
     */
    @Test
    public void testFindSocialOnly_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).findOnSocialChannels(anyInt());
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SOCIAL,
                              WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, 1));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for findOnSpecificFrequency()
     */
    @Test
    public void testFindSpecificFrequency_success() throws Exception {
        int freq = 2412;
        doNothing().when(mISupplicantP2pIfaceMock).findOnSpecificFrequency(anyInt(), anyInt());
        // Default value when service is not yet initialized.
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ, freq, 1));

        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ, freq, 1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ, freq, -1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ, -1, 1));
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ,
                              WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, 1));
    }

    /**
     * Verify that find returns false, if HAL call did not succeed.
     */
    @Test
    public void testFindSpecificFrequency_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).findOnSpecificFrequency(anyInt(), anyInt());
        assertFalse(mDut.find(WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ, 2412, 1));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for stopFind()
     */
    @Test
    public void testStopFind_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).stopFind();
        // Default value when service is not yet initialized.
        assertFalse(mDut.stopFind());
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.stopFind());
    }

    /**
     * Verify that stopFind returns false, if HAL call did not succeed.
     */
    @Test
    public void testStopFind_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).stopFind();
        assertFalse(mDut.stopFind());
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that stopFind disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testStopFind_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).stopFind();
        assertFalse(mDut.stopFind());
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for flush()
     */
    @Test
    public void testFlush_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).flush();
        // Default value when service is not yet initialized.
        assertFalse(mDut.flush());
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.flush());
    }

    /**
     * Verify that flush returns false, if HAL call did not succeed.
     */
    @Test
    public void testFlush_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).flush();
        assertFalse(mDut.flush());
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that flush disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testFlush_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).flush();
        assertFalse(mDut.flush());
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for serviceFlush()
     */
    @Test
    public void testServiceFlush_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).flushServices();
        // Default value when service is not initialized.
        assertFalse(mDut.serviceFlush());
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.serviceFlush());
    }

    /**
     * Verify that serviceFlush returns false, if HAL call did not succeed.
     */
    @Test
    public void testServiceFlush_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).flushServices();
        assertFalse(mDut.serviceFlush());
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that serviceFlush disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testServiceFlush_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).flushServices();
        assertFalse(mDut.serviceFlush());
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for setPowerSave()
     */
    @Test
    public void testSetPowerSave_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).setPowerSave(eq(mIfaceName), anyBoolean());
        // Default value when service is not initialized.
        assertFalse(mDut.setPowerSave(mIfaceName, true));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setPowerSave(mIfaceName, true));
    }

    /**
     * Verify that setPowerSave returns false, if HAL call did not succeed.
     */
    @Test
    public void testSetPowerSave_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).setPowerSave(eq(mIfaceName), anyBoolean());
        assertFalse(mDut.setPowerSave(mIfaceName, true));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that setPowerSave disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testSetPowerSave_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).setPowerSave(eq(mIfaceName), anyBoolean());
        assertFalse(mDut.setPowerSave(mIfaceName, true));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for setGroupIdle()
     */
    @Test
    public void testSetGroupIdle_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).setGroupIdle(eq(mIfaceName), anyInt());
        // Default value when service is not initialized.
        assertFalse(mDut.setGroupIdle(mIfaceName, 1));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setGroupIdle(mIfaceName, 1));
        assertFalse(mDut.setGroupIdle(mIfaceName, -1));
    }

    /**
     * Verify that setGroupIdle returns false, if HAL call did not succeed.
     */
    @Test
    public void testSetGroupIdle_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).setGroupIdle(eq(mIfaceName), anyInt());
        assertFalse(mDut.setGroupIdle(mIfaceName, 1));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that setGroupIdle disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testSetGroupIdle_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).setGroupIdle(eq(mIfaceName), anyInt());
        assertFalse(mDut.setGroupIdle(mIfaceName, 1));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for setSsidPostfix()
     */
    @Test
    public void testSetSsidPostfix_success() throws Exception {
        String ssid = "SSID POSTFIX";
        doNothing().when(mISupplicantP2pIfaceMock).setSsidPostfix(
                eq(NativeUtil.byteArrayFromArrayList(
                        NativeUtil.decodeSsid("\"" + ssid + "\""))));
        // Default value when service is not initialized.
        assertFalse(mDut.setSsidPostfix(ssid));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setSsidPostfix(ssid));
        assertFalse(mDut.setSsidPostfix(null));
    }

    /**
     * Verify that setSsidPostfix returns false, if HAL call did not succeed.
     */
    @Test
    public void testSetSsidPostfix_failure() throws Exception {
        String ssid = "SSID POSTFIX";
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).setSsidPostfix(
                        eq(NativeUtil.byteArrayFromArrayList(
                                NativeUtil.decodeSsid("\"" + ssid + "\""))));
        assertFalse(mDut.setSsidPostfix(ssid));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that setSsidPostfix disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testSetSsidPostfix_exception() throws Exception {
        String ssid = "SSID POSTFIX";
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).setSsidPostfix(
                        eq(NativeUtil.byteArrayFromArrayList(
                                NativeUtil.decodeSsid("\"" + ssid + "\""))));
        assertFalse(mDut.setSsidPostfix(ssid));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for connect() using the HAL method for AIDL <= 2.
     */
    @Test
    public void testConnect_V2_success() throws Exception {
        final String configPin = "12345";
        final HashSet<Integer> methods = new HashSet<>();
        setCachedServiceVersion(2);

        doAnswer(new AnswerWithArguments() {
            public String answer(byte[] peer, int method, String pin, boolean joinExisting,
                    boolean persistent, int goIntent) throws RemoteException {
                methods.add(method);

                if (method == WpsProvisionMethod.DISPLAY && TextUtils.isEmpty(pin)) {
                    // Return the configPin for DISPLAY method if the pin was not provided.
                    return configPin;
                } else {
                    if (method != WpsProvisionMethod.PBC) {
                        // PIN is only required for PIN methods.
                        assertEquals(pin, configPin);
                    }
                    // For all the other cases, there is no generated pin.
                    return "";
                }
            }
        }).when(mISupplicantP2pIfaceMock).connect(eq(mPeerMacAddressBytes), anyInt(),
                anyString(), anyBoolean(), anyBoolean(), anyInt());

        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.DISPLAY, "");

        // Default value when service is not initialized.
        assertNull(mDut.connect(config, false));

        executeAndValidateInitializationSequence(false, false);

        assertEquals(configPin, mDut.connect(config, false));
        assertTrue(methods.contains(WpsProvisionMethod.DISPLAY));
        methods.clear();

        config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.DISPLAY, configPin);
        assertTrue(mDut.connect(config, false).isEmpty());
        assertTrue(methods.contains(WpsProvisionMethod.DISPLAY));
        methods.clear();

        config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.PBC, "");
        assertTrue(mDut.connect(config, false).isEmpty());
        assertTrue(methods.contains(WpsProvisionMethod.PBC));
        methods.clear();

        config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.KEYPAD, configPin);
        assertTrue(mDut.connect(config, false).isEmpty());
        assertTrue(methods.contains(WpsProvisionMethod.KEYPAD));
    }

    /**
     * Sunny day scenario for connect() using the HAL method for AIDL >= 3.
     */
    @Test
    public void testConnect_V3_success() throws Exception {
        final String configPin = "12345";
        final HashSet<Integer> methods = new HashSet<>();
        setCachedServiceVersion(3);

        doAnswer(new AnswerWithArguments() {
            public String answer(P2pConnectInfo info) throws RemoteException {
                String pin = info.preSelectedPin;
                int method = info.provisionMethod;
                methods.add(method);

                if (method == WpsProvisionMethod.DISPLAY && TextUtils.isEmpty(pin)) {
                    // Return the configPin for DISPLAY method if the pin was not provided.
                    return configPin;
                } else {
                    if (method != WpsProvisionMethod.PBC) {
                        // PIN is only required for PIN methods.
                        assertEquals(pin, configPin);
                    }
                    // For all the other cases, there is no generated pin.
                    return "";
                }
            }
        }).when(mISupplicantP2pIfaceMock).connectWithParams(any(P2pConnectInfo.class));

        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.DISPLAY, "");

        // Default value when service is not initialized.
        assertNull(mDut.connect(config, false));

        executeAndValidateInitializationSequence(false, false);

        assertEquals(configPin, mDut.connect(config, false));
        assertTrue(methods.contains(WpsProvisionMethod.DISPLAY));
        methods.clear();

        config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.DISPLAY, configPin);
        assertTrue(mDut.connect(config, false).isEmpty());
        assertTrue(methods.contains(WpsProvisionMethod.DISPLAY));
        methods.clear();

        config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.PBC, "");
        assertTrue(mDut.connect(config, false).isEmpty());
        assertTrue(methods.contains(WpsProvisionMethod.PBC));
        methods.clear();

        config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.KEYPAD, configPin);
        assertTrue(mDut.connect(config, false).isEmpty());
        assertTrue(methods.contains(WpsProvisionMethod.KEYPAD));
    }

    /**
     * Test connect with invalid arguments.
     */
    @Test
    public void testConnect_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doAnswer(new AnswerWithArguments() {
            public String answer(byte[] peer, int method, String pin, boolean joinExisting,
                    boolean persistent, int goIntent) throws RemoteException {
                return pin;
            }
        }).when(mISupplicantP2pIfaceMock).connect(any(byte[].class), anyInt(), anyString(),
                anyBoolean(), anyBoolean(), anyInt());

        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.DISPLAY, "");

        // Unsupported.
        config.wps.setup = -1;
        assertNull(mDut.connect(config, false));

        // Invalid peer address.
        config.wps.setup = WpsInfo.DISPLAY;
        for (String address : mInvalidMacAddresses) {
            config.deviceAddress = address;
            assertNull(mDut.connect(config, false));
        }

        // Null pin is not valid.
        config.wps.setup = WpsInfo.DISPLAY;
        config.wps.pin = null;
        assertNull(mDut.connect(config, false));

        // Pin should be empty for PBC.
        config.wps.setup = WpsInfo.PBC;
        config.wps.pin = "03455323";
        assertNull(mDut.connect(config, false));
    }

    /**
     * Verify that connect returns null, if HAL call did not succeed.
     */
    @Test
    public void testConnect_failure() throws Exception {
        final String configPin = "12345";
        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress,
                WpsInfo.DISPLAY, configPin);

        setCachedServiceVersion(2);
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).connect(eq(mPeerMacAddressBytes), anyInt(),
                        anyString(), anyBoolean(), anyBoolean(), anyInt());

        assertNull(mDut.connect(config, false));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that connect disconnects and returns null, if HAL throws a remote exception.
     */
    @Test
    public void testConnect_exception() throws Exception {
        final String configPin = "12345";
        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress,
                WpsInfo.DISPLAY, configPin);

        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).connect(eq(mPeerMacAddressBytes), anyInt(),
                        anyString(), anyBoolean(), anyBoolean(), anyInt());

        assertNull(mDut.connect(config, false));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for cancelConnect()
     */
    @Test
    public void testCancelConnect_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).cancelConnect();
        // Default value when service is not initialized.
        assertFalse(mDut.cancelConnect());
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.cancelConnect());
    }

    /**
     * Verify that cancelConnect returns false, if HAL call did not succeed.
     */
    @Test
    public void testCancelConnect_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).cancelConnect();
        assertFalse(mDut.cancelConnect());
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that cancelConnect disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testCancelConnect_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).cancelConnect();
        assertFalse(mDut.cancelConnect());
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for provisionDiscovery()
     */
    @Test
    public void testProvisionDiscovery_success() throws Exception {
        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.PBC, "");

        doNothing().when(mISupplicantP2pIfaceMock)
                .provisionDiscovery(eq(mPeerMacAddressBytes), anyInt());
        // Default value when service is not initialized.
        assertFalse(mDut.provisionDiscovery(config));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.provisionDiscovery(config));
    }

    /**
     * Test provisionDiscovery with invalid arguments.
     */
    @Test
    public void testProvisionDiscovery_invalidArguments() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock)
                .provisionDiscovery(eq(mPeerMacAddressBytes), anyInt());
        executeAndValidateInitializationSequence(false, false);

        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.PBC, "");

        // Unsupported method.
        config.wps.setup = -1;
        assertFalse(mDut.provisionDiscovery(config));

        config.wps.setup = WpsInfo.PBC;
        for (String address : mInvalidMacAddresses) {
            config.deviceAddress = address;
            assertFalse(mDut.provisionDiscovery(config));
        }
    }

    /**
     * Verify that provisionDiscovery returns false, if HAL call did not succeed.
     */
    @Test
    public void testProvisionDiscovery_failure() throws Exception {
        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.PBC, "");

        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock)
                .provisionDiscovery(eq(mPeerMacAddressBytes), anyInt());
        assertFalse(mDut.provisionDiscovery(config));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that provisionDiscovery disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testProvisionDiscovery_exception() throws Exception {
        WifiP2pConfig config = createPlaceholderP2pConfig(mPeerMacAddress, WpsInfo.PBC, "");

        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock)
                .provisionDiscovery(eq(mPeerMacAddressBytes), anyInt());
        assertFalse(mDut.provisionDiscovery(config));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for invite()
     */
    @Test
    public void testInvite_success() throws Exception {
        WifiP2pGroup group = createPlaceholderP2pGroup();

        doNothing().when(mISupplicantP2pIfaceMock)
                .invite(eq(mIfaceName), eq(mGroupOwnerMacAddressBytes), eq(mPeerMacAddressBytes));
        // Default value when service is not initialized.
        assertFalse(mDut.invite(group, mPeerMacAddress));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.invite(group, mPeerMacAddress));
    }

    /**
     * Invite with invalid arguments.
     */
    @Test
    public void testInvite_invalidArguments() throws Exception {
        WifiP2pGroup group = createPlaceholderP2pGroup();

        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock)
                .invite(anyString(), any(byte[].class), any(byte[].class));

        for (String address : mInvalidMacAddresses) {
            assertFalse(mDut.invite(group, address));
        }

        for (String address : mInvalidMacAddresses) {
            group.getOwner().deviceAddress = address;
            assertFalse(mDut.invite(group, mPeerMacAddress));
        }

        group.setOwner(null);
        assertFalse(mDut.invite(group, mPeerMacAddress));
        assertFalse(mDut.invite(null, mPeerMacAddress));
    }

    /**
     * Verify that invite returns false, if HAL call did not succeed.
     */
    @Test
    public void testInvite_failure() throws Exception {
        WifiP2pGroup group = createPlaceholderP2pGroup();

        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock)
                .invite(anyString(), any(byte[].class), any(byte[].class));
        assertFalse(mDut.invite(group, mPeerMacAddress));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that invite disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testInvite_exception() throws Exception {
        WifiP2pGroup group = createPlaceholderP2pGroup();

        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .invite(anyString(), any(byte[].class), any(byte[].class));
        assertFalse(mDut.invite(group, mPeerMacAddress));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for reject()
     */
    @Test
    public void testReject_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).reject(eq(mPeerMacAddressBytes));
        // Default value when service is not initialized.
        assertFalse(mDut.reject(mPeerMacAddress));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.reject(mPeerMacAddress));
    }

    /**
     * Reject with invalid arguments.
     */
    @Test
    public void testReject_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).reject(eq(mPeerMacAddressBytes));
        for (String address : mInvalidMacAddresses) {
            assertFalse(mDut.reject(address));
        }
    }

    /**
     * Verify that reject returns false, if HAL call did not succeed.
     */
    @Test
    public void testReject_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).reject(any(byte[].class));
        assertFalse(mDut.reject(mPeerMacAddress));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that reject disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testReject_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).reject(any(byte[].class));
        assertFalse(mDut.reject(mPeerMacAddress));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for getDeviceAddress()
     */
    @Test
    public void testGetDeviceAddress_success() throws Exception {
        doReturn(mPeerMacAddressBytes).when(mISupplicantP2pIfaceMock).getDeviceAddress();

        // Default value when service is not initialized.
        assertNull(mDut.getDeviceAddress());
        executeAndValidateInitializationSequence(false, false);
        assertEquals(mPeerMacAddress, mDut.getDeviceAddress());
    }

    /**
     * Test getDeviceAddress() when invalid mac address is being reported.
     */
    @Test
    public void testGetDeviceAddress_invalidResult() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        HashSet<byte[]> addresses = new HashSet<byte[]>(Arrays.asList(
                mInvalidMacAddressBytes1, mInvalidMacAddressBytes2,
                mInvalidMacAddressBytes3, mInvalidMacAddressBytes4));

        doAnswer(new AnswerWithArguments() {
            public byte[] answer() {
                byte[] address = addresses.iterator().next();
                addresses.remove(address);
                return address;
            }
        }).when(mISupplicantP2pIfaceMock).getDeviceAddress();

        // Default value when service is not initialized.
        while (!addresses.isEmpty()) {
            assertNull(mDut.getDeviceAddress());
        }
    }

    /**
     * Verify that getDeviceAddress returns false, if HAL call did not succeed.
     */
    @Test
    public void testGetDeviceAddress_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).getDeviceAddress();

        assertNull(mDut.getDeviceAddress());
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that getDeviceAddress disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testGetDeviceAddress_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).getDeviceAddress();

        assertNull(mDut.getDeviceAddress());
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for getSsid()
     */
    @Test
    public void testGetSsid_success() throws Exception {
        doReturn(mSsidBytes).when(mISupplicantP2pIfaceMock).getSsid(eq(mPeerMacAddressBytes));

        // Default value when service is not initialized.
        assertNull(mDut.getSsid(mPeerMacAddress));
        executeAndValidateInitializationSequence(false, false);
        assertEquals(NativeUtil.removeEnclosingQuotes(mSsid), mDut.getSsid(mPeerMacAddress));
    }

    /**
     * Test getSsid() with invalid arguments.
     */
    @Test
    public void testGetSsid_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doReturn(mSsidBytes).when(mISupplicantP2pIfaceMock).getSsid(any(byte[].class));
        for (String address : mInvalidMacAddresses) {
            assertNull(mDut.getSsid(address));
        }
    }


    /**
     * Test getSsid() when the HAL returns a null response.
     */
    @Test
    public void testGetSsid_nullResult() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doReturn(null).when(mISupplicantP2pIfaceMock).getSsid(any(byte[].class));
        assertNull(mDut.getSsid(mPeerMacAddress));
    }

    /**
     * Verify that getSsid returns false, if HAL call did not succeed.
     */
    @Test
    public void testGetSsid_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).getSsid(any(byte[].class));
        assertNull(mDut.getSsid(mPeerMacAddress));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that getSsid disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testGetSsid_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).getSsid(any(byte[].class));
        assertNull(mDut.getSsid(mPeerMacAddress));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for reinvoke()
     */
    @Test
    public void testReinvoke_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).reinvoke(anyInt(), eq(mPeerMacAddressBytes));
        // Default value when service is not initialized.
        assertFalse(mDut.reinvoke(0, mPeerMacAddress));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.reinvoke(0, mPeerMacAddress));
    }

    /**
     * Call reinvoke with invalid arguments.
     */
    @Test
    public void testReinvoke_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).reinvoke(anyInt(), eq(mPeerMacAddressBytes));
        for (String address : mInvalidMacAddresses) {
            assertFalse(mDut.reinvoke(0, address));
        }
    }

    /**
     * Verify that reinvoke returns false, if HAL call did not succeed.
     */
    @Test
    public void testReinvoke_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).reinvoke(anyInt(), any(byte[].class));
        assertFalse(mDut.reinvoke(0, mPeerMacAddress));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that reinvoke disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testReinvoke_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .reinvoke(anyInt(), any(byte[].class));
        assertFalse(mDut.reinvoke(0, mPeerMacAddress));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for groupAdd()
     */
    @Test
    public void testGroupAdd_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).addGroup(eq(true), eq(3));
        // Default value when service is not initialized.
        assertFalse(mDut.groupAdd(3, true));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.groupAdd(3, true));
    }

    /**
     * Verify that groupAdd returns false, if HAL call did not succeed.
     */
    @Test
    public void testGroupAdd_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).addGroup(anyBoolean(), anyInt());
        assertFalse(mDut.groupAdd(0, true));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that groupAdd disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testGroupAdd_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .addGroup(anyBoolean(), anyInt());
        assertFalse(mDut.groupAdd(0, true));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for groupAdd() with config
     */
    @Test
    public void testGroupAddWithConfigSuccess() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).addGroupWithConfig(
                eq(NativeUtil.byteArrayFromArrayList(
                        NativeUtil.decodeSsid("\"" + mNetworkName + "\""))),
                eq(mPassphrase),
                eq(mIsPersistent),
                eq(mGroupOwnerBand),
                eq(mPeerMacAddressBytes),
                anyBoolean());
        // Default value when service is not initialized.
        assertFalse(mDut.groupAdd(mNetworkName, mPassphrase, mIsPersistent,
                mGroupOwnerBand, mPeerMacAddress, true));
        verify(mISupplicantP2pIfaceMock, never()).addGroupWithConfig(
                any(byte[].class), anyString(),
                anyBoolean(), anyInt(),
                any(byte[].class), anyBoolean());

        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.groupAdd(mNetworkName, mPassphrase, mIsPersistent,
                mGroupOwnerBand, mPeerMacAddress, true));
        verify(mISupplicantP2pIfaceMock).addGroupWithConfig(
                eq(NativeUtil.byteArrayFromArrayList(
                        NativeUtil.decodeSsid("\"" + mNetworkName + "\""))),
                eq(mPassphrase),
                eq(mIsPersistent),
                eq(mGroupOwnerBand),
                eq(mPeerMacAddressBytes),
                eq(true));
    }

    /**
     * Verify that groupAdd with config returns false, if HAL call did not succeed.
     */
    @Test
    public void testGroupAddWithConfigFailure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).addGroupWithConfig(
                        eq(NativeUtil.byteArrayFromArrayList(
                                NativeUtil.decodeSsid("\"" + mNetworkName + "\""))),
                        eq(mPassphrase),
                        eq(mIsPersistent),
                        eq(mGroupOwnerBand),
                        eq(mPeerMacAddressBytes),
                        anyBoolean());
        assertFalse(mDut.groupAdd(mNetworkName, mPassphrase, mIsPersistent,
                mGroupOwnerBand, mPeerMacAddress, true));
        verify(mISupplicantP2pIfaceMock).addGroupWithConfig(
                eq(NativeUtil.byteArrayFromArrayList(
                        NativeUtil.decodeSsid("\"" + mNetworkName + "\""))),
                eq(mPassphrase),
                eq(mIsPersistent),
                eq(mGroupOwnerBand),
                eq(mPeerMacAddressBytes),
                eq(true));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that groupAdd with config disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testGroupAddWithConfigException() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).addGroupWithConfig(
                        eq(NativeUtil.byteArrayFromArrayList(
                                NativeUtil.decodeSsid("\"" + mNetworkName + "\""))),
                        eq(mPassphrase),
                        eq(mIsPersistent),
                        eq(mGroupOwnerBand),
                        eq(mPeerMacAddressBytes),
                        anyBoolean());
        assertFalse(mDut.groupAdd(mNetworkName, mPassphrase, mIsPersistent,
                mGroupOwnerBand, mPeerMacAddress, true));
        verify(mISupplicantP2pIfaceMock).addGroupWithConfig(
                eq(NativeUtil.byteArrayFromArrayList(
                        NativeUtil.decodeSsid("\"" + mNetworkName + "\""))),
                eq(mPassphrase),
                eq(mIsPersistent),
                eq(mGroupOwnerBand),
                eq(mPeerMacAddressBytes),
                eq(true));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for groupRemove()
     */
    @Test
    public void testGroupRemove_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).removeGroup(eq(mIfaceName));
        // Default value when service is not initialized.
        assertFalse(mDut.groupRemove(mIfaceName));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.groupRemove(mIfaceName));
    }

    /**
     * Verify that groupRemove returns false, if HAL call did not succeed.
     */
    @Test
    public void testGroupRemove_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).removeGroup(anyString());
        assertFalse(mDut.groupRemove(mIfaceName));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that groupRemove disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testGroupRemove_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).removeGroup(anyString());
        assertFalse(mDut.groupRemove(mIfaceName));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for getGroupCapability()
     */
    @Test
    public void testGetGroupCapability_success() throws Exception {
        final int caps = 123;
        doReturn(caps).when(mISupplicantP2pIfaceMock).getGroupCapability(eq(mPeerMacAddressBytes));
        // Default value when service is not initialized.
        assertEquals(-1, mDut.getGroupCapability(mPeerMacAddress));
        executeAndValidateInitializationSequence(false, false);
        assertEquals(caps, mDut.getGroupCapability(mPeerMacAddress));
    }

    /**
     * GetGroupCapability with invalid arguments.
     */
    @Test
    public void testGetGroupCapability_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doReturn(0).when(mISupplicantP2pIfaceMock)
                .getGroupCapability(eq(mPeerMacAddressBytes));
        for (String address : mInvalidMacAddresses) {
            assertEquals(-1, mDut.getGroupCapability(address));
        }
    }

    /**
     * Verify that getGroupCapability returns false, if HAL call did not succeed.
     */
    @Test
    public void testGetGroupCapability_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock)
                .getGroupCapability(eq(mPeerMacAddressBytes));
        assertEquals(-1, mDut.getGroupCapability(mPeerMacAddress));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that getGroupCapability disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testGetGroupCapability_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock)
                .getGroupCapability(eq(mPeerMacAddressBytes));
        assertEquals(-1, mDut.getGroupCapability(mPeerMacAddress));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    private void setCachedServiceVersion(int version) {
        when(mWifiSettingsConfigStore
                .get(eq(WifiSettingsConfigStore.SUPPLICANT_HAL_AIDL_SERVICE_VERSION)))
                .thenReturn(version);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mWifiSettingsConfigStore);
    }

    /**
     * Sunny day scenario for configureExtListen()
     */
    @Test
    public void testConfigureExtListen_success() throws Exception {
        // Only accept (123, 456) and (0, 0) as parameters for this test
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .configureExtListen(anyInt(), anyInt());
        doNothing().when(mISupplicantP2pIfaceMock).configureExtListen(eq(123), eq(456));
        doNothing().when(mISupplicantP2pIfaceMock).configureExtListen(eq(0), eq(0));
        setCachedServiceVersion(2);

        // Default value when service is not initialized.
        assertFalse(mDut.configureExtListen(true, 123, 456, null));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.configureExtListen(true, 123, 456, null));
        // Turning listening off should reset intervals to 0s.
        assertTrue(mDut.configureExtListen(false, 999, 999, null));
        // Disable listening.
        assertTrue(mDut.configureExtListen(false, -1, -1, null));
    }

    /**
     * Sunny day scenario for configureExtListenWithParams()
     */
    @Test
    public void testConfigureExtListenWithParams_success() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        setCachedServiceVersion(3); // API requires HAL >= 3

        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(0x00aabbcc, new PersistableBundle()).build();
        List<OuiKeyedData> vendorData = Arrays.asList(vendorDataElement);
        WifiP2pExtListenParams extListenParams =
                new WifiP2pExtListenParams.Builder().setVendorData(vendorData).build();

        // Default value when service is not initialized.
        assertFalse(mDut.configureExtListen(true, 123, 456, extListenParams));

        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.configureExtListen(true, 123, 456, extListenParams));

        // Disable listening.
        assertTrue(mDut.configureExtListen(false, -1, -1, extListenParams));

        // Legacy HAL API should not get called.
        verify(mISupplicantP2pIfaceMock, atLeastOnce())
                .configureExtListenWithParams(any(P2pExtListenInfo.class));
        verify(mISupplicantP2pIfaceMock, never()).configureExtListen(anyInt(), anyInt());
    }

    /**
     * Test configureExtListen with invalid parameters.
     */
    @Test
    public void testConfigureExtListen_invalidArguments() throws Exception {
        setCachedServiceVersion(2);
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).configureExtListen(anyInt(), anyInt());
        assertFalse(mDut.configureExtListen(true, -1, 1, null));
        assertFalse(mDut.configureExtListen(true, 1, -1, null));
    }

    /**
     * Verify that configureExtListen returns false, if HAL call did not succeed.
     */
    @Test
    public void testConfigureExtListen_failure() throws Exception {
        setCachedServiceVersion(2);
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).configureExtListen(anyInt(), anyInt());
        assertFalse(mDut.configureExtListen(true, 1, 1, null));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that configureExtListen disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testConfigureExtListen_exception() throws Exception {
        setCachedServiceVersion(2);
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .configureExtListen(anyInt(), anyInt());
        assertFalse(mDut.configureExtListen(true, 1, 1, null));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for setListenChannel()
     */
    @Test
    public void testSetListenChannel_success() throws Exception {
        int lc = 6;
        doNothing().when(mISupplicantP2pIfaceMock).setListenChannel(eq(lc), anyInt());
        // Default value when service is not initialized.
        assertFalse(mDut.setListenChannel(lc));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setListenChannel(lc));
    }

    /**
     * Test setListenChannel with invalid parameters.
     */
    @Test
    public void testSetListenChannel_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).setListenChannel(anyInt(), anyInt());
        assertFalse(mDut.setListenChannel(4));
    }

    /**
     * Verify that setListenChannel returns false, if HAL call did not succeed.
     */
    @Test
    public void testSetListenChannel_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).setListenChannel(anyInt(), anyInt());
        assertFalse(mDut.setListenChannel(1));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that setListenChannel disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testSetListenChannel_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .setListenChannel(anyInt(), anyInt());
        assertFalse(mDut.setListenChannel(1));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for setOperatingChannel()
     */
    @Test
    public void testSetOperatingChannel_success() throws Exception {
        int oc = 163;
        FreqRange range1 = new FreqRange();
        range1.min = 1000;
        range1.max = 5810;
        FreqRange range2 = new FreqRange();
        range2.min = 5820;
        range2.max = 6000;
        FreqRange[] ranges = {range1, range2};
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();

        doNothing().when(mISupplicantP2pIfaceMock).setDisallowedFrequencies(eq(ranges));
        // Default value when service is not initialized.
        assertFalse(mDut.setOperatingChannel(oc, unsafeChannels));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setOperatingChannel(oc, unsafeChannels));
    }

    /**
     * Test setOperatingChannel with invalid parameters.
     */
    @Test
    public void testSetOperatingChannel_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).setDisallowedFrequencies(any(FreqRange[].class));
        assertFalse(mDut.setOperatingChannel(1, null));
    }

    /**
     * Verify that setOperatingChannel returns false, if HAL call did not succeed.
     */
    @Test
    public void testSetOperatingChannel_failure() throws Exception {
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).setDisallowedFrequencies(any(FreqRange[].class));
        assertFalse(mDut.setOperatingChannel(1, unsafeChannels));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that setOperatingChannel disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testSetOperatingChannel_exception() throws Exception {
        List<CoexUnsafeChannel> unsafeChannels = new ArrayList<>();
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .setDisallowedFrequencies(any(FreqRange[].class));
        assertFalse(mDut.setOperatingChannel(65, unsafeChannels));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for serviceAdd()
     */
    @Test
    public void testServiceAdd_success() throws Exception {
        WifiP2pServiceInfo info = createPlaceholderP2pServiceInfo(
                mValidUpnpService, mValidBonjourService);
        final HashSet<String> services = new HashSet<String>();

        doAnswer(new AnswerWithArguments() {
            public void answer(int version, String name) {
                services.add("upnp");
                assertEquals(mValidUpnpServiceVersion, version);
                assertEquals(mValidUpnpServiceName, name);
            }
        }).when(mISupplicantP2pIfaceMock).addUpnpService(anyInt(), anyString());

        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] request, byte[] response) {
                services.add("bonjour");
                assertArrayEquals(mValidBonjourServiceRequest, request);
                assertArrayEquals(mValidBonjourServiceResponse, response);
            }
        }).when(mISupplicantP2pIfaceMock).addBonjourService(any(byte[].class), any(byte[].class));

        // Default value when service is not initialized.
        assertFalse(mDut.serviceAdd(info));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.serviceAdd(info));
        // Confirm that both services have been added.
        assertTrue(services.contains("upnp"));
        assertTrue(services.contains("bonjour"));

        // Empty services should not cause any trouble.
        assertTrue(mDut.serviceAdd(createPlaceholderP2pServiceInfo()));
    }

    /**
     * Test serviceAdd with invalid parameters.
     */
    @Test
    public void testServiceAdd_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).addUpnpService(anyInt(), anyString());
        doNothing().when(mISupplicantP2pIfaceMock)
                .addBonjourService(any(byte[].class), any(byte[].class));

        assertFalse(mDut.serviceAdd(null));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidService1)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidService2)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidUpnpService1)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidUpnpService2)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidUpnpService3)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidBonjourService1)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidBonjourService2)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidBonjourService3)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mInvalidBonjourService4)));
    }

    /**
     * Verify that serviceAdd returns false, if HAL call did not succeed.
     */
    @Test
    public void testServiceAdd_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).addUpnpService(anyInt(), anyString());
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock)
                        .addBonjourService(any(byte[].class), any(byte[].class));

        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mValidUpnpService)));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mValidBonjourService)));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that serviceAdd disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testServiceAddUpnp_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .addUpnpService(anyInt(), anyString());
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mValidUpnpService)));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Verify that serviceAdd disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testServiceAddBonjour_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).addBonjourService(
                any(byte[].class), any(byte[].class));
        assertFalse(mDut.serviceAdd(createPlaceholderP2pServiceInfo(mValidBonjourService)));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for serviceRemove()
     */
    @Test
    public void testServiceRemove_success() throws Exception {
        WifiP2pServiceInfo info = createPlaceholderP2pServiceInfo(
                mValidUpnpService, mValidBonjourService);
        final HashSet<String> services = new HashSet<String>();

        doAnswer(new AnswerWithArguments() {
            public void answer(int version, String name) {
                services.add("upnp");
                assertEquals(mValidUpnpServiceVersion, version);
                assertEquals(mValidUpnpServiceName, name);
            }
        }).when(mISupplicantP2pIfaceMock).removeUpnpService(anyInt(), anyString());

        doAnswer(new AnswerWithArguments() {
            public void answer(byte[] request) {
                services.add("bonjour");
                assertArrayEquals(mValidBonjourServiceRequest, request);
            }
        }).when(mISupplicantP2pIfaceMock).removeBonjourService(any(byte[].class));

        // Default value when service is not initialized.
        assertFalse(mDut.serviceRemove(info));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.serviceRemove(info));
        // Confirm that both services have been removed.
        assertTrue(services.contains("upnp"));
        assertTrue(services.contains("bonjour"));

        // Empty services should cause no trouble.
        assertTrue(mDut.serviceRemove(createPlaceholderP2pServiceInfo()));
    }

    /**
     * Test serviceRemove with invalid parameters.
     */
    @Test
    public void testServiceRemove_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).removeUpnpService(anyInt(), anyString());
        doNothing().when(mISupplicantP2pIfaceMock).removeBonjourService(any(byte[].class));

        assertFalse(mDut.serviceRemove(null));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidService1)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidService2)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidUpnpService1)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidUpnpService2)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidUpnpService3)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidBonjourService1)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidBonjourService2)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidBonjourService3)));
        // Response parameter is ignored by serviceRemove call, hence the following would pass.
        // The production code would need to parse otherwise redundant parameter to fail on this
        // one.
        // assertFalse(
        //         mDut.serviceRemove(createPlaceholderP2pServiceInfo(mInvalidBonjourService4)));
    }

    /**
     * Verify that serviceRemove returns false, if HAL call did not succeed.
     */
    @Test
    public void testServiceRemove_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);

        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).removeUpnpService(anyInt(), anyString());
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).removeBonjourService(any(byte[].class));

        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mValidUpnpService)));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mValidBonjourService)));

        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that serviceRemove disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testServiceRemoveUpnp_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .removeUpnpService(anyInt(), anyString());
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mValidUpnpService)));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Verify that serviceRemove disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testServiceRemoveBonjour_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .removeBonjourService(any(byte[].class));
        assertFalse(mDut.serviceRemove(createPlaceholderP2pServiceInfo(mValidBonjourService)));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for requestServiceDiscovery()
     */
    @Test
    public void testRequestServiceDiscovery_success() throws Exception {
        final long caps = 1234;
        doReturn(caps).when(mISupplicantP2pIfaceMock)
                .requestServiceDiscovery(eq(mPeerMacAddressBytes), eq(mValidBonjourServiceRequest));

        // Default value when service is not initialized.
        assertNull(mDut.requestServiceDiscovery(mPeerMacAddress, mValidServiceRequestString));

        executeAndValidateInitializationSequence(false, false);
        assertEquals(Long.toString(caps), mDut.requestServiceDiscovery(
                mPeerMacAddress, mValidServiceRequestString));
    }

    /**
     * RequestServiceDiscovery with invalid arguments.
     */
    @Test
    public void testRequestServiceDiscovery_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doReturn(0L).when(mISupplicantP2pIfaceMock)
                .requestServiceDiscovery(any(byte[].class), any(byte[].class));

        for (String address : mInvalidMacAddresses) {
            assertNull(mDut.requestServiceDiscovery(
                    address, mValidServiceRequestString));
        }
        assertNull(mDut.requestServiceDiscovery(mPeerMacAddress, null));
        assertNull(mDut.requestServiceDiscovery(mPeerMacAddress, mInvalidServiceRequestString));
    }

    /**
     * Verify that requestServiceDiscovery returns false, if HAL call did not succeed.
     */
    @Test
    public void testRequestServiceDiscovery_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock)
                .requestServiceDiscovery(any(byte[].class), any(byte[].class));
        assertNull(mDut.requestServiceDiscovery(mPeerMacAddress, mValidServiceRequestString));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that requestServiceDiscovery disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testRequestServiceDiscovery_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .requestServiceDiscovery(any(byte[].class), any(byte[].class));
        assertNull(mDut.requestServiceDiscovery(mPeerMacAddress, mValidServiceRequestString));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    // Test constants used in cancelServiceDiscovery tests
    static final long SERVICE_IDENTIFIER_LONG = 521918410304L;
    static final String SERVICE_IDENTIFIER_STR = Long.toString(SERVICE_IDENTIFIER_LONG);

    /**
     * Sunny day scenario for cancelServiceDiscovery()
     */
    @Test
    public void testCancelServiceDiscovery_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).cancelServiceDiscovery(SERVICE_IDENTIFIER_LONG);
        // Default value when service is not initialized.
        assertFalse(mDut.cancelServiceDiscovery(SERVICE_IDENTIFIER_STR));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.cancelServiceDiscovery(SERVICE_IDENTIFIER_STR));
    }

    /**
     * Test cancelServiceDiscovery with invalid parameters.
     */
    @Test
    public void testCancelServiceDiscovery_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).cancelServiceDiscovery(SERVICE_IDENTIFIER_LONG);
        assertFalse(mDut.cancelServiceDiscovery(null));
        assertFalse(mDut.cancelServiceDiscovery("not a number"));
    }

    /**
     * Verify that cancelServiceDiscovery returns false, if HAL call did not succeed.
     */
    @Test
    public void testCancelServiceDiscovery_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).cancelServiceDiscovery(anyLong());
        assertFalse(mDut.cancelServiceDiscovery(SERVICE_IDENTIFIER_STR));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that cancelServiceDiscovery disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testCancelServiceDiscovery_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .cancelServiceDiscovery(anyLong());
        assertFalse(mDut.cancelServiceDiscovery(SERVICE_IDENTIFIER_STR));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for setMiracastMode()
     */
    @Test
    public void testSetMiracastMode_success() throws Exception {
        HashSet<Byte> modes = new HashSet<Byte>();
        doAnswer(new AnswerWithArguments() {
            public void answer(byte mode) {
                modes.add(mode);
            }
        }).when(mISupplicantP2pIfaceMock).setMiracastMode(anyByte());

        // Default value when service is not initialized.
        assertFalse(mDut.setMiracastMode(WifiP2pManager.MIRACAST_SOURCE));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setMiracastMode(WifiP2pManager.MIRACAST_SOURCE));
        assertTrue(modes.contains(MiracastMode.SOURCE));

        assertTrue(mDut.setMiracastMode(WifiP2pManager.MIRACAST_SINK));
        assertTrue(modes.contains(MiracastMode.SINK));

        // Any invalid number yields disabled miracast mode.
        assertTrue(mDut.setMiracastMode(-1));
        assertTrue(modes.contains(MiracastMode.DISABLED));
    }

    /**
     * Verify that setMiracastMode returns false, if HAL call did not succeed.
     */
    @Test
    public void testSetMiracastMode_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).setMiracastMode(anyByte());
        assertFalse(mDut.setMiracastMode(WifiP2pManager.MIRACAST_SOURCE));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that setMiracastMode disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testSetMiracastMode_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).setMiracastMode(anyByte());
        assertFalse(mDut.setMiracastMode(WifiP2pManager.MIRACAST_SOURCE));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for startWpsPbc()
     */
    @Test
    public void testStartWpsPbc_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock)
                .startWpsPbc(eq(mIfaceName), eq(mPeerMacAddressBytes));
        // Default value when service is not initialized.
        assertFalse(mDut.startWpsPbc(mIfaceName, mPeerMacAddress));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.startWpsPbc(mIfaceName, mPeerMacAddress));
    }

    /**
     * StartWpsPbc with invalid arguments.
     */
    @Test
    public void testStartWpsPbc_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).startWpsPbc(anyString(), any(byte[].class));
        for (String address : mInvalidMacAddresses) {
            assertFalse(mDut.startWpsPbc(mIfaceName, address));
        }
        assertFalse(mDut.startWpsPbc(null, mPeerMacAddress));
    }

    /**
     * Verify that startWpsPbc returns false, if HAL call did not succeed.
     */
    @Test
    public void testStartWpsPbc_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).startWpsPbc(anyString(), any(byte[].class));
        assertFalse(mDut.startWpsPbc(mIfaceName, mPeerMacAddress));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that startWpsPbc disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testStartWpsPbc_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .startWpsPbc(anyString(), any(byte[].class));
        assertFalse(mDut.startWpsPbc(mIfaceName, mPeerMacAddress));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for startWpsPinKeypad()
     */
    @Test
    public void testStartWpsPinKeypad_success() throws Exception {
        String pin = "1234";
        doNothing().when(mISupplicantP2pIfaceMock).startWpsPinKeypad(eq(mIfaceName), eq(pin));
        // Default value when service is not initialized.
        assertFalse(mDut.startWpsPinKeypad(mIfaceName, pin));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.startWpsPinKeypad(mIfaceName, pin));
    }

    /**
     * StartWpsPinKeypad with invalid arguments.
     */
    @Test
    public void testStartWpsPinKeypad_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).startWpsPinKeypad(anyString(), anyString());

        assertFalse(mDut.startWpsPinKeypad(null, "1234"));
        assertFalse(mDut.startWpsPinKeypad(mIfaceName, null));
        // StartWpsPinPinKeypad does not validate that PIN indeed holds an integer encoded in a
        // string. This code would be redundant, as the HAL requires string to be passed.
    }

    /**
     * Verify that startWpsPinKeypad returns false, if HAL call did not succeed.
     */
    @Test
    public void testStartWpsPinKeypad_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).startWpsPinKeypad(anyString(), anyString());
        assertFalse(mDut.startWpsPinKeypad(mIfaceName, "1234"));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that startWpsPinKeypad disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testStartWpsPinKeypad_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .startWpsPinKeypad(anyString(), anyString());
        assertFalse(mDut.startWpsPinKeypad(mIfaceName, "1234"));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for startWpsPinDisplay()
     */
    @Test
    public void testStartWpsPinDisplay_success() throws Exception {
        String pin = "1234";
        doReturn(pin).when(mISupplicantP2pIfaceMock)
                .startWpsPinDisplay(eq(mIfaceName), eq(mPeerMacAddressBytes));

        // Default value when service is not initialized.
        assertNull(mDut.startWpsPinDisplay(mIfaceName, mPeerMacAddress));
        executeAndValidateInitializationSequence(false, false);
        assertEquals("1234", mDut.startWpsPinDisplay(mIfaceName, mPeerMacAddress));
    }

    /**
     * StartWpsPinDisplay with invalid arguments.
     */
    @Test
    public void testStartWpsPinDisplay_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doReturn("1234").when(mISupplicantP2pIfaceMock)
                .startWpsPinDisplay(anyString(), any(byte[].class));
        for (String address : mInvalidMacAddresses) {
            assertNull(mDut.startWpsPinDisplay(mIfaceName, address));
        }
        assertNull(mDut.startWpsPinDisplay(null, mPeerMacAddress));
    }

    /**
     * Verify that startWpsPinDisplay returns false, if HAL call did not succeed.
     */
    @Test
    public void testStartWpsPinDisplay_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).startWpsPinDisplay(anyString(), any(byte[].class));
        assertNull(mDut.startWpsPinDisplay(mIfaceName, mPeerMacAddress));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that startWpsPinDisplay disconnects and returns false,
     * if HAL throws a remote exception.
     */
    @Test
    public void testStartWpsPinDisplay_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException())
                .when(mISupplicantP2pIfaceMock).startWpsPinDisplay(anyString(), any(byte[].class));
        assertNull(mDut.startWpsPinDisplay(mIfaceName, mPeerMacAddress));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for cancelWps()
     */
    @Test
    public void testCancelWps_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).cancelWps(eq(mIfaceName));
        // Default value when service is not initialized.
        assertFalse(mDut.cancelWps(mIfaceName));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.cancelWps(mIfaceName));
    }

    /**
     * CancelWps with invalid arguments.
     */
    @Test
    public void testCancelWps_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).cancelWps(anyString());
        assertFalse(mDut.cancelWps(null));
    }

    /**
     * Verify that cancelWps returns false, if HAL call did not succeed.
     */
    @Test
    public void testCancelWps_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
            .when(mISupplicantP2pIfaceMock).cancelWps(anyString());
        assertFalse(mDut.cancelWps(mIfaceName));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that cancelWps disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testCancelWps_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).cancelWps(anyString());
        assertFalse(mDut.cancelWps(mIfaceName));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for enableWfd()
     */
    @Test
    public void testEnableWfd_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).enableWfd(eq(true));
        // Default value when service is not initialized.
        assertFalse(mDut.enableWfd(true));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.enableWfd(true));
    }

    /**
     * Verify that enableWfd returns false, if HAL call did not succeed.
     */
    @Test
    public void testEnableWfd_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).enableWfd(anyBoolean());
        assertFalse(mDut.enableWfd(true));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that enableWfd disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testEnableWfd_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).enableWfd(anyBoolean());
        assertFalse(mDut.enableWfd(false));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Sunny day scenario for setWfdDeviceInfo()
     */
    @Test
    public void testSetWfdDeviceInfo_success() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).setWfdDeviceInfo(eq(mValidServiceRequestBytes));
        // Default value when service is not initialized.
        assertFalse(mDut.setWfdDeviceInfo(mValidServiceRequestString));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setWfdDeviceInfo(mValidServiceRequestString));
    }

    /**
     * SetWfdDeviceInfo with invalid arguments.
     */
    @Test
    public void testSetWfdDeviceInfo_invalidArguments() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).setWfdDeviceInfo(any(byte[].class));
        assertFalse(mDut.setWfdDeviceInfo(null));
        assertFalse(mDut.setWfdDeviceInfo(mInvalidServiceRequestString));
    }

    /**
     * Verify that setWfdDeviceInfo returns false, if HAL call did not succeed.
     */
    @Test
    public void testSetWfdDeviceInfo_failure() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new ServiceSpecificException(SupplicantStatusCode.FAILURE_UNKNOWN))
                .when(mISupplicantP2pIfaceMock).setWfdDeviceInfo(any(byte[].class));
        assertFalse(mDut.setWfdDeviceInfo(mValidServiceRequestString));
        // Check that service is still alive.
        assertTrue(mDut.isInitializationComplete());
    }

    /**
     * Verify that setWfdDeviceInfo disconnects and returns false, if HAL throws a remote exception.
     */
    @Test
    public void testSetWfdDeviceInfo_exception() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .setWfdDeviceInfo(any(byte[].class));
        assertFalse(mDut.setWfdDeviceInfo(mValidServiceRequestString));
        // Check service is dead.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Verify the loading of group info. All groups returned by listNetworks are added as a
     * persistent group, as long as they are NOT current.
     */
    @Test
    public void testLoadGroups() throws Exception {
        executeAndValidateInitializationSequence(false, false);

        // Class to hold the P2p group info returned from the AIDL interface.
        class P2pGroupInfo {
            public String ssid;
            public byte[] bssid;
            public boolean isGroupOwner;
            public boolean isCurrent;
            P2pGroupInfo(String ssid, byte[] bssid, boolean isGroupOwner, boolean isCurrent) {
                this.ssid = ssid;
                this.bssid = bssid;
                this.isGroupOwner = isGroupOwner;
                this.isCurrent = isCurrent;
            }
        }

        Map<Integer, P2pGroupInfo> groups = new HashMap<>();
        groups.put(0, new P2pGroupInfo(
                "test_34",
                NativeUtil.macAddressToByteArray("56:34:ab:12:12:34"),
                false, false));
        groups.put(1, new P2pGroupInfo(
                "test_1234",
                NativeUtil.macAddressToByteArray("16:ed:ab:12:45:34"),
                true, false));
        groups.put(2, new P2pGroupInfo(
                "test_4545",
                NativeUtil.macAddressToByteArray("32:89:23:56:45:34"),
                true, false));
        groups.put(3, new P2pGroupInfo(
                "iShouldntBeHere",
                NativeUtil.macAddressToByteArray("aa:bb:cc:56:45:34"),
                true, true));

        doAnswer(new AnswerWithArguments() {
            public int[] answer() {
                Set<Integer> keys = groups.keySet();
                int[] networks = new int[keys.size()];
                int index = 0;
                for (Integer e : keys) {
                    networks[index++] = e;
                }
                return networks;
            }
        }).when(mISupplicantP2pIfaceMock).listNetworks();

        doAnswer(new AnswerWithArguments() {
            public ISupplicantP2pNetwork answer(final int networkId) {
                try {
                    doAnswer(new AnswerWithArguments() {
                        public byte[] answer() {
                            return NativeUtil.stringToByteArray(groups.get(networkId).ssid);
                        }
                    }).when(mISupplicantP2pNetworkMock).getSsid();
                    doAnswer(new AnswerWithArguments() {
                        public byte[] answer() {
                            return groups.get(networkId).bssid;
                        }
                    }).when(mISupplicantP2pNetworkMock).getBssid();
                    doAnswer(new AnswerWithArguments() {
                        public boolean answer() {
                            return groups.get(networkId).isCurrent;
                        }
                    }).when(mISupplicantP2pNetworkMock).isCurrent();
                    doAnswer(new AnswerWithArguments() {
                        public boolean answer() {
                            return groups.get(networkId).isGroupOwner;
                        }
                    }).when(mISupplicantP2pNetworkMock).isGroupOwner();
                } catch (RemoteException e) { }
                return mISupplicantP2pNetworkMock;
            }
        }).when(mISupplicantP2pIfaceMock).getNetwork(anyInt());

        WifiP2pGroupList p2pGroups = new WifiP2pGroupList();
        assertTrue(mDut.loadGroups(p2pGroups));

        assertEquals(3, p2pGroups.getGroupList().size());
        for (WifiP2pGroup group : p2pGroups.getGroupList()) {
            int networkId = group.getNetworkId();
            assertEquals(groups.get(networkId).ssid, group.getNetworkName());
            assertEquals(
                    NativeUtil.macAddressFromByteArray(groups.get(networkId).bssid),
                    group.getOwner().deviceAddress);
            assertEquals(groups.get(networkId).isGroupOwner, group.isGroupOwner());
        }
    }

    /**
     * Sunny day scenario for setClientList()
     */
    @Test
    public void testSetClientList() throws Exception {
        int testNetworkId = 5;
        final String client1 = mGroupOwnerMacAddress;
        final String client2 = mPeerMacAddress;

        executeAndValidateInitializationSequence(false, false);
        doAnswer(new AnswerWithArguments() {
            public ISupplicantP2pNetwork answer(int networkId) {
                if (networkId == testNetworkId) {
                    return mISupplicantP2pNetworkMock;
                } else {
                    return null;
                }
            }
        }).when(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        doNothing().when(mISupplicantP2pNetworkMock).setClientList(any(MacAddress[].class));

        String clientList = client1 + " " + client2;
        assertTrue(mDut.setClientList(testNetworkId, clientList));
        verify(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        ArgumentCaptor<MacAddress[]> capturedClients = ArgumentCaptor.forClass(MacAddress[].class);
        verify(mISupplicantP2pNetworkMock).setClientList(capturedClients.capture());

        // Convert these to long to help with comparisons.
        MacAddress[] clients = capturedClients.getValue();
        List<Long> expectedClients = List.of(
                NativeUtil.macAddressToLong(mGroupOwnerMacAddressBytes),
                NativeUtil.macAddressToLong(mPeerMacAddressBytes));
        ArrayList<Long> receivedClients = new ArrayList<Long>();
        for (MacAddress client : clients) {
            receivedClients.add(NativeUtil.macAddressToLong(client.data));
        }
        assertEquals(expectedClients, receivedClients);
    }

    /**
     * Failure scenario for setClientList() when getNetwork returns null.
     */
    @Test
    public void testSetClientListFailureDueToGetNetwork() throws Exception {
        int testNetworkId = 5;
        final String client1 = mGroupOwnerMacAddress;
        final String client2 = mPeerMacAddress;

        executeAndValidateInitializationSequence(false, false);
        doReturn(null).when(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        doNothing().when(mISupplicantP2pNetworkMock).setClientList(any(MacAddress[].class));

        String clientList = client1 + " " + client2;
        assertFalse(mDut.setClientList(testNetworkId, clientList));
        verify(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        verify(mISupplicantP2pNetworkMock, never()).setClientList(any(MacAddress[].class));
    }

    /**
     * Sunny day scenario for getClientList()
     */
    @Test
    public void testGetClientList() throws Exception {
        int testNetworkId = 5;
        MacAddress client1 = new MacAddress();
        MacAddress client2 = new MacAddress();
        client1.data = NativeUtil.macAddressToByteArray(mGroupOwnerMacAddress);
        client2.data = NativeUtil.macAddressToByteArray(mPeerMacAddress);
        MacAddress[] clientList = {client1, client2};

        executeAndValidateInitializationSequence(false, false);
        doAnswer(new AnswerWithArguments() {
            public ISupplicantP2pNetwork answer(final int networkId) {
                if (networkId == testNetworkId) {
                    return mISupplicantP2pNetworkMock;
                } else {
                    return null;
                }
            }
        }).when(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        doReturn(clientList).when(mISupplicantP2pNetworkMock).getClientList();

        String expectedClients = NativeUtil.macAddressFromByteArray(client1.data)
                + " " + NativeUtil.macAddressFromByteArray(client2.data);
        assertEquals(expectedClients, mDut.getClientList(testNetworkId));
        verify(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        verify(mISupplicantP2pNetworkMock).getClientList();
    }

    /**
     * Failure scenario for getClientList() when getNetwork returns null.
     */
    @Test
    public void testGetClientListFailureDueToGetNetwork() throws Exception {
        int testNetworkId = 5;
        MacAddress client1 = new MacAddress();
        MacAddress client2 = new MacAddress();
        client1.data = NativeUtil.macAddressToByteArray(mGroupOwnerMacAddress);
        client2.data = NativeUtil.macAddressToByteArray(mPeerMacAddress);
        MacAddress[] clientList = {client1, client2};

        executeAndValidateInitializationSequence(false, false);
        doReturn(null).when(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        doReturn(clientList).when(mISupplicantP2pNetworkMock).getClientList();

        assertNull(mDut.getClientList(testNetworkId));
        verify(mISupplicantP2pIfaceMock).getNetwork(anyInt());
        verify(mISupplicantP2pNetworkMock, never()).getClientList();
    }

    /**
     * Sunny day scenario for saveConfig()
     */
    @Test
    public void testSaveConfig() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).saveConfig();

        // Should fail before initialization.
        assertFalse(mDut.saveConfig());
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.saveConfig());
        verify(mISupplicantP2pIfaceMock).saveConfig();
    }

    /**
     * Sunny day scenario for setMacRandomization()
     */
    @Test
    public void testEnableMacRandomization() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).setMacRandomization(anyBoolean());

        // Should fail before initialization.
        assertFalse(mDut.setMacRandomization(true));
        executeAndValidateInitializationSequence(false, false);
        assertTrue(mDut.setMacRandomization(true));
        verify(mISupplicantP2pIfaceMock).setMacRandomization(eq(true));
    }

    /**
     * Sunny day scenario for removeClient()
     */
    @Test
    public void testRemoveClientSuccess() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doNothing().when(mISupplicantP2pIfaceMock).removeClient(
                eq(mPeerMacAddressBytes), anyBoolean());

        assertTrue(mDut.removeClient(mPeerMacAddress, true));
        verify(mISupplicantP2pIfaceMock).removeClient(eq(mPeerMacAddressBytes), eq(true));
    }

    /**
     * Failure scenario for removeClient() due to invalid peer mac address
     */
    @Test
    public void testRemoveClientFailureForInvalidPeerAddress() throws Exception {
        assertFalse(mDut.removeClient(mInvalidMacAddress1, true));
        verify(mISupplicantP2pIfaceMock, never())
                .removeClient(any(byte[].class), eq(true));
    }

    /**
     * Failure scenario for removeClient() when ISupplicant throws exception.
     */
    @Test
    public void testRemoveClientFailureInSupplicantIface() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock).removeClient(
                eq(mPeerMacAddressBytes), anyBoolean());

        assertFalse(mDut.removeClient(mPeerMacAddress, true));
        verify(mISupplicantP2pIfaceMock).removeClient(eq(mPeerMacAddressBytes), eq(true));
    }

    /**
     * Sunny day scenario for setVendorElements()
     */
    @Test
    public void testSetVendorElementsSuccess() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).setVendorElements(anyInt(), any());
        executeAndValidateInitializationSequence(false, false);
        Set<ScanResult.InformationElement> ies =  new HashSet<>();
        ies.add(new ScanResult.InformationElement(221, 0, new byte[]{(byte) 0xb}));
        byte[] iesBytes = new byte[] {(byte) 221, (byte) 1, (byte) 0xb};

        assertTrue(mDut.setVendorElements(ies));
        verify(mISupplicantP2pIfaceMock).setVendorElements(
                eq(P2pFrameTypeMask.P2P_FRAME_PROBE_RESP_P2P),
                aryEq(iesBytes));
    }

    /**
     * Sunny day scenario for setVendorElements() when VSIEs list is empty.
     */
    @Test
    public void testSetVendorElementsSuccessWithEmptyVsieList() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).setVendorElements(anyInt(), any());
        executeAndValidateInitializationSequence(false, false);
        Set<ScanResult.InformationElement> ies =  new HashSet<>();
        byte[] iesBytes = new byte[0];

        assertTrue(mDut.setVendorElements(ies));
        verify(mISupplicantP2pIfaceMock).setVendorElements(
                eq(P2pFrameTypeMask.P2P_FRAME_PROBE_RESP_P2P),
                aryEq(iesBytes));
    }

    /**
     * Failure scenario for setVendorElements() when VSIE list is null.
     */
    @Test
    public void testSetVendorElementsFailureWithNullVsieList() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).setVendorElements(anyInt(), any());
        executeAndValidateInitializationSequence(false, false);
        assertFalse(mDut.setVendorElements(null));
        verify(mISupplicantP2pIfaceMock, never()).setVendorElements(anyInt(), any());
    }

    /**
     * Failure scenario for setVendorElements() when RemoteException is thrown.
     */
    @Test
    public void testSetVendorElementsFailureWithRemoteException() throws Exception {
        doThrow(new RemoteException()).when(mISupplicantP2pIfaceMock)
                .setVendorElements(anyInt(), any(byte[].class));

        executeAndValidateInitializationSequence(false, false);
        Set<ScanResult.InformationElement> ies =  new HashSet<>();

        assertFalse(mDut.setVendorElements(ies));
        verify(mISupplicantP2pIfaceMock).setVendorElements(
                eq(P2pFrameTypeMask.P2P_FRAME_PROBE_RESP_P2P),
                aryEq(new byte[0]));
    }

    /**
     * Test that getSupportedFeatures returns the expected feature sets.
     */
    @Test
    public void testGetSupportedFeatures() {
        WifiSettingsConfigStore mockConfigStore = mock(WifiSettingsConfigStore.class);
        when(mWifiInjector.getSettingsConfigStore()).thenReturn(mockConfigStore);

        // If the service version cannot be retrieved, expect the default feature set.
        when(mockConfigStore.get(any())).thenReturn(-1);
        long defaultFeatureSet = mDut.getSupportedFeatures();
        verify(mockConfigStore).get(any());

        // Full feature set can be retrieved once we have the service version.
        when(mockConfigStore.get(any())).thenReturn(2);
        long fullFeatureSet = mDut.getSupportedFeatures();
        assertNotEquals(defaultFeatureSet, fullFeatureSet);
        verify(mockConfigStore, times(2)).get(any());

        // Service version should be cached on subsequent calls.
        assertEquals(fullFeatureSet, mDut.getSupportedFeatures());
        verifyNoMoreInteractions(mockConfigStore);
    }

    /**
     * Test the EAPOL Ip Address Allocation configuration Parameters
     */
    @Test
    public void testConfigureEapolIpAddressAllocationParamsSuccess() throws Exception {
        doNothing().when(mISupplicantP2pIfaceMock).configureEapolIpAddressAllocationParams(
                anyInt(), anyInt(), anyInt(), anyInt());
        executeAndValidateInitializationSequence(false, false);

        assertTrue(mDut.configureEapolIpAddressAllocationParams(0x0101A8C0,
                0x00FFFFFF, 0x0501A8C0, 0x0801A8C0));
        verify(mISupplicantP2pIfaceMock).configureEapolIpAddressAllocationParams(eq(0x0101A8C0),
                eq(0x00FFFFFF), eq(0x0501A8C0), eq(0x0801A8C0));
    }

    /**
     * Calls initialize and addP2pInterface to mock the startup sequence.
     * The two arguments will each trigger a different failure in addP2pInterface
     * when set to true.
     */
    private void executeAndValidateInitializationSequence(boolean causeRemoteException,
            boolean getNullInterface) throws Exception {
        boolean shouldSucceed = !causeRemoteException && !getNullInterface;
        // Setup addP2pInterface mock answers
        if (causeRemoteException) {
            doThrow(new RemoteException())
                    .when(mISupplicantMock).addP2pInterface(anyString());
        } else if (getNullInterface) {
            doReturn(null)
                    .when(mISupplicantMock).addP2pInterface(anyString());
        } else {
            doReturn(mISupplicantP2pIfaceMock)
                    .when(mISupplicantMock).addP2pInterface(anyString());
        }

        assertTrue(mDut.initialize());
        verify(mServiceBinderMock).linkToDeath(mSupplicantDeathCaptor.capture(), anyInt());
        assertTrue(mDut.isInitializationComplete());

        // Now setup the iface.
        assertEquals(shouldSucceed, mDut.setupIface(mIfaceName));
        verify(mISupplicantMock).addP2pInterface(anyString());
        if (!causeRemoteException && !getNullInterface) {
            verify(mISupplicantP2pIfaceMock).registerCallback(
                    any(ISupplicantP2pIfaceCallback.class));
        }
    }

    /**
     * Create new placeholder WifiP2pConfig instance.
     */
    private WifiP2pConfig createPlaceholderP2pConfig(String peerAddress,
            int wpsProvMethod, String pin) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.wps = new WpsInfo();
        config.deviceAddress = peerAddress;
        config.wps.setup = wpsProvMethod;
        config.wps.pin = pin;
        config.groupOwnerIntent = WifiP2pServiceImpl.DEFAULT_GROUP_OWNER_INTENT;
        return config;
    }

    /**
     * Create new placeholder WifiP2pGroup instance.
     */
    private WifiP2pGroup createPlaceholderP2pGroup() {
        WifiP2pGroup group = new WifiP2pGroup();
        group.setInterface(mIfaceName);
        WifiP2pDevice owner = new WifiP2pDevice();
        owner.deviceAddress = mGroupOwnerMacAddress;
        group.setOwner(owner);
        return group;
    }

    /**
     * Create new placeholder WifiP2pServiceInfo instance.
     */
    private WifiP2pServiceInfo createPlaceholderP2pServiceInfo(String... services) {
        class TestP2pServiceInfo extends WifiP2pServiceInfo {
            TestP2pServiceInfo(String[] services) {
                super(Arrays.asList(services));
            }
        }
        return new TestP2pServiceInfo(services);
    }

    /**
     * Tests the terminate function and ensures that its callback gets called.
     */
    @Test
    public void testTerminateAndDeadHandler() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        mDut.terminate();
        verify(mISupplicantMock).terminate();
        // Trigger the supplicant died.
        mSupplicantDeathCaptor.getValue().binderDied();
        // Check that terminate cleared all internal state.
        assertFalse(mDut.isInitializationComplete());
    }

    /**
     * Tests the handling of supplicant death notification.
     */
    @Test
    public void testSupplicantDeathCallback() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        assertNotNull(mSupplicantDeathCaptor.getValue());
        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));
        mSupplicantDeathCaptor.getValue().binderDied();
        assertFalse(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler).onDeath();
    }

    /**
     * Tests the handling of supplicant death unregister.
     */
    @Test
    public void testSupplicantDeathCallbackUnregister() throws Exception {
        executeAndValidateInitializationSequence(false, false);
        assertNotNull(mSupplicantDeathCaptor.getValue());
        assertTrue(mDut.isInitializationComplete());
        assertTrue(mDut.registerDeathHandler(mSupplicantHalDeathHandler));
        assertTrue(mDut.deregisterDeathHandler());
        mSupplicantDeathCaptor.getValue().binderDied();
        assertFalse(mDut.isInitializationComplete());
        verify(mSupplicantHalDeathHandler, never()).onDeath();
    }
}
