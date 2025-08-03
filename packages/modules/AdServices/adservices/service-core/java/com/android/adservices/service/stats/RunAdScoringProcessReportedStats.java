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

import com.google.auto.value.AutoValue;

/** Class for runAdScoring process reported stats. */
@AutoValue
public abstract class RunAdScoringProcessReportedStats {
    /** @return getAdSelectionLogic latency in milliseconds. */
    public abstract int getGetAdSelectionLogicLatencyInMillis();

    /** @return getAdSelectionLogic result code. */
    public abstract int getGetAdSelectionLogicResultCode();

    /** @return getAdSelectionLogic script type. */
    public abstract int getGetAdSelectionLogicScriptType();

    /** @return fetched ad selection logic script size in bytes. */
    public abstract int getFetchedAdSelectionLogicScriptSizeInBytes();

    /** @return getTrustedScoringSignals latency in milliseconds. */
    public abstract int getGetTrustedScoringSignalsLatencyInMillis();

    /** @return getTrustedScoringSignals result code. */
    public abstract int getGetTrustedScoringSignalsResultCode();

    /** @return fetched trusted scoring signals data size in bytes. */
    public abstract int getFetchedTrustedScoringSignalsDataSizeInBytes();

    /** @return the total scoreAds script execution time when getAdScores() is called. */
    public abstract int getScoreAdsLatencyInMillis();

    /** @return the overall latency of the getAdScores(). */
    public abstract int getGetAdScoresLatencyInMillis();

    /** @return the getAdScores result code. */
    public abstract int getGetAdScoresResultCode();

    /** @return the num of CAs entered scoring. */
    public abstract int getNumOfCasEnteringScoring();

    /** @return the num of remarketing ads entered scoring. */
    public abstract int getNumOfRemarketingAdsEnteringScoring();

    /** @return the num of contextual ads entered scoring. */
    public abstract int getNumOfContextualAdsEnteringScoring();

    /** @return the overall latency of the runAdScoring process. */
    public abstract int getRunAdScoringLatencyInMillis();

    /** @return the runAdScoring result code. */
    public abstract int getRunAdScoringResultCode();

    /** @return generic builder. */
    static Builder builder() {
        return new AutoValue_RunAdScoringProcessReportedStats.Builder();
    }

    /** Builder class for RunAdScoringProcessReportedStats. */
    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setGetAdSelectionLogicLatencyInMillis(int value);

        abstract Builder setGetAdSelectionLogicResultCode(int value);

        abstract Builder setGetAdSelectionLogicScriptType(int value);

        abstract Builder setFetchedAdSelectionLogicScriptSizeInBytes(int value);

        abstract Builder setGetTrustedScoringSignalsLatencyInMillis(int value);

        abstract Builder setGetTrustedScoringSignalsResultCode(int value);

        abstract Builder setFetchedTrustedScoringSignalsDataSizeInBytes(int value);

        abstract Builder setScoreAdsLatencyInMillis(int value);

        abstract Builder setGetAdScoresLatencyInMillis(int value);

        abstract Builder setGetAdScoresResultCode(int value);

        abstract Builder setNumOfCasEnteringScoring(int value);

        abstract Builder setNumOfRemarketingAdsEnteringScoring(int value);

        abstract Builder setNumOfContextualAdsEnteringScoring(int value);

        abstract Builder setRunAdScoringLatencyInMillis(int value);

        abstract Builder setRunAdScoringResultCode(int value);

        abstract RunAdScoringProcessReportedStats build();
    }
}
