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

package com.android.adservices.service.measurement;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Objects;

/**
 * POJO for key-value data.
 *
 * <p>This class is useful for creating & storing arbitrary key-value-dataType combinations. The
 * corresponding table {@code
 * com.android.adservices.data.measurement.MeasurementTables.KeyValueDataContract} is used to
 * persist these objects.
 */
public class KeyValueData {
    public enum DataType {
        REGISTRATION_REDIRECT_COUNT,
        EVENT_REPORT_RETRY_COUNT,
        DEBUG_EVENT_REPORT_RETRY_COUNT,
        AGGREGATE_REPORT_RETRY_COUNT,
        DEBUG_AGGREGATE_REPORT_RETRY_COUNT,
        DEBUG_REPORT_RETRY_COUNT
    }

    private DataType mDataType = null;
    private String mKey = null;
    private String mValue = null;

    private KeyValueData(DataType dataType, String key, String value) {
        mDataType = dataType;
        mKey = key;
        mValue = value;
    }

    /** Returns the data type. */
    public DataType getDataType() {
        return mDataType;
    }

    /** Returns the key. */
    public String getKey() {
        return mKey;
    }

    /** Returns the raw value. */
    public String getValue() {
        return mValue;
    }

    private KeyValueData() {}

    /** Builder class for {@link KeyValueData} */
    public static class Builder {
        private DataType mDataType = null;
        private String mKey = null;
        private String mValue = null;

        /** See {@link KeyValueData#getDataType()} ()} */
        public Builder setDataType(@NonNull DataType dataType) {
            mDataType = dataType;
            return this;
        }

        /** See {@link KeyValueData#getKey()} */
        public Builder setKey(@NonNull String key) {
            mKey = key;
            return this;
        }

        /** See {@link KeyValueData#getValue()} */
        public Builder setValue(@Nullable String value) {
            mValue = value;
            return this;
        }

        /** Build the {@link KeyValueData} */
        public KeyValueData build() {
            Objects.requireNonNull(mDataType);
            Objects.requireNonNull(mKey);
            return new KeyValueData(mDataType, mKey, mValue);
        }
    }

    /** Get the Registration Count value */
    public int getRegistrationRedirectCount() {
        if (mDataType != DataType.REGISTRATION_REDIRECT_COUNT) {
            throw new IllegalStateException("Illegal method call");
        }
        if (mValue == null) {
            // Default value is 1, because the first registration will be the only case when value
            // can be null.
            return 1;
        }
        return Integer.parseInt(mValue);
    }

    /** Set the Registration Count value */
    public void setRegistrationRedirectCount(int value) {
        if (mDataType != DataType.REGISTRATION_REDIRECT_COUNT) {
            throw new IllegalStateException("Illegal method call");
        }
        mValue = String.valueOf(value);
    }

    /** Set the Aggregate/Event/Debug Report Retry Count value */
    public int getReportRetryCount() {
        validateOfTypeReport();
        if (mValue == null) {
            // Default value is 0,
            return 0;
        }
        return Integer.parseInt(mValue);
    }

    /** Set the Aggregate/Event/Debug Report Retry Count value */
    public void setReportRetryCount(int value) {
        validateOfTypeReport();
        mValue = String.valueOf(value);
    }

    private void validateOfTypeReport() {
        if (mDataType != DataType.AGGREGATE_REPORT_RETRY_COUNT
                && mDataType != DataType.DEBUG_AGGREGATE_REPORT_RETRY_COUNT
                && mDataType != DataType.EVENT_REPORT_RETRY_COUNT
                && mDataType != DataType.DEBUG_EVENT_REPORT_RETRY_COUNT
                && mDataType != DataType.DEBUG_REPORT_RETRY_COUNT) {
            throw new IllegalStateException("Illegal method call");
        }
    }
}
