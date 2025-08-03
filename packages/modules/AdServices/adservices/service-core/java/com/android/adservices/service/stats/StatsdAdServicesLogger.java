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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_GET_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_FETCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_DATA_STORED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_MATCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_GET_TOP_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_GET_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.APP_MANIFEST_CONFIG_HELPER_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_FETCH_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.DESTINATION_REGISTERED_BEACONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.GET_AD_SELECTION_DATA_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.GET_AD_SELECTION_DATA_BUYER_INPUT_GENERATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.INTERACTION_REPORTING_TABLE_CLEARED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_BACKGROUND_JOB_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_IMMEDIATE_SIGN_JOIN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_INITIALIZE_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_KEY_ATTESTATION_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_SIGN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_JOIN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.REPORT_INTERACTION_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PER_CA_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SCORING_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SELECTION_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.UPDATE_CUSTOM_AUDIENCE_PROCESS_REPORTED;

import android.annotation.NonNull;
import android.util.proto.ProtoOutputStream;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.stats.kanon.KAnonBackgroundJobStatusStats;
import com.android.adservices.service.stats.kanon.KAnonGetChallengeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonImmediateSignJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonInitializeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonSignStatusStats;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/** {@link AdServicesLogger} that log stats to StatsD. */
@ThreadSafe
public class StatsdAdServicesLogger implements AdServicesLogger {
    private static final int AD_SERVICES_TOPIC_IDS_FIELD_ID = 1;

    @GuardedBy("SINGLETON_LOCK")
    private static volatile StatsdAdServicesLogger sStatsdAdServicesLogger;

    private static final Object SINGLETON_LOCK = new Object();

    @NonNull private final Flags mFlags;

    @VisibleForTesting
    protected StatsdAdServicesLogger(@NonNull Flags flags) {
        this.mFlags = Objects.requireNonNull(flags);
    }

    /** Returns an instance of {@link StatsdAdServicesLogger}. */
    public static StatsdAdServicesLogger getInstance() {
        if (sStatsdAdServicesLogger == null) {
            synchronized (SINGLETON_LOCK) {
                if (sStatsdAdServicesLogger == null) {
                    sStatsdAdServicesLogger = new StatsdAdServicesLogger(FlagsFactory.getFlags());
                }
            }
        }
        return sStatsdAdServicesLogger;
    }

    private String getAllowlistedAppPackageName(String appPackageName) {
        if (!mFlags.getMeasurementEnableAppPackageNameLogging()
                || !AllowLists.isPackageAllowListed(
                        mFlags.getMeasurementAppPackageNameLoggingAllowlist(), appPackageName)) {
            return "";
        }
        return appPackageName;
    }

    /** log method for measurement reporting. */
    public void logMeasurementReports(MeasurementReportsStats measurementReportsStats) {
        AdServicesStatsLog.write(
                measurementReportsStats.getCode(),
                measurementReportsStats.getType(),
                measurementReportsStats.getResultCode(),
                measurementReportsStats.getFailureType(),
                measurementReportsStats.getUploadMethod(),
                measurementReportsStats.getReportingDelay(),
                getAllowlistedAppPackageName(measurementReportsStats.getSourceRegistrant()),
                measurementReportsStats.getRetryCount(),
                /* httpResponseCode */ 0,
                /* isMarkedForDeletion */ false);
    }

