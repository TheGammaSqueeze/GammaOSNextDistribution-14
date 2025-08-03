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

import android.keystore.cts.util.ImportedKey;
import android.keystore.cts.util.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

@RunWith(AndroidJUnit4.class)
public class KeyInfoTest {

    private static final String KEY_ALIAS = KeyInfoTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testImmutabilityViaGetterReturnValues() throws Exception {
        // Assert that none of the mutable return values from getters modify the state of the
        // instance.

        Date keyValidityStartDate = new Date(System.currentTimeMillis() - 2222222);
        Date keyValidityEndDateForOrigination = new Date(System.currentTimeMillis() + 11111111);
        Date keyValidityEndDateForConsumption = new Date(System.currentTimeMillis() + 33333333);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        keyPairGenerator.initialize(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT)
                .setKeySize(1024) // use smaller key size to speed the test up
                .setKeyValidityStart(keyValidityStartDate)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                        KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .build());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        PrivateKey key = keyPair.getPrivate();
        KeyFactory keyFactory = KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore");
        KeyInfo info = keyFactory.getKeySpec(key, KeyInfo.class);

        Date originalKeyValidityStartDate = (Date) info.getKeyValidityStart().clone();
        info.getKeyValidityStart().setTime(1234567890L);
        assertEquals(originalKeyValidityStartDate, info.getKeyValidityStart());

        Date originalKeyValidityEndDateForOrigination =
                (Date) info.getKeyValidityForOriginationEnd().clone();
        info.getKeyValidityForOriginationEnd().setTime(1234567890L);
        assertEquals(originalKeyValidityEndDateForOrigination,
                info.getKeyValidityForOriginationEnd());

        Date originalKeyValidityEndDateForConsumption =
                (Date) info.getKeyValidityForConsumptionEnd().clone();
        info.getKeyValidityForConsumptionEnd().setTime(1234567890L);
        assertEquals(originalKeyValidityEndDateForConsumption,
                info.getKeyValidityForConsumptionEnd());

        String[] originalEncryptionPaddings = info.getEncryptionPaddings().clone();
        info.getEncryptionPaddings()[0] = null;
        assertEquals(Arrays.asList(originalEncryptionPaddings),
                Arrays.asList(info.getEncryptionPaddings()));

        String[] originalSignaturePaddings = info.getSignaturePaddings().clone();
        info.getSignaturePaddings()[0] = null;
        assertEquals(Arrays.asList(originalSignaturePaddings),
                Arrays.asList(info.getSignaturePaddings()));

        String[] originalDigests = info.getDigests().clone();
        info.getDigests()[0] = null;
        assertEquals(Arrays.asList(originalDigests), Arrays.asList(info.getDigests()));

        String[] originalBlockModes = info.getBlockModes().clone();
        info.getBlockModes()[0] = null;
        assertEquals(Arrays.asList(originalBlockModes), Arrays.asList(info.getBlockModes()));

        // Return KeyProperties.UNRESTRICTED_USAGE_COUNT to indicate there is no restriction on
        // the number of times that the key can be used.
        int remainingUsageCount = info.getRemainingUsageCount();
        assertEquals(KeyProperties.UNRESTRICTED_USAGE_COUNT, remainingUsageCount);
    }

    @Test
    public void testLimitedUseKey() throws Exception {
        Date keyValidityStartDate = new Date(System.currentTimeMillis() - 2222222);
        Date keyValidityEndDateForOrigination = new Date(System.currentTimeMillis() + 11111111);
        Date keyValidityEndDateForConsumption = new Date(System.currentTimeMillis() + 33333333);
        int maxUsageCount = 1;

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        keyPairGenerator.initialize(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT)
                .setKeySize(1024) // use smaller key size to speed the test up
                .setKeyValidityStart(keyValidityStartDate)
                .setKeyValidityForOriginationEnd(keyValidityEndDateForOrigination)
                .setKeyValidityForConsumptionEnd(keyValidityEndDateForConsumption)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                        KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setMaxUsageCount(maxUsageCount)
                .build());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        PrivateKey key = keyPair.getPrivate();
        KeyFactory keyFactory = KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore");
        KeyInfo info = keyFactory.getKeySpec(key, KeyInfo.class);

        int remainingUsageCount = info.getRemainingUsageCount();
        assertEquals(maxUsageCount, remainingUsageCount);
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_KEYINFO_UNLOCKED_DEVICE_REQUIRED)
    @Test
    public void testUnlockedDeviceRequiredKey() throws Exception {
        for (boolean required : new boolean[]{false, true}) {
            // Test KeyInfo#isUnlockedDeviceRequired() for a generated key.
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT)
                    .setUnlockedDeviceRequired(required)
                    .build());
            SecretKey key = keyGenerator.generateKey();
            assertEquals(required, TestUtils.getKeyInfo(key).isUnlockedDeviceRequired());

            // Test KeyInfo#isUnlockedDeviceRequired() for an imported key.
            ImportedKey importedKey = TestUtils.importIntoAndroidKeyStore(KEY_ALIAS,
                    KeyGenerator.getInstance("AES").generateKey(),
                    new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT)
                            .setUnlockedDeviceRequired(required)
                            .build());
            assertEquals(required,
                    TestUtils.getKeyInfo(importedKey.getKeystoreBackedEncryptionKey())
                            .isUnlockedDeviceRequired());
        }
    }

    @After
    public void tearDown() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(KEY_ALIAS);
        } catch (Throwable t) {
            // Ignore any exception
        }
    }
}
