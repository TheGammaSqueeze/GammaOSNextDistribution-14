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
import android.annotation.Nullable;

import java.util.Objects;

/** Class for measurement registration response Stats. */
public class MeasurementRegistrationResponseStats {
    private final int mCode;
    private final int mRegistrationType;
    private final long mResponseSize;
    private final String mAdTechDomain;
    private final int mInteractionType;
    private final int mSurfaceType;
    private final int mRegistrationStatus;
    private final int mFailureType;
    private final long mRegistrationDelay;
    private final String mSourceRegistrant;
    private final int mRetryCount;
    private final boolean mIsRedirectOnly;

    private MeasurementRegistrationResponseStats(Builder builder) {
        mCode = builder.mCode;
        mRegistrationType = builder.mRegistrationType;
        mResponseSize = builder.mResponseSize;
        mAdTechDomain = builder.mAdTechDomain;
        mInteractionType = builder.mInteractionType;
        mSurfaceType = builder.mSurfaceType;
        mRegistrationStatus = builder.mRegistrationStatus;
        mFailureType = builder.mFailureType;
        mRegistrationDelay = builder.mRegistrationDelay;
        mSourceRegistrant = builder.mSourceRegistrant;
        mRetryCount = builder.mRetryCount;
        mIsRedirectOnly = builder.mIsRedirectOnly;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeasurementRegistrationResponseStats)) return false;
        MeasurementRegistrationResponseStats that = (MeasurementRegistrationResponseStats) o;
        return mCode == that.mCode
                && mRegistrationType == that.mRegistrationType
                && mResponseSize == that.mResponseSize
                && Objects.equals(mAdTechDomain, that.mAdTechDomain)
                && mInteractionType == that.mInteractionType
                && mSurfaceType == that.mSurfaceType
                && mRegistrationStatus == that.mRegistrationStatus
                && mFailureType == that.mFailureType
                && mRegistrationDelay == that.mRegistrationDelay
                && Objects.equals(mSourceRegistrant, that.mSourceRegistrant)
                && mRetryCount == that.mRetryCount
                && mIsRedirectOnly == that.mIsRedirectOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mCode,
                mRegistrationType,
                mResponseSize,
                mAdTechDomain,
                mInteractionType,
                mSurfaceType,
                mRegistrationStatus,
                mFailureType,
                mRegistrationDelay,
                mSourceRegistrant,
                mRetryCount,
                mIsRedirectOnly);
    }

    @Override
    public String toString() {
        return "MeasurementRegistrationResponseStats{"
                + "mCode="
                + mCode
                + ", mRegistrationType="
                + mRegistrationType
                + ", mResponseSize="
                + mResponseSize
                + ", mAdTechDomain='"
                + mAdTechDomain
                + ", mInteractionType="
                + mInteractionType
                + ", mSurfaceType="
                + mSurfaceType
                + ", mRegistrationStatus="
                + mRegistrationStatus
                + ", mFailureType="
                + mFailureType
                + ", mRegistrationDelay="
                + mRegistrationDelay
                + ", mSourceRegistrant="
                + mSourceRegistrant
                + ", mRetryCount="
                + mRetryCount
                + ", mIsRedirectOnly="
                + mIsRedirectOnly
                + '}';
    }

    public int getCode() {
        return mCode;
    }

    public int getRegistrationType() {
        return mRegistrationType;
    }

    public long getResponseSize() {
        return mResponseSize;
    }

    @Nullable
    public String getAdTechDomain() {
        return mAdTechDomain;
    }

    public int getInteractionType() {
        return mInteractionType;
    }

    public int getSurfaceType() {
        return mSurfaceType;
    }

    public int getRegistrationStatus() {
        return mRegistrationStatus;
    }

    public int getFailureType() {
        return mFailureType;
    }

    public long getRegistrationDelay() {
        return mRegistrationDelay;
    }

    public String getSourceRegistrant() {
        return mSourceRegistrant;
    }

    public int getRetryCount() {
        return mRetryCount;
    }

    public boolean isRedirectOnly() {
        return mIsRedirectOnly;
    }

    /** Builder for {@link MeasurementRegistrationResponseStats}. */
    public static final class Builder {
        private final int mCode;
        private final int mRegistrationType;
        private final long mResponseSize;
        private String mAdTechDomain;
        private final int mInteractionType;
        private final int mSurfaceType;
        private final int mRegistrationStatus;
        private final int mFailureType;
        private final long mRegistrationDelay;
        private final String mSourceRegistrant;
        private final int mRetryCount;
        private final boolean mIsRedirectOnly;

        public Builder(
                int code,
                int registrationType,
                long responseSize,
                int interactionType,
                int surfaceType,
                int registrationStatus,
                int failureType,
                long registrationDelay,
                String sourceRegistrant,
                int retryCount,
                boolean isRedirectOnly) {
            mCode = code;
            mRegistrationType = registrationType;
            mResponseSize = responseSize;
            mInteractionType = interactionType;
            mSurfaceType = surfaceType;
            mRegistrationStatus = registrationStatus;
            mFailureType = failureType;
            mRegistrationDelay = registrationDelay;
            mSourceRegistrant = sourceRegistrant;
            mRetryCount = retryCount;
            mIsRedirectOnly = isRedirectOnly;
        }

        /** See {@link MeasurementRegistrationResponseStats#getAdTechDomain()} . */
        @NonNull
        public MeasurementRegistrationResponseStats.Builder setAdTechDomain(
                @Nullable String adTechDomain) {
            mAdTechDomain = adTechDomain;
            return this;
        }

        /** Build the {@link MeasurementRegistrationResponseStats}. */
        @NonNull
        public MeasurementRegistrationResponseStats build() {
            return new MeasurementRegistrationResponseStats(this);
        }
    }
}
