/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.net.wifi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.wifi.WifiEnterpriseConfig.Eap;
import android.net.wifi.WifiEnterpriseConfig.Phase2;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.Bundle;
import android.os.Parcel;
import android.security.Credentials;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link android.net.wifi.WifiEnterpriseConfig}.
 */
@SmallTest
public class WifiEnterpriseConfigTest {
    // Maintain a ground truth of the keystore uri prefix which is expected by wpa_supplicant.
    public static final String KEYSTORE_URI = "keystore://";
    public static final String CA_CERT_PREFIX = KEYSTORE_URI + Credentials.CA_CERTIFICATE;
    public static final String KEYSTORES_URI = "keystores://";
    private static final String TEST_DOMAIN_SUFFIX_MATCH = "domainSuffixMatch";
    private static final String TEST_ALT_SUBJECT_MATCH = "DNS:server.test.com";
    private static final String TEST_DECORATED_IDENTITY_PREFIX = "androidwifi.dev!";
    private static final long TEST_SELECTED_RCOI = 0xcafeL;

    private WifiEnterpriseConfig mEnterpriseConfig;

    @Before
    public void setUp() throws Exception {
        mEnterpriseConfig = new WifiEnterpriseConfig();
    }

    @Test
    public void testGetEmptyCaCertificate() {
        // A newly-constructed WifiEnterpriseConfig object should have no CA certificate.
        assertNull(mEnterpriseConfig.getCaCertificate());
        assertNull(mEnterpriseConfig.getCaCertificates());
        // Setting CA certificate to null explicitly.
        mEnterpriseConfig.setCaCertificate(null);
        assertNull(mEnterpriseConfig.getCaCertificate());
        // Setting CA certificate to null using setCaCertificates().
        mEnterpriseConfig.setCaCertificates(null);
        assertNull(mEnterpriseConfig.getCaCertificates());
        // Setting CA certificate to zero-length array.
        mEnterpriseConfig.setCaCertificates(new X509Certificate[0]);
        assertNull(mEnterpriseConfig.getCaCertificates());
    }

    @Test
    public void testSetGetSingleCaCertificate() {
        X509Certificate cert0 = FakeKeys.CA_CERT0;
        mEnterpriseConfig.setCaCertificate(cert0);
        assertEquals(mEnterpriseConfig.getCaCertificate(), cert0);
    }

    @Test
    public void testSetGetMultipleCaCertificates() {
        X509Certificate cert0 = FakeKeys.CA_CERT0;
        X509Certificate cert1 = FakeKeys.CA_CERT1;
        mEnterpriseConfig.setCaCertificates(new X509Certificate[] {cert0, cert1});
        X509Certificate[] result = mEnterpriseConfig.getCaCertificates();
        assertEquals(result.length, 2);
        assertTrue(result[0] == cert0 && result[1] == cert1);
    }

    @Test
    public void testSetGetInvalidNumberOfCaCertificates() {
        // Maximum number of CA certificates is 100.
        X509Certificate[] invalidCaCertList = new X509Certificate[105];
        Arrays.fill(invalidCaCertList, FakeKeys.CA_CERT0);
        assertThrows(IllegalArgumentException.class, () -> {
            mEnterpriseConfig.setCaCertificates(invalidCaCertList);
        });
        assertEquals(null, mEnterpriseConfig.getCaCertificates());
    }

    @Test
    public void testSetClientKeyEntryWithNull() {
        mEnterpriseConfig.setClientKeyEntry(null, null);
        assertNull(mEnterpriseConfig.getClientCertificateChain());
        assertNull(mEnterpriseConfig.getClientCertificate());
        mEnterpriseConfig.setClientKeyEntryWithCertificateChain(null, null);
        assertNull(mEnterpriseConfig.getClientCertificateChain());
        assertNull(mEnterpriseConfig.getClientCertificate());

        // Setting the client certificate to null should clear the existing chain.
        PrivateKey clientKey = FakeKeys.RSA_KEY1;
        X509Certificate clientCert0 = FakeKeys.CLIENT_CERT;
        X509Certificate clientCert1 = FakeKeys.CA_CERT1;
        mEnterpriseConfig.setClientKeyEntry(clientKey, clientCert0);
        assertNotNull(mEnterpriseConfig.getClientCertificate());
        mEnterpriseConfig.setClientKeyEntry(null, null);
        assertNull(mEnterpriseConfig.getClientCertificate());
        assertNull(mEnterpriseConfig.getClientCertificateChain());

        // Setting the chain to null should clear the existing chain.
        X509Certificate[] clientChain = new X509Certificate[] {clientCert0, clientCert1};
        mEnterpriseConfig.setClientKeyEntryWithCertificateChain(clientKey, clientChain);
        assertNotNull(mEnterpriseConfig.getClientCertificateChain());
        mEnterpriseConfig.setClientKeyEntryWithCertificateChain(null, null);
        assertNull(mEnterpriseConfig.getClientCertificate());
        assertNull(mEnterpriseConfig.getClientCertificateChain());
    }

