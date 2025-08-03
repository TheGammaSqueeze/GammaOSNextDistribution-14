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

package com.android.ondevicepersonalization.services.util;

import android.annotation.NonNull;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;

/**
 * Utilities to encrypt and decrypt strings.
 */
public class CryptUtils {
    private static final String KEY_ALIAS = "odp_key_alias";
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
                    KEY_ALIAS, null);
            return secretKeyEntry.getSecretKey();
        } else {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                    PROVIDER);
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE).build();
            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();
        }
    }

    /** Encrypts an object and produces a base64 string. */
    public static String encrypt(@NonNull Serializable data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());

        SealedObject sealedData = new SealedObject(data, cipher);

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(
                     byteArrayOutputStream)) {
            objectOutputStream.writeObject(sealedData);
            byte[] sealedBytes = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(sealedBytes, Base64.URL_SAFE | Base64.NO_WRAP);
        }
    }

    /** Decrypts a base64 string. */
    public static Object decrypt(@NonNull String base64data) throws Exception {
        byte[] cipherMessage = Base64.decode(base64data, Base64.URL_SAFE | Base64.NO_WRAP);

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cipherMessage);
             ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            SealedObject sealedData = (SealedObject) objectInputStream.readObject();

            return sealedData.getObject(getSecretKey());
        }
    }

    private CryptUtils() {
    }
}
