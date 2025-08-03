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

import static junit.framework.Assert.assertNull;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiP2pIfaceTest extends WifiBaseTest {
    // HAL mocks
    @Mock android.hardware.wifi.V1_0.IWifiP2pIface mIWifiP2pIfaceHidlMock;
    @Mock android.hardware.wifi.IWifiP2pIface mIWifiP2pIfaceAidlMock;

    // Framework HIDL/AIDL implementation mocks
    @Mock WifiP2pIfaceHidlImpl mWifiP2pIfaceHidlImplMock;
    @Mock WifiP2pIfaceAidlImpl mWifiP2pIfaceAidlImplMock;

    private WifiP2pIface mDut;

    private class WifiP2pIfaceSpy extends WifiP2pIface {
        WifiP2pIfaceSpy(android.hardware.wifi.V1_0.IWifiP2pIface p2pIface) {
            super(p2pIface);
        }

        WifiP2pIfaceSpy(android.hardware.wifi.IWifiP2pIface p2pIface) {
            super(p2pIface);
        }

        @Override
        protected WifiP2pIfaceHidlImpl createWifiP2pIfaceHidlImplMockable(
                android.hardware.wifi.V1_0.IWifiP2pIface p2pIface) {
            return mWifiP2pIfaceHidlImplMock;
        }

        @Override
        protected WifiP2pIfaceAidlImpl createWifiP2pIfaceAidlImplMockable(
                android.hardware.wifi.IWifiP2pIface p2pIface) {
            return mWifiP2pIfaceAidlImplMock;
        }
    }

    private class NullWifiP2pIfaceSpy extends WifiP2pIface {
        NullWifiP2pIfaceSpy() {
            super(mIWifiP2pIfaceAidlMock);
        }

        @Override
        protected WifiP2pIfaceAidlImpl createWifiP2pIfaceAidlImplMockable(
                android.hardware.wifi.IWifiP2pIface p2pIface) {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiP2pIfaceSpy(mIWifiP2pIfaceAidlMock);
    }

    /**
     * Test that we can initialize using the HIDL implementation.
     */
    @Test
    public void testInitWithHidlImpl() {
        mDut = new WifiP2pIfaceSpy(mIWifiP2pIfaceHidlMock);
        mDut.getName();
        verify(mWifiP2pIfaceHidlImplMock).getName();
        verify(mWifiP2pIfaceAidlImplMock, never()).getName();
    }

    /**
     * Test the case where the creation of the xIDL implementation
     * fails, so mWifiP2pIface is null.
     */
    @Test
    public void testInitFailureCase() {
        mDut = new NullWifiP2pIfaceSpy();
        assertNull(mDut.getName());
        verify(mWifiP2pIfaceHidlImplMock, never()).getName();
        verify(mWifiP2pIfaceAidlImplMock, never()).getName();
    }

    @Test
    public void testGetName() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.getName(),
                mWifiP2pIfaceAidlImplMock.getName(),
                "wlan0");
        verify(mWifiP2pIfaceAidlImplMock).getName();
    }
}
