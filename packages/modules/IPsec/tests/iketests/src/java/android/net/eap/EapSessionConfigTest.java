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

package android.net.eap.test;

import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_AKA;
import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_AKA_PRIME;
import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_MSCHAP_V2;
import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_SIM;
import static android.net.eap.test.EapSessionConfig.EapMethodConfig.EAP_TYPE_TTLS;
import static android.telephony.TelephonyManager.APPTYPE_USIM;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.eap.test.EapSessionConfig.EapAkaConfig;
import android.net.eap.test.EapSessionConfig.EapAkaOption;
import android.net.eap.test.EapSessionConfig.EapAkaPrimeConfig;
import android.net.eap.test.EapSessionConfig.EapMethodConfig;
import android.net.eap.test.EapSessionConfig.EapMsChapV2Config;
import android.net.eap.test.EapSessionConfig.EapSimConfig;
import android.net.eap.test.EapSessionConfig.EapTtlsConfig;
import android.os.PersistableBundle;

import com.android.internal.net.ipsec.test.ike.testutils.CertUtils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

public class EapSessionConfigTest {
    private static final byte[] DEFAULT_IDENTITY = new byte[0];
    private static final byte[] EAP_IDENTITY =
            "test@android.net".getBytes(StandardCharsets.US_ASCII);
    private static final int SUB_ID = 1;
    private static final String NETWORK_NAME = "android.net";
    private static final boolean ALLOW_MISMATCHED_NETWORK_NAMES = true;
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final byte[] REAUTH_ID_BYTES =
            "4OLUpQCqFyhm1/UgD56anTzYTqJDckibqjU6PlS4sZaiuLc=".getBytes(StandardCharsets.UTF_8);

    private static void verifyPersistableBundleEncodeDecodeIsLossless(EapMethodConfig config) {
        PersistableBundle bundle = config.toPersistableBundle();
        EapMethodConfig resultConfig = EapMethodConfig.fromPersistableBundle(bundle);

        assertEquals(config, resultConfig);
    }

    private static void verifyPersistableBundleEncodeDecodeIsLossless(EapSessionConfig config) {
        PersistableBundle bundle = config.toPersistableBundle();
        EapSessionConfig resultConfig = EapSessionConfig.fromPersistableBundle(bundle);

        assertEquals(config, resultConfig);
    }

