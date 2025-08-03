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

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.profiling.Tracing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Wrapper class for data compression and decompression using GZIP */
public class AuctionServerDataCompressorGzip implements AuctionServerDataCompressor {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final int VERSION = 2;
    private static final String IO_ERROR_DURING_COMPRESSION = "IOException when compressing data.";
    private static final String IO_ERROR_DURING_DECOMPRESSION =
            "IOException when decompressing data.";

    /** Compresses given data using GZIP */
    public CompressedData compress(@NonNull UncompressedData uncompressedData) {
        Objects.requireNonNull(uncompressedData);

        int traceCookie = Tracing.beginAsyncSection(Tracing.AUCTION_SERVER_GZIP_COMPRESS);
        sLogger.v("Compression request for each BuyerInput with version " + VERSION);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
            gzipOutputStream.write(uncompressedData.getData());
            gzipOutputStream.close();
        } catch (IOException e) {
            sLogger.e(IO_ERROR_DURING_COMPRESSION);
            throw new UncheckedIOException(e);
        }
        CompressedData compressedData = CompressedData.create(byteArrayOutputStream.toByteArray());
        Tracing.endAsyncSection(Tracing.AUCTION_SERVER_GZIP_COMPRESS, traceCookie);
        return compressedData;
    }

    /** Decompresses data compressed by GZIP */
    public UncompressedData decompress(@NonNull CompressedData uncompressedData) {
        Objects.requireNonNull(uncompressedData);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ByteArrayInputStream byteArrayInputStream =
                    new ByteArrayInputStream(uncompressedData.getData());
            GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = gzipInputStream.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            gzipInputStream.close();
        } catch (IOException e) {
            sLogger.e(IO_ERROR_DURING_DECOMPRESSION);
            throw new UncheckedIOException(e);
        }

        return UncompressedData.create(byteArrayOutputStream.toByteArray());
    }
}
