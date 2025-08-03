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

package com.android.adservices.ohttp.algorithms;

import com.android.adservices.ohttp.AeadNativeRef;

import com.google.auto.value.AutoValue;

import java.util.function.Supplier;

/** Specifies the algorithm spec of an Authenticated Encryption with Associated Data algorithm */
@AutoValue
public abstract class AeadAlgorithmSpec {
    // As defined in https://www.rfc-editor.org/rfc/rfc9180#name-authenticated-encryption-wi
    public static final int AES_256_GCM_IDENTIFIER = 0x0002;

    private static final String AES_256_GCM = "AES_256_GCM";

    // The variables as defined in https://www.rfc-editor.org/rfc/rfc9180#name-aead-identifiers

    /** Get the identifier for the algorithm */
    public abstract int identifier();

    /** Get the name of the algorithm */
    public abstract String name();

    /** Get the length in bytes of a key for this algorithm */
    public abstract int keyLength();

    /** Get the length in bytes of a nonce for this algorithm */
    public abstract int nonceLength();

    /** Get the length in bytes of an authentication tag for this algorithm */
    public abstract int tagLength();

    /** Gets the supplier of the Aead algorithm native reference */
    public abstract Supplier<AeadNativeRef> aeadNativeRefSupplier();

    private static AeadAlgorithmSpec aes256Gcm() {
        return new AutoValue_AeadAlgorithmSpec(
                AES_256_GCM_IDENTIFIER,
                AES_256_GCM,
                /* keyLength= */ 32,
                /* nonceLength= */ 12,
                /* tagLength= */ 16,
                () -> AeadNativeRef.getHpkeAeadAes256GcmReference());
    }

    /**
     * Gets the AEAD algorithm corresponding to the identifier
     *
     * <p>As specified in https://www.rfc-editor.org/rfc/rfc9180#name-authenticated-encryption-wi
     */
    public static AeadAlgorithmSpec get(int identifier) throws UnsupportedHpkeAlgorithmException {
        if (identifier == AES_256_GCM_IDENTIFIER) {
            return aes256Gcm();
        }

        throw new UnsupportedHpkeAlgorithmException("Only AEAD ID = 0X0002 supported");
    }
}
