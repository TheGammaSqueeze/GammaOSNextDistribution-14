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

package com.android.adservices.service.stats;

import android.annotation.Nullable;

import com.google.auto.value.AutoValue;

/** Class for AdServicesEncryptionKeyFetched atom. */
@AutoValue
public abstract class AdServicesEncryptionKeyFetchedStats {

    /**
     * @return encryption key fetch job type.
     */
    public abstract FetchJobType getFetchJobType();

    /**
     * @return encryption key fetch status.
     */
    public abstract FetchStatus getFetchStatus();

    /**
     * @return whether the key is fetched for the first time.
     */
    public abstract boolean getIsFirstTimeFetch();

    /**
     * @return enrollment id for the adtech corresponding to the encryption key.
     */
    public abstract String getAdtechEnrollmentId();

    /**
     * @return encryption key url.
     */
    @Nullable
    public abstract String getEncryptionKeyUrl();

    /**
     * @return generic builder.
     */
    public static AdServicesEncryptionKeyFetchedStats.Builder builder() {
        return new AutoValue_AdServicesEncryptionKeyFetchedStats.Builder();
    }

    // Encryption key fetch job type.
    public enum FetchJobType {
        UNKNOWN_JOB(0),
        ENCRYPTION_KEY_DAILY_FETCH_JOB(1),
        MDD_DOWNLOAD_JOB(2);

        private final int mValue;

        FetchJobType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    // Encryption key fetch status.
    public enum FetchStatus {
        UNKNOWN(0),
        NULL_ENDPOINT(1),
        INVALID_ENDPOINT(2),
        IO_EXCEPTION(3),
        BAD_REQUEST_EXCEPTION(4),
        KEY_NOT_MODIFIED(5),
        SUCCESS(6);

        private final int mValue;

        FetchStatus(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** Builder class for {@link AdServicesEncryptionKeyFetchedStats} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set encryption key fetch job type. */
        public abstract Builder setFetchJobType(FetchJobType value);

        /** Set encryption key fetch status. */
        public abstract Builder setFetchStatus(FetchStatus value);

        /** Set whether the key is fetched for the first time. */
        public abstract Builder setIsFirstTimeFetch(boolean value);

        /** Set enrollment id for the adtech corresponding to the encryption key. */
        public abstract Builder setAdtechEnrollmentId(String value);

        /** Set encryption key url. */
        public abstract Builder setEncryptionKeyUrl(@Nullable String value);

        /** Build for {@link AdServicesEncryptionKeyFetchedStats}. */
        public abstract AdServicesEncryptionKeyFetchedStats build();
    }
}
