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

package com.android.adservices.service.consent;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import com.android.adservices.service.appsearch.AppSearchConsentStorageManager;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

public class ConsentCompositeStorageTest {

    private static final String MOCK_PACKAGE_NAME = "com.xyz.abc";
    private static final int MOCK_PACKAGE_UID = 134;
    @Mock AppConsentStorageManager mAppConsentStorageManager;
    @Mock AdServicesStorageManager mAdServicesStorageManager;
    @Mock AppSearchConsentStorageManager mAppSearchConsentStorageManager;

    @Before
    public void setup() {
        mAppConsentStorageManager = mock(AppConsentStorageManager.class);
        mAdServicesStorageManager = mock(AdServicesStorageManager.class);
        mAppSearchConsentStorageManager = mock(AppSearchConsentStorageManager.class);
    }

    @Test
    public void testAllReadMethodsAppStorage() throws IOException {
        ConsentCompositeStorage consentCompositeStorage =
                new ConsentCompositeStorage(ImmutableList.of(mAppConsentStorageManager));
        testAllReadMethods(consentCompositeStorage);
    }

    @Test
    public void testAllReadMethodsPPAPIAndSystem() throws IOException {
        ConsentCompositeStorage consentCompositeStorage =
                new ConsentCompositeStorage(
                        ImmutableList.of(mAppConsentStorageManager, mAdServicesStorageManager));
        testAllReadMethods(consentCompositeStorage);
    }

    @Test
    public void testAllWriteMethodsAppStorage() throws IOException {
        ConsentCompositeStorage consentCompositeStorage =
                new ConsentCompositeStorage(ImmutableList.of(mAppConsentStorageManager));
        testAllWriteMethods(consentCompositeStorage);
    }

    @Test
    public void testAllWriteMethodsPPAPIAndSystem() throws IOException {
        ConsentCompositeStorage consentCompositeStorage =
                new ConsentCompositeStorage(
                        ImmutableList.of(mAppConsentStorageManager, mAdServicesStorageManager));
        testAllWriteMethods(consentCompositeStorage);
    }

    private void testAllReadMethods(ConsentCompositeStorage consentCompositeStorage)
            throws IOException {
        consentCompositeStorage.isAdIdEnabled();
        consentCompositeStorage.isAdultAccount();
        consentCompositeStorage.isConsentRevokedForApp(MOCK_PACKAGE_NAME);
        consentCompositeStorage.isEntryPointEnabled();
        consentCompositeStorage.isU18Account();

        verifyReadMethods(consentCompositeStorage.getConsentStorageList().get(0));
    }

    private void testAllWriteMethods(ConsentCompositeStorage consentCompositeStorage)
            throws IOException {
        consentCompositeStorage.clearAllAppConsentData();
        consentCompositeStorage.clearConsentForUninstalledApp(MOCK_PACKAGE_NAME);
        consentCompositeStorage.clearConsentForUninstalledApp(MOCK_PACKAGE_NAME, MOCK_PACKAGE_UID);
        consentCompositeStorage.clearKnownAppsWithConsent();

        consentCompositeStorage.recordGaUxNotificationDisplayed(true);
        consentCompositeStorage.recordNotificationDisplayed(false);
        consentCompositeStorage.recordUserManualInteractionWithConsent(1);
        consentCompositeStorage.setAdIdEnabled(true);
        consentCompositeStorage.setAdultAccount(true);
        consentCompositeStorage.setConsent(AdServicesApiType.ALL_API, true);
        consentCompositeStorage.setConsentForApp(MOCK_PACKAGE_NAME, false);
        consentCompositeStorage.setConsentForAppIfNew(MOCK_PACKAGE_NAME, true);
        consentCompositeStorage.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        consentCompositeStorage.setEnrollmentChannel(
                PrivacySandboxUxCollection.GA_UX, GaUxEnrollmentChannelCollection.values()[0]);
        consentCompositeStorage.setEntryPointEnabled(false);
        consentCompositeStorage.setU18Account(true);
        consentCompositeStorage.setU18NotificationDisplayed(false);
        consentCompositeStorage.setUx(PrivacySandboxUxCollection.GA_UX);

        List<IConsentStorage> storageList = consentCompositeStorage.getConsentStorageList();
        for (IConsentStorage storage : storageList) {
            verifySetMethodCalled(storage);
        }
    }

    private void verifyReadMethods(IConsentStorage consentStorage) throws IOException {
        Mockito.verify(consentStorage).isAdIdEnabled();
        Mockito.verify(consentStorage).isAdultAccount();
        Mockito.verify(consentStorage).isConsentRevokedForApp(eq(MOCK_PACKAGE_NAME));
        Mockito.verify(consentStorage).isEntryPointEnabled();
        Mockito.verify(consentStorage).isU18Account();
    }

    private void verifySetMethodCalled(IConsentStorage consentStorage) throws IOException {

        Mockito.verify(consentStorage).clearAllAppConsentData();
        Mockito.verify(consentStorage).clearConsentForUninstalledApp(eq(MOCK_PACKAGE_NAME));
        Mockito.verify(consentStorage)
                .clearConsentForUninstalledApp(eq(MOCK_PACKAGE_NAME), eq(MOCK_PACKAGE_UID));
        Mockito.verify(consentStorage).clearKnownAppsWithConsent();

        Mockito.verify(consentStorage).recordGaUxNotificationDisplayed(eq(true));
        Mockito.verify(consentStorage).recordNotificationDisplayed(eq(false));
        Mockito.verify(consentStorage).recordUserManualInteractionWithConsent(eq(1));
        Mockito.verify(consentStorage).setAdIdEnabled(eq(true));
        Mockito.verify(consentStorage).setAdultAccount(eq(true));
        Mockito.verify(consentStorage).setConsent(eq(AdServicesApiType.ALL_API), eq(true));
        Mockito.verify(consentStorage).setConsentForApp(eq(MOCK_PACKAGE_NAME), eq(false));
        Mockito.verify(consentStorage).setConsentForAppIfNew(eq(MOCK_PACKAGE_NAME), eq(true));
        Mockito.verify(consentStorage)
                .setCurrentPrivacySandboxFeature(
                        eq(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT));
        Mockito.verify(consentStorage)
                .setEnrollmentChannel(
                        eq(PrivacySandboxUxCollection.GA_UX),
                        eq(GaUxEnrollmentChannelCollection.values()[0]));
        Mockito.verify(consentStorage).setEntryPointEnabled(eq(false));
        Mockito.verify(consentStorage).setU18Account(eq(true));
        Mockito.verify(consentStorage).setU18NotificationDisplayed(eq(false));
        Mockito.verify(consentStorage).setUx(eq(PrivacySandboxUxCollection.GA_UX));
    }
}