    @Test
    public void testSetClientCertificateChain() {
        PrivateKey clientKey = FakeKeys.RSA_KEY1;
        X509Certificate cert0 = FakeKeys.CLIENT_CERT;
        X509Certificate cert1 = FakeKeys.CA_CERT1;
        X509Certificate[] clientChain = new X509Certificate[] {cert0, cert1};
        mEnterpriseConfig.setClientKeyEntryWithCertificateChain(clientKey, clientChain);
        X509Certificate[] result = mEnterpriseConfig.getClientCertificateChain();
        assertEquals(result.length, 2);
        assertTrue(result[0] == cert0 && result[1] == cert1);
        assertTrue(mEnterpriseConfig.getClientCertificate() == cert0);
    }

    @Test
    public void testSetGetClientKeyPairAlias() {
        assumeTrue(SdkLevel.isAtLeastS());

        final String alias = "alias";
        mEnterpriseConfig.setClientKeyPairAlias(alias);
        assertEquals(alias, mEnterpriseConfig.getClientKeyPairAlias());
        assertEquals(alias, mEnterpriseConfig.getClientKeyPairAliasInternal());

        // Alias should have a maximum length of 256.
        final String invalidAlias = "*".repeat(1000);
        assertThrows(IllegalArgumentException.class, () -> {
            mEnterpriseConfig.setClientKeyPairAlias(invalidAlias);
        });
    }

    private boolean isClientCertificateChainInvalid(X509Certificate[] clientChain) {
        boolean exceptionThrown = false;
        try {
            PrivateKey clientKey = FakeKeys.RSA_KEY1;
            mEnterpriseConfig.setClientKeyEntryWithCertificateChain(clientKey, clientChain);
        } catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        return exceptionThrown;
    }

    @Test
    public void testSetInvalidClientCertificateChain() {
        X509Certificate clientCert = FakeKeys.CLIENT_CERT;
        X509Certificate caCert = FakeKeys.CA_CERT1;
        assertTrue("Invalid client certificate",
                isClientCertificateChainInvalid(new X509Certificate[] {caCert, caCert}));
        assertTrue("Invalid CA certificate",
                isClientCertificateChainInvalid(new X509Certificate[] {clientCert, clientCert}));
        assertTrue("Both certificates invalid",
                isClientCertificateChainInvalid(new X509Certificate[] {caCert, clientCert}));
        assertTrue("Certificate chain contains too many elements",
                isClientCertificateChainInvalid(new X509Certificate[] {
                        clientCert, clientCert, clientCert, clientCert, caCert, caCert}));
    }

    @Test
    public void testSaveSingleCaCertificateAlias() {
        final String alias = "single_alias 0";
        mEnterpriseConfig.setCaCertificateAliases(new String[] {alias});
        assertEquals(getCaCertField(), CA_CERT_PREFIX + alias);
    }

    @Test
    public void testLoadSingleCaCertificateAlias() {
        final String alias = "single_alias 1";
        setCaCertField(CA_CERT_PREFIX + alias);
        String[] aliases = mEnterpriseConfig.getCaCertificateAliases();
        assertEquals(aliases.length, 1);
        assertEquals(aliases[0], alias);
    }

    @Test
    public void testSaveMultipleCaCertificates() {
        final String alias0 = "single_alias 0";
        final String alias1 = "single_alias 1";
        mEnterpriseConfig.setCaCertificateAliases(new String[] {alias0, alias1});
        assertEquals(getCaCertField(), String.format("%s%s %s",
                KEYSTORES_URI,
                WifiEnterpriseConfig.encodeCaCertificateAlias(Credentials.CA_CERTIFICATE + alias0),
                WifiEnterpriseConfig.encodeCaCertificateAlias(Credentials.CA_CERTIFICATE + alias1)));
    }

    @Test
    public void testSetStrictConservativePeerMode() {
        assertFalse(mEnterpriseConfig.getStrictConservativePeerMode());

        mEnterpriseConfig.setStrictConservativePeerMode(true);

        assertTrue(mEnterpriseConfig.getStrictConservativePeerMode());
    }

    @Test
    public void testLoadMultipleCaCertificates() {
        final String alias0 = "single_alias 0";
        final String alias1 = "single_alias 1";
        setCaCertField(String.format("%s%s %s",
                KEYSTORES_URI,
                WifiEnterpriseConfig.encodeCaCertificateAlias(Credentials.CA_CERTIFICATE + alias0),
                WifiEnterpriseConfig.encodeCaCertificateAlias(Credentials.CA_CERTIFICATE + alias1)));
        String[] aliases = mEnterpriseConfig.getCaCertificateAliases();
        assertEquals(aliases.length, 2);
        assertEquals(aliases[0], alias0);
        assertEquals(aliases[1], alias1);
    }

    private String getCaCertField() {
        return mEnterpriseConfig.getFieldValue(WifiEnterpriseConfig.CA_CERT_KEY);
    }

    private void setCaCertField(String value) {
        mEnterpriseConfig.setFieldValue(WifiEnterpriseConfig.CA_CERT_KEY, value);
    }

