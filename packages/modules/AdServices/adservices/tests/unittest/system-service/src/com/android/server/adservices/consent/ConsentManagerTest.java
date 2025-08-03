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

package com.android.server.adservices.consent;

import static com.android.adservices.shared.testing.common.DumpHelper.assertDumpHasPrefix;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.server.adservices.consent.ConsentManager.NOTIFICATION_DISPLAYED_ONCE;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_VERSION;
import static com.android.server.adservices.consent.ConsentManager.STORAGE_XML_IDENTIFIER;
import static com.android.server.adservices.consent.ConsentManager.VERSION_KEY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.adservices.consent.ConsentParcel;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.storage.BooleanFileDatastore;
import com.android.server.adservices.feature.PrivacySandboxEnrollmentChannelCollection;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Tests for {@link ConsentManager} */
public class ConsentManagerTest {
    private static final Context PPAPI_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String BASE_DIR = PPAPI_CONTEXT.getFilesDir().getAbsolutePath();

    private BooleanFileDatastore mDatastore;

    @Before
    public void setup() {
        mDatastore =
                new BooleanFileDatastore(
                        PPAPI_CONTEXT.getFilesDir().getAbsolutePath(),
                        STORAGE_XML_IDENTIFIER,
                        STORAGE_VERSION,
                        VERSION_KEY);
    }

    @After
    public void tearDown() {
        mDatastore.tearDownForTesting();
    }

    @Test
    public void testGetConsentDataStoreDir() {
        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/
        assertThat(
                        ConsentDatastoreLocationHelper.getConsentDataStoreDir(
                                /* baseDir */ "/data/system/adservices", /* userIdentifier */ 0))
                .isEqualTo("/data/system/adservices/0/consent");
        assertThat(
                        ConsentDatastoreLocationHelper.getConsentDataStoreDir(
                                /* baseDir */ "/data/system/adservices", /* userIdentifier */ 1))
                .isEqualTo("/data/system/adservices/1/consent");
        assertThrows(
                NullPointerException.class,
                () -> ConsentDatastoreLocationHelper.getConsentDataStoreDir(null, 0));
    }

