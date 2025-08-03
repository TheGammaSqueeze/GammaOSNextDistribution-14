/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wifi.hal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.MacAddress;
import android.os.RemoteException;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

public class WifiApIfaceHidlImplTest extends WifiBaseTest {
    private static final String TEST_IFACE_NAME = "wlan1";
    private static final MacAddress TEST_MAC_ADDRESS = MacAddress.fromString("ee:33:a2:94:10:92");

    private WifiApIfaceHidlImpl mDut;
    private WifiStatus mWifiStatusSuccess;
    private WifiStatus mWifiStatusFailure;
    private WifiStatus mWifiStatusBusy;

    @Mock private IWifiApIface mIWifiApIfaceMock;
    @Mock private android.hardware.wifi.V1_4.IWifiApIface mIWifiApIfaceMockV14;
    @Mock private android.hardware.wifi.V1_5.IWifiApIface mIWifiApIfaceMockV15;

    private class WifiApIfaceHidlImplSpy extends WifiApIfaceHidlImpl {
        private int mHidlVersion;

        WifiApIfaceHidlImplSpy(int hidlVersion) {
            super(mIWifiApIfaceMock);
            mHidlVersion = hidlVersion;
        }

        @Override
        protected android.hardware.wifi.V1_4.IWifiApIface getWifiApIfaceV1_4Mockable() {
            return mHidlVersion >= 4 ? mIWifiApIfaceMockV14 : null;
        }

        @Override
        protected android.hardware.wifi.V1_5.IWifiApIface getWifiApIfaceV1_5Mockable() {
            return mHidlVersion >= 5 ? mIWifiApIfaceMockV15 : null;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiApIfaceHidlImplSpy(0);

        mWifiStatusSuccess = new WifiStatus();
        mWifiStatusSuccess.code = WifiStatusCode.SUCCESS;
        mWifiStatusFailure = new WifiStatus();
        mWifiStatusFailure.code = WifiStatusCode.ERROR_UNKNOWN;
        mWifiStatusFailure.description = "I don't even know what a Mock Turtle is.";
        mWifiStatusBusy = new WifiStatus();
        mWifiStatusBusy.code = WifiStatusCode.ERROR_BUSY;
        mWifiStatusBusy.description = "Don't bother me, kid";
    }

    /**
     * Verifies setMacAddress() success.
     */
    @Test
    public void testSetMacAddressSuccess() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(4);
        byte[] macByteArray = TEST_MAC_ADDRESS.toByteArray();
        when(mIWifiApIfaceMockV14.setMacAddress(macByteArray)).thenReturn(mWifiStatusSuccess);

        assertTrue(mDut.setMacAddress(TEST_MAC_ADDRESS));
        verify(mIWifiApIfaceMockV14).setMacAddress(macByteArray);
    }

    /**
     * Verifies setMacAddress() can handle a failure status.
     */
    @Test
    public void testSetMacAddressFailDueToStatusFailure() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(4);
        byte[] macByteArray = TEST_MAC_ADDRESS.toByteArray();
        when(mIWifiApIfaceMockV14.setMacAddress(macByteArray)).thenReturn(mWifiStatusFailure);

