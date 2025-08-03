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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;

import com.android.server.wifi.SsidTranslator;
import com.android.server.wifi.WifiBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WifiChipTest extends WifiBaseTest {
    // HAL mocks
    @Mock android.hardware.wifi.V1_0.IWifiChip mIWifiChipHidlMock;
    @Mock android.hardware.wifi.IWifiChip mIWifiChipAidlMock;

    // Framework HIDL/AIDL implementation mocks
    @Mock WifiChipHidlImpl mWifiChipHidlImplMock;
    @Mock WifiChipAidlImpl mWifiChipAidlImplMock;

    @Mock Context mContextMock;
    @Mock SsidTranslator mSsidTranslatorMock;

    private WifiChip mDut;

    private class WifiChipSpy extends WifiChip {
        WifiChipSpy(android.hardware.wifi.V1_0.IWifiChip chip) {
            super(chip, mContextMock, mSsidTranslatorMock);
        }

        WifiChipSpy(android.hardware.wifi.IWifiChip chip) {
            super(chip, mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected WifiChipHidlImpl createWifiChipHidlImplMockable(
                @NonNull android.hardware.wifi.V1_0.IWifiChip chip,
                @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
            return mWifiChipHidlImplMock;
        }

        @Override
        protected WifiChipAidlImpl createWifiChipAidlImplMockable(
                @NonNull android.hardware.wifi.IWifiChip chip,
                @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
            return mWifiChipAidlImplMock;
        }
    }

    private class NullWifiChipSpy extends WifiChip {
        NullWifiChipSpy() {
            super(mIWifiChipAidlMock, mContextMock, mSsidTranslatorMock);
        }

        @Override
        protected WifiChipAidlImpl createWifiChipAidlImplMockable(
                @NonNull android.hardware.wifi.IWifiChip chip,
                @NonNull Context context, @NonNull SsidTranslator ssidTranslator) {
            return null;
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDut = new WifiChipSpy(mIWifiChipAidlMock);
    }

    /**
     * Test that we can initialize using the HIDL implementation.
     */
    @Test
    public void testInitWithHidlImpl() {
        mDut = new WifiChipSpy(mIWifiChipHidlMock);
        mDut.configureChip(1);
        verify(mWifiChipHidlImplMock).configureChip(eq(1));
        verify(mWifiChipAidlImplMock, never()).configureChip(anyInt());
    }

    /**
     * Test the case where the creation of the xIDL implementation
     * fails, so mWifiChip is null.
     */
    @Test
    public void testInitFailureCase() {
        mDut = new NullWifiChipSpy();
        assertFalse(mDut.configureChip(1));
        verify(mWifiChipHidlImplMock, never()).configureChip(anyInt());
        verify(mWifiChipAidlImplMock, never()).configureChip(anyInt());
    }

    @Test
    public void testConfigureChip() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.configureChip(1),
                mWifiChipAidlImplMock.configureChip(anyInt()),
                true);
        verify(mWifiChipAidlImplMock).configureChip(eq(1));
    }

    @Test
    public void testSetCountryCode() {
        HalTestUtils.verifyReturnValue(
                () -> mDut.setCountryCode("MX"),
                mWifiChipAidlImplMock.setCountryCode(any()),
                true);
        verify(mWifiChipAidlImplMock).setCountryCode(any());
    }

    /**
     * Test that setCountryCode rejects invalid country codes.
     */
    @Test
    public void testSetCountryCodeInvalidInput() {
        when(mWifiChipAidlImplMock.setCountryCode(any())).thenReturn(true);
        assertFalse(mDut.setCountryCode(null));
        assertFalse(mDut.setCountryCode(""));
        assertFalse(mDut.setCountryCode("A"));
        assertFalse(mDut.setCountryCode("ABC"));
        verify(mWifiChipAidlImplMock, never()).setCountryCode(any());
    }
}
