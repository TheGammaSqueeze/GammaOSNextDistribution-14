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

import android.annotation.NonNull;
import android.content.Context;

import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiStaIfaceTest extends WifiBaseTest {
    // HAL mocks
    @Mock android.hardware.wifi.V1_0.IWifiStaIface mIWifiStaIfaceHidlMock;
    @Mock android.hardware.wifi.IWifiStaIface mIWifiStaIfaceAidlMock;

    // Framework HIDL/AIDL implementation mocks
    @Mock WifiStaIfaceHidlImpl mWifiStaIfaceHidlImplMock;
    @Mock WifiStaIfaceAidlImpl mWifiStaIfaceAidlImplMock;

    @Mock Context mContextMock;
    @Mock SsidTranslator mSsidTranslatorMock;

    private WifiStaIface mDut;

    private class WifiStaIfaceSpy extends WifiStaIface {
        WifiStaIfaceSpy(android.hardware.wifi.V1_0.IWifiStaIface staIface) {
            super(staIface, mContextMock, mSsidTranslatorMock);
        }

        WifiStaIfaceSpy(android.hardware.wifi.IWifiStaIface staIface) {
            super(staIface, mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected WifiStaIfaceHidlImpl createWifiStaIfaceHidlImplMockable(
                android.hardware.wifi.V1_0.IWifiStaIface staIface,
                @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
            return mWifiStaIfaceHidlImplMock;
        }

        @Override
        protected WifiStaIfaceAidlImpl createWifiStaIfaceAidlImplMockable(
                android.hardware.wifi.IWifiStaIface staIface,
                @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
            return mWifiStaIfaceAidlImplMock;
        }
    }

    private class NullWifiStaIfaceSpy extends WifiStaIface {
        NullWifiStaIfaceSpy() {
            super(mIWifiStaIfaceAidlMock, mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected WifiStaIfaceAidlImpl createWifiStaIfaceAidlImplMockable(
                android.hardware.wifi.IWifiStaIface staIface,
                @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiStaIfaceSpy(mIWifiStaIfaceAidlMock);
    }

    /**
     * Test that we can initialize using the HIDL implementation.
     */
    @Test
    public void testInitWithHidlImpl() {
        mDut = new WifiStaIfaceSpy(mIWifiStaIfaceHidlMock);
        mDut.getName();
        verify(mWifiStaIfaceHidlImplMock).getName();
        verify(mWifiStaIfaceAidlImplMock, never()).getName();
    }

    /**
     * Test the case where the creation of the xIDL implementation
     * fails, so mWifiStaIface is null.
     */
    @Test
    public void testInitFailureCase() {
        mDut = new NullWifiStaIfaceSpy();
        assertNull(mDut.getName());
        verify(mWifiStaIfaceHidlImplMock, never()).getName();
        verify(mWifiStaIfaceAidlImplMock, never()).getName();
    }

    @Test
    public void testGetName() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.getName(),
                mWifiStaIfaceAidlImplMock.getName(),
                "wlan0");
        verify(mWifiStaIfaceAidlImplMock).getName();
    }
}
