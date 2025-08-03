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

package com.android.adservices.service.enrollment;

/** POJO for storing Enrollment(Data/FileDownload/Failed) status */
public class EnrollmentStatus {
    /** Enum is tied to the AdservicesEnrollmentData atom */
    public enum TransactionType {
        UNKNOWN(0),
        READ_TRANSACTION_TYPE(1),
        WRITE_TRANSACTION_TYPE(2);

        private final int mValue;

        TransactionType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** Enum used by AdservicesEnrollmentFailed atom. */
    public enum DataFileGroupStatus {
        UNKNOWN_DATA_FILE_GROUP_STATUS(0),
        DOWNLOADED_DATA_FILE_GROUP_STATUS(1),
        PENDING_DATA_FILE_GROUP_STATUS(2),
        PENDING_CUSTOM_VALIDATION(3);

        private final int mValue;

        DataFileGroupStatus(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** Enum used by AdservicesEnrollmentFailed atom. */
    public enum ErrorCause {
        UNKNOWN_ERROR_CAUSE(0),
        ENROLLMENT_NOT_FOUND_ERROR_CAUSE(1),
        ENROLLMENT_BLOCKLISTED_ERROR_CAUSE(2);

        private final int mValue;

        ErrorCause(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }
}
