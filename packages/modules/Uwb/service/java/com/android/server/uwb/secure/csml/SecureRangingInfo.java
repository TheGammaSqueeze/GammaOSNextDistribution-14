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

package com.android.server.uwb.secure.csml;

import android.annotation.NonNull;

import com.android.server.uwb.params.TlvBuffer;
import com.android.server.uwb.params.TlvDecoderBuffer;

import java.util.Optional;

/**
 * The UWB session Key information in {@link SessionData}.
 * See FiRa CSML 1.0 Table 53 - SECURE_RANGING_INFO
 */
class SecureRangingInfo {
    public static final int SECURE_RANGING_INFO_TAG = 0xA5;
    public static final int UWB_SESSION_KEY_INFO_TAG = 0x80;
    public static final int RESPONDER_SPECIFIC_SUB_SESSION_KEY_INFO_TAG = 0x81;
    public static final int SUS_ADDITIONAL_PARAMS_TAG = 0x82;

    private static final int MAX_FIELD_COUNT = 3;

    public final Optional<byte[]> uwbSessionKeyInfo;
    public final Optional<byte[]> uwbSubSessionKeyInfo;
    public final Optional<byte[]> susAdditionalParams;

    private SecureRangingInfo(
            Optional<byte[]> uwbSessionKeyInfo,
            Optional<byte[]> uwbSubSessionKeyInfo,
            Optional<byte[]> susAdditionalParams) {
        this.uwbSessionKeyInfo = uwbSessionKeyInfo;
        this.uwbSubSessionKeyInfo = uwbSubSessionKeyInfo;
        this.susAdditionalParams = susAdditionalParams;
    }

    /** Converts the{@link SecureRangingInfo} as TLV data payload. */
    @NonNull
    byte[] toBytes() {
        TlvBuffer.Builder secureInfoBuilder = new TlvBuffer.Builder();
        uwbSessionKeyInfo.ifPresent(bArray -> secureInfoBuilder.putByteArray(
                UWB_SESSION_KEY_INFO_TAG, bArray));
        uwbSubSessionKeyInfo.ifPresent(bArray -> secureInfoBuilder.putByteArray(
                RESPONDER_SPECIFIC_SUB_SESSION_KEY_INFO_TAG, bArray));
        susAdditionalParams.ifPresent(bArray -> secureInfoBuilder.putByteArray(
                SUS_ADDITIONAL_PARAMS_TAG, bArray));
        return secureInfoBuilder.build().getByteArray();
    }

    @NonNull
    private static Optional<byte[]> getByteArrayValue(TlvDecoderBuffer parsedTlvBuffer, int tag) {
        try {
            return Optional.of(parsedTlvBuffer.getByteArray(tag));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Gets the {@link SecureRangingInfo} from the TLV data. */
    @NonNull
    static SecureRangingInfo fromBytes(@NonNull byte[] bytes) {
        TlvDecoderBuffer secureInfoTlv = new TlvDecoderBuffer(bytes, MAX_FIELD_COUNT);

        secureInfoTlv.parse();

        return new SecureRangingInfo(
                getByteArrayValue(secureInfoTlv, UWB_SESSION_KEY_INFO_TAG),
                getByteArrayValue(secureInfoTlv, RESPONDER_SPECIFIC_SUB_SESSION_KEY_INFO_TAG),
                getByteArrayValue(secureInfoTlv, SUS_ADDITIONAL_PARAMS_TAG));
    }

    /** The builder class of {@link SecureRangingInfo} */
    static class Builder {
        private  Optional<byte[]> mUwbSessionKeyInfo = Optional.empty();
        private  Optional<byte[]> mUwbSubSessionKeyInfo = Optional.empty();
        private  Optional<byte[]> mSusAdditionalParams = Optional.empty();

        @NonNull
        Builder setUwbSessionKeyInfo(@NonNull byte[] uwbSessionKeyInfo) {
            mUwbSessionKeyInfo = Optional.of(uwbSessionKeyInfo);
            return this;
        }

        @NonNull
        Builder setUwbSubSessionKeyInfo(@NonNull byte[] uwbSubSessionKeyInfo) {
            mUwbSubSessionKeyInfo = Optional.of(uwbSubSessionKeyInfo);
            return this;
        }

        @NonNull
        Builder setSusAdditionalParams(@NonNull byte[] susAdditionalParams) {
            mSusAdditionalParams = Optional.of(susAdditionalParams);
            return this;
        }

        @NonNull
        SecureRangingInfo build() {
            return new SecureRangingInfo(
                    mUwbSessionKeyInfo, mUwbSubSessionKeyInfo, mSusAdditionalParams);
        }
    }
}
