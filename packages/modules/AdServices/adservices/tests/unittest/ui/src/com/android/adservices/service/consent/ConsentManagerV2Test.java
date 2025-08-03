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

package com.android.adservices.service.consent;

import static com.android.adservices.service.consent.ConsentConstants.CONSENT_KEY;
import static com.android.adservices.service.consent.ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE;
import static com.android.adservices.service.consent.ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED;
import static com.android.adservices.service.consent.ConsentConstants.NOTIFICATION_DISPLAYED_ONCE;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_CONSENT;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED;
import static com.android.adservices.service.consent.ConsentManagerV2.MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManagerV2.UNKNOWN;
import static com.android.adservices.service.consent.ConsentManagerV2.resetSharedPreference;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.spe.AdServicesJobInfo.COBALT_LOGGING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.CONSENT_NOTIFICATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.ENCRYPTION_KEY_PERIODIC_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MAINTENANCE_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_EXPIRED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_UNINSTALLED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.argThat;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.atLeastOnce;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.adservices.AdServicesManager;
import android.app.adservices.IAdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.filters.SmallTest;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.cobalt.CobaltJobService;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.download.MddJobService;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.appsearch.AppSearchConsentStorageManager;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.extdata.AdServicesExtDataStorageServiceManager;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.DeleteUninstalledJobService;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ConsentMigrationStats;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochJobService;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.adservices.service.ui.data.UxStatesDao;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.MockedVoidMethod;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(AggregateFallbackReportingJobService.class)
@SpyStatic(AggregateReportingJobService.class)
@SpyStatic(AsyncRegistrationQueueJobService.class)
@SpyStatic(AsyncRegistrationFallbackJobService.class)
@SpyStatic(AttributionJobService.class)
@SpyStatic(AttributionFallbackJobService.class)
@SpyStatic(BackgroundJobsManager.class)
@SpyStatic(ConsentManagerV2.class)
@SpyStatic(DeleteExpiredJobService.class)
@SpyStatic(DeleteUninstalledJobService.class)
@SpyStatic(DeviceRegionProvider.class)
@SpyStatic(EpochJobService.class)
@SpyStatic(ErrorLogUtil.class)
@SpyStatic(EventFallbackReportingJobService.class)
@SpyStatic(EventReportingJobService.class)
@SpyStatic(DebugReportingFallbackJobService.class)
@SpyStatic(VerboseDebugReportingFallbackJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MaintenanceJobService.class)
@SpyStatic(MddJobService.class)
@SpyStatic(EncryptionKeyJobService.class)
@SpyStatic(CobaltJobService.class)
@SpyStatic(UiStatsLogger.class)
@SpyStatic(StatsdAdServicesLogger.class)
@MockStatic(PackageManagerCompatUtils.class)
@MockStatic(SdkLevel.class)
@SmallTest
public final class ConsentManagerV2Test extends AdServicesExtendedMockitoTestCase {
    public static final int UX_TYPE_COUNT = 5;
    public static final int ENROLLMENT_CHANNEL_COUNT = 22;
    private BooleanFileDatastore mDatastore;
    private BooleanFileDatastore mConsentDatastore;
    private ConsentManagerV2 mConsentManager;
    private AppConsentDao mAppConsentDaoSpy;
    private EnrollmentDao mEnrollmentDaoSpy;
    private AdServicesStorageManager mAdServicesStorageManager;

    private AdServicesManager mAdServicesManager;

    private AppConsentStorageManager mAppConsentStorageManager;
    private AppConsentForRStorageManager mAppConsentForRStorageManager;

    @Mock private AdServicesExtDataStorageServiceManager mAdServicesExtDataManager;

    @Mock private AdServicesLoggerImpl mAdServicesLoggerImplMock;

    @Mock private TopicsWorker mTopicsWorkerMock;
    @Mock private MeasurementImpl mMeasurementImplMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock private UiStatsLogger mUiStatsLoggerMock;

    @Mock private AppUpdateManager mAppUpdateManagerMock;
    @Mock private CacheManager mCacheManagerMock;
    @Mock private BlockedTopicsManager mBlockedTopicsManagerMock;
    @Mock private EpochManager mMockEpochManager;

    @Mock private PackageManager mPackageManager;
    @Mock private Flags mMockFlags;
    @Mock private JobScheduler mJobSchedulerMock;
    @Mock private IAdServicesManager mMockIAdServicesManager;
    @Mock private AppSearchConsentStorageManager mAppSearchConsentManagerMock;

    @Mock private UserProfileIdManager mUserProfileIdManagerMock;
    @Mock private UxStatesDao mUxStatesDaoMock;
    @Mock private StatsdAdServicesLogger mStatsdAdServicesLoggerMock;