    /** log method for API call stats. */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        AdServicesStatsLog.write(
                apiCallStats.getCode(),
                apiCallStats.getApiClass(),
                apiCallStats.getApiName(),
                apiCallStats.getAppPackageName(),
                apiCallStats.getSdkPackageName(),
                apiCallStats.getLatencyMillisecond(),
                apiCallStats.getResultCode());
    }

    /** log method for UI stats. */
    public void logUIStats(UIStats uiStats) {
        AdServicesStatsLog.write(
                uiStats.getCode(),
                uiStats.getRegion(),
                uiStats.getAction(),
                uiStats.getDefaultConsent(),
                uiStats.getDefaultAdIdState(),
                /* @deprecated feature_type= */ 0,
                uiStats.getUx(),
                uiStats.getEnrollmentChannel());
    }

    @Override
    public void logFledgeApiCallStats(
            int apiName, String appPackageName, int resultCode, int latencyMs) {
        boolean enabled = BinderFlagReader.readFlag(mFlags::getFledgeAppPackageNameLoggingEnabled);
        if (enabled && (appPackageName != null)) {
            AdServicesStatsLog.write(
                    AD_SERVICES_API_CALLED,
                    AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN,
                    apiName,
                    appPackageName,
                    "",
                    latencyMs,
                    resultCode);
        } else {
            logFledgeApiCallStats(apiName, resultCode, latencyMs);
        }
    }

    @Override
    public void logFledgeApiCallStats(int apiName, int resultCode, int latencyMs) {
        AdServicesStatsLog.write(
                AD_SERVICES_API_CALLED,
                AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN,
                apiName,
                "",
                "",
                latencyMs,
                resultCode);
    }

    @Override
    public void logMeasurementRegistrationsResponseSize(
            MeasurementRegistrationResponseStats stats) {
        AdServicesStatsLog.write(
                stats.getCode(),
                stats.getRegistrationType(),
                stats.getResponseSize(),
                stats.getAdTechDomain(),
                stats.getInteractionType(),
                stats.getSurfaceType(),
                stats.getRegistrationStatus(),
                stats.getFailureType(),
                stats.getRegistrationDelay(),
                getAllowlistedAppPackageName(stats.getSourceRegistrant()),
                stats.getRetryCount(),
                /* httpResponseCode */ 0,
                stats.isRedirectOnly());
    }

    @Override
    public void logRunAdSelectionProcessReportedStats(RunAdSelectionProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_SELECTION_PROCESS_REPORTED,
                stats.getIsRemarketingAdsWon(),
                stats.getDBAdSelectionSizeInBytes(),
                stats.getPersistAdSelectionLatencyInMillis(),
                stats.getPersistAdSelectionResultCode(),
                stats.getRunAdSelectionLatencyInMillis(),
                stats.getRunAdSelectionResultCode());
    }

    @Override
    public void logRunAdBiddingProcessReportedStats(RunAdBiddingProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_BIDDING_PROCESS_REPORTED,
                stats.getGetBuyersCustomAudienceLatencyInMills(),
                stats.getGetBuyersCustomAudienceResultCode(),
                stats.getNumBuyersRequested(),
                stats.getNumBuyersFetched(),
                stats.getNumOfAdsEnteringBidding(),
                stats.getNumOfCasEnteringBidding(),
                stats.getNumOfCasPostBidding(),
                stats.getRatioOfCasSelectingRmktAds(),
                stats.getRunAdBiddingLatencyInMillis(),
                stats.getRunAdBiddingResultCode(),
                stats.getTotalAdBiddingStageLatencyInMillis());
    }

    @Override
    public void logRunAdScoringProcessReportedStats(RunAdScoringProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_SCORING_PROCESS_REPORTED,
                stats.getGetAdSelectionLogicLatencyInMillis(),
                stats.getGetAdSelectionLogicResultCode(),
                stats.getGetAdSelectionLogicScriptType(),
                stats.getFetchedAdSelectionLogicScriptSizeInBytes(),
                stats.getGetTrustedScoringSignalsLatencyInMillis(),
                stats.getGetTrustedScoringSignalsResultCode(),
                stats.getFetchedTrustedScoringSignalsDataSizeInBytes(),
                stats.getScoreAdsLatencyInMillis(),
                stats.getGetAdScoresLatencyInMillis(),
                stats.getGetAdScoresResultCode(),
                stats.getNumOfCasEnteringScoring(),
                stats.getNumOfRemarketingAdsEnteringScoring(),
                stats.getNumOfContextualAdsEnteringScoring(),
                stats.getRunAdScoringLatencyInMillis(),
                stats.getRunAdScoringResultCode());
    }

    @Override
    public void logRunAdBiddingPerCAProcessReportedStats(
            RunAdBiddingPerCAProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_BIDDING_PER_CA_PROCESS_REPORTED,
                stats.getNumOfAdsForBidding(),
                stats.getRunAdBiddingPerCaLatencyInMillis(),
                stats.getRunAdBiddingPerCaResultCode(),
                stats.getGetBuyerDecisionLogicLatencyInMillis(),
                stats.getGetBuyerDecisionLogicResultCode(),
                stats.getBuyerDecisionLogicScriptType(),
                stats.getFetchedBuyerDecisionLogicScriptSizeInBytes(),
                stats.getNumOfKeysOfTrustedBiddingSignals(),
                stats.getFetchedTrustedBiddingSignalsDataSizeInBytes(),
                stats.getGetTrustedBiddingSignalsLatencyInMillis(),
                stats.getGetTrustedBiddingSignalsResultCode(),
                stats.getGenerateBidsLatencyInMillis(),
                stats.getRunBiddingLatencyInMillis(),
                stats.getRunBiddingResultCode());
    }

    @Override
    public void logBackgroundFetchProcessReportedStats(BackgroundFetchProcessReportedStats stats) {
        AdServicesStatsLog.write(
                BACKGROUND_FETCH_PROCESS_REPORTED,
                stats.getLatencyInMillis(),
                stats.getNumOfEligibleToUpdateCas(),
                stats.getResultCode());
    }

    @Override
    public void logUpdateCustomAudienceProcessReportedStats(
            UpdateCustomAudienceProcessReportedStats stats) {
        AdServicesStatsLog.write(
                UPDATE_CUSTOM_AUDIENCE_PROCESS_REPORTED,
                stats.getLatencyInMills(),
                stats.getResultCode(),
                stats.getDataSizeOfAdsInBytes(),
                stats.getNumOfAds());
    }

    @Override
    public void logGetTopicsReportedStats(GetTopicsReportedStats stats) {
        int[] topicIds = new int[] {};
        if (stats.getTopicIds() != null) {
            topicIds = stats.getTopicIds().stream().mapToInt(Integer::intValue).toArray();
        }

        boolean isCompatLoggingEnabled = !mFlags.getCompatLoggingKillSwitch();
        if (isCompatLoggingEnabled) {
            long modeBytesFieldId =
                    ProtoOutputStream.FIELD_COUNT_REPEATED // topic_ids field is repeated.
                            // topic_id is represented by int32 type.
                            | ProtoOutputStream.FIELD_TYPE_INT32
                            // Field ID of topic_ids field in AdServicesTopicIds proto.
                            | AD_SERVICES_TOPIC_IDS_FIELD_ID;

            AdServicesStatsLog.write(
                    AD_SERVICES_BACK_COMPAT_GET_TOPICS_REPORTED,
                    // TODO(b/266626836) Add topic ids logging once long term solution is identified
                    stats.getDuplicateTopicCount(),
                    stats.getFilteredBlockedTopicCount(),
                    stats.getTopicIdsCount(),
                    toBytes(modeBytesFieldId, topicIds));
        }

        // This atom can only be logged on T+ due to usage of repeated fields. See go/rbc-ww-logging
        // for why we are temporarily double logging on T+.s
        if (SdkLevel.isAtLeastT()) {
            AdServicesStatsLog.write(
                    AD_SERVICES_GET_TOPICS_REPORTED,
                    topicIds,
                    stats.getDuplicateTopicCount(),
                    stats.getFilteredBlockedTopicCount(),
                    stats.getTopicIdsCount());
        }
    }

    @Override
    public void logEpochComputationGetTopTopicsStats(EpochComputationGetTopTopicsStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_EPOCH_COMPUTATION_GET_TOP_TOPICS_REPORTED,
                stats.getTopTopicCount(),
                stats.getPaddedRandomTopicsCount(),
                stats.getAppsConsideredCount(),
                stats.getSdksConsideredCount());
    }

    @Override
    public void logEpochComputationClassifierStats(EpochComputationClassifierStats stats) {
        int[] topicIds = stats.getTopicIds().stream().mapToInt(Integer::intValue).toArray();

        boolean isCompatLoggingEnabled = !mFlags.getCompatLoggingKillSwitch();
        if (isCompatLoggingEnabled) {
            long modeBytesFieldId =
                    ProtoOutputStream.FIELD_COUNT_REPEATED // topic_ids field is repeated.
                            // topic_id is represented by int32 type.
                            | ProtoOutputStream.FIELD_TYPE_INT32
                            // Field ID of topic_ids field in AdServicesTopicIds proto.
                            | AD_SERVICES_TOPIC_IDS_FIELD_ID;

            AdServicesStatsLog.write(
                    AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED,
                    toBytes(modeBytesFieldId, topicIds),
                    stats.getBuildId(),
                    stats.getAssetVersion(),
                    stats.getClassifierType().getCompatLoggingValue(),
                    stats.getOnDeviceClassifierStatus().getCompatLoggingValue(),
                    stats.getPrecomputedClassifierStatus().getCompatLoggingValue());
        }

        // This atom can only be logged on T+ due to usage of repeated fields. See go/rbc-ww-logging
        // for why we are temporarily double logging on T+.
        if (SdkLevel.isAtLeastT()) {
            AdServicesStatsLog.write(
                    AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED,
                    topicIds,
                    stats.getBuildId(),
                    stats.getAssetVersion(),
                    stats.getClassifierType().getLoggingValue(),
                    stats.getOnDeviceClassifierStatus().getLoggingValue(),
                    stats.getPrecomputedClassifierStatus().getLoggingValue());
        }
    }

    @Override
    public void logMeasurementDebugKeysMatch(MsmtDebugKeysMatchStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_MEASUREMENT_DEBUG_KEYS,
                stats.getAdTechEnrollmentId(),
                stats.getAttributionType(),
                stats.isMatched(),
                stats.getDebugJoinKeyHashedValue(),
                stats.getDebugJoinKeyHashLimit(),
                getAllowlistedAppPackageName(stats.getSourceRegistrant()));
    }

    @Override
    public void logMeasurementAdIdMatchForDebugKeysStats(MsmtAdIdMatchForDebugKeysStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS,
                stats.getAdTechEnrollmentId(),
                stats.getAttributionType(),
                stats.isMatched(),
                stats.getNumUniqueAdIds(),
                stats.getNumUniqueAdIdsLimit(),
                getAllowlistedAppPackageName(stats.getSourceRegistrant()));
    }

    /** log method for measurement attribution. */
    public void logMeasurementAttributionStats(
            MeasurementAttributionStats measurementAttributionStats) {
        AdServicesStatsLog.write(
                measurementAttributionStats.getCode(),
                measurementAttributionStats.getSourceType(),
                measurementAttributionStats.getSurfaceType(),
                measurementAttributionStats.getResult(),
                measurementAttributionStats.getFailureType(),
                measurementAttributionStats.isSourceDerived(),
                measurementAttributionStats.isInstallAttribution(),
                measurementAttributionStats.getAttributionDelay(),
                getAllowlistedAppPackageName(measurementAttributionStats.getSourceRegistrant()),
                measurementAttributionStats.getAggregateReportCount(),
                measurementAttributionStats.getAggregateDebugReportCount(),
                measurementAttributionStats.getEventReportCount(),
                measurementAttributionStats.getEventDebugReportCount(),
                /* retryCount */ 0);
    }

    /** log method for measurement wipeout. */
    public void logMeasurementWipeoutStats(MeasurementWipeoutStats measurementWipeoutStats) {
        AdServicesStatsLog.write(
                measurementWipeoutStats.getCode(),
                measurementWipeoutStats.getWipeoutType(),
                getAllowlistedAppPackageName(measurementWipeoutStats.getSourceRegistrant()));
    }

    /** log method for measurement attribution. */
    public void logMeasurementDelayedSourceRegistrationStats(
            MeasurementDelayedSourceRegistrationStats measurementDelayedSourceRegistrationStats) {
        AdServicesStatsLog.write(
                measurementDelayedSourceRegistrationStats.getCode(),
                measurementDelayedSourceRegistrationStats.getRegistrationStatus(),
                measurementDelayedSourceRegistrationStats.getRegistrationDelay(),
                getAllowlistedAppPackageName(
                        measurementDelayedSourceRegistrationStats.getRegistrant()));
    }

    /** Log method for measurement click verification. */
    public void logMeasurementClickVerificationStats(
            MeasurementClickVerificationStats measurementClickVerificationStats) {
        AdServicesStatsLog.write(
                AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION,
                measurementClickVerificationStats.getSourceType(),
                measurementClickVerificationStats.isInputEventPresent(),
                measurementClickVerificationStats.isSystemClickVerificationSuccessful(),
                measurementClickVerificationStats.isSystemClickVerificationEnabled(),
                measurementClickVerificationStats.getInputEventDelayMillis(),
                measurementClickVerificationStats.getValidDelayWindowMillis(),
                getAllowlistedAppPackageName(
                        measurementClickVerificationStats.getSourceRegistrant()),
                measurementClickVerificationStats.isClickDeduplicationEnabled(),
                measurementClickVerificationStats.isClickDeduplicationEnforced(),
                measurementClickVerificationStats.getMaxSourcesPerClick(),
                measurementClickVerificationStats
                        .isCurrentRegistrationUnderClickDeduplicationLimit());
    }

    /** log method for consent migrations. */
    public void logConsentMigrationStats(ConsentMigrationStats stats) {
        if (mFlags.getAdservicesConsentMigrationLoggingEnabled()) {
            AdServicesStatsLog.write(
                    AD_SERVICES_CONSENT_MIGRATED,
                    stats.getMsmtConsent(),
                    stats.getTopicsConsent(),
                    stats.getFledgeConsent(),
                    true,
                    stats.getMigrationType().getMigrationTypeValue(),
                    stats.getRegion(),
                    stats.getMigrationStatus().getMigrationStatusValue());
        }
    }

    /** log method for read/write status of enrollment data. */
    public void logEnrollmentDataStats(int mType, boolean mIsSuccessful, int mBuildId) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENROLLMENT_DATA_STORED, mType, mIsSuccessful, mBuildId);
    }

    /** log method for status of enrollment matching queries. */
    public void logEnrollmentMatchStats(boolean mIsSuccessful, int mBuildId) {
        AdServicesStatsLog.write(AD_SERVICES_ENROLLMENT_MATCHED, mIsSuccessful, mBuildId);
    }

    /** log method for status of enrollment downloads. */
    public void logEnrollmentFileDownloadStats(boolean mIsSuccessful, int mBuildId) {
        AdServicesStatsLog.write(AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED, mIsSuccessful, mBuildId);
    }

    /** log method for enrollment-related status_caller_not_found errors. */
    public void logEnrollmentFailedStats(
            int mBuildId,
            int mDataFileGroupStatus,
            int mEnrollmentRecordCountInTable,
            String mQueryParameter,
            int mErrorCause) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENROLLMENT_FAILED,
                mBuildId,
                mDataFileGroupStatus,
                mEnrollmentRecordCountInTable,
                mQueryParameter,
                mErrorCause);
    }

    /** Logs encryption key fetch stats. */
    @Override
    public void logEncryptionKeyFetchedStats(AdServicesEncryptionKeyFetchedStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENCRYPTION_KEY_FETCHED,
                stats.getFetchJobType().getValue(),
                stats.getFetchStatus().getValue(),
                stats.getIsFirstTimeFetch(),
                stats.getAdtechEnrollmentId(),
                "",
                stats.getEncryptionKeyUrl());
    }

    /** Logs encryption key datastore transaction ended stats. */
    @Override
    public void logEncryptionKeyDbTransactionEndedStats(
            AdServicesEncryptionKeyDbTransactionEndedStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED,
                stats.getDbTransactionType().getValue(),
                stats.getDbTransactionStatus().getValue(),
                stats.getMethodName().getValue());
    }

    /** Logs destinationRegisteredBeacon reported stats. */
    @Override
    public void logDestinationRegisteredBeaconsReportedStats(
            DestinationRegisteredBeaconsReportedStats stats) {
        int[] attemptedKeySizesRangeType = new int[] {};
        if (stats.getAttemptedKeySizesRangeType() != null) {
            attemptedKeySizesRangeType =
                    stats.getAttemptedKeySizesRangeType().stream()
                            .mapToInt(
                                    DestinationRegisteredBeaconsReportedStats
                                                    .InteractionKeySizeRangeType
                                            ::getValue)
                            .toArray();
        }
        // TODO: b/325098723 - Add support for DestinationRegisteredBeacons for S- devices
        if (SdkLevel.isAtLeastT()) {
            AdServicesStatsLog.write(
                    DESTINATION_REGISTERED_BEACONS,
                    stats.getBeaconReportingDestinationType(),
                    stats.getAttemptedRegisteredBeacons(),
                    attemptedKeySizesRangeType,
                    stats.getTableNumRows(),
                    stats.getAdServicesStatusCode());
        }
    }

    /** Logs beacon level reporting for ReportInteraction API called stats. */
    @Override
    public void logReportInteractionApiCalledStats(ReportInteractionApiCalledStats stats) {
        AdServicesStatsLog.write(
                REPORT_INTERACTION_API_CALLED,
                stats.getBeaconReportingDestinationType(),
                stats.getNumMatchingUris());
    }

    /** Logs beacon level reporting for clearing interaction reporting table stats. */
    @Override
    public void logInteractionReportingTableClearedStats(
            InteractionReportingTableClearedStats stats) {
        AdServicesStatsLog.write(
                INTERACTION_REPORTING_TABLE_CLEARED,
                stats.getNumUrisCleared(),
                stats.getNumUnreportedUris());
    }

    @Override
    public void logAppManifestConfigCall(AppManifestConfigCall call) {
        AdServicesStatsLog.write(
                APP_MANIFEST_CONFIG_HELPER_CALLED, call.packageName, call.api, call.result);
    }

    @Override
    public void logKAnonSignJoinStatus() {
        // TODO(b/324564459): add logging for KAnon Sign Join
    }

    @Override
    public void logKAnonInitializeStats(KAnonInitializeStatusStats kAnonInitializeStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_INITIALIZE_STATUS_REPORTED,
                kAnonInitializeStatusStats.getWasSuccessful(),
                kAnonInitializeStatusStats.getKAnonAction(),
                kAnonInitializeStatusStats.getKAnonActionFailureReason(),
                kAnonInitializeStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonSignStats(KAnonSignStatusStats kAnonSignStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_SIGN_STATUS_REPORTED,
                kAnonSignStatusStats.getWasSuccessful(),
                kAnonSignStatusStats.getKAnonAction(),
                kAnonSignStatusStats.getKAnonActionFailureReason(),
                kAnonSignStatusStats.getBatchSize(),
                kAnonSignStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonJoinStats(KAnonJoinStatusStats kAnonJoinStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_JOIN_STATUS_REPORTED,
                kAnonJoinStatusStats.getWasSuccessful(),
                kAnonJoinStatusStats.getTotalMessages(),
                kAnonJoinStatusStats.getNumberOfFailedMessages(),
                kAnonJoinStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonBackgroundJobStats(
            KAnonBackgroundJobStatusStats kAnonBackgroundJobStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_BACKGROUND_JOB_STATUS_REPORTED,
                kAnonBackgroundJobStatusStats.getKAnonJobResult(),
                kAnonBackgroundJobStatusStats.getTotalMessagesAttempted(),
                kAnonBackgroundJobStatusStats.getMessagesInDBLeft(),
                kAnonBackgroundJobStatusStats.getMessagesFailedToJoin(),
                kAnonBackgroundJobStatusStats.getMessagesFailedToSign(),
                kAnonBackgroundJobStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonImmediateSignJoinStats(
            KAnonImmediateSignJoinStatusStats kAnonImmediateSignJoinStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_IMMEDIATE_SIGN_JOIN_STATUS_REPORTED,
                kAnonImmediateSignJoinStatusStats.getKAnonJobResult(),
                kAnonImmediateSignJoinStatusStats.getTotalMessagesAttempted(),
                kAnonImmediateSignJoinStatusStats.getMessagesFailedToJoin(),
                kAnonImmediateSignJoinStatusStats.getMessagesFailedToSign(),
                kAnonImmediateSignJoinStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonGetChallengeJobStats(
            KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_KEY_ATTESTATION_STATUS_REPORTED,
                kAnonGetChallengeStatusStats.getCertificateSizeInBytes(),
                kAnonGetChallengeStatusStats.getResultCode(),
                kAnonGetChallengeStatusStats.getLatencyInMs());
    }

    @Override
    public void logGetAdSelectionDataApiCalledStats(GetAdSelectionDataApiCalledStats stats) {
        AdServicesStatsLog.write(
                GET_AD_SELECTION_DATA_API_CALLED,
                stats.getPayloadSizeKb(),
                stats.getNumBuyers(),
                stats.getStatusCode());
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            GetAdSelectionDataBuyerInputGeneratedStats stats) {
        AdServicesStatsLog.write(
                GET_AD_SELECTION_DATA_BUYER_INPUT_GENERATED,
                stats.getNumCustomAudiences(),
                stats.getNumCustomAudiencesOmitAds(),
                stats.getCustomAudienceSizeMeanB(),
                stats.getCustomAudienceSizeVarianceB(),
                stats.getTrustedBiddingSignalsKeysSizeMeanB(),
                stats.getTrustedBiddingSignalsKeysSizeVarianceB(),
                stats.getUserBiddingSignalsSizeMeanB(),
                stats.getUserBiddingSignalsSizeVarianceB());
    }

    @NonNull
    private byte[] toBytes(long fieldId, @NonNull int[] values) {
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();
        for (int value : values) {
            protoOutputStream.write(fieldId, value);
        }
        return protoOutputStream.getBytes();
    }
}
