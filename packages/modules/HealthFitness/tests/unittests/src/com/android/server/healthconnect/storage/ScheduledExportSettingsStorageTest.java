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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.health.connect.exportimport.ScheduledExportSettings;
import android.net.Uri;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@RunWith(AndroidJUnit4.class)
public final class ScheduledExportSettingsStorageTest {
    private static final String EXPORT_SALT_PREFERENCE_KEY = "export_salt_key";
    private static final String EXPORT_URI_PREFERENCE_KEY = "export_uri_key";
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";
    private static final String EXPORT_KEYSTORE_ENTRY = "health_connect_export_key";
    private static final String TEST_PASSWORD = "testpassword";
    private static final byte[] TEST_SALT = new byte[] {1, 2, 3, 4};
    private static final String TEST_URI = "content://com.android.server.healthconnect/testuri";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(PreferenceHelper.class).build();

    private final PreferenceHelper mFakePreferenceHelper = new FakePreferenceHelper();

    @Before
    public void setUp() {
        when(PreferenceHelper.getInstance()).thenReturn(mFakePreferenceHelper);
    }

    @Test
    public void testConfigure_secretKey()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
                    UnrecoverableEntryException, InvalidKeySpecException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withSecretKey(generateSecretKey(), TEST_SALT));

        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry) keyStore.getEntry(EXPORT_KEYSTORE_ENTRY, null);
        assertThat(entry).isNotNull();
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_SALT_PREFERENCE_KEY))
                .isEqualTo(Arrays.toString(TEST_SALT));
    }

    @Test
    public void testConfigure_secretKey_keepsOtherSettings()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
                    InvalidKeySpecException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));
        ScheduledExportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withSecretKey(generateSecretKey(), TEST_SALT));

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY))
                .isEqualTo(TEST_URI);
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(7));
    }

    @Test
    public void testConfigure_uri()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY))
                .isEqualTo(TEST_URI);
    }

    @Test
    public void testConfigure_uri_keepsOtherSettings()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
                    InvalidKeySpecException, UnrecoverableEntryException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withSecretKey(generateSecretKey(), TEST_SALT));
        ScheduledExportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry) keyStore.getEntry(EXPORT_KEYSTORE_ENTRY, null);
        assertThat(entry).isNotNull();
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_SALT_PREFERENCE_KEY))
                .isEqualTo(Arrays.toString(TEST_SALT));
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(7));
    }

    @Test
    public void testConfigure_periodInDays()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        ScheduledExportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(7));
    }

    @Test
    public void testConfigure_periodInDays_keepsOtherSettings()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
                    InvalidKeySpecException, UnrecoverableEntryException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withSecretKey(generateSecretKey(), TEST_SALT));
        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        ScheduledExportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry) keyStore.getEntry(EXPORT_KEYSTORE_ENTRY, null);
        assertThat(entry).isNotNull();
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_SALT_PREFERENCE_KEY))
                .isEqualTo(Arrays.toString(TEST_SALT));
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY))
                .isEqualTo(TEST_URI);
    }

    @Test
    public void testConfigure_clear()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
                    InvalidKeySpecException, UnrecoverableEntryException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withSecretKey(generateSecretKey(), TEST_SALT));
        ScheduledExportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));
        ScheduledExportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        ScheduledExportSettingsStorage.configure(null);

        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry) keyStore.getEntry(EXPORT_KEYSTORE_ENTRY, null);
        assertThat(entry).isNull();

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_SALT_PREFERENCE_KEY)).isNull();
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY)).isNull();
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY)).isNull();
    }

    @Test
    public void testGetScheduledExportPeriodInDays()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
        ScheduledExportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(1));

        assertThat(ScheduledExportSettingsStorage.getScheduledExportPeriodInDays()).isEqualTo(1);
    }

    private static byte[] generateSecretKey()
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec pbeKeySpec = new PBEKeySpec(TEST_PASSWORD.toCharArray(), TEST_SALT, 1000, 256);
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return secretKeyFactory.generateSecret(pbeKeySpec).getEncoded();
    }
}