    @Before
    public void setup() throws IOException {
        doReturn(mStatsdAdServicesLoggerMock).when(StatsdAdServicesLogger::getInstance);
        mDatastore =
                spy(
                        new BooleanFileDatastore(
                                mSpyContext,
                                AppConsentDao.DATASTORE_NAME,
                                AppConsentDao.DATASTORE_VERSION));
        // For each file, we should ensure there is only one instance of datastore that is able to
        // access it. (Refer to BooleanFileDatastore.class)
        mConsentDatastore = spy(ConsentManagerV2.createAndInitializeDataStore(mSpyContext));
        mAppConsentDaoSpy = spy(new AppConsentDao(mDatastore, mSpyContext.getPackageManager()));
        mEnrollmentDaoSpy =
                spy(
                        new EnrollmentDao(
                                mSpyContext, DbTestUtil.getSharedDbHelperForTest(), mMockFlags));
        mAppConsentStorageManager =
                spy(
                        new AppConsentStorageManager(
                                mConsentDatastore, mAppConsentDaoSpy, mUxStatesDaoMock));

        mAppConsentForRStorageManager =
                spy(
                        new AppConsentForRStorageManager(
                                mConsentDatastore,
                                mAppConsentDaoSpy,
                                mUxStatesDaoMock,
                                mAdServicesExtDataManager));
        mAdServicesManager = new AdServicesManager(mMockIAdServicesManager);
        doReturn(mAdServicesManager).when(mSpyContext).getSystemService(AdServicesManager.class);

        doReturn(mAdServicesLoggerImplMock).when(AdServicesLoggerImpl::getInstance);
        mAdServicesStorageManager =
                spy(new AdServicesStorageManager(mAdServicesManager, mPackageManager));
        // Default to use PPAPI consent to test migration-irrelevant logic.
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(true).when(mMockFlags).getFledgeAdSelectionFilteringEnabled();
        doReturn(true).when(mMockFlags).getAdservicesConsentMigrationLoggingEnabled();
        doReturn(true).when(mMockFlags).getEnrollmentEnableLimitedLogging();
        doReturn(mAdServicesLoggerImplMock).when(AdServicesLoggerImpl::getInstance);
        doReturn(true).when(() -> EpochJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        doReturn(true)
                .when(() -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        doReturn(true).when(() -> MddJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        doReturn(true)
                .when(
                        () ->
                                EncryptionKeyJobService.scheduleIfNeeded(
                                        any(Context.class), eq(false)));
        doNothing().when(() -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                AggregateFallbackReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing().when(() -> AttributionFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing()
                .when(() -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> EpochJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(() -> EventFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> DeleteUninstalledJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> MaintenanceJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(() -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> CobaltJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> UiStatsLogger.logOptInSelected());
        doNothing().when(() -> UiStatsLogger.logOptOutSelected());
        doNothing().when(() -> UiStatsLogger.logOptInSelected(any()));
        doNothing().when(() -> UiStatsLogger.logOptOutSelected(any()));
        // The consent_source_of_truth=APPSEARCH_ONLY value is overridden on T+, so ignore level.
        doReturn(false).when(() -> SdkLevel.isAtLeastT());
    }

    @After
    public void teardown() throws IOException {
        mDatastore.clear();
        mConsentDatastore.clear();
    }

    @Test
    public void testConsentIsGivenAfterEnabling_PpApiOnly() throws RemoteException, IOException {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsGivenAfterEnabling_SystemServerOnly()
            throws RemoteException, IOException {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsGivenAfterEnabling_PPAPIAndSystemServer()
            throws RemoteException, IOException {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsGivenAfterEnabling_AppSearchOnly() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verify(mAppSearchConsentManagerMock, atLeastOnce()).getConsent(AdServicesApiType.ALL_API);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentManager_LazyEnable() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);

        doReturn(true).when(mMockFlags).getConsentManagerLazyEnableMode();
        spyConsentManager.enable(mSpyContext);
        spyConsentManager.enable(mSpyContext);
        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager, times(0)).setConsentToSourceOfTruth(isGiven);
        verifyResetApiCalled(spyConsentManager, 0);
    }

    @Test
    public void testConsentManager_LazyDisabled() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        doReturn(false).when(mMockFlags).getConsentManagerLazyEnableMode();
        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager).setConsentToSourceOfTruth(isGiven);
        verifyResetApiCalled(spyConsentManager, 1);
    }

    @Test
    public void testConsentManagerPreApi_LazyEnable() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.MEASUREMENT);

        doReturn(true).when(mMockFlags).getConsentManagerLazyEnableMode();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        spyConsentManager.setPerApiConsentToSourceOfTruth(true, AdServicesApiType.ALL_API);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();
        verify(spyConsentManager, never()).resetByApi(eq(AdServicesApiType.MEASUREMENTS));
    }

    @Test
    public void testConsentManagerPreApi_LazyDisabled() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        doReturn(PrivacySandboxUxCollection.UNSUPPORTED_UX).when(mAdServicesStorageManager).getUx();
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.MEASUREMENT);
        doReturn(false).when(mMockFlags).getConsentManagerLazyEnableMode();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();

        verify(spyConsentManager).resetByApi(eq(AdServicesApiType.MEASUREMENTS));
    }

    private static void verifyResetApiCalled(
            ConsentManagerV2 spyConsentManager, int wantedNumOfInvocations) throws IOException {
        verify(spyConsentManager, times(wantedNumOfInvocations)).resetTopicsAndBlockedTopics();
        verify(spyConsentManager, times(wantedNumOfInvocations)).clearAllAppConsentData();
        verify(spyConsentManager, times(wantedNumOfInvocations)).resetMeasurement();
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_PpApiOnly() throws RemoteException, IOException {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_SystemServerOnly()
            throws RemoteException, IOException {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_PpApiAndSystemServer()
            throws RemoteException, IOException {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_AppSearchOnly()
            throws RemoteException, IOException {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();
        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verify(mAppSearchConsentManagerMock, atLeastOnce()).getConsent(AdServicesApiType.ALL_API);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_notSupportedFlag() throws RemoteException {
        boolean isGiven = true;
        int invalidConsentSourceOfTruth = 5;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getSpiedConsentManagerForMigrationTesting(
                                /* isGiven */ isGiven, invalidConsentSourceOfTruth));
    }

    @Test
    public void testJobsAreScheduledAfterEnablingKillSwitchOff() {
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        mockMeasurementEnabled(true);
        doReturn(false).when(mMockFlags).getMddBackgroundTaskKillSwitch();
        doReturn(true).when(mMockFlags).getCobaltLoggingEnabled();

        mConsentManager.enable(mSpyContext);

        verify(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        verify(() -> EpochJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(() -> MddJobService.scheduleIfNeeded(any(Context.class), eq(false)), times(3));
        verify(
                () -> EncryptionKeyJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                times(2));
        verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                times(2));
        verify(() -> AggregateReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(
                () ->
                        AggregateFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> AttributionFallbackJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(
                () ->
                        AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(
                () ->
                        VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(
                () ->
                        DebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> AttributionJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(() -> EventReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(
                () ->
                        EventFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> DeleteExpiredJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(() -> DeleteUninstalledJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(
                () ->
                        AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> CobaltJobService.scheduleIfNeeded(any(Context.class), eq(false)), times(2));
    }

    @Test
    public void testJobsAreNotScheduledAfterEnablingKillSwitchOn() {
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();
        doReturn(true).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        mockMeasurementEnabled(false);
        doReturn(true).when(mMockFlags).getMddBackgroundTaskKillSwitch();
        doReturn(false).when(mMockFlags).getCobaltLoggingEnabled();

        mConsentManager.enable(mSpyContext);

        verify(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        verify(() -> EpochJobService.scheduleIfNeeded(any(Context.class), eq(false)), never());
        verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(() -> MddJobService.scheduleIfNeeded(any(Context.class), eq(false)), never());
        verify(
                () -> EncryptionKeyJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        AggregateFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () -> AttributionFallbackJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        DebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () -> AttributionJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        EventFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () -> DeleteUninstalledJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(() -> CobaltJobService.scheduleIfNeeded(any(Context.class), eq(false)), never());
    }

    @Test
    public void testJobsAreUnscheduledAfterDisabling() {
        doReturn(mJobSchedulerMock).when(mSpyContext).getSystemService(JobScheduler.class);
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        verify(mJobSchedulerMock).cancel(MAINTENANCE_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(TOPICS_EPOCH_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_EVENT_MAIN_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_DELETE_EXPIRED_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_DELETE_UNINSTALLED_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ATTRIBUTION_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock)
                .cancel(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(FLEDGE_BACKGROUND_FETCH_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(PERIODIC_SIGNALS_ENCODING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(CONSENT_NOTIFICATION_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(ENCRYPTION_KEY_PERIODIC_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(COBALT_LOGGING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId());

        verifyNoMoreInteractions(mJobSchedulerMock);
    }

    @Test
    public void testDataIsResetAfterConsentIsRevoked() throws IOException {
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mEnrollmentDaoSpy).deleteAll();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verify(mUserProfileIdManagerMock).deleteId();
    }

    @Test
    public void testDataIsResetAfterConsentIsRevokedFilteringDisabled() throws IOException {
        doReturn(false).when(mMockFlags).getFledgeAdSelectionFilteringEnabled();
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mEnrollmentDaoSpy).deleteAll();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
        verifyZeroInteractions(mAppInstallDaoMock, mFrequencyCapDaoMock);
        verify(mUserProfileIdManagerMock).deleteId();
    }

    @Test
    public void testDataIsResetAfterConsentIsGiven() throws IOException {
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    public void testDataIsResetAfterConsentIsGivenFilteringDisabled() throws IOException {
        doReturn(false).when(mMockFlags).getFledgeAdSelectionFilteringEnabled();
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
        verifyZeroInteractions(mAppInstallDaoMock, mFrequencyCapDaoMock);
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_systemServerOnly()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);

        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_appSearchOnly()
            throws Exception {
        runTestIsFledgeConsentRevokedForAppWithFullApiConsentAppSearchOnly(true);
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled_appSearchOnly()
            throws Exception {
        runTestIsFledgeConsentRevokedForAppWithFullApiConsentAppSearchOnly(false);
    }

    private void runTestIsFledgeConsentRevokedForAppWithFullApiConsentAppSearchOnly(
            boolean isGaUxEnabled) throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(isGaUxEnabled);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(AdServicesApiConsent.GIVEN);

        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        String app1 = AppConsentDaoFixture.APP10_PACKAGE_NAME;
        String app2 = AppConsentDaoFixture.APP20_PACKAGE_NAME;
        String app3 = AppConsentDaoFixture.APP30_PACKAGE_NAME;
        mockGetPackageUid(app1, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(app2, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(app3, AppConsentDaoFixture.APP30_UID);

        when(mAppSearchConsentManagerMock.isConsentRevokedForApp(app1)).thenReturn(false);
        when(mAppSearchConsentManagerMock.isConsentRevokedForApp(app2)).thenReturn(true);
        when(mAppSearchConsentManagerMock.isConsentRevokedForApp(app3)).thenReturn(false);

        assertFalse(mConsentManager.isFledgeConsentRevokedForApp(app1));
        assertTrue(mConsentManager.isFledgeConsentRevokedForApp(app2));
        assertFalse(mConsentManager.isFledgeConsentRevokedForApp(app3));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);

        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxEnabled_ppApiOnly()
            throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptOutSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxEnabled_sysServer()
            throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxEnabled_bothSrc()
            throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_ppApiOnly()
            throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_systemServerOnly()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    // AppSearch test for isFledgeConsentRevokedForAppAfterSettingFledgeUse with GA UX enabled.
    @Test
    public void testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_as()
            throws Exception {
        runTestIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentAppSearch(true);
    }

    private void runTestIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentAppSearch(
            boolean isGaUxEnabled) throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(isGaUxEnabled);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(AdServicesApiConsent.GIVEN);
        mConsentManager.enable(mSpyContext);
        verify(() -> UiStatsLogger.logOptInSelected());

        String app1 = AppConsentDaoFixture.APP10_PACKAGE_NAME;
        String app2 = AppConsentDaoFixture.APP20_PACKAGE_NAME;
        String app3 = AppConsentDaoFixture.APP30_PACKAGE_NAME;
        mockGetPackageUid(app1, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(app2, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(app3, AppConsentDaoFixture.APP30_UID);

        when(mAppSearchConsentManagerMock.setConsentForAppIfNew(app1, false)).thenReturn(false);
        when(mAppSearchConsentManagerMock.setConsentForAppIfNew(app2, false)).thenReturn(true);
        when(mAppSearchConsentManagerMock.setConsentForAppIfNew(app3, false)).thenReturn(false);

        assertFalse(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app1));
        assertTrue(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app2));
        assertFalse(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app3));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_ppApi()
                    throws IOException, PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_sysSer()
                    throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_UID,
                        false);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_UID,
                        false);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_both()
                    throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_UID,
                        false);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_UID,
                        false);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxEnabled_ppApi()
                    throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);

        mConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxEnabled_sysSer()
                    throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxEnabled_both()
                    throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_ppApiOnly()
            throws PackageManager.NameNotFoundException {
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_systemServerOnly()
            throws PackageManager.NameNotFoundException, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_systemServerOnly() throws RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        List<String> applicationsInstalledNames =
                applicationsInstalled.stream()
                        .map(applicationInfo -> applicationInfo.packageName)
                        .collect(Collectors.toList());
        mockInstalledApplications(applicationsInstalled);

        doReturn(applicationsInstalledNames)
                .when(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        doReturn(List.of())
                .when(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        verify(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        verify(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        verify(mAdServicesStorageManager, times(2)).getInstalledPackages();
        verifyNoMoreInteractions(mAppConsentDaoSpy);

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiAndSystemServer() throws RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        List<String> applicationsInstalledNames =
                applicationsInstalled.stream()
                        .map(applicationInfo -> applicationInfo.packageName)
                        .collect(Collectors.toList());
        mockInstalledApplications(applicationsInstalled);

        doReturn(applicationsInstalledNames)
                .when(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        doReturn(List.of())
                .when(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        verify(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        verify(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        verify(mAdServicesStorageManager, times(2)).getInstalledPackages();
        verifyNoMoreInteractions(mAppConsentDaoSpy);

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_appSearchOnly() {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);

        ImmutableList<String> consentedAppsList =
                ImmutableList.of(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        ImmutableList<String> revokedAppsList =
                ImmutableList.of(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);

        doReturn(consentedAppsList).when(mAppSearchConsentManagerMock).getKnownAppsWithConsent();
        doReturn(revokedAppsList).when(mAppSearchConsentManagerMock).getAppsWithRevokedConsent();

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        verify(mAppSearchConsentManagerMock).getKnownAppsWithConsent();
        verify(mAppSearchConsentManagerMock).getAppsWithRevokedConsent();

        // Correct apps have received consent.
        assertThat(knownAppsWithConsent).hasSize(1);
        assertThat(knownAppsWithConsent.get(0).getPackageName())
                .isEqualTo(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        assertThat(appsWithRevokedConsent).hasSize(2);
        assertThat(
                        appsWithRevokedConsent.stream()
                                .map(app -> app.getPackageName())
                                .collect(Collectors.toList()))
                .containsAtLeast(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
    }

    @Test
    public void testGetKnownAppsWithConsent_ppapiAndAdExtDataServiceOnly() throws IOException {
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_ADEXT_SERVICE);
        assertThat(mConsentManager.getKnownAppsWithConsent()).isEqualTo(ImmutableList.of());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevoked_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        // mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mSpyContext);
        mConsentManager.setConsentToSourceOfTruth(true);
        assertThat(mConsentManager.getConsent().isGiven()).isTrue();

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        // revoke consent for first app
        mConsentManager.revokeConsentForApp(app);
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        App appWithRevokedConsent = appsWithRevokedConsent.get(0);
        assertThat(appWithRevokedConsent.getPackageName()).isEqualTo(app.getPackageName());

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(app.getPackageName());
        verify(mAppInstallDaoMock).deleteByPackageName(app.getPackageName());
        verify(mFrequencyCapDaoMock).deleteHistogramDataBySourceApp(app.getPackageName());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // revoke consent for first app
        mConsentManager.revokeConsentForApp(app);
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        App appWithRevokedConsent = appsWithRevokedConsent.get(0);
        assertThat(appWithRevokedConsent.getPackageName()).isEqualTo(app.getPackageName());

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(app.getPackageName());
        verify(mAppInstallDaoMock).deleteByPackageName(app.getPackageName());
        verify(mFrequencyCapDaoMock).deleteHistogramDataBySourceApp(app.getPackageName());

        // restore consent for first app
        mConsentManager.restoreConsentForApp(app);
        knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testSetConsentForApp_ppApiOnly() throws Exception {
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
    }

    @Test
    public void testSetConsentForApp_systemServerOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        true);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
    }

    @Test
    public void testSetConsentForApp_ppApiAndSystemServer() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        true);
        assertEquals(Boolean.TRUE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        assertEquals(Boolean.FALSE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void testSetConsentForApp_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        mConsentManager.revokeConsentForApp(app);
        verify(mAppSearchConsentManagerMock).setConsentForApp(app.getPackageName(), true);

        mConsentManager.restoreConsentForApp(app);
        verify(mAppSearchConsentManagerMock).setConsentForApp(app.getPackageName(), false);
    }

    @Test
    public void testRevokeConsentForApp_ppapiAndAdExtDataServiceOnly() {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_ADEXT_SERVICE);
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        assertThrows(RuntimeException.class, () -> mConsentManager.revokeConsentForApp(app));
    }

    @Test
    public void testRestoreConsentForApp_ppapiAndAdExtDataServiceOnly() {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_ADEXT_SERVICE);
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        assertThrows(RuntimeException.class, () -> mConsentManager.restoreConsentForApp(app));
    }

    @Test
    public void clearConsentForUninstalledApp_ppApiOnly()
            throws PackageManager.NameNotFoundException, IOException {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(Boolean.FALSE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertNull(mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void clearConsentForUninstalledApp_systemServerOnly() throws RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        verify(mMockIAdServicesManager)
                .clearConsentForUninstalledApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
    }

    @Test
    public void clearConsentForUninstalledApp_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, IOException, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(Boolean.FALSE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertNull(mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        verify(mMockIAdServicesManager)
                .clearConsentForUninstalledApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
    }

    @Test
    public void clearConsentForUninstalledApp_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        String packageName = AppConsentDaoFixture.APP10_PACKAGE_NAME;
        mockGetPackageUid(packageName, AppConsentDaoFixture.APP10_UID);

        mConsentManager.clearConsentForUninstalledApp(packageName, AppConsentDaoFixture.APP10_UID);
        verify(mAppSearchConsentManagerMock)
                .clearConsentForUninstalledApp(packageName, AppConsentDaoFixture.APP10_UID);
    }

    @Test
    public void clearConsentForUninstalledApp_ppapiAndAdExtDataServiceOnly() {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_ADEXT_SERVICE);
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        String packageName = AppConsentDaoFixture.APP10_PACKAGE_NAME;

        assertThrows(
                RuntimeException.class,
                () ->
                        mConsentManager.clearConsentForUninstalledApp(
                                packageName, AppConsentDaoFixture.APP10_UID));
    }

    @Test
    public void clearConsentForUninstalledAppWithoutUid_ppApiOnly() throws IOException {
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mConsentManager.clearConsentForUninstalledApp(AppConsentDaoFixture.APP20_PACKAGE_NAME);

        assertEquals(true, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastore.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertEquals(false, mDatastore.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        verify(mAppConsentDaoSpy).clearConsentForUninstalledApp(anyString());
    }

    @Test
    public void clearConsentForUninstalledAppWithoutUid_ppApiOnly_validatesInput() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mConsentManager.clearConsentForUninstalledApp(null);
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mConsentManager.clearConsentForUninstalledApp("");
                });
    }

    @Test
    public void testGetKnownTopicsWithConsent() {
        long taxonomyVersion = 1L;
        long modelVersion = 1L;
        Topic topic1 = Topic.create(1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(2, taxonomyVersion, modelVersion);
        ImmutableList<Topic> expectedKnownTopicsWithConsent = ImmutableList.of(topic1, topic2);
        doReturn(expectedKnownTopicsWithConsent)
                .when(mTopicsWorkerMock)
                .getKnownTopicsWithConsent();

        ImmutableList<Topic> knownTopicsWithConsent = mConsentManager.getKnownTopicsWithConsent();

        assertThat(knownTopicsWithConsent)
                .containsExactlyElementsIn(expectedKnownTopicsWithConsent);
    }

    @Test
    public void testGetTopicsWithRevokedConsent() {
        long taxonomyVersion = 1L;
        long modelVersion = 1L;
        Topic topic1 = Topic.create(1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(2, taxonomyVersion, modelVersion);
        ImmutableList<Topic> expectedTopicsWithRevokedConsent = ImmutableList.of(topic1, topic2);
        doReturn(expectedTopicsWithRevokedConsent)
                .when(mTopicsWorkerMock)
                .getTopicsWithRevokedConsent();

        ImmutableList<Topic> topicsWithRevokedConsent =
                mConsentManager.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent)
                .containsExactlyElementsIn(expectedTopicsWithRevokedConsent);
    }

    @Test
    public void testResetAllAppConsentAndAppData_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);

        mConsentManager.clearAllAppConsentData();

        // All app consent data was deleted
        knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsent).isEmpty();
        assertThat(appsWithRevokedConsent).isEmpty();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock, times(2)).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock, times(2)).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock, times(2)).deleteAllHistogramData();
    }

    @Test
    public void testResetAllAppConsentAndAppData_systemServerOnly()
            throws IOException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);

        mConsentManager.clearAllAppConsentData();

        verify(mMockIAdServicesManager).clearAllAppConsentData();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
    }

    @Test
    public void testResetAllAppConsentAndAppData_ppApiAndSystemServer()
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        List<App> knownAppsWithConsent =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        List<App> appsWithRevokedConsent =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);

        mConsentManager.clearAllAppConsentData();

        // All app consent data was deleted
        knownAppsWithConsent =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        appsWithRevokedConsent =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsent).isEmpty();
        assertThat(appsWithRevokedConsent).isEmpty();

        verify(mMockIAdServicesManager, times(2)).clearAllAppConsentData();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock, times(2)).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock, times(2)).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock, times(2)).deleteAllHistogramData();
    }

    @Test
    public void testResetAllAppConsentAndAppData_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);

        mConsentManager.clearKnownAppsWithConsent();
        verify(mAppSearchConsentManagerMock).clearKnownAppsWithConsent();
    }

    @Test
    public void testResetAllAppConsentAndAppData_ppapiAndAdExtDataServiceOnly() {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_ADEXT_SERVICE);
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> mConsentManager.clearAllAppConsentData());
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        ImmutableList<App> knownAppsWithConsentBeforeReset =
                mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsentBeforeReset =
                mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsentBeforeReset).hasSize(2);
        assertThat(appsWithRevokedConsentBeforeReset).hasSize(1);
        mConsentManager.clearKnownAppsWithConsent();

        // Known apps with consent were cleared; revoked apps were not deleted
        ImmutableList<App> knownAppsWithConsentAfterReset =
                mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsentAfterReset =
                mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsentAfterReset).isEmpty();
        assertThat(appsWithRevokedConsentAfterReset).hasSize(1);
        assertThat(
                        appsWithRevokedConsentAfterReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(
                        appsWithRevokedConsentBeforeReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()));

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock, times(2)).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock, times(2)).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock, times(2)).deleteAllHistogramData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_systemServerOnly()
            throws IOException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        mConsentManager.clearKnownAppsWithConsent();

        verify(mMockIAdServicesManager).clearKnownAppsWithConsent();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_ppApiAndSystemServer()
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        List<App> knownAppsWithConsentBeforeReset =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        List<App> appsWithRevokedConsentBeforeReset =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsentBeforeReset).hasSize(2);
        assertThat(appsWithRevokedConsentBeforeReset).hasSize(1);
        mConsentManager.clearKnownAppsWithConsent();

        // Known apps with consent were cleared; revoked apps were not deleted
        List<App> knownAppsWithConsentAfterReset =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        List<App> appsWithRevokedConsentAfterReset =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsentAfterReset).isEmpty();
        assertThat(appsWithRevokedConsentAfterReset).hasSize(1);
        assertThat(
                        appsWithRevokedConsentAfterReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(
                        appsWithRevokedConsentBeforeReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()));

        verify(mMockIAdServicesManager).clearKnownAppsWithConsent();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);

