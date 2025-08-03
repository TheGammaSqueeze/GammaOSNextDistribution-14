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

import android.annotation.NonNull;

import java.util.Objects;

/**
 * class for measurement reporting stats.
 */
public class MeasurementReportsStats {
    private int mCode;
    private int mType;
    private int mResultCode;
    private int mFailureType;
    private int mUploadMethod;
    private long mReportingDelay;
    private String mSourceRegistrant;
    private int mRetryCount;

    public MeasurementReportsStats() {}

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MeasurementReportsStats)) {
            return false;
        }
        MeasurementReportsStats measurementReportsStats = (MeasurementReportsStats) obj;
        return mCode == measurementReportsStats.getCode()
                && mType == measurementReportsStats.getType()
                && mResultCode == measurementReportsStats.getResultCode()
                && mFailureType == measurementReportsStats.getFailureType()
                && mUploadMethod == measurementReportsStats.getUploadMethod()
                && mReportingDelay == measurementReportsStats.getReportingDelay()
                && Objects.equals(mSourceRegistrant, measurementReportsStats.getSourceRegistrant())
                && mRetryCount == measurementReportsStats.getRetryCount();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mCode,
                mType,
                mResultCode,
                mFailureType,
                mUploadMethod,
                mReportingDelay,
                mSourceRegistrant,
                mRetryCount);
    }

    public int getCode() {
        return mCode;
    }

    public int getType() {
        return mType;
    }

    public int getResultCode() {
        return mResultCode;
    }

    public int getFailureType() {
        return mFailureType;
    }

    public int getUploadMethod() {
        return mUploadMethod;
    }

    public long getReportingDelay() {
        return mReportingDelay;
    }

    public String getSourceRegistrant() {
        return mSourceRegistrant;
    }

    public int getRetryCount() {
        return mRetryCount;
    }

    /**
     * Builder for {@link MeasurementReportsStats}.
     */
    public static final class Builder {
        private final MeasurementReportsStats mBuilding;

        public Builder() {
            mBuilding = new MeasurementReportsStats();
        }

        /**
         * See {@link MeasurementReportsStats#getCode()} .
         */
        public @NonNull MeasurementReportsStats.Builder setCode(int code) {
            mBuilding.mCode = code;
            return this;
        }

        /**
         * See {@link MeasurementReportsStats#getType()} .
         */
        public @NonNull MeasurementReportsStats.Builder setType(int type) {
            mBuilding.mType = type;
            return this;
        }

        /**
         * See {@link MeasurementReportsStats#getResultCode()} .
         */
        public @NonNull MeasurementReportsStats.Builder setResultCode(int resultCode) {
            mBuilding.mResultCode = resultCode;
            return this;
        }

        /** See {@link MeasurementReportsStats#getFailureType()} . */
        public @NonNull MeasurementReportsStats.Builder setFailureType(int failureType) {
            mBuilding.mFailureType = failureType;
            return this;
        }

        /** See {@link MeasurementReportsStats#getUploadMethod()} . */
        public @NonNull MeasurementReportsStats.Builder setUploadMethod(int uploadMethod) {
            mBuilding.mUploadMethod = uploadMethod;
            return this;
        }

        /** See {@link MeasurementReportsStats#getReportingDelay()} . */
        public @NonNull MeasurementReportsStats.Builder setReportingDelay(long reportingDelay) {
            mBuilding.mReportingDelay = reportingDelay;
            return this;
        }

        /** See {@link #getSourceRegistrant()}. */
        public @NonNull MeasurementReportsStats.Builder setSourceRegistrant(
                String sourceRegistrant) {
            mBuilding.mSourceRegistrant = sourceRegistrant;
            return this;
        }

        /** See {@link MeasurementReportsStats#getRetryCount()} . */
        public @NonNull MeasurementReportsStats.Builder setRetryCount(int retryCount) {
            mBuilding.mRetryCount = retryCount;
            return this;
        }

        /**
         * Build the {@link MeasurementReportsStats}.
         */
        public @NonNull MeasurementReportsStats build() {
            return mBuilding;
        }
    }
}
