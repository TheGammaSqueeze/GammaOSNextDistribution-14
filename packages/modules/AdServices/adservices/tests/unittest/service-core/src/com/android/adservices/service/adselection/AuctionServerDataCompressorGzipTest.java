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

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AuctionServerDataCompressorGzipTest {
    private static final String COMPRESSIBLE_STRING =
            "repetitive test string repetitive test string repetitive test string";
    private AuctionServerDataCompressorGzip mDataCompressorV0;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mDataCompressorV0 = new AuctionServerDataCompressorGzip();
    }

    @Test
    public void testCompress() {
        AuctionServerDataCompressor.UncompressedData uncompressedData =
                AuctionServerDataCompressor.UncompressedData.create(COMPRESSIBLE_STRING.getBytes());
        AuctionServerDataCompressor.CompressedData compressedData =
                mDataCompressorV0.compress(uncompressedData);
        Assert.assertTrue(compressedData.getData().length < uncompressedData.getData().length);
    }

    @Test
    public void testDecompress() {
        AuctionServerDataCompressor.UncompressedData uncompressedData =
                AuctionServerDataCompressor.UncompressedData.create(COMPRESSIBLE_STRING.getBytes());
        AuctionServerDataCompressor.CompressedData compressedData =
                mDataCompressorV0.compress(uncompressedData);
        Assert.assertTrue(compressedData.getData().length < uncompressedData.getData().length);
        AuctionServerDataCompressor.UncompressedData decompressedData =
                mDataCompressorV0.decompress(compressedData);
        Assert.assertArrayEquals(decompressedData.getData(), COMPRESSIBLE_STRING.getBytes());
    }
}
