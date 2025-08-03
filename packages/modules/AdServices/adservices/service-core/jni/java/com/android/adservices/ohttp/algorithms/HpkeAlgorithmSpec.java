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

import com.android.adservices.ohttp.ObliviousHttpKeyConfig;

import com.google.auto.value.AutoValue;

/**
 * Holds the HPKE cipher suite supported by the {@link
 * com.android.adservices.ohttp.ObliviousHttpClient}
 */
@AutoValue
public abstract class HpkeAlgorithmSpec {
    /** Get the KEM algorithm from this HPKE algorithm suite */
    public abstract KemAlgorithmSpec kem();

    /** Get the KDF algorithm from this HPKE algorithm suite */
    public abstract KdfAlgorithmSpec kdf();

    /** Get the AEAD algorithm from this HPKE algorithm suite */
    public abstract AeadAlgorithmSpec aead();

    private static HpkeAlgorithmSpec create(
            KemAlgorithmSpec kem, KdfAlgorithmSpec kdf, AeadAlgorithmSpec aead) {
        return new AutoValue_HpkeAlgorithmSpec(kem, kdf, aead);
    }

    /**
     * Create a {@link HpkeAlgorithmSpec} from the keyConfig and throw {@link
     * UnsupportedHpkeAlgorithmException} if any of the algorithms are not supported
     */
    public static HpkeAlgorithmSpec fromKeyConfig(ObliviousHttpKeyConfig obliviousHttpKeyConfig)
            throws UnsupportedHpkeAlgorithmException {
        KemAlgorithmSpec kem = KemAlgorithmSpec.get(obliviousHttpKeyConfig.kemId());
        KdfAlgorithmSpec kdf = KdfAlgorithmSpec.get(obliviousHttpKeyConfig.kdfId());
        AeadAlgorithmSpec aead = AeadAlgorithmSpec.get(obliviousHttpKeyConfig.aeadId());
        return create(kem, kdf, aead);
    }
}
