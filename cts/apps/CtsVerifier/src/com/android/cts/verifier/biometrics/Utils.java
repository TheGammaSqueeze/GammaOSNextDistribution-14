/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class Utils {
    private static final String TAG = "BiometricTestUtils";

    static void createBiometricBoundKey(String keyName, boolean useStrongBox) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        // Set the alias of the entry in Android KeyStore where the key will appear
        // and the constrains (purposes) in the constructor of the Builder
        keyGenerator.init(new KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setIsStrongBoxBacked(useStrongBox)
                .setInvalidatedByBiometricEnrollment(true)
                .build());
        keyGenerator.generateKey();
    }

    static void createTimeBoundSecretKey_deprecated(String keyName, boolean useStrongBox)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        // Set the alias of the entry in Android KeyStore where the key will appear
        // and the constrains (purposes) in the constructor of the Builder
        keyGenerator.init(new KeyGenParameterSpec.Builder(keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setIsStrongBoxBacked(useStrongBox)
                .setUserAuthenticationValidityDurationSeconds(5 /* seconds */)
                .build());
        keyGenerator.generateKey();
    }

    static Cipher initCipher(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey secretKey = (SecretKey) keyStore.getKey(keyName, null);

        Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher;
    }

    static Cipher initAeadCipher(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey secretKey = (SecretKey) keyStore.getKey(keyName, null);

        Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_GCM + "/"
                + KeyProperties.ENCRYPTION_PADDING_NONE);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher;
    }

    static Signature initSignature(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        KeyStore.Entry entry = keyStore.getEntry(keyName, null);

        PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();

        // TODO: This can be used to verify signature
        // PublicKey publicKey = keyStore.getCertificate(keyName).getPublicKey();

        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        return signature;
    }

    static Mac initMac(String keyName) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        SecretKey secretKey = (SecretKey) keyStore.getKey(keyName, null);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);
        return mac;
    }

    static KeyAgreement initKeyAgreement(String keyName) throws Exception {
        String keyAgreementAlgorithm = "ECDH";
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        // Uses KeyAgreement based on Provider search order.
        KeyAgreement keyAgreement = KeyAgreement.getInstance(keyAgreementAlgorithm);
        keyAgreement.init(keyStore.getKey(keyName, null));
        return keyAgreement;
    }

    static byte[] doEncrypt(Cipher cipher, byte[] data) throws Exception {
        return cipher.doFinal(data);
    }

    static byte[] doSign(Signature signature, byte[] data) throws Exception {
        signature.update(data);
        return signature.sign();
    }

    static void showInstructionDialog(Context context, int titleRes, int messageRes,
            int positiveButtonRes, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleRes);
        builder.setMessage(messageRes);
        builder.setPositiveButton(positiveButtonRes, listener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
