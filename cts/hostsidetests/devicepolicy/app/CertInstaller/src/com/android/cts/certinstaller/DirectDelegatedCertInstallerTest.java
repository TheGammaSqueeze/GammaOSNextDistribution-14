/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.certinstaller;

import static com.android.cts.devicepolicy.TestCertificates.TEST_CA;
import static com.android.cts.devicepolicy.TestCertificates.TEST_CERT;
import static com.android.cts.devicepolicy.TestCertificates.TEST_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import static java.util.Collections.singleton;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.security.AttestedKeyPair;
import android.security.KeyChain;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.telephony.TelephonyManager;
import android.test.InstrumentationTestCase;
import android.util.Base64;
import android.util.Base64InputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Map;

/*
 * Tests the delegated certificate installer functionality.
 *
 * This class is configured as DelegatedCertInstaller by the DelegatedCertInstallerHelper and is
 * invoked directly from the host class,
 * DeviceAndProfileOwnerTest#testDelegatedCertInstallerDirectly.
 *
 * TODO: this class is missing more functionality of the DelegatedCertInstaller tests.
 * When this class is done then the DelegatedCertInstallerTest can be deleted.
 */
public class DirectDelegatedCertInstallerTest extends InstrumentationTestCase {
    private static final String TEST_ALIAS = "DirectDelegatedCertInstallerTest-keypair";
    private static final String NON_EXISTENT_ALIAS = "DirectDelegatedCertInstallerTest-nonexistent";

