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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST;
import static android.adservices.common.AdServicesStatusUtils.FAILURE_REASON_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_DISABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.ADSERVICES_ENTRY_POINT_STATUS_ENABLE;
import static com.android.adservices.data.common.AdservicesEntryPointConstant.KEY_ADSERVICES_ENTRY_POINT_STATUS;
import static com.android.adservices.mockito.MockitoExpectations.mockLogApiCallStats;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.GA_UX;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesStates;
import android.adservices.common.CallerMetadata;
import android.adservices.common.ConsentStatus;
import android.adservices.common.EnableAdServicesResponse;
import android.adservices.common.GetAdServicesCommonStatesParams;
import android.adservices.common.IAdServicesCommonCallback;
import android.adservices.common.IAdServicesCommonStatesCallback;
import android.adservices.common.IEnableAdServicesCallback;
import android.adservices.common.IUpdateAdIdCallback;
import android.adservices.common.IsAdServicesEnabledResult;
import android.adservices.common.UpdateAdIdRequest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.IntFailureSyncCallback;
import com.android.adservices.common.NoFailureSyncCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adid.AdIdWorker;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.ui.UxEngine;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;

public class AdServicesCommonServiceImplTest {
    private static final String UNUSED_AD_ID = "unused_ad_id";

    private AdServicesCommonServiceImpl mCommonService;
    private CountDownLatch mGetCommonCallbackLatch;
    @Mock private Flags mFlags;
    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private UxEngine mUxEngine;
    @Mock private UxStatesManager mUxStatesManager;
    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mEditor;
    @Mock private ConsentManager mConsentManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private AdIdWorker mMockAdIdWorker;
    @Mock private Clock mClock;
    @Captor ArgumentCaptor<String> mStringArgumentCaptor;
    @Captor ArgumentCaptor<Integer> mIntegerArgumentCaptor;
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;
    private static final String TEST_APP_PACKAGE_NAME = "com.android.adservices.servicecoretest";
    private static final String INVALID_PACKAGE_NAME = "com.do_not_exists";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private MockitoSession mStaticMockSession = null;
    private NoFailureSyncCallback<ApiCallStats> mLogApiCallStatsCallback;

