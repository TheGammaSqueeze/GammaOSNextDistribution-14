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

package com.android.server.uwb.discovery.info;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.primitives.Bytes;
import com.google.uwb.support.fira.FiraProtocolVersion;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds data of the FiRa UWB Connector Capabilities according to FiRa BLE OOB v1.0 and CSML v1.0
 * specification.
 */
public class FiraConnectorCapabilities {
    private static final String TAG = FiraConnectorCapabilities.class.getSimpleName();

    // The minimum size of the full data
    private static final int MIN_FIRA_CONNECTOR_CAPABILITIES_SIZE = 7;

    /** FiRa Connector Protocol Version */
    public final FiraProtocolVersion protocolVersion;

    /**
     * Optimized Data Packet size. Maximum value should be set to ATT_MTU – 3 (size in octets). The
     * default value is 20 (based on default ATT_MTU size which is 23 bytes).
     */
    public final int optimizedDataPacketSize;

    /**
     * Maximum Message buffer size. The maximum FiRa Connector Message size which can be
     * re-assembled on its side throughout fragmented session. This value is implementation specific
     * and the minimal value required by 1.0 specification version is 263 (size is in octets).
     */
    public final int maxMessageBufferSize;

    /**
     * Maximum number of concurrent fragmented Message session supported. How many independent
     * fragmented sessions (each with different SECIDs) it can handle. The value is implementation
     * specific and by default it is 1. Note that support of multiple concurrent sessions means that
     * GATT Server must have independent “IN” and “OUT” buffers for each session (each pair
     * dedicated for one “slot” linked to one SECID referenced in FiRa Connector Data Packet
     * header).
     */
    public final int maxConcurrentFragmentedMessageSessionSupported;

    /*List of Secure Components
     */
    @Nullable public final List<SecureComponentInfo> secureComponentInfos;