    private DevicePolicyManager mDpm;
    private PrivateKey mTestPrivateKey;
    private Certificate mTestCertificate;
    private boolean mHasTelephony = false;
    private TelephonyManager mTelephonyManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTestPrivateKey = rsaKeyFromString(TEST_KEY);
        mTestCertificate = certificateFromString(TEST_CERT);
        mDpm = getContext().getSystemService(DevicePolicyManager.class);
        PackageManager pm = getContext().getPackageManager();
        if (pm != null && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            mHasTelephony = true;
            mTelephonyManager = (TelephonyManager) getContext().getSystemService(
                    Context.TELEPHONY_SERVICE);
        }
    }

    @Override
    public void tearDown() throws Exception {
        mDpm.uninstallCaCert(null, TEST_CA.getBytes());
        mDpm.removeKeyPair(null, TEST_ALIAS);
        super.tearDown();
    }

    public void testCaCertsOperations() throws GeneralSecurityException, IOException {
        final byte[] cert = TEST_CA.getBytes();
        final Certificate caCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(cert));

        // Exercise installCaCert()
        KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
        keyStore.load(null, null);
        assertWithMessage("CA certificate must not be installed in KeyStore at the"
                + " beginning of the test").that(keyStore.getCertificateAlias(caCert)).isNull();
        assertWithMessage("CA certificate must not be installed in the DPM at the"
                + " beginning of the test").that(mDpm.hasCaCertInstalled(null, cert)).isFalse();


        assertWithMessage("Expecting CA certificate installation to succeed").that(
                mDpm.installCaCert(null, cert)).isTrue();
        assertWithMessage("Expecting CA cert to be installed").that(
                mDpm.hasCaCertInstalled(null, cert)).isTrue();

        // Exercise getInstalledCaCerts()
        assertWithMessage("Expecting CA cert to be in the list of installed CA certs").that(
                containsCertificate(mDpm.getInstalledCaCerts(null), cert)).isTrue();

        // Verify that the CA cert was marked as installed by the Device Owner / Profile Owner.
        assertWithMessage("CA cert should have a KeyStore alias").that(
                keyStore.getCertificateAlias(caCert)).isNotNull();

        mDpm.uninstallCaCert(null, cert);
        assertWithMessage("Expecting CA cert to no longer be installed").that(
                mDpm.hasCaCertInstalled(null, cert)).isFalse();
    }

    public void testInstallKeyPair() throws Exception {
        final String alias = "delegated-cert-installer-test-key";

        assertThat(mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate},
                alias, true)).isTrue();

        // Test that the installed private key can be obtained.
        PrivateKey obtainedKey = KeyChain.getPrivateKey(getContext(), alias);
        assertThat(obtainedKey).isNotNull();
        assertThat(obtainedKey.getAlgorithm()).isEqualTo("RSA");

        // Test cleaning up the key.
        assertThat(mDpm.removeKeyPair(null, alias)).isTrue();
        assertThat(KeyChain.getPrivateKey(getContext(), alias)).isNull();
    }

    // Test that a key generation request succeeds when device identifiers are not requested.
    public void testGenerateKeyPairWithoutDeviceIdAttestation() {
        final String alias = "com.android.test.generated-rsa-1";
        try {
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                            KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setIsStrongBoxBacked(false)
                    .setAttestationChallenge(new byte[]{'a', 'b', 'c'})
                    .build();

            AttestedKeyPair generated = mDpm.generateKeyPair(
                    null, "RSA", keySpec, 0);
            assertThat(generated).isNotNull();
        } finally {
            assertThat(mDpm.removeKeyPair(null, alias)).isTrue();
        }
    }

    public void testAccessToDeviceIdentifiers() {
        final String adminPackageName = "com.android.cts.deviceandprofileowner";
        if (mDpm.isDeviceOwnerApp(adminPackageName)) {
            validateCanAccessDeviceIdentifiers();
        } else {
            validateNoAccessToIdentifier();
        }
    }

    private void validateNoAccessToIdentifier() {
        assertThrows(SecurityException.class, () -> Build.getSerial());

        if (!mHasTelephony) {
            return;
        }

        assertWithMessage("Telephony service must be available.")
                .that(mTelephonyManager).isNotNull();

        assertThrows(SecurityException.class, () -> mTelephonyManager.getImei());
    }

    public void validateCanAccessDeviceIdentifiers() {
        assertThat(Build.getSerial()).doesNotMatch(Build.UNKNOWN);

        if (!mHasTelephony) {
            return;
        }

        assertWithMessage("Telephony service must be available.")
                .that(mTelephonyManager).isNotNull();

        try {
            mTelephonyManager.getImei();
        } catch (SecurityException e) {
            fail("Should have permission to access IMEI: " + e);
        }
    }

    public void testHasKeyPair_NonExistent() {
        assertThat(mDpm.hasKeyPair(NON_EXISTENT_ALIAS)).isFalse();
    }

    public void testHasKeyPair_Installed() {
        mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate}, TEST_ALIAS,
                /* requestAccess= */ true);

        assertThat(mDpm.hasKeyPair(TEST_ALIAS)).isTrue();
    }

    public void testHasKeyPair_Removed() {
        mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate}, TEST_ALIAS,
                /* requestAccess= */ true);
        mDpm.removeKeyPair(null, TEST_ALIAS);

        assertThat(mDpm.hasKeyPair(TEST_ALIAS)).isFalse();
    }

    public void testGetKeyPairGrants_Empty() {
        // Not granting upon install.
        mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate}, TEST_ALIAS,
                /* requestAccess= */ false);

        assertThat(mDpm.getKeyPairGrants(TEST_ALIAS)).isEmpty();
    }

    public void testGetKeyPairGrants_NonEmpty() {
        // Granting upon install.
        mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate}, TEST_ALIAS,
                /* requestAccess= */ true);

        assertThat(mDpm.getKeyPairGrants(TEST_ALIAS))
                .isEqualTo(Map.of(Process.myUid(), singleton(getContext().getPackageName())));
    }

    public void testIsWifiGrant_default() {
        mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate},
                TEST_ALIAS, /* requestAccess= */ false);

        assertThat(mDpm.isKeyPairGrantedToWifiAuth(TEST_ALIAS)).isFalse();
    }

    public void testIsWifiGrant_allowed() {
        mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate},
                TEST_ALIAS, /* requestAccess= */ false);
        assertTrue(mDpm.grantKeyPairToWifiAuth(TEST_ALIAS));

        assertThat(mDpm.isKeyPairGrantedToWifiAuth(TEST_ALIAS)).isTrue();
    }

    public void testIsWifiGrant_denied() {
        mDpm.installKeyPair(null, mTestPrivateKey, new Certificate[]{mTestCertificate},
                TEST_ALIAS, /* requestAccess= */ false);
        assertTrue(mDpm.grantKeyPairToWifiAuth(TEST_ALIAS));
        assertTrue(mDpm.revokeKeyPairFromWifiAuth(TEST_ALIAS));

        assertThat(mDpm.isKeyPairGrantedToWifiAuth(TEST_ALIAS)).isFalse();
    }

    private PrivateKey rsaKeyFromString(String key) throws Exception {
        final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(
                Base64.decode(key, Base64.DEFAULT));
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private Certificate certificateFromString(String cert) throws Exception {
        return CertificateFactory.getInstance("X.509").generateCertificate(
                new Base64InputStream(new ByteArrayInputStream(cert.getBytes()), Base64.DEFAULT));
    }

    private static boolean containsCertificate(List<byte[]> certificates, byte[] toMatch)
            throws CertificateException {
        Certificate certificateToMatch = readCertificate(toMatch);
        for (byte[] certBuffer : certificates) {
            Certificate cert = readCertificate(certBuffer);
            if (certificateToMatch.equals(cert)) {
                return true;
            }
        }
        return false;
    }

    private static Certificate readCertificate(byte[] certBuffer) throws CertificateException {
        final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return certFactory.generateCertificate(new ByteArrayInputStream(certBuffer));
    }

    private Context getContext() {
        return getInstrumentation().getContext();
    }
}
