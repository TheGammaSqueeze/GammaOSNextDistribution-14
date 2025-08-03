/*
 * Copyright 2015 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.testng.Assert.assertThrows;

import android.os.Process;
import android.security.Flags;
import android.security.keystore.AttestationUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.test.MoreAsserts;

import org.junit.Test;

import java.math.BigInteger;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.security.auth.x500.X500Principal;

public class KeyGenParameterSpecTest {

    private static final X500Principal DEFAULT_CERT_SUBJECT = new X500Principal("CN=fake");
    private static final BigInteger DEFAULT_CERT_SERIAL_NUMBER = new BigInteger("1");
    private static final Date DEFAULT_CERT_NOT_BEFORE = new Date(0L); // Jan 1 1970
    private static final Date DEFAULT_CERT_NOT_AFTER = new Date(2461449600000L); // Jan 1 2048

    @Test
    public void testDefaults() {
        // Set only the mandatory parameters and assert values returned by getters.

        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                "arbitrary", KeyProperties.PURPOSE_ENCRYPT)
                .build();

        assertEquals("arbitrary", spec.getKeystoreAlias());
        assertEquals(KeyProperties.PURPOSE_ENCRYPT, spec.getPurposes());
        assertNull(null, spec.getAlgorithmParameterSpec());
        MoreAsserts.assertEmpty(Arrays.asList(spec.getBlockModes()));
        assertEquals(DEFAULT_CERT_NOT_BEFORE, spec.getCertificateNotBefore());
        assertEquals(DEFAULT_CERT_NOT_AFTER, spec.getCertificateNotAfter());
        assertEquals(DEFAULT_CERT_SERIAL_NUMBER, spec.getCertificateSerialNumber());
        assertEquals(DEFAULT_CERT_SUBJECT, spec.getCertificateSubject());
        assertFalse(spec.isDigestsSpecified());
        try {
            spec.getDigests();
            fail();
        } catch (IllegalStateException expected) {}
        if (Flags.mgf1DigestSetterV2()) {
            assertFalse(spec.isMgf1DigestsSpecified());
            assertThrows(IllegalStateException.class, spec::getMgf1Digests);
        }
        MoreAsserts.assertEmpty(Arrays.asList(spec.getEncryptionPaddings()));
        assertEquals(-1, spec.getKeySize());
        assertNull(spec.getKeyValidityStart());
        assertNull(spec.getKeyValidityForOriginationEnd());
        assertNull(spec.getKeyValidityForConsumptionEnd());
        assertTrue(spec.isRandomizedEncryptionRequired());
        MoreAsserts.assertEmpty(Arrays.asList(spec.getSignaturePaddings()));
        assertFalse(spec.isUserAuthenticationRequired());
        assertEquals(0, spec.getUserAuthenticationValidityDurationSeconds());
        assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, spec.getUserAuthenticationType());
        assertNull(spec.getAttestationChallenge());
        assertFalse(spec.isDevicePropertiesAttestationIncluded());
        assertEquals(0, spec.getAttestationIds().length);
        assertFalse(spec.isUniqueIdIncluded());
        assertTrue(spec.isInvalidatedByBiometricEnrollment());
        assertFalse(spec.isStrongBoxBacked());
        assertFalse(spec.isUnlockedDeviceRequired());
        assertEquals(KeyProperties.UNRESTRICTED_USAGE_COUNT, spec.getMaxUsageCount());
        assertFalse(spec.isCriticalToDeviceEncryption());
    }

    @Test
    public void testSettersReflectedInGetters() {
        // Set all parameters to non-default values and then assert that getters reflect that.

        Date certNotBeforeDate = new Date(System.currentTimeMillis());
        Date certNotAfterDate = new Date(System.currentTimeMillis() + 12345678);
        Date keyValidityStartDate = new Date(System.currentTimeMillis() - 2222222);
        Date keyValidityEndDateForOrigination = new Date(System.currentTimeMillis() + 11111111);
        Date keyValidityEndDateForConsumption = new Date(System.currentTimeMillis() + 33333333);
        AlgorithmParameterSpec algSpecificParams = new ECGenParameterSpec("secp256r1");
        int maxUsageCount = 1;

        byte[] challenge = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        int[] ids = new int[]{AttestationUtils.ID_TYPE_SERIAL,
            AttestationUtils.ID_TYPE_IMEI,
            AttestationUtils.ID_TYPE_MEID,
            AttestationUtils.USE_INDIVIDUAL_ATTESTATION};
        KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(
                "arbitrary", KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT)
                .setAlgorithmParameterSpec(algSpecificParams)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CBC)
                .setCertificateNotBefore(certNotBeforeDate)
                .setCertificateNotAfter(certNotAfterDate)
                .setCertificateSerialNumber(new BigInteger("13946146"))
                .setCertificateSubject(new X500Principal("CN=test"))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                        KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(1234)
                .setKeyValidityStart(keyValidityStartDate)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setRandomizedEncryptionRequired(false)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(12345,
                        KeyProperties.AUTH_DEVICE_CREDENTIAL | KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setAttestationChallenge(challenge)
                .setDevicePropertiesAttestationIncluded(true)
                .setAttestationIds(ids)
                .setUnlockedDeviceRequired(true)
                .setMaxUsageCount(maxUsageCount);

        if (Flags.mgf1DigestSetterV2()) {
            specBuilder.setMgf1Digests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384);
        }
        KeyGenParameterSpec spec = specBuilder.build();

        assertEquals("arbitrary", spec.getKeystoreAlias());
        assertEquals(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT, spec.getPurposes());
        assertSame(algSpecificParams, spec.getAlgorithmParameterSpec());
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getBlockModes()),
                KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CBC);
        assertEquals(certNotBeforeDate, spec.getCertificateNotBefore());
        assertEquals(certNotAfterDate, spec.getCertificateNotAfter());
        assertEquals(new BigInteger("13946146"), spec.getCertificateSerialNumber());
        assertEquals(new X500Principal("CN=test"), spec.getCertificateSubject());
        assertTrue(spec.isDigestsSpecified());
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getDigests()),
                KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384);
        if (Flags.mgf1DigestSetterV2()) {
            assertTrue(spec.isMgf1DigestsSpecified());
            /* Since getMgf1Digest return type is Set, objects could be in any order. */
            MoreAsserts.assertContentsInAnyOrder(spec.getMgf1Digests(),
                    KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384);
        }
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getEncryptionPaddings()),
                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP, KeyProperties.ENCRYPTION_PADDING_PKCS7);
        assertEquals(1234, spec.getKeySize());
        assertEquals(keyValidityStartDate, spec.getKeyValidityStart());
        assertEquals(keyValidityEndDateForOrigination, spec.getKeyValidityForOriginationEnd());
        assertEquals(keyValidityEndDateForConsumption, spec.getKeyValidityForConsumptionEnd());
        assertFalse(spec.isRandomizedEncryptionRequired());
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getSignaturePaddings()),
                KeyProperties.SIGNATURE_PADDING_RSA_PSS, KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);
        assertTrue(spec.isUserAuthenticationRequired());
        assertEquals(12345, spec.getUserAuthenticationValidityDurationSeconds());
        assertEquals(KeyProperties.AUTH_DEVICE_CREDENTIAL | KeyProperties.AUTH_BIOMETRIC_STRONG,
                spec.getUserAuthenticationType());
        assertArrayEquals(challenge, spec.getAttestationChallenge());
        assertTrue(spec.isDevicePropertiesAttestationIncluded());
        assertArrayEquals(ids, spec.getAttestationIds());
        assertTrue(spec.isUnlockedDeviceRequired());
        assertEquals(maxUsageCount, spec.getMaxUsageCount());
    }

    @Test
    public void testNullAliasNotPermitted() {
        try {
            new KeyGenParameterSpec.Builder(null, KeyProperties.PURPOSE_ENCRYPT);
            fail();
        } catch (NullPointerException expected) {}
    }

    @Test
    public void testEmptyAliasNotPermitted() {
        try {
            new KeyGenParameterSpec.Builder("", KeyProperties.PURPOSE_ENCRYPT);
            fail();
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void testSetKeyValidityEndDateAppliesToBothEndDates() {
        Date date = new Date(System.currentTimeMillis() + 555555);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                "ignore", KeyProperties.PURPOSE_VERIFY)
                .setKeyValidityEnd(date)
                .build();
        assertEquals(date, spec.getKeyValidityForOriginationEnd());
        assertEquals(date, spec.getKeyValidityForConsumptionEnd());
    }

    @Test
    public void testSetUserAuthenticationValidityDurationSecondsValidityCheck() {
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder("alias", 0);
        try {
            builder.setUserAuthenticationValidityDurationSeconds(-2);
            fail();
        } catch (IllegalArgumentException expected) {}

        try {
            builder.setUserAuthenticationValidityDurationSeconds(-100);
            fail();
        } catch (IllegalArgumentException expected) {}

        try {
            builder.setUserAuthenticationValidityDurationSeconds(Integer.MIN_VALUE);
            fail();
        } catch (IllegalArgumentException expected) {}

        builder.setUserAuthenticationValidityDurationSeconds(-1);
        builder.setUserAuthenticationValidityDurationSeconds(0);
        builder.setUserAuthenticationValidityDurationSeconds(1);
        builder.setUserAuthenticationValidityDurationSeconds(Integer.MAX_VALUE);

        try {
            builder.setUserAuthenticationValidityDurationSeconds(-2);
            fail();
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void testImmutabilityViaSetterParams() {
        // Assert that all mutable parameters provided to setters are copied to ensure that values
        // returned by getters never change.
        String[] blockModes =
                new String[] {KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CBC};
        String[] originalBlockModes = blockModes.clone();
        Date certNotBeforeDate = new Date(System.currentTimeMillis());
        Date originalCertNotBeforeDate = new Date(certNotBeforeDate.getTime());
        Date certNotAfterDate = new Date(System.currentTimeMillis() + 12345678);
        Date originalCertNotAfterDate = new Date(certNotAfterDate.getTime());
        Date keyValidityStartDate = new Date(System.currentTimeMillis() - 2222222);
        Date originalKeyValidityStartDate = new Date(keyValidityStartDate.getTime());
        Date keyValidityEndDateForOrigination = new Date(System.currentTimeMillis() + 11111111);
        Date originalKeyValidityEndDateForOrigination =
                new Date(keyValidityEndDateForOrigination.getTime());
        Date keyValidityEndDateForConsumption = new Date(System.currentTimeMillis() + 33333333);
        Date originalKeyValidityEndDateForConsumption =
                new Date(keyValidityEndDateForConsumption.getTime());
        String[] digests = new String[] {KeyProperties.DIGEST_MD5, KeyProperties.DIGEST_SHA512};
        String[] originalDigests = digests.clone();
        String[] mgfDigests = new String[] {KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512};
        String[] originalMgfDigests = mgfDigests.clone();
        String[] encryptionPaddings = new String[] {
                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP, KeyProperties.ENCRYPTION_PADDING_PKCS7};
        String[] originalEncryptionPaddings = encryptionPaddings.clone();
        String[] signaturePaddings = new String[] {
                KeyProperties.SIGNATURE_PADDING_RSA_PSS, KeyProperties.SIGNATURE_PADDING_RSA_PKCS1};
        String[] originalSignaturePaddings = signaturePaddings.clone();

        KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(
                "arbitrary", KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(blockModes)
                .setCertificateNotBefore(certNotBeforeDate)
                .setCertificateNotAfter(certNotAfterDate)
                .setDigests(digests)
                .setEncryptionPaddings(encryptionPaddings)
                .setKeyValidityStart(keyValidityStartDate)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setSignaturePaddings(signaturePaddings);

        if (Flags.mgf1DigestSetterV2()) {
            specBuilder.setMgf1Digests(mgfDigests);
        }
        KeyGenParameterSpec spec = specBuilder.build();

        assertEquals(Arrays.asList(originalBlockModes), Arrays.asList(spec.getBlockModes()));
        blockModes[0] = null;
        assertEquals(Arrays.asList(originalBlockModes), Arrays.asList(spec.getBlockModes()));

        assertEquals(originalCertNotBeforeDate, spec.getCertificateNotBefore());
        certNotBeforeDate.setTime(1234567890L);
        assertEquals(originalCertNotBeforeDate, spec.getCertificateNotBefore());

        assertEquals(originalCertNotAfterDate, spec.getCertificateNotAfter());
        certNotAfterDate.setTime(1234567890L);
        assertEquals(originalCertNotAfterDate, spec.getCertificateNotAfter());

        assertEquals(Arrays.asList(originalDigests), Arrays.asList(spec.getDigests()));
        digests[1] = null;
        assertEquals(Arrays.asList(originalDigests), Arrays.asList(spec.getDigests()));

        if (Flags.mgf1DigestSetterV2()) {
            assertTrue(Arrays.asList(originalMgfDigests).containsAll(spec.getMgf1Digests()));
            mgfDigests[1] = null;
            assertTrue(Arrays.asList(originalMgfDigests).containsAll(spec.getMgf1Digests()));
        }

        assertEquals(Arrays.asList(originalEncryptionPaddings),
                Arrays.asList(spec.getEncryptionPaddings()));
        encryptionPaddings[0] = null;
        assertEquals(Arrays.asList(originalEncryptionPaddings),
                Arrays.asList(spec.getEncryptionPaddings()));

        assertEquals(originalKeyValidityStartDate, spec.getKeyValidityStart());
        keyValidityStartDate.setTime(1234567890L);
        assertEquals(originalKeyValidityStartDate, spec.getKeyValidityStart());

        assertEquals(originalKeyValidityEndDateForOrigination,
                spec.getKeyValidityForOriginationEnd());
        keyValidityEndDateForOrigination.setTime(1234567890L);
        assertEquals(originalKeyValidityEndDateForOrigination,
                spec.getKeyValidityForOriginationEnd());

        assertEquals(originalKeyValidityEndDateForConsumption,
                spec.getKeyValidityForConsumptionEnd());
        keyValidityEndDateForConsumption.setTime(1234567890L);
        assertEquals(originalKeyValidityEndDateForConsumption,
                spec.getKeyValidityForConsumptionEnd());

        assertEquals(Arrays.asList(originalSignaturePaddings),
                Arrays.asList(spec.getSignaturePaddings()));
        signaturePaddings[1] = null;
        assertEquals(Arrays.asList(originalSignaturePaddings),
                Arrays.asList(spec.getSignaturePaddings()));
    }

    @Test
    public void testImmutabilityViaGetterReturnValues() {
        // Assert that none of the mutable return values from getters modify the state of the spec.

        KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(
                "arbitrary", KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CBC)
                .setCertificateNotBefore(new Date(System.currentTimeMillis()))
                .setCertificateNotAfter(new Date(System.currentTimeMillis() + 12345678))
                .setDigests(KeyProperties.DIGEST_MD5, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                        KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeyValidityStart(new Date(System.currentTimeMillis() - 2222222))
                .setKeyValidityForOriginationEnd(new Date(System.currentTimeMillis() + 11111111))
                .setKeyValidityForConsumptionEnd(new Date(System.currentTimeMillis() + 33333333))
                .setSignaturePaddings(
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);

        if (Flags.mgf1DigestSetterV2()) {
            specBuilder.setMgf1Digests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512);
        }
        KeyGenParameterSpec spec = specBuilder.build();

        String[] originalBlockModes = spec.getBlockModes().clone();
        spec.getBlockModes()[0] = null;
        assertEquals(Arrays.asList(originalBlockModes), Arrays.asList(spec.getBlockModes()));

        Date originalCertNotBeforeDate = (Date) spec.getCertificateNotBefore().clone();
        spec.getCertificateNotBefore().setTime(1234567890L);
        assertEquals(originalCertNotBeforeDate, spec.getCertificateNotBefore());

        Date originalCertNotAfterDate = (Date) spec.getCertificateNotAfter().clone();
        spec.getCertificateNotAfter().setTime(1234567890L);
        assertEquals(originalCertNotAfterDate, spec.getCertificateNotAfter());

        String[] originalDigests = spec.getDigests().clone();
        spec.getDigests()[0] = null;
        assertEquals(Arrays.asList(originalDigests), Arrays.asList(spec.getDigests()));

        String[] originalEncryptionPaddings = spec.getEncryptionPaddings().clone();
        spec.getEncryptionPaddings()[0] = null;
        assertEquals(Arrays.asList(originalEncryptionPaddings),
                Arrays.asList(spec.getEncryptionPaddings()));

        Date originalKeyValidityStartDate = (Date) spec.getKeyValidityStart().clone();
        spec.getKeyValidityStart().setTime(1234567890L);
        assertEquals(originalKeyValidityStartDate, spec.getKeyValidityStart());

        Date originalKeyValidityEndDateForOrigination =
                (Date) spec.getKeyValidityForOriginationEnd().clone();
        spec.getKeyValidityForOriginationEnd().setTime(1234567890L);
        assertEquals(originalKeyValidityEndDateForOrigination,
                spec.getKeyValidityForOriginationEnd());

        Date originalKeyValidityEndDateForConsumption =
                (Date) spec.getKeyValidityForConsumptionEnd().clone();
        spec.getKeyValidityForConsumptionEnd().setTime(1234567890L);
        assertEquals(originalKeyValidityEndDateForConsumption,
                spec.getKeyValidityForConsumptionEnd());

        String[] originalSignaturePaddings = spec.getSignaturePaddings().clone();
        spec.getSignaturePaddings()[0] = null;
        assertEquals(Arrays.asList(originalSignaturePaddings),
                Arrays.asList(spec.getSignaturePaddings()));
    }

    /**
     * Test that generating a key with UID throws an exception since CTS doesn't have the necessary
     * permissions.
     */
    @Test
    public void testBuilderSetUidGenerateKeyThrowsException() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore");
        keyGenerator.init(
                new KeyGenParameterSpec.Builder("alias", KeyProperties.PURPOSE_SIGN)
                        .setUid(Process.WIFI_UID)
                        .build());
        assertThrows(ProviderException.class, keyGenerator::generateKey);
    }

    @Test
    public void testIllegalMaxUsageCountNotPermitted() {
        try {
            new KeyGenParameterSpec.Builder("LimitedUseKey", KeyProperties.PURPOSE_ENCRYPT)
            .setMaxUsageCount(0);
            fail();
        } catch (IllegalArgumentException expected) {}
    }
}
