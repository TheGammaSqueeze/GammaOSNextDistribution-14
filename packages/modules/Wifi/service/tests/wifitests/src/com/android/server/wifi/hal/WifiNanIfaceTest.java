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

public class WifiNanIfaceTest extends WifiBaseTest {
    // HAL mocks
    @Mock android.hardware.wifi.V1_0.IWifiNanIface mIWifiNanIfaceHidlMock;
    @Mock android.hardware.wifi.IWifiNanIface mIWifiNanIfaceAidlMock;

    // Framework HIDL/AIDL implementation mocks
    @Mock WifiNanIfaceHidlImpl mWifiNanIfaceHidlImplMock;
    @Mock WifiNanIfaceAidlImpl mWifiNanIfaceAidlImplMock;

    private WifiNanIface mDut;

    private class WifiNanIfaceSpy extends WifiNanIface {
        WifiNanIfaceSpy(android.hardware.wifi.V1_0.IWifiNanIface nanIface) {
            super(nanIface);
        }

        WifiNanIfaceSpy(android.hardware.wifi.IWifiNanIface nanIface) {
            super(nanIface);
        }

        @Override
        protected WifiNanIfaceHidlImpl createWifiNanIfaceHidlImplMockable(
                android.hardware.wifi.V1_0.IWifiNanIface nanIface) {
            return mWifiNanIfaceHidlImplMock;
        }

        @Override
        protected WifiNanIfaceAidlImpl createWifiNanIfaceAidlImplMockable(
                android.hardware.wifi.IWifiNanIface nanIface) {
            return mWifiNanIfaceAidlImplMock;
        }
    }

    private class NullWifiNanIfaceSpy extends WifiNanIface {
        NullWifiNanIfaceSpy() {
            super(mIWifiNanIfaceAidlMock);
        }

        @Override
        protected WifiNanIfaceAidlImpl createWifiNanIfaceAidlImplMockable(
                android.hardware.wifi.IWifiNanIface nanIface) {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiNanIfaceSpy(mIWifiNanIfaceAidlMock);
    }

    /**
     * Test that we can initialize using the HIDL implementation.
     */
    @Test
    public void testInitWithHidlImpl() {
        mDut = new WifiNanIfaceSpy(mIWifiNanIfaceHidlMock);
        mDut.getName();
        verify(mWifiNanIfaceHidlImplMock).getName();
        verify(mWifiNanIfaceAidlImplMock, never()).getName();
    }

    /**
     * Test the case where the creation of the xIDL implementation
     * fails, so mWifiNanIface is null.
     */
    @Test
    public void testInitFailureCase() {
        mDut = new NullWifiNanIfaceSpy();
        assertNull(mDut.getName());
        verify(mWifiNanIfaceHidlImplMock, never()).getName();
        verify(mWifiNanIfaceAidlImplMock, never()).getName();
    }

    @Test
    public void testGetName() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.getName(),
                mWifiNanIfaceAidlImplMock.getName(),
                "wlan0");
        verify(mWifiNanIfaceAidlImplMock).getName();
    }
}
