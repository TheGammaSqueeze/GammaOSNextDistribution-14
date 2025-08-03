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

import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_FALSE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_TRUE;
import static android.adservices.extdata.AdServicesExtDataParams.BOOLEAN_UNKNOWN;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_MANUAL_INTERACTIONS_RECORDED;
import static android.adservices.extdata.AdServicesExtDataParams.STATE_UNKNOWN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.extdata.AdServicesExtDataParams;
import android.app.adservices.AdServicesManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.adservices.service.stats.ConsentMigrationStats;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

@SpyStatic(SdkLevel.class)
@SpyStatic(DeviceRegionProvider.class)
public class AdExtDataConsentMigrationUtilsTest extends AdServicesExtendedMockitoTestCase {
    private static final String EXTSERVICES_PKG_NAME_SUFFIX = "android.ext.services";
    private static final String ADSERVICES_PKG_NAME_SUFFIX = "android.adservices.api";

    private static final AdServicesExtDataParams TEST_PARAMS_WITH_ALL_DATA =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_TRUE)
                    .setMsmtConsent(BOOLEAN_FALSE)
                    .setIsU18Account(BOOLEAN_TRUE)
                    .setIsAdultAccount(BOOLEAN_FALSE)
                    .setManualInteractionWithConsentStatus(STATE_MANUAL_INTERACTIONS_RECORDED)
                    .build();

    private static final AdServicesExtDataParams TEST_PARAMS_WITH_PARTIAL_DATA =
            new AdServicesExtDataParams.Builder()
                    .setNotificationDisplayed(BOOLEAN_TRUE)
                    .setMsmtConsent(BOOLEAN_TRUE)
                    .setIsU18Account(BOOLEAN_UNKNOWN)
                    .setIsAdultAccount(BOOLEAN_UNKNOWN)
                    .setManualInteractionWithConsentStatus(STATE_UNKNOWN)
                    .build();

    private static final int REGION_ROW_CODE = 2;

    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();
    @Mock private AppSearchConsentManager mAppSearchConsentManagerMock;
    @Mock private AdServicesExtDataStorageServiceManager mAdServicesExtDataManagerMock;
    @Mock private SharedPreferences mSharedPreferencesMock;
    @Mock private SharedPreferences.Editor mSharedPreferencesEditorMock;
    @Mock private AdServicesExtDataParams mAdServicesExtDataParamsMock;
    @Mock private StatsdAdServicesLogger mStatsdAdServicesLoggerMock;
    @Mock private AdServicesManager mAdServicesManagerMock;

    @Test
    public void testHandleConsentMigrationFromAdExtDataIfNeeded_onR_skipsMigration() {
        doReturn(false).when(SdkLevel::isAtLeastS);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onSWithNullAdExtManager_skipsMigration() {
        mockSDevice();

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                null,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onSWithPastMigrationDone_skipsMigration() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, false))
                .thenReturn(true);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void testHandleConsentMigrationFromAdExtDataIfNeeded_onSWithNotifOnS_skipsMigration() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        mockNoMigration();
        when(mAppSearchConsentManagerMock.wasU18NotificationDisplayed()).thenReturn(true);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void testHandleConsentMigrationFromAdExtDataIfNeeded_onSWithNoNotifOnR_skipsMigration() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        mockNoMigration();
        mockNoNotifOnS();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(mAdServicesExtDataParamsMock);
        when(mAdServicesExtDataParamsMock.getIsNotificationDisplayed()).thenReturn(BOOLEAN_FALSE);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onSWithMigrationEligibleWithFullData_migrationSuccessWithAdExtDataCleared() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        mockNoMigration();
        mockNoNotifOnS();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(TEST_PARAMS_WITH_ALL_DATA);
        when(mSharedPreferencesMock.edit()).thenReturn(mSharedPreferencesEditorMock);
        when(mSharedPreferencesEditorMock.commit()).thenReturn(true);

        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAdServicesManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        verify(mAppSearchConsentManagerMock)
                .setConsent(
                        AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey(),
                        TEST_PARAMS_WITH_ALL_DATA.getIsMeasurementConsented() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .setU18NotificationDisplayed(
                        TEST_PARAMS_WITH_ALL_DATA.getIsNotificationDisplayed() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .recordUserManualInteractionWithConsent(
                        TEST_PARAMS_WITH_ALL_DATA.getManualInteractionWithConsentStatus());
        verify(mAppSearchConsentManagerMock)
                .setU18Account(TEST_PARAMS_WITH_ALL_DATA.getIsU18Account() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .setAdultAccount(TEST_PARAMS_WITH_ALL_DATA.getIsAdultAccount() == BOOLEAN_TRUE);
        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verify(mAdServicesExtDataManagerMock).clearDataOnOtaAsync();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(false)
                        .setFledgeConsent(false)
                        .setMsmtConsent(false)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.ADEXT_SERVICE_TO_APPSEARCH)
                        .setRegion(REGION_ROW_CODE)
                        .build();
        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onSWithMigrationEligibleWithPartialDataWithFailedSharedPrefUpdate_migrationSuccessWithAdExtDataCleared() {
        mockSDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        mockNoMigration();
        mockNoNotifOnS();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(TEST_PARAMS_WITH_PARTIAL_DATA);
        when(mSharedPreferencesMock.edit()).thenReturn(mSharedPreferencesEditorMock);
        when(mSharedPreferencesEditorMock.commit()).thenReturn(false);

        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAdServicesManagerMock);

        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        verify(mAppSearchConsentManagerMock)
                .setConsent(
                        AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey(),
                        TEST_PARAMS_WITH_PARTIAL_DATA.getIsMeasurementConsented() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock)
                .setU18NotificationDisplayed(
                        TEST_PARAMS_WITH_PARTIAL_DATA.getIsNotificationDisplayed() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock, never())
                .recordUserManualInteractionWithConsent(
                        TEST_PARAMS_WITH_PARTIAL_DATA.getManualInteractionWithConsentStatus());
        verify(mAppSearchConsentManagerMock, never())
                .setU18Account(TEST_PARAMS_WITH_PARTIAL_DATA.getIsU18Account() == BOOLEAN_TRUE);
        verify(mAppSearchConsentManagerMock, never())
                .setAdultAccount(TEST_PARAMS_WITH_PARTIAL_DATA.getIsAdultAccount() == BOOLEAN_TRUE);

        verifyNoMoreInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verify(mAdServicesExtDataManagerMock).clearDataOnOtaAsync();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(false)
                        .setFledgeConsent(false)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.ADEXT_SERVICE_TO_APPSEARCH)
                        .setRegion(REGION_ROW_CODE)
                        .build();
        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithNullAdExtManager_skipsMigration() {
        mockTDevice();

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                null,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithMigrationFromAdExtDone_skipsMigration() {
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER,
                        false))
                .thenReturn(true);

        ensureNoMigrationIfPastMigrationDoneOnTPlus();
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithMigrationFromAppSearchDone_skipsMigration() {
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED, false))
                .thenReturn(true);

        ensureNoMigrationIfPastMigrationDoneOnTPlus();
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithMigrationFromPpapiDone_skipsMigration() {
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED, false))
                .thenReturn(true);

        ensureNoMigrationIfPastMigrationDoneOnTPlus();
    }

    private void ensureNoMigrationIfPastMigrationDoneOnTPlus() {
        mockTDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithExtServicesPkg_skipsMigration() {
        mockTDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mContextSpy.getPackageName()).thenReturn(EXTSERVICES_PKG_NAME_SUFFIX);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);
        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAdServicesManagerMock);
    }

    @Test
    public void testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithNotifOnT_skipsMigration() {
        mockTDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        when(mContextSpy.getPackageName()).thenReturn(ADSERVICES_PKG_NAME_SUFFIX);
        mockNoMigration();
        when(mAdServicesManagerMock.wasU18NotificationDisplayed()).thenReturn(true);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verify(mAdServicesManagerMock).wasU18NotificationDisplayed();
        verifyNoMoreInteractions(mAdServicesManagerMock);

        verifyZeroInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAppSearchConsentManagerMock);
    }

    @Test
    public void testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithNoNotifOnR_skipsMigration() {
        mockTDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        mockNoMigration();
        when(mContextSpy.getPackageName()).thenReturn(ADSERVICES_PKG_NAME_SUFFIX);
        mockNoNotifOnT();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(mAdServicesExtDataParamsMock);
        when(mAdServicesExtDataParamsMock.getIsNotificationDisplayed()).thenReturn(BOOLEAN_FALSE);

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verify(mAdServicesManagerMock).wasU18NotificationDisplayed();
        verify(mAdServicesManagerMock).wasGaUxNotificationDisplayed();
        verify(mAdServicesManagerMock).wasNotificationDisplayed();
        verifyNoMoreInteractions(mAdServicesManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);
        verifyZeroInteractions(mStatsdAdServicesLoggerMock);
        verifyZeroInteractions(mAppSearchConsentManagerMock);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithMigrationEligibleWithFullData_migrationSuccessWithAdExtDataCleared() {
        mockTDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        mockNoMigration();
        when(mContextSpy.getPackageName()).thenReturn(ADSERVICES_PKG_NAME_SUFFIX);
        mockNoNotifOnT();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(TEST_PARAMS_WITH_ALL_DATA);
        when(mSharedPreferencesMock.edit()).thenReturn(mSharedPreferencesEditorMock);
        when(mSharedPreferencesEditorMock.commit()).thenReturn(true);

        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesManagerMock).wasU18NotificationDisplayed();
        verify(mAdServicesManagerMock).wasGaUxNotificationDisplayed();
        verify(mAdServicesManagerMock).wasNotificationDisplayed();
        verify(mAdServicesManagerMock).setConsent(any());

        verify(mAdServicesManagerMock)
                .setU18NotificationDisplayed(
                        TEST_PARAMS_WITH_ALL_DATA.getIsNotificationDisplayed() == BOOLEAN_TRUE);
        verify(mAdServicesManagerMock)
                .recordUserManualInteractionWithConsent(
                        TEST_PARAMS_WITH_ALL_DATA.getManualInteractionWithConsentStatus());
        verify(mAdServicesManagerMock)
                .setU18Account(TEST_PARAMS_WITH_ALL_DATA.getIsU18Account() == BOOLEAN_TRUE);
        verify(mAdServicesManagerMock)
                .setAdultAccount(TEST_PARAMS_WITH_ALL_DATA.getIsAdultAccount() == BOOLEAN_TRUE);
        verifyNoMoreInteractions(mAdServicesManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verify(mAdServicesExtDataManagerMock).clearDataOnOtaAsync();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(false)
                        .setFledgeConsent(false)
                        .setMsmtConsent(false)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.ADEXT_SERVICE_TO_SYSTEM_SERVICE)
                        .setRegion(REGION_ROW_CODE)
                        .build();
        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    public void
            testHandleConsentMigrationFromAdExtDataIfNeeded_onTWithMigrationEligibleWithPartialDataWithFailedSharedPrefUpdate_migrationSuccessWithAdExtDataCleared() {
        mockTDevice();

        when(mContextSpy.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mSharedPreferencesMock);
        mockNoMigration();
        when(mContextSpy.getPackageName()).thenReturn(ADSERVICES_PKG_NAME_SUFFIX);
        mockNoNotifOnT();
        when(mAdServicesExtDataManagerMock.getAdServicesExtData())
                .thenReturn(TEST_PARAMS_WITH_PARTIAL_DATA);
        when(mSharedPreferencesMock.edit()).thenReturn(mSharedPreferencesEditorMock);
        when(mSharedPreferencesEditorMock.commit()).thenReturn(false);

        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        AdExtDataConsentMigrationUtils.handleConsentMigrationFromAdExtDataIfNeeded(
                mContextSpy,
                mAppSearchConsentManagerMock,
                mAdServicesExtDataManagerMock,
                mStatsdAdServicesLoggerMock,
                mAdServicesManagerMock);

        verifyZeroInteractions(mAppSearchConsentManagerMock);

        verify(mAdServicesManagerMock).wasU18NotificationDisplayed();
        verify(mAdServicesManagerMock).wasGaUxNotificationDisplayed();
        verify(mAdServicesManagerMock).wasNotificationDisplayed();

        verify(mAdServicesManagerMock).setConsent(any());
        verify(mAdServicesManagerMock)
                .setU18NotificationDisplayed(
                        TEST_PARAMS_WITH_PARTIAL_DATA.getIsNotificationDisplayed() == BOOLEAN_TRUE);
        verify(mAdServicesManagerMock, never())
                .recordUserManualInteractionWithConsent(
                        TEST_PARAMS_WITH_PARTIAL_DATA.getManualInteractionWithConsentStatus());
        verify(mAdServicesManagerMock, never())
                .setU18Account(TEST_PARAMS_WITH_PARTIAL_DATA.getIsU18Account() == BOOLEAN_TRUE);
        verify(mAdServicesManagerMock, never())
                .setAdultAccount(TEST_PARAMS_WITH_PARTIAL_DATA.getIsAdultAccount() == BOOLEAN_TRUE);

        verifyNoMoreInteractions(mAdServicesManagerMock);

        verify(mAdServicesExtDataManagerMock).getAdServicesExtData();
        verify(mAdServicesExtDataManagerMock).clearDataOnOtaAsync();
        verifyNoMoreInteractions(mAdServicesExtDataManagerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(false)
                        .setFledgeConsent(false)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.ADEXT_SERVICE_TO_SYSTEM_SERVICE)
                        .setRegion(REGION_ROW_CODE)
                        .build();
        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    private void mockNoNotifOnS() {
        when(mAppSearchConsentManagerMock.wasU18NotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAppSearchConsentManagerMock.wasNotificationDisplayed()).thenReturn(false);
    }

    private void mockNoNotifOnT() {
        when(mAdServicesManagerMock.wasU18NotificationDisplayed()).thenReturn(false);
        when(mAdServicesManagerMock.wasGaUxNotificationDisplayed()).thenReturn(false);
        when(mAdServicesManagerMock.wasNotificationDisplayed()).thenReturn(false);
    }

    private void mockSDevice() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(true).when(SdkLevel::isAtLeastS);
    }

    private void mockTDevice() {
        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(true).when(SdkLevel::isAtLeastS);
    }

    private void mockNoMigration() {
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED, false))
                .thenReturn(false);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED, false))
                .thenReturn(false);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED_TO_APP_SEARCH, false))
                .thenReturn(false);
        when(mSharedPreferencesMock.getBoolean(
                        ConsentConstants.SHARED_PREFS_KEY_MIGRATED_FROM_ADEXTDATA_TO_SYSTEM_SERVER,
                        false))
                .thenReturn(false);
    }
}
