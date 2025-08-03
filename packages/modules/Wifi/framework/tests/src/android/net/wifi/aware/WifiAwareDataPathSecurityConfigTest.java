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

package android.net.wifi.aware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit test harness for WifiAwareDataPathSecurityConfig class.
 */
@SmallTest
public class WifiAwareDataPathSecurityConfigTest {
    private static final String INVALID_PASSPHRASE = "0";
    private static final String PASSPHRASE = "PASSPHRASE";
    private static final byte[] PMK = "01234567890123456789012345678901".getBytes();
    private static final byte[] INVALID_PMK = "0123456789012345678901234567890".getBytes();
    private static final byte[] PMKID = "0123456789012345".getBytes();
    private static final byte[] INVALID_PMKID = "012345678901234".getBytes();

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidPassphrase() {
        WifiAwareDataPathSecurityConfig.Builder builder =
                new WifiAwareDataPathSecurityConfig
                        .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128);
        builder.setPskPassphrase(INVALID_PASSPHRASE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidPmk() {
        WifiAwareDataPathSecurityConfig.Builder builder =
                new WifiAwareDataPathSecurityConfig
                        .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128);
        builder.setPmk(INVALID_PMK);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidPmkId() {
        WifiAwareDataPathSecurityConfig.Builder builder =
                new WifiAwareDataPathSecurityConfig
                        .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128);
        builder.setPmkId(INVALID_PMKID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidCipherSuite() {
        WifiAwareDataPathSecurityConfig.Builder builder =
                new WifiAwareDataPathSecurityConfig
                        .Builder(3);
    }

    @Test
    public void testBuilderWithSKCipherSuite() {
        WifiAwareDataPathSecurityConfig.Builder builder =
                new WifiAwareDataPathSecurityConfig
                        .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128);
        builder.setPskPassphrase(PASSPHRASE);
        WifiAwareDataPathSecurityConfig securityConfig = builder.build();
        assertTrue(securityConfig.isValid());
        assertEquals(PASSPHRASE, securityConfig.getPskPassphrase());
        assertEquals(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128,
                securityConfig.getCipherSuite());

        builder = new WifiAwareDataPathSecurityConfig
                .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256);
        builder.setPmk(PMK);
        securityConfig = builder.build();
        assertTrue(securityConfig.isValid());
        assertEquals(PMK, securityConfig.getPmk());
        assertEquals(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256,
                securityConfig.getCipherSuite());
    }

    @Test
    public void testBuilderWithPKCipherSuite() {
        WifiAwareDataPathSecurityConfig.Builder builder =
                new WifiAwareDataPathSecurityConfig
                        .Builder(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128);
        builder.setPmk(PMK);
        builder.setPmkId(PMKID);
        WifiAwareDataPathSecurityConfig securityConfig = builder.build();
        assertTrue(securityConfig.isValid());
        assertEquals(PMK, securityConfig.getPmk());
        assertEquals(PMKID, securityConfig.getPmkId());
        assertEquals(Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_128,
                securityConfig.getCipherSuite());
    }

}