    @Test
    public void testCreateAndInitBooleanFileDatastore() {
        BooleanFileDatastore datastore = null;
        try {
            datastore = ConsentManager.createAndInitBooleanFileDatastore(BASE_DIR);
        } catch (IOException e) {
            Assert.fail("Fail to create the DataStore");
        }

        // Assert that the DataStore is created and initialized with NOTIFICATION_DISPLAYED_ONCE
        // is false.
        assertThat(datastore.get(NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testGetConsent_unSet() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Newly initialized ConsentManager has consent = false.
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_null() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.ALL_API)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.TOPICS)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.FLEDGE)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        consentManager.setConsent(
                new ConsentParcel.Builder()
                        .setConsentApiType(ConsentParcel.MEASUREMENT)
                        .setIsGiven(null)
                        .build());
        // null means the consent is not given (false).
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_nonNull() throws IOException {
        // Create a ConsentManager for user 0.
        ConsentManager consentManager0 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        assertThat(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();

        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.TOPICS));
        assertThat(consentManager0.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.TOPICS));
        assertThat(consentManager0.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();

        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE));
        assertThat(consentManager0.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE));
        assertThat(consentManager0.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();

        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.MEASUREMENT));
        assertThat(consentManager0.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();

        consentManager0.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT));
        assertThat(consentManager0.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();

        // Create another ConsentManager for user 1 to make sure ConsentManagers
        // are isolated by users.
        ConsentManager consentManager1 =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 1);
        // By default, this ConsentManager has isGiven false.
        assertThat(consentManager1.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // Set the user 0 to revoked.
        consentManager0.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        assertThat(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // Set the user 1 to given.
        consentManager1.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager1.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();

        // This validates that the consentManager for user 0 was not changed when updating
        // ConsentManager for user 1.
        assertThat(consentManager0.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();
    }

    @Test
    public void testGetAndSetConsent_upgrade() throws IOException {
        // Create a ConsentManager for user 0.
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Test upgrading from 1 consent to 3 consents.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isFalse();
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isFalse();

        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.TOPICS).isIsGiven()).isTrue();
        assertThat(consentManager.getConsent(ConsentParcel.FLEDGE).isIsGiven()).isTrue();
        assertThat(consentManager.getConsent(ConsentParcel.MEASUREMENT).isIsGiven()).isTrue();
    }

    @Test
    public void testGetAndSetConsent_downgrade() throws IOException {
        // Create a ConsentManager for user 0.
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Test downgrading from 3 consents to 1 consent.
        // For Topics.
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only Topics to false, the ALL_API will get false value too.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.TOPICS));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // For FLEDGE
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only FLEDGE to false, the ALL_API will get false value too.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // For Measurement
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
        // Now even setting only Measurement to false, the ALL_API will get false value too.
        consentManager.setConsent(ConsentParcel.createRevokedConsent(ConsentParcel.MEASUREMENT));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isFalse();

        // Now setting 3 consents to true and the ALL_API will be true too.
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.TOPICS));
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE));
        consentManager.setConsent(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT));
        assertThat(consentManager.getConsent(ConsentParcel.ALL_API).isIsGiven()).isTrue();
    }

    @Test
    public void testGetConsent_unSetConsentApiType() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // Newly initialized ConsentManager has consent = false.
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    consentManager.setConsent(
                            new ConsentParcel.Builder()
                                    // Not set the ConsentApiType.
                                    // .setConsentApiType(xxx)
                                    .setIsGiven(true)
                                    .build());
                });
    }

    @Test
    public void testRecordNotificationDisplayed() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // First, the notification displayed is false.
        assertThat(consentManager.wasNotificationDisplayed()).isFalse();
        consentManager.recordNotificationDisplayed(true);

        assertThat(consentManager.wasNotificationDisplayed()).isTrue();
    }

    @Test
    public void testGaUxRecordNotificationDisplayed() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);
        // First, the notification displayed is false.
        assertThat(consentManager.wasGaUxNotificationDisplayed()).isFalse();
        consentManager.recordGaUxNotificationDisplayed(true);

        assertThat(consentManager.wasGaUxNotificationDisplayed()).isTrue();
    }

    @Test
    public void testDeleteConsentDataStoreDir() throws IOException {
        int userIdentifier = 0;
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, userIdentifier);
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        BASE_DIR, userIdentifier);
        Path packageDir = Paths.get(consentDataStoreDir);
        assertThat(Files.exists(packageDir)).isTrue();
        String userDirectoryPath = BASE_DIR + "/" + userIdentifier;
        assertThat(consentManager.deleteUserDirectory(new File(userDirectoryPath))).isTrue();

        assertThat(Files.exists(packageDir)).isFalse();
    }

    @Test
    public void testSetUserManualInteractionWithConsentToTrue() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.recordUserManualInteractionWithConsent(1);

        assertThat(consentManager.getUserManualInteractionWithConsent()).isEqualTo(1);
    }

    @Test
    public void testSetUserManualInteractionWithConsentToFalse() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.recordUserManualInteractionWithConsent(-1);

        assertThat(consentManager.getUserManualInteractionWithConsent()).isEqualTo(-1);
    }

    @Test
    public void testSetUserManualInteractionWithConsentToUnknown() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        consentManager.recordUserManualInteractionWithConsent(0);

        assertThat(consentManager.getUserManualInteractionWithConsent()).isEqualTo(0);
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        // All bits are false in the beginning.
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isEqualTo(false);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isEqualTo(false);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isEqualTo(false);

        consentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name());
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isEqualTo(true);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isEqualTo(false);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isEqualTo(false);

        consentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name());
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isEqualTo(true);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isEqualTo(false);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isEqualTo(false);

        consentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name());
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED))
                .isEqualTo(true);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT))
                .isEqualTo(false);
        assertThat(
                        consentManager.isPrivacySandboxFeatureEnabled(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT))
                .isEqualTo(false);
    }

    @Test
    public void uxConformanceTest() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        // All bits are fall in the beginning.
        assertThat(consentManager.getUx())
                .isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX.toString());

        Stream.of(PrivacySandboxUxCollection.values())
                .forEach(
                        ux -> {
                            consentManager.setUx(ux.toString());
                            assertThat(consentManager.getUx()).isEqualTo(ux.toString());
                        });
    }

    @Test
    public void testDeleteConsentDataStoreDirUserIdentifierNotPresent() throws IOException {
        int userIdentifier = 0;
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, userIdentifier);
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        BASE_DIR, userIdentifier);
        Path packageDir = Paths.get(consentDataStoreDir);

        int userIdentifierNotPresent = 3;
        // Try deleting with non-existent user id. Nothing should happen and ensure userIdentifier
        // is present.
        assertThat(
                        consentManager.deleteUserDirectory(
                                new File(BASE_DIR + userIdentifierNotPresent)))
                .isFalse();
        assertThat(Files.exists(packageDir)).isTrue();
    }

    @Test
    public void isAdIdEnabledTest() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isAdIdEnabled()).isFalse();
        consentManager.setAdIdEnabled(true);

        assertThat(consentManager.isAdIdEnabled()).isTrue();
    }

    @Test
    public void isU18AccountTest() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isU18Account()).isFalse();
        consentManager.setU18Account(true);

        assertThat(consentManager.isU18Account()).isTrue();
    }

    @Test
    public void isEntryPointEnabledTest() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isEntryPointEnabled()).isFalse();
        consentManager.setEntryPointEnabled(true);

        assertThat(consentManager.isEntryPointEnabled()).isTrue();
    }

    @Test
    public void isAdultAccountTest() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.isAdultAccount()).isFalse();
        consentManager.setAdultAccount(true);

        assertThat(consentManager.isAdultAccount()).isTrue();
    }

    @Test
    public void wasU18NotificationDisplayedTest() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        assertThat(consentManager.wasU18NotificationDisplayed()).isFalse();
        consentManager.setU18NotificationDisplayed(true);

        assertThat(consentManager.wasU18NotificationDisplayed()).isTrue();
    }

    @Test
    public void enrollmentChannelConformanceTest() throws IOException {
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier */ 0);

        // All bits are fall in the beginning.
        assertThat(consentManager.getEnrollmentChannel()).isEqualTo(null);

        Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                .forEach(
                        channel -> {
                            consentManager.setEnrollmentChannel(channel.toString());
                            assertThat(consentManager.getEnrollmentChannel())
                                    .isEqualTo(channel.toString());
                        });
    }

    @Test
    public void testDump() throws Exception {
        String prefix = "_";
        ConsentManager consentManager =
                ConsentManager.createConsentManager(BASE_DIR, /* userIdentifier= */ 0);

        String dump = dump(pw -> consentManager.dump(pw, prefix));

        assertWithMessage("content of dump()").that(dump).startsWith(prefix + "ConsentManager:");
        assertDumpHasPrefix(dump, prefix);
        // ConsentManager only dumps the datastore, which is not accessible, so there's nothing else
        // to check
    }
}
