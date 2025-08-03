/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.apksig.kms;

import com.android.apksig.KeyConfig;
import com.android.apksig.SignerEngine;

/** Performs cryptographic signing with a Key Management Service (KMS). */
public abstract class KmsSignerEngine implements SignerEngine {
    public final KmsType kmsType;
    public final String keyAlias;

    /** Subclasses must specify the type of KMS and a signing key alias. */
    public KmsSignerEngine(KmsType kmsType, String keyAlias) {
        this.kmsType = kmsType;
        this.keyAlias = keyAlias;
    }

    @Override
    public abstract byte[] sign(byte[] data);

    /** Fetch a concrete implementation based on the provided config. */
    public static KmsSignerEngine fromKmsConfig(KeyConfig.Kms kmsConfig) {
        switch (kmsConfig.kmsType) {
            default:
                throw new KmsException(kmsConfig.kmsType, "Unsupported KMS");
        }
    }
}
