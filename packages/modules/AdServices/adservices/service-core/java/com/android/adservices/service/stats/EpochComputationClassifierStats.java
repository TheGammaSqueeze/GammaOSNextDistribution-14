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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__UNKNOWN_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__UNKNOWN_CLASSIFIER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_UNSPECIFIED;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * Class for AdServicesEpochComputationClassifierReported atom (for T+ logging) and
 * AdServicesBackCompatEpochComputationClassifierReported atom (for R+ logging).
 *
 * <p>See go/rbc-ww-logging for more details.
 */
@AutoValue
public abstract class EpochComputationClassifierStats {

    /** @return list of topics returned by the classifier for each app. */
    public abstract ImmutableList<Integer> getTopicIds();

    /** @return build id of the assets. */
    public abstract int getBuildId();

    /** @return version of the assets used. */
    public abstract String getAssetVersion();

    /** @return type of the classifier used for classification. */
    public abstract ClassifierType getClassifierType();

    /** @return on-device classifier status. */
    public abstract OnDeviceClassifierStatus getOnDeviceClassifierStatus();

    /** @return pre-computed classifier status. */
    public abstract PrecomputedClassifierStatus getPrecomputedClassifierStatus();

    /** @return generic builder. */
    public static EpochComputationClassifierStats.Builder builder() {
        return new AutoValue_EpochComputationClassifierStats.Builder();
    }

    /** Builder class for {@link EpochComputationClassifierStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set list of topics returned by the classifier for each app */
        public abstract EpochComputationClassifierStats.Builder setTopicIds(
                ImmutableList<Integer> value);

        /** Set duplicate topic count. */
        public abstract EpochComputationClassifierStats.Builder setBuildId(int value);

        /** Set version of the assets used. */
        public abstract EpochComputationClassifierStats.Builder setAssetVersion(String value);

        /** Set type of the classifier used for classification. */
        public abstract EpochComputationClassifierStats.Builder setClassifierType(
                ClassifierType value);

        /** Set on-device classifier status. */
        public abstract EpochComputationClassifierStats.Builder setOnDeviceClassifierStatus(
                OnDeviceClassifierStatus value);

        /** Set pre-computed classifier status. */
        public abstract EpochComputationClassifierStats.Builder setPrecomputedClassifierStatus(
                PrecomputedClassifierStatus value);

        /** build for {@link EpochComputationClassifierStats}. */
        public abstract EpochComputationClassifierStats build();
    }

    /** Type of the classifier used for classifying apps. */
    public enum ClassifierType {
        UNKNOWN_CLASSIFIER(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__UNKNOWN_CLASSIFIER,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__UNKNOWN_CLASSIFIER),
        ON_DEVICE_CLASSIFIER(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__ON_DEVICE_CLASSIFIER),
        PRECOMPUTED_CLASSIFIER(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_CLASSIFIER,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_CLASSIFIER),
        PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__CLASSIFIER_TYPE__PRECOMPUTED_THEN_ON_DEVICE_CLASSIFIER);

        private final int mLoggingValue;
        private final int mCompatLoggingValue;

        ClassifierType(int mLoggingValue, int mCompatLoggingValue) {
            this.mLoggingValue = mLoggingValue;
            this.mCompatLoggingValue = mCompatLoggingValue;
        }

        /**
         * @return Autogen enum logging value for AdServicesEpochComputationClassifierReported atom
         *     used for T+ logging.
         */
        public int getLoggingValue() {
            return mLoggingValue;
        }

        /**
         * @return Autogen enum logging value for AdServicesEpochComputationClassifierReported atom
         *     used for R+ logging.
         */
        public int getCompatLoggingValue() {
            return mCompatLoggingValue;
        }
    }

    /** On Device classifier status. */
    public enum OnDeviceClassifierStatus {
        ON_DEVICE_CLASSIFIER_STATUS_UNSPECIFIED(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_UNSPECIFIED,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_UNSPECIFIED),
        ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_NOT_INVOKED),
        ON_DEVICE_CLASSIFIER_STATUS_SUCCESS(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_SUCCESS),
        ON_DEVICE_CLASSIFIER_STATUS_FAILURE(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_FAILURE,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__ON_DEVICE_CLASSIFIER_STATUS__ON_DEVICE_CLASSIFIER_STATUS_FAILURE);

        private final int mLoggingValue;
        private final int mCompatLoggingValue;

        OnDeviceClassifierStatus(int mLoggingValue, int mCompatLoggingValue) {
            this.mLoggingValue = mLoggingValue;
            this.mCompatLoggingValue = mCompatLoggingValue;
        }

        /**
         * @return Autogen enum logging value for AdServicesEpochComputationClassifierReported atom
         *     used for T+ logging.
         */
        public int getLoggingValue() {
            return mLoggingValue;
        }

        /**
         * @return Autogen enum logging value for AdServicesEpochComputationClassifierReported atom
         *     used for R+ logging.
         */
        public int getCompatLoggingValue() {
            return mCompatLoggingValue;
        }
    }

    /** Precomputed classifier status. */
    public enum PrecomputedClassifierStatus {
        PRECOMPUTED_CLASSIFIER_STATUS_UNSPECIFIED(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_UNSPECIFIED,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_UNSPECIFIED),
        PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_NOT_INVOKED),
        PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_SUCCESS),
        PRECOMPUTED_CLASSIFIER_STATUS_FAILURE(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_FAILURE,
                AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED__PRECOMPUTED_CLASSIFIER_STATUS__PRECOMPUTED_CLASSIFIER_STATUS_FAILURE);

        private final int mLoggingValue;
        private final int mCompatLoggingValue;

        PrecomputedClassifierStatus(int mLoggingValue, int mCompatLoggingValue) {
            this.mLoggingValue = mLoggingValue;
            this.mCompatLoggingValue = mCompatLoggingValue;
        }

        /**
         * @return Autogen enum logging value for AdServicesEpochComputationClassifierReported atom
         *     used for T+ logging.
         */
        public int getLoggingValue() {
            return mLoggingValue;
        }

        /**
         * @return Autogen enum logging value for AdServicesEpochComputationClassifierReported atom
         *     used for R+ logging.
         */
        public int getCompatLoggingValue() {
            return mCompatLoggingValue;
        }
    }
}
