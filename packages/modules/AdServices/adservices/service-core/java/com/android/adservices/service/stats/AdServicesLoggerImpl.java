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

import android.annotation.Nullable;

import com.android.adservices.cobalt.AppNameApiErrorLogger;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.stats.kanon.KAnonBackgroundJobStatusStats;
import com.android.adservices.service.stats.kanon.KAnonGetChallengeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonImmediateSignJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonInitializeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonSignStatusStats;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

import javax.annotation.concurrent.ThreadSafe;

/** AdServicesLogger that delegate to the appropriate Logger Implementations. */
@ThreadSafe
public final class AdServicesLoggerImpl implements AdServicesLogger {

    private static volatile AdServicesLoggerImpl sAdServicesLogger;
    private final StatsdAdServicesLogger mStatsdAdServicesLogger;
    @Nullable private AppNameApiErrorLogger mAppNameApiErrorLogger;

    private AdServicesLoggerImpl() {
        this(StatsdAdServicesLogger.getInstance());
    }

    @VisibleForTesting
    AdServicesLoggerImpl(StatsdAdServicesLogger statsdAdServicesLogger) {
        mStatsdAdServicesLogger = statsdAdServicesLogger;
    }

    /** Returns an instance of AdServicesLogger. */
    public static AdServicesLoggerImpl getInstance() {
        if (sAdServicesLogger == null) {
            synchronized (AdServicesLoggerImpl.class) {
                if (sAdServicesLogger == null) {
                    sAdServicesLogger = new AdServicesLoggerImpl();
                }
            }
        }
        return sAdServicesLogger;
    }

    @Override
    public void logMeasurementReports(MeasurementReportsStats measurementReportsStats) {
        mStatsdAdServicesLogger.logMeasurementReports(measurementReportsStats);
    }

    @Override
    public void logApiCallStats(ApiCallStats apiCallStats) {
        mStatsdAdServicesLogger.logApiCallStats(apiCallStats);

        cobaltLogAppNameApiError(
                apiCallStats.getAppPackageName(),
                apiCallStats.getApiName(),
                apiCallStats.getResultCode());
    }

    @Override
    public void logUIStats(UIStats uiStats) {
        mStatsdAdServicesLogger.logUIStats(uiStats);
    }

    @Override
    public void logFledgeApiCallStats(int apiName, int resultCode, int latencyMs) {
        mStatsdAdServicesLogger.logFledgeApiCallStats(apiName, resultCode, latencyMs);
        // TODO(b/324155747): Add Cobalt app name api error logging.
    }

    @Override
    public void logFledgeApiCallStats(
            int apiName, String appPackageName, int resultCode, int latencyMs) {
        mStatsdAdServicesLogger.logFledgeApiCallStats(
                apiName, appPackageName, resultCode, latencyMs);
        // TODO(b/324155747): Add Cobalt app name api error logging.
    }

    @Override
    public void logMeasurementRegistrationsResponseSize(
            MeasurementRegistrationResponseStats stats) {
        mStatsdAdServicesLogger.logMeasurementRegistrationsResponseSize(stats);
    }

    @Override
    public void logRunAdSelectionProcessReportedStats(RunAdSelectionProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdSelectionProcessReportedStats(stats);
    }

    @Override
    public void logRunAdBiddingProcessReportedStats(RunAdBiddingProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdBiddingProcessReportedStats(stats);
    }

    @Override
    public void logRunAdScoringProcessReportedStats(RunAdScoringProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdScoringProcessReportedStats(stats);
    }

    @Override
    public void logRunAdBiddingPerCAProcessReportedStats(
            RunAdBiddingPerCAProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdBiddingPerCAProcessReportedStats(stats);
    }

    @Override
    public void logBackgroundFetchProcessReportedStats(BackgroundFetchProcessReportedStats stats) {
        mStatsdAdServicesLogger.logBackgroundFetchProcessReportedStats(stats);
    }

    @Override
    public void logUpdateCustomAudienceProcessReportedStats(
            UpdateCustomAudienceProcessReportedStats stats) {
        mStatsdAdServicesLogger.logUpdateCustomAudienceProcessReportedStats(stats);
    }

    @Override
    public void logGetTopicsReportedStats(GetTopicsReportedStats stats) {
        mStatsdAdServicesLogger.logGetTopicsReportedStats(stats);
    }

    @Override
    public void logEpochComputationGetTopTopicsStats(EpochComputationGetTopTopicsStats stats) {
        mStatsdAdServicesLogger.logEpochComputationGetTopTopicsStats(stats);
    }

    @Override
    public void logEpochComputationClassifierStats(EpochComputationClassifierStats stats) {
        mStatsdAdServicesLogger.logEpochComputationClassifierStats(stats);
    }

    @Override
    public void logMeasurementDebugKeysMatch(MsmtDebugKeysMatchStats stats) {
        mStatsdAdServicesLogger.logMeasurementDebugKeysMatch(stats);
    }

