/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cobalt.crypto;

import androidx.annotation.NonNull;

/** The API for providing Hybrid Public Key Encryption (HPKE) to Cobalt. */
public interface HpkeEncrypt {

    /**
     * Encrypt a byte string using HPKE.
     *
     * @param publicKey the public key to use for encryption
     * @param plainText the plain text string to encrypt
     * @param contextInfo the HPKE associated data
     * @return the encrypted ciphertext
     */
    byte[] encrypt(
            @NonNull byte[] publicKey, @NonNull byte[] plainText, @NonNull byte[] contextInfo);
}
