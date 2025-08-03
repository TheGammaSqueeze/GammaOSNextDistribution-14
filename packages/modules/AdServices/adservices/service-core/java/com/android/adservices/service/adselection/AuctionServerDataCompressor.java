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

import java.util.Arrays;

/** Data compressor interface */
public interface AuctionServerDataCompressor {
    /**
     * Buffer size controls the size of the batches we read from the input stream. If the batches
     * are too big then will consume more memory. If the batches are two small then will consume
     * more cycles. This number (1KB) is a commonly used sweet-spot. Can be changed when further
     * data is available.
     */
    int BUFFER_SIZE = 1024;

    /** Compresses buyer input map collected from device */
    CompressedData compress(UncompressedData data);

    /** Decompresses given input */
    UncompressedData decompress(CompressedData compressedData);

    /**
     * Represents formatted data, input to {@link AuctionServerDataCompressor#compress} and output
     * from {@link AuctionServerDataCompressor#decompress}
     */
    class UncompressedData {
        private final byte[] mData;

        private UncompressedData(byte[] data) {
            this.mData = data;
        }

        /**
         * @return data
         */
        public byte[] getData() {
            return Arrays.copyOf(mData, mData.length);
        }

        /** Creates {@link UncompressedData} */
        public static UncompressedData create(byte[] data) {
            return new UncompressedData(Arrays.copyOf(data, data.length));
        }
    }

    /**
     * Represents formatted data, input to {@link AuctionServerDataCompressor#decompress} and output
     * from {@link AuctionServerDataCompressor#compress}
     */
    class CompressedData {
        private final byte[] mData;

        private CompressedData(byte[] data) {
            this.mData = data;
        }

        /**
         * @return data
         */
        public byte[] getData() {
            return Arrays.copyOf(mData, mData.length);
        }

        /** Creates {@link CompressedData} */
        public static CompressedData create(byte[] data) {
            return new CompressedData(Arrays.copyOf(data, data.length));
        }
    }
}
