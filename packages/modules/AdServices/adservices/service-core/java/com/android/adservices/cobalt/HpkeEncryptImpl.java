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

package com.android.adservices.cobalt;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.HpkeJni;
import com.android.cobalt.crypto.HpkeEncrypt;

/** Wrapper around the HPKE JNI bindings to pass to Cobalt. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class HpkeEncryptImpl implements HpkeEncrypt {
    public HpkeEncryptImpl() {}

    /**
     * Encrypt a byte string using the HPKE JNI wrapper.
     *
     * @param publicKey the public key to use for encryption
     * @param plainText the plain text string to encrypt
     * @param contextInfo the HPKE associated data
     * @return the encrypted ciphertext
     */
    @Override
    public byte[] encrypt(
            @NonNull byte[] publicKey, @NonNull byte[] plainText, @NonNull byte[] contextInfo) {
        return HpkeJni.encrypt(publicKey, plainText, contextInfo);
    }
}
