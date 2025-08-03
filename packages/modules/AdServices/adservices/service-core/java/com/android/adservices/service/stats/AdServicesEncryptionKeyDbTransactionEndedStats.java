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

import com.google.auto.value.AutoValue;

/** Class for AdServicesEncryptionKeyDbTransactionEnded atom. */
@AutoValue
public abstract class AdServicesEncryptionKeyDbTransactionEndedStats {

    /**
     * @return encryption key datastore transaction type.
     */
    public abstract DbTransactionType getDbTransactionType();

    /**
     * @return encryption key datastore transaction status.
     */
    public abstract DbTransactionStatus getDbTransactionStatus();

    /**
     * @return encryption key dao method name.
     */
    public abstract MethodName getMethodName();

    /**
     * @return generic builder.
     */
    public static AdServicesEncryptionKeyDbTransactionEndedStats.Builder builder() {
        return new AutoValue_AdServicesEncryptionKeyDbTransactionEndedStats.Builder();
    }

    // Encryption key datastore transaction type.
    public enum DbTransactionType {
        UNKNOWN(0),
        READ_TRANSACTION_TYPE(1),
        WRITE_TRANSACTION_TYPE(2);

        private final int mValue;

        DbTransactionType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    // Encryption key datastore transaction status.
    public enum DbTransactionStatus {
        UNKNOWN_EXCEPTION(0),
        INVALID_KEY(1),
        INSERT_EXCEPTION(2),
        DELETE_EXCEPTION(3),
        SEARCH_EXCEPTION(4),
        SUCCESS(5);

        private final int mValue;

        DbTransactionStatus(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    // Encryption key DAO method names.
    public enum MethodName {
        UNKNOWN_METHOD(0),
        GET_KEY_FROM_ENROLLMENT_ID(1),
        GET_KEY_FROM_ENROLLMENT_ID_AND_KEY_TYPE(2),
        GET_KEY_FROM_ENROLLMENT_ID_AND_KEY_ID(3),
        GET_KEY_FROM_REPORTING_ORIGIN(4),
        GET_ALL_KEYS(5),
        INSERT_KEY(6),
        INSERT_KEYS(7),
        DELETE_KEY(8);

        private final int mValue;

        MethodName(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** Builder class for {@link AdServicesEncryptionKeyDbTransactionEndedStats} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Set encryption key datastore transaction type. */
        public abstract Builder setDbTransactionType(DbTransactionType value);

        /** Set encryption key datastore transaction status. */
        public abstract Builder setDbTransactionStatus(DbTransactionStatus value);

        /** Set encryption key dao method name. */
        public abstract Builder setMethodName(MethodName value);

        /** Build for {@link AdServicesEncryptionKeyDbTransactionEndedStats}. */
        public abstract AdServicesEncryptionKeyDbTransactionEndedStats build();
    }
}
