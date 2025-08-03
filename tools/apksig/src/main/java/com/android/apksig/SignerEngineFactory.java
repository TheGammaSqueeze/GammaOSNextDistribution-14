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

package com.android.apksig;

import com.android.apksig.kms.KmsSignerEngine;

import java.security.spec.AlgorithmParameterSpec;

/** Simple util to fetch a signer engine based on provided config values. */
public class SignerEngineFactory {
    private SignerEngineFactory() {}

    /**
     * Retrieves an implementation based on the provided config. If keyConfig is a {@link
     * KeyConfig.Kms}, signatureAlgorithm and signatureAlgorithmParameterSpec are ignored.
     *
     * @param keyConfig kms key type and alias, or a local private key.
     * @param signatureAlgorithm required if keyConfig is {@link KeyConfig.Jca}, ignored if
     *     keyConfig is {@link KeyConfig.Kms}.
     * @param algorithmParameterSpec optional, ignored if keyConfig is {@link KeyConfig.Kms}.
     * @return a concrete {@link SignerEngine} implementation.
     */
    public static SignerEngine getImplementation(
            KeyConfig keyConfig,
            String signatureAlgorithm,
            AlgorithmParameterSpec algorithmParameterSpec) {
        return keyConfig.match(
                jca ->
                        new JcaSignerEngine(
                                jca.privateKey, signatureAlgorithm, algorithmParameterSpec),
                KmsSignerEngine::fromKmsConfig);
    }
}
