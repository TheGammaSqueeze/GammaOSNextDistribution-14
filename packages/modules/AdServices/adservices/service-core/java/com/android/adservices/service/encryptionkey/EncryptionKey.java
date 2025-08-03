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

package com.android.adservices.service.encryptionkey;

import android.net.Uri;

import java.util.Objects;

/**
 * POJO represents the encryption key JSON response when calling Adtech encryption key url endpoint.
 */
public class EncryptionKey {

    private String mId;
    private KeyType mKeyType;
    private String mEnrollmentId;
    private Uri mReportingOrigin;
    private String mEncryptionKeyUrl;
    private ProtocolType mProtocolType;
    private int mKeyCommitmentId;
    private String mBody;
    private long mExpiration;
    private long mLastFetchTime;

    public EncryptionKey() {
        mId = null;
        mKeyType = KeyType.ENCRYPTION;
        mEnrollmentId = null;
        mReportingOrigin = null;
        mEncryptionKeyUrl = null;
        mProtocolType = ProtocolType.HPKE;
        mKeyCommitmentId = 0;
        mBody = null;
        mExpiration = 0L;
        mLastFetchTime = 0L;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EncryptionKey)) {
            return false;
        }
        EncryptionKey encryptionKey = (EncryptionKey) obj;
        return Objects.equals(mId, encryptionKey.mId)
                && Objects.equals(mKeyType, encryptionKey.mKeyType)
                && Objects.equals(mEnrollmentId, encryptionKey.mEnrollmentId)
                && Objects.equals(mReportingOrigin, encryptionKey.mReportingOrigin)
                && Objects.equals(mEncryptionKeyUrl, encryptionKey.mEncryptionKeyUrl)
                && Objects.equals(mProtocolType, encryptionKey.mProtocolType)
                && (mKeyCommitmentId == encryptionKey.mKeyCommitmentId)
                && Objects.equals(mBody, encryptionKey.mBody)
                && (mExpiration == encryptionKey.mExpiration)
                && (mLastFetchTime == encryptionKey.mLastFetchTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mKeyType,
                mEnrollmentId,
                mReportingOrigin,
                mEncryptionKeyUrl,
                mProtocolType,
                mKeyCommitmentId,
                mBody,
                mExpiration,
                mLastFetchTime);
    }

    /** Returns id for this encryption key, this is the UUID for each key in db table. */
    public String getId() {
        return mId;
    }

    /** Returns key type for this key commitment. */
    public KeyType getKeyType() {
        return mKeyType;
    }

    /** Returns enrollment id for the Adtech. */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** Returns Adtech reporting origin, set as triggerRegistrationUrl during enrollment. */
    public Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /**
     * Returns the encryption key url endpoint provided by Adtech, we use this endpoint to fetch and
     * update keys.
     */
    public String getEncryptionKeyUrl() {
        return mEncryptionKeyUrl;
    }

    /** Returns protocol type for this key commitment. */
    public ProtocolType getProtocolType() {
        return mProtocolType;
    }

    /** Returns id for this key commitment, this id is unique per adtech. */
    public int getKeyCommitmentId() {
        return mKeyCommitmentId;
    }

    /** Returns base64-encoded public key body. */
    public String getBody() {
        return mBody;
    }

    /** Returns expiration time of this public key in milliseconds. */
    public long getExpiration() {
        return mExpiration;
    }

    /** Returns the last fetch time for this encryption key. */
    public long getLastFetchTime() {
        return mLastFetchTime;
    }

    /** Returns the builder for the instance */
    public EncryptionKey.Builder cloneToBuilder() {
        return new EncryptionKey.Builder()
                .setId(this.mId)
                .setKeyType(this.mKeyType)
                .setEnrollmentId(this.mEnrollmentId)
                .setReportingOrigin(this.mReportingOrigin)
                .setEncryptionKeyUrl(this.mEncryptionKeyUrl)
                .setProtocolType(this.mProtocolType)
                .setKeyCommitmentId(this.mKeyCommitmentId)
                .setBody(this.mBody)
                .setExpiration(this.mExpiration)
                .setLastFetchTime(this.mLastFetchTime);
    }

    /** Builder for {@link EncryptionKey}. */
    public static final class Builder {
        private final EncryptionKey mBuilding;

        public Builder() {
            mBuilding = new EncryptionKey();
        }

        /** See {@link EncryptionKey#getId()}. */
        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        /** See {@link EncryptionKey#getKeyType()}. */
        public Builder setKeyType(KeyType keyType) {
            mBuilding.mKeyType = keyType;
            return this;
        }

        /** See {@link EncryptionKey#getEnrollmentId()}. */
        public Builder setEnrollmentId(String enrollmentId) {
            mBuilding.mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link EncryptionKey#getReportingOrigin()}. */
        public Builder setReportingOrigin(Uri reportingOrigin) {
            mBuilding.mReportingOrigin = reportingOrigin;
            return this;
        }

        /** See {@link EncryptionKey#getEncryptionKeyUrl()}. */
        public Builder setEncryptionKeyUrl(String encryptionKeyUrl) {
            mBuilding.mEncryptionKeyUrl = encryptionKeyUrl;
            return this;
        }

        /** See {@link EncryptionKey#getProtocolType()}. */
        public Builder setProtocolType(ProtocolType protocolType) {
            mBuilding.mProtocolType = protocolType;
            return this;
        }

        /** See {@link EncryptionKey#getKeyCommitmentId()}. */
        public Builder setKeyCommitmentId(int keyCommitmentId) {
            mBuilding.mKeyCommitmentId = keyCommitmentId;
            return this;
        }

        /** See {@link EncryptionKey#getBody()}. */
        public Builder setBody(String body) {
            mBuilding.mBody = body;
            return this;
        }

        /** See {@link EncryptionKey#getExpiration()}. */
        public Builder setExpiration(long expiration) {
            mBuilding.mExpiration = expiration;
            return this;
        }

        /** See {@link EncryptionKey#getLastFetchTime()}. */
        public Builder setLastFetchTime(long lastFetchTime) {
            mBuilding.mLastFetchTime = lastFetchTime;
            return this;
        }

        /** Build the {@link EncryptionKey}. */
        public EncryptionKey build() {
            return mBuilding;
        }
    }

    // The key type for this key, a key can be either an encryption key or a signing key.
    public enum KeyType {
        ENCRYPTION("encryption"),
        SIGNING("signing");

        private final String mValue;

        KeyType(String value) {
            mValue = value;
        }

        public String getValue() {
            return mValue;
        }
    }

    /**
     * ProtocolType enumerates the algorithm used with the key. Set as HPKE by default, more
     * algorithms can be supported in the future.
     */
    public enum ProtocolType {
        // Algorithm used by signing key.
        ECDSA("ecdsa"),
        // Algorithm used by Topics encryption key.
        HPKE("hpke");
        private final String mValue;

        ProtocolType(String value) {
            mValue = value;
        }

        public String getValue() {
            return mValue;
        }
    }
}
