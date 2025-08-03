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

package com.android.adservices.service.adselection;

import com.android.adservices.LoggerFactory;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Utility methods and constants for auction server payload formatting.
 *
 * <p>This interface also includes methods for creating and extracting meta info byte.
 */
public class AuctionServerPayloadFormattingUtil {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int ONE_BYTE_IN_BITS = 8;
    static final int BYTES_CONVERSION_FACTOR = 1024;
    @VisibleForTesting static final int PAYLOAD_FORMAT_VERSION_LENGTH_BITS = 3;
    @VisibleForTesting static final int COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS = 5;
    static final int META_INFO_LENGTH_BYTE =
            (PAYLOAD_FORMAT_VERSION_LENGTH_BITS + COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS)
                    / ONE_BYTE_IN_BITS;

    /** Creates meta info byte from given version integers. */
    static byte getMetaInfoByte(int compressionVersion, int formatterVersion) {
        int formatterVersionLowerLimit = 0;
        int formatterVersionUpperLimit = (1 << PAYLOAD_FORMAT_VERSION_LENGTH_BITS) - 1;
        if (formatterVersion < formatterVersionLowerLimit
                || formatterVersion > formatterVersionUpperLimit) {
            String err =
                    String.format(
                            "Formatter version must be between %s and %s. Given version: %s",
                            formatterVersionLowerLimit,
                            formatterVersionUpperLimit,
                            formatterVersion);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }

        int compressionVersionLowerLimit = 0;
        int compressionVersionUpperLimit = (1 << COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS) - 1;
        if (compressionVersion < compressionVersionLowerLimit
                || compressionVersion > compressionVersionUpperLimit) {
            String err =
                    String.format(
                            "Compression version must be between %s and %s. Given version: %s",
                            compressionVersionLowerLimit,
                            compressionVersionUpperLimit,
                            compressionVersion);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }

        // Left-shift the compressionVersion by the length of formatter bits, then bitwise OR with
        // formatterVersion.
        return (byte)
                ((formatterVersion << COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS)
                        | compressionVersion);
    }

    /**
     * Extracts compression version from a byte. The compression version is the last 5 bits in the
     * meta info byte.
     */
    static int extractCompressionVersion(byte metaInfoByte) {
        // 0x1f (which is 31 in decimal or 11111 in binary) is used to make sure we only
        // keep the lower 5 bits of the byte (which is the compression version)
        return metaInfoByte & ((1 << COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS) - 1);
    }

    /**
     * Extracts formatter version from a byte. The formatter version is the first 3 bits in the meta
     * info byte.
     */
    static int extractFormatterVersion(byte metaInfoByte) {
        // 0xFF is used to make sure the shift fills with 0s instead of sign-extending
        return (metaInfoByte & 0xFF) >>> COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS;
    }

    private AuctionServerPayloadFormattingUtil() {}
}
