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

import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.META_INFO_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.getMetaInfoByte;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.annotations.VisibleForTesting;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Data padding and padding removal class. */
public class AuctionServerPayloadFormatterExcessiveMaxSize
        implements AuctionServerPayloadFormatter, AuctionServerPayloadExtractor {
    public static final int VERSION = 1;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private static final String DATA_SIZE_MISMATCH =
            "Data size extracted from padded bytes is longer than the rest of the data";

    @VisibleForTesting static final int DATA_SIZE_PADDING_LENGTH_BYTE = 4;

    AuctionServerPayloadFormatterExcessiveMaxSize() {}

    /**
     * Creates the payload of with minimum size of 1024 bytes and no maximum size.
     *
     * <ul>
     *   <li>First 1 byte represents meta info
     *       <ul>
     *         <li>3 bits for payload format version
     *         <li>5 bits for compression algorithm version
     *       </ul>
     *   <li>Next 4 bytes are size of the given data
     *   <li>Next {@code N} bytes are the given data where N is {@code data.length}
     *   <li>Next {@code bucketSize - N - 4 - 1} are padded zeros
     * </ul>
     */
    public AuctionServerPayloadFormattedData apply(
            @NonNull AuctionServerPayloadUnformattedData unformattedData, int compressorVersion) {
        Objects.requireNonNull(unformattedData);

        int traceCookie = Tracing.beginAsyncSection(Tracing.FORMAT_PAYLOAD_EXCESSIVE_MAX_SIZE);

        byte[] data = unformattedData.getData();

        // Empty payload to fill in
        byte[] payload = new byte[getPayloadBucketSizeInBytes(data.length)];

        // Fill in
        payload[0] = getMetaInfoByte(compressorVersion, VERSION);
        sLogger.v("Meta info byte added: %d", payload[0]);

        byte[] dataSizeBytes =
                ByteBuffer.allocate(DATA_SIZE_PADDING_LENGTH_BYTE).putInt(data.length).array();
        System.arraycopy(
                dataSizeBytes, 0, payload, META_INFO_LENGTH_BYTE, DATA_SIZE_PADDING_LENGTH_BYTE);
        sLogger.v(
                "Data size bytes are added: %s for size: %d",
                Arrays.toString(dataSizeBytes), data.length);
        System.arraycopy(
                data,
                0,
                payload,
                META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE,
                data.length);

        AuctionServerPayloadFormattedData formattedData =
                AuctionServerPayloadFormattedData.create(payload);
        Tracing.endAsyncSection(Tracing.FORMAT_PAYLOAD_EXCESSIVE_MAX_SIZE, traceCookie);
        return formattedData;
    }

    /** Extracts the original payload from padded data and the compression algorithm identifier. */
    public AuctionServerPayloadUnformattedData extract(
            AuctionServerPayloadFormattedData formattedData) {
        byte[] payload = formattedData.getData();

        // Next 4 bytes encode the size of the data
        byte[] sizeBytes = new byte[DATA_SIZE_PADDING_LENGTH_BYTE];
        System.arraycopy(
                payload, META_INFO_LENGTH_BYTE, sizeBytes, 0, DATA_SIZE_PADDING_LENGTH_BYTE);
        int dataSize = ByteBuffer.wrap(sizeBytes).getInt();

        if (META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + dataSize > payload.length) {
            sLogger.e(DATA_SIZE_MISMATCH);
            throw new IllegalArgumentException(DATA_SIZE_MISMATCH);
        }

        // Extract the data
        byte[] data = new byte[dataSize];
        System.arraycopy(
                payload, META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE, data, 0, dataSize);

        return AuctionServerPayloadUnformattedData.create(data);
    }

    private int getPayloadBucketSizeInBytes(int dataLength) {
        int payloadSize_bytes = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + dataLength;

        // TODO(b/285182469): Implement payload size management
        int bucketSize_bytes = 1024;
        while (payloadSize_bytes > bucketSize_bytes) {
            bucketSize_bytes *= 2;
        }
        sLogger.d("Final bucket size in bytes: %d", bucketSize_bytes);
        return bucketSize_bytes;
    }
}
