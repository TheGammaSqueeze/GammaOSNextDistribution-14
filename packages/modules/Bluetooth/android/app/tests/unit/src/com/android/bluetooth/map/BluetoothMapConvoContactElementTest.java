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

import static org.mockito.Mockito.when;

import android.util.Xml;

import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.SignedLongLong;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapConvoContactElementTest {
    private static final String TEST_UCI = "test_bt_uci";
    private static final String TEST_NAME = "test_name";
    private static final String TEST_DISPLAY_NAME = "test_display_name";
    private static final String TEST_PRESENCE_STATUS = "test_presence_status";
    private static final int TEST_PRESENCE_AVAILABILITY = 2;
    private static final long TEST_LAST_ACTIVITY = 1;
    private static final int TEST_CHAT_STATE = 2;
    private static final int TEST_PRIORITY = 1;
    private static final String TEST_BT_UID = "1111";

    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

    @Mock
    private MapContact mMapContact;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void constructorWithArguments() {
        BluetoothMapConvoContactElement contactElement =
                new BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, TEST_PRIORITY, TEST_BT_UID);

        assertThat(contactElement.getContactId()).isEqualTo(TEST_UCI);
        assertThat(contactElement.getName()).isEqualTo(TEST_NAME);
        assertThat(contactElement.getDisplayName()).isEqualTo(TEST_DISPLAY_NAME);
        assertThat(contactElement.getPresenceStatus()).isEqualTo(TEST_PRESENCE_STATUS);
        assertThat(contactElement.getPresenceAvailability()).isEqualTo(TEST_PRESENCE_AVAILABILITY);
        assertThat(contactElement.getLastActivityString()).isEqualTo(
                format.format(TEST_LAST_ACTIVITY));
        assertThat(contactElement.getChatState()).isEqualTo(TEST_CHAT_STATE);
        assertThat(contactElement.getPriority()).isEqualTo(TEST_PRIORITY);
        assertThat(contactElement.getBtUid()).isEqualTo(TEST_BT_UID);
    }

    @Test
    public void createFromMapContact() {
        final long id = 1111;
        final SignedLongLong signedLongLong = new SignedLongLong(id, 0);
        when(mMapContact.getId()).thenReturn(id);
        when(mMapContact.getName()).thenReturn(TEST_DISPLAY_NAME);
        BluetoothMapConvoContactElement contactElement =
                BluetoothMapConvoContactElement.createFromMapContact(mMapContact, TEST_UCI);
        assertThat(contactElement.getContactId()).isEqualTo(TEST_UCI);
        assertThat(contactElement.getBtUid()).isEqualTo(signedLongLong.toHexString());
        assertThat(contactElement.getDisplayName()).isEqualTo(TEST_DISPLAY_NAME);
    }

    @Test
    public void settersAndGetters() throws Exception {
        BluetoothMapConvoContactElement contactElement = new BluetoothMapConvoContactElement();
        contactElement.setDisplayName(TEST_DISPLAY_NAME);
        contactElement.setPresenceStatus(TEST_PRESENCE_STATUS);
        contactElement.setPresenceAvailability(TEST_PRESENCE_AVAILABILITY);
        contactElement.setPriority(TEST_PRIORITY);
        contactElement.setName(TEST_NAME);
        contactElement.setBtUid(SignedLongLong.fromString(TEST_BT_UID));
        contactElement.setChatState(TEST_CHAT_STATE);
        contactElement.setLastActivity(TEST_LAST_ACTIVITY);
        contactElement.setContactId(TEST_UCI);

        assertThat(contactElement.getContactId()).isEqualTo(TEST_UCI);
        assertThat(contactElement.getName()).isEqualTo(TEST_NAME);
        assertThat(contactElement.getDisplayName()).isEqualTo(TEST_DISPLAY_NAME);
        assertThat(contactElement.getPresenceStatus()).isEqualTo(TEST_PRESENCE_STATUS);
        assertThat(contactElement.getPresenceAvailability()).isEqualTo(TEST_PRESENCE_AVAILABILITY);
        assertThat(contactElement.getLastActivityString()).isEqualTo(
                format.format(TEST_LAST_ACTIVITY));
        assertThat(contactElement.getChatState()).isEqualTo(TEST_CHAT_STATE);
        assertThat(contactElement.getPriority()).isEqualTo(TEST_PRIORITY);
        assertThat(contactElement.getBtUid()).isEqualTo(TEST_BT_UID);
    }

    @Test
    public void encodeToXml_thenDecodeToInstance_returnsCorrectly() throws Exception {
        BluetoothMapConvoContactElement contactElement = new
                BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, TEST_PRIORITY, TEST_BT_UID);

        final XmlSerializer serializer = Xml.newSerializer();
        final StringWriter writer = new StringWriter();

        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", true);
        contactElement.encode(serializer);
        serializer.endDocument();

        final XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        final XmlPullParser parser;
        parser = parserFactory.newPullParser();

        parser.setInput(new StringReader(writer.toString()));
        parser.next();

        BluetoothMapConvoContactElement contactElementFromXml =
                BluetoothMapConvoContactElement.createFromXml(parser);

        assertThat(contactElementFromXml.getContactId()).isEqualTo(TEST_UCI);
        assertThat(contactElementFromXml.getName()).isEqualTo(TEST_NAME);
        assertThat(contactElementFromXml.getDisplayName()).isEqualTo(TEST_DISPLAY_NAME);
        assertThat(contactElementFromXml.getPresenceStatus()).isEqualTo(TEST_PRESENCE_STATUS);
        assertThat(contactElementFromXml.getPresenceAvailability()).isEqualTo(
                TEST_PRESENCE_AVAILABILITY);
        assertThat(contactElementFromXml.getLastActivityString()).isEqualTo(
                format.format(TEST_LAST_ACTIVITY));
        assertThat(contactElementFromXml.getChatState()).isEqualTo(TEST_CHAT_STATE);
        assertThat(contactElementFromXml.getPriority()).isEqualTo(TEST_PRIORITY);
        assertThat(contactElementFromXml.getBtUid()).isEqualTo(TEST_BT_UID);
    }

    @Test
    public void equalsWithSameValues_returnsTrue() {
        BluetoothMapConvoContactElement contactElement =
                new BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, TEST_PRIORITY, TEST_BT_UID);

        BluetoothMapConvoContactElement contactElementEqual =
                new BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, TEST_PRIORITY, TEST_BT_UID);

        assertThat(contactElement).isEqualTo(contactElementEqual);
    }

    @Test
    public void equalsWithDifferentPriority_returnsFalse() {
        BluetoothMapConvoContactElement contactElement =
                new BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, TEST_PRIORITY, TEST_BT_UID);

        BluetoothMapConvoContactElement contactElementWithDifferentPriority =
                new BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, /*priority=*/0, TEST_BT_UID);

        assertThat(contactElement).isNotEqualTo(contactElementWithDifferentPriority);
    }

    @Test
    public void compareTo_withSameValues_returnsZero() {
        BluetoothMapConvoContactElement contactElement =
                new BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, TEST_PRIORITY, TEST_BT_UID);

        BluetoothMapConvoContactElement contactElementSameLastActivity =
                new BluetoothMapConvoContactElement(TEST_UCI, TEST_NAME, TEST_DISPLAY_NAME,
                TEST_PRESENCE_STATUS, TEST_PRESENCE_AVAILABILITY, TEST_LAST_ACTIVITY,
                TEST_CHAT_STATE, TEST_PRIORITY, TEST_BT_UID);

        assertThat(contactElement.compareTo(contactElementSameLastActivity)).isEqualTo(0);
    }
}