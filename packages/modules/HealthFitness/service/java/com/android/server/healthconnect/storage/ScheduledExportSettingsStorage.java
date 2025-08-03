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

package com.android.server.healthconnect.storage;

import static android.health.connect.Constants.DEFAULT_INT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;

import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stores the settings for the scheduled export service.
 *
 * @hide
 */
public final class ScheduledExportSettingsStorage {
    private static final String EXPORT_SALT_PREFERENCE_KEY = "export_salt_key";
    private static final String EXPORT_URI_PREFERENCE_KEY = "export_uri_key";
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";
    private static final String EXPORT_KEYSTORE_ENTRY = "health_connect_export_key";

    /**
     * Configures the settings for the scheduled export of Health Connect data.
     *
     * @param settings Settings to use for the scheduled export. Use null to clear the settings.
     */
    public static void configure(@Nullable ScheduledExportSettings settings)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        if (settings != null) {
            configureNonNull(settings);
        } else {
            clear();
        }
    }

    /** Configures the settings for the scheduled export of Health Connect data. */
    private static void configureNonNull(@NonNull ScheduledExportSettings settings)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        // Android key store type is from
        // https://developer.android.com/privacy-and-security/keystore#UsingAndroidKeyStore.
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (settings.getSecretKey() != null && settings.getSalt() != null) {
            SecretKey secretKey =
                    new SecretKeySpec(settings.getSecretKey(), KeyProperties.KEY_ALGORITHM_AES);
            keyStore.setEntry(
                    EXPORT_KEYSTORE_ENTRY,
                    new KeyStore.SecretKeyEntry(secretKey),
                    new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());

            String salt = Arrays.toString(settings.getSalt());
            PreferenceHelper.getInstance()
                    .insertOrReplacePreference(EXPORT_SALT_PREFERENCE_KEY, salt);
        }

        if (settings.getUri() != null) {
            PreferenceHelper.getInstance()
                    .insertOrReplacePreference(
                            EXPORT_URI_PREFERENCE_KEY, settings.getUri().toString());
        }

        if (settings.getPeriodInDays() != DEFAULT_INT) {
            String periodInDays = String.valueOf(settings.getPeriodInDays());
            PreferenceHelper.getInstance()
                    .insertOrReplacePreference(EXPORT_PERIOD_PREFERENCE_KEY, periodInDays);
        }
    }

    /** Clears the settings for the scheduled export of Health Connect data. */
    private static void clear()
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        // Android key store type is from
        // https://developer.android.com/privacy-and-security/keystore#UsingAndroidKeyStore.
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(EXPORT_KEYSTORE_ENTRY);

        PreferenceHelper.getInstance().removeKey(EXPORT_SALT_PREFERENCE_KEY);
        PreferenceHelper.getInstance().removeKey(EXPORT_URI_PREFERENCE_KEY);
        PreferenceHelper.getInstance().removeKey(EXPORT_PERIOD_PREFERENCE_KEY);
    }

    /** Gets scheduled export period for exporting Health Connect data. */
    public static int getScheduledExportPeriodInDays() {
        String result = PreferenceHelper.getInstance().getPreference(EXPORT_PERIOD_PREFERENCE_KEY);

        if (result == null) return 0;
        return Integer.parseInt(result);
    }
}
