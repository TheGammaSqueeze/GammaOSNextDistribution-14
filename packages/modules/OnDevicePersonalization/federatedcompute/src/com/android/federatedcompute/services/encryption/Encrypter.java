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

package com.android.federatedcompute.services.encryption;

/** Interface for crypto libraries to encrypt data */
public interface Encrypter {

    /**
     * encrypt {@code plainText} to cipher text {@code byte[]}.
     *
     * @param publicKey the public key used for encryption
     * @param plainText the plain text string to encrypt
     * @param associatedData additional data used for encryption
     * @return the encrypted ciphertext
     */
    byte[] encrypt(byte[] publicKey, byte[] plainText, byte[] associatedData);

}
