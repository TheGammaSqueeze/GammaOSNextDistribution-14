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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiRttControllerTest extends WifiBaseTest {
    // HAL mocks
    @Mock android.hardware.wifi.V1_0.IWifiRttController mIWifiRttControllerHidlMock;
    @Mock android.hardware.wifi.IWifiRttController mIWifiRttControllerAidlMock;

    // Framework HIDL/AIDL implementation mocks
    @Mock WifiRttControllerHidlImpl mWifiRttControllerHidlImplMock;
    @Mock WifiRttControllerAidlImpl mWifiRttControllerAidlImplMock;

    private WifiRttController mDut;

    private class WifiRttControllerSpy extends WifiRttController {
        WifiRttControllerSpy(android.hardware.wifi.V1_0.IWifiRttController rttController) {
            super(rttController);
        }

        WifiRttControllerSpy(android.hardware.wifi.IWifiRttController rttController) {
            super(rttController);
        }

        @Override
        protected WifiRttControllerHidlImpl createWifiRttControllerHidlImplMockable(
                android.hardware.wifi.V1_0.IWifiRttController rttController) {
            return mWifiRttControllerHidlImplMock;
        }

        @Override
        protected WifiRttControllerAidlImpl createWifiRttControllerAidlImplMockable(
                android.hardware.wifi.IWifiRttController rttController) {
            return mWifiRttControllerAidlImplMock;
        }
    }

    private class NullWifiRttControllerSpy extends WifiRttController {
        NullWifiRttControllerSpy() {
            super(mIWifiRttControllerAidlMock);
        }

        @Override
        protected WifiRttControllerAidlImpl createWifiRttControllerAidlImplMockable(
                android.hardware.wifi.IWifiRttController rttController) {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiRttControllerSpy(mIWifiRttControllerAidlMock);
    }

    /**
     * Test that we can initialize using the HIDL implementation.
     */
    @Test
    public void testInitWithHidlImpl() {
        mDut = new WifiRttControllerSpy(mIWifiRttControllerHidlMock);
        mDut.getRttCapabilities();
        verify(mWifiRttControllerHidlImplMock).getRttCapabilities();
        verify(mWifiRttControllerAidlImplMock, never()).getRttCapabilities();
    }

    /**
     * Test the case where the creation of the xIDL implementation
     * fails, so mWifiRttController is null.
     */
    @Test
    public void testInitFailureCase() {
        mDut = new NullWifiRttControllerSpy();
        assertNull(mDut.getRttCapabilities());
        verify(mWifiRttControllerHidlImplMock, never()).getRttCapabilities();
        verify(mWifiRttControllerAidlImplMock, never()).getRttCapabilities();
    }

    @Test
    public void testGetRttCapabilities() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.getRttCapabilities(),
                mWifiRttControllerAidlImplMock.getRttCapabilities(),
                mock(WifiRttController.Capabilities.class));
        verify(mWifiRttControllerAidlImplMock).getRttCapabilities();
    }
}
