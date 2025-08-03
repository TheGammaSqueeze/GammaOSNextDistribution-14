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

package com.android.adservices.service.stats;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastT;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionStatus.INSERT_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.DbTransactionType.WRITE_TRANSACTION_TYPE;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyDbTransactionEndedStats.MethodName.INSERT_KEY;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchJobType.ENCRYPTION_KEY_DAILY_FETCH_JOB;
import static com.android.adservices.service.stats.AdServicesEncryptionKeyFetchedStats.FetchStatus.IO_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_FETCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_DATA_STORED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_MATCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_ATTRIBUTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.APP_MANIFEST_CONFIG_HELPER_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.DESTINATION_REGISTERED_BEACONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.GET_AD_SELECTION_DATA_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.GET_AD_SELECTION_DATA_BUYER_INPUT_GENERATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_IMMEDIATE_SIGN_JOIN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_BACKGROUND_JOB_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_KEY_ATTESTATION_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_INITIALIZE_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_IMMEDIATE_SIGN_JOIN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_SIGN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_JOIN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.INTERACTION_REPORTING_TABLE_CLEARED;
import static com.android.adservices.service.stats.AdServicesStatsLog.REPORT_INTERACTION_API_CALLED;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.ClassifierType;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.OnDeviceClassifierStatus;
import static com.android.adservices.service.stats.EpochComputationClassifierStats.PrecomputedClassifierStatus;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.ReportEventRequest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.common.AppManifestConfigCall.ApiType;
import com.android.adservices.service.common.AppManifestConfigCall.Result;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.WipeoutStatus;
import com.android.adservices.service.measurement.attribution.AttributionStatus;
import com.android.adservices.service.stats.kanon.KAnonBackgroundJobStatusStats;
import com.android.adservices.service.stats.kanon.KAnonGetChallengeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonImmediateSignJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonInitializeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonSignStatusStats;
import com.android.dx.mockito.inline.extended.MockedVoidMethod;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

@SpyStatic(SdkLevel.class)
@SpyStatic(AdServicesStatsLog.class)
public final class StatsdAdServicesLoggerTest extends AdServicesExtendedMockitoTestCase {

    // Atom IDs
    private static final int TOPICS_REPORTED_ATOM_ID = 535;
    private static final int EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID = 537;
    private static final int TOPICS_REPORTED_COMPAT_ATOM_ID = 598;
    private static final int EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID = 599;
    private static final ImmutableList<Integer> TOPIC_IDS = ImmutableList.of(10230, 10227);

    // Test params for GetTopicsReportedStats
    private static final int FILTERED_BLOCKED_TOPIC_COUNT = 0;
    private static final int DUPLICATE_TOPIC_COUNT = 0;
    private static final int TOPIC_IDS_COUNT = 1;
    private static final String SOURCE_REGISTRANT = "android-app://com.registrant";

    private static final GetTopicsReportedStats TOPICS_REPORTED_STATS_DATA =
            GetTopicsReportedStats.builder()
                    .setFilteredBlockedTopicCount(FILTERED_BLOCKED_TOPIC_COUNT)
                    .setDuplicateTopicCount(DUPLICATE_TOPIC_COUNT)
                    .setTopicIdsCount(TOPIC_IDS_COUNT)
                    .setTopicIds(TOPIC_IDS)
                    .build();

    // Test params for EpochComputationClassifierStats
    private static final int BUILD_ID = 8;
    private static final String ASSET_VERSION = "2";

    private static final EpochComputationClassifierStats EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA =
            EpochComputationClassifierStats.builder()
                    .setTopicIds(TOPIC_IDS)
                    .setBuildId(BUILD_ID)
                    .setAssetVersion(ASSET_VERSION)
                    .setClassifierType(ClassifierType.ON_DEVICE_CLASSIFIER)
                    .setOnDeviceClassifierStatus(
                            OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS)
                    .setPrecomputedClassifierStatus(
                            PrecomputedClassifierStatus.PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED)
                    .build();

    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

    private StatsdAdServicesLogger mLogger;
    @Mock private Flags mFlags;

    @Before
    public void setUp() {
        mLogger = new StatsdAdServicesLogger(mFlags);
    }

