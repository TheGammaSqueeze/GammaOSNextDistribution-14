/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net;

import static android.net.IpSecAlgorithm.ALGO_TO_REQUIRED_FIRST_SDK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.res.Resources;
import android.os.Build;
import android.os.Parcel;
import android.os.SystemProperties;

import androidx.test.filters.SmallTest;

import com.android.internal.util.CollectionUtils;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

/** Unit tests for {@link IpSecAlgorithm}. */
@SmallTest
@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
public class IpSecAlgorithmTest {
    private static final byte[] KEY_MATERIAL;

    private final Resources mMockResources = mock(Resources.class);

    static {
        KEY_MATERIAL = new byte[128];
        new Random().nextBytes(KEY_MATERIAL);
    };

    private static byte[] generateKey(int keyLenInBits) {
        return Arrays.copyOf(KEY_MATERIAL, keyLenInBits / 8);
    }

    @Test
    public void testNoTruncLen() throws Exception {
        Entry<String, Integer>[] authAndAeadList =
                new Entry[] {
                    new SimpleEntry<>(IpSecAlgorithm.AUTH_HMAC_MD5, 128),
                    new SimpleEntry<>(IpSecAlgorithm.AUTH_HMAC_SHA1, 160),
                    new SimpleEntry<>(IpSecAlgorithm.AUTH_HMAC_SHA256, 256),
                    new SimpleEntry<>(IpSecAlgorithm.AUTH_HMAC_SHA384, 384),
                    new SimpleEntry<>(IpSecAlgorithm.AUTH_HMAC_SHA512, 512),
                    new SimpleEntry<>(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, 224),
                };

        // Expect auth and aead algorithms to throw errors if trunclen is omitted.
        for (Entry<String, Integer> algData : authAndAeadList) {
            try {
                new IpSecAlgorithm(
                        algData.getKey(), Arrays.copyOf(KEY_MATERIAL, algData.getValue() / 8));
                fail("Expected exception on unprovided auth trunclen");
            } catch (IllegalArgumentException expected) {
            }
        }

        // Ensure crypt works with no truncation length supplied.
        new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, Arrays.copyOf(KEY_MATERIAL, 256 / 8));
    }

    private void checkAuthKeyAndTruncLenValidation(String algoName, int keyLen, int truncLen)
            throws Exception {
        new IpSecAlgorithm(algoName, generateKey(keyLen), truncLen);

        try {
            new IpSecAlgorithm(algoName, generateKey(keyLen));
            fail("Expected exception on unprovided auth trunclen");
        } catch (IllegalArgumentException pass) {
        }

        try {
            new IpSecAlgorithm(algoName, generateKey(keyLen + 8), truncLen);
            fail("Invalid key length not validated");
        } catch (IllegalArgumentException pass) {
        }

        try {
            new IpSecAlgorithm(algoName, generateKey(keyLen), truncLen + 1);
            fail("Invalid truncation length not validated");
        } catch (IllegalArgumentException pass) {
        }
    }

    private void checkCryptKeyLenValidation(String algoName, int keyLen) throws Exception {
        new IpSecAlgorithm(algoName, generateKey(keyLen));

        try {
            new IpSecAlgorithm(algoName, generateKey(keyLen + 8));
            fail("Invalid key length not validated");
        } catch (IllegalArgumentException pass) {
        }
    }

    @Test
    public void testValidationForAlgosAddedInS() throws Exception {
        if (SystemProperties.getInt("ro.vendor.api_level", 10000) <= Build.VERSION_CODES.R) return;

        for (int len : new int[] {160, 224, 288}) {
            checkCryptKeyLenValidation(IpSecAlgorithm.CRYPT_AES_CTR, len);
        }
        checkAuthKeyAndTruncLenValidation(IpSecAlgorithm.AUTH_AES_XCBC, 128, 96);
        checkAuthKeyAndTruncLenValidation(IpSecAlgorithm.AUTH_AES_CMAC, 128, 96);
        checkAuthKeyAndTruncLenValidation(IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305, 288, 128);
    }

    @Test
    public void testTruncLenValidation() throws Exception {
        for (int truncLen : new int[] {256, 512}) {
            new IpSecAlgorithm(
                    IpSecAlgorithm.AUTH_HMAC_SHA512,
                    Arrays.copyOf(KEY_MATERIAL, 512 / 8),
                    truncLen);
        }

        for (int truncLen : new int[] {255, 513}) {
            try {
                new IpSecAlgorithm(
                        IpSecAlgorithm.AUTH_HMAC_SHA512,
                        Arrays.copyOf(KEY_MATERIAL, 512 / 8),
                        truncLen);
                fail("Invalid truncation length not validated");
            } catch (IllegalArgumentException pass) {
            }
        }
    }

    @Test
    public void testLenValidation() throws Exception {
        for (int len : new int[] {128, 192, 256}) {
            new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, Arrays.copyOf(KEY_MATERIAL, len / 8));
        }
        try {
            new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, Arrays.copyOf(KEY_MATERIAL, 384 / 8));
            fail("Invalid key length not validated");
        } catch (IllegalArgumentException pass) {
        }
    }

    @Test
    public void testAlgoNameValidation() throws Exception {
        try {
            new IpSecAlgorithm("rot13", Arrays.copyOf(KEY_MATERIAL, 128 / 8));
            fail("Invalid algorithm name not validated");
        } catch (IllegalArgumentException pass) {
        }
    }

    @Test
    public void testParcelUnparcel() throws Exception {
        IpSecAlgorithm init =
                new IpSecAlgorithm(
                        IpSecAlgorithm.AUTH_HMAC_SHA512, Arrays.copyOf(KEY_MATERIAL, 512 / 8), 256);

        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        init.writeToParcel(p, 0);

        p.setDataPosition(0);
        IpSecAlgorithm fin = IpSecAlgorithm.CREATOR.createFromParcel(p);
        assertTrue("Parcel/Unparcel failed!", IpSecAlgorithm.equals(init, fin));
        p.recycle();
    }

    private static Set<String> getMandatoryAlgos() {
        int vendorApiLevel = SystemProperties.getInt("ro.vendor.api_level", 10000);
        return CollectionUtils.filter(
                ALGO_TO_REQUIRED_FIRST_SDK.keySet(),
                i -> vendorApiLevel >= ALGO_TO_REQUIRED_FIRST_SDK.get(i));
    }

    private static Set<String> getOptionalAlgos() {
        int vendorApiLevel = SystemProperties.getInt("ro.vendor.api_level", 10000);
        return CollectionUtils.filter(
                ALGO_TO_REQUIRED_FIRST_SDK.keySet(),
                i -> vendorApiLevel < ALGO_TO_REQUIRED_FIRST_SDK.get(i));
    }

    @Test
    public void testGetSupportedAlgorithms() throws Exception {
        assertTrue(IpSecAlgorithm.getSupportedAlgorithms().containsAll(getMandatoryAlgos()));
        assertTrue(ALGO_TO_REQUIRED_FIRST_SDK.keySet().containsAll(
                IpSecAlgorithm.getSupportedAlgorithms()));
    }

    @Test
    public void testLoadAlgos() throws Exception {
        final Set<String> optionalAlgoSet = getOptionalAlgos();
        final String[] optionalAlgos = optionalAlgoSet.toArray(new String[0]);

        // Query the identifier instead of using the R.array constant, as the test may be built
        // separately from the platform and they may not match.
        final int resId = Resources.getSystem().getIdentifier("config_optionalIpSecAlgorithms",
                "array", "android");
        doReturn(optionalAlgos).when(mMockResources).getStringArray(resId);

        final Set<String> enabledAlgos = new HashSet<>(IpSecAlgorithm.loadAlgos(mMockResources));
        final Set<String> expectedAlgos = ALGO_TO_REQUIRED_FIRST_SDK.keySet();

        assertEquals(expectedAlgos, enabledAlgos);
    }
}