        assertFalse(mDut.setMacAddress(TEST_MAC_ADDRESS));
        verify(mIWifiApIfaceMockV14).setMacAddress(macByteArray);
    }

    /**
     * Verifies setMacAddress() can handle a RemoteException.
     */
    @Test
    public void testSetMacAddressFailDueToRemoteException() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(4);
        byte[] macByteArray = TEST_MAC_ADDRESS.toByteArray();
        doThrow(new RemoteException()).when(mIWifiApIfaceMockV14).setMacAddress(macByteArray);

        assertFalse(mDut.setMacAddress(TEST_MAC_ADDRESS));
        verify(mIWifiApIfaceMockV14).setMacAddress(macByteArray);
    }

    /**
     * Verifies resetToFactoryMacAddress() success.
     */
    @Test
    public void testResetToFactoryMacAddressSuccess() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(5);
        when(mIWifiApIfaceMockV15.resetToFactoryMacAddress()).thenReturn(mWifiStatusSuccess);
        assertTrue(mDut.resetToFactoryMacAddress());
        verify(mIWifiApIfaceMockV15).resetToFactoryMacAddress();
    }

    /**
     * Verifies that resetToFactoryMacAddress() can handle a failure status.
     */
    @Test
    public void testResetToFactoryMacAddressFailDueToStatusFailure() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(5);
        when(mIWifiApIfaceMockV15.resetToFactoryMacAddress()).thenReturn(mWifiStatusFailure);
        assertFalse(mDut.resetToFactoryMacAddress());
        verify(mIWifiApIfaceMockV15).resetToFactoryMacAddress();
    }

    /**
     * Verifies that resetToFactoryMacAddress() can handle a RemoteException.
     */
    @Test
    public void testResetToFactoryMacAddressFailDueToRemoteException() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(5);
        doThrow(new RemoteException()).when(mIWifiApIfaceMockV15).resetToFactoryMacAddress();
        assertFalse(mDut.resetToFactoryMacAddress());
        verify(mIWifiApIfaceMockV15).resetToFactoryMacAddress();
    }

    /**
     * Verifies that resetToFactoryMacAddress() does not crash with a HAL < V1.5
     */
    @Test
    public void testResetToFactoryMacAddressDoesNotCrashOnOlderHal() throws Exception {
        assertFalse(mDut.resetToFactoryMacAddress());
    }

    /**
     * Verifies getBridgedApInstances() success.
     */
    @Test
    public void testGetBridgedApInstancesSuccess() throws Exception {
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(
                    android.hardware.wifi.V1_5.IWifiApIface.getBridgedInstancesCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess,
                        new ArrayList<String>() {{ add(TEST_IFACE_NAME); }});
            }
        }).when(mIWifiApIfaceMockV15).getBridgedInstances(any(
                android.hardware.wifi.V1_5.IWifiApIface.getBridgedInstancesCallback.class));
        mDut = new WifiApIfaceHidlImplSpy(5);
        assertNotNull(mDut.getBridgedInstances());
        verify(mIWifiApIfaceMockV15).getBridgedInstances(any());
    }

    /**
     * Verifies that getBridgedApInstances() can handle a failure status.
     */
    @Test
    public void testGetBridgedApInstancesFailDueToStatusFailure() throws Exception {
        doAnswer(new MockAnswerUtil.AnswerWithArguments() {
            public void answer(
                    android.hardware.wifi.V1_5.IWifiApIface.getBridgedInstancesCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusFailure, null);
            }
        }).when(mIWifiApIfaceMockV15).getBridgedInstances(any(
                android.hardware.wifi.V1_5.IWifiApIface.getBridgedInstancesCallback.class));
        mDut = new WifiApIfaceHidlImplSpy(5);
        assertNull(mDut.getBridgedInstances());
        verify(mIWifiApIfaceMockV15).getBridgedInstances(any());
    }

    /**
     * Verifies that getBridgedApInstances() can handle a RemoteException.
     */
    @Test
    public void testGetBridgedApInstancesFailDueToRemoteException() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(5);
        doThrow(new RemoteException()).when(mIWifiApIfaceMockV15).getBridgedInstances(any());
        assertNull(mDut.getBridgedInstances());
        verify(mIWifiApIfaceMockV15).getBridgedInstances(any());
    }

    /**
     * Verifies that getBridgedApInstances() does not crash with a HAL < V1.5
     */
    @Test
    public void testGetBridgedApInstancesNotCrashOnOlderHal() throws Exception {
        assertNull(mDut.getBridgedInstances());
    }

    /**
     * Verifies isSetMacAddressSupported().
     */
    @Test
    public void testIsSetMacAddressSupportedWhenV1_4Support() throws Exception {
        mDut = new WifiApIfaceHidlImplSpy(4);
        assertTrue(mDut.isSetMacAddressSupported());
    }

    /**
     * Verifies that isSetMacAddressSupported() does not crash with a HAL < V1.4
     */
    @Test
    public void testIsSetMacAddressSupportedOnOlderHal() throws Exception {
        assertFalse(mDut.isSetMacAddressSupported());
    }
}