    @Test
    public void testBuildEapSim() {
        EapSessionConfig result = new EapSessionConfig.Builder()
                .setEapIdentity(EAP_IDENTITY)
                .setEapSimConfig(SUB_ID, APPTYPE_USIM)
                .build();

        assertArrayEquals(EAP_IDENTITY, result.getEapIdentity());

        EapMethodConfig eapMethodConfig = result.getEapConfigs().get(EAP_TYPE_SIM);
        assertEquals(EAP_TYPE_SIM, eapMethodConfig.getMethodType());
        EapSimConfig eapSimConfig = (EapSimConfig) eapMethodConfig;
        assertEquals(SUB_ID, eapSimConfig.getSubId());
        assertEquals(APPTYPE_USIM, eapSimConfig.getAppType());
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapSim() throws Exception {
        verifyPersistableBundleEncodeDecodeIsLossless(new EapSimConfig(SUB_ID, APPTYPE_USIM));
    }

    private void verifyBuildEapAka(EapSessionConfig eapConfig, boolean withOption) {
        assertArrayEquals(DEFAULT_IDENTITY, eapConfig.getEapIdentity());
        EapMethodConfig eapMethodConfig = eapConfig.getEapConfigs().get(EAP_TYPE_AKA);
        assertEquals(EAP_TYPE_AKA, eapMethodConfig.getMethodType());
        EapAkaConfig eapAkaConfig = (EapAkaConfig) eapMethodConfig;
        assertEquals(SUB_ID, eapAkaConfig.getSubId());
        assertEquals(APPTYPE_USIM, eapAkaConfig.getAppType());
        if (withOption) {
            assertArrayEquals(REAUTH_ID_BYTES, eapAkaConfig.getEapAkaOption().getReauthId());
        } else {
            assertNull(eapAkaConfig.getEapAkaOption());
        }
    }

    @Test
    public void testBuildEapAka() {
        EapSessionConfig result = new EapSessionConfig.Builder()
                .setEapAkaConfig(SUB_ID, APPTYPE_USIM)
                .build();

        verifyBuildEapAka(result, false /* withOption */);
    }

    @Test
    public void testBuildEapAkaWithOption() {
        EapSessionConfig result =
                new EapSessionConfig.Builder()
                        .setEapAkaConfig(
                                SUB_ID,
                                APPTYPE_USIM,
                                new EapAkaOption.Builder().setReauthId(REAUTH_ID_BYTES).build())
                        .build();

        verifyBuildEapAka(result, true /* withOption */);
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapAka() throws Exception {
        verifyPersistableBundleEncodeDecodeIsLossless(new EapAkaConfig(SUB_ID, APPTYPE_USIM));
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapAkaWithOption() throws Exception {
        EapAkaOption option = new EapAkaOption.Builder().setReauthId(REAUTH_ID_BYTES).build();
        verifyPersistableBundleEncodeDecodeIsLossless(
                new EapAkaConfig(SUB_ID, APPTYPE_USIM, option));
    }

    @Test
    public void testBuildEapAkaPrime() {
        EapSessionConfig result =
                new EapSessionConfig.Builder()
                        .setEapAkaPrimeConfig(
                                SUB_ID, APPTYPE_USIM, NETWORK_NAME, ALLOW_MISMATCHED_NETWORK_NAMES)
                        .build();

        assertArrayEquals(DEFAULT_IDENTITY, result.getEapIdentity());
        EapMethodConfig eapMethodConfig = result.getEapConfigs().get(EAP_TYPE_AKA_PRIME);
        assertEquals(EAP_TYPE_AKA_PRIME, eapMethodConfig.getMethodType());
        EapAkaPrimeConfig eapAkaPrimeConfig = (EapAkaPrimeConfig) eapMethodConfig;
        assertEquals(SUB_ID, eapAkaPrimeConfig.getSubId());
        assertEquals(APPTYPE_USIM, eapAkaPrimeConfig.getAppType());
        assertEquals(NETWORK_NAME, eapAkaPrimeConfig.getNetworkName());
        assertTrue(eapAkaPrimeConfig.allowsMismatchedNetworkNames());
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapAkaPrime() throws Exception {
        verifyPersistableBundleEncodeDecodeIsLossless(
                new EapAkaPrimeConfig(
                        SUB_ID, APPTYPE_USIM, NETWORK_NAME, ALLOW_MISMATCHED_NETWORK_NAMES));
    }

    @Test
    public void testBuildEapMsChapV2() {
        EapSessionConfig result =
                new EapSessionConfig.Builder().setEapMsChapV2Config(USERNAME, PASSWORD).build();

        EapMsChapV2Config config =
                (EapMsChapV2Config) result.getEapConfigs().get(EAP_TYPE_MSCHAP_V2);
        assertEquals(EAP_TYPE_MSCHAP_V2, config.getMethodType());
        assertEquals(USERNAME, config.getUsername());
        assertEquals(PASSWORD, config.getPassword());
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapMsChapV2() throws Exception {
        verifyPersistableBundleEncodeDecodeIsLossless(new EapMsChapV2Config(USERNAME, PASSWORD));
    }

    @Test
    public void testBuildEapTtls() throws Exception {
        EapSessionConfig innerConfig =
                new EapSessionConfig.Builder().setEapMsChapV2Config(USERNAME, PASSWORD).build();
        X509Certificate trustedCa = CertUtils.createCertFromPemFile("self-signed-ca-a.pem");

        EapSessionConfig result =
                new EapSessionConfig.Builder().setEapTtlsConfig(trustedCa, innerConfig).build();

        assertArrayEquals(DEFAULT_IDENTITY, result.getEapIdentity());
        EapTtlsConfig config = (EapTtlsConfig) result.getEapConfigs().get(EAP_TYPE_TTLS);
        assertEquals(EAP_TYPE_TTLS, config.getMethodType());
        assertEquals(innerConfig, config.getInnerEapSessionConfig());
        assertEquals(trustedCa, config.getServerCaCert());
    }

    @Test
    public void testEqualsEapTtls() throws Exception {
        EapSessionConfig innerConfig =
                new EapSessionConfig.Builder().setEapMsChapV2Config(USERNAME, PASSWORD).build();
        X509Certificate trustedCa = CertUtils.createCertFromPemFile("self-signed-ca-a.pem");

        assertEquals(
                new EapTtlsConfig(trustedCa, innerConfig),
                new EapTtlsConfig(trustedCa, innerConfig));
        assertEquals(new EapTtlsConfig(null, innerConfig), new EapTtlsConfig(null, innerConfig));
        assertNotEquals(
                new EapTtlsConfig(trustedCa, innerConfig), new EapTtlsConfig(null, innerConfig));
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapTtls() throws Exception {
        EapSessionConfig innerConfig =
                new EapSessionConfig.Builder().setEapMsChapV2Config(USERNAME, PASSWORD).build();
        X509Certificate trustedCa = CertUtils.createCertFromPemFile("self-signed-ca-a.pem");

        verifyPersistableBundleEncodeDecodeIsLossless(new EapTtlsConfig(trustedCa, innerConfig));
    }

    @Test(expected = NullPointerException.class)
    public void testSetEapIdentityNull() {
        new EapSessionConfig.Builder().setEapIdentity(null);
    }

    @Test(expected = NullPointerException.class)
    public void testBuildEapAkaPrimeNullNetworkName() {
        new EapSessionConfig.Builder()
                .setEapAkaPrimeConfig(SUB_ID, APPTYPE_USIM, null, ALLOW_MISMATCHED_NETWORK_NAMES);
    }

    @Test(expected = NullPointerException.class)
    public void testBuildEapMsChapV2NullUsername() {
        new EapSessionConfig.Builder().setEapMsChapV2Config(null, PASSWORD);
    }

    @Test(expected = NullPointerException.class)
    public void testBuildEapMsChapV2NullPassword() {
        new EapSessionConfig.Builder().setEapMsChapV2Config(USERNAME, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildEapTtls_invalidInnerConfig() throws Exception {
        EapSessionConfig msChapConfig =
                new EapSessionConfig.Builder().setEapMsChapV2Config(USERNAME, PASSWORD).build();
        EapSessionConfig innerTtlsConfig =
                new EapSessionConfig.Builder()
                        .setEapTtlsConfig(null /* trustedCa */, msChapConfig)
                        .build();
        X509Certificate trustedCa = CertUtils.createCertFromPemFile("self-signed-ca-a.pem");

        EapSessionConfig result =
                new EapSessionConfig.Builder().setEapTtlsConfig(trustedCa, innerTtlsConfig).build();
    }

    @Test(expected = NullPointerException.class)
    public void testBuildEapTtls_missingInnerConfig() throws Exception {
        X509Certificate trustedCa = CertUtils.createCertFromPemFile("self-signed-ca-a.pem");

        EapSessionConfig result =
                new EapSessionConfig.Builder().setEapTtlsConfig(trustedCa, null).build();
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapSessioConfig() throws Exception {
        EapSessionConfig config =
                new EapSessionConfig.Builder()
                        .setEapIdentity(EAP_IDENTITY)
                        .setEapSimConfig(SUB_ID, APPTYPE_USIM)
                        .setEapAkaConfig(SUB_ID, APPTYPE_USIM)
                        .setEapAkaPrimeConfig(
                                SUB_ID, APPTYPE_USIM, NETWORK_NAME, ALLOW_MISMATCHED_NETWORK_NAMES)
                        .setEapMsChapV2Config(USERNAME, PASSWORD)
                        .build();

        verifyPersistableBundleEncodeDecodeIsLossless(config);
    }

    @Test
    public void testPersistableBundleEncodeDecodeEapSessioConfigWithoutId() throws Exception {
        EapSessionConfig config =
                new EapSessionConfig.Builder()
                        .setEapSimConfig(SUB_ID, APPTYPE_USIM)
                        .setEapAkaConfig(SUB_ID, APPTYPE_USIM)
                        .setEapAkaPrimeConfig(
                                SUB_ID, APPTYPE_USIM, NETWORK_NAME, ALLOW_MISMATCHED_NETWORK_NAMES)
                        .setEapMsChapV2Config(USERNAME, PASSWORD)
                        .build();

        verifyPersistableBundleEncodeDecodeIsLossless(config);
    }

    @Test
    public void testBuildWithoutConfigs() {
        try {
            new EapSessionConfig.Builder().build();
            fail("build() should throw an IllegalStateException if no EAP methods are configured");
        } catch (IllegalStateException expected) {
        }
    }
}
