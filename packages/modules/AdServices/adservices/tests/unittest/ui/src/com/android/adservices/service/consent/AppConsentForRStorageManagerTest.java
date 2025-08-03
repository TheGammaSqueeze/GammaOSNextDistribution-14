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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;


import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.adservices.service.ui.data.UxStatesDao;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;

@SpyStatic(PackageManagerCompatUtils.class)
@SpyStatic(SdkLevel.class)
@SpyStatic(FlagsFactory.class)
public final class AppConsentForRStorageManagerTest extends AdServicesExtendedMockitoTestCase {

    private BooleanFileDatastore mAppDaoDatastore;
    private BooleanFileDatastore mConsentDatastore;
    private AppConsentDao mAppConsentDaoSpy;
    private AppConsentForRStorageManager mAppConsentForRStorageManager;
    @Mock private UxStatesDao mUxStatesDaoMock;

    @Mock private AdServicesExtDataStorageServiceManager mAdExtDataManager;

    @Before
    public void setup() {
        mConsentDatastore =
                new BooleanFileDatastore(
                        mSpyContext,
                        ConsentConstants.STORAGE_XML_IDENTIFIER,
                        ConsentConstants.STORAGE_VERSION);

        mAppDaoDatastore =
                new BooleanFileDatastore(
                        mSpyContext, AppConsentDao.DATASTORE_NAME, AppConsentDao.DATASTORE_VERSION);

        mAppConsentDaoSpy =
                spy(new AppConsentDao(mAppDaoDatastore, mSpyContext.getPackageManager()));
        mAppConsentForRStorageManager =
                spy(
                        new AppConsentForRStorageManager(
                                mConsentDatastore,
                                mAppConsentDaoSpy,
                                mUxStatesDaoMock,
                                mAdExtDataManager));
    }

    @After
    public void teardown() throws IOException {
        mAppDaoDatastore.clear();
        mConsentDatastore.clear();
    }

    @Test
    public void testNotSupportMethodException() {
        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.setConsentForApp("", false));

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.clearAllAppConsentData());

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.clearConsentForUninstalledApp("", 0));

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.clearConsentForUninstalledApp(""));

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.clearKnownAppsWithConsent());

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.recordGaUxNotificationDisplayed(true));

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.recordNotificationDisplayed(true));

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.getAppsWithRevokedConsent());

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.getKnownAppsWithConsent());

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.isConsentRevokedForApp(""));

        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.setConsentForAppIfNew("", true));
    }

    @Test
    public void testGetMeasurementConsent() {
        when(mAdExtDataManager.getMsmtConsent()).thenReturn(true);
        for (AdServicesApiType apiType : AdServicesApiType.values()) {
            AdServicesApiConsent apiConsent = mAppConsentForRStorageManager.getConsent(apiType);
            if (apiType == AdServicesApiType.MEASUREMENTS) {

                assertThat(apiConsent.isGiven()).isTrue();
            } else {
                assertThat(apiConsent.isGiven()).isFalse();
            }
        }
    }

    @Test
    public void testSetMeasurementConsent() throws IOException {
        mAppConsentForRStorageManager.setConsent(AdServicesApiType.MEASUREMENTS, true);
        verify(mAdExtDataManager).setMsmtConsent(eq(true));
    }

    @Test
    public void testGetUserManualInteractionWithConsent() {
        mAppConsentForRStorageManager.getUserManualInteractionWithConsent();
        verify(mAdExtDataManager).getManualInteractionWithConsentStatus();
    }

    @Test
    public void testSetUserManualInteractionWithConsent() {
        int userInteraction = 1;
        mAppConsentForRStorageManager.recordUserManualInteractionWithConsent(userInteraction);
        verify(mAdExtDataManager).setManualInteractionWithConsentStatus(eq(userInteraction));
    }

    @Test
    public void testIsAdultAccount() {
        mAppConsentForRStorageManager.isAdultAccount();
        verify(mAdExtDataManager).getIsAdultAccount();
    }

    @Test
    public void testSetIsAdultAccount() {
        mAppConsentForRStorageManager.setAdultAccount(true);
        verify(mAdExtDataManager).setIsAdultAccount(eq(true));
    }

    @Test
    public void testIsU18Account() {
        mAppConsentForRStorageManager.isU18Account();
        verify(mAdExtDataManager).getIsU18Account();
    }

    @Test
    public void testGetU18Notification() {
        mAppConsentForRStorageManager.wasU18NotificationDisplayed();
        verify(mAdExtDataManager).getNotificationDisplayed();
    }

    @Test
    public void testSetU18Notification() {
        mAppConsentForRStorageManager.setU18NotificationDisplayed(true);
        verify(mAdExtDataManager).setNotificationDisplayed(eq(true));
    }

    @Test
    public void testSetIsU18Account() {
        mAppConsentForRStorageManager.setU18Account(true);
        verify(mAdExtDataManager).setIsU18Account(eq(true));
    }

    @Test
    public void testGAAndBetaNotificationFlag() {
        boolean gaDisplayedFlag = mAppConsentForRStorageManager.wasGaUxNotificationDisplayed();

        boolean betaDisplayedFlag = mAppConsentForRStorageManager.wasNotificationDisplayed();
        assertThat(gaDisplayedFlag).isFalse();
        assertThat(betaDisplayedFlag).isFalse();
    }

    @Test
    public void testSetMeasurementConsentException() {
        assertThrows(
                IllegalStateException.class,
                () -> mAppConsentForRStorageManager.setConsent(AdServicesApiType.FLEDGE, true));
    }
}
