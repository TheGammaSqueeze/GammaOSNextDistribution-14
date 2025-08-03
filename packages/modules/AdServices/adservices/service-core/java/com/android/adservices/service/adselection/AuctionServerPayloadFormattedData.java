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

/**
 * Represents formatted data, input to {@link AuctionServerPayloadExtractor#extract} and output from
 * {@link AuctionServerPayloadFormatter#apply}
 */
public class AuctionServerPayloadFormattedData {
    private final byte[] mData;

    private AuctionServerPayloadFormattedData(byte[] data) {
        this.mData = data;
    }

    /**
     * @return data
     */
    public byte[] getData() {
        return Arrays.copyOf(mData, mData.length);
    }

    /** Creates {@link AuctionServerPayloadFormattedData} */
    public static AuctionServerPayloadFormattedData create(byte[] data) {
        return new AuctionServerPayloadFormattedData(Arrays.copyOf(data, data.length));
    }
}
