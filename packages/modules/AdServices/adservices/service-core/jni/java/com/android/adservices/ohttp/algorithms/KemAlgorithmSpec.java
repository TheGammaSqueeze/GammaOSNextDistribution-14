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

import com.android.adservices.ohttp.KemNativeRef;

import com.google.auto.value.AutoValue;

import java.util.function.Supplier;

/** Specifies the algorithm spec of a Key Encapsulation Mechanism algorithm */
@AutoValue
public abstract class KemAlgorithmSpec {
    // As defined in https://www.rfc-editor.org/rfc/rfc9180#name-key-encapsulation-mechanism
    public static final int DHKEM_X25519_HKDF_SHA256_IDENTIFIER = 0x0020;

    private static final String DHKEM_X25519_HKDF_SHA256 = "DHKEM_X25519_HKDF_SHA256";

    /** The variables as defined in : https://www.rfc-editor.org/rfc/rfc9180#section-11.1-1 */

    /** Get the identifier for the algorithm */
    public abstract int identifier();

    /** Get the name of the algorithm */
    public abstract String name();

    /** Get the length in bytes of a KEM shared secret produced by the algorithm */
    public abstract int secretLength();

    /** Get the length in bytes of an encoded encapsulated key produced by the algorithm */
    public abstract int encapsulatedKeyLength();

    /** Get the length in bytes of an encoded public key for the algorithm */
    public abstract int publicKeyLength();

    /** Get the length in bytes of an encoded private key for the algorithm */
    public abstract int privateKeyLength();

    /**
     * Get the length in bytes of the seed that will be used by Hpke Setup Base Operation
     *
     * <p>The Hpke Setup Base operation uses a seed. That seed should always be random unless
     * testing. The length of the seed depends on the KEM algorithm being used. For example, for KEM
     * X25519, it is equal to the length of the private key.
     */
    public abstract int seedLength();

    /** Gets the supplier of the KEM algorithm native reference */
    public abstract Supplier<KemNativeRef> kemNativeRefSupplier();

    private static KemAlgorithmSpec dhkemX25519HkdfSha256() {
        // Spec taken from https://www.rfc-editor.org/rfc/rfc9180#name-key-encapsulation-mechanism
        return new AutoValue_KemAlgorithmSpec(
                /* identifier= */ DHKEM_X25519_HKDF_SHA256_IDENTIFIER,
                DHKEM_X25519_HKDF_SHA256,
                /* secretLength= */ 32,
                /* encapsulatedKeyLength= */ 32,
                /* publicKeyLength= */ 32,
                /* privateKeyLength= */ 32,
                /* seedLength= */ 32,
                () -> KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference());
    }

    /**
     * Gets the KEM algorithm corresponding to the identifier
     *
     * <p>As specified in https://www.rfc-editor.org/rfc/rfc9180#name-key-encapsulation-mechanism
     */
    public static KemAlgorithmSpec get(int identifier) throws UnsupportedHpkeAlgorithmException {
        if (identifier == DHKEM_X25519_HKDF_SHA256_IDENTIFIER) {
            return dhkemX25519HkdfSha256();
        }

        throw new UnsupportedHpkeAlgorithmException("Only Kem ID = 0X0020 supported");
    }
}
