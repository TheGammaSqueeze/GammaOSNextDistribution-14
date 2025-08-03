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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdServicesJobServiceLogger;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStopJobLogged;
import static com.android.adservices.service.common.ConsentNotificationJobService.ADID_ENABLE_STATUS;
import static com.android.adservices.service.common.ConsentNotificationJobService.MILLISECONDS_IN_THE_DAY;
import static com.android.adservices.service.common.ConsentNotificationJobService.RE_CONSENT_STATUS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;

/** Unit test for {@link com.android.adservices.service.common.ConsentNotificationJobService}. */
public class ConsentNotificationJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Spy
    private ConsentNotificationJobService mConsentNotificationJobService =
            new ConsentNotificationJobService();

    @Mock Context mContext;
    @Mock ConsentManager mConsentManager;
    @Mock JobParameters mMockJobParameters;
    @Mock PackageManager mPackageManager;
    @Mock AdServicesSyncUtil mAdservicesSyncUtil;
    @Mock PersistableBundle mPersistableBundle;
    @Mock JobScheduler mMockJobScheduler;
    @Mock Flags mFlags;
    @Mock private SharedPreferences mSharedPreferences;
    @Mock private SharedPreferences.Editor mEditor;
    @Mock UxStatesManager mUxStatesManager;
    private AdServicesJobServiceLogger mSpyLogger;
    private MockitoSession mStaticMockSession = null;

    /** Initialize static spies. */
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(AdServicesSyncUtil.class)
                        .spyStatic(ConsentNotificationJobService.class)
                        .spyStatic(AdServicesJobServiceLogger.class)
                        .spyStatic(UxStatesManager.class)
                        .mockStatic(ServiceCompatUtils.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();

        mSpyLogger = mockAdServicesJobServiceLogger(CONTEXT, mFlags);
        ExtendedMockito.doReturn(mUxStatesManager).when(() -> UxStatesManager.getInstance());
        ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance());

        mConsentNotificationJobService.setConsentManager(mConsentManager);
        mConsentNotificationJobService.setUxStatesManager(mUxStatesManager);
    }

    /** Clean up static spies. */
    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    /** Test successful onStart method execution. */
    @Test
    public void testOnStartJobAsyncUtilExecute_withoutLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mFlags, /* overrideValue= */ true);

        testOnStartJobAsyncUtilExecute();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStartJobAsyncUtilExecute_withLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        testOnStartJobAsyncUtilExecute();

        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    /** Test GA UX disabled and reconsent, onStart method will not execute the job */
    @Test
    public void testOnStartJobAsyncUtilExecute_Reconsent_GaUxDisabled()
            throws InterruptedException {
        mockServiceCompatUtilDisableJob(false);
        doReturn(mFlags).when(FlagsFactory::getFlags);
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(false);
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(false);
        ConsentManager consentManager = mock(ConsentManager.class);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        mConsentNotificationJobService.setConsentManager(consentManager);

        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
        when(mPersistableBundle.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);

        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mAdservicesSyncUtil, times(0)).execute(any(Context.class), any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    /** Test reconsent false, onStart method will execute the job */
    @Test
    public void testOnStartJobAsyncUtilExecute_ReconsentFalse() throws InterruptedException {
        mockServiceCompatUtilDisableJob(false);
        doReturn(mFlags).when(FlagsFactory::getFlags);
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(false);
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(true);
        ConsentManager consentManager = mock(ConsentManager.class);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        mConsentNotificationJobService.setConsentManager(consentManager);
        doReturn(consentManager).when(() -> ConsentManager.getInstance());
        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
        when(mPersistableBundle.getBoolean(eq(ADID_ENABLE_STATUS), anyBoolean())).thenReturn(true);
        when(mPersistableBundle.getBoolean(eq(RE_CONSENT_STATUS), anyBoolean())).thenReturn(false);
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);

        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mAdservicesSyncUtil, times(1)).execute(any(Context.class), any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    @Test
    public void testOnStartJobShouldDisableJobTrue_withoutLogging() {
        mockGetFlags(mFlags);
        mockBackgroundJobsLoggingKillSwitch(mFlags, /* overrideValue= */ true);

        testOnStartJobShouldDisableJobTrue();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStartJobShouldDisableJobTrue_withLoggingEnabled() {
        mockGetFlags(mFlags);
        mockBackgroundJobsLoggingKillSwitch(mFlags, /* overrideValue= */ false);

        testOnStartJobShouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mSpyLogger);
    }

    /** Test successful onStop method execution. */
    @Test
    public void testOnStopJob_withoutLogging() {
        mockGetFlags(mFlags);
        mockBackgroundJobsLoggingKillSwitch(mFlags, /* overrideValue= */ true);

        testOnStopJob();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStopJob_withLogging() throws InterruptedException {
        mockGetFlags(mFlags);
        mockBackgroundJobsLoggingKillSwitch(mFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        testOnStopJob();

        verifyOnStopJobLogged(mSpyLogger, callback);
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * before approved interval.
     */
    @Test
    public void testDelaysWhenCalledBeforeIntervalBegins() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"));
        calendar.setTimeInMillis(0);

        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        assertThat(initialDelay)
                .isEqualTo(FlagsFactory.getFlagsForTest().getConsentNotificationIntervalBeginMs());
        assertThat(deadline)
                .isEqualTo(FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs());
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * within approved interval.
     */
    @Test
    public void testDelaysWhenCalledWithinTheInterval() {
        long expectedDelay = /* 100 seconds */ 100000;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"));
        calendar.setTimeInMillis(
                FlagsFactory.getFlagsForTest().getConsentNotificationIntervalBeginMs()
                        + expectedDelay);

        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        assertThat(initialDelay).isEqualTo(0L);
        assertThat(deadline)
                .isEqualTo(
                        FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs()
                                - (FlagsFactory.getFlagsForTest()
                                                .getConsentNotificationIntervalBeginMs()
                                        + expectedDelay));
    }

    /**
     * Test calculation of initial delay and maximum deadline for task which is being scheduled
     * after approved interval (the same day).
     */
    @Test
    public void testDelaysWhenCalledAfterTheInterval() {
        long delay = /* 100 seconds */ 100000;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/UTC"));
        calendar.setTimeInMillis(
                FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs() + delay);

        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);

        long midnight =
                MILLISECONDS_IN_THE_DAY
                        - (FlagsFactory.getFlagsForTest().getConsentNotificationIntervalEndMs()
                                + delay);

        assertThat(initialDelay)
                .isEqualTo(
                        midnight
                                + FlagsFactory.getFlagsForTest()
                                        .getConsentNotificationIntervalBeginMs());
        assertThat(deadline)
                .isEqualTo(
                        midnight
                                + FlagsFactory.getFlagsForTest()
                                        .getConsentNotificationIntervalEndMs());
    }

    @Test
    public void testDelaysWhenDebugModeOn() {
        doReturn(mFlags).when(FlagsFactory::getFlags);
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(true);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(0);
        long initialDelay = ConsentNotificationJobService.calculateInitialDelay(calendar);
        long deadline = ConsentNotificationJobService.calculateDeadline(calendar);
        assertThat(initialDelay).isEqualTo(0L);
        assertThat(deadline).isEqualTo(0L);
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() {
        final ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mMockJobScheduler);
        when(mContext.getPackageName()).thenReturn("testSchedule_jobInfoIsPersisted");
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mSharedPreferences.edit()).thenReturn(mEditor);
        when(mEditor.putInt(anyString(), anyInt())).thenReturn(mEditor);
        Mockito.doNothing().when(mEditor).apply();

        ConsentNotificationJobService.schedule(mContext, true, false);

        Mockito.verify(mMockJobScheduler, times(1)).schedule(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue().isPersisted()).isTrue();
    }

    /** Test that when the OTA strings feature is on, no notifications are sent immediately. */
    @Test
    public void testOnStartJob_otaStringsFeatureEnabled() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaStringsFeature(/* enabled */ true);
        mockJobFinished();

        verify(mAdservicesSyncUtil, times(0)).getInstance();
    }

    /** Test that when the OTA strings feature is disabled, the notification is sent immediately. */
    @Test
    public void testOnStartJob_otaStringsFeatureDisabled() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaStringsFeature(/* enabled */ false);
        mockJobFinished();

        verify(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
    }

    /** Test that the notification will be sent immediately when OTA deadline passed. */
    @Test
    public void testOnStartJob_otaStringsDeadlinePassed() throws Exception {
        mockAdIdEnabled();
        mockEuDevice();
        mockGaUxEnabled();
        mockConsentDebugMode(/* enabled */ false);

        mockOtaStringsFeature(/* enabled */ true);
        when(mFlags.getUiOtaStringsDownloadDeadline()).thenReturn(Long.valueOf(0));
        mockJobFinished();

        verify(mAdservicesSyncUtil, times(1)).execute(any(Context.class), any(Boolean.class));
    }

    private void testOnStartJobAsyncUtilExecute() throws InterruptedException {
        mockServiceCompatUtilDisableJob(false);
        doReturn(mFlags).when(FlagsFactory::getFlags);
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(false);
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(true);
        ConsentManager consentManager = mock(ConsentManager.class);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        doReturn(Boolean.FALSE).when(consentManager).wasNotificationDisplayed();
        doReturn(Boolean.TRUE).when(consentManager).wasGaUxNotificationDisplayed();
        doNothing().when(consentManager).recordNotificationDisplayed(true);
        doNothing().when(consentManager).recordGaUxNotificationDisplayed(true);
        mConsentNotificationJobService.setConsentManager(consentManager);
        doReturn(consentManager).when(() -> ConsentManager.getInstance());
        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
        when(mPersistableBundle.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);
        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        jobFinishedCountDown.await();

        verify(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
    }

    private void testOnStartJobShouldDisableJobTrue() {
        mockServiceCompatUtilDisableJob(true);
        doReturn(mMockJobScheduler)
                .when(mConsentNotificationJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);

        assertThat(mConsentNotificationJobService.onStartJob(mMockJobParameters)).isFalse();

        verify(mConsentNotificationJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(mConsentManager);
    }

    private void testOnStopJob() {
        // Verify nothing throws
        mConsentNotificationJobService.onStopJob(mMockJobParameters);
    }

    private void mockOtaStringsFeature(boolean enabled) {
        doReturn(mFlags).when(FlagsFactory::getFlags);
        when(mFlags.getUiOtaStringsFeatureEnabled()).thenReturn(enabled);
    }

    private void mockConsentDebugMode(boolean enabled) {
        doReturn(mFlags).when(FlagsFactory::getFlags);
        when(mFlags.getConsentNotificationDebugMode()).thenReturn(enabled);
    }

    private void mockJobFinished() throws Exception {
        mockServiceCompatUtilDisableJob(false);
        doReturn(mAdservicesSyncUtil).when(AdServicesSyncUtil::getInstance);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mConsentNotificationJobService)
                .jobFinished(mMockJobParameters, false);

        mConsentNotificationJobService.onStartJob(mMockJobParameters);
        doNothing().when(mAdservicesSyncUtil).execute(any(Context.class), any(Boolean.class));
        jobFinishedCountDown.await();
    }

    private void mockAdIdEnabled() {
        when(mPersistableBundle.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        when(mMockJobParameters.getExtras()).thenReturn(mPersistableBundle);
    }

    private void mockEuDevice() {
        doReturn(mPackageManager).when(mConsentNotificationJobService).getPackageManager();
        Mockito.doReturn(true).when(mUxStatesManager).isEeaDevice();
    }

    private void mockServiceCompatUtilDisableJob(boolean returnValue) {
        doReturn(returnValue)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
    }

    private void mockGaUxEnabled() {
        when(mFlags.getGaUxFeatureEnabled()).thenReturn(true);
    }
}
