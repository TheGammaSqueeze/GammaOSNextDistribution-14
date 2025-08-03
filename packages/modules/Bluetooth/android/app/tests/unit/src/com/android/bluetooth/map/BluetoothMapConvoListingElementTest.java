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

import com.android.bluetooth.SignedLongLong;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapConvoListingElementTest {
    private static final long TEST_ID = 1111;
    private static final String TEST_NAME = "test_name";
    private static final long TEST_LAST_ACTIVITY = 0;
    private static final boolean TEST_READ = true;
    private static final boolean TEST_REPORT_READ = true;
    private static final long TEST_VERSION_COUNTER = 0;
    private static final int TEST_CURSOR_INDEX = 1;
    private static final TYPE TEST_TYPE = TYPE.EMAIL;
    private static final String TEST_SUMMARY = "test_summary";
    private static final String TEST_SMS_MMS_CONTACTS = "test_sms_mms_contacts";

    private final BluetoothMapConvoContactElement TEST_CONTACT_ELEMENT_ONE =
            new BluetoothMapConvoContactElement("test_uci_one", "test_name_one",
                    "test_display_name_one", "test_presence_status_one", 2, TEST_LAST_ACTIVITY, 2,
                    1, "1111");

    private final BluetoothMapConvoContactElement TEST_CONTACT_ELEMENT_TWO =
            new BluetoothMapConvoContactElement("test_uci_two", "test_name_two",
                    "test_display_name_two", "test_presence_status_two", 1, TEST_LAST_ACTIVITY, 1,
                    2, "1112");

    private final List<BluetoothMapConvoContactElement> TEST_CONTACTS = new ArrayList<>(
            Arrays.asList(TEST_CONTACT_ELEMENT_ONE, TEST_CONTACT_ELEMENT_TWO));

    private final SignedLongLong signedLongLong = new SignedLongLong(TEST_ID, 0);

    private BluetoothMapConvoListingElement mListingElement;

    @Before
    public void setUp() throws Exception {
        mListingElement = new BluetoothMapConvoListingElement();

        mListingElement.setCursorIndex(TEST_CURSOR_INDEX);
        mListingElement.setVersionCounter(TEST_VERSION_COUNTER);
        mListingElement.setName(TEST_NAME);
        mListingElement.setType(TEST_TYPE);
        mListingElement.setContacts(TEST_CONTACTS);
        mListingElement.setLastActivity(TEST_LAST_ACTIVITY);
        mListingElement.setRead(TEST_READ, TEST_REPORT_READ);
        mListingElement.setConvoId(0, TEST_ID);
        mListingElement.setSummary(TEST_SUMMARY);
        mListingElement.setSmsMmsContacts(TEST_SMS_MMS_CONTACTS);
    }

    @Test
    public void getters() throws Exception {
        assertThat(mListingElement.getCursorIndex()).isEqualTo(TEST_CURSOR_INDEX);
        assertThat(mListingElement.getVersionCounter()).isEqualTo(TEST_VERSION_COUNTER);
        assertThat(mListingElement.getName()).isEqualTo(TEST_NAME);
        assertThat(mListingElement.getType()).isEqualTo(TEST_TYPE);
        assertThat(mListingElement.getContacts()).isEqualTo(TEST_CONTACTS);
        assertThat(mListingElement.getLastActivity()).isEqualTo(TEST_LAST_ACTIVITY);
        assertThat(mListingElement.getRead()).isEqualTo("READ");
        assertThat(mListingElement.getReadBool()).isEqualTo(TEST_READ);
        assertThat(mListingElement.getConvoId()).isEqualTo(signedLongLong.toHexString());
        assertThat(mListingElement.getCpConvoId()).isEqualTo(
                signedLongLong.getLeastSignificantBits());
        assertThat(mListingElement.getFullSummary()).isEqualTo(TEST_SUMMARY);
        assertThat(mListingElement.getSmsMmsContacts()).isEqualTo(TEST_SMS_MMS_CONTACTS);
    }

    @Test
    public void incrementVersionCounter() {
        mListingElement.incrementVersionCounter();
        assertThat(mListingElement.getVersionCounter()).isEqualTo(TEST_VERSION_COUNTER + 1);
    }

    @Test
    public void removeContactWithObject() {
        mListingElement.removeContact(TEST_CONTACT_ELEMENT_TWO);
        assertThat(mListingElement.getContacts().size()).isEqualTo(1);
    }

    @Test
    public void removeContactWithIndex() {
        mListingElement.removeContact(1);
        assertThat(mListingElement.getContacts().size()).isEqualTo(1);
    }

    @Test
    public void encodeToXml_thenDecodeToInstance_returnsCorrectly() throws Exception {
        final XmlSerializer serializer = Xml.newSerializer();
        final StringWriter writer = new StringWriter();

        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", true);
        mListingElement.encode(serializer);
        serializer.endDocument();

        final XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        final XmlPullParser parser;
        parser = parserFactory.newPullParser();

        parser.setInput(new StringReader(writer.toString()));
        parser.next();

        BluetoothMapConvoListingElement listingElementFromXml =
                BluetoothMapConvoListingElement.createFromXml(parser);

        assertThat(listingElementFromXml.getVersionCounter()).isEqualTo(0);
        assertThat(listingElementFromXml.getName()).isEqualTo(TEST_NAME);
        assertThat(listingElementFromXml.getContacts()).isEqualTo(TEST_CONTACTS);
        assertThat(listingElementFromXml.getLastActivity()).isEqualTo(TEST_LAST_ACTIVITY);
        assertThat(listingElementFromXml.getRead()).isEqualTo("UNREAD");
        assertThat(listingElementFromXml.getConvoId()).isEqualTo(signedLongLong.toHexString());
        assertThat(listingElementFromXml.getFullSummary().trim()).isEqualTo(TEST_SUMMARY);
    }

    @Test
    public void equalsWithSameValues_returnsTrue() {
        BluetoothMapConvoListingElement listingElement = new BluetoothMapConvoListingElement();
        listingElement.setName(TEST_NAME);
        listingElement.setContacts(TEST_CONTACTS);
        listingElement.setLastActivity(TEST_LAST_ACTIVITY);
        listingElement.setRead(TEST_READ, TEST_REPORT_READ);

        BluetoothMapConvoListingElement listingElementEqual = new BluetoothMapConvoListingElement();
        listingElementEqual.setName(TEST_NAME);
        listingElementEqual.setContacts(TEST_CONTACTS);
        listingElementEqual.setLastActivity(TEST_LAST_ACTIVITY);
        listingElementEqual.setRead(TEST_READ, TEST_REPORT_READ);

        assertThat(listingElement).isEqualTo(listingElementEqual);
    }

    @Test
    public void equalsWithDifferentRead_returnsFalse() {
        BluetoothMapConvoListingElement
                listingElement = new BluetoothMapConvoListingElement();

        BluetoothMapConvoListingElement listingElementWithDifferentRead =
                new BluetoothMapConvoListingElement();
        listingElementWithDifferentRead.setRead(TEST_READ, TEST_REPORT_READ);

        assertThat(listingElement).isNotEqualTo(listingElementWithDifferentRead);
    }

    @Test
    public void compareToWithSameValues_returnsZero() {
        BluetoothMapConvoListingElement
                listingElement = new BluetoothMapConvoListingElement();
        listingElement.setLastActivity(TEST_LAST_ACTIVITY);

        BluetoothMapConvoListingElement listingElementSameLastActivity =
                new BluetoothMapConvoListingElement();
        listingElementSameLastActivity.setLastActivity(TEST_LAST_ACTIVITY);

        assertThat(listingElement.compareTo(listingElementSameLastActivity)).isEqualTo(0);
    }
}