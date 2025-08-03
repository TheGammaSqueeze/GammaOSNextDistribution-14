/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wifi.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanResult.InformationElement;
import android.util.SparseIntArray;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.MboOceConstants;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.util.InformationElementUtil.ApType6GHz;
import com.android.server.wifi.util.InformationElementUtil.EhtOperation;
import com.android.server.wifi.util.InformationElementUtil.HeOperation;
import com.android.server.wifi.util.InformationElementUtil.HtOperation;
import com.android.server.wifi.util.InformationElementUtil.VhtOperation;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.util.InformationElementUtil}.
 */
@SmallTest
public class InformationElementUtilTest extends WifiBaseTest {

    // SSID Information Element tags
    private static final byte[] TEST_SSID_BYTES_TAG = new byte[] { (byte) 0x00, (byte) 0x0B };
    // SSID Information Element entry used for testing.
    private static final byte[] TEST_SSID_BYTES = "GoogleGuest".getBytes();
    // Valid zero length tag.
    private static final byte[] TEST_VALID_ZERO_LENGTH_TAG =
            new byte[] { (byte) 0x0B, (byte) 0x00 };
    // BSS_LOAD Information Element entry used for testing.
    private static final byte[] TEST_BSS_LOAD_BYTES_IE =
            new byte[] { (byte) 0x0B, (byte) 0x01, (byte) 0x08 };

    /*
     * Function to provide SSID Information Element (SSID = "GoogleGuest").
     *
     * @return byte[] Byte array representing the test SSID
     */
    private byte[] getTestSsidIEBytes() throws IOException {
        return concatenateByteArrays(TEST_SSID_BYTES_TAG, TEST_SSID_BYTES);
    }

