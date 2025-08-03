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

package com.android.server.wifi.util;

import android.security.keystore.AndroidKeyStoreProvider;
import android.security.keystore.BackendBusyException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.UnrecoverableKeyException;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * Mockable wrapper that interacts with Android KeyStore to get hash function used for MAC
 * randomization.
 */
public class KeystoreWrapper {
    private static final String TAG = "KeystoreWrapper";

    /**
     * Gets the hash function used to calculate persistent randomized MAC address from the KeyStore.
     */
    public Mac getHmacSHA256ForUid(int uid, String alias) {
        try {
            KeyStore keyStore = AndroidKeyStoreProvider.getKeyStoreForUid(uid);
            // tries to retrieve the secret, and generate a new one if it's unavailable.
            Key key = keyStore.getKey(alias, null);
            if (key == null) {
                key = generateAndPersistNewMacRandomizationSecret(uid, alias);
                if (key == null) {
                    Log.e(TAG, "Failed to generate secret for " + alias);
                    return null;
                }
            }
            Mac result = Mac.getInstance("HmacSHA256");
            result.init(key);
            return result;
        } catch (KeyStoreException | NoSuchAlgorithmException | InvalidKeyException
                | UnrecoverableKeyException | NoSuchProviderException e) {
            Log.e(TAG, "Failure in getHmacSHA256ForUid", e);
            return null;
        } catch (Exception e) {
            if (SdkLevel.isAtLeastS() && e instanceof BackendBusyException) {
                Log.e(TAG, "Failure in getHmacSHA256ForUid", e);
                return null;
            }
            Log.e(TAG, "Unexpected exception caught in getHmacSHA256ForUid", e);
            throw e;
        }
    }

    /**
     * Generates and returns a secret key to use for Mac randomization.
     * Will also persist the generated secret inside KeyStore, accessible in the
     * future with KeyGenerator#getKey.
     */
    private SecretKey generateAndPersistNewMacRandomizationSecret(int uid, String alias) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore");
            keyGenerator.init(
                    new KeyGenParameterSpec.Builder(alias,
                            KeyProperties.PURPOSE_SIGN)
                            .setUid(uid)
                            .build());
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException
                | NoSuchProviderException | ProviderException e) {
            Log.e(TAG, "Failure in generateMacRandomizationSecret", e);
            return null;
        }
    }
}
