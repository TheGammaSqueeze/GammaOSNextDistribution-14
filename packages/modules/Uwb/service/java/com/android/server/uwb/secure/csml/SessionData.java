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

import static com.android.server.uwb.secure.csml.SecureRangingInfo.SECURE_RANGING_INFO_TAG;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.params.TlvBuffer;
import com.android.server.uwb.params.TlvDecoderBuffer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * The session data used to config UWB session, see CSML 8.5.3.3.
 */
public class SessionData {

    public static final String TAG = SessionData.class.getSimpleName();

    public static final int UWB_SESSION_DATA_VERSION_HEADER = 0x80;
    public static int UWB_SESSION_DATA_VERSION_MINOR = 1;
    public static int UWB_SESSION_DATA_VERSION_MAJOR = 1;
    public static int UWB_SESSION_DATA_VERSION_MINOR_CURRENT = 1;
    public static int UWB_SESSION_DATA_VERSION_MAJOR_CURRENT = 1;
    public static int UWB_SESSION_ID = 0x81;
    public static int UWB_SUB_SESSION_ID = 0x82;

    public static final int CONFIGURATION_PARAMS = 0xA3;
    public Optional<ConfigurationParams> mConfigurationParams;
    public Optional<SecureRangingInfo> mSecureRangingInfo;
    public static int SESSION_DATA_COUNT_MAX = 8;

    public final int mSessionId;
    public final Optional<Integer> mSubSessionId;

    private SessionData(int sessionId, Optional<Integer> subSessionId,
            Optional<ConfigurationParams> configurationParams,
            Optional<SecureRangingInfo> secureRangingInfo) {
        mSessionId = sessionId;
        mSubSessionId = subSessionId;
        mConfigurationParams = configurationParams;
        mSecureRangingInfo = secureRangingInfo;
    }

    /**
     * Convert to raw data represented as TLV according to CSML 8.5.3.3.
     */
    @NonNull
    public byte[] toBytes() {
        TlvBuffer.Builder sessionDataBuilder = new TlvBuffer.Builder()
                .putByteArray(UWB_SESSION_DATA_VERSION_HEADER, new byte[]{
                        (byte) UWB_SESSION_DATA_VERSION_MAJOR_CURRENT,
                        (byte) UWB_SESSION_DATA_VERSION_MINOR_CURRENT});

        sessionDataBuilder.putByteArray(UWB_SESSION_ID, ByteBuffer.allocate(4).putInt(
                mSessionId).array());
        mSubSessionId.ifPresent(
                integer -> sessionDataBuilder.putByteArray(UWB_SUB_SESSION_ID, ByteBuffer.allocate(
                        4).putInt(
                        integer).array()));
        mConfigurationParams.ifPresent(
                configurationParams -> sessionDataBuilder.putByteArray(CONFIGURATION_PARAMS,
                        configurationParams.toBytes()));
        mSecureRangingInfo.ifPresent(
                secureRangingInfo -> sessionDataBuilder.putByteArray(
                        SECURE_RANGING_INFO_TAG, secureRangingInfo.toBytes()));

        return sessionDataBuilder.build().getByteArray();
    }

    private static boolean isPresent(TlvDecoderBuffer tlvDecoderBuffer, int tagType) {
        try {
            tlvDecoderBuffer.getByte(tagType);
        } catch (IllegalArgumentException e) {
            try {
                tlvDecoderBuffer.getByteArray(tagType);
            } catch (IllegalArgumentException e1) {
                return false;
            }
        }
        return true;
    }

    /** Converts {@link SessionData} from the TLV data payload. */
    @Nullable
    public static SessionData fromBytes(@NonNull byte[] data) {
        TlvDecoderBuffer tlvs = new TlvDecoderBuffer(data, SESSION_DATA_COUNT_MAX);
        tlvs.parse();

        if (isPresent(tlvs, UWB_SESSION_DATA_VERSION_HEADER)) {
            byte[] sessionDataVersion = tlvs.getByteArray(UWB_SESSION_DATA_VERSION_HEADER);
            if (sessionDataVersion.length == 2 && versionCheck(sessionDataVersion)) {
                Builder sessionDataBuilder = new Builder();
                if (isPresent(tlvs, UWB_SESSION_ID)) {
                    sessionDataBuilder.setSessionId(
                            ByteBuffer.wrap(tlvs.getByteArray(UWB_SESSION_ID)).getInt());
                }
                if (isPresent(tlvs, UWB_SUB_SESSION_ID)) {
                    sessionDataBuilder.setSubSessionId(
                            ByteBuffer.wrap(tlvs.getByteArray(UWB_SUB_SESSION_ID)).getInt());
                }
                if (isPresent(tlvs, CONFIGURATION_PARAMS)) {
                    byte[] configurationParams = tlvs.getByteArray(CONFIGURATION_PARAMS);
                    sessionDataBuilder.setConfigParams(
                            ConfigurationParams.fromBytes(configurationParams));
                }
                if (isPresent(tlvs, SECURE_RANGING_INFO_TAG)) {
                    byte[] secureRangingInfo = tlvs.getByteArray(SECURE_RANGING_INFO_TAG);
                    sessionDataBuilder.setSecureRangingInfo(
                            SecureRangingInfo.fromBytes(secureRangingInfo));
                }
                return sessionDataBuilder.build();
            }
            Log.e(TAG, "UWB_SESSION_DATA_VERSION " + Arrays.toString(sessionDataVersion)
                    + " Not supported");
        } else {
            Log.e(TAG, "Controlee info version is not included. Failure !");
        }
        return null;
    }

    private static boolean versionCheck(byte[] sessionDataVersion) {
        return sessionDataVersion[0] == UWB_SESSION_DATA_VERSION_MAJOR
                && sessionDataVersion[1] == UWB_SESSION_DATA_VERSION_MINOR;
    }

    /** Builder */
    public static class Builder {
        private int mSessionId;
        private Optional<Integer> mSubSessionId = Optional.empty();
        private Optional<ConfigurationParams> mConfigurationParams = Optional.empty();
        private Optional<SecureRangingInfo> mSecureRangingInfo = Optional.empty();

        public Builder() {
        }

        SessionData.Builder setSessionId(int sessionId) {
            mSessionId = sessionId;
            return this;
        }

        SessionData.Builder setSubSessionId(int subSessionId) {
            mSubSessionId = Optional.of(subSessionId);
            return this;
        }

        @NonNull
        SessionData.Builder setConfigParams(
                @NonNull ConfigurationParams configurationParams) {
            mConfigurationParams = Optional.of(configurationParams);
            return this;
        }

        @NonNull
        SessionData.Builder setSecureRangingInfo(@NonNull SecureRangingInfo secureRangingInfo) {
            mSecureRangingInfo = Optional.of(secureRangingInfo);
            return this;
        }

        @NonNull
        SessionData build() {
            return new SessionData(mSessionId, mSubSessionId,
                    mConfigurationParams, mSecureRangingInfo);
        }
    }
}
