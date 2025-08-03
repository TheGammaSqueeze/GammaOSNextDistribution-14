/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wifi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.net.MacAddress;
import android.net.wifi.WifiConfiguration;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.util.KeystoreWrapper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.ProviderException;
import java.util.Random;

import javax.crypto.Mac;

/**
 * Unit tests for {@link com.android.server.wifi.MacAddressUtil}.
 */
@SmallTest
public class MacAddressUtilTest extends WifiBaseTest {
    private MacAddressUtil mMacAddressUtil;

    @Mock private KeystoreWrapper mKeystoreWrapper;
    @Mock private Mac mMacForSta;
    @Mock private Mac mMacForSap;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mKeystoreWrapper.getHmacSHA256ForUid(anyInt(),
                eq(MacAddressUtil.MAC_RANDOMIZATION_ALIAS))).thenReturn(mMacForSta);
        when(mKeystoreWrapper.getHmacSHA256ForUid(anyInt(),
                eq(MacAddressUtil.MAC_RANDOMIZATION_SAP_ALIAS))).thenReturn(mMacForSap);
        mMacAddressUtil = new MacAddressUtil(mKeystoreWrapper);
    }

    @Test
    public void testCalculatePersistentMacForSta() {
        // verify null input
        assertNull(mMacAddressUtil.calculatePersistentMacForSta(null, 0));

        Random rand = new Random();
        byte[] bytes = new byte[32];
        // Verify that the MAC address calculated is valid
        for (int i = 0; i < 10; i++) {
            rand.nextBytes(bytes);
            when(mMacForSta.doFinal(any())).thenReturn(bytes);
            MacAddress macAddress = mMacAddressUtil.calculatePersistentMacForSta(
                    "TEST_SSID_AND_SECURITY_TYPE_" + i, 0);
            assertTrue(WifiConfiguration.isValidMacAddressForRandomization(macAddress));
        }

        // Verify the HashFunction is only queried from KeyStore once
        verify(mMacForSta, times(10)).doFinal(any());
        verify(mKeystoreWrapper).getHmacSHA256ForUid(0, MacAddressUtil.MAC_RANDOMIZATION_ALIAS);

        // Now simulate the cached hash being invalid
        when(mMacForSta.doFinal(any())).thenThrow(new ProviderException("error occurred"));
        // Mock mKeystoreWrapper to return a new Mac when called again
        Mac macForSta2 = mock(Mac.class);
        when(macForSta2.doFinal(any())).thenReturn(bytes);
        when(mKeystoreWrapper.getHmacSHA256ForUid(anyInt(),
                eq(MacAddressUtil.MAC_RANDOMIZATION_ALIAS))).thenReturn(macForSta2);

        // Then verify the cache is updated
        assertTrue(WifiConfiguration.isValidMacAddressForRandomization(
                mMacAddressUtil.calculatePersistentMacForSta("TEST_SSID_AND_SECURITY_TYPE", 0)));
        verify(mKeystoreWrapper, times(2)).getHmacSHA256ForUid(
                0, MacAddressUtil.MAC_RANDOMIZATION_ALIAS);
        verify(macForSta2).doFinal(any());
    }

    @Test
    public void testCalculatePersistentMacForSap() {
        // verify null input
        assertNull(mMacAddressUtil.calculatePersistentMacForSap(null, 0));

        Random rand = new Random();
        byte[] bytes = new byte[32];
        // Verify that the MAC address calculated is valid
        for (int i = 0; i < 10; i++) {
            rand.nextBytes(bytes);
            when(mMacForSap.doFinal(any())).thenReturn(bytes);
            MacAddress macAddress = mMacAddressUtil.calculatePersistentMacForSap(
                    "TEST_SSID_AND_SECURITY_TYPE_" + i, 0);
            assertTrue(WifiConfiguration.isValidMacAddressForRandomization(macAddress));
        }

        // Verify the HashFunction is only queried from KeyStore once
        verify(mMacForSap, times(10)).doFinal(any());
        verify(mKeystoreWrapper).getHmacSHA256ForUid(0,
                MacAddressUtil.MAC_RANDOMIZATION_SAP_ALIAS);

        // Now simulate the cached hash being invalid
        when(mMacForSap.doFinal(any())).thenThrow(new ProviderException("error occurred"));
        // Mock mKeystoreWrapper to return a new Mac when called again
        Mac macForSap2 = mock(Mac.class);
        when(macForSap2.doFinal(any())).thenReturn(bytes);
        when(mKeystoreWrapper.getHmacSHA256ForUid(anyInt(),
                eq(MacAddressUtil.MAC_RANDOMIZATION_SAP_ALIAS))).thenReturn(macForSap2);

        // Then verify the cache is updated
        assertTrue(WifiConfiguration.isValidMacAddressForRandomization(
                mMacAddressUtil.calculatePersistentMacForSap("TEST_SSID_AND_SECURITY_TYPE", 0)));
        verify(mKeystoreWrapper, times(2)).getHmacSHA256ForUid(
                0, MacAddressUtil.MAC_RANDOMIZATION_SAP_ALIAS);
        verify(macForSap2).doFinal(any());
    }

    /**
     * Verifies getNextMacAddressForSecondary()
     */
    @Test
    public void testGetNextMacAddressForSecondary() {
        assertTrue(MacAddress.fromString("2a:53:43:c3:56:2b").equals(
                MacAddressUtil.nextMacAddress(
                        MacAddress.fromString("2a:53:43:c3:56:2a"))));
        assertTrue(MacAddress.fromString("2a:53:43:c3:56:00").equals(
                MacAddressUtil.nextMacAddress(
                        MacAddress.fromString("2a:53:43:c3:56:ff"))));
    }
}
