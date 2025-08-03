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

package com.android.adservices.service.appsearch;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.UserHandle;

import androidx.appsearch.app.AppSearchBatchResult;
import androidx.appsearch.app.AppSearchResult;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.app.SetSchemaResponse;
import androidx.appsearch.platformstorage.PlatformStorage;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentConstants;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SpyStatic(FlagsFactory.class)
@RequiresSdkLevelAtLeastS
public final class AppSearchConsentWorkerTest extends AdServicesExtendedMockitoTestCase {

    private static final String ADSERVICES_PACKAGE_NAME = "com.android.adservices.api";
    private static final String ADEXTSERVICES_PACKAGE_NAME = "com.android.ext.services";
    private static final String API_TYPE = AdServicesApiType.TOPICS.toPpApiDatastoreKey();
    private static final Boolean CONSENTED = true;
    private static final String TEST = "test";
    private static final int UID = 55;
    private static final Topic TOPIC1 = Topic.create(0, 1, 11);
    private static final Topic TOPIC2 = Topic.create(12, 2, 22);
    private static final Topic TOPIC3 = Topic.create(123, 3, 33);
    private static final int APPSEARCH_TIMEOUT_MS = 1000;

    private final List<Topic> mTopics = Arrays.asList(TOPIC1, TOPIC2, TOPIC3);
    private final ListeningExecutorService mExecutorService =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(mMockFlags);
        when(mMockFlags.getAdservicesApkShaCertificate())
                .thenReturn(Flags.ADSERVICES_APK_SHA_CERTIFICATE);
        when(mMockFlags.getAppsearchWriterAllowListOverride()).thenReturn("");
        // Reduce AppSearch write timeout to speed up the tests.
        when(mMockFlags.getAppSearchWriteTimeout()).thenReturn(APPSEARCH_TIMEOUT_MS);
        when(mMockFlags.getAppSearchReadTimeout()).thenReturn(APPSEARCH_TIMEOUT_MS);
    }

    @Test
    @MockStatic(AppSearchConsentDao.class)
    public void testGetConsent() {
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                AppSearchConsentDao.readConsentData(
                                        /* searchSession= */ any(ListenableFuture.class),
                                        /* executor= */ any(),
                                        /* userId= */ any(),
                                        eq(API_TYPE),
                                        any()));

        boolean result = AppSearchConsentWorker.getInstance().getConsent(API_TYPE);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                AppSearchConsentDao.readConsentData(
                                        /* searchSession= */ any(ListenableFuture.class),
                                        /* executor= */ any(),
                                        /* userId= */ any(),
                                        eq(API_TYPE),
                                        any()));

        boolean result2 = AppSearchConsentWorker.getInstance().getConsent(API_TYPE);
        assertThat(result2).isTrue();
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void testSetConsent_failure() {
        initFailureResponse();

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> AppSearchConsentWorker.getInstance().setConsent(API_TYPE, CONSENTED));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testSetConsent_failure_timeout() {
        initTimeoutResponse();

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> AppSearchConsentWorker.getInstance().setConsent(API_TYPE, CONSENTED));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        assertThat(e.getCause()).isNotNull();
        assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @MockStatic(UserHandle.class)
    public void testSetConsent() {
        initSuccessResponse();

        // Verify that no exception is thrown.
        AppSearchConsentWorker.getInstance().setConsent(API_TYPE, CONSENTED);
    }

    @Test
    @SpyStatic(UserHandle.class)
    public void testGetUserIdentifierFromBinderCallingUid() {
        UserHandle mockUserHandle = Mockito.mock(UserHandle.class);

        Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                .thenReturn(mockUserHandle);
        Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);

        String result =
                AppSearchConsentWorker.getInstance().getUserIdentifierFromBinderCallingUid();
        assertThat(result).isEqualTo("" + UID);
    }

    @Test
    @SpyStatic(AdServicesCommon.class)
    public void testGetAdServicesPackageName_null() {
        Context context = Mockito.mock(Context.class);
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);

        Mockito.when(context.getPackageManager()).thenReturn(mockPackageManager);
        Mockito.when(AdServicesCommon.resolveAdServicesService(any(), any())).thenReturn(null);

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> AppSearchConsentWorker.getAdServicesPackageName(context));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    public void testGetAdServicesPackageName() {
        Context context = Mockito.mock(Context.class);
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);

        // When the resolveInfo returns AdServices package name, that is returned.
        Mockito.when(context.getPackageManager()).thenReturn(mockPackageManager);

        ServiceInfo serviceInfo1 = new ServiceInfo();
        serviceInfo1.packageName = ADSERVICES_PACKAGE_NAME;
        ResolveInfo resolveInfo1 = new ResolveInfo();
        resolveInfo1.serviceInfo = serviceInfo1;

        ServiceInfo serviceInfo2 = new ServiceInfo();
        serviceInfo2.packageName = ADEXTSERVICES_PACKAGE_NAME;
        ResolveInfo resolveInfo2 = new ResolveInfo();
        resolveInfo2.serviceInfo = serviceInfo2;
        Mockito.when(mockPackageManager.queryIntentServices(any(), anyInt()))
                .thenReturn(List.of(resolveInfo1, resolveInfo2));

        assertThat(AppSearchConsentWorker.getAdServicesPackageName(context))
                .isEqualTo(ADSERVICES_PACKAGE_NAME);

        // When the resolveInfo returns AdExtServices package name, the AdServices package name
        // is returned.
        Mockito.when(mockPackageManager.queryIntentServices(any(), anyInt()))
                .thenReturn(List.of(resolveInfo2));

        assertThat(AppSearchConsentWorker.getAdServicesPackageName(context))
                .isEqualTo(ADSERVICES_PACKAGE_NAME);
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testGetAppsWithConsent_nullOrEmpty() {
        // Null dao is returned.
        ExtendedMockito.doReturn(null)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEmpty();

        // Dao is returned, but list is null.
        AppSearchAppConsentDao mockDao = Mockito.mock(AppSearchAppConsentDao.class);
        ExtendedMockito.doReturn(mockDao)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), any(), any()));

        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEmpty();
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testGetAppsWithConsent() {
        // Null dao is returned.
        ExtendedMockito.doReturn(null)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEmpty();

        // Dao is returned, but list is null.
        AppSearchAppConsentDao mockDao = Mockito.mock(AppSearchAppConsentDao.class);
        List<String> apps = ImmutableList.of(TEST);

        when(mockDao.getApps()).thenReturn(apps);
        ExtendedMockito.doReturn(mockDao)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), any(), any()));

        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
        assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEqualTo(apps);
    }

    @Test
    @SpyStatic(AppSearchDao.class)
    public void testClearAppsWithConsent_failure() {
        Exception exception = new IllegalStateException("test exception");
        ExtendedMockito.doThrow(exception)
                .when(() -> AppSearchDao.deleteData(any(), any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> appSearchConsentWorker.clearAppsWithConsent(TEST));
        assertThat(e).isSameInstanceAs(exception);
    }

    @Test
    @SpyStatic(AppSearchDao.class)
    public void testClearAppsWithConsent() {
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        ExtendedMockito.doReturn(result)
                .when(() -> AppSearchDao.deleteData(any(), any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        // No exceptions are thrown.
        appSearchConsentWorker.clearAppsWithConsent(TEST);
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testAddAppWithConsent_null() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;

        ExtendedMockito.doReturn(null)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();

        // No exceptions are thrown.
        assertThat(appSearchConsentWorker.addAppWithConsent(consentType, TEST)).isTrue();
        ExtendedMockito.verify(
                () -> AppSearchAppConsentDao.getRowId(any(), eq(consentType)), atLeastOnce());
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testAddAppWithConsent_failure() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);

        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), eq(consentType), any()));
        when(dao.getApps()).thenReturn(List.of());
        when(dao.writeData(any(), any(), any())).thenThrow(new IllegalStateException("test", null));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.addAppWithConsent(consentType, TEST)).isFalse();
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testAddAppWithConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);

        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), eq(consentType), any()));
        when(dao.getApps()).thenReturn(List.of());
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();

        // No exceptions are thrown.
        assertThat(appSearchConsentWorker.addAppWithConsent(consentType, TEST)).isTrue();
        verify(dao, atLeastOnce()).getApps();
        verify(dao, atLeastOnce()).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testRemoveAppWithConsent_null() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;

        ExtendedMockito.doReturn(null)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        // No exceptions are thrown.
        appSearchConsentWorker.removeAppWithConsent(consentType, TEST);
        ExtendedMockito.verify(
                () -> AppSearchAppConsentDao.getRowId(any(), eq(consentType)), never());
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testRemoveAppWithConsent_failure() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);
        Exception exception = new IllegalStateException("test");

        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), eq(consentType), any()));
        when(dao.getApps()).thenReturn(List.of(TEST));
        when(dao.writeData(any(), any(), any())).thenThrow(exception);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> appSearchConsentWorker.removeAppWithConsent(consentType, TEST));
        assertThat(e).isSameInstanceAs(exception);
    }

    @Test
    @SpyStatic(AppSearchAppConsentDao.class)
    public void testRemoveAppWithConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);

        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchAppConsentDao.readConsentData(
                                        any(), any(), any(), eq(consentType), any()));

        when(dao.getApps()).thenReturn(List.of(TEST));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        // No exceptions are thrown.
        appSearchConsentWorker.removeAppWithConsent(consentType, TEST);
        verify(dao, atLeastOnce()).getApps();
        verify(dao, atLeastOnce()).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(AppSearchNotificationDao.class)
    public void testWasNotificationDisplayed() {
        runNotificationDisplayedTest(true);
    }

    @Test
    @SpyStatic(AppSearchNotificationDao.class)
    public void testNotificationNotDisplayed() {
        runNotificationDisplayedTest(false);
    }

    private void runNotificationDisplayedTest(boolean displayed) {
        ExtendedMockito.doReturn(displayed)
                .when(
                        () ->
                                AppSearchNotificationDao.wasNotificationDisplayed(
                                        any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.wasNotificationDisplayed()).isEqualTo(displayed);
    }

    @Test
    @SpyStatic(AppSearchNotificationDao.class)
    public void testWasGaUxNotificationDisplayed() {
        runGaUxNotificationDisplayedTest(true);
    }

    @Test
    @SpyStatic(AppSearchNotificationDao.class)
    public void testGaUxNotificationNotDisplayed() {
        runGaUxNotificationDisplayedTest(false);
    }

    private void runGaUxNotificationDisplayedTest(boolean displayed) {
        ExtendedMockito.doReturn(displayed)
                .when(
                        () ->
                                AppSearchNotificationDao.wasGaUxNotificationDisplayed(
                                        any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.wasGaUxNotificationDisplayed()).isEqualTo(displayed);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void testRecordNotificationDisplayed_failure() {
        runRecordNotificationDisplayedTestFailure(/* isBetaUx= */ true);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void testRecordGaUxNotificationDisplayed_failure() {
        runRecordNotificationDisplayedTestFailure(/* isBetaUx= */ false);
    }

    private void runRecordNotificationDisplayedTestFailure(boolean isBetaUx) {
        initFailureResponse();
        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        isBetaUx
                                ? () -> worker.recordNotificationDisplayed(true)
                                : () -> worker.recordGaUxNotificationDisplayed(true));

        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordNotificationDisplayed_failure_timeout() {
        runRecordNotificationDisplayedTestFailureTimeout(/* isBetaUx= */ true);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordGaUxNotificationDisplayed_failure_timeout() {
        runRecordNotificationDisplayedTestFailureTimeout(/* isBetaUx= */ false);
    }

    private void runRecordNotificationDisplayedTestFailureTimeout(boolean isBetaUx) {
        initTimeoutResponse();
        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();

        RuntimeException e;
        if (isBetaUx) {
            e =
                    assertThrows(
                            RuntimeException.class, () -> worker.recordNotificationDisplayed(true));
        } else {
            e =
                    assertThrows(
                            RuntimeException.class,
                            () -> worker.recordGaUxNotificationDisplayed(true));
        }

        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        assertThat(e.getCause()).isNotNull();
        assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    @MockStatic(AppSearchNotificationDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordNotificationDisplayed() {
        runRecordNotificationDisplayed(/* isBetaUx= */ true);
    }

    @Test
    @MockStatic(AppSearchNotificationDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordGaUxNotificationDisplayed() {
        runRecordNotificationDisplayed(/* isBetaUx= */ false);
    }

    private void runRecordNotificationDisplayed(boolean isBetaUx) {
        initSuccessResponse();
        when(AppSearchNotificationDao.getRowId(eq("" + UID))).thenReturn("" + UID);
        // Verify that no exception is thrown.
        if (isBetaUx) {
            when(AppSearchNotificationDao.wasGaUxNotificationDisplayed(any(), any(), any(), any()))
                    .thenReturn(false);
            AppSearchConsentWorker.getInstance().recordNotificationDisplayed(true);
        } else {
            when(AppSearchNotificationDao.wasNotificationDisplayed(any(), any(), any(), any()))
                    .thenReturn(false);
            AppSearchConsentWorker.getInstance().recordGaUxNotificationDisplayed(true);
        }
    }

    @Test
    @SpyStatic(AppSearchInteractionsDao.class)
    public void testGetPrivacySandboxFeature() {
        PrivacySandboxFeatureType feature = PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT;

        ExtendedMockito.doReturn(feature)
                .when(
                        () ->
                                AppSearchInteractionsDao.getPrivacySandboxFeatureType(
                                        any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.getPrivacySandboxFeature()).isEqualTo(feature);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void testSetCurrentPrivacySandboxFeature_failure() {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                worker.setCurrentPrivacySandboxFeature(
                                        PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testSetCurrentPrivacySandboxFeature_failure_timeout() {
        initTimeoutResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                worker.setCurrentPrivacySandboxFeature(
                                        PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        assertThat(e.getCause()).isNotNull();
        assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testSetCurrentPrivacySandboxFeature() {
        initSuccessResponse();

        // Verify that no exception is thrown.
        PrivacySandboxFeatureType feature = PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT;
        AppSearchConsentWorker.getInstance().setCurrentPrivacySandboxFeature(feature);
    }

    @Test
    @SpyStatic(AppSearchInteractionsDao.class)
    public void testGetInteractions() {
        int umi = ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;

        ExtendedMockito.doReturn(umi)
                .when(
                        () ->
                                AppSearchInteractionsDao.getManualInteractions(
                                        any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.getUserManualInteractionWithConsent()).isEqualTo(umi);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void testRecordUserManualInteractionWithConsent_failure() {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        int interactions = ConsentManager.MANUAL_INTERACTIONS_RECORDED;

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> worker.recordUserManualInteractionWithConsent(interactions));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @MockStatic(AppSearchInteractionsDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordUserManualInteractionWithConsent_failure_timeout() {
        initTimeoutResponse();

        when(AppSearchInteractionsDao.getRowId(any(), any())).thenReturn("" + UID);

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        int interactions = ConsentManager.MANUAL_INTERACTIONS_RECORDED;

        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> worker.recordUserManualInteractionWithConsent(interactions));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        assertThat(e.getCause()).isNotNull();
        assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    @MockStatic(AppSearchInteractionsDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordUserManualInteractionWithConsent() {
        initSuccessResponse();

        when(AppSearchInteractionsDao.getRowId(any(), any())).thenReturn("" + UID);

        // Verify that no exception is thrown.
        int interactions = ConsentManager.MANUAL_INTERACTIONS_RECORDED;
        AppSearchConsentWorker.getInstance().recordUserManualInteractionWithConsent(interactions);
    }

    @Test
    @SpyStatic(AppSearchTopicsConsentDao.class)
    public void testGetBlockedTopics() {
        ExtendedMockito.doReturn(mTopics)
                .when(() -> AppSearchTopicsConsentDao.getBlockedTopics(any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.getBlockedTopics()).isEqualTo(mTopics);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void testRecordBlockedTopic_failure() {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(RuntimeException.class, () -> worker.recordBlockedTopic(TOPIC1));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @MockStatic(AppSearchTopicsConsentDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordBlockedTopic_failure_timeout() {
        initTimeoutResponse();

        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchTopicsConsentDao.getQuery(any()));
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchTopicsConsentDao.readConsentData(any(), any(), any(), any()));

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(RuntimeException.class, () -> worker.recordBlockedTopic(TOPIC1));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        assertThat(e.getCause()).isNotNull();
        assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    @MockStatic(AppSearchTopicsConsentDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordBlockedTopic_new() {
        initSuccessResponse();

        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchTopicsConsentDao.getQuery(any()));
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchTopicsConsentDao.readConsentData(any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.recordBlockedTopic(TOPIC1);
    }

    @Test
    @MockStatic(AppSearchTopicsConsentDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordBlockedTopic() {
        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchTopicsConsentDao.getQuery(any()));
        AppSearchTopicsConsentDao dao = Mockito.mock(AppSearchTopicsConsentDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchTopicsConsentDao.readConsentData(any(), any(), any(), any()));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.recordBlockedTopic(TOPIC1);
        verify(dao).addBlockedTopic(TOPIC1);
        verify(dao).writeData(any(), any(), any());
    }

    @Test
    @MockStatic(AppSearchTopicsConsentDao.class)
    @SpyStatic(PlatformStorage.class)
    public void testRecordUnblockedTopic_failure() {
        AppSearchTopicsConsentDao dao = Mockito.mock(AppSearchTopicsConsentDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchTopicsConsentDao.readConsentData(any(), any(), any(), any()));

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        IllegalStateException exception = new IllegalStateException("test exception");
        when(dao.writeData(any(), any(), any())).thenThrow(exception);

        RuntimeException e =
                assertThrows(RuntimeException.class, () -> worker.recordUnblockedTopic(TOPIC1));
        verify(dao).removeBlockedTopic(TOPIC1);
        verify(dao).writeData(any(), any(), any());
        assertThat(e).isSameInstanceAs(exception);
    }

    @Test
    @MockStatic(AppSearchTopicsConsentDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordUnblockedTopic_new() {
        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchTopicsConsentDao.getQuery(any()));
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchTopicsConsentDao.readConsentData(any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.recordUnblockedTopic(TOPIC1);
    }

    @Test
    @MockStatic(AppSearchTopicsConsentDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testRecordUnblockedTopic() {
        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchTopicsConsentDao.getQuery(any()));
        AppSearchTopicsConsentDao dao = Mockito.mock(AppSearchTopicsConsentDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchTopicsConsentDao.readConsentData(any(), any(), any(), any()));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.recordUnblockedTopic(TOPIC1);
        verify(dao).removeBlockedTopic(TOPIC1);
        verify(dao).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testClearBlockedTopics_failure() {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e = assertThrows(RuntimeException.class, worker::clearBlockedTopics);
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testClearBlockedTopics_failure_timeout() {
        initTimeoutResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e = assertThrows(RuntimeException.class, worker::clearBlockedTopics);
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        assertThat(e.getCause()).isNotNull();
        assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void testClearBlockedTopics() {
        initSuccessResponse();

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        // Verify no exceptions are thrown.
        appSearchConsentWorker.clearBlockedTopics();
    }

    private void initSuccessResponse() {
        AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
        UserHandle mockUserHandle = Mockito.mock(UserHandle.class);
        Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                .thenReturn(mockUserHandle);
        Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);
        ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                .when(() -> PlatformStorage.createSearchSessionAsync(any()));
        verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

        SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
        when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                .thenReturn(Futures.immediateFuture(mockResponse));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(mockSession.putAsync(any())).thenReturn(Futures.immediateFuture(result));

        verify(mockResponse, atMost(1)).getMigrationFailures();
        when(mockResponse.getMigrationFailures()).thenReturn(List.of());
    }

    private void initTimeoutResponse() {
        AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
        UserHandle mockUserHandle = Mockito.mock(UserHandle.class);
        Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                .thenReturn(mockUserHandle);
        Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);
        ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                .when(() -> PlatformStorage.createSearchSessionAsync(any()));
        verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

        SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
        when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                .thenReturn(Futures.immediateFuture(mockResponse));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(mockSession.putAsync(any())).thenReturn(getLongRunningOperation(result));

        verify(mockResponse, atMost(1)).getMigrationFailures();
        when(mockResponse.getMigrationFailures()).thenReturn(List.of());
    }

    private void initFailureResponse() {
        AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
        ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                .when(() -> PlatformStorage.createSearchSessionAsync(any()));
        verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

        SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
        when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                .thenReturn(Futures.immediateFuture(mockResponse));

        AppSearchResult<String> mockResult =
                AppSearchResult.newFailedResult(AppSearchResult.RESULT_INVALID_ARGUMENT, "test");
        SetSchemaResponse.MigrationFailure failure =
                new SetSchemaResponse.MigrationFailure(
                        /* namespace= */ TEST,
                        /* id= */ TEST,
                        /* schemaType= */ TEST,
                        /* appSearchResult= */ mockResult);
        when(mockResponse.getMigrationFailures()).thenReturn(List.of(failure));
    }

    private <T> ListenableFuture<T> getLongRunningOperation(T result) {
        // Wait for a time that's longer than the AppSearch write timeout, then return the result.
        return mExecutorService.submit(
                () -> {
                    TimeUnit.MILLISECONDS.sleep(APPSEARCH_TIMEOUT_MS + 1000);
                    return result;
                });
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isAdIdEnabledTest_trueBit() {
        isAdIdEnabledTest(true);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isAdIdEnabledTest_falseBit() {
        isAdIdEnabledTest(false);
    }

    private void isAdIdEnabledTest(boolean isAdIdEnabled) {
        ExtendedMockito.doReturn(isAdIdEnabled)
                .when(() -> AppSearchUxStatesDao.readIsAdIdEnabled(any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.isAdIdEnabled()).isEqualTo(isAdIdEnabled);
    }

    @Test
    @MockStatic(AppSearchUxStatesDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void setAdIdEnabledTest_success() {
        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchUxStatesDao.getQuery(any()));
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchUxStatesDao.readData(any(), any(), any(), any()));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.setAdIdEnabled(true);
        verify(dao).setAdIdEnabled(anyBoolean());
        verify(dao).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setAdIdEnabledTest_failure_trueBit() {
        setAdIdEnabledTestFailure(true);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setAdIdEnabledTest_failure_falseBit() {
        setAdIdEnabledTestFailure(false);
    }

    private void setAdIdEnabledTestFailure(boolean isAdIdEnabled) {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(RuntimeException.class, () -> worker.setAdIdEnabled(isAdIdEnabled));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isU18AccountTest_trueBit() {
        isU18AccountTest(true);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isU18AccountTest_falseBit() {
        isU18AccountTest(false);
    }

    private void isU18AccountTest(boolean isU18Account) {
        ExtendedMockito.doReturn(isU18Account)
                .when(() -> AppSearchUxStatesDao.readIsU18Account(any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.isU18Account()).isEqualTo(isU18Account);
    }

    @Test
    @MockStatic(AppSearchUxStatesDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void setU18AccountTest_success() {
        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchUxStatesDao.getQuery(any()));
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchUxStatesDao.readData(any(), any(), any(), any()));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.setU18Account(true);
        verify(dao).setU18Account(anyBoolean());
        verify(dao).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setU18AccountTest_trueBit() {
        setU18AccountTest(true);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setU18AccountTest_falseBit() {
        setU18AccountTest(false);
    }

    private void setU18AccountTest(boolean isU18Account) {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(RuntimeException.class, () -> worker.setU18Account(isU18Account));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isEntryPointEnabledTest_trueBit() {
        isEntryPointEnabledTest(true);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isEntryPointEnabledTest_falseBit() {
        isEntryPointEnabledTest(false);
    }

    private void isEntryPointEnabledTest(boolean isEntryPointEnabled) {
        ExtendedMockito.doReturn(isEntryPointEnabled)
                .when(
                        () ->
                                AppSearchUxStatesDao.readIsEntryPointEnabled(
                                        any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.isEntryPointEnabled()).isEqualTo(isEntryPointEnabled);
    }

    @Test
    @MockStatic(AppSearchUxStatesDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void setEntryPointEnabledTest_success() {
        initSuccessResponse();

        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchUxStatesDao.getQuery(any()));
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchUxStatesDao.readData(any(), any(), any(), any()));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.setEntryPointEnabled(true);
        verify(dao).setEntryPointEnabled(anyBoolean());
        verify(dao).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setEntryPointEnabledTest_trueBit() {
        setEntryPointEnabledTest(true);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setEntryPointEnabledTest_falseBit() {
        setEntryPointEnabledTest(false);
    }

    private void setEntryPointEnabledTest(boolean isEntryPointEnabled) {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> worker.setEntryPointEnabled(isEntryPointEnabled));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isAdultAccountTest_trueBit() {
        isAdultAccountTest(true);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void isAdultAccountTest_falseBit() {
        isAdultAccountTest(false);
    }

    private void isAdultAccountTest(boolean isAdultAccount) {
        ExtendedMockito.doReturn(isAdultAccount)
                .when(() -> AppSearchUxStatesDao.readIsAdultAccount(any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.isAdultAccount()).isEqualTo(isAdultAccount);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setAdultAccountTest_trueBit() {
        setAdultAccountTest(true);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setAdultAccountTest_falseBit() {
        setAdultAccountTest(false);
    }

    private void setAdultAccountTest(boolean isAdultAccount) {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(RuntimeException.class, () -> worker.setAdultAccount(isAdultAccount));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @MockStatic(AppSearchUxStatesDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void setAdultAccountTest_success() {
        initSuccessResponse();

        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchUxStatesDao.getQuery(any()));
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchUxStatesDao.readData(any(), any(), any(), any()));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.setAdultAccount(true);
        verify(dao).setAdultAccount(anyBoolean());
        verify(dao).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void wasU18NotificationDisplayedTest_trueBit() {
        wasU18NotificationDisplayedTest(true);
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void wasU18NotificationDisplayedTest_falseBit() {
        wasU18NotificationDisplayedTest(false);
    }

    private void wasU18NotificationDisplayedTest(boolean wasU18NotificationDisplayed) {
        ExtendedMockito.doReturn(wasU18NotificationDisplayed)
                .when(
                        () ->
                                AppSearchUxStatesDao.readIsU18NotificationDisplayed(
                                        any(), any(), any(), any()));

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        assertThat(appSearchConsentWorker.wasU18NotificationDisplayed())
                .isEqualTo(wasU18NotificationDisplayed);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setU18NotificationDisplayedTest_trueBit() {
        setU18NotificationDisplayedTest(true);
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setU18NotificationDisplayedTest_falseBit() {
        setU18NotificationDisplayedTest(false);
    }

    private void setU18NotificationDisplayedTest(boolean wasU18NotificationDisplayed) {
        initFailureResponse();

        AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> worker.setU18NotificationDisplayed(wasU18NotificationDisplayed));
        assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
    }

    @Test
    @MockStatic(AppSearchUxStatesDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void setU18NotificationDisplayedTest_success() {
        initSuccessResponse();

        String query = "" + UID;
        ExtendedMockito.doReturn(query).when(() -> AppSearchUxStatesDao.getQuery(any()));
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        ExtendedMockito.doReturn(dao)
                .when(() -> AppSearchUxStatesDao.readData(any(), any(), any(), any()));
        AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
        when(dao.writeData(any(), any(), any())).thenReturn(result);

        AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
        appSearchConsentWorker.setU18NotificationDisplayed(true);
        verify(dao).setU18NotificationDisplayed(anyBoolean());
        verify(dao).writeData(any(), any(), any());
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void getUxTest_allUxs() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            ExtendedMockito.doReturn(ux)
                    .when(() -> AppSearchUxStatesDao.readUx(any(), any(), any(), any()));
            AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
            assertThat(appSearchConsentWorker.getUx()).isEqualTo(ux);
        }
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setUxTest_allUxsFailure() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            initFailureResponse();

            AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
            RuntimeException e = assertThrows(RuntimeException.class, () -> worker.setUx(ux));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        }
    }

    @Test
    @MockStatic(AppSearchUxStatesDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void setUxTest_allUxsSuccess() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            String query = "" + UID;
            ExtendedMockito.doReturn(query).when(() -> AppSearchUxStatesDao.getQuery(any()));
            AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
            ExtendedMockito.doReturn(dao)
                    .when(() -> AppSearchUxStatesDao.readData(any(), any(), any(), any()));
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            when(dao.writeData(any(), any(), any())).thenReturn(result);
            AppSearchConsentWorker appSearchConsentWorker = AppSearchConsentWorker.getInstance();
            appSearchConsentWorker.setUx(ux);
            verify(dao).setUx(ux.toString());
            verify(dao).writeData(any(), any(), any());
        }
    }

    @Test
    @SpyStatic(AppSearchUxStatesDao.class)
    public void getEnrollmentChannelTest_allUxsAllEnrollmentChannels() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                ExtendedMockito.doReturn(channel)
                        .when(
                                () ->
                                        AppSearchUxStatesDao.readEnrollmentChannel(
                                                any(), any(), any(), any(), any()));

                AppSearchConsentWorker appSearchConsentWorker =
                        AppSearchConsentWorker.getInstance();
                assertThat(appSearchConsentWorker.getEnrollmentChannel(ux)).isEqualTo(channel);
            }
        }
    }

    @Test
    @SpyStatic(PlatformStorage.class)
    public void setEnrollmentChannelTest_allUxsAllEnrollmentChannelsFailure() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                initFailureResponse();

                AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance();
                RuntimeException e =
                        assertThrows(
                                RuntimeException.class,
                                () -> worker.setEnrollmentChannel(ux, channel));
                assertThat(e.getMessage())
                        .isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
            }
        }
    }

    @Test
    @MockStatic(AppSearchUxStatesDao.class)
    @SpyStatic(PlatformStorage.class)
    @SpyStatic(UserHandle.class)
    public void setEnrollmentChannelTest_allUxsAllEnrollmentChannelsSuccess() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                String query = "" + UID;
                ExtendedMockito.doReturn(query).when(() -> AppSearchUxStatesDao.getQuery(any()));
                AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
                ExtendedMockito.doReturn(dao)
                        .when(() -> AppSearchUxStatesDao.readData(any(), any(), any(), any()));
                AppSearchBatchResult<String, Void> result =
                        Mockito.mock(AppSearchBatchResult.class);
                when(dao.writeData(any(), any(), any())).thenReturn(result);

                AppSearchConsentWorker appSearchConsentWorker =
                        AppSearchConsentWorker.getInstance();
                appSearchConsentWorker.setEnrollmentChannel(ux, channel);
                verify(dao).setEnrollmentChannel(channel.toString());
                verify(dao).writeData(any(), any(), any());
            }
        }
    }
}
