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
 * limitations under the License
 */

package android.keystore.cts.performance;

import android.security.keystore.KeyProperties;

import org.junit.Test;

public class RsaKeyGenPerformanceTest extends PerformanceTestBase {

    @Test
    public void testRsa2048KeyGenWithAndroidProvider() throws Exception {
        measureKeyGenWithAndroidProvider(2048);
    }

    @Test
    public void testRsa3072KeyGenWithAndroidProvider() throws Exception {
        measureKeyGenWithAndroidProvider(3072);
    }

    @Test
    public void testRsa4096KeyGenWithAndroidProvider() throws Exception {
        measureKeyGenWithAndroidProvider(4096);
    }

    @Test
    public void testRsa2048KeyGenWithDefaultProvider() throws Exception {
        measureKeyGenWithDefaultProvider(2048);
    }

    @Test
    public void testRsa3072KeyGenWithDefaultProvider() throws Exception {
        measureKeyGenWithDefaultProvider(3072);
    }

    @Test
    public void testRsa4096KeyGenWithDefaultProvider() throws Exception {
        measureKeyGenWithDefaultProvider(4096);
    }

    private void measureKeyGen(KeystoreKeyGenerator generator, int keySize) throws Exception {
        measure(new KeystoreKeyPairGenMeasurable(generator, keySize));
    }

    private void measureKeyGenWithAndroidProvider(int keySize) throws Exception {
        AndroidKeystoreKeyGenerator generator = new AndroidKeystoreKeyGenerator("RSA") {
        };
        generator.getKeyPairGenerator()
                .initialize(
                        generator.getKeyGenParameterSpecBuilder(
                                KeyProperties.PURPOSE_SIGN
                                        | KeyProperties.PURPOSE_VERIFY)
                                .setKeySize(keySize)
                                .build());
        measureKeyGen(generator, keySize);
    }

    private void measureKeyGenWithDefaultProvider(int keySize) throws Exception {
        measureKeyGen(new DefaultKeystoreKeyPairGenerator("RSA", keySize), keySize);
    }
}
