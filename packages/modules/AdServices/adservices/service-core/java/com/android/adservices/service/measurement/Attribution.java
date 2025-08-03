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

package com.android.adservices.service.measurement;

import android.annotation.IntDef;
import android.net.Uri;

import com.android.adservices.service.measurement.util.Validation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * It is used to insert and retrieve an entry in the database that counts towards attribution rate
 * limits. It is typically built from an {@link EventReport} or {@link Source}-{@link Trigger}
 * combination.
 */
public class Attribution {
    private final String mId;
    // Defaults to Scope.EVENT
    @Scope private int mScope;
    private final String mSourceSite;
    private final String mSourceOrigin;
    private final String mDestinationSite;
    private final String mDestinationOrigin;
    private final String mEnrollmentId;
    // TODO: b/276638412 rename to source time
    private final long mTriggerTime;
    private final String mRegistrant;
    private final String mSourceId;
    private final String mTriggerId;
    private final Uri mRegistrationOrigin;

    private Attribution(Builder builder) {
        this.mId = builder.mId;
        this.mScope = builder.mScope;
        this.mSourceSite = builder.mSourceSite;
        this.mSourceOrigin = builder.mSourceOrigin;
        this.mDestinationSite = builder.mDestinationSite;
        this.mDestinationOrigin = builder.mDestinationOrigin;
        this.mEnrollmentId = builder.mEnrollmentId;
        this.mTriggerTime = builder.mTriggerTime;
        this.mRegistrant = builder.mRegistrant;
        this.mSourceId = builder.mSourceId;
        this.mTriggerId = builder.mTriggerId;
        this.mRegistrationOrigin = builder.mRegistrationOrigin;
    }

    @IntDef(value = {Scope.EVENT, Scope.AGGREGATE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Scope {
        int EVENT = 0;
        int AGGREGATE = 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Attribution)) {
            return false;
        }
        Attribution attr = (Attribution) obj;
        return mScope == attr.mScope
                && mTriggerTime == attr.mTriggerTime
                && Objects.equals(mSourceSite, attr.mSourceSite)
                && Objects.equals(mSourceOrigin, attr.mSourceOrigin)
                && Objects.equals(mDestinationSite, attr.mDestinationSite)
                && Objects.equals(mDestinationOrigin, attr.mDestinationOrigin)
                && Objects.equals(mEnrollmentId, attr.mEnrollmentId)
                && Objects.equals(mRegistrant, attr.mRegistrant)
                && Objects.equals(mSourceId, attr.mSourceId)
                && Objects.equals(mTriggerId, attr.mTriggerId)
                && Objects.equals(mRegistrationOrigin, attr.mRegistrationOrigin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mScope,
                mSourceSite,
                mSourceOrigin,
                mDestinationSite,
                mDestinationOrigin,
                mEnrollmentId,
                mTriggerTime,
                mRegistrant,
                mSourceId,
                mTriggerId,
                mRegistrationOrigin);
    }

    /** @return unique identifier for {@link Attribution} */
    public String getId() {
        return mId;
    }

    /** Current scope of the {@link Attribution}. */
    @Scope
    public int getScope() {
        return mScope;
    }

    /** @return top private domain of {@link Source} publisher */
    public String getSourceSite() {
        return mSourceSite;
    }

    /** @return complete {@link Source} publisher */
    public String getSourceOrigin() {
        return mSourceOrigin;
    }

    /** @return top private domain of {@link Trigger} destination */
    public String getDestinationSite() {
        return mDestinationSite;
    }

    /** @return complete {@link Trigger} destination */
    public String getDestinationOrigin() {
        return mDestinationOrigin;
    }

    /** @return {@link Source} or {@link Trigger} enrollment ID */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** @return {@link Trigger} event time */
    public long getTriggerTime() {
        return mTriggerTime;
    }

    /** @return {@link Trigger} registrant */
    public String getRegistrant() {
        return mRegistrant;
    }

    /** @return {@link Source} ID */
    public String getSourceId() {
        return mSourceId;
    }

    /** @return {@link Trigger} ID */
    public String getTriggerId() {
        return mTriggerId;
    }

    /**
     * @return {@link Trigger} registration origin
     */
    public Uri getRegistrationOrigin() {
        return mRegistrationOrigin;
    }

    /** Builder for AttributionRateLimit */
    public static final class Builder {
        private String mId;
        @Scope private int mScope;
        private String mSourceSite;
        private String mSourceOrigin;
        private String mDestinationSite;
        private String mDestinationOrigin;
        private String mEnrollmentId;
        private long mTriggerTime;
        private String mRegistrant;
        private String mSourceId;
        private String mTriggerId;
        private Uri mRegistrationOrigin;

        /** See {@link Attribution#getId()}. */
        public Builder setId(String id) {
            mId = id;
            return this;
        }

        /** See {@link Attribution#getScope()}. */
        public Builder setScope(@Scope int scope) {
            mScope = scope;
            return this;
        }

        /** See {@link Attribution#getSourceSite()}. */
        public Builder setSourceSite(String sourceSite) {
            mSourceSite = sourceSite;
            return this;
        }

        /** See {@link Attribution#getSourceOrigin()}. */
        public Builder setSourceOrigin(String sourceOrigin) {
            mSourceOrigin = sourceOrigin;
            return this;
        }

        /** See {@link Attribution#getDestinationSite()}. */
        public Builder setDestinationSite(String destinationSite) {
            mDestinationSite = destinationSite;
            return this;
        }

        /** See {@link Attribution#getDestinationOrigin()}. */
        public Builder setDestinationOrigin(String destinationOrigin) {
            mDestinationOrigin = destinationOrigin;
            return this;
        }

        /** See {@link Attribution#getEnrollmentId()}. */
        public Builder setEnrollmentId(String enrollmentId) {
            mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link Attribution#getTriggerTime()}. */
        public Builder setTriggerTime(long triggerTime) {
            mTriggerTime = triggerTime;
            return this;
        }

        /** See {@link Attribution#getRegistrant()}. */
        public Builder setRegistrant(String registrant) {
            mRegistrant = registrant;
            return this;
        }

        /** See {@link Attribution#getSourceId()}. */
        public Builder setSourceId(String sourceId) {
            mSourceId = sourceId;
            return this;
        }

        /** See {@link Attribution#getTriggerId()}. */
        public Builder setTriggerId(String triggerId) {
            mTriggerId = triggerId;
            return this;
        }

        /** See {@link Attribution#getRegistrationOrigin()} ()}. */
        public Builder setRegistrationOrigin(Uri registrationOrigin) {
            mRegistrationOrigin = registrationOrigin;
            return this;
        }

        /** Validate and build the {@link Attribution}. */
        public Attribution build() {
            Validation.validateNonNull(
                    mSourceSite,
                    mSourceOrigin,
                    mDestinationSite,
                    mDestinationOrigin,
                    mEnrollmentId,
                    mRegistrant);
            return new Attribution(this);
        }
    }
}
