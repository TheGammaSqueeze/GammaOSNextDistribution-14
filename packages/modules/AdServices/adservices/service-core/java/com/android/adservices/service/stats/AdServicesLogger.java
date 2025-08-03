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

import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.stats.kanon.KAnonBackgroundJobStatusStats;
import com.android.adservices.service.stats.kanon.KAnonGetChallengeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonImmediateSignJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonInitializeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonSignStatusStats;

/** Interface for Adservices logger. */
public interface AdServicesLogger {
    /** log method for MeasurementReportsStats. */
    void logMeasurementReports(MeasurementReportsStats measurementReportsStats);

    /** log ApiCallStats which has stats about the API call such as the status. */
    void logApiCallStats(ApiCallStats apiCallStats);

    /** log UIStats which has stats about UI events. */
    void logUIStats(UIStats uiStats);

    /**
     * Logs API call stats specific to the FLEDGE APIs as an {@link ApiCallStats} object with app
     * package name, if enabled.
     */
    void logFledgeApiCallStats(int apiName, String appPackageName, int resultCode, int latencyMs);

    /** Logs API call stats specific to the FLEDGE APIs as an {@link ApiCallStats} object. */
    void logFledgeApiCallStats(int apiName, int resultCode, int latencyMs);

    /** Logs measurement registrations response size. */
    void logMeasurementRegistrationsResponseSize(MeasurementRegistrationResponseStats stats);

    /**
     * Logs the runAdSelection process stats as an {@link RunAdSelectionProcessReportedStats}
     * object.
     */
    void logRunAdSelectionProcessReportedStats(RunAdSelectionProcessReportedStats stats);

    /**
     * Logs the runAdBidding process stats as an {@link RunAdBiddingProcessReportedStats} object.
     */
    void logRunAdBiddingProcessReportedStats(RunAdBiddingProcessReportedStats stats);

    /**
     * Logs the runAdScoring process stats as an {@link RunAdScoringProcessReportedStats} object.
     */
    void logRunAdScoringProcessReportedStats(RunAdScoringProcessReportedStats stats);

    /**
     * Logs the runAdBiddingPerCA process stats as an {@link RunAdBiddingPerCAProcessReportedStats}
     * object.
     */
    void logRunAdBiddingPerCAProcessReportedStats(RunAdBiddingPerCAProcessReportedStats stats);

    /**
     * Logs the backgroundFetch process stats as an {@link BackgroundFetchProcessReportedStats}
     * object.
     */
    void logBackgroundFetchProcessReportedStats(BackgroundFetchProcessReportedStats stats);

    /**
     * Logs the updateCustomAudience process stats as an {@link
     * com.android.adservices.service.stats.UpdateCustomAudienceProcessReportedStats} objects.
     */
    void logUpdateCustomAudienceProcessReportedStats(
            UpdateCustomAudienceProcessReportedStats stats);

    /**
     * Logs GetTopics API call stats as an {@link
     * com.android.adservices.service.stats.GetTopicsReportedStats} object.
     */
    void logGetTopicsReportedStats(GetTopicsReportedStats stats);

    /**
     * Logs stats for getTopTopics as an {@link
     * com.android.adservices.service.stats.EpochComputationGetTopTopicsStats} object.
     */
    void logEpochComputationGetTopTopicsStats(EpochComputationGetTopTopicsStats stats);

    /**
     * Logs classifier stats during epoch computation as an {@link
     * com.android.adservices.service.stats.EpochComputationClassifierStats} object.
     */
    void logEpochComputationClassifierStats(EpochComputationClassifierStats stats);

    /** Logs measurement debug keys stats. */
    void logMeasurementDebugKeysMatch(MsmtDebugKeysMatchStats stats);

    /** Logs measurement AdID match for debug keys stats. */
    void logMeasurementAdIdMatchForDebugKeysStats(MsmtAdIdMatchForDebugKeysStats stats);

    /** Logs measurement attribution stats. */
    void logMeasurementAttributionStats(MeasurementAttributionStats measurementAttributionStats);

