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
package com.android.cobalt.crypto.testing;

import androidx.annotation.VisibleForTesting;

import com.android.cobalt.crypto.HpkeEncrypt;

/** Factory that generated testing HpkeEncrypt with required behavior. */
@VisibleForTesting
public final class HpkeEncryptFactory {

    /** Returns an HpkeEncrypt implementation which returns the input plain text. */
    public static HpkeEncrypt noOpHpkeEncrypt() {
        return new HpkeEncrypt() {
            public byte[] encrypt(byte[] publicKey, byte[] plainText, byte[] contextInfo) {
                return plainText;
            }
        };
    }

    /**
     * Returns an HpkeEncrypt implementation which returns an empty byte array to mimic encryption
     * failure in HPKE JNI.
     */
    public static HpkeEncrypt emptyHpkeEncrypt() {
        return new HpkeEncrypt() {
            public byte[] encrypt(byte[] publicKey, byte[] plainText, byte[] contextInfo) {
                return new byte[] {};
            }
        };
    }
}
