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

package com.android.cts.verifier.biometrics;

import android.hardware.biometrics.BiometricPrompt;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assert;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyAgreement;

/**
 * This is a test to validate Key Agreement with auth type
 * AUTH_DEVICE_CREDENTIAL | AUTH_BIOMETRIC_STRONG.
 */
@ApiTest(apis = {"javax.crypto.KeyAgreement#generateSecret"})
public abstract class AbstractUserAuthenticationKeyAgreementTest
        extends AbstractUserAuthenticationTest {
    private KeyAgreement mKeyAgreement;
    private KeyPair mKeyPair;

    @Override
    void createUserAuthenticationKey(
            String keyName, int timeout, int authType, boolean useStrongBox) throws Exception {
        KeyGenParameterSpec.Builder builder =
                new KeyGenParameterSpec.Builder(
                        keyName, KeyProperties.PURPOSE_AGREE_KEY);
        builder.setUserAuthenticationRequired(true)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setUserAuthenticationParameters(timeout, authType)
                .setIsStrongBoxBacked(useStrongBox);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        keyPairGenerator.initialize(builder.build());
        mKeyPair = keyPairGenerator.generateKeyPair();
    }

    @Override
    void initializeKeystoreOperation(String keyName) throws Exception {
        mKeyAgreement = Utils.initKeyAgreement(keyName);
    }

    @Override
    BiometricPrompt.CryptoObject getCryptoObject() {
        return new BiometricPrompt.CryptoObject(mKeyAgreement);
    }

    @Override
    void doKeystoreOperation(byte[] payload) throws Exception {
        try {
            KeyPairGenerator otherKPG = KeyPairGenerator.getInstance("EC");
            otherKPG.initialize(256);
            KeyPair otherKP = otherKPG.generateKeyPair();

            // Generate shared secret of the first keyAgreement
            mKeyAgreement.doPhase(otherKP.getPublic(), true);
            byte[] ourSharedSecret = mKeyAgreement.generateSecret();

            // Generate second Shared Secret
            KeyAgreement secondKeyAgreement = KeyAgreement.getInstance("ECDH");
            secondKeyAgreement.init(otherKP.getPrivate());
            secondKeyAgreement.doPhase(mKeyPair.getPublic(), true);
            byte[] theirSharedSecret = secondKeyAgreement.generateSecret();

            Assert.assertArrayEquals(ourSharedSecret, theirSharedSecret);
        } finally {
            mKeyAgreement = null;
            mKeyPair = null;
        }
    }
}
