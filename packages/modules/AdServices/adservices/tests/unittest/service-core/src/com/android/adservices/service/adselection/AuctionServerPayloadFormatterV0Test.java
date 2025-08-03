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

import static com.android.adservices.service.adselection.AuctionServerPayloadFormatterV0.DATA_SIZE_PADDING_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormatterV0.PAYLOAD_SIZE_EXCEEDS_LIMIT;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.BYTES_CONVERSION_FACTOR;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.META_INFO_LENGTH_BYTE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.exceptions.UnsupportedPayloadSizeException;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

public class AuctionServerPayloadFormatterV0Test {
    private static final int VALID_COMPRESSOR_VERSION = 0;
    private static final byte EXPECTED_META_INFO_BYTE =
            0; // both formatter and compressor versions are 0
    private static final int DATA_START = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE;
    private static final int MAXIMUM_PAYLOAD_SIZE_IN_BYTES =
            Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES.stream().max(Integer::compare).get();
    private AuctionServerPayloadFormatterV0 mAuctionServerPayloadFormatterV0;

    private AdServicesLogger mAdServicesLoggerSpy;

    private static byte[] getRandomByteArray(int size) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] result = new byte[size];
        secureRandom.nextBytes(result);
        return result;
    }

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mAdServicesLoggerSpy = Mockito.spy(AdServicesLoggerImpl.getInstance());
        mAuctionServerPayloadFormatterV0 =
                new AuctionServerPayloadFormatterV0(
                        Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES);
    }

    @Test
    public void testEmptyInput_roundupToMinimumBucketSize_success() {
        testDataPaddingAndDispadding(new byte[] {}, 1024);
    }

    @Test
    public void testInputSmallerThanMinimumBucketSize_roundupToMinimum_success() {
        testDataPaddingAndDispadding(new byte[] {2, 3, 4}, 1024);
    }

    @Test
    public void testInputSmallerThenABucketSize_roundUpToUseSmallestAvailableBucket_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(3 * BYTES_CONVERSION_FACTOR), 4 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputEqualToAvailableBucketSize_useThatSize_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(
                        4 * BYTES_CONVERSION_FACTOR
                                - META_INFO_LENGTH_BYTE
                                - DATA_SIZE_PADDING_LENGTH_BYTE),
                4 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputLargerThanABucketSize_roundUpToUseSmallestAvailableBucketSize_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(4 * BYTES_CONVERSION_FACTOR), 8 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputSizeEqualToLargestAvailableBucketSize_useTheBucket_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(
                        MAXIMUM_PAYLOAD_SIZE_IN_BYTES
                                - META_INFO_LENGTH_BYTE
                                - DATA_SIZE_PADDING_LENGTH_BYTE),
                MAXIMUM_PAYLOAD_SIZE_IN_BYTES);
    }

    @Test
    public void testReuseFormatter() {
        testDataPaddingAndDispadding(
                getRandomByteArray(4 * BYTES_CONVERSION_FACTOR), 8 * BYTES_CONVERSION_FACTOR);
        testDataPaddingAndDispadding(
                getRandomByteArray(
                        4 * BYTES_CONVERSION_FACTOR
                                - META_INFO_LENGTH_BYTE
                                - DATA_SIZE_PADDING_LENGTH_BYTE),
                4 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputSizeLargerThenLargestBucket_throwsPayloadSizeException() {
        AuctionServerPayloadUnformattedData input =
                AuctionServerPayloadUnformattedData.create(
                        getRandomByteArray(MAXIMUM_PAYLOAD_SIZE_IN_BYTES));
        UnsupportedPayloadSizeException exception =
                assertThrows(
                        UnsupportedPayloadSizeException.class,
                        () ->
                                mAuctionServerPayloadFormatterV0.apply(
                                        input, VALID_COMPRESSOR_VERSION));
        assertThat(exception.getMessage()).isEqualTo(PAYLOAD_SIZE_EXCEEDS_LIMIT);
        assertThat(exception.getPayloadSizeKb()).isEqualTo(MAXIMUM_PAYLOAD_SIZE_IN_BYTES / 1000);
    }

    public void testDataPaddingAndDispadding(byte[] data, int expectedSizeInBytes) {
        AuctionServerPayloadUnformattedData input =
                AuctionServerPayloadUnformattedData.create(data);

        AuctionServerPayloadFormattedData formatted =
                mAuctionServerPayloadFormatterV0.apply(input, VALID_COMPRESSOR_VERSION);
        AuctionServerPayloadUnformattedData unformattedData =
                mAuctionServerPayloadFormatterV0.extract(formatted);

        assertArrayEquals(
                "Formatted data not un-formatted correctly.", data, unformattedData.getData());

        validateFormattedData(data, expectedSizeInBytes, formatted);
    }

    private void validateFormattedData(
            byte[] data, int expectedSizeInBytes, AuctionServerPayloadFormattedData formatted) {
        assertEquals(
                "data length (bucket size) mismatch.",
                expectedSizeInBytes,
                formatted.getData().length);
        assertEquals("meta info byte mismatch.", EXPECTED_META_INFO_BYTE, formatted.getData()[0]);
        assertEquals(
                "data size bytes mismatch.",
                data.length,
                ByteBuffer.wrap(
                                formatted.getData(),
                                META_INFO_LENGTH_BYTE,
                                DATA_SIZE_PADDING_LENGTH_BYTE)
                        .getInt());
        assertArrayEquals(
                "Original data and packed data mismatch.",
                data,
                Arrays.copyOfRange(formatted.getData(), DATA_START, DATA_START + data.length));

        for (int i = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + data.length;
                i < expectedSizeInBytes;
                i++) {
            assertEquals("padding bytes not set to zero", (byte) 0, formatted.getData()[i]);
        }
    }
}
