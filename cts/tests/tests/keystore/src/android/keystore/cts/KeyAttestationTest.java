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
 * limitations under the License.
 */

package android.keystore.cts;

import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_SOFTWARE;
import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_STRONG_BOX;
import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
import static android.keystore.cts.AuthorizationList.KM_ALGORITHM_EC;
import static android.keystore.cts.AuthorizationList.KM_ALGORITHM_RSA;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_NONE;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_SHA_2_256;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_SHA_2_512;
import static android.keystore.cts.AuthorizationList.KM_ORIGIN_GENERATED;
import static android.keystore.cts.AuthorizationList.KM_ORIGIN_UNKNOWN;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_DECRYPT;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_ENCRYPT;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_SIGN;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_VERIFY;
import static android.keystore.cts.RootOfTrust.KM_VERIFIED_BOOT_VERIFIED;
import static android.security.keymaster.KeymasterDefs.KM_PURPOSE_AGREE_KEY;
import static android.security.keystore.KeyProperties.DIGEST_NONE;
import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.DIGEST_SHA512;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_OAEP;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA;
import static android.security.keystore.KeyProperties.PURPOSE_AGREE_KEY;
import static android.security.keystore.KeyProperties.PURPOSE_DECRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_ENCRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static android.security.keystore.KeyProperties.PURPOSE_VERIFY;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PSS;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.keystore.cts.Attestation;
import android.keystore.cts.util.TestUtils;
import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.RestrictedBuildTest;
import android.security.KeyStoreException;
import android.security.keystore.AttestationUtils;
import android.security.keystore.DeviceIdAttestationException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.PropertyUtil;

import com.google.common.collect.ImmutableSet;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.KeyGenerator;

/**
 * Tests for Android Keystore attestation.
 */
@RunWith(AndroidJUnit4.class)
public class KeyAttestationTest {

    private static final String TAG = AndroidKeyStoreTest.class.getSimpleName();

    private static final int ORIGINATION_TIME_OFFSET = 1000000;
    private static final int CONSUMPTION_TIME_OFFSET = 2000000;

    private static final int KEY_USAGE_BITSTRING_LENGTH = 9;
    private static final int KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET = 0;
    private static final int KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET = 2;
    private static final int KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET = 3;
    private static final int KEY_USAGE_KEY_AGREE_BIT_OFFSET = 4;

    private static final int OS_MAJOR_VERSION_MATCH_GROUP_NAME = 1;
    private static final int OS_MINOR_VERSION_MATCH_GROUP_NAME = 2;
    private static final int OS_SUBMINOR_VERSION_MATCH_GROUP_NAME = 3;
    private static final Pattern OS_VERSION_STRING_PATTERN = Pattern
            .compile("([0-9]{1,2})(?:\\.([0-9]{1,2}))?(?:\\.([0-9]{1,2}))?(?:[^0-9.]+.*)?");

    private static final int OS_PATCH_LEVEL_YEAR_GROUP_NAME = 1;
    private static final int OS_PATCH_LEVEL_MONTH_GROUP_NAME = 2;
    private static final Pattern OS_PATCH_LEVEL_STRING_PATTERN = Pattern
            .compile("([0-9]{4})-([0-9]{2})-[0-9]{2}");

    private static final int KM_ERROR_CANNOT_ATTEST_IDS = -66;
    private static final int KM_ERROR_INVALID_INPUT_LENGTH = -21;
    private static final int KM_ERROR_PERMISSION_DENIED = 6;

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testVersionParser() throws Exception {
        // Non-numerics/empty give version 0
        assertEquals(0, parseSystemOsVersion(""));
        assertEquals(0, parseSystemOsVersion("N"));

        // Should support one, two or three version number values.
        assertEquals(10000, parseSystemOsVersion("1"));
        assertEquals(10200, parseSystemOsVersion("1.2"));
        assertEquals(10203, parseSystemOsVersion("1.2.3"));

        // It's fine to append other stuff to the dotted numeric version.
        assertEquals(10000, parseSystemOsVersion("1stuff"));
        assertEquals(10200, parseSystemOsVersion("1.2garbage.32"));
        assertEquals(10203, parseSystemOsVersion("1.2.3-stuff"));

        // Two digits per version field are supported
        assertEquals(152536, parseSystemOsVersion("15.25.36"));
        assertEquals(999999, parseSystemOsVersion("99.99.99"));
        assertEquals(0, parseSystemOsVersion("100.99.99"));
        assertEquals(0, parseSystemOsVersion("99.100.99"));
        assertEquals(0, parseSystemOsVersion("99.99.100"));
    }

    @RequiresDevice
    @Test
    public void testEcAttestation() throws Exception {
        testEcAttestation(false);
    }