    @Override
    public void logMeasurementAdIdMatchForDebugKeysStats(MsmtAdIdMatchForDebugKeysStats stats) {
        mStatsdAdServicesLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);
    }

    @Override
    public void logMeasurementAttributionStats(
            MeasurementAttributionStats measurementAttributionStats) {
        mStatsdAdServicesLogger.logMeasurementAttributionStats(measurementAttributionStats);
    }

    @Override
    public void logMeasurementWipeoutStats(MeasurementWipeoutStats measurementWipeoutStats) {
        mStatsdAdServicesLogger.logMeasurementWipeoutStats(measurementWipeoutStats);
    }

    @Override
    public void logMeasurementDelayedSourceRegistrationStats(
            MeasurementDelayedSourceRegistrationStats measurementDelayedSourceRegistrationStats) {
        mStatsdAdServicesLogger.logMeasurementDelayedSourceRegistrationStats(
                measurementDelayedSourceRegistrationStats);
    }

    @Override
    public void logMeasurementClickVerificationStats(
            MeasurementClickVerificationStats measurementClickVerificationStats) {
        mStatsdAdServicesLogger.logMeasurementClickVerificationStats(
                measurementClickVerificationStats);
    }

    @Override
    public void logEnrollmentDataStats(int mType, boolean mIsSuccessful, int mBuildId) {
        mStatsdAdServicesLogger.logEnrollmentDataStats(mType, mIsSuccessful, mBuildId);
    }

    @Override
    public void logEnrollmentMatchStats(boolean mIsSuccessful, int mBuildId) {
        mStatsdAdServicesLogger.logEnrollmentMatchStats(mIsSuccessful, mBuildId);
    }

    @Override
    public void logEnrollmentFileDownloadStats(boolean mIsSuccessful, int mBuildId) {
        mStatsdAdServicesLogger.logEnrollmentFileDownloadStats(mIsSuccessful, mBuildId);
    }

    @Override
    public void logEnrollmentFailedStats(
            int mBuildId,
            int mDataFileGroupStatus,
            int mEnrollmentRecordCountInTable,
            String mQueryParameter,
            int mErrorCause) {
        mStatsdAdServicesLogger.logEnrollmentFailedStats(
                mBuildId,
                mDataFileGroupStatus,
                mEnrollmentRecordCountInTable,
                mQueryParameter,
                mErrorCause);
    }

    /** Logs encryption key fetch stats. */
    @Override
    public void logEncryptionKeyFetchedStats(AdServicesEncryptionKeyFetchedStats stats) {
        mStatsdAdServicesLogger.logEncryptionKeyFetchedStats(stats);
    }

    /** Logs encryption key datastore transaction ended stats. */
    @Override
    public void logEncryptionKeyDbTransactionEndedStats(
            AdServicesEncryptionKeyDbTransactionEndedStats stats) {
        mStatsdAdServicesLogger.logEncryptionKeyDbTransactionEndedStats(stats);
    }

    /** Logs destinationRegisteredBeacon reported stats. */
    @Override
    public void logDestinationRegisteredBeaconsReportedStats(
            DestinationRegisteredBeaconsReportedStats stats) {
        mStatsdAdServicesLogger.logDestinationRegisteredBeaconsReportedStats(stats);
    }

    /** Logs beacon level reporting for ReportInteraction API called stats. */
    @Override
    public void logReportInteractionApiCalledStats(ReportInteractionApiCalledStats stats) {
        mStatsdAdServicesLogger.logReportInteractionApiCalledStats(stats);
    }

    /** Logs beacon level reporting for clearing interaction reporting table stats. */
    @Override
    public void logInteractionReportingTableClearedStats(
            InteractionReportingTableClearedStats stats) {
        mStatsdAdServicesLogger.logInteractionReportingTableClearedStats(stats);
    }

    @Override
    public void logAppManifestConfigCall(AppManifestConfigCall call) {
        mStatsdAdServicesLogger.logAppManifestConfigCall(call);
    }

    @Override
    public void logKAnonSignJoinStatus() {
        // TODO(b/324564459): add logging for KAnon Sign Join
    }

    @Override
    public void logKAnonInitializeStats(KAnonInitializeStatusStats kAnonInitializeStatusStats) {
        mStatsdAdServicesLogger.logKAnonInitializeStats(kAnonInitializeStatusStats);
    }

    @Override
    public void logKAnonSignStats(KAnonSignStatusStats kAnonSignStatusStats) {
        mStatsdAdServicesLogger.logKAnonSignStats(kAnonSignStatusStats);
    }

    @Override
    public void logKAnonJoinStats(KAnonJoinStatusStats kAnonJoinStatusStats) {
        mStatsdAdServicesLogger.logKAnonJoinStats(kAnonJoinStatusStats);
    }

    @Override
    public void logKAnonBackgroundJobStats(
            KAnonBackgroundJobStatusStats kAnonBackgroundJobStatusStats) {
        mStatsdAdServicesLogger.logKAnonBackgroundJobStats(kAnonBackgroundJobStatusStats);
    }

    @Override
    public void logKAnonGetChallengeJobStats(
            KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats) {
        mStatsdAdServicesLogger.logKAnonGetChallengeJobStats(kAnonGetChallengeStatusStats);
    }

    @Override
    public void logKAnonImmediateSignJoinStats(
            KAnonImmediateSignJoinStatusStats kAnonImmediateSignJoinStatusStats) {
        mStatsdAdServicesLogger.logKAnonImmediateSignJoinStats(kAnonImmediateSignJoinStatusStats);
    }

    @Override
    public void logGetAdSelectionDataApiCalledStats(GetAdSelectionDataApiCalledStats stats) {
        mStatsdAdServicesLogger.logGetAdSelectionDataApiCalledStats(stats);
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            GetAdSelectionDataBuyerInputGeneratedStats stats) {
        mStatsdAdServicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);
    }

    /** Logs api call error status using {@code CobaltLogger}. */
    private void cobaltLogAppNameApiError(String appPackageName, int apiName, int errorCode) {
        mAppNameApiErrorLogger =
                AppNameApiErrorLogger.getInstance(
                        ApplicationContextSingleton.get(), FlagsFactory.getFlags());
        if (mAppNameApiErrorLogger != null) {
            mAppNameApiErrorLogger.logErrorOccurrence(appPackageName, apiName, errorCode);
        }
    }
}