    /*
     * Function used to set byte arrays used for testing.
     *
     * @param byteArrays variable number of byte arrays to concatenate
     * @return byte[] Byte array resulting from concatenating the arrays passed to the function
     */
    private static byte[] concatenateByteArrays(byte[]... byteArrays) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (byte[] b : byteArrays) {
            baos.write(b);
        }
        baos.flush();
        return baos.toByteArray();
    }

    /**
     * Test parseInformationElements with an empty byte array.
     * Expect parseInformationElement to return an empty InformationElement array.
     */
    @Test
    public void parseInformationElements_withEmptyByteArray() throws IOException {
        byte[] emptyBytes = new byte[0];
        InformationElement[] results =
                InformationElementUtil.parseInformationElements(emptyBytes);
        assertEquals("parsed results should be empty", 0, results.length);
    }

    /**
     * Test parseInformationElements called with a null parameter.
     * Expect parseInformationElement to return an empty InformationElement array.
     */
    @Test
    public void parseInformationElements_withNullBytes() throws IOException {
        byte[] nullBytes = null;
        InformationElement[] results =
                InformationElementUtil.parseInformationElements(nullBytes);
        assertEquals("parsed results should be empty", 0, results.length);
    }

    /**
     * Test parseInformationElements called with a zero length, and extension id.
     * Expect parseInformationElement to return an empty InformationElement array.
     */
    @Test
    public void parseInformationElements_withZeroLengthAndExtensionId() throws IOException {
        byte[] bytes = { (byte) 0xFF, (byte) 0x00 };
        InformationElement[] results =
                InformationElementUtil.parseInformationElements(bytes);
        assertEquals("parsed results should be empty", 0, results.length);
    }

    /**
     * Test parseInformationElements called with a zero length, and extension id after
     * other IEs.
     * Expect parseInformationElement to parse the IEs prior to the malformed IE.
     */
    @Test
    public void parseInformationElements_withZeroLengthAndExtensionIdAfterAnotherIe()
            throws IOException {
        byte[] malFormedIEbytes = { (byte) 0xFF, (byte) 0x00 };
        byte[] bytes = concatenateByteArrays(TEST_BSS_LOAD_BYTES_IE, malFormedIEbytes);
        InformationElement[] results =
                InformationElementUtil.parseInformationElements(bytes);
        assertEquals("parsed results should have 1 IE", 1, results.length);
        assertEquals("Parsed element should be a BSS_LOAD tag",
                InformationElement.EID_BSS_LOAD, results[0].id);
    }

    /*
     * Test parseInformationElements with a single element represented in the byte array.
     * Expect a single element to be returned in the InformationElements array.  The
     * length of this array should be 1 and the contents should be valid.
     *
     * @throws java.io.IOException
     */
    @Test
    public void parseInformationElements_withSingleElement() throws IOException {
        byte[] ssidBytes = getTestSsidIEBytes();

        InformationElement[] results =
                InformationElementUtil.parseInformationElements(ssidBytes);
        assertEquals("Parsed results should have 1 IE", 1, results.length);
        assertEquals("Parsed result should be a ssid", InformationElement.EID_SSID, results[0].id);
        assertArrayEquals("parsed SSID does not match input",
                TEST_SSID_BYTES, results[0].bytes);
    }

    /*
     * Test parseInformationElement with extra padding in the data to parse.
     * Expect the function to return the SSID information element.
     *
     * Note: Experience shows that APs often pad messages with 0x00.  This happens to be the tag for
     * EID_SSID.  This test checks if padding will be properly discarded.
     *
     * @throws java.io.IOException
     */
    @Test
    public void parseInformationElements_withExtraPadding() throws IOException {
        byte[] paddingBytes = new byte[10];
        Arrays.fill(paddingBytes, (byte) 0x00);
        byte[] ssidBytesWithPadding = concatenateByteArrays(getTestSsidIEBytes(), paddingBytes);

        InformationElement[] results =
                InformationElementUtil.parseInformationElements(ssidBytesWithPadding);
        assertEquals("Parsed results should have 1 IE", 1, results.length);
        assertEquals("Parsed result should be a ssid", InformationElement.EID_SSID, results[0].id);
        assertArrayEquals("parsed SSID does not match input",
                TEST_SSID_BYTES, results[0].bytes);
    }

    /*
     * Test parseInformationElement with two elements where the second element has an invalid
     * length.
     * Expect the function to return the first valid entry and skip the remaining information.
     *
     * Note:  This test partially exposes issues with blindly parsing the data.  A higher level
     * function to validate the parsed data may be added.
     *
     * @throws java.io.IOException
     * */
    @Test
    public void parseInformationElements_secondElementInvalidLength() throws IOException {
        byte[] invalidTag = new byte[] { (byte) 0x01, (byte) 0x08, (byte) 0x08 };
        byte[] twoTagsSecondInvalidBytes = concatenateByteArrays(getTestSsidIEBytes(), invalidTag);

        InformationElement[] results =
                InformationElementUtil.parseInformationElements(twoTagsSecondInvalidBytes);
        assertEquals("Parsed results should have 1 IE", 1, results.length);
        assertEquals("Parsed result should be a ssid.", InformationElement.EID_SSID, results[0].id);
        assertArrayEquals("parsed SSID does not match input",
                TEST_SSID_BYTES, results[0].bytes);
    }

    /*
     * Test parseInformationElements with two valid Information Element entries.
     * Expect the function to return an InformationElement array with two entries containing valid
     * data.
     *
     * @throws java.io.IOException
     */
    @Test
    public void parseInformationElements_twoElements() throws IOException {
        byte[] twoValidTagsBytes =
                concatenateByteArrays(getTestSsidIEBytes(), TEST_BSS_LOAD_BYTES_IE);

        InformationElement[] results =
                InformationElementUtil.parseInformationElements(twoValidTagsBytes);
        assertEquals("parsed results should have 2 elements", 2, results.length);
        assertEquals("First parsed element should be a ssid",
                InformationElement.EID_SSID, results[0].id);
        assertArrayEquals("parsed SSID does not match input",
                TEST_SSID_BYTES, results[0].bytes);
        assertEquals("second element should be a BSS_LOAD tag",
                InformationElement.EID_BSS_LOAD, results[1].id);
        assertEquals("second element should have data of length 1", 1, results[1].bytes.length);
        assertEquals("second element data was not parsed correctly.",
                (byte) 0x08, results[1].bytes[0]);
    }

    /*
     * Test parseInformationElements with two elements where the first information element has a
     * length of zero.
     * Expect the function to return an InformationElement array with two entries containing valid
     * data.
     *
     * @throws java.io.IOException
     */
    @Test
    public void parseInformationElements_firstElementZeroLength() throws IOException {
        byte[] zeroLengthTagWithSSIDBytes =
                concatenateByteArrays(TEST_VALID_ZERO_LENGTH_TAG, getTestSsidIEBytes());

        InformationElement[] results =
                InformationElementUtil.parseInformationElements(zeroLengthTagWithSSIDBytes);
        assertEquals("Parsed results should have 2 elements.", 2, results.length);
        assertEquals("First element tag should be EID_BSS_LOAD",
                InformationElement.EID_BSS_LOAD, results[0].id);
        assertEquals("First element should be length 0", 0, results[0].bytes.length);

        assertEquals("Second element should be a ssid", InformationElement.EID_SSID, results[1].id);
        assertArrayEquals("parsed SSID does not match input",
                TEST_SSID_BYTES, results[1].bytes);
    }

    /*
     * Test parseInformationElements with two elements where the first element has an invalid
     * length.  The invalid length in the first element causes us to miss the start of the second
     * Infomation Element.  This results in a single element in the returned array.
     * Expect the function to return a single entry in an InformationElement array. This returned
     * entry is not validated at this time and does not contain valid data (since the incorrect
     * length was used).
     * TODO: attempt to validate the data and recover as much as possible.  When the follow-on CL
     * is in development, this test will be updated to reflect the change.
     *
     * @throws java.io.IOException
     */
    @Test
    public void parseInformationElements_firstElementWrongLength() throws IOException {
        byte[] invalidLengthTag = new byte[] {(byte) 0x0B, (byte) 0x01 };
        byte[] invalidLengthTagWithSSIDBytes =
                concatenateByteArrays(invalidLengthTag, getTestSsidIEBytes());

        InformationElement[] results =
                InformationElementUtil.parseInformationElements(invalidLengthTagWithSSIDBytes);
        assertEquals("Parsed results should have 1 element", 1, results.length);
        assertEquals("First result should be a EID_BSS_LOAD tag.",
                InformationElement.EID_BSS_LOAD, results[0].id);
        assertEquals("First result should have data of 1 byte", 1, results[0].bytes.length);
        assertEquals("First result should have data set to 0x00",
                invalidLengthTagWithSSIDBytes[2], results[0].bytes[0]);
    }

    /**
     * Test parseInformationElement with an element that uses extension IE
     */
    @Test
    public void parseInformationElementWithExtensionId() throws IOException {
        byte[] testByteArray = new byte[] {(byte) 0xFF, (byte) 0x02, (byte) 0x01, (byte) 0x40};
        InformationElement[] results =
                InformationElementUtil.parseInformationElements(testByteArray);
        assertEquals("Parsed results should have 1 element", 1, results.length);
        assertEquals("First result should have id = EID_EXTENSION_PRESENT",
                InformationElement.EID_EXTENSION_PRESENT, results[0].id);
        assertEquals("First result should have idExt = 0x01", 0x01, results[0].idExt);
        assertEquals("First result should have data of 1 byte", 1, results[0].bytes.length);
        assertEquals("First result should have data set to 0x40",
                testByteArray[3], results[0].bytes[0]);
    }

    /** Verify subelement fragmentation within a fragmented element. */
    @Test
    public void parseInformationElementWithTwoLevelFragmentation() throws IOException {

        /**
         *   Format of the Multi link element
         *
         *   Ext Tag: Multi-Link
         *     Ext Tag length: 578 (Tag len: 579)   [fragmented]
         *     Ext Tag Number: Multi-Link
         *      Multi-Link Control: 0x01b0
         *      Common Info
         *      Subelement ID: Per-STA Profile
         *      Subelement Length: 309              [fragmented]
         *      Per-STA Profile 1
         *        Per-STA Profile, Link-ID = 0
         *      Subelement ID: Per-STA Profile
         *      Subelement Length: 248
         *      Per-STA Profile 2
         *        Per-STA Profile, Link-ID = 1
         *
         *      Basic STA Profile Count: 2
         *      STA Profiles LinkIds: 0_1
         */
        byte[] testByteArray =
                new byte[] {
                    (byte) 0xff, (byte) 0xff, (byte) 0x6b, (byte) 0xb0, (byte) 0x01, (byte) 0x0d,
                    (byte) 0x40, (byte) 0xed, (byte) 0x00, (byte) 0x14, (byte) 0xf9, (byte) 0xf1,
                    (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x42, (byte) 0x00,
                    (byte) 0x00, (byte) 0xff, (byte) 0xf0, (byte) 0x01, (byte) 0x13, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x64,
                    (byte) 0x00, (byte) 0x22, (byte) 0xa8, (byte) 0x31, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x31,
                    (byte) 0x04, (byte) 0x01, (byte) 0x08, (byte) 0x82, (byte) 0x84, (byte) 0x8b,
                    (byte) 0x96, (byte) 0x0c, (byte) 0x12, (byte) 0x18, (byte) 0x24, (byte) 0x03,
                    (byte) 0x01, (byte) 0x06, (byte) 0x07, (byte) 0x06, (byte) 0x55, (byte) 0x53,
                    (byte) 0x20, (byte) 0x01, (byte) 0x0b, (byte) 0x1e, (byte) 0x2a, (byte) 0x01,
                    (byte) 0x00, (byte) 0x32, (byte) 0x04, (byte) 0x30, (byte) 0x48, (byte) 0x60,
                    (byte) 0x6c, (byte) 0x2d, (byte) 0x1a, (byte) 0x8d, (byte) 0x09, (byte) 0x03,
                    (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3d,
                    (byte) 0x16, (byte) 0x06, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x4a,
                    (byte) 0x0e, (byte) 0x14, (byte) 0x00, (byte) 0x0a, (byte) 0x00, (byte) 0x2c,
                    (byte) 0x01, (byte) 0xc8, (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x05,
                    (byte) 0x00, (byte) 0x19, (byte) 0x00, (byte) 0x7f, (byte) 0x08, (byte) 0x05,
                    (byte) 0x00, (byte) 0x0f, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x40, (byte) 0xbf, (byte) 0x0c, (byte) 0x92, (byte) 0x79, (byte) 0x83,
                    (byte) 0x33, (byte) 0xaa, (byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0xaa,
                    (byte) 0xff, (byte) 0x00, (byte) 0x20, (byte) 0xc0, (byte) 0x05, (byte) 0x00,
                    (byte) 0x06, (byte) 0x00, (byte) 0xfc, (byte) 0xff, (byte) 0xff, (byte) 0x1d,
                    (byte) 0x23, (byte) 0x09, (byte) 0x01, (byte) 0x08, (byte) 0x1a, (byte) 0x40,
                    (byte) 0x10, (byte) 0x00, (byte) 0x60, (byte) 0x40, (byte) 0x88, (byte) 0x0f,
                    (byte) 0x43, (byte) 0x81, (byte) 0x1c, (byte) 0x11, (byte) 0x08, (byte) 0x00,
                    (byte) 0xaa, (byte) 0xff, (byte) 0xaa, (byte) 0xff, (byte) 0x1b, (byte) 0x1c,
                    (byte) 0xc7, (byte) 0x71, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0xff,
                    (byte) 0x07, (byte) 0x24, (byte) 0xf4, (byte) 0x3f, (byte) 0x00, (byte) 0x2e,
                    (byte) 0xfc, (byte) 0xff, (byte) 0xff, (byte) 0x0f, (byte) 0x6c, (byte) 0x80,
                    (byte) 0x00, (byte) 0xe0, (byte) 0x01, (byte) 0x03, (byte) 0x00, (byte) 0x18,
                    (byte) 0x36, (byte) 0x08, (byte) 0x12, (byte) 0x00, (byte) 0x44, (byte) 0x44,
                    (byte) 0x44, (byte) 0xff, (byte) 0x06, (byte) 0x6a, (byte) 0x04, (byte) 0x11,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xdd, (byte) 0x17, (byte) 0x8c,
                    (byte) 0xfd, (byte) 0xf0, (byte) 0x01, (byte) 0x01, (byte) 0x02, (byte) 0x01,
                    (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x03, (byte) 0x03,
                    (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x01, (byte) 0xf2,
                    (byte) 0xff, (byte) 0x01, (byte) 0x09, (byte) 0x02, (byte) 0x0f, (byte) 0x00,
                    (byte) 0xdd, (byte) 0x18, (byte) 0x00, (byte) 0x50, (byte) 0xf2, (byte) 0x02,
                    (byte) 0x01, (byte) 0x01, (byte) 0x80, (byte) 0x00, (byte) 0x03, (byte) 0xa4,
                    (byte) 0x00, (byte) 0xfe, (byte) 0x36, (byte) 0x00, (byte) 0x27, (byte) 0xa4,
                    (byte) 0x00, (byte) 0x00, (byte) 0x42, (byte) 0x43, (byte) 0x5e, (byte) 0x00,
                    (byte) 0x62, (byte) 0x32, (byte) 0x2f, (byte) 0x00, (byte) 0xdd, (byte) 0x16,
                    (byte) 0x8c, (byte) 0xfd, (byte) 0xf0, (byte) 0x04, (byte) 0x00, (byte) 0x00,
                    (byte) 0x49, (byte) 0x4c, (byte) 0x51, (byte) 0x03, (byte) 0x02, (byte) 0x09,
                    (byte) 0x72, (byte) 0x01, (byte) 0xcb, (byte) 0x17, (byte) 0x00, (byte) 0x00,
                    (byte) 0x09, (byte) 0x11, (byte) 0x00, (byte) 0x00, (byte) 0xdd, (byte) 0x07,
                    (byte) 0x8c, (byte) 0xfd, (byte) 0xf0, (byte) 0x04, (byte) 0x01, (byte) 0x01,
                    (byte) 0x00, (byte) 0xff, (byte) 0x06, (byte) 0x38, (byte) 0x03, (byte) 0x20,
                    (byte) 0x23, (byte) 0xc3, (byte) 0x00, (byte) 0x00, (byte) 0xf8, (byte) 0xf1,
                    (byte) 0x01, (byte) 0x13, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x02, (byte) 0x64, (byte) 0x00, (byte) 0x0c, (byte) 0x74,
                    (byte) 0x19, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                    (byte) 0x00, (byte) 0x03, (byte) 0x11, (byte) 0x05, (byte) 0x07, (byte) 0x0a,
                    (byte) 0x55, (byte) 0x53, (byte) 0x04, (byte) 0xc9, (byte) 0x83, (byte) 0x00,
                    (byte) 0x21, (byte) 0x33, (byte) 0x00, (byte) 0x00, (byte) 0x23, (byte) 0x02,
                    (byte) 0x13, (byte) 0x00, (byte) 0x7f, (byte) 0x0b, (byte) 0x04, (byte) 0x00,
                    (byte) 0x4f, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                    (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0xc3, (byte) 0x05, (byte) 0x53,
                    (byte) 0x3c, (byte) 0x3c, (byte) 0x3c, (byte) 0x3c, (byte) 0xc3, (byte) 0x05,
                    (byte) 0x13, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0xff,
                    (byte) 0x27, (byte) 0x23, (byte) 0x09, (byte) 0x01, (byte) 0x08, (byte) 0x1a,
                    (byte) 0x40, (byte) 0x10, (byte) 0x0c, (byte) 0x63, (byte) 0x40, (byte) 0x88,
                    (byte) 0xfd, (byte) 0x5b, (byte) 0x81, (byte) 0x1c, (byte) 0x11, (byte) 0x08,
                    (byte) 0x00, (byte) 0xaa, (byte) 0xff, (byte) 0xaa, (byte) 0xff, (byte) 0xaa,
                    (byte) 0xff, (byte) 0xaa, (byte) 0xff, (byte) 0x7b, (byte) 0x1c, (byte) 0xc7,
                    (byte) 0x71, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0x1c, (byte) 0xc7,
                    (byte) 0x71, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0xff, (byte) 0x0c,
                    (byte) 0x24, (byte) 0xf4, (byte) 0x3f, (byte) 0x02, (byte) 0x13, (byte) 0xfc,
                    (byte) 0xff, (byte) 0x45, (byte) 0x03, (byte) 0x47, (byte) 0x4f, (byte) 0x01,
                    (byte) 0xff, (byte) 0x03, (byte) 0x3b, (byte) 0xb8, (byte) 0x36, (byte) 0xff,
                    (byte) 0x12, (byte) 0x6c, (byte) 0x00, (byte) 0x00, (byte) 0xe0, (byte) 0x1f,
                    (byte) 0x1b, (byte) 0x00, (byte) 0x18, (byte) 0x36, (byte) 0xd8, (byte) 0x36,
                    (byte) 0x00, (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0x44,
                    (byte) 0x44, (byte) 0xff, (byte) 0x06, (byte) 0x6a, (byte) 0x04, (byte) 0x11,
                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xdd, (byte) 0x17, (byte) 0x8c,
                    (byte) 0xfd, (byte) 0xf0, (byte) 0x01, (byte) 0x01, (byte) 0x02, (byte) 0x01,
                    (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x03, (byte) 0x03,
                    (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x01, (byte) 0x01,
                    (byte) 0x09, (byte) 0x02, (byte) 0x0f, (byte) 0x0f, (byte) 0xf2, (byte) 0x45,
                    (byte) 0xdd, (byte) 0x18, (byte) 0x00, (byte) 0x50, (byte) 0xf2, (byte) 0x02,
                    (byte) 0x01, (byte) 0x01, (byte) 0x80, (byte) 0x00, (byte) 0x03, (byte) 0xa4,
                    (byte) 0x00, (byte) 0x00, (byte) 0x27, (byte) 0xa4, (byte) 0x00, (byte) 0x00,
                    (byte) 0x42, (byte) 0x43, (byte) 0x5e, (byte) 0x00, (byte) 0x62, (byte) 0x32,
                    (byte) 0x2f, (byte) 0x00, (byte) 0xdd, (byte) 0x16, (byte) 0x8c, (byte) 0xfd,
                    (byte) 0xf0, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x49, (byte) 0x4c,
                    (byte) 0x51, (byte) 0x03, (byte) 0x02, (byte) 0x09, (byte) 0x72, (byte) 0x01,
                    (byte) 0xcb, (byte) 0x17, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x11,
                    (byte) 0x00, (byte) 0x00, (byte) 0xdd, (byte) 0x07, (byte) 0x8c, (byte) 0xfd,
                    (byte) 0xf0, (byte) 0x04, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0xff,
                    (byte) 0x08, (byte) 0x38, (byte) 0x05, (byte) 0x2d, (byte) 0x3d, (byte) 0xbf,
                    (byte) 0xc0, (byte) 0xc9, (byte) 0x00
                };

        final int MAX_NUM_IES = 3;
        // Generate multiple IE's concatenated
        ByteArrayOutputStream multiLinkIes = new ByteArrayOutputStream();
        for (int i = 0; i < MAX_NUM_IES; ++i) {
            multiLinkIes.write(testByteArray);
        }

        /* Multi link element fragmentation verification */
        InformationElement[] results =
                InformationElementUtil.parseInformationElements(multiLinkIes.toByteArray());
        assertEquals("Parsed results should have 1 element", MAX_NUM_IES, results.length);
        assertEquals(
                "First result should have id = EID_EXTENSION_PRESENT",
                InformationElement.EID_EXTENSION_PRESENT,
                results[0].id);
        assertEquals(
                "First result should have idExt = " + InformationElement.EID_EXT_MULTI_LINK,
                InformationElement.EID_EXT_MULTI_LINK,
                results[0].idExt);
        assertEquals("First result should have data of 578 bytes", 578, results[0].bytes.length);

        /* Per STA profile sub-element fragmentation verification */
        InformationElementUtil.MultiLink multiLink = new InformationElementUtil.MultiLink();
        multiLink.from(results[0]);
        assertTrue(multiLink.isPresent());
        assertEquals(2, multiLink.getLinkId());
        assertEquals(2, multiLink.getAffiliatedLinks().size());
        assertEquals(0, multiLink.getAffiliatedLinks().get(0).getLinkId());
        assertEquals(
                MacAddress.fromString("00:00:00:00:00:01"),
                multiLink.getAffiliatedLinks().get(0).getApMacAddress());
        assertEquals(1, multiLink.getAffiliatedLinks().get(1).getLinkId());
        assertEquals(
                MacAddress.fromString("00:00:00:00:00:02"),
                multiLink.getAffiliatedLinks().get(1).getApMacAddress());
    }

    private void verifyCapabilityStringFromIes(
            InformationElement[] ies,
            int beaconCap,
            boolean isOweSupported,
            String capsStr,
            SparseIntArray unknownAkmMap) {
        InformationElementUtil.Capabilities capabilities =
                new InformationElementUtil.Capabilities();
        capabilities.from(ies, beaconCap, isOweSupported, 2400, unknownAkmMap);
        String result = capabilities.generateCapabilitiesString();

        assertEquals(capsStr, result);
    }

    private void verifyCapabilityStringFromIe(
            InformationElement ie,
            int beaconCap,
            boolean isOweSupported,
            String capsStr,
            SparseIntArray unknownAkmMap) {
        InformationElement[] ies = new InformationElement[] { ie };
        verifyCapabilityStringFromIes(
                new InformationElement[] {ie}, beaconCap, isOweSupported, capsStr, unknownAkmMap);
    }

    private void verifyCapabilityStringFromIeWithoutOweSupported(
            InformationElement ie, String capsStr) {
        verifyCapabilityStringFromIe(ie, 0x1 << 4, false, capsStr, null);
    }

    private void verifyCapabilityStringFromIeWithOweSupported(
            InformationElement ie, String capsStr, SparseIntArray unknownAkmMap) {
        verifyCapabilityStringFromIe(ie, 0x1 << 4, true, capsStr, unknownAkmMap);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnElement() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F,
                                (byte) 0xAC, (byte) 0x02, (byte) 0x02, (byte) 0x00,
                                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                                (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F,
                                (byte) 0xAC, (byte) 0x02, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithoutOweSupported(ie,
                "[WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE which contains
     * an unknown AKM.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnElementWithUnknownAkm() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] { (byte) 0x01, (byte) 0x00, // Version
                                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02, // TKIP
                                (byte) 0x02, (byte) 0x00, // Pairwise cipher count
                                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04, // CCMP
                                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02, // TKIP
                                (byte) 0x01, (byte) 0x00, // AKM count
                                (byte) 0x00, (byte) 0x0F, (byte) 0x99, (byte) 0x99, // Unknown AKM
                                (byte) 0x00, (byte) 0x00 // RSN capabilities
        };
        verifyCapabilityStringFromIeWithoutOweSupported(ie,
                "[RSN-?-CCMP+TKIP]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnElementWithGroupManagementCipher() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] {
                // Version
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: TKIP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // Pairwise cipher count
                (byte) 0x02, (byte) 0x00,
                // Pairwise cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Pairwise cipher suite: TKIP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // AKM count
                (byte) 0x01, (byte) 0x00,
                // AMK suite: EAP/SHA1
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x01,
                // RSN capabilities
                (byte) 0x40, (byte) 0x00,
                // PMKID count
                (byte) 0x01, (byte) 0x00,
                // PMKID
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                // Group mgmt cipher suite: BIP_GMAC_256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x0c,
        };
        verifyCapabilityStringFromIeWithoutOweSupported(ie,
                "[WPA2-EAP/SHA1-CCMP+TKIP][RSN-EAP/SHA1-CCMP+TKIP][MFPR]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnElementWithWpa3EnterpriseOnlyNetwork() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] {
                // Version
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: TKIP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // Pairwise cipher count
                (byte) 0x01, (byte) 0x00,
                // Pairwise cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // AKM count
                (byte) 0x01, (byte) 0x00,
                // AMK suite: EAP/SHA256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x05,
                // RSN capabilities
                (byte) 0xc0, (byte) 0x00,
                // PMKID count
                (byte) 0x00, (byte) 0x00,
                // Group mgmt cipher suite: BIP_GMAC_256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x0c,
        };
        verifyCapabilityStringFromIeWithoutOweSupported(ie,
                "[WPA2-EAP/SHA256-CCMP]"
                        + "[RSN-EAP/SHA256-CCMP][MFPR][MFPC]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnElementWithWpa3EnterpriseOnlyNetworkNoGroupMgmtCiherSuite() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] {
                // Version
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: TKIP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // Pairwise cipher count
                (byte) 0x01, (byte) 0x00,
                // Pairwise cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // AKM count
                (byte) 0x01, (byte) 0x00,
                // AMK suite: EAP/SHA256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x05,
                // RSN capabilities
                (byte) 0xc0, (byte) 0x00,
        };
        verifyCapabilityStringFromIeWithoutOweSupported(ie,
                "[WPA2-EAP/SHA256-CCMP]"
                        + "[RSN-EAP/SHA256-CCMP][MFPR][MFPC]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnElementWithWpa3EnterpriseTransitionNetwork() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] {
                // Version
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: TKIP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // Pairwise cipher count
                (byte) 0x01, (byte) 0x00,
                // Pairwise cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // AKM count
                (byte) 0x02, (byte) 0x00,
                // AMK suite: EAP/SHA1
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x01,
                // AMK suite: EAP/SHA256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x05,
                // RSN capabilities
                (byte) 0x80, (byte) 0x00,
                // PMKID count
                (byte) 0x00, (byte) 0x00,
                // Group mgmt cipher suite: BIP_GMAC_256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x0c,
        };
        verifyCapabilityStringFromIeWithoutOweSupported(ie,
                "[WPA2-EAP/SHA1+EAP/SHA256-CCMP]"
                        + "[RSN-EAP/SHA1+EAP/SHA256-CCMP][MFPC]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnElementWithoutGroupManagementCipherButSetMfpr() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] {
                // Version
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: TKIP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // Pairwise cipher count
                (byte) 0x02, (byte) 0x00,
                // Pairwise cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Pairwise cipher suite: TKIP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // AKM count
                (byte) 0x01, (byte) 0x00,
                // AMK suite: EAP/SHA1
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x01,
                // RSN capabilities
                (byte) 0x40, (byte) 0x00,
        };
        verifyCapabilityStringFromIeWithoutOweSupported(ie,
                "[WPA2-EAP/SHA1-CCMP+TKIP][RSN-EAP/SHA1-CCMP+TKIP][MFPR]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a RSN IE which is malformed.
     * Expect the function to return a string with empty key management & pairswise cipher security
     * information.
     */
    @Test
    public void buildCapabilities_malformedRsnElement() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RSN;
        ie.bytes = new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F,
                (byte) 0xAC, (byte) 0x02, (byte) 0x02, (byte) 0x00,
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC };
        verifyCapabilityStringFromIeWithoutOweSupported(ie, "[RSN]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a WPA type 1 IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_wpa1Element() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x01,
                                (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x50,
                                (byte) 0xF2, (byte) 0x02, (byte) 0x02, (byte) 0x00,
                                (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x04,
                                (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x02,
                                (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x50,
                                (byte) 0xF2, (byte) 0x02, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithoutOweSupported(ie, "[WPA-PSK-CCMP+TKIP]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a WPA type 1 IE which
     * contains an unknown AKM.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_wpa1ElementWithUnknownAkm() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x01, // OUI & type
                                (byte) 0x01, (byte) 0x00, // Version
                                (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x02, // TKIP
                                (byte) 0x02, (byte) 0x00, // Pairwise cipher count
                                (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x04, // CCMP
                                (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x02, // TKIP
                                (byte) 0x01, (byte) 0x00, // AKM count
                                (byte) 0x00, (byte) 0x50, (byte) 0x99, (byte) 0x99, // Unknown AKM
                                (byte) 0x00, (byte) 0x00};
        verifyCapabilityStringFromIeWithoutOweSupported(ie, "[WPA-?-CCMP+TKIP]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a WPA type 1 IE which is malformed.
     * Expect the function to return a string with empty key management & pairswise cipher security
     * information.
     */
    @Test
    public void buildCapabilities_malformedWpa1Element() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x01,
                (byte) 0x01, (byte) 0x00 };
        verifyCapabilityStringFromIeWithoutOweSupported(ie, "[WPA]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with both RSN and WPA1 IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnAndWpaElement() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F,
                                   (byte) 0xAC, (byte) 0x02, (byte) 0x02, (byte) 0x00,
                                   (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                                   (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                                   (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F,
                                   (byte) 0xAC, (byte) 0x02, (byte) 0x00, (byte) 0x00 };

        InformationElement ieWpa = new InformationElement();
        ieWpa.id = InformationElement.EID_VSA;
        ieWpa.bytes = new byte[] { (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x01,
                                   (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x50,
                                   (byte) 0xF2, (byte) 0x02, (byte) 0x02, (byte) 0x00,
                                   (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x04,
                                   (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x02,
                                   (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x50,
                                   (byte) 0xF2, (byte) 0x02, (byte) 0x00, (byte) 0x00 };

        InformationElement[] ies = new InformationElement[] { ieWpa, ieRsn };
        verifyCapabilityStringFromIes(
                ies,
                0x1 << 4,
                false,
                "[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP]",
                null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE, CCMP and PSK+SAE transition mode.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnPskSaeTransitionElement() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of AKMs (2)
                (byte) 0x02, (byte) 0x00,
                // PSK AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                // SAE AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x08,
                // Padding
                (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithOweSupported(
                ieRsn, "[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE, CCMP and SAE+FT/SAE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnSaeFtSaeElement() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of AKMs (2)
                (byte) 0x02, (byte) 0x00,
                // SAE AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x08,
                // FT/SAE AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x09,
                // Padding
                (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithOweSupported(ieRsn, "[RSN-SAE+FT/SAE-CCMP]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE, CCMP and SAE+SAE_EXT_KEY.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnSaeSaeExtKeyElement() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of AKMs (2)
                (byte) 0x02, (byte) 0x00,
                // SAE AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x08,
                // SAE-EXT-KEY AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x18,
                // Padding
                (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithOweSupported(ieRsn, "[RSN-SAE+SAE_EXT_KEY-CCMP]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE, CCMP and
     * SAE_EXT_KEY+FT_SAE_EXT_KEY.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnSaeExtKeyFtSaeExtKeyElement() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of AKMs (2)
                (byte) 0x02, (byte) 0x00,
                // SAE-EXT-KEY AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x18,
                // FT-SAE-EXT-KEY AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x19,
                // Padding
                (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithOweSupported(
                ieRsn, "[RSN-SAE_EXT_KEY+FT/SAE_EXT_KEY-CCMP]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE, CCMP and OWE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnOweElement() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of AKMs (1)
                (byte) 0x01, (byte) 0x00,
                // OWE AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x12,
                // Padding
                (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithOweSupported(ieRsn, "[RSN-OWE-CCMP]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with OWE IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_oweVsElementOweSupported() {
        InformationElement ieOwe = new InformationElement();
        ieOwe.id = InformationElement.EID_VSA;
        ieOwe.bytes = new byte[] {
                // OWE vendor specific
                (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x1C,
                // OWE IE contains BSSID, SSID and channel of other BSS, but we don't parse it.
                (byte) 0x00, (byte) 0x000, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIe(ieOwe, 0x1 << 0, true, "[RSN-OWE_TRANSITION-CCMP][ESS]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with OWE IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_oweVsElementOweNotSupported() {
        InformationElement ieOwe = new InformationElement();
        ieOwe.id = InformationElement.EID_VSA;
        ieOwe.bytes = new byte[] {
                // OWE vendor specific
                (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x1C,
                // OWE IE contains BSSID, SSID and channel of other BSS, but we don't parse it.
                (byte) 0x00, (byte) 0x000, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIe(ieOwe, 0x1 << 0, false, "[ESS]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE, GCMP-256 and SUITE_B_192.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnSuiteB192Element() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: GCMP-256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x09,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: GCMP-256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x09,
                // Number of AKMs (1)
                (byte) 0x01, (byte) 0x00,
                // SUITE_B_192 AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x0C,
                // RSN capabilities
                (byte) 0x40, (byte) 0x00,
                // PMKID count
                (byte) 0x00, (byte) 0x00,
                // Group mgmt cipher suite: BIP_GMAC_256
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x0c,
        };
        verifyCapabilityStringFromIeWithoutOweSupported(ieRsn,
                "[RSN-EAP_SUITE_B_192-GCMP-256][MFPR]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE,
     * CCMP and FILS SHA256. Expect the function to return a string
     * with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnFilsSha256Element() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of AKMs (3)
                (byte) 0x03, (byte) 0x00,
                // WPA AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x01,
                // WPA SHA256 AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x05,
                // FILS SHA256 AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x0E,
                // RSN capabilities
                (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithOweSupported(
                ieRsn,
                "[WPA2-EAP/SHA1+EAP/SHA256+EAP-FILS-SHA256-CCMP]"
                        + "[RSN-EAP/SHA1+EAP/SHA256+EAP-FILS-SHA256-CCMP]",
                null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE,
     * CCMP and FILS SHA384. Expect the function to return a string
     * with the proper security information.
     */
    @Test
    public void buildCapabilities_rsnFilsSha384Element() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] {
                // RSNE Version (0x0001)
                (byte) 0x01, (byte) 0x00,
                // Group cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of cipher suites (1)
                (byte) 0x01, (byte) 0x00,
                // Cipher suite: CCMP
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                // Number of AKMs (3)
                (byte) 0x03, (byte) 0x00,
                // WPA AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x01,
                // WPA SHA256 AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x05,
                // FILS SHA384 AKM
                (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x0F,
                // RSN capabilities
                (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithOweSupported(
                ieRsn,
                "[WPA2-EAP/SHA1+EAP/SHA256+EAP-FILS-SHA384-CCMP]"
                        + "[RSN-EAP/SHA1+EAP/SHA256+EAP-FILS-SHA384-CCMP]",
                null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with RSN IE, CCMP and unknown AKM suite
     * selector. Expect the function to return a capability string with the mapped AKM scheme.
     */
    @Test
    public void buildCapabilities_rsnIeUnknownAkmMapping() {
        // unknown AKM (0x00, 0x40, 0x96, 0x00) -> known AKM (0x00, 0x0F, 0xAC, 0x18) - SAE_EXT_KEY
        SparseIntArray unknownAkmMap =
                new SparseIntArray() {
                    {
                        put(0x00964000, 0x12);
                    }
                };
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes =
                new byte[] {
                    // RSNE Version (0x0001)
                    (byte) 0x01,
                    (byte) 0x00,
                    // Group cipher suite: CCMP
                    (byte) 0x00,
                    (byte) 0x0F,
                    (byte) 0xAC,
                    (byte) 0x04,
                    // Number of cipher suites (1)
                    (byte) 0x01,
                    (byte) 0x00,
                    // Cipher suite: CCMP
                    (byte) 0x00,
                    (byte) 0x0F,
                    (byte) 0xAC,
                    (byte) 0x04,
                    // Number of AKMs (1)
                    (byte) 0x01,
                    (byte) 0x00,
                    // unknown AKM (0x00, 0x40, 0x96, 0x00)
                    (byte) 0x00,
                    (byte) 0x40,
                    (byte) 0x96,
                    (byte) 0x00,
                    // RSN capabilities
                    (byte) 0x00,
                    (byte) 0x00
                };
        verifyCapabilityStringFromIeWithOweSupported(
                ieRsn, "[RSN-SAE_EXT_KEY-CCMP]", unknownAkmMap);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with both RSN and WPA1 IE which are malformed.
     * Expect the function to return a string with empty key management & pairswise cipher security
     * information.
     */
    @Test
    public void buildCapabilities_malformedRsnAndWpaElement() {
        InformationElement ieRsn = new InformationElement();
        ieRsn.id = InformationElement.EID_RSN;
        ieRsn.bytes = new byte[] { (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F,
                (byte) 0xAC, (byte) 0x02, (byte) 0x02 };

        InformationElement ieWpa = new InformationElement();
        ieWpa.id = InformationElement.EID_VSA;
        ieWpa.bytes = new byte[] { (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x01,
                (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x50,
                (byte) 0xF2, (byte) 0x02, (byte) 0x02, (byte) 0x00,
                (byte) 0x00, (byte) 0x50 };
        InformationElement[] ies = new InformationElement[] { ieWpa, ieRsn };
        verifyCapabilityStringFromIes(ies, 0x1 << 4, false, "[WPA][RSN]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with both WPS and WPA1 IE.
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_wpaAndWpsElement() {
        InformationElement ieWpa = new InformationElement();
        ieWpa.id = InformationElement.EID_VSA;
        ieWpa.bytes = new byte[] { (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x01,
                                   (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x50,
                                   (byte) 0xF2, (byte) 0x02, (byte) 0x02, (byte) 0x00,
                                   (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x04,
                                   (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x02,
                                   (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x50,
                                   (byte) 0xF2, (byte) 0x02, (byte) 0x00, (byte) 0x00 };

        InformationElement ieWps = new InformationElement();
        ieWps.id = InformationElement.EID_VSA;
        ieWps.bytes = new byte[] { (byte) 0x00, (byte) 0x50, (byte) 0xF2, (byte) 0x04 };

        InformationElement[] ies = new InformationElement[] { ieWpa, ieWps };
        verifyCapabilityStringFromIes(ies, 0x1 << 4, false, "[WPA-PSK-CCMP+TKIP][WPS]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a vendor specific element which
     * is not WPA type 1. Beacon Capability Information field has the Privacy
     * bit set.
     *
     * Expect the function to return a string with the proper security information.
     */
    @Test
    public void buildCapabilities_nonRsnWpa1Element_privacySet() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x04, (byte) 0x0E, (byte) 0x01,
                                (byte) 0x01, (byte) 0x02, (byte) 0x01, (byte) 0x00,
                                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIeWithoutOweSupported(ie, "[WEP]");
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a vendor specific element which
     * is not WPA type 1. Beacon Capability Information field doesn't have the
     * Privacy bit set.
     *
     * Expect the function to return an empty string.
     */
    @Test
    public void buildCapabilities_nonRsnWpa1Element_privacyClear() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x04, (byte) 0x0E, (byte) 0x01,
                                (byte) 0x01, (byte) 0x02, (byte) 0x01, (byte) 0x00,
                                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIe(ie, 0, false, "", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a vendor specific element which
     * is not WPA type 1. Beacon Capability Information field has the ESS bit set.
     *
     * Expect the function to return a string with [ESS] there.
     */
    @Test
    public void buildCapabilities_nonRsnWpa1Element_essSet() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x04, (byte) 0x0E, (byte) 0x01,
                                (byte) 0x01, (byte) 0x02, (byte) 0x01, (byte) 0x00,
                                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIe(ie, 0x1 << 0, false, "[ESS]", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with a vendor specific element which
     * is not WPA type 1. Beacon Capability Information field doesn't have the
     * ESS bit set.
     *
     * Expect the function to return an empty string.
     */
    @Test
    public void buildCapabilities_nonRsnWpa1Element_essClear() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x04, (byte) 0x0E, (byte) 0x01,
                                (byte) 0x01, (byte) 0x02, (byte) 0x01, (byte) 0x00,
                                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        verifyCapabilityStringFromIe(ie, 0, false, "", null);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with the IBSS capability bit set.
     *
     * Expect the function to return a string with [IBSS] there.
     */
    @Test
    public void buildCapabilities_IbssCapabilitySet() {
        int beaconCap = 0x1 << 1;

        InformationElementUtil.Capabilities capabilities =
                new InformationElementUtil.Capabilities();
        capabilities.from(new InformationElement[0], beaconCap, false, 2400, null);
        String result = capabilities.generateCapabilitiesString();

        assertEquals("[IBSS]", result);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with the IBSS capability bit set for DMG.
     *
     * Expect the function to return a string with [IBSS] there.
     */
    @Test
    public void buildCapabilities_DmgIbssCapabilitySet() {
        int beaconCap = 0x1;

        InformationElementUtil.Capabilities capabilities =
                new InformationElementUtil.Capabilities();
        capabilities.from(new InformationElement[0], beaconCap, false, 58320, null);
        String result = capabilities.generateCapabilitiesString();

        assertEquals("[IBSS]", result);
    }

    /**
     * Test Capabilities.generateCapabilitiesString() with the ESS capability bit set for DMG.
     *
     * Expect the function to return a string with [IBSS] there.
     */
    @Test
    public void buildCapabilities_DmgEssCapabilitySet() {
        int beaconCap = 0x3;

        InformationElementUtil.Capabilities capabilities =
                new InformationElementUtil.Capabilities();
        capabilities.from(new InformationElement[0], beaconCap, false, 58320, null);
        String result = capabilities.generateCapabilitiesString();

        assertEquals("[ESS]", result);
    }

    /**
     * Verify the expectations when building an ExtendedCapabilites IE from data with no bits set.
     * Both ExtendedCapabilities#isStrictUtf8() and ExtendedCapabilites#is80211McRTTResponder()
     * should return false.
     */
    @Test
    public void buildExtendedCapabilities_emptyBitSet() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENDED_CAPS;
        ie.bytes = new byte[8];

        InformationElementUtil.ExtendedCapabilities extendedCap =
                new InformationElementUtil.ExtendedCapabilities();
        extendedCap.from(ie);
        assertFalse(extendedCap.isStrictUtf8());
        assertFalse(extendedCap.is80211McRTTResponder());
        assertFalse(extendedCap.isTriggerBasedRangingRespSupported());
        assertFalse(extendedCap.isNonTriggerBasedRangingRespSupported());
        assertFalse(extendedCap.isTwtRequesterSupported());
        assertFalse(extendedCap.isTwtResponderSupported());
        assertFalse(extendedCap.isFilsCapable());
    }

    /**
     * Verify the expectations when building an ExtendedCapabilites IE from data with UTF-8 SSID
     * bit set (bit 48).  ExtendedCapabilities#isStrictUtf8() should return true.
     */
    @Test
    public void buildExtendedCapabilites_strictUtf8() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENDED_CAPS;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00 };

        InformationElementUtil.ExtendedCapabilities extendedCap =
                new InformationElementUtil.ExtendedCapabilities();
        extendedCap.from(ie);
        assertTrue(extendedCap.isStrictUtf8());
        assertFalse(extendedCap.is80211McRTTResponder());
    }

    /**
     * Verify the expectations when building an ExtendedCapabilites IE from data with RTT Response
     * Enable bit set (bit 70).  ExtendedCapabilities#is80211McRTTResponder() should return true.
     */
    @Test
    public void buildExtendedCapabilites_80211McRTTResponder() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENDED_CAPS;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                                (byte) 0x40 };

        InformationElementUtil.ExtendedCapabilities extendedCap =
                new InformationElementUtil.ExtendedCapabilities();
        extendedCap.from(ie);
        assertFalse(extendedCap.isStrictUtf8());
        assertTrue(extendedCap.is80211McRTTResponder());
    }

    /**
     * Verify Extended Capabilities: trigger and non-trigger based ranging support, TWT requester
     * and responder support and FILS support.
     */
    @Test
    public void testExtendedCapabilitiesMisc() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENDED_CAPS;
        ie.bytes = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x61, (byte) 0x00, (byte) 0x0c };

        InformationElementUtil.ExtendedCapabilities extendedCap =
                new InformationElementUtil.ExtendedCapabilities();
        extendedCap.from(ie);
        assertTrue(extendedCap.isTriggerBasedRangingRespSupported());
        assertTrue(extendedCap.isNonTriggerBasedRangingRespSupported());
        assertTrue(extendedCap.isTwtRequesterSupported());
        assertTrue(extendedCap.isTwtResponderSupported());
        assertTrue(extendedCap.isFilsCapable());
    }

    /**
     * Test a that a correctly formed TIM Information Element is decoded into a valid TIM element,
     * and the values are captured
     */
    @Test
    public void parseTrafficIndicationMapInformationElementValid() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_TIM;
        ie.bytes = new byte[] { (byte) 0x03, (byte) 0x05, (byte) 0x00, (byte) 0x00};
        InformationElementUtil.TrafficIndicationMap trafficIndicationMap =
                new InformationElementUtil.TrafficIndicationMap();
        trafficIndicationMap.from(ie);
        assertEquals(trafficIndicationMap.mLength, 4);
        assertEquals(trafficIndicationMap.mDtimCount, 3);
        assertEquals(trafficIndicationMap.mDtimPeriod, 5);
        assertEquals(trafficIndicationMap.mBitmapControl, 0);
        assertEquals(trafficIndicationMap.isValid(), true);
    }

    /**
     * Test that a short invalid Information Element is marked as being an invalid TIM element when
     * parsed as Traffic Indication Map.
     */
    @Test
    public void parseTrafficIndicationMapInformationElementInvalidTooShort() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_TIM;
        ie.bytes = new byte[] { (byte) 0x01, (byte) 0x07 };
        InformationElementUtil.TrafficIndicationMap trafficIndicationMap =
                new InformationElementUtil.TrafficIndicationMap();
        trafficIndicationMap.from(ie);
        assertEquals(trafficIndicationMap.isValid(), false);
    }

    /**
     * Test that a too-large invalid Information Element is marked as an invalid TIM element when
     * parsed as Traffic Indication Map.
     */
    @Test
    public void parseTrafficIndicationMapInformationElementInvalidTooLong() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_TIM;
        ie.bytes = new byte[255]; // bytes length of upto 254 is valid for TIM
        Arrays.fill(ie.bytes, (byte) 7);
        InformationElementUtil.TrafficIndicationMap trafficIndicationMap =
                new InformationElementUtil.TrafficIndicationMap();
        trafficIndicationMap.from(ie);
        assertEquals(trafficIndicationMap.isValid(), false);
    }

    /**
     * Verify that the expected Roaming Consortium information element is parsed and retrieved
     * from the list of IEs.
     *
     * @throws Exception
     */
    @Test
    public void getRoamingConsortiumIE() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_ROAMING_CONSORTIUM;
        /**
         * Roaming Consortium Format;
         * | Number of OIs | OI#1 and OI#2 Lengths | OI #1 | OI #2 (optional) | OI #3 (optional) |
         *        1                  1              variable     variable           variable
         */
        ie.bytes = new byte[] { (byte) 0x01 /* number of OIs */, (byte) 0x03 /* OI Length */,
                                (byte) 0x11, (byte) 0x22, (byte) 0x33};
        InformationElementUtil.RoamingConsortium roamingConsortium =
                InformationElementUtil.getRoamingConsortiumIE(new InformationElement[] {ie});
        assertEquals(1, roamingConsortium.anqpOICount);
        assertEquals(1, roamingConsortium.getRoamingConsortiums().length);
        assertEquals(0x112233, roamingConsortium.getRoamingConsortiums()[0]);
    }

    /**
     * Verify that the expected Hotspot 2.0 Vendor Specific information element is parsed and
     * retrieved from the list of IEs.
     *
     * @throws Exception
     */
    @Test
    public void getHS2VendorSpecificIEWithDomainIdOnly() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        /**
         * Vendor Specific OI Format:
         * | OI | Type | Hotspot Configuration | PPS MO ID (optional) | ANQP Domain ID (optional)
         *    3    1              1                    2                        2
         *
         * With OI=0x506F9A and Type=0x10 for Hotspot 2.0
         *
         * The Format of Hotspot Configuration:
         *        B0               B1                   B2             B3    B4              B7
         * | DGAF Disabled | PPS MO ID Flag | ANQP Domain ID Flag | reserved | Release Number |
         */
        ie.bytes = new byte[] { (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x10,
                                (byte) 0x14 /* Hotspot Configuration */, (byte) 0x11, (byte) 0x22};
        InformationElementUtil.Vsa vsa =
                InformationElementUtil.getHS2VendorSpecificIE(new InformationElement[] {ie});
        assertEquals(NetworkDetail.HSRelease.R2, vsa.hsRelease);
        assertEquals(0x2211, vsa.anqpDomainID);
    }

    /**
     * Verify that the expected Hotspot 2.0 Vendor Specific information element is parsed and
     * retrieved from the list of IEs.
     *
     * @throws Exception
     */
    @Test
    public void getHS2VendorSpecificIEWithDomainIdAndPpsMoId() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        /**
         * Vendor Specific OI Format:
         * | OI | Type | Hotspot Configuration | PPS MO ID (optional) | ANQP Domain ID (optional)
         *    3    1              1                    2                        2
         *
         * With OI=0x506F9A and Type=0x10 for Hotspot 2.0
         *
         * The Format of Hotspot Configuration:
         *        B0               B1                   B2             B3    B4              B7
         * | DGAF Disabled | PPS MO ID Flag | ANQP Domain ID Flag | reserved | Release Number |
         */
        ie.bytes = new byte[] { (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x10,
                (byte) 0x16 /* Hotspot Configuration */, (byte) 0x44, (byte) 0x33 /* PPS MO */,
                (byte) 0x11, (byte) 0x22 /* ANQP Domain */};
        InformationElementUtil.Vsa vsa =
                InformationElementUtil.getHS2VendorSpecificIE(new InformationElement[] {ie});
        assertEquals(NetworkDetail.HSRelease.R2, vsa.hsRelease);
        assertEquals(0x2211, vsa.anqpDomainID);
    }

    /**
     * Verify that the expected Hotspot 2.0 Vendor Specific information element is parsed and
     * retrieved from the list of IEs.
     *
     * @throws Exception
     */
    @Test
    public void testHS2VendorSpecificIEWithDomainIdAndPpsMoIdBitsIncorrectSize() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        /**
         * Vendor Specific OI Format:
         * | OI | Type | Hotspot Configuration | PPS MO ID (optional) | ANQP Domain ID (optional)
         *    3    1              1                    2                        2
         *
         * With OI=0x506F9A and Type=0x10 for Hotspot 2.0
         *
         * The Format of Hotspot Configuration:
         *        B0               B1                   B2             B3    B4              B7
         * | DGAF Disabled | PPS MO ID Flag | ANQP Domain ID Flag | reserved | Release Number |
         */
        ie.bytes = new byte[] { (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x10,
                (byte) 0x16 /* Hotspot Configuration */, (byte) 0x44, (byte) 0x33 /* PPS MO */
                /* ANQP Domain missing */};
        InformationElementUtil.Vsa vsa =
                InformationElementUtil.getHS2VendorSpecificIE(new InformationElement[] {ie});
        assertEquals(0, vsa.anqpDomainID);
    }

    /**
     * Verify that the expected Vendor Specific information element is parsed and retrieved from
     * the list of IEs.
     */
    @Test
    public void testVendorSpecificIEWithOneVsaAndOneNonVsa() throws Exception {
        InformationElement ie1 = new InformationElement();
        InformationElement ie2 = new InformationElement();
        ie1.id = InformationElement.EID_VSA;
        ie2.id = InformationElement.EID_COUNTRY;
        ie1.bytes = new byte[] { (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03};
        ie2.bytes = new byte[] { (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07};
        List<InformationElementUtil.Vsa> vsas =
                InformationElementUtil.getVendorSpecificIE(new InformationElement[] {ie1, ie2});
        assertEquals(1, vsas.size());
        assertArrayEquals(new byte[] {(byte) 0x00, (byte) 0x01, (byte) 0x02}, vsas.get(0).oui);
    }

    /**
     * Verify that the expected Interworking information element is parsed and retrieved from the
     * list of IEs. Uses an IE w/o the optional Venue Info.
     *
     * @throws Exception
     */
    @Test
    public void getInterworkingElementNoVenueIE() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_INTERWORKING;
        /**
         * Interworking Format:
         * | Access Network Option | Venue Info (optional) | HESSID (optional) |
         *           1                       2                     6
         *
         * Access Network Option Format:
         *
         * B0                   B3    B4       B5    B6     B7
         * | Access Network Type | Internet | ASRA | ESR | UESA |
         */
        ie.bytes = new byte[] { (byte) 0x10, (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
                                (byte) 0x55, (byte) 0x66 };
        InformationElementUtil.Interworking interworking =
                InformationElementUtil.getInterworkingIE(new InformationElement[] {ie});
        assertTrue(interworking.internet);
        assertEquals(NetworkDetail.Ant.Private, interworking.ant);
        assertEquals(0x112233445566L, interworking.hessid);
    }

    /**
     * Verify that the expected Interworking information element is parsed and retrieved from the
     * list of IEs. Uses an IE with the optional Venue Info.
     *
     * @throws Exception
     */
    @Test
    public void getInterworkingElementWithVenueIE() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_INTERWORKING;
        /**
         * Interworking Format:
         * | Access Network Option | Venue Info (optional) | HESSID (optional) |
         *           1                       2                     6
         *
         * Access Network Option Format:
         *
         * B0                   B3    B4       B5    B6     B7
         * | Access Network Type | Internet | ASRA | ESR | UESA |
         */
        ie.bytes = new byte[]{(byte) 0x10, (byte) 0xAA, (byte) 0xBB, (byte) 0x11, (byte) 0x22,
                (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66};
        InformationElementUtil.Interworking interworking =
                InformationElementUtil.getInterworkingIE(new InformationElement[] {ie});
        assertTrue(interworking.internet);
        assertEquals(NetworkDetail.Ant.Private, interworking.ant);
        assertEquals(0x112233445566L, interworking.hessid);
    }

    /**
     * Verify that the expected HT Operation information element is parsed and retrieved from the
     * list of IEs.
     *
     * @throws Exception
     */
    @Test
    public void getHtOperationElement() throws Exception {
        final int primaryFreq = 2467;
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_HT_OPERATION;
        /**
         * HT Operation Format:
         * | Primary Channel | HT Operation Info | Basic HT-MCS Set |
         *           1                5                 16
         *
         * HT Operation Info Format (relevant parts only):
         *
         * B0                        B1         B2          -----
         * | Secondary Channel Offset | STA Channel Width | Other |
         */
        ie.bytes = new byte[22];
        ie.bytes[0] = (byte) 11;
        ie.bytes[1] = (byte) 0x83; //Setting Secondary channel offset = 3
        // Remaining bytes are not relevant

        HtOperation htOperation = new HtOperation();
        htOperation.from(ie);

        assertTrue(htOperation.isPresent());
        assertEquals(ScanResult.CHANNEL_WIDTH_40MHZ, htOperation.getChannelWidth());
        assertEquals(primaryFreq - 10, htOperation.getCenterFreq0(primaryFreq));
    }

    /**
     * Verify that the expected VHT Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel BW is set to be 20/40 MHz
     *
     * @throws Exception
     */
    @Test
    public void getVhtOperationElement20_40Mhz() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VHT_OPERATION;
        /**
         * VHT Operation Format:
         * | VHT Operation Info | Basic HT-MCS Set |
         *           3                  2
         *
         * VHT Operation Info Format:
         * | Channel Width | Channel Center Freq Seg 0 | Channel Center Freq Seg 1 |
         *         1                      1                      1
         */
        ie.bytes = new byte[]{(byte) 0x00, (byte) 0xF0, (byte) 0xF1, (byte) 0x00, (byte) 0x00};

        VhtOperation vhtOperation = new VhtOperation();
        vhtOperation.from(ie);

        assertTrue(vhtOperation.isPresent());
        assertEquals(ScanResult.UNSPECIFIED, vhtOperation.getChannelWidth());
        assertEquals(0, vhtOperation.getCenterFreq0());
        assertEquals(0, vhtOperation.getCenterFreq1());
    }

    /**
     * Verify that the expected VHT Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel BW is set to be 80 MHz
     *
     * @throws Exception
     */
    @Test
    public void getVhtOperationElement80Mhz() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VHT_OPERATION;
        /**
         * VHT Operation Format:
         * | VHT Operation Info | Basic HT-MCS Set |
         *           3                  2
         *
         * VHT Operation Info Format:
         * | Channel Width | Channel Center Freq Seg 0 | Channel Center Freq Seg 1 |
         *         1                      1                      1
         */
        ie.bytes = new byte[]{(byte) 0x01, (byte) 36, (byte) 0x00, (byte) 0x00, (byte) 0x00};

        VhtOperation vhtOperation = new VhtOperation();
        vhtOperation.from(ie);

        assertTrue(vhtOperation.isPresent());
        assertEquals(ScanResult.CHANNEL_WIDTH_80MHZ, vhtOperation.getChannelWidth());
        assertEquals(5180, vhtOperation.getCenterFreq0());
        assertEquals(0, vhtOperation.getCenterFreq1());
    }

    /**
     * Verify that the expected VHT Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel BW is set to be 160 MHz
     *
     * @throws Exception
     */
    @Test
    public void getVhtOperationElement160Mhz() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VHT_OPERATION;
        /**
         * VHT Operation Format:
         * | VHT Operation Info | Basic HT-MCS Set |
         *           3                  2
         *
         * VHT Operation Info Format:
         * | Channel Width | Channel Center Freq Seg 0 | Channel Center Freq Seg 1 |
         *         1                      1                      1
         */
        ie.bytes = new byte[]{(byte) 0x01, (byte) 44, (byte) 36, (byte) 0x00, (byte) 0x00};

        VhtOperation vhtOperation = new VhtOperation();
        vhtOperation.from(ie);

        assertTrue(vhtOperation.isPresent());
        assertEquals(ScanResult.CHANNEL_WIDTH_160MHZ, vhtOperation.getChannelWidth());
        assertEquals(5220, vhtOperation.getCenterFreq0());
        assertEquals(5180, vhtOperation.getCenterFreq1());
    }

    /**
     * Verify that the expected VHT Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel BW is set to be 80+80 MHz
     *
     * @throws Exception
     */
    @Test
    public void getVhtOperationElement80PlusMhz() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VHT_OPERATION;
        /**
         * VHT Operation Format:
         * | VHT Operation Info | Basic HT-MCS Set |
         *           3                  2
         *
         * VHT Operation Info Format:
         * | Channel Width | Channel Center Freq Seg 0 | Channel Center Freq Seg 1 |
         *         1                      1                      1
         */
        ie.bytes = new byte[]{(byte) 0x01, (byte) 54, (byte) 36, (byte) 0x00, (byte) 0x00};

        VhtOperation vhtOperation = new VhtOperation();
        vhtOperation.from(ie);

        assertTrue(vhtOperation.isPresent());
        assertEquals(ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ, vhtOperation.getChannelWidth());
        assertEquals(5270, vhtOperation.getCenterFreq0());
        assertEquals(5180, vhtOperation.getCenterFreq1());
    }

    /**
     * Verify that the expected HE Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel is in 6GHz band and channel width is 80MHz
     *
     * @throws Exception
     */
    @Test
    public void getHeOperationElement80Mhz() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_HE_OPERATION;
        /**
         * HE Operation Format:
         * | HE Operation Info | BSS Color | Basic HE-MCS | VHT Info  | Cohosted BSS| 6GH Info |
         *          3                1            2           0/3           0/1         0/5
         *
         * HE Operation Info:
         *    |  Misc | VHT Operation Info | Misc | 6 GHz Operation Info Present | reserved |
         * bits:  14           1              2                   1                   6
         *
         * 6GHz Info Format:
         * | Primary Channel | Control | Center Freq Seg 0 | Center Freq Seg 1 | Min Rate |
         *         1             1               1                  1               1
         *
         * Control Field:
         *       | Channel Width | Reserved |
         * bits:        2             6
         *
         */
        ie.bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x02,  //HE Operation Info
                              (byte) 0x00, (byte) 0x00, (byte) 0x00,  // BSS Color and HE-MCS
                              (byte) 0x10, (byte) 0x02, (byte) 0x14, (byte) 0x00, (byte) 0x00};

        HeOperation heOperation = new HeOperation();
        heOperation.from(ie);

        assertTrue(heOperation.isPresent());
        assertTrue(heOperation.is6GhzInfoPresent());
        assertFalse(heOperation.isVhtInfoPresent());
        assertEquals(ScanResult.CHANNEL_WIDTH_80MHZ, heOperation.getChannelWidth());
        assertEquals(6050, heOperation.getCenterFreq0());
        assertEquals(0, heOperation.getCenterFreq1());
    }

    /**
     * Verify that the expected HE Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel is in 6GHz band and channel width is 160MHz
     *
     * @throws Exception
     */
    @Test
    public void getHeOperationElement160Mhz() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_HE_OPERATION;
        /**
         * HE Operation Format:
         * | HE Operation Info | BSS Color | Basic HE-MCS | VHT Info  | Cohosted BSS| 6GH Info |
         *          3                1            2           0/3           0/1         0/5
         *
         * HE Operation Info:
         *    |  Misc | VHT Operation Info | Misc | 6 GHz Operation Info Present | reserved |
         * bits:  14           1              2                   1                   6
         *
         * 6GHz Info Format:
         * | Primary Channel | Control | Center Freq Seg 0 | Center Freq Seg 1 | Min Rate |
         *         1             1               1                  1               1
         *
         * Control Field:
         *       | Channel Width | Reserved |
         * bits:        2             6
         *
         */
        ie.bytes = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x02,  //HE Operation Info
                              (byte) 0x00, (byte) 0x00, (byte) 0x00,  // BSS Color and HE-MCS
                              (byte) 0x10, (byte) 0x03, (byte) 0x14, (byte) 0x1C, (byte) 0x00};

        HeOperation heOperation = new HeOperation();
        heOperation.from(ie);

        assertTrue(heOperation.isPresent());
        assertTrue(heOperation.is6GhzInfoPresent());
        assertFalse(heOperation.isVhtInfoPresent());
        assertEquals(ScanResult.CHANNEL_WIDTH_160MHZ, heOperation.getChannelWidth());
        assertEquals(6050, heOperation.getCenterFreq0());
        assertEquals(6090, heOperation.getCenterFreq1());
    }

    /**
     * Verify that the expected HE Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel is not in 6GHz band and VHT info not present
     *
     * @throws Exception
     */
    @Test
    public void getHeOperationElementNo6GHzNoVht() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_HE_OPERATION;
        /**
         * HE Operation Format:
         * | HE Operation Info | BSS Color | Basic HE-MCS | VHT Info  | Cohosted BSS| 6GH Info |
         *          3                1            2           0/3           0/1         0/5
         *
         * HE Operation Info:
         *    |  Misc | VHT Operation Info | Misc | 6 GHz Operation Info Present | reserved |
         * bits:  14           1              2                   1                   6
         *
         */
        ie.bytes = new byte[] {
            (byte) 0x00, (byte) 0x00, (byte) 0x00,  //HE Operation Info
            (byte) 0x00, (byte) 0x00, (byte) 0x00   // BSS Color and HE-MCS
        };

        HeOperation heOperation = new HeOperation();
        heOperation.from(ie);

        assertTrue(heOperation.isPresent());
        assertFalse(heOperation.is6GhzInfoPresent());
        assertFalse(heOperation.isVhtInfoPresent());
        assertEquals(ScanResult.UNSPECIFIED, heOperation.getChannelWidth());
        assertEquals(0, heOperation.getCenterFreq0());
        assertEquals(0, heOperation.getCenterFreq1());
    }

    /**
     * Verify that the expected HE Operation information element is parsed and retrieved from the
     * list of IEs.
     * In this test case Channel is not in 6GHz band and VHT info is present
     * channel width is 80 MHz
     *
     * @throws Exception
     */
    @Test
    public void getHeOperationElementNo6GHzWithVht() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_HE_OPERATION;
        /**
         * HE Operation Format:
         * | HE Operation Info | BSS Color | Basic HE-MCS | VHT Info  | Cohosted BSS| 6GH Info |
         *          3                1            2           0/3           0/1         0/5
         *
         * HE Operation Info:
         *    |  Misc | VHT Operation Info | Misc | 6 GHz Operation Info Present | reserved |
         * bits:  14           1              2                   1                   6
         *
         * VHT Operation Info Format:
         * | Channel Width | Channel Center Freq Seg 0 | Channel Center Freq Seg 1 |
         *         1                      1                      1
         */
        ie.bytes = new byte[]{(byte) 0x00, (byte) 0x40, (byte) 0x00,  //HE Operation Info
                              (byte) 0x00, (byte) 0x00, (byte) 0x00,  // BSS Color and HE-MCS
                              (byte) 0x01, (byte) 0x28, (byte) 0x00};

        HeOperation heOperation = new HeOperation();
        heOperation.from(ie);

        assertTrue(heOperation.isPresent());
        assertFalse(heOperation.is6GhzInfoPresent());
        assertTrue(heOperation.isVhtInfoPresent());
        assertEquals(ScanResult.UNSPECIFIED, heOperation.getChannelWidth());
        assertEquals(0, heOperation.getCenterFreq0());
        assertEquals(0, heOperation.getCenterFreq1());

        VhtOperation vhtOperation = new VhtOperation();
        vhtOperation.from(heOperation.getVhtInfoElement());
        assertEquals(ScanResult.CHANNEL_WIDTH_80MHZ, vhtOperation.getChannelWidth());
        assertEquals(5200, vhtOperation.getCenterFreq0());
        assertEquals(0, vhtOperation.getCenterFreq1());
    }

    /**
     * Verify TWT Info, 6 Ghz Access Point Type
     */
    @Test
    public void testHeOperationMisc() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_HE_OPERATION;
        /**
         * HE Operation Format:
         * | HE Operation Info | BSS Color | Basic HE-MCS | VHT Info  | Cohosted BSS| 6GH Info |
         *          3                1            2           0/3           0/1         0/5
         *
         * HE Operation Info:
         *    |  Misc | VHT Operation Info | Misc | 6 GHz Operation Info Present | reserved |
         * bits:  14           1              2                   1                   6
         *
         * 6GHz Info Format:
         * | Primary Channel | Control | Center Freq Seg 0 | Center Freq Seg 1 | Min Rate |
         *         1             1               1                  1               1
         *
         * Control Field:
         *       | Channel Width | Duplicate Beacon | Reg Info | Reserved |
         * bits:        2             1                  3          2
         *
         */
        ie.bytes = new byte[]{(byte) 0x08, (byte) 0x00, (byte) 0x02,  //HE Operation Info
            (byte) 0x00, (byte) 0x00, (byte) 0x00,  // BSS Color and HE-MCS
            (byte) 0x10, (byte) 0x0B, (byte) 0x14, (byte) 0x1C, (byte) 0x00};

        HeOperation heOperation = new HeOperation();
        heOperation.from(ie);
        assertTrue(heOperation.isPresent());
        assertTrue(heOperation.is6GhzInfoPresent());
        assertTrue(heOperation.isTwtRequired());
        assertEquals(ApType6GHz.AP_TYPE_6GHZ_STANDARD_POWER, heOperation.getApType6GHz());
    }

    /**
     * Verify that the expected max number of spatial stream is parsed correctly from
     * HT capabilities IE
     *
     * HT capabilities IE Format:
     * | HT Capability Information | A-MPDU Parameters | Supported MCS Set
     *               2                      1                   16
     * | HT Extended Capabilities | Transmit Beamforming Capabilities | ASEL Capabilities |
     *               2                      4                                   1
     *
     *  Supported MCS Set Format:
     *    B0                   B8                    B16                  B23
     *  | Rx MCS Bitmask 1SS | Rx MCS Bitmask 2SS  | Rx MCS Bitmask 3SS | Rx MCS Bitmask 4SS
     */
    @Test
    public void getMaxNumberSpatialStreamsWithHtCapabilitiesIE() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_HT_CAPABILITIES;
        ie.bytes = new byte[]{(byte) 0xee, (byte) 0x01, (byte) 0x17, (byte) 0xff, (byte) 0xff,
                (byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00};
        InformationElementUtil.HtCapabilities htCapabilities =
                new InformationElementUtil.HtCapabilities();
        htCapabilities.from(ie);
        assertEquals(3, htCapabilities.getMaxNumberSpatialStreams());
        assertEquals(true, htCapabilities.isPresent());
    }

    /**
     * Verify that the expected max number of spatial stream is parsed correctly from
     * VHT capabilities IE
     */
    @Test
    public void getMaxNumberSpatialStreamsWithVhtCapabilitiesIE() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VHT_CAPABILITIES;
        /**
         * VHT Capabilities IE Format:
         * | VHT capabilities Info |  Supported VHT-MCS and NSS Set |
         *           4                              8
         *
         * Supported VHT-MCS set Format:
         *   B0                B2                B4                B6
         * | Max MCS For 1SS | Max MCS For 2SS | Max MCS For 3SS | Max MCS For 4SS
         *   B8                B10               B12               B14
         * | Max MCS For 5SS | Max MCS For 6SS | Max MCS For 7SS | Max MCS For 8SS
         */
        ie.bytes = new byte[]{(byte) 0x92, (byte) 0x01, (byte) 0x80, (byte) 0x33, (byte) 0xaa,
                (byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0xaa, (byte) 0xff, (byte) 0x00,
                (byte) 0x00};
        InformationElementUtil.VhtCapabilities vhtCapabilities =
                new InformationElementUtil.VhtCapabilities();
        vhtCapabilities.from(ie);
        assertEquals(4, vhtCapabilities.getMaxNumberSpatialStreams());
        assertEquals(true, vhtCapabilities.isPresent());
    }

    /**
     * Verify that the expected max number of spatial stream is parsed correctly from
     * HE capabilities IE
     */
    @Test
    public void getMaxNumberSpatialStreamsWithHeCapabilitiesIE() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_HE_CAPABILITIES;
        /**
         * HE Capabilities IE Format:
         * | HE MAC Capabilities Info | HE PHY Capabilities Info | Supported HE-MCS and NSS Set |
         *           6                              11                     4
         *
         * Supported HE-MCS set Format:
         *   B0                B2                B4                B6
         * | Max MCS For 1SS | Max MCS For 2SS | Max MCS For 3SS | Max MCS For 4SS
         *   B8                B10               B12               B14
         * | Max MCS For 5SS | Max MCS For 6SS | Max MCS For 7SS | Max MCS For 8SS
         */
        ie.bytes = new byte[]{(byte) 0x09, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x40,
                (byte) 0x04, (byte) 0x70, (byte) 0x0c, (byte) 0x80, (byte) 0x00, (byte) 0x07,
                (byte) 0x80, (byte) 0x04, (byte) 0x00, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa,
                (byte) 0xaa, (byte) 0x7f, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0x1c,
                (byte) 0xc7, (byte) 0x71};
        InformationElementUtil.HeCapabilities heCapabilities =
                new InformationElementUtil.HeCapabilities();
        heCapabilities.from(ie);
        assertEquals(8, heCapabilities.getMaxNumberSpatialStreams());
        assertEquals(true, heCapabilities.isPresent());
    }

    @Test
    public void testTwtHeCapabilities() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_HE_CAPABILITIES;
        /**
         * HE Capabilities IE Format:
         * | HE MAC Capabilities Info | HE PHY Capabilities Info | Supported HE-MCS and NSS Set |
         *           6                              11                     4
         *
         * HE MAC Capabilities Info format:
         *      B1 : TWT Requester Support
         *      B2 : TWT Responder Support
         *      B20: Broadcast TWT Support
         */
        ie.bytes = new byte[]{(byte) 0x06, (byte) 0x00, (byte) 0x10, (byte) 0x02, (byte) 0x40,
            (byte) 0x04, (byte) 0x70, (byte) 0x0c, (byte) 0x80, (byte) 0x00, (byte) 0x07,
            (byte) 0x80, (byte) 0x04, (byte) 0x00, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa,
            (byte) 0xaa, (byte) 0x7f, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0x1c,
            (byte) 0xc7, (byte) 0x71};
        InformationElementUtil.HeCapabilities heCapabilities =
                new InformationElementUtil.HeCapabilities();
        heCapabilities.from(ie);
        assertEquals(true, heCapabilities.isTwtRequesterSupported());
        assertEquals(true, heCapabilities.isTwtResponderSupported());
        assertEquals(true, heCapabilities.isBroadcastTwtSupported());
    }

    /**
     * Verify that the expected MBO-OCE Vendor Specific information
     * element is parsed and MBO AP Capability Indication is
     * retrieved.
     *
     * @throws Exception
     */
    @Test
    public void parseMboOceIeWithApCapabilityIndicationAttr() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        /**
         * Vendor Specific MBO-OCE IE Format:
         * |  OUI  |  OUI Type  |  MBO-OCE attributes  |
         *     3         1            Variable
         *
         * The Format of MBO Attribute:
         * | Attribute ID | Attribute length | Attribute Body Field
         *        1                1              Variable
         *
         * MBO AP capability indication attribute Body field:
         * | MBO AP Capability Indication field values |
         *                       1
         * |   Reserved   |    Cellular Data aware   |   Reserved
         * Bits: 0x80(MSB)            0x40               0x20-0x01(LSB)
         */
        ie.bytes = new byte[] { (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x16,
                                (byte) 0x01, (byte) 0x01, (byte) 0x40};
        InformationElementUtil.Vsa vsa = new InformationElementUtil.Vsa();
        vsa.from(ie);
        assertEquals(true, vsa.IsMboCapable);
        assertEquals(true, vsa.IsMboApCellularDataAware);
        assertEquals(MboOceConstants.MBO_OCE_ATTRIBUTE_NOT_PRESENT,
                vsa.mboAssociationDisallowedReasonCode);
        assertEquals(false, vsa.IsOceCapable);
    }

    /**
     * Verify that the expected MBO-OCE Vendor Specific information
     * element is parsed and MBO association disallowed reason code is
     * retrieved.
     *
     * @throws Exception
     */
    @Test
    public void parseMboOceIeWithAssociationDisallowedAttr() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        /**
         * Vendor Specific MBO-OCE IE Format:
         * |  OUI  |  OUI Type  |  MBO-OCE attributes  |
         *     3         1            Variable
         *
         * The Format of MBO Attribute:
         * | Attribute ID | Attribute length | Attribute Body Field
         *        1                1              Variable
         *
         * MBO AP capability indication attribute Body field:
         * | MBO AP Capability Indication field values |
         *                       1
         * |   Reserved   |    Cellular Data aware   |   Reserved
         * Bits: 0x80(MSB)            0x40               0x20-0x01(LSB)
         *
         * MBO association disallowed attribute Body field:
         * | Reason code |
         *        1
         */
        ie.bytes = new byte[] { (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x16,
                                (byte) 0x01, (byte) 0x01, (byte) 0x40,
                                (byte) 0x04, (byte) 0x01, (byte) 0x03};
        InformationElementUtil.Vsa vsa = new InformationElementUtil.Vsa();
        vsa.from(ie);
        assertEquals(0x03, vsa.mboAssociationDisallowedReasonCode);
    }

    /**
     * Verify that the expected MBO-OCE Vendor Specific information
     * element is parsed and OCE capability indication attribute is
     * retrieved.
     *
     * @throws Exception
     */
    @Test
    public void parseMboOceIeWithOceCapabilityIndicationAttr() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_VSA;
        /**
         * Vendor Specific MBO-OCE IE Format:
         * |  OUI  |  OUI Type  |  MBO-OCE attributes  |
         *     3         1            Variable
         *
         * The Format of MBO Attribute:
         * | Attribute ID | Attribute length | Attribute Body Field
         *        1                1              Variable
         *
         * MBO AP capability indication attribute Body field:
         * | MBO AP Capability Indication field values |
         *                       1
         * |   Reserved   |    Cellular Data aware   |   Reserved
         * Bits: 0x80(MSB)            0x40               0x20-0x01(LSB)
         *
         * OCE capability indication attribute Body field:
         * | OCE Control field |
         *        1
         * | OCE ver | STA-CFON | 11b-only AP present | HLP Enabled | Non-OCE AP present | Rsvd |
         * Bits: B0 B2    B3             B4                 B5               B6             B7
         */
        ie.bytes = new byte[] { (byte) 0x50, (byte) 0x6F, (byte) 0x9A, (byte) 0x16,
                                (byte) 0x01, (byte) 0x01, (byte) 0x40,
                                (byte) 0x65, (byte) 0x01, (byte) 0x01};
        InformationElementUtil.Vsa vsa = new InformationElementUtil.Vsa();
        vsa.from(ie);
        assertEquals(true, vsa.IsOceCapable);
    }

    /**
     * Tests for parsing RNR IE
     * RNR format as described in IEEE 802.11 specs, Section 9.4.2.170
     *
     *              | ElementID | Length | Neighbor AP Information Fields |
     * Octets:            1          1             variable
     *
     * Where Neighbor AP Information Fields is one or more Neighbor AP Information Field as,
     *
     *               | Header | Operating Class | Channel | TBTT Information Set |
     * Octets:            2            1            1           variable
     *
     * The Header subfield is described as follows,
     *
     *            | Type  | Filtered AP | Reserved | Count | Length |
     * Bits:         2          1           1          4       8
     *
     *
     * Information Set is one or more TBTT Information fields, which is described as,
     *
     *         | Offset | BSSID  | Short-SSID | BSS Params | 20MHz PSD | MLD Params|
     * Octets:     1      0 or 6    0 or 4        0 or 1      0 or 1      0 or 3
     *
     * The MLD Params are described as,
     *       | MLD ID | Link ID | BSS Change Count | Reserved |
     * Bits:     8        4              8              4
     */

    /**
     * Verify that the expected RNR information element to be parsed correctly
     */
    @Test
    public void parseRnrIe() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_RNR;
        ie.bytes = new byte[] {
                (byte) 0x00,  (byte) 0x04, (byte) 81,   (byte) 11,   // First TBTT Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x01, (byte) 0x00, //  First Set
                (byte) 0x10,  (byte) 0x04, (byte) 120,  (byte) 149,  // Second TBTT Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x02, (byte) 0x00, //  First Set
                (byte) 0x00,  (byte) 0x22, (byte) 0x01, (byte) 0x00  //  Second Set
        };
        InformationElementUtil.Rnr rnr = new InformationElementUtil.Rnr();
        rnr.from(ie);
        assertTrue(rnr.isPresent());
        assertEquals(2, rnr.getAffiliatedMloLinks().size());
    }

    /**
     * Tests for parsing Multi-Link IE
     *
     * Multi-Link IE format as described in IEEE 802.11 specs, Section 9.4.2.312
     *
     *              | ElementID | Length | ExtendedID | Control | Common Info | Link Info |
     * Octets:            1          1         1          2        Variable     variable
     *
     *
     * Where Control field is described as,
     *
     *         | Type | Reserved | Presence Bitmap |
     * Bits:      3        1            12
     *
     * Where the Presence Bitmap subfield is described as,
     *
     *        | LinkId | BSS change count | MedSync | EML cap | MLD cap | Reserved |
     * Bits:      1            1               1         1         1         7
     *
     * Common Info filed as described in IEEE 802.11 specs, Section 9.4.2.312,
     *
     *        | Len | MLD Address | Link Id | BSS Change count | MedSync | EML Cap | MLD Cap |
     * Octets:   1        6          0 or 1        0 or 1         0 or 2    0 or 2    0 or 2
     *
     * Link Info filed as described in IEEE 802.11 specs, Section 9.4.2.312,
     *
     *        | ID | Len | STA Control | STA Info | STA Profile |
     * Octets:  1     1        2         variable    variable
     *
     * where STA Control subfield is described as,
     *
     *      | LinkId | Complete | MAC | Beacon Interval | DTIM | NSTR Link | NSTR Bitmap | R |
     * Bits:    4          1       1          1             1        1            1        6
     */

    /**
     * Verify that the expected Multi-Link information element with no link info to be parsed
     * correctly.
     */
    @Test
    public void parseMultiLinkIeNoLinkInfo() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_MULTI_LINK;

        ie.bytes = new byte[] {
                (byte) 0x10,  (byte) 0x00,                              // Control
                (byte) 0x08,  (byte) 0x02, (byte) 0x34, (byte) 0x56,    // Common Info
                (byte) 0x78,  (byte) 0x9A, (byte) 0xBC, (byte) 0x01
        };
        InformationElementUtil.MultiLink multiLink = new InformationElementUtil.MultiLink();
        multiLink.from(ie);
        assertTrue(multiLink.isPresent());
        assertEquals(1, multiLink.getLinkId());
        assertTrue(multiLink.getAffiliatedLinks().isEmpty());
    }

    /**
     * Verify that the expected Multi-Link information element with link info to be parsed
     * correctly.
     */
    @Test
    public void parseMultiLinkIeWithLinkInfo() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_MULTI_LINK;

        ie.bytes = new byte[] {
                (byte) 0x10,  (byte) 0x00,                              // Control
                (byte) 0x08,  (byte) 0x02, (byte) 0x34, (byte) 0x56,    // Common Info
                (byte) 0x78,  (byte) 0x9A, (byte) 0xBC, (byte) 0x01,
                (byte) 0x00,  (byte) 0x08, (byte) 0x02, (byte) 0x00,    // First Link Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x00, (byte) 0x00,    //
                (byte) 0x00,  (byte) 0x08, (byte) 0x03, (byte) 0x00,    // Second Link Info
                (byte) 0x00,  (byte) 0x00, (byte) 0x00, (byte) 0x00     //
        };
        InformationElementUtil.MultiLink multiLink = new InformationElementUtil.MultiLink();
        multiLink.from(ie);
        assertTrue(multiLink.isPresent());
        assertEquals(1, multiLink.getLinkId());
        assertEquals(2, multiLink.getAffiliatedLinks().size());
    }

    /**
     * Verify that the expected Multi-Link information element with fragmented link info to be
     * parsed correctly.
     */
    @Test
    public void parseMultiLinkIeWithFragmentedLinkInfo() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_MULTI_LINK;

        ie.bytes = new byte[] {
                /* Multi-link Control */
                (byte) 0xb0, (byte) 0x01,
                /* Common Info */
                (byte) 0x0d, (byte) 0x40, (byte) 0xed, (byte) 0x00, (byte) 0x14, (byte) 0xf9,
                (byte) 0xf1, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x42,
                (byte) 0x00,
                /* Per STA Profile 1: Fragmented */
                (byte) 0x00, (byte) 0xff, (byte) 0xf0, (byte) 0x01, (byte) 0x13, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x64,
                (byte) 0x00, (byte) 0x22, (byte) 0xa8, (byte) 0x31, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x31,
                (byte) 0x04, (byte) 0x01, (byte) 0x08, (byte) 0x82, (byte) 0x84, (byte) 0x8b,
                (byte) 0x96, (byte) 0x0c, (byte) 0x12, (byte) 0x18, (byte) 0x24, (byte) 0x03,
                (byte) 0x01, (byte) 0x06, (byte) 0x07, (byte) 0x06, (byte) 0x55, (byte) 0x53,
                (byte) 0x20, (byte) 0x01, (byte) 0x0b, (byte) 0x1e, (byte) 0x2a, (byte) 0x01,
                (byte) 0x00, (byte) 0x32, (byte) 0x04, (byte) 0x30, (byte) 0x48, (byte) 0x60,
                (byte) 0x6c, (byte) 0x2d, (byte) 0x1a, (byte) 0x8d, (byte) 0x09, (byte) 0x03,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3d,
                (byte) 0x16, (byte) 0x06, (byte) 0x04, (byte) 0x04, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x4a,
                (byte) 0x0e, (byte) 0x14, (byte) 0x00, (byte) 0x0a, (byte) 0x00, (byte) 0x2c,
                (byte) 0x01, (byte) 0xc8, (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x05,
                (byte) 0x00, (byte) 0x19, (byte) 0x00, (byte) 0x7f, (byte) 0x08, (byte) 0x05,
                (byte) 0x00, (byte) 0x0f, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x40, (byte) 0xbf, (byte) 0x0c, (byte) 0x92, (byte) 0x79, (byte) 0x83,
                (byte) 0x33, (byte) 0xaa, (byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0xaa,
                (byte) 0xff, (byte) 0x00, (byte) 0x20, (byte) 0xc0, (byte) 0x05, (byte) 0x00,
                (byte) 0x06, (byte) 0x00, (byte) 0xfc, (byte) 0xff, (byte) 0xff, (byte) 0x1d,
                (byte) 0x23, (byte) 0x09, (byte) 0x01, (byte) 0x08, (byte) 0x1a, (byte) 0x40,
                (byte) 0x10, (byte) 0x00, (byte) 0x60, (byte) 0x40, (byte) 0x88, (byte) 0x0f,
                (byte) 0x43, (byte) 0x81, (byte) 0x1c, (byte) 0x11, (byte) 0x08, (byte) 0x00,
                (byte) 0xaa, (byte) 0xff, (byte) 0xaa, (byte) 0xff, (byte) 0x1b, (byte) 0x1c,
                (byte) 0xc7, (byte) 0x71, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0xff,
                (byte) 0x07, (byte) 0x24, (byte) 0xf4, (byte) 0x3f, (byte) 0x00, (byte) 0x2e,
                (byte) 0xfc, (byte) 0xff, (byte) 0xff, (byte) 0x0f, (byte) 0x6c, (byte) 0x80,
                (byte) 0x00, (byte) 0xe0, (byte) 0x01, (byte) 0x03, (byte) 0x00, (byte) 0x18,
                (byte) 0x36, (byte) 0x08, (byte) 0x12, (byte) 0x00, (byte) 0x44, (byte) 0x44,
                (byte) 0x44, (byte) 0xff, (byte) 0x06, (byte) 0x6a, (byte) 0x04, (byte) 0x11,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xdd, (byte) 0x17, (byte) 0x8c,
                (byte) 0xfd, (byte) 0xf0, (byte) 0x01, (byte) 0x01, (byte) 0x02, (byte) 0x01,
                (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x03, (byte) 0x03,
                (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x01, (byte) 0x01,
                (byte) 0x09, (byte) 0x02, (byte) 0x0f, (byte) 0x00, (byte) 0xdd, (byte) 0x18,
                (byte) 0x00, (byte) 0x50, (byte) 0xf2, (byte) 0x02, (byte) 0x01, (byte) 0x01,
                (byte) 0x80, (byte) 0x00, (byte) 0x03, (byte) 0xa4, (byte) 0x00, (byte) 0xfe,
                (byte) 0x36, (byte) 0x00, (byte) 0x27, (byte) 0xa4, (byte) 0x00, (byte) 0x00,
                (byte) 0x42, (byte) 0x43, (byte) 0x5e, (byte) 0x00, (byte) 0x62, (byte) 0x32,
                (byte) 0x2f, (byte) 0x00, (byte) 0xdd, (byte) 0x16, (byte) 0x8c, (byte) 0xfd,
                (byte) 0xf0, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x49, (byte) 0x4c,
                (byte) 0x51, (byte) 0x03, (byte) 0x02, (byte) 0x09, (byte) 0x72, (byte) 0x01,
                (byte) 0xcb, (byte) 0x17, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x11,
                (byte) 0x00, (byte) 0x00, (byte) 0xdd, (byte) 0x07, (byte) 0x8c, (byte) 0xfd,
                (byte) 0xf0, (byte) 0x04, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0xff,
                (byte) 0x06, (byte) 0x38, (byte) 0x03, (byte) 0x20, (byte) 0x23, (byte) 0xc3,
                (byte) 0x00,
                /* Per STA Profile 1: Non Fragmented */
                (byte) 0x00, (byte) 0xf8, (byte) 0xf1, (byte) 0x01, (byte) 0x13, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x64,
                (byte) 0x00, (byte) 0x0c, (byte) 0x74, (byte) 0x19, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x11,
                (byte) 0x05, (byte) 0x07, (byte) 0x0a, (byte) 0x55, (byte) 0x53, (byte) 0x04,
                (byte) 0xc9, (byte) 0x83, (byte) 0x00, (byte) 0x21, (byte) 0x33, (byte) 0x00,
                (byte) 0x00, (byte) 0x23, (byte) 0x02, (byte) 0x13, (byte) 0x00, (byte) 0x7f,
                (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x4f, (byte) 0x02, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x40, (byte) 0x00, (byte) 0x00, (byte) 0x09,
                (byte) 0xc3, (byte) 0x05, (byte) 0x53, (byte) 0x3c, (byte) 0x3c, (byte) 0x3c,
                (byte) 0x3c, (byte) 0xc3, (byte) 0x05, (byte) 0x13, (byte) 0x30, (byte) 0x30,
                (byte) 0x30, (byte) 0x30, (byte) 0xff, (byte) 0x27, (byte) 0x23, (byte) 0x09,
                (byte) 0x01, (byte) 0x08, (byte) 0x1a, (byte) 0x40, (byte) 0x10, (byte) 0x0c,
                (byte) 0x63, (byte) 0x40, (byte) 0x88, (byte) 0xfd, (byte) 0x5b, (byte) 0x81,
                (byte) 0x1c, (byte) 0x11, (byte) 0x08, (byte) 0x00, (byte) 0xaa, (byte) 0xff,
                (byte) 0xaa, (byte) 0xff, (byte) 0xaa, (byte) 0xff, (byte) 0xaa, (byte) 0xff,
                (byte) 0x7b, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0x1c, (byte) 0xc7,
                (byte) 0x71, (byte) 0x1c, (byte) 0xc7, (byte) 0x71, (byte) 0x1c, (byte) 0xc7,
                (byte) 0x71, (byte) 0xff, (byte) 0x0c, (byte) 0x24, (byte) 0xf4, (byte) 0x3f,
                (byte) 0x02, (byte) 0x13, (byte) 0xfc, (byte) 0xff, (byte) 0x45, (byte) 0x03,
                (byte) 0x47, (byte) 0x4f, (byte) 0x01, (byte) 0xff, (byte) 0x03, (byte) 0x3b,
                (byte) 0xb8, (byte) 0x36, (byte) 0xff, (byte) 0x12, (byte) 0x6c, (byte) 0x00,
                (byte) 0x00, (byte) 0xe0, (byte) 0x1f, (byte) 0x1b, (byte) 0x00, (byte) 0x18,
                (byte) 0x36, (byte) 0xd8, (byte) 0x36, (byte) 0x00, (byte) 0x44, (byte) 0x44,
                (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0xff, (byte) 0x06,
                (byte) 0x6a, (byte) 0x04, (byte) 0x11, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0xdd, (byte) 0x17, (byte) 0x8c, (byte) 0xfd, (byte) 0xf0, (byte) 0x01,
                (byte) 0x01, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x01,
                (byte) 0x01, (byte) 0x03, (byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) 0x00,
                (byte) 0x04, (byte) 0x01, (byte) 0x01, (byte) 0x09, (byte) 0x02, (byte) 0x0f,
                (byte) 0x0f, (byte) 0xdd, (byte) 0x18, (byte) 0x00, (byte) 0x50, (byte) 0xf2,
                (byte) 0x02, (byte) 0x01, (byte) 0x01, (byte) 0x80, (byte) 0x00, (byte) 0x03,
                (byte) 0xa4, (byte) 0x00, (byte) 0x00, (byte) 0x27, (byte) 0xa4, (byte) 0x00,
                (byte) 0x00, (byte) 0x42, (byte) 0x43, (byte) 0x5e, (byte) 0x00, (byte) 0x62,
                (byte) 0x32, (byte) 0x2f, (byte) 0x00, (byte) 0xdd, (byte) 0x16, (byte) 0x8c,
                (byte) 0xfd, (byte) 0xf0, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x49,
                (byte) 0x4c, (byte) 0x51, (byte) 0x03, (byte) 0x02, (byte) 0x09, (byte) 0x72,
                (byte) 0x01, (byte) 0xcb, (byte) 0x17, (byte) 0x00, (byte) 0x00, (byte) 0x09,
                (byte) 0x11, (byte) 0x00, (byte) 0x00, (byte) 0xdd, (byte) 0x07, (byte) 0x8c,
                (byte) 0xfd, (byte) 0xf0, (byte) 0x04, (byte) 0x01, (byte) 0x01, (byte) 0x00,
                (byte) 0xff, (byte) 0x08, (byte) 0x38, (byte) 0x05, (byte) 0x2d, (byte) 0x3d,
                (byte) 0xbf, (byte) 0xc0, (byte) 0xc9, (byte) 0x00
        };
        InformationElementUtil.MultiLink multiLink = new InformationElementUtil.MultiLink();
        multiLink.from(ie);
        assertTrue(multiLink.isPresent());
        assertEquals(2, multiLink.getLinkId());
        assertEquals(2, multiLink.getAffiliatedLinks().size());
        assertEquals(0, multiLink.getAffiliatedLinks().get(0).getLinkId());
        assertEquals(
                MacAddress.fromString("00:00:00:00:00:01"),
                multiLink.getAffiliatedLinks().get(0).getApMacAddress());
        assertEquals(1, multiLink.getAffiliatedLinks().get(1).getLinkId());
        assertEquals(
                MacAddress.fromString("00:00:00:00:00:02"),
                multiLink.getAffiliatedLinks().get(1).getApMacAddress());
    }
    /**
     * verify determineMode for various combinations.
     */
    @Test
    public void determineMode() throws Exception {
        assertEquals(InformationElementUtil.WifiMode.MODE_11B,
                InformationElementUtil.WifiMode.determineMode(
                        2412, 11000000, false, false, false, false, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11G,
                InformationElementUtil.WifiMode.determineMode(
                        2412, 54000000, false, false, false, false, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11A,
                InformationElementUtil.WifiMode.determineMode(
                        5180, 54000000, false, false, false, false, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11G,
                InformationElementUtil.WifiMode.determineMode(
                        2412, 54000000, false, false, false, false, true));
        assertEquals(InformationElementUtil.WifiMode.MODE_11N,
                InformationElementUtil.WifiMode.determineMode(
                        2412, 72000000, false, false, false, true, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11N,
                InformationElementUtil.WifiMode.determineMode(
                        2412, 72000000, false, false, true, true, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11AC,
                InformationElementUtil.WifiMode.determineMode(
                        5180, 866000000, false, false, true, true, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11AX,
                InformationElementUtil.WifiMode.determineMode(
                        5180, 866000000, false, true, true, true, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11AX,
                InformationElementUtil.WifiMode.determineMode(
                        2412, 72000000, false, true, true, true, false));
        assertEquals(InformationElementUtil.WifiMode.MODE_11BE,
                InformationElementUtil.WifiMode.determineMode(
                        5180, 866000000, true, true, true, true, false));
    }

    /**
     * Verify that the country code is parsed correctly from country IE
     */
    @Test
    public void getCountryCodeWithCountryIE() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        /** Country IE format (size unit: byte)
         *
         * |ElementID | Length | country string | triplet | padding
         *      1          1          3            Q*x       0 or 1
         */
        ie.bytes = new byte[]{(byte) 0x75, (byte) 0x73, (byte) 0x49};
        InformationElementUtil.Country country = new InformationElementUtil.Country();
        country.from(ie);
        assertEquals("US", country.getCountryCode());
    }
    // TODO: SAE, OWN, SUITE_B

    /**
     * Verify EHT capabilities default values.
     */
    @Test
    public void testEhtCapabilitiesNotSet() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_EHT_CAPABILITIES;
        ie.bytes = new byte[2];

        InformationElementUtil.EhtCapabilities ehtCapabilities =
                new InformationElementUtil.EhtCapabilities();
        ehtCapabilities.from(ie);
        assertFalse(ehtCapabilities.isRestrictedTwtSupported());
        assertFalse(ehtCapabilities.isEpcsPriorityAccessSupported());
    }

    /**
     * Verify EHT Capabilities, R-TWT support and EPCS priority support.
     */
    @Test
    public void testEhtCapabilities() {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_EHT_CAPABILITIES;
        ie.bytes = new byte[]{(byte) 0x11, (byte) 0x00};

        InformationElementUtil.EhtCapabilities ehtCapabilities =
                new InformationElementUtil.EhtCapabilities();
        ehtCapabilities.from(ie);
        assertTrue(ehtCapabilities.isRestrictedTwtSupported());
        assertTrue(ehtCapabilities.isEpcsPriorityAccessSupported());
    }

    /**
     * Verify that the expected EHT Operation information element is parsed correctly.
     */
    @Test
    public void testEhtOperationElement() throws Exception {
        InformationElement ie = new InformationElement();
        ie.id = InformationElement.EID_EXTENSION_PRESENT;
        ie.idExt = InformationElement.EID_EXT_EHT_OPERATION;
        /**
         * EHT Operation Format:
         * | EHT Operation Param | Basic EHT-MCS | EHT Operation Info |
         *          1                   1                0/3/5
         *
         * EHT Operation Param:
         * |  EHT Operation Info Present | Disabled Subchannel Bitmap present |
         * bits:       1                                  1                        ...
         *
         * EHT Operation Info:
         * | Control | CCFS0 | CCFS 1 | Disabled Subchannel Bitmap |
         *     1         1       1             0/2
         */
        ie.bytes = new byte[]{(byte) 0x03, //EHT Operation Param
                (byte) 0xfc, (byte) 0xff, (byte) 0xfc, (byte) 0xff, //EHT-MCS
                (byte) 0x03, (byte) 0x32, (byte) 0x32, //EHT Operation Info: Control, CCFS0, CCFS1
                (byte) 0x03, (byte) 0x00}; //EHT Operation Info: Disabled Subchannel Bitmap

        EhtOperation ehtOperation = new EhtOperation();
        ehtOperation.from(ie);

        assertTrue(ehtOperation.isPresent());
        assertTrue(ehtOperation.isEhtOperationInfoPresent());
        assertTrue(ehtOperation.isDisabledSubchannelBitmapPresent());
        assertArrayEquals(new byte[]{(byte) 0x3, (byte) 0x0},
                ehtOperation.getDisabledSubchannelBitmap());
        assertEquals(ScanResult.CHANNEL_WIDTH_160MHZ, ehtOperation.getChannelWidth());
        assertEquals(5250, ehtOperation.getCenterFreq0(ScanResult.WIFI_BAND_5_GHZ));
        assertEquals(5250, ehtOperation.getCenterFreq0(ScanResult.WIFI_BAND_5_GHZ));

        ie.bytes = new byte[]{(byte) 0x01, //EHT Operation Param
                (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0x44, //EHT-MCS
                (byte) 0x04, (byte) 0x2f, (byte) 0x1f}; //EHT Operation Info: Control, CCFS0, CCFS1

        ehtOperation.from(ie);

        assertTrue(ehtOperation.isPresent());
        assertTrue(ehtOperation.isEhtOperationInfoPresent());
        assertFalse(ehtOperation.isDisabledSubchannelBitmapPresent());
        assertEquals(ScanResult.CHANNEL_WIDTH_320MHZ, ehtOperation.getChannelWidth());
        // Center frequency of channel index 47 (0x2F), 160 Mhz
        assertEquals(6185, ehtOperation.getCenterFreq0(ScanResult.WIFI_BAND_6_GHZ));
        // Center frequency of channel index 31 (0x1F), 320 Mhz
        assertEquals(6105, ehtOperation.getCenterFreq1(ScanResult.WIFI_BAND_6_GHZ));

    }
}
