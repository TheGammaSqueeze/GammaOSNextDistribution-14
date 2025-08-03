/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.wifi.passpoint.cts;

import static org.junit.Assert.assertNotEquals;

import android.net.wifi.cts.FakeKeys;
import android.net.wifi.cts.WifiJUnit3TestBase;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SmallTest;
import androidx.test.filters.SdkSuppress;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@SmallTest
public class PasspointConfigurationTest extends WifiJUnit3TestBase {
    private static final int CERTIFICATE_FINGERPRINT_BYTES = 32;
    public static final int EAP_SIM = 18;
    public static final int EAP_TTLS = 21;
    private static final String TEST_DECORATED_IDENTITY_PREFIX = "androidwifi.dev!";

    /**
     * Verify that the unique identifier generated is identical for two instances
     */
    public void testEqualUniqueId() throws Exception {
        PasspointConfiguration config1 = createConfig();
        PasspointConfiguration config2 = createConfig();

        assertEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the unique identifier generated is the same for two instances with different
     * HomeSp node but same FQDN
     */
    public void testUniqueIdSameHomeSpSameFqdn() throws Exception {
        PasspointConfiguration config1 = createConfig();
        HomeSp homeSp = config1.getHomeSp();
        homeSp.setMatchAnyOis(new long[]{0x1000, 0x2000});

        // Modify config2's RCOIs and friendly name to a different set of values
        PasspointConfiguration config2 = createConfig();
        homeSp = config2.getHomeSp();

        homeSp.setRoamingConsortiumOis(new long[]{0xaa, 0xbb});
        homeSp.setFriendlyName("Some other name");
        homeSp.setOtherHomePartnersList(Arrays.asList("other-provider1", "other-provider2"));
        homeSp.setMatchAllOis(new long[]{0x1000, 0x2000});
        config2.setHomeSp(homeSp);

        assertEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the unique identifier generated is different for two instances with the same
     * HomeSp node but different FQDN
     */
    public void testUniqueIdDifferentHomeSpDifferentFqdn() throws Exception {
        PasspointConfiguration config1 = createConfig();

        // Modify config2's FQDN to a different value
        PasspointConfiguration config2 = createConfig();
        HomeSp homeSp = config2.getHomeSp();
        homeSp.setFqdn("fqdn2.com");
        config2.setHomeSp(homeSp);

        assertNotEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the unique identifier generated is different for two instances with different
     * SIM Credential node
     */
    public void testUniqueIdDifferentSimCredential() throws Exception {
        PasspointConfiguration config1 = createConfig();

        // Modify config2's realm and SIM credential to a different set of values
        PasspointConfiguration config2 = createConfig();
        Credential credential = config2.getCredential();
        credential.setRealm("realm2.example.com");
        credential.getSimCredential().setImsi("350460*");
        config2.setCredential(credential);

        assertNotEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the unique identifier generated is different for two instances with different
     * Realm in the Credential node
     */
    public void testUniqueIdDifferentRealm() throws Exception {
        PasspointConfiguration config1 = createConfig();

        // Modify config2's realm to a different set of values
        PasspointConfiguration config2 = createConfig();
        Credential credential = config2.getCredential();
        credential.setRealm("realm2.example.com");
        config2.setCredential(credential);

        assertNotEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the unique identifier generated is the same for two instances with different
     * password and same username in the User Credential node
     */
    public void testUniqueIdSameUserInUserCredential() throws Exception {
        PasspointConfiguration config1 = createConfig();
        Credential credential = createCredentialWithUserCredential("user", "passwd");
        config1.setCredential(credential);

        // Modify config2's Passpowrd to a different set of values
        PasspointConfiguration config2 = createConfig();
        credential = createCredentialWithUserCredential("user", "newpasswd");
        config2.setCredential(credential);

        assertEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the unique identifier generated is different for two instances with different
     * username in the User Credential node
     */
    public void testUniqueIdDifferentUserCredential() throws Exception {
        PasspointConfiguration config1 = createConfig();
        Credential credential = createCredentialWithUserCredential("user", "passwd");
        config1.setCredential(credential);

        // Modify config2's username to a different value
        PasspointConfiguration config2 = createConfig();
        credential = createCredentialWithUserCredential("user2", "passwd");
        config2.setCredential(credential);

        assertNotEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the unique identifier generated is different for two instances with different
     * Cert Credential node
     */
    public void testUniqueIdDifferentCertCredential() throws Exception {
        PasspointConfiguration config1 = createConfig();
        Credential credential = createCredentialWithCertificateCredential(true, true);
        config1.setCredential(credential);

        // Modify config2's cert credential to a different set of values
        PasspointConfiguration config2 = createConfig();
        credential = createCredentialWithCertificateCredential(false, false);
        config2.setCredential(credential);

        assertNotEquals(config1.getUniqueId(), config2.getUniqueId());
    }

    /**
     * Verify that the set and get decorated identity prefix methods work as expected.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testSetGetDecoratedIdentityPrefix() throws Exception {
        PasspointConfiguration config = createConfig();
        assertNull(config.getDecoratedIdentityPrefix());
        config.setDecoratedIdentityPrefix(TEST_DECORATED_IDENTITY_PREFIX);
        assertEquals(TEST_DECORATED_IDENTITY_PREFIX, config.getDecoratedIdentityPrefix());

    }

    /**
     * Helper function for generating user credential for testing.
     *
     * @return {@link Credential}
     */
    private static Credential createCredentialWithUserCredential(String username, String password) {
        Credential.UserCredential userCred = new Credential.UserCredential();
        userCred.setUsername(username);
        userCred.setPassword(password);
        userCred.setEapType(EAP_TTLS);
        userCred.setNonEapInnerMethod("MS-CHAP");
        return createCredential(userCred, null, null, null, null, FakeKeys.CA_CERT0);
    }

    /**
     * Helper function for generating Credential for testing.
     *
     * @param userCred               Instance of UserCredential
     * @param certCred               Instance of CertificateCredential
     * @param simCred                Instance of SimCredential
     * @param clientCertificateChain Chain of client certificates
     * @param clientPrivateKey       Client private key
     * @param caCerts                CA certificates
     * @return {@link Credential}
     */
    private static Credential createCredential(Credential.UserCredential userCred,
            Credential.CertificateCredential certCred,
            Credential.SimCredential simCred,
            X509Certificate[] clientCertificateChain, PrivateKey clientPrivateKey,
            X509Certificate... caCerts) {
        Credential cred = new Credential();
        cred.setRealm("realm");
        cred.setUserCredential(userCred);
        cred.setCertCredential(certCred);
        cred.setSimCredential(simCred);
        cred.setCaCertificate(caCerts[0]);
        cred.setClientCertificateChain(clientCertificateChain);
        cred.setClientPrivateKey(clientPrivateKey);
        return cred;
    }

    /**
     * Helper function for generating certificate credential for testing.
     *
     * @return {@link Credential}
     */
    private static Credential createCredentialWithCertificateCredential(Boolean useCaCert0,
            Boolean useCert0)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        Credential.CertificateCredential certCred = new Credential.CertificateCredential();
        certCred.setCertType("x509v3");
        if (useCert0) {
            certCred.setCertSha256Fingerprint(
                    MessageDigest.getInstance("SHA-256").digest(FakeKeys.CLIENT_CERT.getEncoded()));
        } else {
            certCred.setCertSha256Fingerprint(MessageDigest.getInstance("SHA-256")
                    .digest(FakeKeys.CLIENT_SUITE_B_RSA3072_CERT.getEncoded()));
        }
        return createCredential(null, certCred, null, new X509Certificate[]{FakeKeys.CLIENT_CERT},
                FakeKeys.RSA_KEY1, useCaCert0 ? FakeKeys.CA_CERT0 : FakeKeys.CA_CERT1);
    }

    /**
     * Helper function for creating a {@link PasspointConfiguration} for testing.
     *
     * @return {@link PasspointConfiguration}
     */
    private static PasspointConfiguration createConfig() {
        PasspointConfiguration config = new PasspointConfiguration();
        config.setHomeSp(createHomeSp());
        config.setCredential(createCredential());
        Map<String, byte[]> trustRootCertList = new HashMap<>();
        trustRootCertList.put("trustRoot.cert1.com",
                new byte[CERTIFICATE_FINGERPRINT_BYTES]);
        trustRootCertList.put("trustRoot.cert2.com",
                new byte[CERTIFICATE_FINGERPRINT_BYTES]);
        return config;
    }

    /**
     * Utility function for creating a {@link android.net.wifi.hotspot2.pps.HomeSp} for testing.
     *
     * @return {@link android.net.wifi.hotspot2.pps.HomeSp}
     */
    private static HomeSp createHomeSp() {
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendly name");
        homeSp.setRoamingConsortiumOis(new long[]{0x55, 0x66});
        return homeSp;
    }

    /**
     * Utility function for creating a {@link android.net.wifi.hotspot2.pps.Credential} for
     * testing..
     *
     * @return {@link android.net.wifi.hotspot2.pps.Credential}
     */
    private static Credential createCredential() {
        Credential cred = new Credential();
        cred.setRealm("realm");
        cred.setUserCredential(null);
        cred.setCertCredential(null);
        cred.setSimCredential(new Credential.SimCredential());
        cred.getSimCredential().setImsi("1234*");
        cred.getSimCredential().setEapType(EAP_SIM);
        cred.setCaCertificate(null);
        cred.setClientCertificateChain(null);
        cred.setClientPrivateKey(null);
        return cred;
    }
}
