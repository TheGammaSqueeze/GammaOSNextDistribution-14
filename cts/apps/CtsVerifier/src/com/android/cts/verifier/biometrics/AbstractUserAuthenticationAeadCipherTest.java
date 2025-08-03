/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.verifier.biometrics;

import android.hardware.biometrics.BiometricPrompt;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

/**
 * An abstract base class to add Aead Cipher tests.
 */
public abstract class AbstractUserAuthenticationAeadCipherTest
        extends AbstractUserAuthenticationTest {
    private Cipher mCipher;

    @Override
    void createUserAuthenticationKey(String keyName, int timeout, int authType,
            boolean useStrongBox) throws Exception {
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                keyName, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
        builder.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(timeout, authType)
                .setIsStrongBoxBacked(useStrongBox);

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(builder.build());
        keyGenerator.generateKey();
    }

    @Override
    void initializeKeystoreOperation(String keyName) throws Exception {
        mCipher = Utils.initAeadCipher(keyName);
    }

    @Override
    BiometricPrompt.CryptoObject getCryptoObject() {
        return new BiometricPrompt.CryptoObject(mCipher);
    }

    @Override
    void doKeystoreOperation(byte[] payload) throws Exception {
        try {
            byte[] aad = "Test aad data".getBytes();
            mCipher.updateAAD(aad);
            Utils.doEncrypt(mCipher, payload);
        } finally {
            mCipher = null;
        }
    }
}