    // Retrieves the value for a specific key supplied to wpa_supplicant.
    private class SupplicantConfigExtractor implements WifiEnterpriseConfig.SupplicantSaver {
        private String mValue = null;
        private String mKey;

        SupplicantConfigExtractor(String key) {
            mKey = key;
        }

        @Override
        public boolean saveValue(String key, String value) {
            if (key.equals(mKey)) {
                mValue = value;
            }
            return true;
        }

        public String getValue() {
            return mValue;
        }
    }

    private String getSupplicantEapMethod() {
        SupplicantConfigExtractor entryExtractor = new SupplicantConfigExtractor(
                WifiEnterpriseConfig.EAP_KEY);
        mEnterpriseConfig.saveToSupplicant(entryExtractor);
        return entryExtractor.getValue();
    }

    private String getSupplicantPhase2Method() {
        SupplicantConfigExtractor entryExtractor = new SupplicantConfigExtractor(
                WifiEnterpriseConfig.PHASE2_KEY);
        mEnterpriseConfig.saveToSupplicant(entryExtractor);
        return entryExtractor.getValue();
    }

    /** Verifies the default value for EAP outer and inner methods */
    @Test
    public void eapInnerDefault() {
        assertEquals(null, getSupplicantEapMethod());
        assertEquals(null, getSupplicantPhase2Method());
    }

    /** Verifies that the EAP inner method is reset when we switch to TLS */
    @Test
    public void eapPhase2MethodForTls() {
        // Initially select an EAP method that supports an phase2.
        mEnterpriseConfig.setEapMethod(Eap.PEAP);
        mEnterpriseConfig.setPhase2Method(Phase2.MSCHAPV2);
        assertEquals("PEAP", getSupplicantEapMethod());
        assertEquals("\"auth=MSCHAPV2\"", getSupplicantPhase2Method());

        // Change the EAP method to another type which supports a phase2.
        mEnterpriseConfig.setEapMethod(Eap.TTLS);
        assertEquals("TTLS", getSupplicantEapMethod());
        assertEquals("\"auth=MSCHAPV2\"", getSupplicantPhase2Method());

        // Change the EAP method to TLS which does not support a phase2.
        mEnterpriseConfig.setEapMethod(Eap.TLS);
        assertEquals(null, getSupplicantPhase2Method());
    }

    /** Verfies that the EAP inner method is reset when we switch phase2 to NONE */
    @Test
    public void eapPhase2None() {
        // Initially select an EAP method that supports an phase2.
        mEnterpriseConfig.setEapMethod(Eap.PEAP);
        mEnterpriseConfig.setPhase2Method(Phase2.MSCHAPV2);
        assertEquals("PEAP", getSupplicantEapMethod());
        assertEquals("\"auth=MSCHAPV2\"", getSupplicantPhase2Method());

        // Change the phase2 method to NONE and ensure the value is cleared.
        mEnterpriseConfig.setPhase2Method(Phase2.NONE);
        assertEquals(null, getSupplicantPhase2Method());
    }

    /** Verfies that the correct "autheap" parameter is supplied for TTLS/GTC. */
    @Test
    public void peapGtcToTtls() {
        mEnterpriseConfig.setEapMethod(Eap.PEAP);
        mEnterpriseConfig.setPhase2Method(Phase2.GTC);
        assertEquals("PEAP", getSupplicantEapMethod());
        assertEquals("\"auth=GTC\"", getSupplicantPhase2Method());

        mEnterpriseConfig.setEapMethod(Eap.TTLS);
        assertEquals("TTLS", getSupplicantEapMethod());
        assertEquals("\"autheap=GTC\"", getSupplicantPhase2Method());
    }

    /** Verfies that the correct "auth" parameter is supplied for PEAP/GTC. */
    @Test
    public void ttlsGtcToPeap() {
        mEnterpriseConfig.setEapMethod(Eap.TTLS);
        mEnterpriseConfig.setPhase2Method(Phase2.GTC);
        assertEquals("TTLS", getSupplicantEapMethod());
        assertEquals("\"autheap=GTC\"", getSupplicantPhase2Method());

        mEnterpriseConfig.setEapMethod(Eap.PEAP);
        assertEquals("PEAP", getSupplicantEapMethod());
        assertEquals("\"auth=GTC\"", getSupplicantPhase2Method());
    }

    /** Verfies PEAP/SIM, PEAP/AKA, PEAP/AKA'. */
    @Test
    public void peapSimAkaAkaPrime() {
        mEnterpriseConfig.setEapMethod(Eap.PEAP);
        mEnterpriseConfig.setPhase2Method(Phase2.SIM);
        assertEquals("PEAP", getSupplicantEapMethod());
        assertEquals("\"auth=SIM\"", getSupplicantPhase2Method());

        mEnterpriseConfig.setPhase2Method(Phase2.AKA);
        assertEquals("\"auth=AKA\"", getSupplicantPhase2Method());

        mEnterpriseConfig.setPhase2Method(Phase2.AKA_PRIME);
        assertEquals("\"auth=AKA'\"", getSupplicantPhase2Method());
    }

