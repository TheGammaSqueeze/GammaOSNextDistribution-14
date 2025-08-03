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

package com.android.adservices.service.measurement.registration;

import android.annotation.NonNull;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.util.Validation;

import java.util.Objects;

/** POJO for AsyncRegistration. */
public class AsyncRegistration {

    public enum RegistrationType {
        APP_SOURCE,
        APP_SOURCES,
        APP_TRIGGER,
        WEB_SOURCE,
        WEB_TRIGGER
    }

    private final String mId;
    private final Uri mOsDestination;
    private final Uri mWebDestination;
    private final Uri mRegistrationUri;
    private final Uri mVerifiedDestination;
    private final Uri mTopOrigin;
    private final Uri mRegistrant;
    private final Source.SourceType mSourceType;
    private long mRequestTime;
    private long mRetryCount;
    private final RegistrationType mType;
    private final boolean mDebugKeyAllowed;
    private final boolean mAdIdPermission;
    @Nullable private String mRegistrationId;
    @Nullable private final String mPlatformAdId;
    @Nullable private String mPostBody;
    private final AsyncRedirect.RedirectBehavior mRedirectBehavior;

    public enum RedirectType {
        LOCATION,
        LIST
    }

    public AsyncRegistration(@NonNull AsyncRegistration.Builder builder) {
        mId = builder.mId;
        mOsDestination = builder.mOsDestination;
        mWebDestination = builder.mWebDestination;
        mRegistrationUri = builder.mRegistrationUri;
        mVerifiedDestination = builder.mVerifiedDestination;
        mTopOrigin = builder.mTopOrigin;
        mRegistrant = builder.mRegistrant;
        mSourceType = builder.mSourceType;
        mRequestTime = builder.mRequestTime;
        mRetryCount = builder.mRetryCount;
        mType = builder.mType;
        mDebugKeyAllowed = builder.mDebugKeyAllowed;
        mAdIdPermission = builder.mAdIdPermission;
        mRegistrationId = builder.mRegistrationId;
        mPlatformAdId = builder.mPlatformAdId;
        mPostBody = builder.mPostBody;
        mRedirectBehavior = builder.mRedirectBehavior;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AsyncRegistration)) return false;
        AsyncRegistration that = (AsyncRegistration) o;
        return mRequestTime == that.mRequestTime
                && mRetryCount == that.mRetryCount
                && mDebugKeyAllowed == that.mDebugKeyAllowed
                && mAdIdPermission == that.mAdIdPermission
                && Objects.equals(mId, that.mId)
                && Objects.equals(mOsDestination, that.mOsDestination)
                && Objects.equals(mWebDestination, that.mWebDestination)
                && Objects.equals(mRegistrationUri, that.mRegistrationUri)
                && Objects.equals(mVerifiedDestination, that.mVerifiedDestination)
                && Objects.equals(mTopOrigin, that.mTopOrigin)
                && Objects.equals(mRegistrant, that.mRegistrant)
                && mSourceType == that.mSourceType
                && mType == that.mType
                && Objects.equals(mRegistrationId, that.mRegistrationId)
                && mPlatformAdId.equals(that.mPlatformAdId)
                && Objects.equals(mPostBody, that.mPostBody)
                && Objects.equals(mRedirectBehavior, that.mRedirectBehavior);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mOsDestination,
                mWebDestination,
                mRegistrationUri,
                mVerifiedDestination,
                mTopOrigin,
                mRegistrant,
                mSourceType,
                mRequestTime,
                mRetryCount,
                mType,
                mDebugKeyAllowed,
                mAdIdPermission,
                mRegistrationId,
                mPlatformAdId,
                mPostBody,
                mRedirectBehavior);
    }

    /** Unique identifier for the {@link AsyncRegistration}. */
    public String getId() {
        return mId;
    }

    /** App destination of the {@link Source}. */
    @Nullable
    public Uri getOsDestination() {
        return mOsDestination;
    }

    /** Web destination of the {@link Source}. */
    @Nullable
    public Uri getWebDestination() {
        return mWebDestination;
    }

    /** Represents the location of registration payload. */
    @NonNull
    public Uri getRegistrationUri() {
        return mRegistrationUri;
    }

    /** Uri used to identify and locate a {@link Source} originating from the web. */
    @Nullable
    public Uri getVerifiedDestination() {
        return mVerifiedDestination;
    }

    /** Package name of caller app. */
    @NonNull
    public Uri getTopOrigin() {
        return mTopOrigin;
    }

    /** Package name of caller app, name comes from context. */
    @NonNull
    public Uri getRegistrant() {
        return mRegistrant;
    }

    /** Determines whether the input event was a click or view. */
    public Source.SourceType getSourceType() {
        return mSourceType;
    }

    /** Time in ms that record arrived at Registration Queue. */
    public long getRequestTime() {
        return mRequestTime;
    }

    /** Retry attempt counter. */
    public long getRetryCount() {
        return mRetryCount;
    }

    /** Indicates how the record will be processed . */
    public RegistrationType getType() {
        return mType;
    }

    /** Indicates whether the debug key provided by Ad-Tech is allowed to be used or not. */
    public boolean getDebugKeyAllowed() {
        return mDebugKeyAllowed;
    }

    /** Indicates whether Ad Id permission is enabled. */
    public boolean hasAdIdPermission() {
        return mAdIdPermission;
    }

    /** Returns the registration id. */
    @NonNull
    public String getRegistrationId() {
        return mRegistrationId;
    }

    /**
     * Returns the AdID from an app registration, to be matched with a value from a web registration
     * response for supplying debug keys.
     */
    public String getPlatformAdId() {
        return mPlatformAdId;
    }

    /** Returns the post body. */
    @Nullable
    public String getPostBody() {
        return mPostBody;
    }

    /** Return the configuration for redirect behavior. */
    public AsyncRedirect.RedirectBehavior getRedirectBehavior() {
        return mRedirectBehavior;
    }

    /** Increments the retry count of the current record. */
    public void incrementRetryCount() {
        ++mRetryCount;
    }

    /** Indicates whether the registration runner should process redirects for this registration. */
    public boolean shouldProcessRedirects() {
        return mType == RegistrationType.APP_SOURCE || mType == RegistrationType.APP_TRIGGER;
    }

    public boolean isWebRequest() {
        return mType == RegistrationType.WEB_SOURCE || mType == RegistrationType.WEB_TRIGGER;
    }

    public boolean isAppRequest() {
        return mType == RegistrationType.APP_SOURCE
                || mType == RegistrationType.APP_TRIGGER
                || mType == RegistrationType.APP_SOURCES;
    }

    public boolean isSourceRequest() {
        return mType == RegistrationType.APP_SOURCE
                || mType == RegistrationType.WEB_SOURCE
                || mType == RegistrationType.APP_SOURCES;
    }

    public boolean isTriggerRequest() {
        return mType == RegistrationType.APP_TRIGGER || mType == RegistrationType.WEB_TRIGGER;
    }

    /** Builder for {@link AsyncRegistration}. */
    public static class Builder {
        private String mId;
        private Uri mOsDestination;
        private Uri mWebDestination;
        private Uri mRegistrationUri;
        private Uri mVerifiedDestination;
        private Uri mTopOrigin;
        private Uri mRegistrant;
        private Source.SourceType mSourceType;
        private long mRequestTime;
        private long mRetryCount = 0;
        private AsyncRegistration.RegistrationType mType;
        private boolean mDebugKeyAllowed;
        private boolean mAdIdPermission;
        @Nullable private String mRegistrationId;
        @Nullable private String mPlatformAdId;
        @Nullable private String mPostBody;
        private AsyncRedirect.RedirectBehavior mRedirectBehavior;

        /** See {@link AsyncRegistration#getId()}. */
        @NonNull
        public Builder setId(@NonNull String id) {
            Validation.validateNonNull(id);
            mId = id;
            return this;
        }

        /** See {@link AsyncRegistration#getOsDestination()}. */
        @NonNull
        public Builder setOsDestination(@Nullable Uri osDestination) {
            mOsDestination = osDestination;
            return this;
        }

        /** See {@link AsyncRegistration#getRegistrationUri()}. */
        @NonNull
        public Builder setRegistrationUri(@NonNull Uri registrationUri) {
            Validation.validateNonNull(registrationUri);
            mRegistrationUri = registrationUri;
            return this;
        }

        /** See {@link AsyncRegistration#getVerifiedDestination()}. */
        @NonNull
        public Builder setVerifiedDestination(@Nullable Uri verifiedDestination) {
            mVerifiedDestination = verifiedDestination;
            return this;
        }

        /** See {@link AsyncRegistration#getWebDestination()}. */
        @NonNull
        public Builder setWebDestination(@Nullable Uri webDestination) {
            mWebDestination = webDestination;
            return this;
        }

        /** See {@link AsyncRegistration#getTopOrigin()}. */
        @NonNull
        public Builder setTopOrigin(@Nullable Uri topOrigin) {
            mTopOrigin = topOrigin;
            return this;
        }

        /** See {@link AsyncRegistration#getRegistrant()}. */
        @NonNull
        public Builder setRegistrant(@NonNull Uri registrant) {
            Validation.validateNonNull(registrant);
            mRegistrant = registrant;
            return this;
        }

        /**
         * See {@link AsyncRegistration#getSourceType()}. Valid inputs are ordinals of {@link
         * Source.SourceType} enum values.
         */
        @NonNull
        public Builder setSourceType(Source.SourceType sourceType) {
            mSourceType = sourceType;
            return this;
        }

        /** See {@link AsyncRegistration#getRequestTime()}. */
        @NonNull
        public Builder setRequestTime(long requestTime) {
            mRequestTime = requestTime;
            return this;
        }

        /** See {@link AsyncRegistration#getRetryCount()}. */
        @NonNull
        public Builder setRetryCount(long retryCount) {
            mRetryCount = retryCount;
            return this;
        }

        /**
         * See {@link AsyncRegistration#getType()}. Valid inputs are ordinals of {@link
         * AsyncRegistration.RegistrationType} enum values.
         */
        @NonNull
        public Builder setType(RegistrationType type) {
            mType = type;
            return this;
        }

        /** See {@link AsyncRegistration#getDebugKeyAllowed()}. */
        @NonNull
        public Builder setDebugKeyAllowed(boolean debugKeyAllowed) {
            mDebugKeyAllowed = debugKeyAllowed;
            return this;
        }

        /** See {@link AsyncRegistration#hasAdIdPermission()}. */
        @NonNull
        public Builder setAdIdPermission(boolean adIdPermission) {
            mAdIdPermission = adIdPermission;
            return this;
        }

        /** See {@link AsyncRegistration#getRegistrationId()} */
        @NonNull
        public Builder setRegistrationId(@NonNull String registrationId) {
            mRegistrationId = registrationId;
            return this;
        }

        /** See {@link AsyncRegistration#getPlatformAdId()} */
        @NonNull
        public Builder setPlatformAdId(@Nullable String platformAdId) {
            mPlatformAdId = platformAdId;
            return this;
        }

        /** See {@link AsyncRegistration#getPlatformAdId()} */
        @NonNull
        public Builder setPostBody(@Nullable String postBody) {
            mPostBody = postBody;
            return this;
        }

        /** See {@link AsyncRegistration#getRedirectBehavior()}. */
        public Builder setRedirectBehavior(AsyncRedirect.RedirectBehavior redirectBehavior) {
            mRedirectBehavior = redirectBehavior;
            return this;
        }

        /** Build the {@link AsyncRegistration}. */
        @NonNull
        public AsyncRegistration build() {
            Objects.requireNonNull(mRegistrationId);
            return new AsyncRegistration(this);
        }
    }
}
