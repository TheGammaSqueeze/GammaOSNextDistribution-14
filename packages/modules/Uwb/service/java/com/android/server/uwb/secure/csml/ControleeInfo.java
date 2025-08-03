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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.params.TlvBuffer;
import com.android.server.uwb.params.TlvDecoderBuffer;

import java.util.Arrays;
import java.util.Optional;

/**
 * Provide the controlee info, see CSML 8.5.3.2
 */
public class ControleeInfo {

    public static final String TAG = ControleeInfo.class.getSimpleName();

    public static final int UWB_CONTROLEE_INFO_VERSION_HEADER = 0x80;
    public static int UWB_CONTROLEE_INFO_VERSION_MINOR = 1;
    public static int UWB_CONTROLEE_INFO_VERSION_MAJOR = 1;
    public static int UWB_CONTROLEE_INFO_VERSION_MINOR_CURRENT = 1;
    public static int UWB_CONTROLEE_INFO_VERSION_MAJOR_CURRENT = 1;
    public static final int UWB_CAPABILITY = 0xA3;
    public Optional<UwbCapability> mUwbCapability;
    public static final int CONTROLEE_INFO_MAX_COUNT = 6;


    public ControleeInfo(Optional<UwbCapability> uwbCapability) {
        mUwbCapability = uwbCapability;
    }

    /**
     * Converts the controlee info to the bytes which are combined per the TLV of CSML 8.5.3.2.
     */
    @NonNull
    public byte[] toBytes() {
        TlvBuffer.Builder controleeInfoBuilder = new TlvBuffer.Builder()
                .putByteArray(UWB_CONTROLEE_INFO_VERSION_HEADER, new byte[]{
                        (byte) UWB_CONTROLEE_INFO_VERSION_MAJOR_CURRENT,
                        (byte) UWB_CONTROLEE_INFO_VERSION_MINOR_CURRENT});

        mUwbCapability.ifPresent(uwbCapability -> controleeInfoBuilder.putByteArray(UWB_CAPABILITY,
                uwbCapability.toBytes()));

        return controleeInfoBuilder.build().getByteArray();

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

    /**
     * Converts the {@link ControleeInfo} from the data stream, which is encoded
     * per the CSML 8.5.3.2.
     *
     * @return null if the data cannot be decoded per spec.
     */
    @Nullable
    public static ControleeInfo fromBytes(@NonNull byte[] data) {
        TlvDecoderBuffer tlvs = new TlvDecoderBuffer(data, CONTROLEE_INFO_MAX_COUNT);
        tlvs.parse();

        if (isPresent(tlvs, UWB_CONTROLEE_INFO_VERSION_HEADER)) {
            byte[] controleeInfoVersion = tlvs.getByteArray(UWB_CONTROLEE_INFO_VERSION_HEADER);
            if (controleeInfoVersion.length == 2 && versionCheck(controleeInfoVersion)) {
                Builder controleeInfoBuilder = new Builder();
                if (isPresent(tlvs, UWB_CAPABILITY)) {
                    byte[] uwbCapability = tlvs.getByteArray(UWB_CAPABILITY);
                    controleeInfoBuilder.setUwbCapability(
                            UwbCapability.fromBytes(uwbCapability));
                }
                return controleeInfoBuilder.build();
            }
            Log.e(TAG, "UWB_CONTROLEE_INFO_VERSION " + Arrays.toString(controleeInfoVersion)
                    + " Not supported");
        } else {
            Log.e(TAG, "Controlee info version is not included. Failure !");
        }
        return new ControleeInfo(Optional.empty());
    }

    private static boolean versionCheck(byte[] controleeInfoVersion) {
        return controleeInfoVersion[0] == UWB_CONTROLEE_INFO_VERSION_MAJOR
                && controleeInfoVersion[1] == UWB_CONTROLEE_INFO_VERSION_MINOR;
    }

    /** Builder */
    public static class Builder {
        private Optional<UwbCapability> mUwbCapability = Optional.empty();

        /** set {@link UwbCapability} in the ControleeInfo. */
        public ControleeInfo.Builder setUwbCapability(UwbCapability uwbCapability) {
            mUwbCapability = Optional.of(uwbCapability);
            return this;
        }

        /** build the ControleeInfo instance */
        public ControleeInfo build() {
            return new ControleeInfo(mUwbCapability);
        }
    }
}
