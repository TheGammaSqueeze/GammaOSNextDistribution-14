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

package com.android.adservices.service.adselection.signature;

/** Interface for a cryptographic signature verifier */
public interface SignatureVerifier {

    /**
     * Verifies a given signature against the given {@code publicKey} and the serialized {@code
     * data}
     *
     * @param publicKey public key paired with the private key used to sign the data
     * @param data serialized representation of the data
     * @param signature signature to verify
     */
    boolean verify(byte[] publicKey, byte[] data, byte[] signature);
}
