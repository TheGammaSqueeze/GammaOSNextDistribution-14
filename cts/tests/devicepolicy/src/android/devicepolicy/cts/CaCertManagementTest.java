/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.net.http.X509TrustManagerExtensions;
import android.stats.devicepolicy.EventId;
import android.util.Base64;
import android.util.Base64InputStream;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.CaCertManagement;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FakeKeys;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

// These tests rely on the line "android:networkSecurityConfig="@xml/network_security_config"" in
// the <application> element in the manifest.
// TODO(b/205261115): Use a testapp and query for it rather than relying on the Manifest content
@RunWith(BedsteadJUnit4.class)
public final class CaCertManagementTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final byte[] CA_CERT_1 = FakeKeys.FAKE_RSA_1.caCertificate;
    private static final byte[] CA_CERT_2 = FakeKeys.FAKE_DSA_1.caCertificate;
    private static final String ALIAS = "alias";

    // Content from userkey.pem without the private key header and footer.
    private static final String TEST_KEY =
            "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBALCYprGsTU+5L3KM\n"
                    + "fhkm0gXM2xjGUH+543YLiMPGVr3eVS7biue1/tQlL+fJsw3rqsPKJe71RbVWlpqU\n"
                    + "mhegxG4s3IvGYVB0KZoRIjDKmnnvlx6nngL2ZJ8O27U42pHsw4z4MKlcQlWkjL3T\n"
                    + "9sV6zW2Wzri+f5mvzKjhnArbLktHAgMBAAECgYBlfVVPhtZnmuXJzzQpAEZzTugb\n"
                    + "tN1OimZO0RIocTQoqj4KT+HkiJOLGFQPwbtFpMre+q4SRqNpM/oZnI1yRtKcCmIc\n"
                    + "mZgkwJ2k6pdSxqO0ofxFFTdT9czJ3rCnqBHy1g6BqUQFXT4olcygkxUpKYUwzlz1\n"
                    + "oAl487CoPxyr4sVEAQJBANwiUOHcdGd2RoRILDzw5WOXWBoWPOKzX/K9wt0yL+mO\n"
                    + "wlFNFSymqo9eLheHcEq/VD9qK9rT700dCewJfWj6+bECQQDNXmWNYIxGii5NJilT\n"
                    + "OBOHiMD/F0NE178j+/kmacbhDJwpkbLYXaP8rW4+Iswrm4ORJ59lvjNuXaZ28+sx\n"
                    + "fFp3AkA6Z7Bl/IO135+eATgbgx6ZadIqObQ1wbm3Qbmtzl7/7KyJvZXcnuup1icM\n"
                    + "fxa//jtwB89S4+Ad6ZJ0WaA4dj5BAkEAuG7V9KmIULE388EZy8rIfyepa22Q0/qN\n"
                    + "hdt8XasRGHsio5Jdc0JlSz7ViqflhCQde/aBh/XQaoVgQeO8jKyI8QJBAJHekZDj\n"
                    + "WA0w1RsBVVReN1dVXgjm1CykeAT8Qx8TUmBUfiDX6w6+eGQjKtS7f4KC2IdRTV6+\n"
                    + "bDzDoHBChHNC9ms=\n";

    // Content from usercert.pem without the header and footer.
    private static final String TEST_CERT =
            "MIIDEjCCAfqgAwIBAgIBATANBgkqhkiG9w0BAQsFADBFMQswCQYDVQQGEwJBVTET\n"
                    + "MBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50ZXJuZXQgV2lkZ2l0cyBQ\n"
                    + "dHkgTHRkMB4XDTE1MDUwMTE2NTQwNVoXDTI1MDQyODE2NTQwNVowWzELMAkGA1UE\n"
                    + "BhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoMGEludGVybmV0IFdp\n"
                    + "ZGdpdHMgUHR5IEx0ZDEUMBIGA1UEAwwLY2xpZW50IGNlcnQwgZ8wDQYJKoZIhvcN\n"
                    + "AQEBBQADgY0AMIGJAoGBALCYprGsTU+5L3KMfhkm0gXM2xjGUH+543YLiMPGVr3e\n"
                    + "VS7biue1/tQlL+fJsw3rqsPKJe71RbVWlpqUmhegxG4s3IvGYVB0KZoRIjDKmnnv\n"
                    + "lx6nngL2ZJ8O27U42pHsw4z4MKlcQlWkjL3T9sV6zW2Wzri+f5mvzKjhnArbLktH\n"
                    + "AgMBAAGjezB5MAkGA1UdEwQCMAAwLAYJYIZIAYb4QgENBB8WHU9wZW5TU0wgR2Vu\n"
                    + "ZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBQ8GL+jKSarvTn9fVNA2AzjY7qq\n"
                    + "gjAfBgNVHSMEGDAWgBRzBBA5sNWyT/fK8GrhN3tOqO5tgjANBgkqhkiG9w0BAQsF\n"
                    + "AAOCAQEAgwQEd2bktIDZZi/UOwU1jJUgGq7NiuBDPHcqgzjxhGFLQ8SQAAP3v3PR\n"
                    + "mLzcfxsxnzGynqN5iHQT4rYXxxaqrp1iIdj9xl9Wl5FxjZgXITxhlRscOd/UOBvG\n"
                    + "oMrazVczjjdoRIFFnjtU3Jf0Mich68HD1Z0S3o7X6sDYh6FTVR5KbLcxbk6RcoG4\n"
                    + "VCI5boR5LUXgb5Ed5UxczxvN12S71fyxHYVpuuI0z0HTIbAxKeRw43I6HWOmR1/0\n"
                    + "G6byGCNL/1Fz7Y+264fGqABSNTKdZwIU2K4ANEH7F+9scnhoO6OBp+gjBe5O+7jb\n"
                    + "wZmUCAoTka4hmoaOCj7cqt/IkmxozQ==\n";

    private static final String TEST_CERT_WITH_HEADER_FOOTER =
            "-----BEGIN CERTIFICATE-----\n" + TEST_CERT + "-----END CERTIFICATE-----";
    private static final byte[] TEST_CERT_BYTES = TEST_CERT_WITH_HEADER_FOOTER.getBytes();
    private static final KeyStore sKeyStore;
    private static final Certificate sCertificate;

    static {
        try {
            sCertificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(TEST_CERT_BYTES));
        } catch (CertificateException e) {
            throw new NeneException("Unable to load generate certificate: " + e);
        }

        try {
            sKeyStore = KeyStore.getInstance("AndroidCAStore");
            sKeyStore.load(null, null);
        } catch (KeyStoreException | CertificateException | IOException |
                 NoSuchAlgorithmException e) {
            throw new NeneException("Unable to load keystore: " + e);
        }
    }


    @CanSetPolicyTest(policy = CaCertManagement.class)
    public void getInstalledCaCerts_doesNotReturnNull() throws Exception {
        assertThat(sDeviceState.dpc().devicePolicyManager().getInstalledCaCerts(
                sDeviceState.dpc().componentName())).isNotNull();
    }

    @CannotSetPolicyTest(policy = CaCertManagement.class)
    public void getInstalledCaCerts_invalidAdmin_throwsException() throws Exception {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().getInstalledCaCerts(
                        sDeviceState.dpc().componentName()));
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void installCaCert_caCertIsInstalled() throws Exception {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        try {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());

            boolean result = remoteDpm.installCaCert(
                    sDeviceState.dpc().componentName(), CA_CERT_1);

            assertThat(result).isTrue();
            assertCaCertInstalledForTheDpcAndLocally(CA_CERT_1);
        } finally {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
        }
    }

    @CannotSetPolicyTest(policy = CaCertManagement.class)
    public void installCaCert_invalidAdmin_throwsException() throws Exception {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().installCaCert(
                        sDeviceState.dpc().componentName(), CA_CERT_1));
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void installCaCert_logsEvent() throws Exception {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        try {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());

            try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
                remoteDpm.installCaCert(sDeviceState.dpc().componentName(), CA_CERT_1);

                assertThat(metrics.query()
                        .whereType().isEqualTo(EventId.INSTALL_CA_CERT_VALUE)
                        .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                        .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                ).wasLogged();
            }
        } finally {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
        }
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void uninstallCaCert_caCertIsNotInstalled() throws Exception {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        try {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
            remoteDpm.installCaCert(sDeviceState.dpc().componentName(), CA_CERT_1);

            remoteDpm.uninstallCaCert(sDeviceState.dpc().componentName(), CA_CERT_1);

            assertCaCertNotInstalledForTheDpcOrLocally(CA_CERT_1);
        } finally {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
        }
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void uninstallCaCert_otherCaCertsAreNotUninstalled() throws Exception {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        try {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
            remoteDpm.installCaCert(sDeviceState.dpc().componentName(), CA_CERT_1);
            remoteDpm.installCaCert(sDeviceState.dpc().componentName(), CA_CERT_2);

            remoteDpm.uninstallCaCert(sDeviceState.dpc().componentName(), CA_CERT_1);

            assertCaCertInstalledForTheDpcAndLocally(CA_CERT_2);
        } finally {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
        }
    }

    @CannotSetPolicyTest(policy = CaCertManagement.class)
    public void uninstallCaCert_invalidAdmin_throwsException() throws Exception {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();

        assertThrows(SecurityException.class,
                () -> remoteDpm.uninstallCaCert(sDeviceState.dpc().componentName(), CA_CERT_1));
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void uninstallCaCert_logsEvent() throws Exception {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        try {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
            try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
                remoteDpm.installCaCert(
                        sDeviceState.dpc().componentName(), CA_CERT_1);

                remoteDpm.uninstallCaCert(
                        sDeviceState.dpc().componentName(), CA_CERT_1);

                assertThat(metrics.query()
                        .whereType().isEqualTo(EventId.UNINSTALL_CA_CERTS_VALUE)
                        .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                        .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                ).wasLogged();
            }
        } finally {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
        }
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void uninstallAllUserCaCerts_uninstallsAllCaCerts()
            throws Exception {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        try {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
            remoteDpm.installCaCert(sDeviceState.dpc().componentName(), CA_CERT_1);
            remoteDpm.installCaCert(sDeviceState.dpc().componentName(), CA_CERT_2);

            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());

            assertCaCertNotInstalledForTheDpcOrLocally(CA_CERT_1);
            assertCaCertNotInstalledForTheDpcOrLocally(CA_CERT_2);
        } finally {
            remoteDpm.uninstallAllUserCaCerts(sDeviceState.dpc().componentName());
        }
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void installKeyPair_installsKeyPair() throws Exception {
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.decode(TEST_KEY, Base64.DEFAULT)));

        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                new Base64InputStream(new ByteArrayInputStream(TEST_CERT.getBytes()),
                        Base64.DEFAULT));

        try {
            boolean result = sDeviceState.dpc().devicePolicyManager()
                    .installKeyPair(
                            sDeviceState.dpc().componentName(), privateKey, certificate, ALIAS);
            assertThat(result).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .removeKeyPair(sDeviceState.dpc().componentName(), ALIAS);
        }
    }

    @CannotSetPolicyTest(policy = CaCertManagement.class)
    public void installKeyPair_invalidAdmin_throwsException() throws Exception {
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.decode(TEST_KEY, Base64.DEFAULT)));

        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                new Base64InputStream(new ByteArrayInputStream(TEST_CERT.getBytes()),
                        Base64.DEFAULT));

        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .installKeyPair(
                        sDeviceState.dpc().componentName(), privateKey, certificate, ALIAS));
    }

    @PolicyAppliesTest(policy = CaCertManagement.class)
    public void removeKeyPair_removedKeyPair() throws Exception {
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.decode(TEST_KEY, Base64.DEFAULT)));

        Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                new Base64InputStream(new ByteArrayInputStream(TEST_CERT.getBytes()),
                        Base64.DEFAULT));

        sDeviceState.dpc().devicePolicyManager()
                .installKeyPair(
                        sDeviceState.dpc().componentName(), privateKey, certificate, ALIAS);

        boolean result = sDeviceState.dpc().devicePolicyManager()
                .removeKeyPair(sDeviceState.dpc().componentName(), ALIAS);
        assertThat(result).isTrue();
    }

    @CannotSetPolicyTest(policy = CaCertManagement.class)
    public void removeKeyPair_invalidAdmin_throwsException() throws Exception {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .removeKeyPair(sDeviceState.dpc().componentName(), ALIAS));
    }

    @ApiTest(apis = {
            "android.app.admin.DevicePolicyManager#installCaCert",
            "android.app.admin.DevicePolicyManager#getOwnerInstalledCaCerts"
    })
    @CanSetPolicyTest(policy = CaCertManagement.class)
    @Postsubmit(reason = "new test")
    public void getOwnerInstalledCaCerts_returnsCertInstalledByAdmin()
            throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().installCaCert(
                    sDeviceState.dpc().componentName(), TEST_CERT_BYTES);
            String alias = sKeyStore.getCertificateAlias(sCertificate);

            assertThat(TestApis.devicePolicy().getOwnerInstalledCaCerts(
                    sDeviceState.dpc().user())).contains(alias);
        } finally {
            sDeviceState.dpc().devicePolicyManager().uninstallCaCert(
                    sDeviceState.dpc().componentName(), TEST_CERT_BYTES);
        }
    }

    private void assertCaCertInstalledForTheDpcAndLocally(byte[] caBytes)
            throws GeneralSecurityException {
        assertCaCertInstalledAndTrusted(caBytes, /* installed= */ true);
    }

    private void assertCaCertNotInstalledForTheDpcOrLocally(byte[] caBytes)
            throws GeneralSecurityException {
        assertCaCertInstalledAndTrusted(caBytes, /* installed= */ false);
    }

    /**
     * Whether a given cert, or one a lot like it, has been installed system-wide and is available
     * to all apps.
     *
     * <p>A CA certificate is "installed" if it matches all of the following conditions:
     * <ul>
     *   <li>{@link DevicePolicyManager#hasCaCertInstalled} returns {@code true}.</li>
     *   <li>{@link DevicePolicyManager#getInstalledCaCerts} lists a matching certificate (not
     *       necessarily exactly the same) in its response.</li>
     *   <li>Any new instances of {@link TrustManager} should report the certificate among their
     *       accepted issuer list -- older instances may keep the set of issuers they were created
     *       with until explicitly refreshed.</li>
     */
    private void assertCaCertInstalledAndTrusted(byte[] caBytes, boolean installed)
            throws GeneralSecurityException {
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        Certificate caCert = readCertificate(caBytes);
        // All three responses should match - if an installed certificate isn't trusted or (worse)
        // a trusted certificate isn't even installed we should fail now, loudly.
        boolean isInstalled = remoteDpm.hasCaCertInstalled(
                sDeviceState.dpc().componentName(), caCert.getEncoded());
        assertThat(isInstalled).isEqualTo(installed);

        assertThat(isCaCertListed(caCert)).isEqualTo(installed);

        assertCaCertTrustStatusChange(caCert, isInstalled);

        X509TrustManagerExtensions xtm = new X509TrustManagerExtensions(getFirstX509TrustManager());
        boolean userAddedCertificate = xtm.isUserAddedCertificate((X509Certificate) caCert);
        assertThat(userAddedCertificate).isEqualTo(installed);
    }

    /**
     * Convert an encoded certificate back into a {@link Certificate}.
     *
     * Instantiates a fresh CertificateFactory every time for repeatability.
     */
    private static Certificate readCertificate(byte[] certBuffer) throws CertificateException {
        final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return certFactory.generateCertificate(new ByteArrayInputStream(certBuffer));
    }

    private boolean isCaCertListed(Certificate caCert) throws CertificateException {
        boolean listed = false;
        RemoteDevicePolicyManager remoteDpm = sDeviceState.dpc().devicePolicyManager();
        for (byte[] certBuffer :
                remoteDpm.getInstalledCaCerts(sDeviceState.dpc().componentName())) {
            if (caCert.equals(readCertificate(certBuffer))) {
                listed = true;
            }
        }
        return listed;
    }

    private void assertCaCertTrustStatusChange(Certificate caCert, boolean newStatus)
            throws GeneralSecurityException {
        // Verify that the user added CA is reflected in the default X509TrustManager.
        X509TrustManager tm = getFirstX509TrustManager();

        Poll.forValue("Accepted issuers", () -> Arrays.asList(tm.getAcceptedIssuers()))
                .toMeet((acceptedIssuers) -> newStatus == acceptedIssuers.contains(caCert))
                .errorOnFail(
                        newStatus ? "Couldn't find certificate in trusted certificates list."
                                : "Found uninstalled certificate in trusted certificates list.")
                .await();
    }

    private static X509TrustManager getFirstX509TrustManager() throws GeneralSecurityException {
        final TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        // Use platform provided CA store.
        tmf.init((KeyStore) null);
        return getFirstX509TrustManager(tmf);
    }

    private static X509TrustManager getFirstX509TrustManager(TrustManagerFactory tmf) {
        for (TrustManager trustManager : tmf.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new RuntimeException("Unable to find X509TrustManager");
    }
}
