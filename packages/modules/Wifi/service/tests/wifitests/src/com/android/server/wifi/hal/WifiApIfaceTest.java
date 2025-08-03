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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.MacAddress;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiApIfaceTest extends WifiBaseTest {
    // HAL mocks
    @Mock android.hardware.wifi.V1_0.IWifiApIface mIWifiApIfaceHidlMock;
    @Mock android.hardware.wifi.IWifiApIface mIWifiApIfaceAidlMock;

    // Framework HIDL/AIDL implementation mocks
    @Mock WifiApIfaceHidlImpl mWifiApIfaceHidlImplMock;
    @Mock WifiApIfaceAidlImpl mWifiApIfaceAidlImplMock;

    private WifiApIface mDut;

    private class WifiApIfaceSpy extends WifiApIface {
        WifiApIfaceSpy(android.hardware.wifi.V1_0.IWifiApIface apIface) {
            super(apIface);
        }

        WifiApIfaceSpy(android.hardware.wifi.IWifiApIface apIface) {
            super(apIface);
        }

        @Override
        protected WifiApIfaceHidlImpl createWifiApIfaceHidlImplMockable(
                android.hardware.wifi.V1_0.IWifiApIface apIface) {
            return mWifiApIfaceHidlImplMock;
        }

        @Override
        protected WifiApIfaceAidlImpl createWifiApIfaceAidlImplMockable(
                android.hardware.wifi.IWifiApIface apIface) {
            return mWifiApIfaceAidlImplMock;
        }
    }

    private class NullWifiApIfaceSpy extends WifiApIface {
        NullWifiApIfaceSpy() {
            super(mIWifiApIfaceAidlMock);
        }

        @Override
        protected WifiApIfaceAidlImpl createWifiApIfaceAidlImplMockable(
                android.hardware.wifi.IWifiApIface apIface) {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiApIfaceSpy(mIWifiApIfaceAidlMock);
    }

    /**
     * Test that we can initialize using the HIDL implementation.
     */
    @Test
    public void testInitWithHidlImpl() {
        mDut = new WifiApIfaceSpy(mIWifiApIfaceHidlMock);
        mDut.getName();
        verify(mWifiApIfaceHidlImplMock).getName();
        verify(mWifiApIfaceAidlImplMock, never()).getName();
    }

    /**
     * Test the case where the creation of the xIDL implementation
     * fails, so mWifiApIface is null.
     */
    @Test
    public void testInitFailureCase() {
        mDut = new NullWifiApIfaceSpy();
        assertNull(mDut.getName());
        verify(mWifiApIfaceHidlImplMock, never()).getName();
        verify(mWifiApIfaceAidlImplMock, never()).getName();
    }

    @Test
    public void testGetName() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.getName(),
                mWifiApIfaceAidlImplMock.getName(),
                "wlan0");
        verify(mWifiApIfaceAidlImplMock).getName();
    }

    @Test
    public void testSetCountryCode() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.setCountryCode("MX"),
                mWifiApIfaceAidlImplMock.setCountryCode(any()),
                true);
        verify(mWifiApIfaceAidlImplMock).setCountryCode(any());
    }

    /**
     * Test that setCountryCode rejects invalid country codes.
     */
    @Test
    public void testSetCountryCodeInvalidInput() {
        when(mWifiApIfaceAidlImplMock.setCountryCode(any())).thenReturn(true);
        assertFalse(mDut.setCountryCode(null));
        assertFalse(mDut.setCountryCode(""));
        assertFalse(mDut.setCountryCode("A"));
        assertFalse(mDut.setCountryCode("ABC"));
        verify(mWifiApIfaceAidlImplMock, never()).setCountryCode(any());
    }

    @Test
    public void testSetMacAddress() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.setMacAddress(mock(MacAddress.class)),
                mWifiApIfaceAidlImplMock.setMacAddress(any()),
                true);
        verify(mWifiApIfaceAidlImplMock).setMacAddress(any());
    }

    /**
     * Test that setMacAddress rejects a null MacAddress.
     */
    @Test
    public void testSetMacAddressInvalidInput() {
        when(mWifiApIfaceAidlImplMock.setMacAddress(any())).thenReturn(true);
        assertFalse(mDut.setMacAddress(null));
        verify(mWifiApIfaceAidlImplMock, never()).setMacAddress(any());
    }
}
