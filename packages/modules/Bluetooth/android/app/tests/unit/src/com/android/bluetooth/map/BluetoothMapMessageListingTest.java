/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import android.util.Xml;

import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.Utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapMessageListingTest {
    private static final long TEST_DATE_TIME_EARLIEST = 0;
    private static final long TEST_DATE_TIME_MIDDLE = 1;
    private static final long TEST_DATE_TIME_LATEST = 2;
    private static final boolean TEST_READ = true;
    private static final boolean TEST_REPORT_READ = true;
    private static final String TEST_VERSION = "test_version";

    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private BluetoothMapMessageListingElement mListingElementEarliestWithReadFalse;
    private BluetoothMapMessageListingElement mListingElementMiddleWithReadFalse;
    private BluetoothMapMessageListingElement mListingElementLatestWithReadTrue;

    private BluetoothMapMessageListing mListing;

    @Before
    public void setUp() {
        mListingElementEarliestWithReadFalse = new BluetoothMapMessageListingElement();
        mListingElementEarliestWithReadFalse.setDateTime(TEST_DATE_TIME_EARLIEST);

        mListingElementMiddleWithReadFalse = new BluetoothMapMessageListingElement();
        mListingElementMiddleWithReadFalse.setDateTime(TEST_DATE_TIME_MIDDLE);

        mListingElementLatestWithReadTrue = new BluetoothMapMessageListingElement();
        mListingElementLatestWithReadTrue.setDateTime(TEST_DATE_TIME_LATEST);
        mListingElementLatestWithReadTrue.setRead(TEST_READ, TEST_REPORT_READ);

        mListing = new BluetoothMapMessageListing();
        mListing.add(mListingElementEarliestWithReadFalse);
        mListing.add(mListingElementMiddleWithReadFalse);
        mListing.add(mListingElementLatestWithReadTrue);
    }

    @Test
    public void addElement() {
        final BluetoothMapMessageListing listing = new BluetoothMapMessageListing();
        assertThat(listing.getCount()).isEqualTo(0);
        listing.add(mListingElementEarliestWithReadFalse);
        assertThat(listing.getCount()).isEqualTo(1);
        assertThat(listing.hasUnread()).isEqualTo(true);
    }

    @Test
    public void segment_whenCountIsLessThanOne_returnsOffsetToEnd() {
        mListing.segment(0, 1);
        assertThat(mListing.getList().size()).isEqualTo(2);
    }

    @Test
    public void segment_whenOffsetIsBiggerThanSize_returnsEmptyList() {
        mListing.segment(1, 4);
        assertThat(mListing.getList().size()).isEqualTo(0);
    }

    @Test
    public void segment_whenOffsetCountCombinationIsValid_returnsCorrectly() {
        mListing.segment(1, 1);
        assertThat(mListing.getList().size()).isEqualTo(1);
    }

    @Test
    public void sort() {
        // BluetoothMapMessageListingElements are sorted according to their mDateTime values
        mListing.sort();
        assertThat(mListing.getList().get(0).getDateTime()).isEqualTo(TEST_DATE_TIME_LATEST);
        assertThat(mListing.getList().get(1).getDateTime()).isEqualTo(TEST_DATE_TIME_MIDDLE);
        assertThat(mListing.getList().get(2).getDateTime()).isEqualTo(TEST_DATE_TIME_EARLIEST);
    }

    @Test
    public void encodeToXml_thenAppendFromXml() throws Exception {
        final BluetoothMapMessageListing listingToAppend = new BluetoothMapMessageListing();
        final BluetoothMapMessageListingElement listingElementToAppendOne =
                new BluetoothMapMessageListingElement();
        final BluetoothMapMessageListingElement listingElementToAppendTwo =
                new BluetoothMapMessageListingElement();

        listingElementToAppendOne.setDateTime(TEST_DATE_TIME_EARLIEST);
        listingElementToAppendTwo.setRead(TEST_READ, TEST_REPORT_READ);

        listingToAppend.add(listingElementToAppendOne);
        listingToAppend.add(listingElementToAppendTwo);

        assertThat(listingToAppend.getList().size()).isEqualTo(2);

        final InputStream listingStream = new ByteArrayInputStream(
                listingToAppend.encode(false, TEST_VERSION));

        BluetoothMapMessageListing listing = new BluetoothMapMessageListing();
        appendFromXml(listingStream, listing);
        assertThat(listing.getList().size()).isEqualTo(2);
        assertThat(listing.getList().get(0).getDateTime()).isEqualTo(TEST_DATE_TIME_EARLIEST);
        assertThat(listing.getList().get(1).getReadBool()).isTrue();
    }

    /**
     * Decodes the encoded xml document then append the BluetoothMapMessageListingElements to the
     * given BluetoothMapMessageListing object.
     */
    private void appendFromXml(InputStream xmlDocument, BluetoothMapMessageListing newListing)
            throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            int type;
            parser.setInput(xmlDocument, "UTF-8");

            while ((type = parser.next()) != XmlPullParser.END_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if (!name.equalsIgnoreCase("MAP-msg-listing")) {
                    Utils.skipCurrentTag(parser);
                }
                readMessageElements(parser, newListing);
            }
        } finally {
            xmlDocument.close();
        }
    }

    private void readMessageElements(XmlPullParser parser, BluetoothMapMessageListing newListing)
            throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != XmlPullParser.END_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (!name.trim().equalsIgnoreCase("msg")) {
                Utils.skipCurrentTag(parser);
                continue;
            }
            newListing.add(createFromXml(parser));
        }
    }

    private BluetoothMapMessageListingElement createFromXml(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        BluetoothMapMessageListingElement newElement = new BluetoothMapMessageListingElement();
        int count = parser.getAttributeCount();
        for (int i = 0; i < count; i++) {
            String attributeName = parser.getAttributeName(i).trim();
            String attributeValue = parser.getAttributeValue(i);
            if (attributeName.equalsIgnoreCase("datetime")) {
                newElement.setDateTime(LocalDateTime.parse(attributeValue, formatter).toInstant(
                        ZoneOffset.ofTotalSeconds(0)).toEpochMilli());
            } else if (attributeName.equalsIgnoreCase("read")) {
                newElement.setRead(true, true);
            }
        }
        parser.nextTag();
        return newElement;
    }
}