    /**
     * Generate the FiraConnectorCapabilities from raw bytes array.
     *
     * @param bytes byte array containing the FiRa UWB Connector Capabilities data encoding based on
     *     the FiRa specification.
     * @return decode bytes into {@link FiraConnectorCapabilities}, else null if invalid.
     */
    @Nullable
    public static FiraConnectorCapabilities fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            Log.w(TAG, "Failed to convert empty into FiRa Connector Capabilities.");
            return null;
        }

        if (bytes.length < MIN_FIRA_CONNECTOR_CAPABILITIES_SIZE) {
            Log.w(
                    TAG,
                    "Failed to convert bytes into FiRa Connector Capabilities due to invalid data"
                            + " size.");
            return null;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        FiraConnectorCapabilities.Builder builder = new FiraConnectorCapabilities.Builder();

        byte[] protocolVersionBytes = new byte[FiraProtocolVersion.bytesUsed()];
        byteBuffer.get(protocolVersionBytes);
        builder.setProtocolVersion(
                FiraProtocolVersion.fromBytes(protocolVersionBytes, /*startIndex=*/ 0));

        builder.setOptimizedDataPacketSize((int) (byteBuffer.getShort() & 0xFFFF));
        builder.setMaxMessageBufferSize((int) (byteBuffer.getShort() & 0xFFFF));
        builder.setMaxConcurrentFragmentedMessageSessionSupported(
                (int) (byteBuffer.get() & 0x00FF));

        int info_size = byteBuffer.remaining() / SecureComponentInfo.size();

        for (int i = 0; i < info_size; i++) {
            byte[] secureComponentInfoBytes = new byte[SecureComponentInfo.size()];
            byteBuffer.get(secureComponentInfoBytes);
            SecureComponentInfo info = SecureComponentInfo.fromBytes(secureComponentInfoBytes);
            builder.addSecureComponentInfo(info);
        }

        return builder.build();
    }

    /**
     * Generate raw bytes array from FiraConnectorCapabilities.
     *
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public byte[] toBytes() {
        byte[] optimizedDataPacketSizeBytes =
                DataTypeConversionUtil.i32ToByteArray(optimizedDataPacketSize);
        byte[] maxMessageBufferSizeBytes =
                DataTypeConversionUtil.i32ToByteArray(maxMessageBufferSize);
        byte[] maxConcurrentFragmentedMessageSessionSupportedBytes =
                DataTypeConversionUtil.i32ToByteArray(
                        maxConcurrentFragmentedMessageSessionSupported);
        byte[] data =
                new byte[] {
                    optimizedDataPacketSizeBytes[2],
                    optimizedDataPacketSizeBytes[3],
                    maxMessageBufferSizeBytes[2],
                    maxMessageBufferSizeBytes[3],
                    maxConcurrentFragmentedMessageSessionSupportedBytes[3]
                };

        data = Bytes.concat(protocolVersion.toBytes(), data);

        for (SecureComponentInfo i : secureComponentInfos) {
            data = Bytes.concat(data, SecureComponentInfo.toBytes(i));
        }

        return data;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FiraConnectorCapabilities: protocolVersion=")
                .append(protocolVersion)
                .append(" optimizedDataPacketSize=")
                .append(optimizedDataPacketSize)
                .append(" maxMessageBufferSize=")
                .append(maxMessageBufferSize)
                .append(" maxConcurrentFragmentedMessageSessionSupported=")
                .append(maxConcurrentFragmentedMessageSessionSupported)
                .append(" secureComponentInfos=")
                .append(Arrays.toString(secureComponentInfos.toArray()));
        return sb.toString();
    }

    private FiraConnectorCapabilities(
            FiraProtocolVersion protocolVersion,
            int optimizedDataPacketSize,
            int maxMessageBufferSize,
            int maxConcurrentFragmentedMessageSessionSupported,
            List<SecureComponentInfo> secureComponentInfos) {
        this.protocolVersion = protocolVersion;
        this.optimizedDataPacketSize = optimizedDataPacketSize;
        this.maxMessageBufferSize = maxMessageBufferSize;
        this.maxConcurrentFragmentedMessageSessionSupported =
                maxConcurrentFragmentedMessageSessionSupported;
        this.secureComponentInfos = secureComponentInfos;
    }

    /** Builder for {@link FiraConnectorCapabilities}. */
    public static final class Builder {
        /** Base FiRa OOB spec version: 1.0 */
        private FiraProtocolVersion mProtocolVersion =
                new FiraProtocolVersion(/*major=*/ 1, /*minor=*/ 0);

        /** FiRa OOB 1.0 spec default: 20 */
        private int mOptimizedDataPacketSize = 20;

        /** Minimal value required by FiRa OOB 1.0 spec: 263. */
        private int mMaxMessageBufferSize = 263;

        /** FiRa OOB 1.0 spec default: 1 */
        private int mMaxConcurrentFragmentedMessageSessionSupported = 1;

        @Nullable
        private List<SecureComponentInfo> mSecureComponentInfos =
                new ArrayList<SecureComponentInfo>();

        /**
         * Set the protocol version of the FiRa connector capabilities.
         *
         * @param protocolVersion The protocol version.
         * @throws IllegalArgumentException If the {@code protocolVersion} is below 1.0.
         */
        public Builder setProtocolVersion(FiraProtocolVersion protocolVersion) {
            if (protocolVersion.getMajor() < 1) {
                throw new IllegalArgumentException("protocolVersion is below minimum value 1.0");
            }
            mProtocolVersion = protocolVersion;
            return this;
        }

        /**
         * Set the optimized data packet size of the FiRa connector capabilities.
         *
         * @param optimizedDataPacketSize The optimized data packet size.
         * @throws IllegalArgumentException If the {@code optimizedDataPacketSize} is less than 1.
         */
        public Builder setOptimizedDataPacketSize(int optimizedDataPacketSize) {
            if (optimizedDataPacketSize < 1) {
                throw new IllegalArgumentException(
                        "optimizedDataPacketSize is below minimum value 1: "
                                + optimizedDataPacketSize);
            }
            mOptimizedDataPacketSize = optimizedDataPacketSize;
            return this;
        }

        /**
         * Set the maximum message buffer size of the FiRa connector capabilities.
         *
         * @param maxMessageBufferSize The maximum message buffer size.
         * @throws IllegalArgumentException If the {@code maxMessageBufferSize} is less than 263.
         */
        public Builder setMaxMessageBufferSize(int maxMessageBufferSize) {
            if (maxMessageBufferSize < 263) {
                throw new IllegalArgumentException(
                        "maxMessageBufferSize is below minimum value 263: " + maxMessageBufferSize);
            }
            mMaxMessageBufferSize = maxMessageBufferSize;
            return this;
        }

        /**
         * Set the maximum concurrent fragmented message session supported of the FiRa connector
         * capabilities.
         *
         * @param maxConcurrentFragmentedMessageSessionSupported The maximum concurrent fragmented
         *     message session supported.
         * @throws IllegalArgumentException If the {@code
         *     maxConcurrentFragmentedMessageSessionSupported} is less than 1.
         */
        public Builder setMaxConcurrentFragmentedMessageSessionSupported(
                int maxConcurrentFragmentedMessageSessionSupported) {
            if (maxConcurrentFragmentedMessageSessionSupported < 1) {
                throw new IllegalArgumentException(
                        "maxConcurrentFragmentedMessageSessionSupported is below minimum value 1: "
                                + maxConcurrentFragmentedMessageSessionSupported);
            }
            mMaxConcurrentFragmentedMessageSessionSupported =
                    maxConcurrentFragmentedMessageSessionSupported;
            return this;
        }

        /**
         * Add a secure component info of the FiRa connector capabilities.
         *
         * @param secureComponentInfo A secure component info.
         * @throws IllegalArgumentException If the {@code secureComponentInfo} is null.
         */
        public Builder addSecureComponentInfo(SecureComponentInfo secureComponentInfo) {
            if (secureComponentInfo == null) {
                throw new IllegalArgumentException("secureComponentInfo is null");
            }
            mSecureComponentInfos.add(secureComponentInfo);
            return this;
        }

        /** Build the {@link FiraConnectorCapabilities}. */
        public FiraConnectorCapabilities build() {
            return new FiraConnectorCapabilities(
                    mProtocolVersion,
                    mOptimizedDataPacketSize,
                    mMaxMessageBufferSize,
                    mMaxConcurrentFragmentedMessageSessionSupported,
                    mSecureComponentInfos);
        }
    }
}