    /**
     * Verifies that the copy constructor preseves both the masked password and inner method
     * information.
     */
    @Test
    public void copyConstructor() {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setPassword("*");
        enterpriseConfig.setEapMethod(Eap.TTLS);
        enterpriseConfig.setPhase2Method(Phase2.GTC);
        mEnterpriseConfig = new WifiEnterpriseConfig(enterpriseConfig);
        assertEquals("TTLS", getSupplicantEapMethod());
        assertEquals("\"autheap=GTC\"", getSupplicantPhase2Method());
        assertEquals("*", mEnterpriseConfig.getPassword());
    }

    /**
     * Verifies that the copy from external ignores masked passwords and preserves the
     * inner method information.
     */
    @Test
    public void copyFromExternal() {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setPassword("*");
        enterpriseConfig.setEapMethod(Eap.TTLS);
        enterpriseConfig.setPhase2Method(Phase2.GTC);
        enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS);
        mEnterpriseConfig = new WifiEnterpriseConfig();
        mEnterpriseConfig.copyFromExternal(enterpriseConfig, "*");
        assertEquals("TTLS", getSupplicantEapMethod());
        assertEquals("\"autheap=GTC\"", getSupplicantPhase2Method());
        assertNotEquals("*", mEnterpriseConfig.getPassword());
        assertEquals(enterpriseConfig.getOcsp(), mEnterpriseConfig.getOcsp());
    }

    /** Verfies that parceling a WifiEnterpriseConfig preseves method information. */
    @Test
    public void parcelConstructor() {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(Eap.TTLS);
        enterpriseConfig.setPhase2Method(Phase2.GTC);
        Parcel parcel = Parcel.obtain();
        enterpriseConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);  // Allow parcel to be read from the beginning.
        mEnterpriseConfig = WifiEnterpriseConfig.CREATOR.createFromParcel(parcel);
        assertEquals("TTLS", getSupplicantEapMethod());
        assertEquals("\"autheap=GTC\"", getSupplicantPhase2Method());
    }

    /**
     * Verifies that parceling a WifiEnterpriseConfig preserves the key
     * and certificates information.
     */
    @Test
    public void parcelConfigWithKeyAndCerts() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        PrivateKey clientKey = FakeKeys.RSA_KEY1;
        X509Certificate clientCert = FakeKeys.CLIENT_CERT;
        X509Certificate[] caCerts = new X509Certificate[] {FakeKeys.CA_CERT0, FakeKeys.CA_CERT1};
        enterpriseConfig.setClientKeyEntry(clientKey, clientCert);
        enterpriseConfig.setCaCertificates(caCerts);
        Parcel parcel = Parcel.obtain();
        enterpriseConfig.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);  // Allow parcel to be read from the beginning.
        mEnterpriseConfig = WifiEnterpriseConfig.CREATOR.createFromParcel(parcel);
        PrivateKey actualClientKey = mEnterpriseConfig.getClientPrivateKey();
        X509Certificate actualClientCert = mEnterpriseConfig.getClientCertificate();
        X509Certificate[] actualCaCerts = mEnterpriseConfig.getCaCertificates();

        /* Verify client private key. */
        assertNotNull(actualClientKey);
        assertEquals(clientKey.getAlgorithm(), actualClientKey.getAlgorithm());
        assertArrayEquals(clientKey.getEncoded(), actualClientKey.getEncoded());

        /* Verify client certificate. */
        assertNotNull(actualClientCert);
        assertArrayEquals(clientCert.getEncoded(), actualClientCert.getEncoded());

        /* Verify CA certificates. */
        assertNotNull(actualCaCerts);
        assertEquals(caCerts.length, actualCaCerts.length);
        for (int i = 0; i < caCerts.length; i++) {
            assertNotNull(actualCaCerts[i]);
            assertArrayEquals(caCerts[i].getEncoded(), actualCaCerts[i].getEncoded());
        }
    }

    /** Verifies proper operation of the getKeyId() method. */
    @Test
    public void getKeyId() {
        assertEquals("NULL", mEnterpriseConfig.getKeyId(null));
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setEapMethod(Eap.TTLS);
        enterpriseConfig.setPhase2Method(Phase2.GTC);
        assertEquals("TTLS_GTC", mEnterpriseConfig.getKeyId(enterpriseConfig));
        mEnterpriseConfig.setEapMethod(Eap.PEAP);
        mEnterpriseConfig.setPhase2Method(Phase2.MSCHAPV2);
        assertEquals("PEAP_MSCHAPV2", mEnterpriseConfig.getKeyId(enterpriseConfig));
    }

    /** Verifies that passwords are not displayed in toString. */
    @Test
    public void passwordNotInToString() {
        String password = "supersecret";
        mEnterpriseConfig.setPassword(password);
        assertFalse(mEnterpriseConfig.toString().contains(password));
    }

    /** Verifies that certificate ownership flag is set correctly */
    @Test
    public void testIsAppInstalledDeviceKeyAndCert() {
        // First make sure that app didn't install anything
        assertFalse(mEnterpriseConfig.isAppInstalledDeviceKeyAndCert());
        assertFalse(mEnterpriseConfig.isAppInstalledCaCert());

        // Then app loads keys via the enterprise config API
        PrivateKey clientKey = FakeKeys.RSA_KEY1;
        X509Certificate cert0 = FakeKeys.CLIENT_CERT;
        X509Certificate cert1 = FakeKeys.CA_CERT1;
        X509Certificate[] clientChain = new X509Certificate[] {cert0, cert1};
        mEnterpriseConfig.setClientKeyEntryWithCertificateChain(clientKey, clientChain);
        X509Certificate[] result = mEnterpriseConfig.getClientCertificateChain();
        assertEquals(result.length, 2);
        assertTrue(result[0] == cert0 && result[1] == cert1);
        assertTrue(mEnterpriseConfig.getClientCertificate() == cert0);

        // Make sure it is the owner now
        assertTrue(mEnterpriseConfig.isAppInstalledDeviceKeyAndCert());
        assertFalse(mEnterpriseConfig.isAppInstalledCaCert());
    }

    /** Verifies that certificate ownership flag is set correctly */
    @Test
    public void testIsAppInstalledCaCert() {
        // First make sure that app didn't install anything
        assertFalse(mEnterpriseConfig.isAppInstalledDeviceKeyAndCert());
        assertFalse(mEnterpriseConfig.isAppInstalledCaCert());

        // Then app loads CA cert via the enterprise config API
        X509Certificate cert = FakeKeys.CA_CERT1;
        mEnterpriseConfig.setCaCertificate(cert);
        X509Certificate result = mEnterpriseConfig.getCaCertificate();
        assertTrue(result == cert);

        // Make sure it is the owner now
        assertFalse(mEnterpriseConfig.isAppInstalledDeviceKeyAndCert());
        assertTrue(mEnterpriseConfig.isAppInstalledCaCert());
    }

    /** Verifies that certificate ownership flag is set correctly */
    @Test
    public void testIsAppInstalledCaCerts() {
        // First make sure that app didn't install anything
        assertFalse(mEnterpriseConfig.isAppInstalledDeviceKeyAndCert());
        assertFalse(mEnterpriseConfig.isAppInstalledCaCert());

        // Then app loads CA cert via the enterprise config API
        X509Certificate cert0 = FakeKeys.CA_CERT0;
        X509Certificate cert1 = FakeKeys.CA_CERT1;
        X509Certificate[] cert = new X509Certificate[] {cert0, cert1};

        mEnterpriseConfig.setCaCertificates(cert);
        X509Certificate[] result = mEnterpriseConfig.getCaCertificates();
        assertEquals(result.length, 2);
        assertTrue(result[0] == cert0 && result[1] == cert1);
//        assertTrue(mEnterpriseConfig.getClientCertificate() == cert0);

        // Make sure it is the owner now
        assertFalse(mEnterpriseConfig.isAppInstalledDeviceKeyAndCert());
        assertTrue(mEnterpriseConfig.isAppInstalledCaCert());
    }

    /** Verifies that OCSP value is set correctly. */
    @Test
    public void testOcspSetGet() throws Exception {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();

        enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_NONE);
        assertEquals(WifiEnterpriseConfig.OCSP_NONE, enterpriseConfig.getOcsp());

        enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS);
        assertEquals(WifiEnterpriseConfig.OCSP_REQUIRE_CERT_STATUS, enterpriseConfig.getOcsp());

        enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_REQUEST_CERT_STATUS);
        assertEquals(WifiEnterpriseConfig.OCSP_REQUEST_CERT_STATUS, enterpriseConfig.getOcsp());

        enterpriseConfig.setOcsp(WifiEnterpriseConfig.OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS);
        assertEquals(WifiEnterpriseConfig.OCSP_REQUIRE_ALL_NON_TRUSTED_CERTS_STATUS,
                enterpriseConfig.getOcsp());
    }

    /** Verifies that an exception is thrown when invalid OCSP is set. */
    @Test
    public void testInvalidOcspValue() {
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        try {
            enterpriseConfig.setOcsp(-1);
            fail("Should raise an IllegalArgumentException here.");
        } catch (IllegalArgumentException e) {
            // expected exception.
        }
    }

    /** Verifies that the EAP inner method is reset when we switch to Unauth-TLS */
    @Test
    public void eapPhase2MethodForUnauthTls() {
        // Initially select an EAP method that supports an phase2.
        mEnterpriseConfig.setEapMethod(Eap.PEAP);
        mEnterpriseConfig.setPhase2Method(Phase2.MSCHAPV2);
        assertEquals("PEAP", getSupplicantEapMethod());
        assertEquals("\"auth=MSCHAPV2\"", getSupplicantPhase2Method());

        // Change the EAP method to another type which supports a phase2.
        mEnterpriseConfig.setEapMethod(Eap.TTLS);
        assertEquals("TTLS", getSupplicantEapMethod());
        assertEquals("\"auth=MSCHAPV2\"", getSupplicantPhase2Method());

        // Change the EAP method to Unauth-TLS which does not support a phase2.
        mEnterpriseConfig.setEapMethod(Eap.UNAUTH_TLS);
        assertEquals(null, getSupplicantPhase2Method());
    }

    @Test
    public void testIsEnterpriseConfigServerCertNotEnabled() {
        WifiEnterpriseConfig baseConfig = new WifiEnterpriseConfig();
        baseConfig.setEapMethod(Eap.PEAP);
        baseConfig.setPhase2Method(Phase2.MSCHAPV2);
        assertTrue(baseConfig.isEapMethodServerCertUsed());
        assertFalse(baseConfig.isServerCertValidationEnabled());

        WifiEnterpriseConfig noMatchConfig = new WifiEnterpriseConfig(baseConfig);
        noMatchConfig.setCaCertificate(FakeKeys.CA_CERT0);
        // Missing match disables validation.
        assertTrue(baseConfig.isEapMethodServerCertUsed());
        assertFalse(baseConfig.isServerCertValidationEnabled());

        WifiEnterpriseConfig noCaConfig = new WifiEnterpriseConfig(baseConfig);
        noCaConfig.setDomainSuffixMatch(TEST_DOMAIN_SUFFIX_MATCH);
        // Missing CA certificate disables validation.
        assertTrue(baseConfig.isEapMethodServerCertUsed());
        assertFalse(baseConfig.isServerCertValidationEnabled());

        WifiEnterpriseConfig noValidationConfig = new WifiEnterpriseConfig();
        noValidationConfig.setEapMethod(Eap.AKA);
        assertFalse(noValidationConfig.isEapMethodServerCertUsed());
    }

    @Test
    public void testIsEnterpriseConfigServerCertEnabledWithPeap() {
        testIsEnterpriseConfigServerCertEnabled(Eap.PEAP);
    }

    @Test
    public void testIsEnterpriseConfigServerCertEnabledWithTls() {
        testIsEnterpriseConfigServerCertEnabled(Eap.TLS);
    }

    @Test
    public void testIsEnterpriseConfigServerCertEnabledWithTTLS() {
        testIsEnterpriseConfigServerCertEnabled(Eap.TTLS);
    }

    @Test
    public void testSetGetDecoratedIdentityPrefix() {
        assumeTrue(SdkLevel.isAtLeastS());
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();

        assertNull(config.getDecoratedIdentityPrefix());
        config.setDecoratedIdentityPrefix(TEST_DECORATED_IDENTITY_PREFIX);
        assertEquals(TEST_DECORATED_IDENTITY_PREFIX, config.getDecoratedIdentityPrefix());
    }

    @Test
    public void testTrustOnFirstUse() {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();

        assertFalse(config.isTrustOnFirstUseEnabled());
        config.enableTrustOnFirstUse(true);
        assertTrue(config.isTrustOnFirstUseEnabled());
        config.enableTrustOnFirstUse(false);
        assertFalse(config.isTrustOnFirstUseEnabled());
    }

    @Test
    public void testHasCaCertificate() {
        assumeTrue(SdkLevel.isAtLeastT());
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();
        assertFalse(config.hasCaCertificate());
        config.setCaPath("/tmp/testCa.cert");
        assertTrue(config.hasCaCertificate());

        config = new WifiEnterpriseConfig();
        assertFalse(config.hasCaCertificate());
        config.setCaCertificate(FakeKeys.CA_CERT0);
        assertTrue(config.hasCaCertificate());

        config = new WifiEnterpriseConfig();
        assertFalse(config.hasCaCertificate());
        config.setCaCertificateAliases(new String[] {"single_alias 0"});
        assertTrue(config.hasCaCertificate());
    }

    @Test
    public void testUserApproveNoCaCert() {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();

        assertFalse(config.isUserApproveNoCaCert());
        config.setUserApproveNoCaCert(true);
        assertTrue(config.isUserApproveNoCaCert());
        config.setUserApproveNoCaCert(false);
        assertFalse(config.isUserApproveNoCaCert());
    }

    /**
     * Verify that the set decorated identity prefix doesn't accept a malformed input.
     *
     * @throws Exception
     */
    @Test (expected = IllegalArgumentException.class)
    public void testSetDecoratedIdentityPrefixWithInvalidValue() {
        assumeTrue(SdkLevel.isAtLeastS());
        PasspointConfiguration config = new PasspointConfiguration();

        config.setDecoratedIdentityPrefix(TEST_DECORATED_IDENTITY_PREFIX.replace('!', 'a'));
    }

    @Test
    public void testSetGetSelectedRcoi() {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();

        assertEquals(0, config.getSelectedRcoi());
        config.setSelectedRcoi(TEST_SELECTED_RCOI);
        assertEquals(TEST_SELECTED_RCOI, config.getSelectedRcoi());
    }

    private void testIsEnterpriseConfigServerCertEnabled(int eapMethod) {
        WifiEnterpriseConfig configWithCertAndDomainSuffixMatch = createEnterpriseConfig(eapMethod,
                Phase2.NONE, FakeKeys.CA_CERT0, null, TEST_DOMAIN_SUFFIX_MATCH, null);
        assertTrue(configWithCertAndDomainSuffixMatch.isEapMethodServerCertUsed());
        assertTrue(configWithCertAndDomainSuffixMatch.isServerCertValidationEnabled());

        WifiEnterpriseConfig configWithCertAndAltSubjectMatch = createEnterpriseConfig(eapMethod,
                Phase2.NONE, FakeKeys.CA_CERT0, null, null, TEST_ALT_SUBJECT_MATCH);
        assertTrue(configWithCertAndAltSubjectMatch.isEapMethodServerCertUsed());
        assertTrue(configWithCertAndAltSubjectMatch.isServerCertValidationEnabled());

        WifiEnterpriseConfig configWithAliasAndDomainSuffixMatch = createEnterpriseConfig(eapMethod,
                Phase2.NONE, null, new String[]{"alias1", "alisa2"}, TEST_DOMAIN_SUFFIX_MATCH,
                null);
        assertTrue(configWithAliasAndDomainSuffixMatch.isEapMethodServerCertUsed());
        assertTrue(configWithAliasAndDomainSuffixMatch.isServerCertValidationEnabled());

        WifiEnterpriseConfig configWithAliasAndAltSubjectMatch = createEnterpriseConfig(eapMethod,
                Phase2.NONE, null, new String[]{"alias1", "alisa2"}, null, TEST_ALT_SUBJECT_MATCH);
        assertTrue(configWithAliasAndAltSubjectMatch.isEapMethodServerCertUsed());
        assertTrue(configWithAliasAndAltSubjectMatch.isServerCertValidationEnabled());
    }

    private WifiEnterpriseConfig createEnterpriseConfig(int eapMethod, int phase2Method,
            X509Certificate caCertificate, String[] aliases, String domainSuffixMatch,
            String altSubjectMatch) {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();
        config.setEapMethod(eapMethod);
        config.setPhase2Method(phase2Method);
        config.setCaCertificate(caCertificate);
        config.setCaCertificateAliases(aliases);
        config.setDomainSuffixMatch(domainSuffixMatch);
        config.setAltSubjectMatch(altSubjectMatch);
        return config;
    }

    /**
     * Verify that setCaCertificate() raises IllegalArgumentException
     * for a self-signed certificate.
     *
     * @throws IllegalArgumentException
     */
    @Test (expected = IllegalArgumentException.class)
    public void testSetCaCertificateExceptionWithSelfSignedCert() throws Exception {
        X509Certificate mockCert = mock(X509Certificate.class);
        when(mockCert.getBasicConstraints()).thenReturn(-1);

        mEnterpriseConfig.setCaCertificate(mockCert);
    }

    /**
     * Verify that setCaCertificateForTrustOnFirstUse sunny case.
     */
    @Test
    public void testSetCaCertificateForTrustOnFirstUseSuccess() throws Exception {
        X509Certificate mockCert = mock(X509Certificate.class);
        when(mockCert.getBasicConstraints()).thenReturn(-1);

        mEnterpriseConfig.enableTrustOnFirstUse(true);

        mEnterpriseConfig.setCaCertificateForTrustOnFirstUse(mockCert);
        assertEquals(mEnterpriseConfig.getCaCertificate(), mockCert);
    }

    /**
     * Verify that setCaCertificateForTrustOnFirstUse() raises IllegalArgumentException
     * when Trust on First Use is not enabled.
     *
     * @throws IllegalArgumentException
     */
    @Test (expected = IllegalArgumentException.class)
    public void testSetCaCertificateForTrustOnFirstUseExceptionWithNoTofuEnabled()
            throws Exception {
        X509Certificate mockCert = mock(X509Certificate.class);
        when(mockCert.getBasicConstraints()).thenReturn(-1);

        mEnterpriseConfig.enableTrustOnFirstUse(false);

        mEnterpriseConfig.setCaCertificateForTrustOnFirstUse(mockCert);
    }

    /**
     * Verify setMinimumTlsVersion sunny cases.
     */
    @Test
    public void testSetMinimumTlsVersionWithValidValues() throws Exception {
        for (int i = WifiEnterpriseConfig.TLS_VERSION_MIN;
                i <= WifiEnterpriseConfig.TLS_VERSION_MAX; i++) {
            mEnterpriseConfig.setMinimumTlsVersion(i);
            assertEquals(i, mEnterpriseConfig.getMinimumTlsVersion());
        }
    }

    /**
     * Verify that setMinimumTlsVersion() raises IllegalArgumentException when
     * an invalid TLS version is set.
     *
     * @throws IllegalArgumentException
     */
    @Test (expected = IllegalArgumentException.class)
    public void testSetMinimumTlsVersionWithVersionLargerThanMaxVersion() throws Exception {
        mEnterpriseConfig.setMinimumTlsVersion(WifiEnterpriseConfig.TLS_VERSION_MAX + 1);
    }

    /**
     * Verify that setMinimumTlsVersion() raises IllegalArgumentException when
     * an invalid TLS version is set.
     *
     * @throws IllegalArgumentException
     */
    @Test (expected = IllegalArgumentException.class)
    public void testSetMinimumTlsVersionWithVersionSmallerThanMinVersion() throws Exception {
        mEnterpriseConfig.setMinimumTlsVersion(WifiEnterpriseConfig.TLS_VERSION_MIN - 1);
    }

    /**
     * Verify that fields with unsupported keys cannot be set or retrieved.
     */
    @Test
    public void testCannotSetOrGetUnsupportedKeys() {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();
        String password = "somePassword";
        config.setFieldValue(WifiEnterpriseConfig.PASSWORD_KEY, password);
        assertEquals(password, config.getFieldValue(WifiEnterpriseConfig.PASSWORD_KEY));

        String invalidKey = "invalidKey";
        String invalidValue = "invalidValue";
        config.setFieldValue(invalidKey, invalidValue);
        assertEquals("", config.getFieldValue(invalidKey));
    }

    /**
     * Construct a WifiEnterpriceConfig parcel using the provided fields map.
     * Map is allowed to contain invalid keys.
     */
    private Parcel constructParcelWithFieldsMap(Map<String, String> fieldsMap) {
        Parcel parcel = Parcel.obtain();
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : fieldsMap.entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        parcel.writeBundle(bundle);

        // Use the default value for the remaining fields.
        parcel.writeInt(Eap.NONE);
        parcel.writeInt(Phase2.NONE);
        parcel.writeInt(0); // numCaCertificates
        parcel.writeString(null); // privateKey
        parcel.writeInt(0); // numClientCertificates
        parcel.writeString(""); // keyChainAlias
        parcel.writeBoolean(false); // isAppInstalledDeviceKeyAndCert
        parcel.writeBoolean(false); // isAppInstalledCaCert
        parcel.writeInt(0); // OSCP
        parcel.writeBoolean(false); // isTrustOnFirstUseEnabled
        parcel.writeBoolean(false); // userApproveNoCaCert
        parcel.writeInt(WifiEnterpriseConfig.TLS_V1_0);
        return parcel;
    }

    /**
     * Verify that unsupported keys are ignored during unparceling.
     */
    @Test
    public void testUnsupportedKeysIgnoredDuringUnparceling() {
        Map<String, String> fieldsMap = new HashMap<>();
        String password = "somePassword";
        fieldsMap.put(WifiEnterpriseConfig.PASSWORD_KEY, password);

        String invalidKey = "invalidKey";
        String invalidValue = "invalidValue";
        fieldsMap.put(invalidKey, invalidValue);

        // Invalid field should be ignored during unparceling.
        Parcel parcel = constructParcelWithFieldsMap(fieldsMap);
        parcel.setDataPosition(0);
        WifiEnterpriseConfig config = WifiEnterpriseConfig.CREATOR.createFromParcel(parcel);
        assertEquals(password, config.getPassword());
        assertEquals("", config.getFieldValue(invalidKey));
    }

    /**
     * Verify that fields with invalid field lengths cannot be set or retrieved.
     */
    @Test
    public void testCannotSetOrGetFieldsWithInvalidLength() {
        WifiEnterpriseConfig config = new WifiEnterpriseConfig();
        String password = "somePassword";
        config.setFieldValue(WifiEnterpriseConfig.PASSWORD_KEY, password);
        assertEquals(password, config.getFieldValue(WifiEnterpriseConfig.PASSWORD_KEY));

        // Value of OPP_KEY_CACHING is expected to have a length of 1.
        String invalidValue = "invalidValue";
        config.setFieldValue(WifiEnterpriseConfig.OPP_KEY_CACHING, invalidValue);
        assertEquals("", config.getFieldValue(WifiEnterpriseConfig.OPP_KEY_CACHING));
    }

    /**
     * Verify that field values with invalid lengths are ignored during unparceling.
     */
    @Test
    public void testFieldsWithInvalidLengthIgnoredDuringUnparceling() {
        Map<String, String> fieldsMap = new HashMap<>();
        String password = "somePassword";
        fieldsMap.put(WifiEnterpriseConfig.PASSWORD_KEY, password);

        // Value of OPP_KEY_CACHING is expected to have a length of 1.
        String invalidValue = "invalidValue";
        fieldsMap.put(WifiEnterpriseConfig.OPP_KEY_CACHING, invalidValue);

        // Invalid field should be ignored during unparceling.
        Parcel parcel = constructParcelWithFieldsMap(fieldsMap);
        parcel.setDataPosition(0);
        WifiEnterpriseConfig config = WifiEnterpriseConfig.CREATOR.createFromParcel(parcel);
        assertEquals(password, config.getFieldValue(WifiEnterpriseConfig.PASSWORD_KEY));
        assertEquals("", config.getFieldValue(WifiEnterpriseConfig.OPP_KEY_CACHING));
    }
}
