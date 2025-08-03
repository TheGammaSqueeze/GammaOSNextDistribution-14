/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.stats.kanon.KAnonBackgroundJobStatusStats;
import com.android.adservices.service.stats.kanon.KAnonGetChallengeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonImmediateSignJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonInitializeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonSignStatusStats;

/** No-op version of {@link AdServicesLogger}. */
public class NoOpLoggerImpl implements AdServicesLogger {
    @Override
    public void logMeasurementReports(MeasurementReportsStats measurementReportsStats) {}

    @Override
    public void logApiCallStats(ApiCallStats apiCallStats) {}

    @Override
    public void logUIStats(UIStats uiStats) {}

    @Override
    public void logFledgeApiCallStats(
            int apiName, String appPackageName, int resultCode, int latencyMs) {}

    @Override
    public void logFledgeApiCallStats(int apiName, int resultCode, int latencyMs) {}

    @Override
    public void logMeasurementRegistrationsResponseSize(
            MeasurementRegistrationResponseStats stats) {}

    @Override
    public void logRunAdSelectionProcessReportedStats(RunAdSelectionProcessReportedStats stats) {}

    @Override
    public void logRunAdBiddingProcessReportedStats(RunAdBiddingProcessReportedStats stats) {}

    @Override
    public void logRunAdScoringProcessReportedStats(RunAdScoringProcessReportedStats stats) {}

    @Override
    public void logRunAdBiddingPerCAProcessReportedStats(
            RunAdBiddingPerCAProcessReportedStats stats) {}

    @Override
    public void logBackgroundFetchProcessReportedStats(BackgroundFetchProcessReportedStats stats) {}

    @Override
    public void logUpdateCustomAudienceProcessReportedStats(
            UpdateCustomAudienceProcessReportedStats stats) {}

    @Override
    public void logGetTopicsReportedStats(GetTopicsReportedStats stats) {}

    @Override
    public void logEpochComputationGetTopTopicsStats(EpochComputationGetTopTopicsStats stats) {}

    @Override
    public void logEpochComputationClassifierStats(EpochComputationClassifierStats stats) {}

    @Override
    public void logMeasurementDebugKeysMatch(MsmtDebugKeysMatchStats stats) {}

    @Override
    public void logMeasurementAdIdMatchForDebugKeysStats(MsmtAdIdMatchForDebugKeysStats stats) {}

    @Override
    public void logMeasurementAttributionStats(
            MeasurementAttributionStats measurementAttributionStats) {}

    @Override
    public void logMeasurementWipeoutStats(MeasurementWipeoutStats measurementWipeoutStats) {}

    @Override
    public void logMeasurementDelayedSourceRegistrationStats(
            MeasurementDelayedSourceRegistrationStats measurementDelayedSourceRegistrationStats) {}

    @Override
    public void logMeasurementClickVerificationStats(
            MeasurementClickVerificationStats measurementClickVerificationStats) {}

    @Override
    public void logEnrollmentDataStats(int mType, boolean mIsSuccessful, int mBuildId) {}

    @Override
    public void logEnrollmentMatchStats(boolean mIsSuccessful, int mBuildId) {}

    @Override
    public void logEnrollmentFileDownloadStats(boolean mIsSuccessful, int mBuildId) {}

    @Override
    public void logEnrollmentFailedStats(
            int mBuildId,
            int mDataFileGroupStatus,
            int mEnrollmentRecordCountInTable,
            String mQueryParameter,
            int mErrorCause) {}

    @Override
    public void logEncryptionKeyFetchedStats(AdServicesEncryptionKeyFetchedStats stats) {}

    @Override
    public void logEncryptionKeyDbTransactionEndedStats(
            AdServicesEncryptionKeyDbTransactionEndedStats stats) {}

    @Override
    public void logDestinationRegisteredBeaconsReportedStats(
            DestinationRegisteredBeaconsReportedStats stats) {}

    @Override
    public void logReportInteractionApiCalledStats(ReportInteractionApiCalledStats stats) {}

    @Override
    public void logInteractionReportingTableClearedStats(
            InteractionReportingTableClearedStats stats) {}

    @Override
    public void logGetAdSelectionDataApiCalledStats(GetAdSelectionDataApiCalledStats stats) {

    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            GetAdSelectionDataBuyerInputGeneratedStats stats) {
    }

    @Override
    public void logAppManifestConfigCall(AppManifestConfigCall call) {}

    @Override
    public void logKAnonSignJoinStatus() {}

    @Override
    public void logKAnonInitializeStats(KAnonInitializeStatusStats kAnonInitializeStatusStats) {}

    @Override
    public void logKAnonSignStats(KAnonSignStatusStats kAnonSignStatusStats) {}

    @Override
    public void logKAnonJoinStats(KAnonJoinStatusStats kAnonJoinStatusStats) {}

    @Override
    public void logKAnonBackgroundJobStats(
            KAnonBackgroundJobStatusStats kAnonBackgroundJobStatusStats) {}

    @Override
    public void logKAnonImmediateSignJoinStats(
            KAnonImmediateSignJoinStatusStats kAnonImmediateSignJoinStatusStats) {}

    @Override
    public void logKAnonGetChallengeJobStats(
            KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats) {}
}
