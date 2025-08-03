/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices;

import androidx.annotation.NonNull;

/**
 * Hybrid Public Key Encryption (HPKE) operations.
 *
 * <p>RFC: https://datatracker.ietf.org/doc/rfc9180
 */
public class HpkeJni {

    static {
        System.loadLibrary("hpke_jni");
    }

    /**
     * Encryption operation using Hybrid Public Key Encryption (HPKE)
     *
     * @param publicKey used by the encryption algorithm, must be exactly 32 bytes long. The public
     *     key is typically retrieved from a trusted server.
     * @param plainText the message to be encrypted.
     * @param associatedData used by the encryption algorithm, intended to provide additional data
     *     keeping the message integrity.
     * @return ciphertext encrypted result, ciphertext would be null if encryption fails.
     */
    public static synchronized native byte[] encrypt(
            @NonNull byte[] publicKey, @NonNull byte[] plainText, @NonNull byte[] associatedData);

    /**
     * Decryption operation using Hybrid Public Key Encryption (HPKE)
     *
     * @param privateKey used to decrypt the ciphertext.
     * @param ciphertext the encrypted message to be decrypted.
     * @param associatedData used on encryption providing additional data keeping message integrity.
     * @return plaintext decrypted result, plaintext would be null if decryption fails.
     */
    public static synchronized native byte[] decrypt(
            @NonNull byte[] privateKey, @NonNull byte[] ciphertext, @NonNull byte[] associatedData);
}
