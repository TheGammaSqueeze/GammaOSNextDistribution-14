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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.security.Flags;
import android.security.GateKeeper;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.test.MoreAsserts;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Date;

@RunWith(AndroidJUnit4.class)
public class KeyProtectionTest {
    @Test
    public void testDefaults() {
        // Set only the mandatory parameters and assert values returned by getters.

        KeyProtection spec = new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT)
                .build();

        assertEquals(KeyProperties.PURPOSE_ENCRYPT, spec.getPurposes());
        MoreAsserts.assertEmpty(Arrays.asList(spec.getBlockModes()));
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
        assertNull(spec.getKeyValidityStart());
        assertNull(spec.getKeyValidityForOriginationEnd());
        assertNull(spec.getKeyValidityForConsumptionEnd());
        assertTrue(spec.isRandomizedEncryptionRequired());
        MoreAsserts.assertEmpty(Arrays.asList(spec.getSignaturePaddings()));
        assertFalse(spec.isUserAuthenticationRequired());
        assertEquals(0, spec.getUserAuthenticationValidityDurationSeconds());
        assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, spec.getUserAuthenticationType());
        assertEquals(GateKeeper.INVALID_SECURE_USER_ID, spec.getBoundToSpecificSecureUserId());
        assertFalse(spec.isUnlockedDeviceRequired());
        assertEquals(KeyProperties.UNRESTRICTED_USAGE_COUNT, spec.getMaxUsageCount());
        assertEquals(spec.isStrongBoxBacked(), false);
    }

    @Test
    public void testSettersReflectedInGetters() {
        // Set all parameters to non-default values and then assert that getters reflect that.

        Date keyValidityStartDate = new Date(System.currentTimeMillis() - 2222222);
        Date keyValidityEndDateForOrigination = new Date(System.currentTimeMillis() + 11111111);
        Date keyValidityEndDateForConsumption = new Date(System.currentTimeMillis() + 33333333);
        int maxUsageCount = 1;

        KeyProtection.Builder specBuilder = new KeyProtection.Builder(
                KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_VERIFY)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CTR)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                        KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeyValidityStart(keyValidityStartDate)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setRandomizedEncryptionRequired(false)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(123456 /* validity duration seconds */,
                        KeyProperties.AUTH_DEVICE_CREDENTIAL | KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setBoundToSpecificSecureUserId(654321)
                .setUnlockedDeviceRequired(true)
                .setMaxUsageCount(maxUsageCount)
                .setIsStrongBoxBacked(true);

        if (Flags.mgf1DigestSetterV2()) {
            specBuilder.setMgf1Digests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512);
        }
        KeyProtection spec = specBuilder.build();

        assertEquals(
                KeyProperties.PURPOSE_DECRYPT| KeyProperties.PURPOSE_VERIFY, spec.getPurposes());
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getBlockModes()),
                KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CTR);
        assertTrue(spec.isDigestsSpecified());
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getDigests()),
                KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512);
        if (Flags.mgf1DigestSetterV2()) {
            assertTrue(spec.isMgf1DigestsSpecified());
            /* Since getMgf1Digest return type is Set, objects could be in any order. */
            MoreAsserts.assertContentsInAnyOrder(spec.getMgf1Digests(),
                    KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512);
        }
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getEncryptionPaddings()),
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1, KeyProperties.ENCRYPTION_PADDING_PKCS7);
        assertEquals(keyValidityStartDate, spec.getKeyValidityStart());
        assertEquals(keyValidityEndDateForOrigination, spec.getKeyValidityForOriginationEnd());
        assertEquals(keyValidityEndDateForConsumption, spec.getKeyValidityForConsumptionEnd());
        assertFalse(spec.isRandomizedEncryptionRequired());
        MoreAsserts.assertContentsInOrder(Arrays.asList(spec.getSignaturePaddings()),
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS);
        assertTrue(spec.isUserAuthenticationRequired());
        assertEquals(123456, spec.getUserAuthenticationValidityDurationSeconds());
        assertEquals(KeyProperties.AUTH_DEVICE_CREDENTIAL | KeyProperties.AUTH_BIOMETRIC_STRONG,
                spec.getUserAuthenticationType());
        assertEquals(654321, spec.getBoundToSpecificSecureUserId());
        assertTrue(spec.isUnlockedDeviceRequired());
        assertEquals(spec.getMaxUsageCount(), maxUsageCount);
        assertEquals(spec.isStrongBoxBacked(), true);
    }

    @Test
    public void testSetKeyValidityEndDateAppliesToBothEndDates() {
        Date date = new Date(System.currentTimeMillis() + 555555);
        KeyProtection spec = new KeyProtection.Builder(
                KeyProperties.PURPOSE_SIGN)
                .setKeyValidityEnd(date)
                .build();
        assertEquals(date, spec.getKeyValidityForOriginationEnd());
        assertEquals(date, spec.getKeyValidityForConsumptionEnd());
    }

    @Test
    public void testSetUserAuthenticationValidityDurationSecondsValidityCheck() {
        KeyProtection.Builder builder = new KeyProtection.Builder(0);
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

        KeyProtection.Builder specBuilder = new KeyProtection.Builder(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(blockModes)
                .setDigests(digests)
                .setEncryptionPaddings(encryptionPaddings)
                .setKeyValidityStart(keyValidityStartDate)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setSignaturePaddings(signaturePaddings);

        if (Flags.mgf1DigestSetterV2()) {
            specBuilder.setMgf1Digests(mgfDigests);
        }
        KeyProtection spec = specBuilder.build();

        assertEquals(Arrays.asList(originalBlockModes), Arrays.asList(spec.getBlockModes()));
        blockModes[0] = null;
        assertEquals(Arrays.asList(originalBlockModes), Arrays.asList(spec.getBlockModes()));

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

        KeyProtection.Builder specBuilder = new KeyProtection.Builder(
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CBC)
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
        KeyProtection spec = specBuilder.build();

        String[] originalBlockModes = spec.getBlockModes().clone();
        spec.getBlockModes()[0] = null;
        assertEquals(Arrays.asList(originalBlockModes), Arrays.asList(spec.getBlockModes()));

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

    @Test
    public void testIllegalMaxUsageCountNotPermitted() {
        try {
            new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT).setMaxUsageCount(0);
            fail();
        } catch (IllegalArgumentException expected) {}
    }
}
