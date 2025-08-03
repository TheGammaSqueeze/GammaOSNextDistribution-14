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

/** Factory for {@link AuctionServerDataCompressor} */
public class AuctionServerDataCompressorFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String NO_IMPLEMENTATION_FOUND =
            "No data compressor implementation found for version %s";

    /** Returns an implementation for the {@link AuctionServerDataCompressor} */
    public static AuctionServerDataCompressor getDataCompressor(int version) {
        if (version == AuctionServerDataCompressorGzip.VERSION) {
            return new AuctionServerDataCompressorGzip();
        }

        String errMsg = String.format(NO_IMPLEMENTATION_FOUND, version);
        sLogger.e(errMsg);
        throw new IllegalArgumentException(errMsg);
    }
}
