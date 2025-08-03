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
package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** Debug Report. */
public final class DebugReport {
    private final String mId;
    private final String mType;
    private final JSONObject mBody;
    private final String mEnrollmentId;
    private final Uri mRegistrationOrigin;
    private final String mReferenceId;
    private final Long mInsertionTime;
    private final Uri mRegistrant;

    /** Create a new debug report object. */
    private DebugReport(
            @Nullable String id,
            @NonNull String type,
            @NonNull JSONObject body,
            @NonNull String enrollmentId,
            @NonNull Uri registrationOrigin,
            @Nullable String referenceId,
            @Nullable Uri registrant,
            @Nullable Long insertionTime) {
        mId = id;
        mType = type;
        mBody = body;
        mEnrollmentId = enrollmentId;
        mRegistrationOrigin = registrationOrigin;
        mReferenceId = referenceId;
        mInsertionTime = insertionTime;
        mRegistrant = registrant;
    }

    @Override
    public boolean equals(Object obj) {
        // TODO (b/300109438) Investigate DebugReport::equals
        if (!(obj instanceof DebugReport key)) {
            return false;
        }
        return Objects.equals(mType, key.mType)
                && Objects.equals(mBody, key.mBody)
                && Objects.equals(mEnrollmentId, key.mEnrollmentId)
                && Objects.equals(mRegistrationOrigin, key.mRegistrationOrigin)
                && Objects.equals(mReferenceId, key.mReferenceId)
                && Objects.equals(mRegistrant, key.mRegistrant)
                && Objects.equals(mInsertionTime, key.mInsertionTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mType,
                mBody,
                mEnrollmentId,
                mRegistrationOrigin,
                mReferenceId,
                mRegistrant,
                mInsertionTime);
    }

    /** Unique identifier for the {@link DebugReport}. */
    public String getId() {
        return mId;
    }

    /** Type of debug report. */
    public String getType() {
        return mType;
    }

    /** Body of debug report. */
    public JSONObject getBody() {
        return mBody;
    }

    /** AdTech enrollment ID. */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }
    /** Registration Origin URL */
    public Uri getRegistrationOrigin() {
        return mRegistrationOrigin;
    }
    /** Reference ID for grouping reports. */
    public String getReferenceId() {
        return mReferenceId;
    }
    /** Datastore Insertion time */
    public Long getInsertionTime() {
        return mInsertionTime;
    }
    /** Associated Source App Identifier */
    public Uri getRegistrant() {
        return mRegistrant;
    }

    /** Generate the JSON serialization of the debug report payload. */
    public JSONObject toPayloadJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", mType);
        jsonObject.put("body", mBody);
        return jsonObject;
    }

    /** A builder for {@link DebugReport}. */
    public static final class Builder {
        private String mId;
        private String mType;
        private JSONObject mBody;
        private String mEnrollmentId;
        private Uri mRegistrationOrigin;
        private String mReferenceId;
        private Long mInsertionTime;
        private Uri mRegistrant;

        public Builder() {}

        /** See {@link DebugReport#getId()}. */
        public Builder setId(String id) {
            mId = id;
            return this;
        }

        /** See {@link DebugReport#getType}. */
        @NonNull
        public Builder setType(@NonNull String type) {
            mType = type;
            return this;
        }

        /** See {@link DebugReport#getBody}. */
        @NonNull
        public Builder setBody(@NonNull String body) {
            try {
                mBody = new JSONObject(body);
            } catch (JSONException e) {
                throw new IllegalArgumentException("Invalid debug report body json");
            }
            return this;
        }

        /** See {@link DebugReport#getBody}. */
        @NonNull
        public Builder setBody(@NonNull JSONObject body) {
            mBody = body;
            return this;
        }

        /** See {@link DebugReport#getEnrollmentId()}. */
        @NonNull
        public Builder setEnrollmentId(String enrollmentId) {
            mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link DebugReport#getRegistrationOrigin()} ()}. */
        @NonNull
        public Builder setRegistrationOrigin(Uri registrationOrigin) {
            mRegistrationOrigin = registrationOrigin;
            return this;
        }

        /** See {@link DebugReport#getReferenceId()} ()}. */
        @NonNull
        public Builder setReferenceId(String referenceId) {
            mReferenceId = referenceId;
            return this;
        }

        /** See {@link DebugReport#getInsertionTime()} ()}. */
        @NonNull
        public Builder setInsertionTime(Long insertionTime) {
            mInsertionTime = insertionTime;
            return this;
        }

        /** See {@link DebugReport#getRegistrant()} ()}. */
        @NonNull
        public Builder setRegistrant(Uri registrant) {
            mRegistrant = registrant;
            return this;
        }

        /** Build the DebugReport. */
        @NonNull
        public DebugReport build() {
            if (mType == null || mBody == null) {
                throw new IllegalArgumentException("Uninitialized fields");
            }
            return new DebugReport(
                    mId,
                    mType,
                    mBody,
                    mEnrollmentId,
                    mRegistrationOrigin,
                    mReferenceId,
                    mRegistrant,
                    mInsertionTime);
        }
    }
}
