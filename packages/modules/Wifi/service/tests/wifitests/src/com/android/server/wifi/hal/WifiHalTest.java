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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

public class WifiHalTest extends WifiBaseTest {
    // Framework HIDL/AIDL implementation mocks
    @Mock WifiHalHidlImpl mWifiHalHidlImplMock;
    @Mock WifiHalAidlImpl mWifiHalAidlImplMock;

    @Mock Context mContextMock;
    @Mock SsidTranslator mSsidTranslatorMock;

    private WifiHal mDut;

    private class WifiHalAidlSpy extends WifiHal {
        WifiHalAidlSpy() {
            super(mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected IWifiHal createWifiHalMockable(@NonNull Context context,
                @NonNull SsidTranslator ssidTranslator) {
            return mWifiHalAidlImplMock;
        }
    }

    private class WifiHalHidlSpy extends WifiHal {
        WifiHalHidlSpy() {
            super(mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected IWifiHal createWifiHalMockable(@NonNull Context context,
                @NonNull SsidTranslator ssidTranslator) {
            return mWifiHalHidlImplMock;
        }
    }

    private class WifiHalNullSpy extends WifiHal {
        WifiHalNullSpy() {
            super(mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected IWifiHal createWifiHalMockable(@NonNull Context context,
                @NonNull SsidTranslator ssidTranslator) {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test that we can initialize using the HIDL implementation.
     */
    @Test
    public void testInitWithHidlImpl() {
        mDut = new WifiHalHidlSpy();
        mDut.getChip(1);
        verify(mWifiHalHidlImplMock).getChip(eq(1));
        verify(mWifiHalAidlImplMock, never()).getChip(anyInt());
    }

    /**
     * Test the case where the creation of the xIDL implementation
     * fails, so mWifi is null.
     */
    @Test
    public void testInitFailureCase() {
        mDut = new WifiHalNullSpy();
        assertNull(mDut.getChip(1));
        verify(mWifiHalHidlImplMock, never()).getChip(anyInt());
        verify(mWifiHalAidlImplMock, never()).getChip(anyInt());
    }

    @Test
    public void testGetChip() {
        mDut = new WifiHalAidlSpy();
        HalTestUtils.verifyReturnValue(
                () -> mDut.getChip(1),
                mWifiHalAidlImplMock.getChip(anyInt()),
                mock(WifiChip.class));
        verify(mWifiHalAidlImplMock).getChip(eq(1));
    }
}