    @RequiresDevice
    @Test
    public void testEcAttestation_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));

        testEcAttestation(true);
    }

    private void testEcAttestation(boolean isStrongBox) throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        final int[] purposes = {
                KM_PURPOSE_SIGN, KM_PURPOSE_VERIFY, KM_PURPOSE_SIGN | KM_PURPOSE_VERIFY
        };
        final boolean[] devicePropertiesAttestationValues = {true, false};
        final boolean[] includeValidityDatesValues = {true, false};
        final String[] curves;
        final int[] keySizes;
        final byte[][] challenges;

        if (isStrongBox) {
            // StrongBox only supports secp256r1 keys.
            curves = new String[] {"secp256r1"};
            keySizes = new int[] {256};
            challenges = new byte[][] {
                    // Empty challange is not accepted by StrongBox.
                    "challenge".getBytes(), // short challenge
                    new byte[128], // long challenge
            };
        } else {
            curves = new String[] {
                    "secp224r1", "secp256r1", "secp384r1", "secp521r1"
            };
            keySizes = new int[]{
                    224, 256, 384, 521
            };
            challenges = new byte[][]{
                    new byte[0], // empty challenge
                    "challenge".getBytes(), // short challenge
                    new byte[128], // long challenge
            };
        }

        for (int curveIndex = 0; curveIndex < curves.length; ++curveIndex) {
            for (int challengeIndex = 0; challengeIndex < challenges.length; ++challengeIndex) {
                for (int purposeIndex = 0; purposeIndex < purposes.length; ++purposeIndex) {
                    for (boolean includeValidityDates : includeValidityDatesValues) {
                        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
                            try {
                                testEcAttestation(challenges[challengeIndex], includeValidityDates,
                                        curves[curveIndex], keySizes[curveIndex],
                                        purposes[purposeIndex], devicePropertiesAttestation,
                                        isStrongBox);
                            } catch (Throwable e) {
                                boolean isIdAttestationFailure =
                                        (e.getCause() instanceof KeyStoreException)
                                        && KeyStoreException.ERROR_ID_ATTESTATION_FAILURE
                                        == ((KeyStoreException) e.getCause()).getNumericErrorCode();
                                if (devicePropertiesAttestation && isIdAttestationFailure) {
                                    if (getContext().getPackageManager().hasSystemFeature(
                                            PackageManager.FEATURE_DEVICE_ID_ATTESTATION)) {
                                        throw new Exception("Unexpected failure while generating"
                                                + " key.\nIn case of AOSP/GSI builds, system "
                                                + "provided properties could be different from "
                                                + "provisioned properties in KeyMaster/KeyMint. "
                                                + "In such cases, make sure attestation specific "
                                                + "properties (Build.*_FOR_ATTESTATION) are "
                                                + "configured correctly.", e);
                                    } else {
                                        Log.i(TAG, "key attestation with device IDs not supported;"
                                                + " test skipped");
                                        continue;
                                    }
                                }
                                throw new Exception("Failed on curve " + curveIndex +
                                        " challenge " + challengeIndex + " purpose " +
                                        purposeIndex + " includeValidityDates " +
                                        includeValidityDates + " and devicePropertiesAttestation " +
                                        devicePropertiesAttestation, e);
                            }
                        }
                    }
                }
            }
        }
    }

    private void assertPublicAttestationError(KeyStoreException keyStoreException,
            boolean devicePropertiesAttestation) {
        // Assert public failure information.
        int errorCode = keyStoreException.getNumericErrorCode();
        String assertMessage = String.format(
                "Error code was %d, device properties attestation? %b",
                errorCode, devicePropertiesAttestation);
        assertTrue(assertMessage, KeyStoreException.ERROR_INCORRECT_USAGE == errorCode
                || (devicePropertiesAttestation
                && KeyStoreException.ERROR_ID_ATTESTATION_FAILURE == errorCode));
        assertFalse("Unexpected transient failure.", keyStoreException.isTransientFailure());
    }

    @Test
    public void testEcAttestation_TooLargeChallenge() throws Exception {
        testEcAttestation_TooLargeChallenge(false);
    }

    @Test
    public void testEcAttestation_TooLargeChallenge_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testEcAttestation_TooLargeChallenge(true);
    }

    private void testEcAttestation_TooLargeChallenge(boolean isStrongBox) throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            try {
                testEcAttestation(new byte[129], true /* includeValidityDates */, "secp256r1", 256,
                        KM_PURPOSE_SIGN, devicePropertiesAttestation, isStrongBox);
                fail("Attestation challenges larger than 128 bytes should be rejected");
            } catch (ProviderException e) {
                KeyStoreException cause = (KeyStoreException) e.getCause();
                int errorCode = cause.getErrorCode();
                String assertMessage = String.format(
                        "The KeyMint implementation may only return INVALID_INPUT_LENGTH or "
                        + "CANNOT_ATTEST_IDSs as errors when the attestation challenge is "
                        + "too large (error code was %d, attestation properties %b)",
                        errorCode, devicePropertiesAttestation);
                assertTrue(assertMessage, KM_ERROR_INVALID_INPUT_LENGTH == cause.getErrorCode()
                        || (devicePropertiesAttestation
                            && KM_ERROR_CANNOT_ATTEST_IDS == cause.getErrorCode())
                );
                assertPublicAttestationError(cause, devicePropertiesAttestation);
            }
        }
    }

    @Test
    public void testEcAttestation_NoChallenge() throws Exception {
        testEcAttestation_NoChallenge(false);
    }

    @Test
    public void testEcAttestation_NoChallenge_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testEcAttestation_NoChallenge(true);
    }

    public void testEcAttestation_NoChallenge(boolean isStrongBox) throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            Date now = new Date();
            Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
            Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                    .setAttestationChallenge(null)
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd)
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .setIsStrongBoxBacked(isStrongBox)
                    .build();

            generateKeyPair(KEY_ALGORITHM_EC, spec);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            try {
                Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
                assertEquals(1, certificates.length);

                X509Certificate attestationCert = (X509Certificate) certificates[0];
                assertNull(attestationCert.getExtensionValue(Attestation.ASN1_OID));
                assertNull(attestationCert.getExtensionValue(Attestation.EAT_OID));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    private void testEcAttestation_DeviceLocked(Boolean expectStrongBox) throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setAttestationChallenge(new byte[128])
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd)
                    .setIsStrongBoxBacked(expectStrongBox);

        if (expectStrongBox) {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256);
        } else {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512);
        }

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, expectStrongBox);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            checkDeviceLocked(Attestation.loadFromCertificate(attestationCert));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    @RestrictedBuildTest
    @RequiresDevice
    @Test
    public void testEcAttestation_DeviceLocked() throws Exception {
        testEcAttestation_DeviceLocked(false /* expectStrongBox */);
    }

    @RestrictedBuildTest
    @RequiresDevice
    @Test
    public void testEcAttestation_DeviceLockedStrongbox() throws Exception {
        if (!TestUtils.hasStrongBox(getContext()))
            return;
        testEcAttestation_DeviceLocked(true /* expectStrongBox */);
    }

    @Test
    public void testAttestationKmVersionMatchesFeatureVersion() throws Exception {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        testAttestationKmVersionMatchesFeatureVersion(false);
    }

    @Test
    public void testAttestationKmVersionMatchesFeatureVersionStrongBox() throws Exception {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        int keyStoreFeatureVersionStrongBox =
                TestUtils.getFeatureVersionKeystoreStrongBox(getContext());

        if (!TestUtils.hasStrongBox(getContext())) {
            // If there's no StrongBox, ensure there's no feature version for it.
            assertEquals(0, keyStoreFeatureVersionStrongBox);
            return;
        }

        testAttestationKmVersionMatchesFeatureVersion(true);
    }

    private void testAttestationKmVersionMatchesFeatureVersion(boolean isStrongBox)
            throws  Exception {

        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
                new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setAttestationChallenge(new byte[128])
                        .setKeyValidityStart(now)
                        .setKeyValidityForOriginationEnd(originationEnd)
                        .setKeyValidityForConsumptionEnd(consumptionEnd)
                        .setIsStrongBoxBacked(isStrongBox);

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, isStrongBox /* expectStrongBox */);
            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);
            int kmVersionFromAttestation = attestation.keymasterVersion;
            int keyStoreFeatureVersion;

            if (isStrongBox) {
                keyStoreFeatureVersion =
                        TestUtils.getFeatureVersionKeystoreStrongBox(getContext());
            } else {
                keyStoreFeatureVersion =
                        TestUtils.getFeatureVersionKeystore(getContext());
            }
            // Feature Version is required on devices launching with Android 12 (API Level
            // 31) but may be reported on devices launching with an earlier version. If it's
            // present, it must match what is reported in attestation.
            if (PropertyUtil.getFirstApiLevel() >= 31) {
                assertNotEquals(0, keyStoreFeatureVersion);
            }
            if (keyStoreFeatureVersion != 0) {
                assertEquals(kmVersionFromAttestation, keyStoreFeatureVersion);
            }
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    @Test
    public void testEcAttestation_KeyStoreExceptionWhenRequestingUniqueId() throws Exception {
        testEcAttestation_KeyStoreExceptionWhenRequestingUniqueId(false);
    }

    @Test
    public void testEcAttestation_KeyStoreExceptionWhenRequestingUniqueId_StrongBox()
            throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testEcAttestation_KeyStoreExceptionWhenRequestingUniqueId(true);
    }

    private void testEcAttestation_KeyStoreExceptionWhenRequestingUniqueId(
            boolean isStrongBox) throws Exception {
        String keystoreAlias = "test_key";
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                .setAttestationChallenge(new byte[128])
                .setUniqueIdIncluded(true)
                .setIsStrongBoxBacked(isStrongBox)
                .build();

        try {
            generateKeyPair(KEY_ALGORITHM_EC, spec);
            fail("Attestation should have failed.");
        } catch (ProviderException e) {
            // Attestation is expected to fail because of lack of permissions.
            KeyStoreException cause = (KeyStoreException) e.getCause();
            assertEquals(KM_ERROR_PERMISSION_DENIED, cause.getErrorCode());
            // Assert public failure information.
            assertEquals(KeyStoreException.ERROR_PERMISSION_DENIED, cause.getNumericErrorCode());
            assertFalse("Unexpected transient failure in generate key.",
                    cause.isTransientFailure());
        } finally {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    @Test
    public void testEcAttestation_UniqueIdWorksWithCorrectPermission() throws Exception {
        testEcAttestation_UniqueIdWorksWithCorrectPermission(false);
    }
    @Test
    public void testEcAttestation_UniqueIdWorksWithCorrectPermission_StrongBox()
            throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testEcAttestation_UniqueIdWorksWithCorrectPermission(true);
    }

    private void testEcAttestation_UniqueIdWorksWithCorrectPermission(boolean isStrongBox)
            throws Exception {
        assumeTrue("Device doesn't have secure lock screen",
                TestUtils.hasSecureLockScreen(getContext()));
        assumeTrue("Device does not support attestation", TestUtils.isAttestationSupported());

        String keystoreAlias = "test_key";
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                .setAttestationChallenge(new byte[128])
                .setUniqueIdIncluded(true)
                .setIsStrongBoxBacked(isStrongBox)
                .build();

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try (PermissionContext c = TestApis.permissions().withPermission(
                  "android.permission.REQUEST_UNIQUE_ID_ATTESTATION")) {
            generateKeyPair(KEY_ALGORITHM_EC, spec);
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            Attestation attestation = Attestation.loadFromCertificate((X509Certificate) certificates[0]);
            byte[] firstUniqueId = attestation.getUniqueId();
            assertTrue("UniqueId must not be empty", firstUniqueId.length > 0);

            // The unique id rotates (30 days in the default implementation), and it's possible to
            // get a spurious failure if the test runs exactly when the rotation occurs. Allow a
            // single retry, just in case.
            byte[] secondUniqueId = null;
            for (int i = 0; i < 2; ++i) {
                keyStore.deleteEntry(keystoreAlias);

                generateKeyPair(KEY_ALGORITHM_EC, spec);
                certificates = keyStore.getCertificateChain(keystoreAlias);
                attestation = Attestation.loadFromCertificate((X509Certificate) certificates[0]);
                secondUniqueId = attestation.getUniqueId();

                if (Arrays.equals(firstUniqueId, secondUniqueId)) {
                    break;
                } else {
                    firstUniqueId = secondUniqueId;
                    secondUniqueId = null;
                }
            }
            assertTrue("UniqueIds must be consistent",
                    Arrays.equals(firstUniqueId, secondUniqueId));

        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    @RequiresDevice
    @Test
    public void testRsaAttestation() throws Exception {
        testRsaAttestation(false);
    }

    @RequiresDevice
    @Test
    public void testRsaAttestation_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testRsaAttestation(true);
    }

    private void testRsaAttestation(boolean isStrongBox) throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        final int[] purposes = {
                PURPOSE_SIGN | PURPOSE_VERIFY,
                PURPOSE_ENCRYPT | PURPOSE_DECRYPT,
        };
        final String[][] signaturePaddingModes = {
                {
                        SIGNATURE_PADDING_RSA_PKCS1,
                },
                {
                        SIGNATURE_PADDING_RSA_PSS,
                },
                {
                        SIGNATURE_PADDING_RSA_PKCS1,
                        SIGNATURE_PADDING_RSA_PSS,
                },
        };
        final boolean[] devicePropertiesAttestationValues = {true, false};
        final int[] keySizes;
        final byte[][] challenges;
        final String[][] encryptionPaddingModes;

        if (isStrongBox) {
            // StrongBox has to support 2048 bit key.
            keySizes = new int[] {2048};
            challenges = new byte[][] {
                    "challenge".getBytes(), // short challenge
                    new byte[128] // long challenge
            };
            encryptionPaddingModes = new String [][] {
                    {
                            ENCRYPTION_PADDING_RSA_OAEP,
                    },
                    {
                            ENCRYPTION_PADDING_RSA_PKCS1,
                    },
                    {
                            ENCRYPTION_PADDING_RSA_OAEP,
                            ENCRYPTION_PADDING_RSA_PKCS1,
                    },
            };
        } else {
            keySizes = new int[]{ // Smallish sizes to keep test runtimes down.
                    512, 768, 1024
            };
            challenges = new byte[][]{
                    new byte[0], // empty challenge
                    "challenge".getBytes(), // short challenge
                    new byte[128] // long challenge
            };
            encryptionPaddingModes = new String[][]{
                    {
                            ENCRYPTION_PADDING_NONE
                    },
                    {
                            ENCRYPTION_PADDING_RSA_OAEP,
                    },
                    {
                            ENCRYPTION_PADDING_RSA_PKCS1,
                    },
                    {
                            ENCRYPTION_PADDING_RSA_OAEP,
                            ENCRYPTION_PADDING_RSA_PKCS1,
                    },
            };
        }

        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            for (int keySize : keySizes) {
                for (byte[] challenge : challenges) {
                    for (int purpose : purposes) {
                        if (isEncryptionPurpose(purpose)) {
                            testRsaAttestations(keySize, challenge, purpose, encryptionPaddingModes,
                                    devicePropertiesAttestation, isStrongBox);
                        } else {
                            testRsaAttestations(keySize, challenge, purpose, signaturePaddingModes,
                                    devicePropertiesAttestation, isStrongBox);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testRsaAttestation_TooLargeChallenge() throws Exception {
        testRsaAttestation_TooLargeChallenge(512, false);
    }

    @Test
    public void testRsaAttestation_TooLargeChallenge_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testRsaAttestation_TooLargeChallenge(2048, true);
    }

    private void testRsaAttestation_TooLargeChallenge(int keySize, boolean isStrongBox)
            throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            try {
                testRsaAttestation(new byte[129], true /* includeValidityDates */, keySize,
                        PURPOSE_SIGN,
                        null /* paddingModes; may be empty because we'll never test them */,
                        devicePropertiesAttestation, isStrongBox);
                fail("Attestation challenges larger than 128 bytes should be rejected");
            } catch(ProviderException e){
                KeyStoreException cause = (KeyStoreException) e.getCause();
                int errorCode = cause.getErrorCode();
                String assertMessage = String.format(
                        "The KeyMint implementation may only return INVALID_INPUT_LENGTH or "
                        + "CANNOT_ATTEST_IDSs as errors when the attestation challenge is "
                        + "too large (error code was %d, attestation properties %b)",
                        errorCode, devicePropertiesAttestation);
                assertTrue(assertMessage, KM_ERROR_INVALID_INPUT_LENGTH == cause.getErrorCode()
                        || (devicePropertiesAttestation
                            && KM_ERROR_CANNOT_ATTEST_IDS == cause.getErrorCode())
                );
                assertPublicAttestationError(cause, devicePropertiesAttestation);
            }
        }
    }

    @Test
    public void testRsaAttestation_NoChallenge() throws Exception {
        testRsaAttestation_NoChallenge(false);
    }

    @Test
    public void testRsaAttestation_NoChallenge_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testRsaAttestation_NoChallenge(true);
    }

    private void testRsaAttestation_NoChallenge(boolean isStrongBox) throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            Date now = new Date();
            Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
            Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                    .setAttestationChallenge(null)
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd)
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .setIsStrongBoxBacked(isStrongBox)
                    .build();

            generateKeyPair(KEY_ALGORITHM_RSA, spec);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            try {
                Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
                assertEquals(1, certificates.length);

                X509Certificate attestationCert = (X509Certificate) certificates[0];
                assertNull(attestationCert.getExtensionValue(Attestation.ASN1_OID));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    private void testRsaAttestation_DeviceLocked(Boolean expectStrongBox) throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                    .setAttestationChallenge("challenge".getBytes())
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd)
                    .setIsStrongBoxBacked(expectStrongBox);

        if (expectStrongBox) {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256);
        } else {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512);
        }

        generateKeyPair(KEY_ALGORITHM_RSA, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, expectStrongBox);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            checkDeviceLocked(Attestation.loadFromCertificate(attestationCert));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    @RestrictedBuildTest
    @RequiresDevice  // Emulators have no place to store the needed key
    @Test
    public void testRsaAttestation_DeviceLocked() throws Exception {
        testRsaAttestation_DeviceLocked(false /* expectStrongbox */);
    }

    @RestrictedBuildTest
    @RequiresDevice  // Emulators have no place to store the needed key
    @Test
    public void testRsaAttestation_DeviceLockedStrongbox() throws Exception {
        if (!TestUtils.hasStrongBox(getContext()))
            return;

        testRsaAttestation_DeviceLocked(true /* expectStrongbox */);
    }

    @Test
    public void testAesAttestation() throws Exception {
        testAesAttestation(false);
    }

    @Test
    public void testAesAttestation_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testAesAttestation(true);
    }

    private void testAesAttestation(boolean isStrongBox) throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias,
                    PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setAttestationChallenge(new byte[0])
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .setIsStrongBoxBacked(isStrongBox)
                    .build();
            generateKey(spec, KeyProperties.KEY_ALGORITHM_AES);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            try {
                assertNull(keyStore.getCertificateChain(keystoreAlias));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    @Test
    public void testHmacAttestation() throws Exception {
        testHmacAttestation(false);
    }

    @Test
    public void testHmacAttestation_StrongBox() throws Exception {
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));
        testHmacAttestation(true);
    }

    private void testHmacAttestation(boolean isStrongBox) throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .setIsStrongBoxBacked(isStrongBox)
                    .build();

            generateKey(spec, KeyProperties.KEY_ALGORITHM_HMAC_SHA256);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            try {
                assertNull(keyStore.getCertificateChain(keystoreAlias));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    private void testRsaAttestations(int keySize, byte[] challenge, int purpose,
            String[][] paddingModes, boolean devicePropertiesAttestation,
            boolean isStrongBox) throws Exception {
        for (String[] paddings : paddingModes) {
            try {
                testRsaAttestation(challenge, true /* includeValidityDates */, keySize, purpose,
                        paddings, devicePropertiesAttestation, isStrongBox);
                testRsaAttestation(challenge, false /* includeValidityDates */, keySize, purpose,
                        paddings, devicePropertiesAttestation, isStrongBox);
            } catch (Throwable e) {
                boolean isIdAttestationFailure =
                        (e.getCause() instanceof KeyStoreException)
                                && KeyStoreException.ERROR_ID_ATTESTATION_FAILURE
                                == ((KeyStoreException) e.getCause()).getNumericErrorCode();
                if (devicePropertiesAttestation && isIdAttestationFailure) {
                    if (getContext().getPackageManager().hasSystemFeature(
                            PackageManager.FEATURE_DEVICE_ID_ATTESTATION)) {
                        throw new Exception("Unexpected failure while generating key."
                            + "\nIn case of AOSP/GSI builds, system provided properties could be"
                            + " different from provisioned properties in KeyMaster/KeyMint. In"
                            + " such cases, make sure attestation specific properties"
                            + " (Build.*_FOR_ATTESTATION) are configured correctly.", e);
                    } else {
                        Log.i(TAG, "key attestation with device IDs not supported; test skipped");
                        continue;
                    }
                }
                throw new Exception("Failed on key size " + keySize + " challenge [" +
                        new String(challenge) + "], purposes " +
                        buildPurposeSet(purpose) + " paddings " +
                        ImmutableSet.copyOf(paddings) + " and devicePropertiesAttestation "
                        + devicePropertiesAttestation,
                        e);
            }
        }
    }

    @Test
    public void testDeviceIdAttestation() throws Exception {
        testDeviceIdAttestationFailure(AttestationUtils.ID_TYPE_SERIAL, null);
        testDeviceIdAttestationFailure(AttestationUtils.ID_TYPE_IMEI, "Unable to retrieve IMEI");
        testDeviceIdAttestationFailure(AttestationUtils.ID_TYPE_MEID, "Unable to retrieve MEID");
    }

    @Test
    public void testAttestedRoTAcrossKeymints() throws Exception {
        assumeTrue("This test requires a device supporting key attestation",
                TestUtils.isAttestationSupported());
        assumeTrue("This test is not applicable for PC",
                !getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC));
        assumeTrue("This test is only applicable to devices with StrongBox",
                TestUtils.hasStrongBox(getContext()));

        RootOfTrust teeRootOfTrust = generateAttestationAndExtractRoT("tee_test_key", false);
        RootOfTrust sbRootOfTrust = generateAttestationAndExtractRoT("sb_test_key", true);

        assertNotNull("RootOfTrust should not be null for TEE", teeRootOfTrust);
        assertNotNull("RootOfTrust should not be null for StrongBox", sbRootOfTrust);
        assertArrayEquals("Verified boot hash in TEE and StrongBox issued certificates must be"
                        + " same.", teeRootOfTrust.getVerifiedBootHash(),
                sbRootOfTrust.getVerifiedBootHash());
        assertArrayEquals("Verified boot key in TEE and StrongBox issued certificates must be"
                        + " same.", teeRootOfTrust.getVerifiedBootKey(),
                sbRootOfTrust.getVerifiedBootKey());
        assertEquals("Verified boot state in TEE and StrongBox issued certificates must be same.",
                teeRootOfTrust.getVerifiedBootState(),
                sbRootOfTrust.getVerifiedBootState());
        assertEquals("Device locked state in TEE and StrongBox issued certificates must be same.",
                teeRootOfTrust.isDeviceLocked(), sbRootOfTrust.isDeviceLocked());
    }

    private RootOfTrust generateAttestationAndExtractRoT(String alias, boolean isStrongBox)
            throws Exception {
        KeyGenParameterSpec.Builder specBuilder =
                new KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN | PURPOSE_VERIFY)
                        .setIsStrongBoxBacked(isStrongBox)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setDigests(DIGEST_SHA256)
                        .setAttestationChallenge("challenge".getBytes());
        generateKeyPair(KEY_ALGORITHM_EC, specBuilder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        Certificate[] certificates = keyStore.getCertificateChain(alias);
        verifyCertificateChain(certificates, isStrongBox);

        Attestation attestation =
                Attestation.loadFromCertificate((X509Certificate) certificates[0]);
        return attestation.getRootOfTrust();
    }

    @RequiresDevice
    @Test
    public void testCurve25519Attestation() throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }
        assumeTrue("Curve25519 Key attestation supported from KeyMint v2 and above.",
                getContext().getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_HARDWARE_KEYSTORE,
                                Attestation.KM_VERSION_KEYMINT_2));

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC)) {
            return;
        }

        byte[][] challenges = {
                new byte[0], // empty challenge
                "challenge".getBytes(), // short challenge
                new byte[128] // long challenge
        };
        boolean[] devicePropertiesAttestationValues = {true, false};

        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            for (byte[] challenge : challenges) {
                testCurve25519Attestations("ed25519", challenge, PURPOSE_SIGN | PURPOSE_VERIFY,
                        devicePropertiesAttestation);
                testCurve25519Attestations("x25519", challenge, PURPOSE_AGREE_KEY,
                        devicePropertiesAttestation);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void testCurve25519Attestations(String curve, byte[] challenge,
                                         int purpose, boolean devicePropertiesAttestation)
            throws Exception {
        Log.i(TAG, curve + " curve key attestation with: "
                + " / challenge " + Arrays.toString(challenge)
                + " / purposes " + purpose
                + " / devicePropertiesAttestation " + devicePropertiesAttestation);

        String keystoreAlias = "test_key";
        Date startTime = new Date();
        KeyGenParameterSpec.Builder builder =
                new KeyGenParameterSpec.Builder(keystoreAlias, purpose)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec(curve))
                        .setDigests(KeyProperties.DIGEST_NONE)
                        .setAttestationChallenge(challenge)
                        .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation);

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate []certificates = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, false /* expectStrongBox */);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);

            checkEcKeyDetails(attestation, "CURVE_25519", 256);
            checkKeyUsage(attestationCert, purpose);
            checkKeyIndependentAttestationInfo(challenge, purpose,
                    ImmutableSet.of(KM_DIGEST_NONE), startTime, false,
                    devicePropertiesAttestation, attestation);
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    @SuppressWarnings("deprecation")
    private void testRsaAttestation(byte[] challenge, boolean includeValidityDates, int keySize,
            int purposes, String[] paddingModes, boolean devicePropertiesAttestation,
            boolean isStrongBox) throws Exception {
        Log.i(TAG, "RSA key attestation with: challenge " + Arrays.toString(challenge) +
                " / includeValidityDates " + includeValidityDates + " / keySize " + keySize +
                " / purposes " + purposes + " / paddingModes " + Arrays.toString(paddingModes) +
                " / devicePropertiesAttestation " + devicePropertiesAttestation);

        String keystoreAlias = "test_key";
        Date startTime = new Date();
        Date originationEnd = new Date(startTime.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(startTime.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, purposes)
                        .setKeySize(keySize)
                        .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                        .setAttestationChallenge(challenge)
                        .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                        .setIsStrongBoxBacked(isStrongBox);

        if (includeValidityDates) {
            builder.setKeyValidityStart(startTime)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);
        }
        if (isEncryptionPurpose(purposes)) {
            builder.setEncryptionPaddings(paddingModes);
            // Because we sometimes set "no padding", allow non-randomized encryption.
            builder.setRandomizedEncryptionRequired(false);
        }
        if (isSignaturePurpose(purposes)) {
            builder.setSignaturePaddings(paddingModes);
        }

        generateKeyPair(KEY_ALGORITHM_RSA, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, isStrongBox /* expectStrongBox */);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);

            checkRsaKeyDetails(attestation, keySize, purposes,
                    (paddingModes == null)
                            ? new HashSet<String>() : ImmutableSet.copyOf(paddingModes));
            checkKeyUsage(attestationCert, purposes);
            checkKeyIndependentAttestationInfo(challenge, purposes, startTime,
                includeValidityDates, devicePropertiesAttestation, attestation);
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    private void checkKeyUsage(X509Certificate attestationCert, int purposes) {

        boolean[] expectedKeyUsage = new boolean[KEY_USAGE_BITSTRING_LENGTH];
        if (isSignaturePurpose(purposes)) {
            expectedKeyUsage[KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET] = true;
        }
        if (isEncryptionPurpose(purposes)) {
            expectedKeyUsage[KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET] = true;
            expectedKeyUsage[KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET] = true;
        }
        if (isAgreeKeyPurpose(purposes)) {
            expectedKeyUsage[KEY_USAGE_KEY_AGREE_BIT_OFFSET] = true;
        }
        assertThat("Attested certificate has unexpected key usage.",
                attestationCert.getKeyUsage(), is(expectedKeyUsage));
    }

    @SuppressWarnings("deprecation")
    private void testEcAttestation(byte[] challenge, boolean includeValidityDates, String ecCurve,
            int keySize, int purposes, boolean devicePropertiesAttestation,
            boolean isStrongBox) throws Exception {
        Log.i(TAG, "EC key attestation with: challenge " + Arrays.toString(challenge) +
                " / includeValidityDates " + includeValidityDates + " / ecCurve " + ecCurve +
                " / keySize " + keySize + " / purposes " + purposes +
                " / devicePropertiesAttestation " + devicePropertiesAttestation);

        String keystoreAlias = "test_key";
        Date startTime = new Date();
        Date originationEnd = new Date(startTime.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(startTime.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keystoreAlias,
                purposes)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec(ecCurve))
                        .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                        .setAttestationChallenge(challenge)
                        .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                        .setIsStrongBoxBacked(isStrongBox);

        if (includeValidityDates) {
            builder.setKeyValidityStart(startTime)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);
        }

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, isStrongBox /* expectStrongBox */);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);

            checkEcKeyDetails(attestation, ecCurve, keySize);
            checkKeyUsage(attestationCert, purposes);
            checkKeyIndependentAttestationInfo(challenge, purposes, startTime,
                includeValidityDates, devicePropertiesAttestation, attestation);
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    private void checkAttestationApplicationId(Attestation attestation)
            throws NoSuchAlgorithmException, NameNotFoundException {
        AttestationApplicationId aaid = null;
        int kmVersion = attestation.getKeymasterVersion();
        assertNull(attestation.getTeeEnforced().getAttestationApplicationId());
        aaid = attestation.getSoftwareEnforced().getAttestationApplicationId();

        if (kmVersion >= 3) {
            // must be present and correct
            assertNotNull(aaid);
            assertEquals(new AttestationApplicationId(getContext()), aaid);
        } else {
            // may be present and
            // must be correct if present
            if (aaid != null) {
                assertEquals(new AttestationApplicationId(getContext()), aaid);
            }
        }
    }

    private void checkAttestationDeviceProperties(boolean devicePropertiesAttestation,
            Attestation attestation) {
        final AuthorizationList keyDetailsList;
        final AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                || attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_STRONG_BOX) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }

        if (devicePropertiesAttestation) {
            final String platformReportedBrand =
                    TestUtils.isPropertyEmptyOrUnknown(Build.BRAND_FOR_ATTESTATION)
                    ? Build.BRAND : Build.BRAND_FOR_ATTESTATION;
            assertThat(keyDetailsList.getBrand()).isEqualTo(platformReportedBrand);
            final String platformReportedDevice =
                    TestUtils.isPropertyEmptyOrUnknown(Build.DEVICE_FOR_ATTESTATION)
                            ? Build.DEVICE : Build.DEVICE_FOR_ATTESTATION;
            assertThat(keyDetailsList.getDevice()).isEqualTo(platformReportedDevice);
            final String platformReportedProduct =
                    TestUtils.isPropertyEmptyOrUnknown(Build.PRODUCT_FOR_ATTESTATION)
                    ? Build.PRODUCT : Build.PRODUCT_FOR_ATTESTATION;
            assertThat(keyDetailsList.getProduct()).isEqualTo(platformReportedProduct);
            final String platformReportedManufacturer =
                    TestUtils.isPropertyEmptyOrUnknown(Build.MANUFACTURER_FOR_ATTESTATION)
                            ? Build.MANUFACTURER : Build.MANUFACTURER_FOR_ATTESTATION;
            assertThat(keyDetailsList.getManufacturer()).isEqualTo(platformReportedManufacturer);
            final String platformReportedModel =
                    TestUtils.isPropertyEmptyOrUnknown(Build.MODEL_FOR_ATTESTATION)
                    ? Build.MODEL : Build.MODEL_FOR_ATTESTATION;
            assertThat(keyDetailsList.getModel()).isEqualTo(platformReportedModel);
        } else {
            assertNull(keyDetailsList.getBrand());
            assertNull(keyDetailsList.getDevice());
            assertNull(keyDetailsList.getProduct());
            assertNull(keyDetailsList.getManufacturer());
            assertNull(keyDetailsList.getModel());
        }
        assertNull(nonKeyDetailsList.getBrand());
        assertNull(nonKeyDetailsList.getDevice());
        assertNull(nonKeyDetailsList.getProduct());
        assertNull(nonKeyDetailsList.getManufacturer());
        assertNull(nonKeyDetailsList.getModel());
    }

    private void checkAttestationNoUniqueIds(Attestation attestation) {
        assertNull(attestation.getTeeEnforced().getImei());
        assertNull(attestation.getTeeEnforced().getMeid());
        assertNull(attestation.getTeeEnforced().getSerialNumber());
        assertNull(attestation.getSoftwareEnforced().getImei());
        assertNull(attestation.getSoftwareEnforced().getMeid());
        assertNull(attestation.getSoftwareEnforced().getSerialNumber());
    }

    private void checkKeyIndependentAttestationInfo(byte[] challenge, int purposes,
            Date startTime, boolean includesValidityDates,
            boolean devicePropertiesAttestation, Attestation attestation)
            throws NoSuchAlgorithmException, NameNotFoundException {
        checkKeyIndependentAttestationInfo(challenge, purposes,
                ImmutableSet.of(KM_DIGEST_NONE, KM_DIGEST_SHA_2_256, KM_DIGEST_SHA_2_512),
                startTime, includesValidityDates,
                devicePropertiesAttestation, attestation);
    }

    private void checkKeyIndependentAttestationInfo(byte[] challenge, int purposes,
            Set digests, Date startTime, boolean includesValidityDates,
            boolean devicePropertiesAttestation, Attestation attestation)
            throws NoSuchAlgorithmException, NameNotFoundException {
        checkUnexpectedOids(attestation);
        checkAttestationSecurityLevelDependentParams(attestation);
        assertNotNull("Attestation challenge must not be null.",
                attestation.getAttestationChallenge());
        assertThat("Attestation challenge not matching with provided challenge.",
                attestation.getAttestationChallenge(), is(challenge));
        // In EAT, this is null if not filled in. In ASN.1, this is an array with length 0.
        if (attestation.getUniqueId() != null) {
            assertEquals("Unique ID must not be empty if present.",
                    0, attestation.getUniqueId().length);
        }
        checkPurposes(attestation, purposes);
        checkDigests(attestation, digests);
        checkValidityPeriod(attestation, startTime, includesValidityDates);
        checkFlags(attestation);
        checkOrigin(attestation);
        checkAttestationApplicationId(attestation);
        checkAttestationDeviceProperties(devicePropertiesAttestation, attestation);
        checkAttestationNoUniqueIds(attestation);
    }

    private void checkUnexpectedOids(Attestation attestation) {
        assertThat("Attestations must not contain any extra data",
                attestation.getUnexpectedExtensionOids(), is(empty()));
    }

    private int getSystemPatchLevel() {
        Matcher matcher = OS_PATCH_LEVEL_STRING_PATTERN.matcher(Build.VERSION.SECURITY_PATCH);
        String invalidPatternMessage = "Invalid pattern for security path level string "
                + Build.VERSION.SECURITY_PATCH;
        assertTrue(invalidPatternMessage, matcher.matches());
        String year_string = matcher.group(OS_PATCH_LEVEL_YEAR_GROUP_NAME);
        String month_string = matcher.group(OS_PATCH_LEVEL_MONTH_GROUP_NAME);
        int patch_level = Integer.parseInt(year_string) * 100 + Integer.parseInt(month_string);
        return patch_level;
    }

    private int getSystemOsVersion() {
        return parseSystemOsVersion(Build.VERSION.RELEASE);
    }

    private int parseSystemOsVersion(String versionString) {
        Matcher matcher = OS_VERSION_STRING_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            return 0;
        }

        int version = 0;
        String major_string = matcher.group(OS_MAJOR_VERSION_MATCH_GROUP_NAME);
        String minor_string = matcher.group(OS_MINOR_VERSION_MATCH_GROUP_NAME);
        String subminor_string = matcher.group(OS_SUBMINOR_VERSION_MATCH_GROUP_NAME);
        if (major_string != null) {
            version += Integer.parseInt(major_string) * 10000;
        }
        if (minor_string != null) {
            version += Integer.parseInt(minor_string) * 100;
        }
        if (subminor_string != null) {
            version += Integer.parseInt(subminor_string);
        }
        return version;
    }

    private void checkOrigin(Attestation attestation) {
        assertTrue("Origin must be defined",
                attestation.getSoftwareEnforced().getOrigin() != null ||
                        attestation.getTeeEnforced().getOrigin() != null);
        if (attestation.getKeymasterVersion() != 0) {
            assertTrue("Origin may not be defined in both SW and TEE, except on keymaster0",
                    attestation.getSoftwareEnforced().getOrigin() == null ||
                            attestation.getTeeEnforced().getOrigin() == null);
        }

        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE) {
            assertThat("For security level software,"
                            + " SoftwareEnforced origin must be " + KM_ORIGIN_GENERATED,
                    attestation.getSoftwareEnforced().getOrigin(), is(KM_ORIGIN_GENERATED));
        } else if (attestation.getKeymasterVersion() == 0) {
            assertThat("For KeyMaster version 0,"
                            + "TeeEnforced origin must be " + KM_ORIGIN_UNKNOWN,
                    attestation.getTeeEnforced().getOrigin(), is(KM_ORIGIN_UNKNOWN));
        } else {
            assertThat("TeeEnforced origin must be " + KM_ORIGIN_GENERATED,
                    attestation.getTeeEnforced().getOrigin(), is(KM_ORIGIN_GENERATED));
        }
    }

    private void checkFlags(Attestation attestation) {
        assertFalse("All applications was not requested",
                attestation.getSoftwareEnforced().isAllApplications());
        assertFalse("All applications was not requested",
                attestation.getTeeEnforced().isAllApplications());
        assertFalse("Allow while on body was not requested",
                attestation.getSoftwareEnforced().isAllowWhileOnBody());
        assertFalse("Allow while on body was not requested",
                attestation.getTeeEnforced().isAllowWhileOnBody());
        assertNull("Auth binding was not requiested",
                attestation.getSoftwareEnforced().getUserAuthType());
        assertNull("Auth binding was not requiested",
                attestation.getTeeEnforced().getUserAuthType());
        assertTrue("noAuthRequired must be true",
                attestation.getSoftwareEnforced().isNoAuthRequired()
                        || attestation.getTeeEnforced().isNoAuthRequired());
        assertFalse("auth is either software or TEE",
                attestation.getSoftwareEnforced().isNoAuthRequired()
                        && attestation.getTeeEnforced().isNoAuthRequired());
        assertFalse("Software cannot implement rollback resistance",
                attestation.getSoftwareEnforced().isRollbackResistant());
    }

    private void checkValidityPeriod(Attestation attestation, Date startTime,
            boolean includesValidityDates) {
        AuthorizationList validityPeriodList = attestation.getSoftwareEnforced();
        AuthorizationList nonValidityPeriodList = attestation.getTeeEnforced();

        // A bug in Android S leads Android S devices with KeyMint1 not to add a creationDateTime.
        boolean creationDateTimeBroken =
            Build.VERSION.SDK_INT == Build.VERSION_CODES.S &&
            attestation.getKeymasterVersion() == Attestation.KM_VERSION_KEYMINT_1;

        if (!creationDateTimeBroken) {
            assertNull(nonValidityPeriodList.getCreationDateTime());

            Date creationDateTime = validityPeriodList.getCreationDateTime();

            boolean requireCreationDateTime =
                attestation.getKeymasterVersion() >= Attestation.KM_VERSION_KEYMINT_1;

            if (requireCreationDateTime || creationDateTime != null) {
                assertNotNull(creationDateTime);

                assertTrue("Test start time (" + startTime.getTime() + ") and key creation time (" +
                        creationDateTime.getTime() + ") should be close",
                        Math.abs(creationDateTime.getTime() - startTime.getTime()) <= 2000);

                // Allow 1 second leeway in case of nearest-second rounding.
                Date now = new Date();
                assertTrue("Key creation time (" + creationDateTime.getTime() + ") must be now (" +
                        now.getTime() + ") or earlier.",
                        now.getTime() >= (creationDateTime.getTime() - 1000));
            }
        }

        if (includesValidityDates) {
            Date activeDateTime = validityPeriodList.getActiveDateTime();
            Date originationExpirationDateTime = validityPeriodList.getOriginationExpireDateTime();
            Date usageExpirationDateTime = validityPeriodList.getUsageExpireDateTime();

            assertNotNull("Active date time should not be null in SoftwareEnforced"
                            + " authorization list.", activeDateTime);
            assertNotNull("Origination expiration date time should not be null in"
                            + " SoftwareEnforced authorization list.",
                    originationExpirationDateTime);
            assertNotNull("Usage expiration date time should not be null in SoftwareEnforced"
                            + " authorization list.", usageExpirationDateTime);

            assertNull("Active date time must not be included in TeeEnforced authorization list.",
                    nonValidityPeriodList.getActiveDateTime());
            assertNull("Origination date time must not be included in TeeEnforced authorization"
                            + "list.", nonValidityPeriodList.getOriginationExpireDateTime());
            assertNull("Usage expiration date time must not be included in TeeEnforced"
                            + " authorization list.",
                    nonValidityPeriodList.getUsageExpireDateTime());

            assertThat("Origination expiration date time must match with provided expiration"
                            + " date time.", originationExpirationDateTime.getTime(),
                    is(startTime.getTime() + ORIGINATION_TIME_OFFSET));
            assertThat("Usage (consumption) expiration date time must match with provided"
                            + " expiration date time.", usageExpirationDateTime.getTime(),
                    is(startTime.getTime() + CONSUMPTION_TIME_OFFSET));
        }
    }

    private void checkDigests(Attestation attestation, Set<Integer> expectedDigests) {
        Set<Integer> softwareEnforcedDigests = attestation.getSoftwareEnforced().getDigests();
        Set<Integer> teeEnforcedDigests = attestation.getTeeEnforced().getDigests();

        if (softwareEnforcedDigests == null) {
            softwareEnforcedDigests = ImmutableSet.of();
        }
        if (teeEnforcedDigests == null) {
            teeEnforcedDigests = ImmutableSet.of();
        }

        Set<Integer> allDigests = ImmutableSet.<Integer> builder()
                .addAll(softwareEnforcedDigests)
                .addAll(teeEnforcedDigests)
                .build();
        Set<Integer> intersection = new ArraySet<>();
        intersection.addAll(softwareEnforcedDigests);
        intersection.retainAll(teeEnforcedDigests);

        assertThat("Set of digests from software enforced and Tee enforced must match"
                + " with expected digests set.", allDigests, is(expectedDigests));
        assertTrue("Digest sets must be disjoint", intersection.isEmpty());

        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE
                || attestation.getKeymasterVersion() == 0) {
            assertThat("Digests in software-enforced",
                    softwareEnforcedDigests, is(expectedDigests));
        } else {
            if (attestation.getKeymasterVersion() == 1) {
                // KM1 implementations may not support SHA512 in the TEE
                assertTrue("KeyMaster version 1 may not support SHA256, in which case it must be"
                        + " software-emulated.",
                        softwareEnforcedDigests.contains(KM_DIGEST_SHA_2_512)
                        || teeEnforcedDigests.contains(KM_DIGEST_SHA_2_512));

                assertThat("Tee enforced digests should have digests {none and SHA2-256}",
                        teeEnforcedDigests, hasItems(KM_DIGEST_NONE, KM_DIGEST_SHA_2_256));
            } else {
                assertThat("Tee enforced digests should have all expected digests.",
                        teeEnforcedDigests, is(expectedDigests));
            }
        }
    }

    private Set<Integer> checkPurposes(Attestation attestation, int purposes) {
        Set<Integer> expectedPurposes = buildPurposeSet(purposes);
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE
                || attestation.getKeymasterVersion() == 0) {
            assertThat("Purposes in software-enforced should match expected set",
                    attestation.getSoftwareEnforced().getPurposes(), is(expectedPurposes));
            assertNull("Should be no purposes in TEE-enforced",
                    attestation.getTeeEnforced().getPurposes());
        } else {
            assertThat("Purposes in TEE-enforced should match expected set",
                    attestation.getTeeEnforced().getPurposes(), is(expectedPurposes));
            assertNull("No purposes in software-enforced",
                    attestation.getSoftwareEnforced().getPurposes());
        }
        return expectedPurposes;
    }

    private void checkSystemPatchLevel(int teeOsPatchLevel, int systemPatchLevel) {
        if (TestUtils.isGsiImage()) {
            // b/168663786: When using a GSI image, the system patch level might be
            // greater than or equal to the OS patch level reported from TEE.
            assertThat("For GSI image TEE os patch level should be less than or equal to system"
                    + " patch level.", teeOsPatchLevel, lessThanOrEqualTo(systemPatchLevel));
        } else {
            assertThat("TEE os patch level must be equal to system patch level.",
                    teeOsPatchLevel, is(systemPatchLevel));
        }
    }

    @SuppressWarnings("unchecked")
    private void checkAttestationSecurityLevelDependentParams(Attestation attestation) {
        assertThat("Attestation version must be one of: {1, 2, 3, 4, 100, 200, 300}",
                attestation.getAttestationVersion(),
                either(is(1)).or(is(2)).or(is(3)).or(is(4)).or(is(100)).or(is(200)).or(is(300)));

        AuthorizationList teeEnforced = attestation.getTeeEnforced();
        AuthorizationList softwareEnforced = attestation.getSoftwareEnforced();

        int systemOsVersion = getSystemOsVersion();
        int systemPatchLevel = getSystemPatchLevel();

        switch (attestation.getAttestationSecurityLevel()) {
            case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                assertThat("TEE attestation can only come from TEE keymaster",
                        attestation.getKeymasterSecurityLevel(),
                        is(KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT));
                assertThat("KeyMaster version is not valid.", attestation.getKeymasterVersion(),
                           either(is(2)).or(is(3)).or(is(4)).or(is(41))
                           .or(is(100)).or(is(200)).or(is(300)));

                checkRootOfTrust(attestation, false /* requireLocked */);
                assertThat("TEE enforced OS version and system OS version must be same.",
                        teeEnforced.getOsVersion(), is(systemOsVersion));
                checkSystemPatchLevel(teeEnforced.getOsPatchLevel(), systemPatchLevel);
                break;

            case KM_SECURITY_LEVEL_STRONG_BOX:
                assertThat("StrongBox attestation can only come from StrongBox keymaster",
                        attestation.getKeymasterSecurityLevel(),
                        is(KM_SECURITY_LEVEL_STRONG_BOX));
                assertThat("KeyMaster version is not valid.", attestation.getKeymasterVersion(),
                        either(is(2)).or(is(3)).or(is(4)).or(is(41))
                                .or(is(100)).or(is(200)).or(is(300)));

                checkRootOfTrust(attestation, false /* requireLocked */);
                assertThat("StrongBox enforced OS version and system OS version must be same.",
                        teeEnforced.getOsVersion(), is(systemOsVersion));
                checkSystemPatchLevel(teeEnforced.getOsPatchLevel(), systemPatchLevel);
                break;

            case KM_SECURITY_LEVEL_SOFTWARE:
                if (attestation
                        .getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
                    assertThat("TEE KM version must be 0 or 1 with software attestation",
                            attestation.getKeymasterVersion(), either(is(0)).or(is(1)));
                } else {
                    assertThat("Software KM is version 3", attestation.getKeymasterVersion(),
                            is(3));
                    assertThat("Software enforced OS version and System OS version must be same.",
                            softwareEnforced.getOsVersion(), is(systemOsVersion));
                    checkSystemPatchLevel(softwareEnforced.getOsPatchLevel(), systemPatchLevel);
                }

                assertNull("Software attestation cannot provide root of trust",
                        teeEnforced.getRootOfTrust());

                break;

            default:
                fail("Invalid attestation security level: "
                        + attestation.getAttestationSecurityLevel());
                break;
        }
    }

    private void checkDeviceLocked(Attestation attestation) {
        assertThat("Attestation version must be >= 1",
                attestation.getAttestationVersion(), greaterThanOrEqualTo(1));

        int attestationSecurityLevel = attestation.getAttestationSecurityLevel();
        switch (attestationSecurityLevel) {
            case KM_SECURITY_LEVEL_STRONG_BOX:
            case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                assertThat("Attestation security level doesn't match keymaster security level",
                        attestation.getKeymasterSecurityLevel(), is(attestationSecurityLevel));
                assertThat("Keymaster version should be greater than or equal to 2.",
                        attestation.getKeymasterVersion(), greaterThanOrEqualTo(2));

                // Devices launched in Android 10.0 (API level 29) and after should run CTS
                // in LOCKED state.
                boolean requireLocked = PropertyUtil.getFirstApiLevel() >= 29;
                checkRootOfTrust(attestation, requireLocked);
                break;

            case KM_SECURITY_LEVEL_SOFTWARE:
            default:
                // TEE attestation has been required since Android 7.0.
                fail("Unexpected attestation security level: " +
                     attestation.securityLevelToString(attestationSecurityLevel));
                break;
        }
    }

    private void checkVerifiedBootHash(byte[] verifiedBootHash) {
        assertNotNull(verifiedBootHash);
        StringBuilder hexVerifiedBootHash = new StringBuilder(verifiedBootHash.length * 2);
        for (byte b : verifiedBootHash) {
            hexVerifiedBootHash.append(String.format("%02x", b));
        }
        String bootVbMetaDigest = SystemProperties.get("ro.boot.vbmeta.digest", "");
        assertEquals(
                "VerifiedBootHash field of RootOfTrust section does not match with"
                        + "system property ro.boot.vbmeta.digest",
                bootVbMetaDigest, hexVerifiedBootHash.toString());
    }

    private void checkRootOfTrust(Attestation attestation, boolean requireLocked) {
        RootOfTrust rootOfTrust = attestation.getRootOfTrust();
        assertNotNull(rootOfTrust);
        assertNotNull(rootOfTrust.getVerifiedBootKey());
        assertTrue("Verified boot key is only " + rootOfTrust.getVerifiedBootKey().length +
                   " bytes long", rootOfTrust.getVerifiedBootKey().length >= 32);
        if (requireLocked) {
            final String unlockedDeviceMessage = "The device's bootloader must be locked. This may "
                    + "not be the default for pre-production devices.";
            assertTrue(unlockedDeviceMessage, rootOfTrust.isDeviceLocked());
            checkEntropy(rootOfTrust.getVerifiedBootKey());
            assertEquals(KM_VERIFIED_BOOT_VERIFIED, rootOfTrust.getVerifiedBootState());
            if (PropertyUtil.getFirstApiLevel() < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Verified boot hash was not previously checked in CTS, so set an api level check
                // to avoid running into waiver issues.
                return;
            }
            assertNotNull(rootOfTrust.getVerifiedBootHash());
            assertEquals(32, rootOfTrust.getVerifiedBootHash().length);
            checkEntropy(rootOfTrust.getVerifiedBootHash());
            checkVerifiedBootHash(rootOfTrust.getVerifiedBootHash());
        }
    }

    private void checkEntropy(byte[] verifiedBootKey) {
        assertTrue("Failed Shannon entropy check", checkShannonEntropy(verifiedBootKey));
        assertTrue("Failed BiEntropy check", checkTresBiEntropy(verifiedBootKey));
    }

    private boolean checkShannonEntropy(byte[] verifiedBootKey) {
        double probabilityOfSetBit = countSetBits(verifiedBootKey) / (double)(verifiedBootKey.length * 8);
        return calculateShannonEntropy(probabilityOfSetBit) > 0.8;
    }

    private double calculateShannonEntropy(double probabilityOfSetBit) {
        if (probabilityOfSetBit <= 0.001 || probabilityOfSetBit >= .999) return 0;
        double entropy = (-probabilityOfSetBit * logTwo(probabilityOfSetBit)) -
                            ((1 - probabilityOfSetBit) * logTwo(1 - probabilityOfSetBit));
        Log.i(TAG, "Shannon entropy of VB Key: " + entropy);
        return entropy;
    }

    private boolean checkTresBiEntropy(byte[] verifiedBootKey) {
        double weightingFactor = 0;
        double weightedEntropy = 0;
        double probabilityOfSetBit = 0;
        int length = verifiedBootKey.length * 8;
        for(int i = 0; i < (verifiedBootKey.length * 8) - 2; i++) {
            probabilityOfSetBit = countSetBits(verifiedBootKey) / (double)length;
            weightingFactor += logTwo(i+2);
            weightedEntropy += calculateShannonEntropy(probabilityOfSetBit) * logTwo(i+2);
            deriveBitString(verifiedBootKey, length);
            length -= 1;
        }
        double tresBiEntropy = (1 / weightingFactor) * weightedEntropy;
        Log.i(TAG, "BiEntropy of VB Key: " + tresBiEntropy);
        return tresBiEntropy > 0.9;
    }

    private void deriveBitString(byte[] bitString, int activeLength) {
        int length = activeLength / 8;
        if (activeLength % 8 != 0) {
            length += 1;
        }

        byte mask = (byte)((byte)0x80 >>> ((activeLength + 6) % 8));
        if (activeLength % 8 == 1) {
            mask = (byte)~mask;
        }

        for(int i = 0; i < length; i++) {
            if (i == length - 1) {
                bitString[i] ^= ((bitString[i] & 0xFF) << 1);
                bitString[i] &= mask;
            } else {
                bitString[i] ^= ((bitString[i] & 0xFF) << 1) | ((bitString[i+1] & 0xFF) >>> 7);
            }
        }
    }

    private double logTwo(double value) {
        return Math.log(value) / Math.log(2);
    }

    private int countSetBits(byte[] toCount) {
        int setBitCount = 0;
        for(int i = 0; i < toCount.length; i++) {
            setBitCount += countSetBits(toCount[i]);
        }
        return setBitCount;
    }

    private int countSetBits(byte toCount) {
        int setBitCounter = 0;
        while(toCount != 0) {
            toCount &= (toCount - 1);
            setBitCounter++;
        }
        return setBitCounter;
    }

    private void checkRsaKeyDetails(Attestation attestation, int keySize, int purposes,
            Set<String> expectedPaddingModes) throws CertificateParsingException {
        AuthorizationList keyDetailsList;
        AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                || attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_STRONG_BOX) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }
        assertEquals(keySize, keyDetailsList.getKeySize().intValue());
        assertNull(nonKeyDetailsList.getKeySize());

        assertEquals(KM_ALGORITHM_RSA, keyDetailsList.getAlgorithm().intValue());
        assertNull(nonKeyDetailsList.getAlgorithm());

        assertNull(keyDetailsList.getEcCurve());
        assertNull(nonKeyDetailsList.getEcCurve());

        assertEquals(65537, keyDetailsList.getRsaPublicExponent().longValue());
        assertNull(nonKeyDetailsList.getRsaPublicExponent());

        Set<String> paddingModes;
        if (attestation.getKeymasterVersion() == 0) {
            // KM0 implementations don't support padding info, so it's always in the
            // software-enforced list.
            paddingModes = attestation.getSoftwareEnforced().getPaddingModesAsStrings();
            assertNull(attestation.getTeeEnforced().getPaddingModes());
        } else {
            paddingModes = keyDetailsList.getPaddingModesAsStrings();
            assertNull(nonKeyDetailsList.getPaddingModes());
        }

        // KM1 implementations may add ENCRYPTION_PADDING_NONE to the list of paddings.
        Set<String> km1PossiblePaddingModes = expectedPaddingModes;
        if (attestation.getKeymasterVersion() == 1 &&
                attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            builder.addAll(expectedPaddingModes);
            builder.add(ENCRYPTION_PADDING_NONE);
            km1PossiblePaddingModes = builder.build();
        }

        assertThat("Attested padding mode does not matched with expected modes.",
                paddingModes, either(is(expectedPaddingModes)).or(is(km1PossiblePaddingModes)));
    }

    private void checkEcKeyDetails(Attestation attestation, String ecCurve, int keySize) {
        AuthorizationList keyDetailsList;
        AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                || attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_STRONG_BOX) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }
        assertEquals(keySize, keyDetailsList.getKeySize().intValue());
        assertNull(nonKeyDetailsList.getKeySize());
        assertEquals(KM_ALGORITHM_EC, keyDetailsList.getAlgorithm().intValue());
        assertNull(nonKeyDetailsList.getAlgorithm());
        assertEquals(ecCurve, keyDetailsList.ecCurveAsString());
        assertNull(nonKeyDetailsList.getEcCurve());
        assertNull(keyDetailsList.getRsaPublicExponent());
        assertNull(nonKeyDetailsList.getRsaPublicExponent());
        assertNull(keyDetailsList.getPaddingModes());
        assertNull(nonKeyDetailsList.getPaddingModes());
    }

    private boolean isEncryptionPurpose(int purposes) {
        return (purposes & PURPOSE_DECRYPT) != 0 || (purposes & PURPOSE_ENCRYPT) != 0;
    }

    private boolean isSignaturePurpose(int purposes) {
        return (purposes & PURPOSE_SIGN) != 0 || (purposes & PURPOSE_VERIFY) != 0;
    }

    private boolean isAgreeKeyPurpose(int purposes) {
        return (purposes & PURPOSE_AGREE_KEY) != 0;
    }

    private ImmutableSet<Integer> buildPurposeSet(int purposes) {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        if ((purposes & PURPOSE_SIGN) != 0)
            builder.add(KM_PURPOSE_SIGN);
        if ((purposes & PURPOSE_VERIFY) != 0)
            builder.add(KM_PURPOSE_VERIFY);
        if ((purposes & PURPOSE_ENCRYPT) != 0)
            builder.add(KM_PURPOSE_ENCRYPT);
        if ((purposes & PURPOSE_DECRYPT) != 0)
            builder.add(KM_PURPOSE_DECRYPT);
        if ((purposes & PURPOSE_AGREE_KEY) != 0) {
            builder.add(KM_PURPOSE_AGREE_KEY);
        }
        return builder.build();
    }

    private void generateKey(KeyGenParameterSpec spec, String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm, "AndroidKeyStore");
        keyGenerator.init(spec);
        keyGenerator.generateKey();
    }

    private void generateKeyPair(String algorithm, KeyGenParameterSpec spec)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm,
                "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        keyPairGenerator.generateKeyPair();
    }

    public static void verifyCertificateChain(Certificate[] certChain, boolean expectStrongBox)
            throws GeneralSecurityException {
        assertNotNull(certChain);
        boolean strongBoxSubjectFound = false;
        for (int i = 1; i < certChain.length; ++i) {
            try {
                PublicKey pubKey = certChain[i].getPublicKey();
                certChain[i - 1].verify(pubKey);
                if (i == certChain.length - 1) {
                    // Last cert should be self-signed.
                    certChain[i].verify(pubKey);
                }

                // Check that issuer in the signed cert matches subject in the signing cert.
                X509Certificate x509CurrCert = (X509Certificate) certChain[i];
                X509Certificate x509PrevCert = (X509Certificate) certChain[i - 1];
                X500Name signingCertSubject =
                        new JcaX509CertificateHolder(x509CurrCert).getSubject();
                X500Name signedCertIssuer =
                        new JcaX509CertificateHolder(x509PrevCert).getIssuer();
                // Use .toASN1Object().equals() rather than .equals() because .equals() is case
                // insensitive, and we want to verify an exact match.
                assertTrue(String.format("Certificate Issuer (%s) is not matching with parent"
                            + " certificate's Subject (%s).",
                                signedCertIssuer.toString(), signingCertSubject.toString()),
                        signedCertIssuer.toASN1Object().equals(signingCertSubject.toASN1Object()));

                X500Name signedCertSubject =
                        new JcaX509CertificateHolder(x509PrevCert).getSubject();
                if (i == 1) {
                    // First cert should have subject "CN=Android Keystore Key".
                    assertEquals(signedCertSubject, new X500Name("CN=Android Keystore Key"));
                } else if (signedCertSubject.toString().toLowerCase().contains("strongbox")) {
                    strongBoxSubjectFound = true;
                }
            } catch (InvalidKeyException | CertificateException | NoSuchAlgorithmException
                    | NoSuchProviderException | SignatureException e) {
                throw new GeneralSecurityException("Using StrongBox: " + expectStrongBox + "\n"
                                + "Failed to verify certificate " + certChain[i - 1]
                                + " with public key " + certChain[i].getPublicKey(),
                        e);
            }
        }
        // At least one intermediate in a StrongBox chain must have "strongbox" in the subject.
        assertEquals(expectStrongBox, strongBoxSubjectFound);
    }

    private void testDeviceIdAttestationFailure(int idType,
            String acceptableDeviceIdAttestationFailureMessage) throws Exception {
        try {
            AttestationUtils.attestDeviceIds(getContext(), new int[] {idType}, "123".getBytes());
            fail("Attestation should have failed.");
        } catch (SecurityException e) {
            // Attestation is expected to fail. If the device has the device ID type we are trying
            // to attest, it should fail with a SecurityException as we do not hold
            // READ_PRIVILEGED_PHONE_STATE permission.
        } catch (DeviceIdAttestationException e) {
            // Attestation is expected to fail. If the device does not have the device ID type we
            // are trying to attest (e.g. no IMEI on devices without a radio), it should fail with
            // a corresponding DeviceIdAttestationException.
            if (acceptableDeviceIdAttestationFailureMessage == null ||
                    !acceptableDeviceIdAttestationFailureMessage.equals(e.getMessage())) {
                throw e;
            }
        }
    }
}