    private final AdServicesLogger mAdServicesLogger =
            Mockito.spy(AdServicesLoggerImpl.getInstance());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentNotificationJobService.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .spyStatic(PermissionHelper.class)
                        .spyStatic(UxStatesManager.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mContext,
                        mFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        mLogApiCallStatsCallback = mockLogApiCallStats(mAdServicesLogger);
        doReturn(true).when(mFlags).getAdServicesEnabled();
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        ExtendedMockito.doReturn(mUxStatesManager).when(() -> UxStatesManager.getInstance());
        doNothing()
                .when(
                        () ->
                                ConsentNotificationJobService.schedule(
                                        any(Context.class),
                                        any(Boolean.class),
                                        any(Boolean.class)));

        doReturn(mSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putInt(anyString(), anyInt());
        doReturn(true).when(mEditor).commit();
        doReturn(true).when(mSharedPreferences).contains(anyString());

        ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance());

        // Set device to EU
        doReturn(Flags.UI_EEA_COUNTRIES).when(mFlags).getUiEeaCountries();
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mPackageManager).hasSystemFeature(anyString());
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);
        doReturn(true).when(mUxStatesManager).isEnrolledUser(mContext);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    // For the old entry point logic, we only check the UX flag and user enrollment is irrelevant.
    @Test
    public void isAdServiceEnabledTest_userNotEnrolledEntryPointLogicV1() throws Exception {
        doReturn(false).when(mUxStatesManager).isEnrolledUser(mContext);
        doReturn(false).when(mFlags).getEnableAdServicesSystemApi();
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mContext,
                        mFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);

        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
    }

    // For the new entry point logic, only enrolled user that has gone through UxEngine
    // can see the entry point.
    @Test
    public void isAdServiceEnabledTest_userNotEnrolledEntryPointLogicV2() throws Exception {
        doReturn(false).when(mUxStatesManager).isEnrolledUser(mContext);
        doReturn(true).when(mFlags).getEnableAdServicesSystemApi();
        doReturn(GA_UX).when(mConsentManager).getUx();

        mCommonService =
                new AdServicesCommonServiceImpl(
                        mContext,
                        mFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isFalse();
    }

    @Test
    public void getAdserviceStatusTest() throws Exception {
        doReturn(false).when(mFlags).getGaUxFeatureEnabled();
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mContext,
                        mFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        // Calling get adservice status, init set the flag to true, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();

        // Set the flag to false
        doReturn(false).when(mFlags).getAdServicesEnabled();

        // Calling again, expect to false
        getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isFalse();
    }

    @Test
    public void getAdserviceStatusWithCheckActivityTest() throws Exception {
        doReturn(true).when(mFlags).isBackCompatActivityFeatureEnabled();

        doReturn(false).when(mFlags).getGaUxFeatureEnabled();
        mCommonService =
                new AdServicesCommonServiceImpl(
                        mContext,
                        mFlags,
                        mUxEngine,
                        mUxStatesManager,
                        mMockAdIdWorker,
                        mAdServicesLogger,
                        mClock);
        ExtendedMockito.doReturn(true)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        // Calling get adservice status, set the activity to enabled, expect to return true
        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();

        // Set the activity to disabled
        ExtendedMockito.doReturn(false)
                .when(() -> PackageManagerCompatUtils.isAdServicesActivityEnabled(any()));

        // Calling again, expect to false
        getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isFalse();
    }

    @Test
    public void isAdservicesEnabledReconsentTest_happycase() throws Exception {
        // Happy case
        // Calling get adservice status, init set the flag to true, expect to return true
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(1));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_gaUxFeatureDisabled() throws Exception {
        // GA UX feature disable, should not execute scheduler
        doReturn(false).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_deviceNotEu() throws Exception {
        // GA UX feature enable, set device to not EU, not execute scheduler
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_gaUxNotificationDisplayed() throws Exception {
        // GA UX feature enabled, device set to EU, GA UX notification set to displayed
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn("pl").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_sharedPreferenceNotContain() throws Exception {
        // GA UX notification set to not displayed, sharedpreference set to not contains
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mSharedPreferences).contains(anyString());
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void isAdservicesEnabledReconsentTest_userConsentRevoked() throws Exception {
        // Sharedpreference set to contains, user consent set to revoke
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(false)).when(mConsentManager).getConsent();

        IsAdServicesEnabledResult getAdservicesStatusResult = getStatusResult();
        assertThat(getAdservicesStatusResult.getAdServicesEnabled()).isTrue();
        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), anyBoolean(), anyBoolean()),
                times(0));
    }

    @Test
    public void setAdservicesEntryPointStatusTest() throws Exception {
        // Not reconsent, as not ROW devices, Not first Consent, as notification displayed is true
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), any(Boolean.class)),
                times(0));
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));

        Mockito.verify(mEditor)
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_ENABLE);

        // Not executed, as entry point enabled status is false
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        mCommonService.setAdServicesEnabled(false, true);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), any(Boolean.class)),
                times(0));
        Mockito.verify(mEditor, times(2))
                .putInt(mStringArgumentCaptor.capture(), mIntegerArgumentCaptor.capture());
        assertThat(mStringArgumentCaptor.getValue()).isEqualTo(KEY_ADSERVICES_ENTRY_POINT_STATUS);
        assertThat(mIntegerArgumentCaptor.getValue())
                .isEqualTo(ADSERVICES_ENTRY_POINT_STATUS_DISABLE);
    }

    @Test
    public void setAdservicesEnabledConsentTest_happycase() throws Exception {
        // Set device to ROW
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(1));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentGaUxFeatureDisabled()
            throws InterruptedException {
        // GA UX feature disable
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(false).when(mFlags).getGaUxFeatureEnabled();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentEUDevice() throws Exception {
        // enable GA UX feature, but EU device
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentGaUxNotificationDisplayed()
            throws InterruptedException {
        // ROW device, GA UX notification displayed
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentNotificationNotDisplayed()
            throws InterruptedException {
        // GA UX notification not displayed, notification not displayed, this also trigger
        // first consent case, but we verify here for reconsentStatus as true
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_ReconsentUserConsentRevoked()
            throws InterruptedException {
        // Notification displayed, user consent is revoked
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(false)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(true)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_FirstConsentHappycase()
            throws InterruptedException {
        // First Consent happy case, should be executed
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(false).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(1));
    }

    @Test
    public void setAdservicesEnabledConsentTest_FirstConsentGaUxNotificationDisplayed()
            throws InterruptedException {
        // GA UX notification was displayed
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(0));
    }

    @Test
    public void setAdservicesEnabledConsentTest_FirstConsentNotificationDisplayed()
            throws InterruptedException {
        // Notification was displayed
        doReturn(true).when(mFlags).getGaUxFeatureEnabled();
        doReturn(false).when(mConsentManager).wasGaUxNotificationDisplayed();
        doReturn(true).when(mConsentManager).wasNotificationDisplayed();
        doReturn(AdServicesApiConsent.getConsent(true)).when(mConsentManager).getConsent();
        mCommonService.setAdServicesEnabled(true, false);
        Thread.sleep(1000);

        verify(
                () ->
                        ConsentNotificationJobService.schedule(
                                any(Context.class), eq(false), eq(false)),
                times(0));
    }

    @Test
    public void enableAdServicesTest_unauthorizedCaller() throws Exception {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(false)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        callback.assertFailed(STATUS_UNAUTHORIZED);

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mFlags, never()).getEnableAdServicesSystemApi();
        verify(mUxEngine, never()).start(any());
    }

    @Test
    @FlakyTest(bugId = 299686058)
    public void enableAdServicesTest_apiDisabled() throws InterruptedException {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(false).when(mFlags).getEnableAdServicesSystemApi();

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        assertThat(callback.assertSuccess().isApiEnabled()).isFalse();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mFlags).getEnableAdServicesSystemApi();
        verify(mUxEngine, never()).start(any());
    }

    @Test
    public void enableAdServicesTest_engineStarted() throws InterruptedException {
        SyncIEnableAdServicesCallback callback =
                new SyncIEnableAdServicesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        doReturn(true).when(mFlags).getEnableAdServicesSystemApi();

        mCommonService.enableAdServices(new AdServicesStates.Builder().build(), callback);
        EnableAdServicesResponse response = callback.assertSuccess();
        assertThat(response.isApiEnabled()).isTrue();
        assertThat(response.isSuccess()).isTrue();

        ExtendedMockito.verify(() -> PermissionHelper.hasModifyAdServicesStatePermission(any()));
        verify(mFlags).getEnableAdServicesSystemApi();
        verify(mUxEngine).start(any());
    }

    @Test
    public void testUpdateAdIdChange() throws InterruptedException {
        mGetCommonCallbackLatch = new CountDownLatch(1);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        doReturn(true).when(mFlags).getAdIdCacheEnabled();

        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(UNUSED_AD_ID).build();
        doNothing().when(mMockAdIdWorker).updateAdId(request);

        SyncIUpdateAdIdCallback callback = callUpdateAdIdCache(request);
        callback.assertResultReceived();

        ExtendedMockito.verify(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        verify(mFlags).getAdIdCacheEnabled();
        verify(mMockAdIdWorker).updateAdId(request);
    }

    @Test
    public void testUpdateAdIdChange_unauthorizedCaller() throws InterruptedException {
        mGetCommonCallbackLatch = new CountDownLatch(1);
        ExtendedMockito.doReturn(false)
                .when(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        doReturn(true).when(mFlags).getAdIdCacheEnabled();

        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(UNUSED_AD_ID).build();
        doNothing().when(mMockAdIdWorker).updateAdId(request);

        SyncIUpdateAdIdCallback callback = callUpdateAdIdCache(request);
        callback.assertFailed(STATUS_UNAUTHORIZED);

        ExtendedMockito.verify(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        verify(mFlags).getAdIdCacheEnabled();
        verify(mMockAdIdWorker, never()).updateAdId(request);
    }

    @Test
    public void testUpdateAdIdChange_disabled() throws InterruptedException {
        mGetCommonCallbackLatch = new CountDownLatch(1);
        ExtendedMockito.doReturn(true)
                .when(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        doReturn(false).when(mFlags).getAdIdCacheEnabled();

        UpdateAdIdRequest request = new UpdateAdIdRequest.Builder(UNUSED_AD_ID).build();
        doNothing().when(mMockAdIdWorker).updateAdId(request);

        SyncIUpdateAdIdCallback callback = callUpdateAdIdCache(request);
        callback.assertFailed(STATUS_KILLSWITCH_ENABLED);

        ExtendedMockito.verify(() -> PermissionHelper.hasUpdateAdIdCachePermission(any()));
        verify(mFlags).getAdIdCacheEnabled();
        verify(mMockAdIdWorker, never()).updateAdId(request);
    }

    @Test
    public void testGetAdservicesCommonStates_unauthorizedUser() throws Exception {
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        callback.assertFailed(STATUS_UNAUTHORIZED);

        ExtendedMockito.verify(
                () -> PermissionHelper.hasAccessAdServicesCommonStatePermission(any(), any()));
        verify(mFlags, never()).isGetAdServicesCommonStatesApiEnabled();
        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_UNAUTHORIZED);
        assertThat(apiCallStats.getFailureReason())
                .isEqualTo(FAILURE_REASON_MANIFEST_ADSERVICES_CONFIG_NO_PERMISSION);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(350);
    }

    @Test
    public void testGetAdservicesCommonStates_notAllowed() throws Exception {
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        NoFailureSyncCallback<ApiCallStats> logApiCallStatsCallback =
                mockLogApiCallStats(mAdServicesLogger);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        doReturn(INVALID_PACKAGE_NAME).when(mFlags).getAdServicesCommonStatesAllowList();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        callback.assertFailed(STATUS_CALLER_NOT_ALLOWED);

        ExtendedMockito.verify(
                () -> PermissionHelper.hasAccessAdServicesCommonStatePermission(any(), any()));
        verify(mFlags, never()).isGetAdServicesCommonStatesApiEnabled();
        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_CALLER_NOT_ALLOWED);
        assertThat(apiCallStats.getFailureReason())
                .isEqualTo(FAILURE_REASON_PACKAGE_NOT_IN_ALLOWLIST);
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
    }

    @Test
    public void testGetAdservicesCommonStates_getCommonStatus() throws Exception {
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        doReturn(true).when(mFlags).isGetAdServicesCommonStatesApiEnabled();
        doReturn("com.android.adservices.servicecoretest")
                .when(mFlags)
                .getAdServicesCommonStatesAllowList();
        ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance());
        doReturn(true).when(mConsentManager).isPasMeasurementConsentGiven();
        doReturn(false).when(mConsentManager).isPasFledgeConsentGiven();
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);

        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        AdServicesCommonStatesResponse response = callback.assertSuccess();
        assertThat(response.getAdServicesCommonStates().getMeasurementState())
                .isEqualTo(ConsentStatus.GIVEN);
        assertThat(response.getAdServicesCommonStates().getPaState())
                .isEqualTo(ConsentStatus.REVOKED);

        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCallStats.getFailureReason()).isEqualTo(FAILURE_REASON_UNSET);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(350);
    }

    @Test
    public void testGetAdservicesCommonStates_NotEnabled() throws Exception {
        SyncIAdServicesCommonStatesCallback callback =
                new SyncIAdServicesCommonStatesCallback(BINDER_CONNECTION_TIMEOUT_MS);
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAccessAdServicesCommonStatePermission(
                                        any(), any()));
        doReturn(false).when(mFlags).isGetAdServicesCommonStatesApiEnabled();
        doReturn("com.android.adservices.servicecoretest")
                .when(mFlags)
                .getAdServicesCommonStatesAllowList();
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);

        GetAdServicesCommonStatesParams params =
                new GetAdServicesCommonStatesParams.Builder(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME)
                        .build();
        CallerMetadata metadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(0).build();
        mCommonService.getAdServicesCommonStates(params, metadata, callback);
        AdServicesCommonStatesResponse response = callback.assertSuccess();
        assertThat(response.getAdServicesCommonStates().getMeasurementState())
                .isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
        assertThat(response.getAdServicesCommonStates().getPaState())
                .isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
        ApiCallStats apiCallStats = mLogApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(apiCallStats.getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(STATUS_SUCCESS);
        assertThat(apiCallStats.getFailureReason()).isEqualTo(FAILURE_REASON_UNSET);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(350);
    }

    private IsAdServicesEnabledResult getStatusResult() throws Exception {
        SyncIAdServicesCommonCallback callback =
                new SyncIAdServicesCommonCallback(BINDER_CONNECTION_TIMEOUT_MS);

        mCommonService.isAdServicesEnabled(callback);
        return callback.assertResultReceived();
    }

    private SyncIUpdateAdIdCallback callUpdateAdIdCache(UpdateAdIdRequest request) {
        SyncIUpdateAdIdCallback callback =
                new SyncIUpdateAdIdCallback(BINDER_CONNECTION_TIMEOUT_MS);
        mCommonService.updateAdIdCache(request, callback);

        return callback;
    }

    private static final class SyncIAdServicesCommonCallback
            extends IntFailureSyncCallback<IsAdServicesEnabledResult>
            implements IAdServicesCommonCallback {
        private SyncIAdServicesCommonCallback(int timeoutMs) {
            super(timeoutMs);
        }
    }

    private static final class SyncIUpdateAdIdCallback extends IntFailureSyncCallback<String>
            implements IUpdateAdIdCallback {
        private SyncIUpdateAdIdCallback(int timeoutMs) {
            super(timeoutMs);
        }
    }

    private static final class SyncIEnableAdServicesCallback
            extends IntFailureSyncCallback<EnableAdServicesResponse>
            implements IEnableAdServicesCallback {
        private SyncIEnableAdServicesCallback(int timeoutMs) {
            super(timeoutMs);
        }
    }

    private static final class SyncIAdServicesCommonStatesCallback
            extends IntFailureSyncCallback<AdServicesCommonStatesResponse>
            implements IAdServicesCommonStatesCallback {
        private SyncIAdServicesCommonStatesCallback(int timeoutMs) {
            super(timeoutMs);
        }
    }
}