        mConsentManager.clearKnownAppsWithConsent();
        verify(mAppSearchConsentManagerMock).clearKnownAppsWithConsent();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_ppapiAndAdExtDataServiceOnly() {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_ADEXT_SERVICE);
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);

        assertThrows(
                IllegalStateException.class, () -> mConsentManager.clearKnownAppsWithConsent());
    }

    @Test
    public void testNotificationDisplayedRecorded_PpApiOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasNotificationDisplayed();

        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasNotificationDisplayed();
        verify(mMockIAdServicesManager, never()).recordNotificationDisplayed(true);
    }

    @Test
    public void testNotificationDisplayedRecorded_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Verify notificationDisplayed is not set in PPAPI
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testNotificationDisplayedRecorded_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasNotificationDisplayed = spyConsentManager.wasNotificationDisplayed();

        assertThat(wasNotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Verify notificationDisplayed is also set in PPAPI
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();
    }

    @Test
    public void testNotificationDisplayedRecorded_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();

        doReturn(true).when(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).wasNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).recordNotificationDisplayed(true);
    }

    @Test
    public void testNotificationDisplayedRecorded_ppapiAndAdExtDataServiceOnly()
            throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_PpApiOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasGaUxNotificationDisplayed();

        spyConsentManager.recordGaUxNotificationDisplayed(true);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager, never()).recordGaUxNotificationDisplayed(true);
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasGaUxNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        spyConsentManager.recordGaUxNotificationDisplayed(true);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);

        // Verify notificationDisplayed is not set in PPAPI
        assertThat(mConsentDatastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_PpApiAndSystemServer()
            throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasGaUxNotificationDisplayed = spyConsentManager.wasGaUxNotificationDisplayed();

        assertThat(wasGaUxNotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasGaUxNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        spyConsentManager.recordGaUxNotificationDisplayed(true);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);

        // Verify notificationDisplayed is also set in PPAPI
        assertThat(mConsentDatastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE)).isTrue();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(false);
        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();

        when(mAppSearchConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(true);
        spyConsentManager.recordGaUxNotificationDisplayed(true);
        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).recordGaUxNotificationDisplayed(true);
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_ppapiAndAdExtDataServiceOnly()
            throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();
    }

    @Test
    public void testNotificationDisplayedRecorded_notSupportedFlag() throws RemoteException {
        int invalidConsentSourceOfTruth = 5;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getSpiedConsentManagerForMigrationTesting(
                                /* isGiven */ false, invalidConsentSourceOfTruth));
    }

    @Test
    public void testClearPpApiConsent() throws IOException {
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isNull();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isNull();

        // Verify this should only happen once
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();
        // Consent is not cleared again
        ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        // Clear shared preference
        ConsentManagerV2.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
    }

    @Test
    public void testMigratePpApiConsentToSystemService() throws RemoteException, IOException {
        // Disable IPC calls
        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);

        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        ConsentManagerV2.migratePpApiConsentToSystemService(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);

        verify(mMockIAdServicesManager).setConsent(any());
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Verify this should only happen once
        ConsentManagerV2.migratePpApiConsentToSystemService(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);
        verify(mMockIAdServicesManager).setConsent(any());
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Clear shared preference
        ConsentManagerV2.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testMigratePpApiConsentToSystemServiceWithSuccessfulConsentMigrationLogging()
            throws RemoteException, IOException {
        // Disable IPC calls
        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        SharedPreferences sharedPreferences =
                mSpyContext.getSharedPreferences(SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, false);
        editor.putBoolean(SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED, false);
        editor.commit();
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManagerV2.migratePpApiConsentToSystemService(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);

        // Clear shared preference
        ConsentManagerV2.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
        ConsentManagerV2.resetSharedPreference(
                mSpyContext, SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED);
    }

    @Test
    public void testMigratePpApiConsentToSystemServiceWithUnSuccessfulConsentMigrationLogging()
            throws RemoteException, IOException {
        // Disable IPC calls
        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);

        SharedPreferences sharedPreferences = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        doReturn(editor).when(sharedPreferences).edit();
        doReturn(false).when(editor).commit();
        doReturn(sharedPreferences).when(mSpyContext).getSharedPreferences(anyString(), anyInt());

        doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        doNothing().when(mStatsdAdServicesLoggerMock).logConsentMigrationStats(any());
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManagerV2.migratePpApiConsentToSystemService(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);

        doReturn(true).when(editor).commit();
        // Clear shared preference
        ConsentManagerV2.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testMigratePpApiConsentToSystemServiceThrowsException()
            throws RemoteException, IOException {
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        doThrow(RemoteException.class)
                .when(mMockIAdServicesManager)
                .recordNotificationDisplayed(true);

        doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        doNothing().when(mStatsdAdServicesLoggerMock).logConsentMigrationStats(any());
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManagerV2.migratePpApiConsentToSystemService(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(ConsentMigrationStats.MigrationStatus.FAILURE)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);

        // Clear shared preference
        ConsentManagerV2.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_ExtServices() throws RemoteException {
        doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(mSpyContext)
                .getPackageName();

        ConsentManagerV2.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock,
                Flags.PPAPI_AND_SYSTEM_SERVER);

        verify(mSpyContext, never()).getSharedPreferences(anyString(), anyInt());
        verify(mMockIAdServicesManager, never()).setConsent(any());
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeeded_ExtServices() throws Exception {
        doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(mSpyContext)
                .getPackageName();
        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);
        ConsentManagerV2.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);

        verify(mSpyContext, never()).getSharedPreferences(anyString(), anyInt());
        verify(mAppSearchConsentManagerMock, never())
                .migrateConsentDataIfNeeded(any(), any(), any(), any());
        verify(mMockIAdServicesManager, never()).setConsent(any());
        verify(mMockIAdServicesManager, never()).recordNotificationDisplayed(true);
        verify(mMockIAdServicesManager, never()).recordGaUxNotificationDisplayed(true);
        verify(mMockIAdServicesManager, never()).recordDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordAdServicesDeletionOccurred(anyInt());
        verify(mMockIAdServicesManager, never()).recordDefaultAdIdState(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordFledgeDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordMeasurementDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordTopicsDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordUserManualInteractionWithConsent(anyInt());
        verify(mockEditor, never()).putBoolean(any(), anyBoolean());
    }

    @Test
    public void testResetSharedPreference() {
        SharedPreferences sharedPreferences =
                FileCompatUtils.getSharedPreferencesHelper(
                        mSpyContext, SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean(SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);
        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, true);
        editor.commit();

        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false))
                .isTrue();
        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false))
                .isTrue();

        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);

        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ true))
                .isFalse();
        assertThat(sharedPreferences.getBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ true))
                .isFalse();
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_PpApiOnly() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesStorageManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManagerV2.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        verify(
                () ->
                        ConsentManagerV2.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesStorageManager,
                                mStatsdAdServicesLoggerMock),
                never());
        verify(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore), never());
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_SystemServerOnly() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesStorageManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManagerV2.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        verify(
                () ->
                        ConsentManagerV2.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesStorageManager,
                                mStatsdAdServicesLoggerMock));
        verify(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore));
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_PpApiAndSystemServer() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesStorageManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManagerV2.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        verify(
                () ->
                        ConsentManagerV2.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesStorageManager,
                                mStatsdAdServicesLoggerMock));
        verify(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore), never());
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_AppSearchOnly() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManagerV2.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesStorageManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManagerV2.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManagerV2.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        verify(
                () ->
                        ConsentManagerV2.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesStorageManager,
                                mStatsdAdServicesLoggerMock),
                never());
        verify(() -> ConsentManagerV2.clearPpApiConsent(mSpyContext, mConsentDatastore), never());
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeeded_notMigrated() throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenReturn(false);
        BooleanFileDatastore mockDatastore = mock(BooleanFileDatastore.class);

        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);

        ConsentManagerV2.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mockDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);
        verify(mockEditor, never()).putBoolean(any(), anyBoolean());
        verify(mAppSearchConsentManagerMock).migrateConsentDataIfNeeded(any(), any(), any(), any());
        verify(mAppSearchConsentManagerMock, never()).recordNotificationDisplayed(true);
        verify(mAppSearchConsentManagerMock, never()).recordGaUxNotificationDisplayed(true);

        verify(mAppSearchConsentManagerMock, never())
                .recordUserManualInteractionWithConsent(anyInt());
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeeded() throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenReturn(true);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(AdServicesApiConsent.GIVEN);
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);

        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);
        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(MANUAL_INTERACTIONS_RECORDED);
        when(mockEditor.commit()).thenReturn(true);
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManagerV2.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);
        verify(mAppSearchConsentManagerMock).migrateConsentDataIfNeeded(any(), any(), any(), any());

        // Verify interactions data is migrated.
        assertThat(mConsentDatastore.get(ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED))
                .isTrue();
        verify(mAdServicesStorageManager).recordUserManualInteractionWithConsent(anyInt());

        // Verify migration is recorded.
        verify(mockEditor)
                .putBoolean(eq(ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED), eq(true));

        assertThat(mConsentDatastore.get(ConsentConstants.CONSENT_KEY)).isTrue();

        // Verify per API consents data is migrated.
        assertThat(mConsentDatastore.get(AdServicesApiType.TOPICS.toPpApiDatastoreKey())).isTrue();
        assertThat(mConsentDatastore.get(AdServicesApiType.FLEDGE.toPpApiDatastoreKey())).isTrue();
        assertThat(mConsentDatastore.get(AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey()))
                .isTrue();
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeededSharedPrefsEditorUnsuccessful()
            throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenReturn(true);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(AdServicesApiConsent.GIVEN);
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);

        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);
        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(MANUAL_INTERACTIONS_RECORDED);
        when(mockEditor.commit()).thenReturn(false);
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManagerV2.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeededThrowsException() throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenThrow(IOException.class);

        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        doNothing().when(mStatsdAdServicesLoggerMock).logConsentMigrationStats(any());

        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManagerV2.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mAdServicesStorageManager,
                mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(ConsentMigrationStats.MigrationStatus.FAILURE)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();
        Mockito.verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    public void testTopicsProxyCalls() {
        Topic topic = Topic.create(1, 1, 1);
        ArrayList<String> tablesToBlock = new ArrayList<>();
        tablesToBlock.add(TopicsTables.BlockedTopicsContract.TABLE);

        TopicsWorker topicsWorker =
                spy(
                        new TopicsWorker(
                                mMockEpochManager,
                                mCacheManagerMock,
                                mBlockedTopicsManagerMock,
                                mAppUpdateManagerMock,
                                mMockFlags));

        ConsentManagerV2 consentManager =
                new ConsentManagerV2(
                        topicsWorker,
                        mAppConsentDaoSpy,
                        mEnrollmentDaoSpy,
                        mMeasurementImplMock,
                        mCustomAudienceDaoMock,
                        mAppConsentStorageManager,
                        mAppInstallDaoMock,
                        mFrequencyCapDaoMock,
                        mAdServicesStorageManager,
                        mConsentDatastore,
                        mAppSearchConsentManagerMock,
                        mUserProfileIdManagerMock,
                        mAppConsentForRStorageManager,
                        mMockFlags,
                        Flags.PPAPI_ONLY,
                        true,
                        true);

        doNothing().when(mBlockedTopicsManagerMock).blockTopic(any());
        doNothing().when(mBlockedTopicsManagerMock).unblockTopic(any());
        // The actual usage is to invoke clearAllTopicsData() from TopicsWorker
        doNothing().when(topicsWorker).clearAllTopicsData(any());

        consentManager.revokeConsentForTopic(topic);
        consentManager.restoreConsentForTopic(topic);
        consentManager.resetTopics();

        verify(mBlockedTopicsManagerMock).blockTopic(topic);
        verify(mBlockedTopicsManagerMock).unblockTopic(topic);
        verify(topicsWorker).clearAllTopicsData(tablesToBlock);
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedRow() {
        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManagerV2 temporalConsentManagerV2 =
                getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        temporalConsentManagerV2.enable(mSpyContext);

        verify(mUiStatsLoggerMock).logOptInSelected();
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedEu() {
        doReturn(true).when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManagerV2 temporalConsentManagerV2 =
                getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        temporalConsentManagerV2.enable(mSpyContext);

        verify(mUiStatsLoggerMock).logOptInSelected();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_PpApiOnly()
            throws RemoteException, IOException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.TOPICS));

        verify(spyConsentManager).resetTopicsAndBlockedTopics();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_SystemServerOnly() throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(PrivacySandboxUxCollection.UNSUPPORTED_UX).when(mAdServicesStorageManager).getUx();
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.TOPICS.toConsentApiType());

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        verify(mAdServicesStorageManager).setConsent(eq(AdServicesApiType.TOPICS), eq(isGiven));
        verify(mMockIAdServicesManager, times(2)).getConsent(ConsentParcel.TOPICS);
        verify(spyConsentManager).resetTopicsAndBlockedTopics();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_PpApiAndSystemServer()
            throws RemoteException, IOException {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(PrivacySandboxUxCollection.UNSUPPORTED_UX).when(mAdServicesStorageManager).getUx();
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.TOPICS.toConsentApiType());
        doThrow(RuntimeException.class)
                .when(mAdServicesStorageManager)
                .getConsent(eq(AdServicesApiType.TOPICS));
        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mAdServicesStorageManager)
                .getConsent(eq(AdServicesApiType.TOPICS));
        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        verify(mAdServicesStorageManager).setConsent(eq(AdServicesApiType.TOPICS), eq(isGiven));

        verify(mAdServicesStorageManager, times(2)).getConsent(eq(AdServicesApiType.TOPICS));

        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.TOPICS));
        verify(spyConsentManager).resetTopicsAndBlockedTopics();
        verify(
                () ->
                        ErrorLogUtil.e(
                                any(Throwable.class),
                                eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX)));
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_AppSearchOnly() throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        when(mAppSearchConsentManagerMock.getConsent(any()))
                .thenReturn(AdServicesApiConsent.REVOKED);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        AdServicesApiType apiType = AdServicesApiType.TOPICS;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(eq(/* isGiven */ true), eq(apiType));
        verify(mAppSearchConsentManagerMock).setConsent(eq(apiType), eq(isGiven));
        when(mAppSearchConsentManagerMock.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.GIVEN);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
    }

    @Test
    public void testMeasurementConsentIsGivenAfterEnabling_ppapiAndAdExtDataServiceOnly()
            throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(true).when(mMockFlags).getEnableAdExtServiceConsentData();
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        AdServicesApiType apiType = AdServicesApiType.MEASUREMENTS;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(eq(/* isGiven */ true), eq(apiType));
        verify(mAdServicesExtDataManager).setMsmtConsent(eq(true));
        when(mAdServicesExtDataManager.getMsmtConsent()).thenReturn(true);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();
    }

    @Test
    public void testMeasurementConsentIsRevokedAfterDisabling_ppapiAndAdExtDataServiceOnly()
            throws RemoteException, IOException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(true).when(mMockFlags).getEnableAdExtServiceConsentData();
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        AdServicesApiType apiType = AdServicesApiType.MEASUREMENTS;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        when(mAppConsentForRStorageManager.getConsent(any()))
                .thenReturn(AdServicesApiConsent.REVOKED);
        spyConsentManager.disable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(eq(/* isGiven */ false), eq(apiType));
        verify(mAppConsentForRStorageManager)
                .setConsent(eq(AdServicesApiType.MEASUREMENTS), eq(false));
        when(mAppConsentForRStorageManager.getConsent(eq(AdServicesApiType.MEASUREMENTS)))
                .thenReturn(AdServicesApiConsent.REVOKED);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven())
                .isFalse();
    }

    @Test
    public void testFledgeConsentIsEnabled_userProfileIdIsClearedThanRecreated()
            throws RemoteException {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        doReturn(PrivacySandboxUxCollection.UNSUPPORTED_UX).when(mAdServicesStorageManager).getUx();
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.FLEDGE.toConsentApiType());

        spyConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isTrue();
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    public void testFledgeConsentIsDisabled_userProfileIdIsCleared() throws RemoteException {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(PrivacySandboxUxCollection.UNSUPPORTED_UX).when(mAdServicesStorageManager).getUx();
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.FLEDGE.toConsentApiType());

        spyConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isFalse();
        verify(mUserProfileIdManagerMock).deleteId();
    }


    @Test
    public void testAllThreeConsentsPerApiAreGivenAggregatedConsentIsSet_PpApiOnly()
            throws RemoteException, IOException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.TOPICS));
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.FLEDGE));
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.MEASUREMENTS));
        verify(mConsentDatastore, times(1))
                .put(eq(AdServicesApiType.ALL_API.toPpApiDatastoreKey()), eq(true));

        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testAllConsentAreRevokedClenaupIsExecuted() throws IOException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        // set up the initial state
        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.TOPICS));
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.FLEDGE));
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(
                        eq(/* isGiven */ true), eq(AdServicesApiType.MEASUREMENTS));

        // disable all the consent one by one
        spyConsentManager.disable(mSpyContext, AdServicesApiType.TOPICS);
        spyConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);
        spyConsentManager.disable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isFalse();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isFalse();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven())
                .isFalse();
        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.TOPICS)));
        verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.FLEDGE)));
        verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.MEASUREMENTS)));
        verify(() -> BackgroundJobsManager.unscheduleAllBackgroundJobs(any(JobScheduler.class)));

        verify(spyConsentManager, times(2)).resetTopicsAndBlockedTopics();
        verify(spyConsentManager, times(2)).clearAllAppConsentData();
        verify(spyConsentManager, times(2)).resetMeasurement();
    }

    @Test
    public void testManualInteractionWithConsentRecorded_PpApiOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent()).isEqualTo(UNKNOWN);

        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();

        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager, never()).recordUserManualInteractionWithConsent(anyInt());
    }

    @Test
    public void testManualInteractionWithConsentRecorded_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent()).isEqualTo(UNKNOWN);

        verify(mMockIAdServicesManager).getUserManualInteractionWithConsent();

        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mMockIAdServicesManager)
                .getUserManualInteractionWithConsent();
        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, times(2)).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());

        // Verify the bit is not set in PPAPI
        assertThat(mConsentDatastore.get(MANUAL_INTERACTION_WITH_CONSENT_RECORDED)).isNull();
    }

    @Test
    public void testManualInteractionWithConsentRecorded_PpApiAndSystemServer()
            throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        @ConsentManagerV2.UserManualInteraction
        int userManualInteractionWithConsent =
                spyConsentManager.getUserManualInteractionWithConsent();

        assertThat(userManualInteractionWithConsent).isEqualTo(UNKNOWN);

        verify(mMockIAdServicesManager).getUserManualInteractionWithConsent();

        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mMockIAdServicesManager)
                .getUserManualInteractionWithConsent();
        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, times(2)).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());

        // Verify the bit is also set in PPAPI
        assertThat(mConsentDatastore.get(MANUAL_INTERACTION_WITH_CONSENT_RECORDED)).isTrue();
    }

    @Test
    public void testManualInteractionWithConsentRecorded_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(UNKNOWN);
        assertThat(spyConsentManager.getUserManualInteractionWithConsent()).isEqualTo(UNKNOWN);
        verify(mAppSearchConsentManagerMock).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();

        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentManagerMock)
                .recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);
        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(MANUAL_INTERACTIONS_RECORDED);
        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager, never()).recordUserManualInteractionWithConsent(anyInt());
    }

    @Test
    public void testManualInteractionWithConsentRecorded_ppapiAndAdExtDataServiceOnly()
            throws RemoteException, IOException {
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        when(mAdServicesExtDataManager.getManualInteractionWithConsentStatus()).thenReturn(UNKNOWN);
        assertThat(spyConsentManager.getUserManualInteractionWithConsent()).isEqualTo(UNKNOWN);
        verify(mAdServicesExtDataManager).getManualInteractionWithConsentStatus();

        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);
        verify(mAppConsentForRStorageManager)
                .recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);
        when(mAdServicesExtDataManager.getManualInteractionWithConsentStatus())
                .thenReturn(MANUAL_INTERACTIONS_RECORDED);
        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);
    }

    // Note this method needs to be invoked after other private variables are initialized.
    private ConsentManagerV2 getConsentManagerByConsentSourceOfTruth(int consentSourceOfTruth) {
        return new ConsentManagerV2(
                mTopicsWorkerMock,
                mAppConsentDaoSpy,
                mEnrollmentDaoSpy,
                mMeasurementImplMock,
                mCustomAudienceDaoMock,
                mAppConsentStorageManager,
                mAppInstallDaoMock,
                mFrequencyCapDaoMock,
                mAdServicesStorageManager,
                mConsentDatastore,
                mAppSearchConsentManagerMock,
                mUserProfileIdManagerMock,
                mAppConsentForRStorageManager,
                mMockFlags,
                consentSourceOfTruth,
                true,
                true);
    }

    private ConsentManagerV2 getSpiedConsentManagerForMigrationTesting(
            boolean isGiven, int consentSourceOfTruth) throws RemoteException {
        ConsentManagerV2 consentManager =
                spy(getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth));

        // Disable IPC calls
        ConsentParcel consentParcel =
                isGiven
                        ? ConsentParcel.createGivenConsent(ConsentParcel.ALL_API)
                        : ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API);
        doReturn(consentParcel).when(mMockIAdServicesManager).getConsent(ConsentParcel.ALL_API);
        doReturn(isGiven).when(mMockIAdServicesManager).wasNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        doReturn(isGiven).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);
        doReturn(UNKNOWN).when(mMockIAdServicesManager).getUserManualInteractionWithConsent();
        doNothing().when(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());
        doReturn(AdServicesApiConsent.getConsent(isGiven))
                .when(mAppSearchConsentManagerMock)
                .getConsent(AdServicesApiType.ALL_API);
        return consentManager;
    }

    private ConsentManagerV2 getSpiedConsentManagerForConsentPerApiTesting(
            boolean isGiven,
            int consentSourceOfTruth,
            @ConsentParcel.ConsentApiType int consentApiType)
            throws RemoteException {
        ConsentManagerV2 consentManager =
                spy(getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth));

        // Disable IPC calls
        doNothing().when(mAdServicesStorageManager).setConsent(any(), anyBoolean());
        ConsentParcel consentParcel =
                isGiven
                        ? ConsentParcel.createGivenConsent(consentApiType)
                        : ConsentParcel.createRevokedConsent(consentApiType);
        doReturn(consentParcel).when(mMockIAdServicesManager).getConsent(consentApiType);
        doReturn(isGiven).when(mMockIAdServicesManager).wasNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        doReturn(isGiven).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);
        doReturn(UNKNOWN).when(mMockIAdServicesManager).getUserManualInteractionWithConsent();
        doNothing().when(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());
        doReturn(AdServicesApiConsent.getConsent(isGiven))
                .when(mAppSearchConsentManagerMock)
                .getConsent(any());
        return consentManager;
    }

    private void verifyConsentMigration(
            boolean isGiven,
            boolean hasWrittenToPpApi,
            boolean hasWrittenToSystemServer,
            boolean hasReadFromSystemServer)
            throws RemoteException, IOException {
        verify(mAppConsentStorageManager, verificationMode(hasWrittenToPpApi))
                .setConsent(any(), eq(isGiven));
        verify(mAdServicesStorageManager, verificationMode(hasWrittenToSystemServer))
                .setConsent(any(), eq(isGiven));

        verify(mMockIAdServicesManager, verificationMode(hasReadFromSystemServer))
                .getConsent(ConsentParcel.ALL_API);
    }

    private void verifyDataCleanup(ConsentManagerV2 consentManager) throws IOException {
        verify(consentManager).resetTopicsAndBlockedTopics();
        verify(consentManager).clearAllAppConsentData();
        verify(consentManager).resetMeasurement();
    }

    private VerificationMode verificationMode(boolean hasHappened) {
        return hasHappened ? atLeastOnce() : never();
    }

    private void mockGetPackageUid(@NonNull String packageName, int uid)
            throws PackageManager.NameNotFoundException {
        doReturn(uid)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void mockInstalledApplications(List<ApplicationInfo> applicationsInstalled) {
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
    }

    private void mockThrowExceptionOnGetPackageUid(@NonNull String packageName) {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void mockMeasurementEnabled(boolean value) {
        when(mMockFlags.getMeasurementEnabled()).thenReturn(value);
    }

    private List<ApplicationInfo> createApplicationInfos(String... packageNames) {
        return Arrays.stream(packageNames)
                .map(s -> ApplicationInfoBuilder.newBuilder().setPackageName(s).build())
                .collect(Collectors.toList());
    }

    private class ListMatcherIgnoreOrder implements ArgumentMatcher<List<String>> {
        @NonNull private final List<String> mStrings;

        private ListMatcherIgnoreOrder(@NonNull List<String> strings) {
            Objects.requireNonNull(strings);
            mStrings = strings;
        }

        @Override
        public boolean matches(@Nullable List<String> argument) {
            if (argument == null) {
                return false;
            }
            if (argument.size() != mStrings.size()) {
                return false;
            }
            if (!argument.containsAll(mStrings)) {
                return false;
            }
            if (!mStrings.containsAll(argument)) {
                return false;
            }
            return true;
        }
    }

    @Test
    public void testCurrentPrivacySandboxFeature_ppapiOnly() throws RemoteException {
        getCurrentPrivacySandboxFeatureWithPpApiOnly(Flags.PPAPI_ONLY);
    }

    @Test
    public void testCurrentPrivacySandboxFeature_ppapiAndAdExtDataServiceOnly()
            throws RemoteException {
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        getCurrentPrivacySandboxFeatureWithPpApiOnly(Flags.PPAPI_AND_ADEXT_SERVICE);
    }

    private void getCurrentPrivacySandboxFeatureWithPpApiOnly(int consentSourceOfTruth)
            throws RemoteException {
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        verify(mMockIAdServicesManager, never()).getCurrentPrivacySandboxFeature();

        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        verify(mMockIAdServicesManager, never()).getCurrentPrivacySandboxFeature();
        verify(mMockIAdServicesManager, never()).setCurrentPrivacySandboxFeature(anyString());
    }

    @Test
    public void testCurrentPrivacySandboxFeature_SystemServerOnly()
            throws RemoteException, IOException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        doThrow(RuntimeException.class)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        verify(mAdServicesStorageManager).getCurrentPrivacySandboxFeature();

        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE;
        MockedVoidMethod mockedVoidMethod =
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
        verify(mockedVoidMethod);
        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);

        assertThat(
                        mConsentDatastore.get(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name()))
                .isNull();
        assertThat(
                        mConsentDatastore.get(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name()))
                .isNull();
        assertThat(
                        mConsentDatastore.get(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name()))
                .isNull();
    }

    @Test
    public void testCurrentPrivacySandboxFeature_PpApiAndSystemServer()
            throws RemoteException, IOException {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);
        doThrow(RuntimeException.class)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        verify(mAdServicesStorageManager).getCurrentPrivacySandboxFeature();
        doThrow(RuntimeException.class)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        int saveErrorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE;
        MockedVoidMethod mockedVoidMethod =
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(saveErrorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX));
        verify(mockedVoidMethod);
        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT)
                .when(mAdServicesStorageManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);

        // Only the last set bit is true.
        assertThat(
                        mConsentDatastore.get(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name()))
                .isFalse();
        assertThat(
                        mConsentDatastore.get(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name()))
                .isFalse();
        assertThat(
                        mConsentDatastore.get(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name()))
                .isTrue();
    }

    @Test
    public void testCurrentPrivacySandboxFeature_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getCurrentPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);

        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        verify(mAppSearchConsentManagerMock)
                .setCurrentPrivacySandboxFeature(
                        eq(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT));
        when(mAppSearchConsentManagerMock.getCurrentPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        verify(mMockIAdServicesManager, never()).getCurrentPrivacySandboxFeature();
        verify(mMockIAdServicesManager, never()).setCurrentPrivacySandboxFeature(anyString());
    }

    @Test
    public void isAdIdEnabledTest_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isAdIdEnabled()).isFalse();

        verify(mMockIAdServicesManager).isAdIdEnabled();

        doReturn(true).when(mMockIAdServicesManager).isAdIdEnabled();
        spyConsentManager.setAdIdEnabled(true);

        assertThat(spyConsentManager.isAdIdEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdIdEnabled();
        verify(mMockIAdServicesManager).setAdIdEnabled(anyBoolean());
    }

    @Test
    public void isAdIdEnabledTest_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isAdIdEnabled = spyConsentManager.isAdIdEnabled();

        assertThat(isAdIdEnabled).isFalse();

        verify(mMockIAdServicesManager).isAdIdEnabled();

        doReturn(true).when(mMockIAdServicesManager).isAdIdEnabled();
        spyConsentManager.setAdIdEnabled(true);

        assertThat(spyConsentManager.isAdIdEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdIdEnabled();
        verify(mMockIAdServicesManager).setAdIdEnabled(anyBoolean());
    }

    @Test
    public void isAdIdEnabledTest_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isAdIdEnabled();
        assertThat(spyConsentManager.isAdIdEnabled()).isFalse();
        verify(mAppSearchConsentManagerMock).isAdIdEnabled();

        doReturn(true).when(mAppSearchConsentManagerMock).isAdIdEnabled();
        spyConsentManager.setAdIdEnabled(true);

        assertThat(spyConsentManager.isAdIdEnabled()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isAdIdEnabled();
        verify(mAppSearchConsentManagerMock).setAdIdEnabled(anyBoolean());
    }

    @Test
    public void isU18AccountTest_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isU18Account()).isFalse();

        verify(mMockIAdServicesManager).isU18Account();

        doReturn(true).when(mMockIAdServicesManager).isU18Account();
        spyConsentManager.setU18Account(true);

        assertThat(spyConsentManager.isU18Account()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isU18Account();
        verify(mMockIAdServicesManager).setU18Account(anyBoolean());
    }

    @Test
    public void isU18AccountTest_PpApiAndSystemServer() throws RemoteException, IOException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isU18Account = spyConsentManager.isU18Account();

        assertThat(isU18Account).isFalse();

        verify(mMockIAdServicesManager).isU18Account();

        doReturn(true).when(mMockIAdServicesManager).isU18Account();
        spyConsentManager.setU18Account(true);

        assertThat(spyConsentManager.isU18Account()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isU18Account();
        verify(mMockIAdServicesManager).setU18Account(anyBoolean());
    }

    @Test
    public void isU18AccountTest_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isU18Account();
        assertThat(spyConsentManager.isU18Account()).isFalse();
        verify(mAppSearchConsentManagerMock).isU18Account();

        doReturn(true).when(mAppSearchConsentManagerMock).isU18Account();
        spyConsentManager.setU18Account(true);

        assertThat(spyConsentManager.isU18Account()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isU18Account();
        verify(mAppSearchConsentManagerMock).setU18Account(anyBoolean());
    }

    @Test
    public void isU18AccountTest_ppapiAndAdExtDataServiceOnly()
            throws RemoteException, IOException {
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppConsentForRStorageManager).isU18Account();
        assertThat(spyConsentManager.isU18Account()).isFalse();
        verify(mAppConsentForRStorageManager).isU18Account();

        doReturn(true).when(mAppConsentForRStorageManager).isU18Account();
        spyConsentManager.setU18Account(true);

        assertThat(spyConsentManager.isU18Account()).isTrue();

        verify(mAppConsentForRStorageManager, times(2)).isU18Account();
        verify(mAppConsentForRStorageManager).setU18Account(anyBoolean());
    }

    @Test
    public void isEntryPointEnabledTest_ppapiAndAdExtDataServiceOnly()
            throws RemoteException, IOException {
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        spyConsentManager.setEntryPointEnabled(true);
        assertThat(spyConsentManager.isEntryPointEnabled()).isTrue();
        spyConsentManager.setEntryPointEnabled(false);
        assertThat(spyConsentManager.isEntryPointEnabled()).isFalse();
    }

    @Test
    public void isEntryPointEnabledTest_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isEntryPointEnabled()).isFalse();

        verify(mMockIAdServicesManager).isEntryPointEnabled();

        doReturn(true).when(mMockIAdServicesManager).isEntryPointEnabled();
        spyConsentManager.setEntryPointEnabled(true);

        assertThat(spyConsentManager.isEntryPointEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isEntryPointEnabled();
        verify(mMockIAdServicesManager).setEntryPointEnabled(anyBoolean());
    }

    @Test
    public void isEntryPointEnabledTest_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isEntryPointEnabled = spyConsentManager.isEntryPointEnabled();

        assertThat(isEntryPointEnabled).isFalse();

        verify(mMockIAdServicesManager).isEntryPointEnabled();

        doReturn(true).when(mMockIAdServicesManager).isEntryPointEnabled();
        spyConsentManager.setEntryPointEnabled(true);

        assertThat(spyConsentManager.isEntryPointEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isEntryPointEnabled();
        verify(mMockIAdServicesManager).setEntryPointEnabled(anyBoolean());
    }

    @Test
    public void isEntryPointEnabledTest_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isEntryPointEnabled();
        assertThat(spyConsentManager.isEntryPointEnabled()).isFalse();
        verify(mAppSearchConsentManagerMock).isEntryPointEnabled();

        doReturn(true).when(mAppSearchConsentManagerMock).isEntryPointEnabled();
        spyConsentManager.setEntryPointEnabled(true);

        assertThat(spyConsentManager.isEntryPointEnabled()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isEntryPointEnabled();
        verify(mAppSearchConsentManagerMock).setEntryPointEnabled(anyBoolean());
    }

    @Test
    public void isAdultAccountTest_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isAdultAccount()).isFalse();

        verify(mMockIAdServicesManager).isAdultAccount();

        doReturn(true).when(mMockIAdServicesManager).isAdultAccount();
        spyConsentManager.setAdultAccount(true);

        assertThat(spyConsentManager.isAdultAccount()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdultAccount();
        verify(mMockIAdServicesManager).setAdultAccount(anyBoolean());
    }

    @Test
    public void isAdultAccountTest_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isAdultAccount = spyConsentManager.isAdultAccount();

        assertThat(isAdultAccount).isFalse();

        verify(mMockIAdServicesManager).isAdultAccount();

        doReturn(true).when(mMockIAdServicesManager).isAdultAccount();
        spyConsentManager.setAdultAccount(true);

        assertThat(spyConsentManager.isAdultAccount()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdultAccount();
        verify(mMockIAdServicesManager).setAdultAccount(anyBoolean());
    }

    @Test
    public void isAdultAccountTest_ppapiAndAdExtDataServiceOnly()
            throws RemoteException, IOException {
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppConsentForRStorageManager).isAdultAccount();
        assertThat(spyConsentManager.isAdultAccount()).isFalse();
        verify(mAppConsentForRStorageManager).isAdultAccount();

        doReturn(true).when(mAppConsentForRStorageManager).isAdultAccount();
        spyConsentManager.setAdultAccount(true);

        assertThat(spyConsentManager.isAdultAccount()).isTrue();

        verify(mAppConsentForRStorageManager, times(2)).isAdultAccount();
        verify(mAppConsentForRStorageManager).setAdultAccount(anyBoolean());
    }

    @Test
    public void isAdultAccountTest_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isAdultAccount();
        assertThat(spyConsentManager.isAdultAccount()).isFalse();
        verify(mAppSearchConsentManagerMock).isAdultAccount();

        doReturn(true).when(mAppSearchConsentManagerMock).isAdultAccount();
        spyConsentManager.setAdultAccount(true);

        assertThat(spyConsentManager.isAdultAccount()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isAdultAccount();
        verify(mAppSearchConsentManagerMock).setAdultAccount(anyBoolean());
    }

    @Test
    public void wasU18NotificationDisplayedTest_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasU18NotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasU18NotificationDisplayed();
        spyConsentManager.setU18NotificationDisplayed(true);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasU18NotificationDisplayed();
        verify(mMockIAdServicesManager).setU18NotificationDisplayed(anyBoolean());
    }

    @Test
    public void wasU18NotificationDisplayedTest_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasU18NotificationDisplayed = spyConsentManager.wasU18NotificationDisplayed();

        assertThat(wasU18NotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasU18NotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasU18NotificationDisplayed();
        spyConsentManager.setU18NotificationDisplayed(true);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasU18NotificationDisplayed();
        verify(mMockIAdServicesManager).setU18NotificationDisplayed(anyBoolean());
    }

    @Test
    public void wasU18NotificationDisplayedTest_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isFalse();
        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();

        doReturn(true).when(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        spyConsentManager.setU18NotificationDisplayed(true);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).setU18NotificationDisplayed(anyBoolean());
    }

    @Test
    public void testWasU18NotificationDisplayed_ppapiAndAdExtDataServiceOnly()
            throws RemoteException, IOException {
        int consentSourceOfTruth = Flags.PPAPI_AND_ADEXT_SERVICE;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppConsentForRStorageManager).wasU18NotificationDisplayed();
        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isFalse();
        verify(mAppConsentForRStorageManager).wasU18NotificationDisplayed();

        doReturn(true).when(mAppConsentForRStorageManager).wasU18NotificationDisplayed();
        spyConsentManager.setU18NotificationDisplayed(true);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isTrue();

        verify(mAppConsentForRStorageManager, times(2)).wasU18NotificationDisplayed();
        verify(mAppConsentForRStorageManager).setU18NotificationDisplayed(anyBoolean());
    }

    @Test
    public void testIsRvcAdultUser_ageCheckFlagOn() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        when(mMockFlags.getRvcPostOtaNotifAgeCheck()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(true).when(mAppConsentForRStorageManager).wasU18NotificationDisplayed();
        doReturn(false).when(mAppConsentForRStorageManager).isU18Account();
        doReturn(true).when(mAppConsentForRStorageManager).isAdultAccount();
        assertThat(spyConsentManager.isOtaAdultUserFromRvc()).isTrue();
        verify(mAppConsentForRStorageManager).wasU18NotificationDisplayed();
        verify(mAppConsentForRStorageManager).isAdultAccount();
        verify(mAppConsentForRStorageManager).isU18Account();
    }

    @Test
    public void testIsRvcAdultUser_ageCheckFlagOff() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(true).when(mAppConsentForRStorageManager).wasU18NotificationDisplayed();
        assertThat(spyConsentManager.isOtaAdultUserFromRvc()).isTrue();
        verify(mAppConsentForRStorageManager).wasU18NotificationDisplayed();
    }

    @Test
    public void testGetUx_PpApiOnly() throws RemoteException {
        getUxWithPpApiOnly(Flags.PPAPI_ONLY);
    }

    @Test
    public void testGetUx_ppapiAndAdExtDataServiceOnly() throws RemoteException {
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        getUxWithPpApiOnly(Flags.PPAPI_AND_ADEXT_SERVICE);
    }

    private void getUxWithPpApiOnly(int consentSourceOfTruth) throws RemoteException {
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mUxStatesDaoMock).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mUxStatesDaoMock, times(UX_TYPE_COUNT)).getUx();
        verify(mUxStatesDaoMock, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void getUxTest_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux.toString()).when(mMockIAdServicesManager).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).getUx();
        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void getUxTest_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux.toString()).when(mMockIAdServicesManager).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).getUx();
        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void getUxTest_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mAppSearchConsentManagerMock).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mAppSearchConsentManagerMock, times(UX_TYPE_COUNT)).getUx();
        verify(mAppSearchConsentManagerMock, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void testGetEnrollmentChannel_ppapiOnly() throws RemoteException, IOException {
        getEnrollmentChannelWithPpApiOnly(Flags.PPAPI_ONLY);
    }

    @Test
    public void testGetEnrollmentChannel_ppapiAndAdExtDataServiceOnly()
            throws RemoteException, IOException {
        when(mMockFlags.getEnableAdExtServiceConsentData()).thenReturn(true);
        getEnrollmentChannelWithPpApiOnly(Flags.PPAPI_AND_ADEXT_SERVICE);
    }

    private void getEnrollmentChannelWithPpApiOnly(int consentSourceOfTruth)
            throws RemoteException {
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mUxStatesDaoMock).getEnrollmentChannel(ux);
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mUxStatesDaoMock, times(ENROLLMENT_CHANNEL_COUNT)).getEnrollmentChannel(any());
        verify(mUxStatesDaoMock, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(any(), any());
    }

    @Test
    public void getEnrollmentChannel_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel.toString()).when(mMockIAdServicesManager).getEnrollmentChannel();
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT)).getEnrollmentChannel();
        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(anyString());
    }

    @Test
    public void getEnrollmentChannel_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel.toString()).when(mMockIAdServicesManager).getEnrollmentChannel();
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT)).getEnrollmentChannel();
        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(anyString());
    }

    @Test
    public void getEnrollmentChannel_appSearchOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManagerV2 spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mAppSearchConsentManagerMock).getEnrollmentChannel(ux);
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mAppSearchConsentManagerMock, times(ENROLLMENT_CHANNEL_COUNT))
                .getEnrollmentChannel(any());
        verify(mAppSearchConsentManagerMock, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(any(), any());
    }
}