    /** Logs measurement wipeout stats. */
    void logMeasurementWipeoutStats(MeasurementWipeoutStats measurementWipeoutStats);

    /** Logs measurement delayed source registration stats. */
    void logMeasurementDelayedSourceRegistrationStats(
            MeasurementDelayedSourceRegistrationStats measurementDelayedSourceRegistrationStats);

    /** Logs measurement click verification stats. */
    void logMeasurementClickVerificationStats(
            MeasurementClickVerificationStats measurementClickVerificationStats);

    /** Logs enrollment data stats. */
    void logEnrollmentDataStats(int mType, boolean mIsSuccessful, int mBuildId);

    /** Logs enrollment matching stats. */
    void logEnrollmentMatchStats(boolean mIsSuccessful, int mBuildId);

    /** Logs enrollment file download stats. */
    void logEnrollmentFileDownloadStats(boolean mIsSuccessful, int mBuildId);

    /** Logs enrollment failure stats. */
    void logEnrollmentFailedStats(
            int mBuildId,
            int mDataFileGroupStatus,
            int mEnrollmentRecordCountInTable,
            String mQueryParameter,
            int mErrorCause);

    /** Logs encryption key fetch stats. */
    void logEncryptionKeyFetchedStats(AdServicesEncryptionKeyFetchedStats stats);

    /** Logs encryption key datastore transaction ended stats. */
    void logEncryptionKeyDbTransactionEndedStats(
            AdServicesEncryptionKeyDbTransactionEndedStats stats);

    /** Logs destinationRegisteredBeacon reported stats. */
    void logDestinationRegisteredBeaconsReportedStats(
            DestinationRegisteredBeaconsReportedStats stats);

    /** Logs beacon level reporting for ReportInteraction API called stats. */
    void logReportInteractionApiCalledStats(ReportInteractionApiCalledStats stats);

    /** Logs beacon level reporting for clearing interaction reporting table stats. */
    void logInteractionReportingTableClearedStats(InteractionReportingTableClearedStats stats);

    /** Logs call to {@link AppManifestConfigHelper} to check if app is allowed to access an API. */
    void logAppManifestConfigCall(AppManifestConfigCall call);

    /** Logs status for {@link com.android.adservices.service.kanon.KAnonSignJoinManager}. */
    void logKAnonSignJoinStatus();

    /**
     * Logs status for initialize method for {@link
     * com.android.adservices.service.kanon.KAnonCaller}.
     */
    void logKAnonInitializeStats(KAnonInitializeStatusStats kAnonInitializeStatusStats);

    /** Logs status for sign method for {@link com.android.adservices.service.kanon.KAnonCaller */
    void logKAnonSignStats(KAnonSignStatusStats kAnonSignStatusStats);

    /** Logs status for join method for {@link com.android.adservices.service.kanon.KAnonCaller} */
    void logKAnonJoinStats(KAnonJoinStatusStats kAnonJoinStatusStats);

    /**
     * Logs status for {@link
     * com.android.adservices.service.kanon.KAnonSignJoinBackgroundJobService}
     */
    void logKAnonBackgroundJobStats(KAnonBackgroundJobStatusStats kAnonBackgroundJobStatusStats);

    /**
     * Logs status for immediate sign join in {@link
     * com.android.adservices.service.kanon.KAnonSignJoinManager}
     */
    void logKAnonImmediateSignJoinStats(
            KAnonImmediateSignJoinStatusStats kAnonImmediateSignJoinStatusStats);

    /** Logs status for get challenge method during kAnon sign join process. */
    void logKAnonGetChallengeJobStats(KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats);

    /** Logs stats for GetAdSelectionDataApiCalled */
    void logGetAdSelectionDataApiCalledStats(GetAdSelectionDataApiCalledStats stats);

    /** Logs stats for GetAdSelectionDataBuyerInputGenerated */
    void logGetAdSelectionDataBuyerInputGeneratedStats(
            GetAdSelectionDataBuyerInputGeneratedStats stats);
}
