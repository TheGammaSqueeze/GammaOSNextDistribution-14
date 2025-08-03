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

import static com.android.adservices.service.measurement.XNetworkData.XNetworkDataContract.KEY_OFFSET;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Optional;

/** POJO for XNetworkData. */
public class XNetworkData {

    private final Optional<UnsignedLong> mKeyOffset;

    private XNetworkData(@NonNull XNetworkData.Builder builder) {
        mKeyOffset = builder.mKeyOffset;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof XNetworkData)) {
            return false;
        }
        XNetworkData xNetworkData = (XNetworkData) obj;
        return Objects.equals(mKeyOffset, xNetworkData.mKeyOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKeyOffset);
    }

    /** Returns the value of keyOffset as Long */
    public Optional<UnsignedLong> getKeyOffset() {
        return mKeyOffset;
    }

    /** Builder for {@link XNetworkData}. */
    public static final class Builder {
        private Optional<UnsignedLong> mKeyOffset;

        public Builder() {
            mKeyOffset = Optional.empty();
        }

        public Builder(@NonNull JSONObject jsonObject) throws JSONException {
            if (!jsonObject.isNull(KEY_OFFSET)) {
                String keyOffset = jsonObject.getString(KEY_OFFSET);
                // Unassigned in order to validate the long value
                try {
                    mKeyOffset = Optional.of(new UnsignedLong(keyOffset));
                } catch (NumberFormatException e) {
                    LoggerFactory.getMeasurementLogger()
                            .d(e, "XNetworkData.Builder: Failed to parse keyOffset.");
                    // Wrapped into JSONException so that it does not crash and becomes a checked
                    // Exception that is caught by the caller.
                    throw new JSONException("Failed to parse keyOffset", e);
                }
            }
        }

        /** See {@link XNetworkData#getKeyOffset()}. */
        @NonNull
        public Builder setKeyOffset(@Nullable UnsignedLong keyOffset) {
            mKeyOffset = Optional.ofNullable(keyOffset);
            return this;
        }

        /** Build the {@link XNetworkData}. */
        @NonNull
        public XNetworkData build() {
            return new XNetworkData(this);
        }
    }

    /** Constants related to XNetworkData. */
    public interface XNetworkDataContract {
        String KEY_OFFSET = "key_offset";
    }
}
