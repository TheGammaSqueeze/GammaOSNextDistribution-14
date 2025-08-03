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

import com.android.adservices.ohttp.HkdfMessageDigestNativeRef;
import com.android.adservices.ohttp.KdfNativeRef;

import com.google.auto.value.AutoValue;

import java.util.function.Supplier;

/** Get the algorithm spec of a Key Derivation Function algorithm */
@AutoValue
public abstract class KdfAlgorithmSpec {

    // As defined in https://www.rfc-editor.org/rfc/rfc9180#name-key-derivation-functions-kd
    public static final int HKDF_SHA256_IDENTIFIER = 0x0001;

    private static final String HKDF_SHA256 = "HKDF_SHA256";

    // The variables as defined in https://www.rfc-editor.org/rfc/rfc9180#name-kdf-identifiers

    /** Get the identifier for the algorithm */
    public abstract int identifier();

    /** Get the name of the algorithm */
    public abstract String name();

    /** Get the output size of the Extract function in byte */
    public abstract int extractOutputLength();

    /** Gets the supplier of the KDF algorithm native reference */
    public abstract Supplier<KdfNativeRef> kdfNativeRefSupplier();

    /** Gets the supplier of the associated message digest native reference */
    public abstract Supplier<HkdfMessageDigestNativeRef> messageDigestSupplier();

    private static KdfAlgorithmSpec hkdfSha256() {
        return new AutoValue_KdfAlgorithmSpec(
                HKDF_SHA256_IDENTIFIER,
                HKDF_SHA256,
                /* extractOutputLength= */ 32,
                () -> KdfNativeRef.getHpkeKdfHkdfSha256Reference(),
                () -> HkdfMessageDigestNativeRef.getHkdfSha256MessageDigestReference());
    }

    /**
     * Gets the KDF algorithm corresponding to the identifier
     *
     * <p>As specified in https://www.rfc-editor.org/rfc/rfc9180#name-key-derivation-functions-kd
     */
    public static KdfAlgorithmSpec get(int identifier) throws UnsupportedHpkeAlgorithmException {
        if (identifier == HKDF_SHA256_IDENTIFIER) {
            return hkdfSha256();
        }

        throw new UnsupportedHpkeAlgorithmException("Only HKDF ID = 0X0001 supported");
    }
}