    @Test
    public void testLogGetTopicsReportedStats_tPlus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        mockIsAtLeastT(true);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), any(byte[].class)));
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), any(int[].class), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify compat logging
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(TOPICS_REPORTED_COMPAT_ATOM_ID),
                                eq(FILTERED_BLOCKED_TOPIC_COUNT),
                                eq(DUPLICATE_TOPIC_COUNT),
                                eq(TOPIC_IDS_COUNT),
                                any(byte[].class)));
        // Verify T+ logging
        verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_tPlus_noCompatLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        mockIsAtLeastT(true);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), any(int[].class), anyInt(), anyInt(), anyInt()));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify T+ logging only
        verify(
                () ->
                        AdServicesStatsLog.write(
                                TOPICS_REPORTED_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                FILTERED_BLOCKED_TOPIC_COUNT,
                                DUPLICATE_TOPIC_COUNT,
                                TOPIC_IDS_COUNT));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_sMinus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        mockIsAtLeastT(false);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), any(byte[].class)));

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // Verify only compat logging took place
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(TOPICS_REPORTED_COMPAT_ATOM_ID),
                                eq(FILTERED_BLOCKED_TOPIC_COUNT),
                                eq(DUPLICATE_TOPIC_COUNT),
                                eq(TOPIC_IDS_COUNT),
                                any(byte[].class)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogGetTopicsReportedStats_sMinus_noLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        mockIsAtLeastT(false);

        // Invoke logging call
        mLogger.logGetTopicsReportedStats(TOPICS_REPORTED_STATS_DATA);

        // No compat (and T+) logging should happen
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_tPlus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        mockIsAtLeastT(true);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(byte[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(int[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify compat logging
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID),
                                any(byte[].class), // topic ids converted into byte[]
                                eq(BUILD_ID),
                                eq(ASSET_VERSION),
                                eq(ClassifierType.ON_DEVICE_CLASSIFIER.getCompatLoggingValue()),
                                eq(
                                        OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                                .getCompatLoggingValue()),
                                eq(
                                        PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                                .getCompatLoggingValue())));
        // Verify T+ logging
        verify(
                () ->
                        AdServicesStatsLog.write(
                                EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                BUILD_ID,
                                ASSET_VERSION,
                                ClassifierType.ON_DEVICE_CLASSIFIER.getLoggingValue(),
                                OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                        .getLoggingValue(),
                                PrecomputedClassifierStatus
                                        .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                        .getLoggingValue()));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_tPlus_noCompatLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        mockIsAtLeastT(true);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(int[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify T+ logging
        verify(
                () ->
                        AdServicesStatsLog.write(
                                EPOCH_COMPUTATION_CLASSIFIER_ATOM_ID,
                                TOPIC_IDS.stream().mapToInt(Integer::intValue).toArray(),
                                BUILD_ID,
                                ASSET_VERSION,
                                ClassifierType.ON_DEVICE_CLASSIFIER.getLoggingValue(),
                                OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                        .getLoggingValue(),
                                PrecomputedClassifierStatus
                                        .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                        .getLoggingValue()));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_sMinus() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(false);
        mockIsAtLeastT(false);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        any(byte[].class),
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // Verify only compat logging took place
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(EPOCH_COMPUTATION_CLASSIFIER_COMPAT_ATOM_ID),
                                any(byte[].class), // topic ids converted into byte[]
                                eq(BUILD_ID),
                                eq(ASSET_VERSION),
                                eq(ClassifierType.ON_DEVICE_CLASSIFIER.getCompatLoggingValue()),
                                eq(
                                        OnDeviceClassifierStatus.ON_DEVICE_CLASSIFIER_STATUS_SUCCESS
                                                .getCompatLoggingValue()),
                                eq(
                                        PrecomputedClassifierStatus
                                                .PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED
                                                .getCompatLoggingValue())));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogEpochComputationClassifierStats_sMinus_noLoggingDueToKillSwitch() {
        // Mocks
        when(mFlags.getCompatLoggingKillSwitch()).thenReturn(true);
        mockIsAtLeastT(false);

        // Invoke logging call
        mLogger.logEpochComputationClassifierStats(EPOCH_COMPUTATION_CLASSIFIER_STATS_DATA);

        // No compat (and T+) logging should happen
        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testlogFledgeApiCallStats() {
        // Mocks
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyString(),
                                        anyString(),
                                        anyInt(),
                                        anyInt()));

        int apiName = AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
        int resultCode = STATUS_SUCCESS;
        int latencyMs = 10;

        // Invoke logging call
        mLogger.logFledgeApiCallStats(apiName, resultCode, latencyMs);

        // Verify logging
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_API_CALLED),
                                eq(AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN),
                                eq(apiName),
                                eq(""),
                                eq(""),
                                eq(latencyMs),
                                eq(resultCode)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testlogFledgeApiCallStatsWithAppPackageNameLogging_enabled() {
        when(mFlags.getFledgeAppPackageNameLoggingEnabled()).thenReturn(true);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyString(),
                                        anyString(),
                                        anyInt(),
                                        anyInt()));

        mLogger = new StatsdAdServicesLogger(mFlags);

        int apiName = AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
        String appPackageName = TEST_PACKAGE_NAME;
        int resultCode = STATUS_SUCCESS;
        int latencyMs = 10;

        // Log api call with app package name.
        mLogger.logFledgeApiCallStats(apiName, appPackageName, resultCode, latencyMs);

        // Verify app package name is logged.
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_API_CALLED),
                                eq(AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN),
                                eq(apiName),
                                eq(appPackageName),
                                eq(""),
                                eq(latencyMs),
                                eq(resultCode)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testlogFledgeApiCallStatsWithAppPackageNameLogging_nullAppPackageName() {
        when(mFlags.getFledgeAppPackageNameLoggingEnabled()).thenReturn(true);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyString(),
                                        anyString(),
                                        anyInt(),
                                        anyInt()));

        mLogger = new StatsdAdServicesLogger(mFlags);

        int apiName = AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
        String appPackageName = null;
        int resultCode = STATUS_SUCCESS;
        int latencyMs = 10;

        // Log api call with app package name.
        mLogger.logFledgeApiCallStats(apiName, appPackageName, resultCode, latencyMs);

        // Verify app package name is logged.
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_API_CALLED),
                                eq(AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN),
                                eq(apiName),
                                eq(""),
                                eq(""),
                                eq(latencyMs),
                                eq(resultCode)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testlogFledgeApiCallStatsWithAppPackageNameLogging_disabled() {
        when(mFlags.getFledgeAppPackageNameLoggingEnabled()).thenReturn(false);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyString(),
                                        anyString(),
                                        anyInt(),
                                        anyInt()));

        mLogger = new StatsdAdServicesLogger(mFlags);

        int apiName = AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
        String appPackageName = TEST_PACKAGE_NAME;
        int resultCode = STATUS_SUCCESS;
        int latencyMs = 10;

        // Log api call with app package name.
        mLogger.logFledgeApiCallStats(apiName, appPackageName, resultCode, latencyMs);

        // Verify app package name is not logged.
        verify(
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_API_CALLED),
                                eq(AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN),
                                eq(apiName),
                                eq(""),
                                eq(""),
                                eq(latencyMs),
                                eq(resultCode)));

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }


    @Test
    public void logMeasurementDebugKeysMatch_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        String enrollmentId = "EnrollmentId";
        long hashedValue = 5000L;
        long hashLimit = 10000L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setDebugJoinKeyHashedValue(hashedValue)
                        .setDebugJoinKeyHashLimit(hashLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementDebugKeysMatch(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_DEBUG_KEYS),
                                eq(enrollmentId),
                                // topic ids converted into byte[]
                                eq(attributionType),
                                eq(true),
                                eq(hashedValue),
                                eq(hashLimit),
                                eq(SOURCE_REGISTRANT));
        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAttribution_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        MeasurementAttributionStats stats =
                new MeasurementAttributionStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_ATTRIBUTION)
                        .setSourceType(AttributionStatus.SourceType.VIEW.getValue())
                        .setSurfaceType(AttributionStatus.AttributionSurface.APP_WEB.getValue())
                        .setResult(AttributionStatus.AttributionResult.SUCCESS.getValue())
                        .setFailureType(AttributionStatus.FailureType.UNKNOWN.getValue())
                        .setSourceDerived(false)
                        .setInstallAttribution(true)
                        .setAttributionDelay(100L)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .setAggregateReportCount(1)
                        .setAggregateDebugReportCount(1)
                        .setEventReportCount(3)
                        .setEventDebugReportCount(1)
                        .build();
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyString(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logMeasurementAttributionStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_ATTRIBUTION),
                                eq(AttributionStatus.SourceType.VIEW.getValue()),
                                eq(AttributionStatus.AttributionSurface.APP_WEB.getValue()),
                                eq(AttributionStatus.AttributionResult.SUCCESS.getValue()),
                                eq(AttributionStatus.FailureType.UNKNOWN.getValue()),
                                eq(false),
                                eq(true),
                                eq(100L),
                                eq(SOURCE_REGISTRANT),
                                eq(1),
                                eq(1),
                                eq(3),
                                eq(1),
                                eq(0));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementWipeout_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        MeasurementWipeoutStats stats =
                new MeasurementWipeoutStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                        .setWipeoutType(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyString()));

        // Invoke logging call
        mLogger.logMeasurementWipeoutStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_WIPEOUT),
                                eq(WipeoutStatus.WipeoutType.CONSENT_FLIP.ordinal()),
                                eq(SOURCE_REGISTRANT));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementDelayedSourceRegistration_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        int UnknownEnumValue = 0;
        long registrationDelay = 500L;
        MeasurementDelayedSourceRegistrationStats stats =
                new MeasurementDelayedSourceRegistrationStats.Builder()
                        .setCode(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION)
                        .setRegistrationStatus(UnknownEnumValue)
                        .setRegistrationDelay(registrationDelay)
                        .setRegistrant(SOURCE_REGISTRANT)
                        .build();
        doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyLong(), anyString()));

        // Invoke logging call
        mLogger.logMeasurementDelayedSourceRegistrationStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_DELAYED_SOURCE_REGISTRATION),
                                eq(UnknownEnumValue),
                                eq(registrationDelay),
                                eq(SOURCE_REGISTRANT));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logConsentMigrationStats_success() {
        when(mFlags.getAdservicesConsentMigrationLoggingEnabled()).thenReturn(true);
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));

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

        // Invoke logging call
        mLogger.logConsentMigrationStats(consentMigrationStats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_CONSENT_MIGRATED),
                                eq(true),
                                eq(true),
                                eq(true),
                                eq(true),
                                eq(2),
                                eq(2),
                                eq(2));
        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logConsentMigrationStats_disabled() {
        when(mFlags.getAdservicesConsentMigrationLoggingEnabled()).thenReturn(false);

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

        // Invoke logging call
        mLogger.logConsentMigrationStats(consentMigrationStats);

        verifyZeroInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAdIdMatchForDebugKeys_success() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn(SOURCE_REGISTRANT);
        String enrollmentId = "EnrollmentId";
        long uniqueAdIdValue = 1L;
        long uniqueAdIdLimit = 5L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setNumUniqueAdIds(uniqueAdIdValue)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS),
                                eq(enrollmentId),
                                eq(attributionType),
                                eq(true),
                                eq(uniqueAdIdValue),
                                eq(uniqueAdIdLimit),
                                eq(SOURCE_REGISTRANT));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAdIdMatchForDebugKeys_appLoggingDisabled_emptyString() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(false);
        String enrollmentId = "EnrollmentId";
        long uniqueAdIdValue = 1L;
        long uniqueAdIdLimit = 5L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setNumUniqueAdIds(uniqueAdIdValue)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS),
                                eq(enrollmentId),
                                eq(attributionType),
                                eq(true),
                                eq(uniqueAdIdValue),
                                eq(uniqueAdIdLimit),
                                eq(""));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementAdIdMatchForDebugKeys_appNotAllowlisted_emptyString() {
        when(mFlags.getMeasurementEnableAppPackageNameLogging()).thenReturn(true);
        when(mFlags.getMeasurementAppPackageNameLoggingAllowlist()).thenReturn("");
        String enrollmentId = "EnrollmentId";
        long uniqueAdIdValue = 1L;
        long uniqueAdIdLimit = 5L;
        int attributionType = AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
        MsmtAdIdMatchForDebugKeysStats stats =
                MsmtAdIdMatchForDebugKeysStats.builder()
                        .setAdTechEnrollmentId(enrollmentId)
                        .setMatched(true)
                        .setAttributionType(attributionType)
                        .setNumUniqueAdIds(uniqueAdIdValue)
                        .setNumUniqueAdIdsLimit(uniqueAdIdLimit)
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .build();
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyString(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString()));

        // Invoke logging call
        mLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS),
                                eq(enrollmentId),
                                eq(attributionType),
                                eq(true),
                                eq(uniqueAdIdValue),
                                eq(uniqueAdIdLimit),
                                eq(""));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentData_success() {
        int transactionTypeEnumValue =
                EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.ordinal();
        doNothing()
                .when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyBoolean(), anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentDataStats(transactionTypeEnumValue, true, 100);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_DATA_STORED),
                                eq(transactionTypeEnumValue),
                                eq(true),
                                eq(100));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentMatch_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyBoolean(), anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentMatchStats(true, 100);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_MATCHED), eq(true), eq(100));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentFileDownload_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyBoolean(), anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentFileDownloadStats(true, 100);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED), eq(true), eq(100));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEnrollmentFailed_success() {
        int dataFileGroupStatusEnumValue =
                EnrollmentStatus.DataFileGroupStatus.PENDING_CUSTOM_VALIDATION.ordinal();
        int errorCauseEnumValue =
                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.ordinal();
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyString(),
                                        anyInt()));

        // Invoke logging call
        mLogger.logEnrollmentFailedStats(
                100, dataFileGroupStatusEnumValue, 10, "SomeSdkName", errorCauseEnumValue);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENROLLMENT_FAILED),
                                eq(100),
                                eq(dataFileGroupStatusEnumValue),
                                eq(10),
                                eq("SomeSdkName"),
                                eq(errorCauseEnumValue));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logMeasurementClickVerificationStats_success() {
        int sourceType = Source.SourceType.NAVIGATION.getIntValue();
        boolean inputEventPresent = true;
        boolean systemClickVerificationSuccessful = true;
        boolean systemClickVerificationEnabled = true;
        long inputEventDelayMs = 200L;
        long validDelayWindowMs = 1000L;
        String sourceRegistrant = "test_source_registrant";
        boolean clickDeduplicationEnabled = true;
        boolean clickDeduplicationEnforced = true;
        long maxSourcesPerClick = 1;
        boolean clickUnderLimit = true;

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(sourceType)
                        .setInputEventPresent(inputEventPresent)
                        .setSystemClickVerificationSuccessful(systemClickVerificationSuccessful)
                        .setSystemClickVerificationEnabled(systemClickVerificationEnabled)
                        .setInputEventDelayMillis(inputEventDelayMs)
                        .setValidDelayWindowMillis(validDelayWindowMs)
                        .setSourceRegistrant(sourceRegistrant)
                        .setClickDeduplicationEnabled(clickDeduplicationEnabled)
                        .setClickDeduplicationEnforced(clickDeduplicationEnforced)
                        .setMaxSourcesPerClick(maxSourcesPerClick)
                        .setCurrentRegistrationUnderClickDeduplicationLimit(clickUnderLimit)
                        .build();

        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyLong(),
                                        anyString(),
                                        anyBoolean(),
                                        anyBoolean(),
                                        anyLong(),
                                        anyBoolean()));

        // Invoke logging call.
        mLogger.logMeasurementClickVerificationStats(stats);

        // Verify only compat logging took place.
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION),
                                eq(sourceType),
                                eq(inputEventPresent),
                                eq(systemClickVerificationSuccessful),
                                eq(systemClickVerificationEnabled),
                                eq(inputEventDelayMs),
                                eq(validDelayWindowMs),
                                eq(""), // App package name not in allow list.
                                eq(clickDeduplicationEnabled),
                                eq(clickDeduplicationEnforced),
                                eq(maxSourcesPerClick),
                                eq(clickUnderLimit));
        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEncryptionKeyFetchedStats_success() {
        String enrollmentId = "enrollmentId";
        String encryptionKeyUrl = "https://www.adtech1.com/.well-known/encryption-keys";

        AdServicesEncryptionKeyFetchedStats stats =
                AdServicesEncryptionKeyFetchedStats.builder()
                        .setFetchJobType(ENCRYPTION_KEY_DAILY_FETCH_JOB)
                        .setFetchStatus(IO_EXCEPTION)
                        .setIsFirstTimeFetch(false)
                        .setAdtechEnrollmentId(enrollmentId)
                        .setEncryptionKeyUrl(encryptionKeyUrl)
                        .build();

        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyBoolean(),
                                        anyString(),
                                        anyString(),
                                        anyString()));

        // Invoke logging call.
        mLogger.logEncryptionKeyFetchedStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENCRYPTION_KEY_FETCHED),
                                eq(ENCRYPTION_KEY_DAILY_FETCH_JOB.getValue()),
                                eq(IO_EXCEPTION.getValue()),
                                eq(false),
                                eq(enrollmentId),
                                eq(""),
                                eq(encryptionKeyUrl));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logEncryptionKeyDbTransactionEndedStats_success() {
        AdServicesEncryptionKeyDbTransactionEndedStats stats =
                AdServicesEncryptionKeyDbTransactionEndedStats.builder()
                        .setDbTransactionType(WRITE_TRANSACTION_TYPE)
                        .setDbTransactionStatus(INSERT_EXCEPTION)
                        .setMethodName(INSERT_KEY)
                        .build();

        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));

        // Invoke logging call.
        mLogger.logEncryptionKeyDbTransactionEndedStats(stats);

        // Verify only compat logging took place
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED),
                                eq(WRITE_TRANSACTION_TYPE.getValue()),
                                eq(INSERT_EXCEPTION.getValue()),
                                eq(INSERT_KEY.getValue()));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logDestinationRegisteredBeaconsReportedStats_tPlus_success() {
        // TODO: b/325098723 - Atoms using writeIntArray() crash the module on S- devices
        mockIsAtLeastT(true);
        List<DestinationRegisteredBeaconsReportedStats.InteractionKeySizeRangeType>
                keySizeRangeTypeList = Arrays.asList(
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .LARGER_THAN_MAXIMUM_KEY_SIZE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .SMALLER_THAN_MAXIMUM_KEY_SIZE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .EQUAL_TO_MAXIMUM_KEY_SIZE);
        int[] keySizeRangeTypeArray = new int[] {
                /* LARGER_THAN_MAXIMUM_KEY_SIZE */ 4,
                /* SMALLER_THAN_MAXIMUM_KEY_SIZE */ 2,
                /* EQUAL_TO_MAXIMUM_KEY_SIZE */ 3};

        DestinationRegisteredBeaconsReportedStats stats =
                DestinationRegisteredBeaconsReportedStats.builder()
                        .setBeaconReportingDestinationType(SELLER_DESTINATION)
                        .setAttemptedRegisteredBeacons(5)
                        .setAttemptedKeySizesRangeType(keySizeRangeTypeList)
                        .setTableNumRows(25)
                        .setAdServicesStatusCode(0)
                        .build();

        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), any(), anyInt(), anyInt()));

        // Invoke logging call.
        mLogger.logDestinationRegisteredBeaconsReportedStats(stats);

        // Verify only logging with T+ devices.
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(DESTINATION_REGISTERED_BEACONS),
                                eq(SELLER_DESTINATION),
                                eq(/* attemptedRegisteredBeacons */ 5),
                                eq(/* attemptedKeySizesRangeType */ keySizeRangeTypeArray),
                                eq(/* tableNumRows */ 25),
                                eq(/* adServicesStatusCode */ 0)
                        );

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logDestinationRegisteredBeaconsReportedStats_sMinus_emptyLogging() {
        // TODO: b/325098723 - Atoms using writeIntArray() crash the module on S- devices
        mockIsAtLeastT(false);
        List<DestinationRegisteredBeaconsReportedStats.InteractionKeySizeRangeType>
                keySizeRangeTypeList = Arrays.asList(
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .LARGER_THAN_MAXIMUM_KEY_SIZE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .SMALLER_THAN_MAXIMUM_KEY_SIZE,
                DestinationRegisteredBeaconsReportedStats
                        .InteractionKeySizeRangeType
                        .EQUAL_TO_MAXIMUM_KEY_SIZE);
        int[] keySizeRangeTypeArray = new int[] {
                /* LARGER_THAN_MAXIMUM_KEY_SIZE */ 4,
                /* SMALLER_THAN_MAXIMUM_KEY_SIZE */ 2,
                /* EQUAL_TO_MAXIMUM_KEY_SIZE */ 3};

        DestinationRegisteredBeaconsReportedStats stats =
                DestinationRegisteredBeaconsReportedStats.builder()
                        .setBeaconReportingDestinationType(SELLER_DESTINATION)
                        .setAttemptedRegisteredBeacons(5)
                        .setAttemptedKeySizesRangeType(keySizeRangeTypeList)
                        .setTableNumRows(25)
                        .setAdServicesStatusCode(0)
                        .build();

        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), any(), anyInt(), anyInt()));
        // Invoke logging call.
        mLogger.logDestinationRegisteredBeaconsReportedStats(stats);

        // Verify no logging action with S- devices.
        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logReportInteractionApiCalledStats_success() {
        ReportInteractionApiCalledStats stats =
                ReportInteractionApiCalledStats.builder()
                        .setBeaconReportingDestinationType(SELLER_DESTINATION)
                        .setNumMatchingUris(5)
                        .build();

        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt()));

        // Invoke logging call.
        mLogger.logReportInteractionApiCalledStats(stats);

        // Verify only compat logging took place.
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(REPORT_INTERACTION_API_CALLED),
                                eq(SELLER_DESTINATION),
                                eq(/* numMatchingUris */ 5)
                        );

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void logInteractionReportingTableClearedStats_success() {
        InteractionReportingTableClearedStats stats =
                InteractionReportingTableClearedStats.builder()
                        .setNumUrisCleared(25)
                        .setNumUnreportedUris(5)
                        .build();

        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt()));

        // Invoke logging call.
        mLogger.logInteractionReportingTableClearedStats(stats);

        // Verify only compat logging took place.
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(INTERACTION_REPORTING_TABLE_CLEARED),
                                eq(/* numUrisCleared */ 25),
                                eq(/* numUnreportedUris */ 5)
                        );

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogAppManifestConfigCall() {
        String pkgName = "pkg.I.am";
        @ApiType int apiType = AppManifestConfigCall.API_TOPICS;
        @Result int result = AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
        AppManifestConfigCall call = new AppManifestConfigCall(pkgName, apiType);
        call.result = result;
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyString(), anyInt(), anyInt()));

        mLogger.logAppManifestConfigCall(call);

        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                APP_MANIFEST_CONFIG_HELPER_CALLED, pkgName, apiType, result);
        verify(writeInvocation);
        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogKAnonSignStatus_success() {
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyBoolean(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));
        boolean wasSuccessful = true;
        int action = 0;
        int actionFailureReason = 0;
        int latency = 1000;
        int batchSize = 32;
        KAnonSignStatusStats kAnonSignStatusStats =
                KAnonSignStatusStats.builder()
                        .setKAnonAction(action)
                        .setKAnonActionFailureReason(actionFailureReason)
                        .setBatchSize(batchSize)
                        .setLatencyInMs(latency)
                        .setWasSuccessful(wasSuccessful)
                        .build();
        mLogger.logKAnonSignStats(kAnonSignStatusStats);

        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                K_ANON_SIGN_STATUS_REPORTED,
                                wasSuccessful,
                                action,
                                actionFailureReason,
                                batchSize,
                                latency);
        verify(writeInvocation);
        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogKAnonJoinStatus_success() {
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyBoolean(), anyInt(), anyInt(), anyInt()));
        boolean wasSuccessful = true;
        int latency = 1000;
        int numberOfFailedMessages = 32;
        int totalMessages = 100;
        KAnonJoinStatusStats kAnonSignStatusStats =
                KAnonJoinStatusStats.builder()
                        .setLatencyInMs(latency)
                        .setNumberOfFailedMessages(numberOfFailedMessages)
                        .setWasSuccessful(true)
                        .setTotalMessages(totalMessages)
                        .build();
        mLogger.logKAnonJoinStats(kAnonSignStatusStats);

        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                K_ANON_JOIN_STATUS_REPORTED,
                                wasSuccessful,
                                totalMessages,
                                numberOfFailedMessages,
                                latency);
        verify(writeInvocation);
        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogKAnonInitializeStats_success() {
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(),
                                        anyBoolean(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt(),
                                        anyInt()));
        boolean wasSuccessful = true;
        int action = 1;
        int actionFailureReason = 2;
        int latency = 122;
        KAnonInitializeStatusStats kAnonInitializeStatusStats =
                KAnonInitializeStatusStats.builder()
                        .setKAnonAction(1)
                        .setKAnonActionFailureReason(actionFailureReason)
                        .setLatencyInMs(latency)
                        .setWasSuccessful(wasSuccessful)
                        .build();
        mLogger.logKAnonInitializeStats(kAnonInitializeStatusStats);

        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                K_ANON_INITIALIZE_STATUS_REPORTED,
                                wasSuccessful,
                                action,
                                actionFailureReason,
                                latency);
        verify(writeInvocation);
        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogKanonBackgroundJobStats_success() {
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                                        anyInt()));
        int jobResult = 1;
        int totalMessagesAttempted = 12;
        int messagesFailedToJoin = 123;
        int messageFailedToSign = 19;
        int latency = 17;
        int messagesLeftInDb = 12356;
        KAnonBackgroundJobStatusStats kAnonBackgroundJobStatusStats =
                KAnonBackgroundJobStatusStats.builder()
                        .setKAnonJobResult(jobResult)
                        .setMessagesFailedToJoin(messagesFailedToJoin)
                        .setMessagesFailedToSign(messageFailedToSign)
                        .setMessagesInDBLeft(messagesLeftInDb)
                        .setTotalMessagesAttempted(totalMessagesAttempted)
                        .setLatencyInMs(latency)
                        .build();
        mLogger.logKAnonBackgroundJobStats(kAnonBackgroundJobStatusStats);

        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                K_ANON_BACKGROUND_JOB_STATUS_REPORTED,
                                jobResult,
                                totalMessagesAttempted,
                                messagesLeftInDb,
                                messagesFailedToJoin,
                                messageFailedToSign,
                                latency);
        verify(writeInvocation);
        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogKanonImmediateSignJoinStats_success() {
        doNothing()
                .when(
                        () ->
                                AdServicesStatsLog.write(
                                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                                        anyInt()));
        int jobResult = 1;
        int totalMessagesAttempted = 12;
        int messagesFailedToJoin = 123;
        int messageFailedToSign = 19;
        int latency = 17;
        KAnonImmediateSignJoinStatusStats kAnonImmediateSignJoinStatusStats =
                KAnonImmediateSignJoinStatusStats.builder()
                        .setKAnonJobResult(jobResult)
                        .setMessagesFailedToJoin(messagesFailedToJoin)
                        .setMessagesFailedToSign(messageFailedToSign)
                        .setTotalMessagesAttempted(totalMessagesAttempted)
                        .setLatencyInMs(latency)
                        .build();
        mLogger.logKAnonImmediateSignJoinStats(kAnonImmediateSignJoinStatusStats);

        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                K_ANON_IMMEDIATE_SIGN_JOIN_STATUS_REPORTED,
                                jobResult,
                                totalMessagesAttempted,
                                messagesFailedToJoin,
                                messageFailedToSign,
                                latency);
        verify(writeInvocation);
        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testLogKAnonGetChallengeStats_success() {
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt(), anyInt()));
        int jobResult = 1;
        int latency = 17;
        int challengeSizeInBytes = 100;
        KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats =
                KAnonGetChallengeStatusStats.builder()
                        .setCertificateSizeInBytes(challengeSizeInBytes)
                        .setResultCode(jobResult)
                        .setLatencyInMs(latency)
                        .build();

        mLogger.logKAnonGetChallengeJobStats(kAnonGetChallengeStatusStats);

        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                K_ANON_KEY_ATTESTATION_STATUS_REPORTED,
                                challengeSizeInBytes,
                                jobResult,
                                latency);
        verify(writeInvocation);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStats_success() {
        GetAdSelectionDataApiCalledStats stats =
                GetAdSelectionDataApiCalledStats.builder()
                        .setPayloadSizeKb(64)
                        .setNumBuyers(3)
                        .setStatusCode(STATUS_SUCCESS)
                        .build();

        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt()));

        // Invoke logging call.
        mLogger.logGetAdSelectionDataApiCalledStats(stats);

        // Verify only compat logging took place.
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(GET_AD_SELECTION_DATA_API_CALLED),
                                eq(64),
                                eq(3),
                                eq(STATUS_SUCCESS));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }

    @Test
    public void testlogGetAdSelectionDataBuyerInputGeneratedStats_success() {
        GetAdSelectionDataBuyerInputGeneratedStats stats =
                GetAdSelectionDataBuyerInputGeneratedStats.builder()
                        .setNumCustomAudiences(2)
                        .setNumCustomAudiencesOmitAds(1)
                        .setCustomAudienceSizeMeanB(23F)
                        .setCustomAudienceSizeVarianceB(24F)
                        .setTrustedBiddingSignalsKeysSizeMeanB(25F)
                        .setTrustedBiddingSignalsKeysSizeVarianceB(26F)
                        .setUserBiddingSignalsSizeMeanB(27F)
                        .setUserBiddingSignalsSizeVarianceB(28F)
                        .build();
        doNothing().when(() -> AdServicesStatsLog.write(anyInt(), anyInt(), anyInt()));

        // Invoke logging call.
        mLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);

        // Verify only compat logging took place.
        MockedVoidMethod writeInvocation =
                () ->
                        AdServicesStatsLog.write(
                                eq(GET_AD_SELECTION_DATA_BUYER_INPUT_GENERATED),
                                eq(2),
                                eq(1),
                                eq(23F),
                                eq(24F),
                                eq(25F),
                                eq(26F),
                                eq(27F),
                                eq(28F));

        verify(writeInvocation);

        verifyNoMoreInteractions(staticMockMarker(AdServicesStatsLog.class));
    }
}
