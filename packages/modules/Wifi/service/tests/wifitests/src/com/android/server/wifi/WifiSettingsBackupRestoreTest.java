/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.WifiSettingsBackupRestore.XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.inOrder;

import android.util.Xml;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Unit tests for {@link com.android.server.wifi.WifiSettingsBackupRestoreTest}.
 */
@SmallTest
public class WifiSettingsBackupRestoreTest extends WifiBaseTest {

    @Mock WifiSettingsConfigStore mWifiSettingsConfigStore;
    @Mock XmlPullParser mXmlPullParser;
    @Mock ByteArrayOutputStream mByteArrayOutputStream;
    @Mock XmlSerializer mXmlSerializer;

    @SuppressWarnings("DoubleBraceInitialization")
    private static final ArrayList<WifiSettingsConfigStore.Key> TEST_KEYS = new ArrayList<>() {{
            add(WifiSettingsConfigStore.WIFI_WEP_ALLOWED); }};

    /**
     * The data we backup in this module is <WifiSettingsBackData></WifiSettingsBackData>
     * and the depth is 1.
     */
    public static String generateTestWifiSettingsTestingXml(String backupKeys) {
        return  "<WifiSettingsBackData>\n"
            + "<Settings>\n"
            + "<map name=\"Values\">\n"
            + backupKeys
            + "</map>\n"
            + "</Settings>\n"
            + "</WifiSettingsBackData>\n";
    }

    private WifiSettingsBackupRestore mWifiSettingsBackupRestore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWifiSettingsConfigStore.getAllKeys()).thenReturn(TEST_KEYS);
        when(mWifiSettingsConfigStore.getAllBackupRestoreKeys()).thenReturn(TEST_KEYS);
        mWifiSettingsBackupRestore = new WifiSettingsBackupRestore(mWifiSettingsConfigStore);
        mWifiSettingsBackupRestore.enableVerboseLogging(true);
    }

    private XmlPullParser generateTestXmlPullParser(byte[] data) throws Exception {
        final XmlPullParser in = Xml.newPullParser();
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(data);
        in.setInput(inputStream, StandardCharsets.UTF_8.name());
        return in;
    }

    @Test
    public void testBackupWifiSettings() throws Exception {
        InOrder order = inOrder(mXmlSerializer);
        mWifiSettingsBackupRestore.retrieveBackupDataFromSettingsConfigStore(
                mXmlSerializer, mByteArrayOutputStream);
        order.verify(mXmlSerializer).startTag(eq(null),
                eq(XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA));
        order.verify(mXmlSerializer).startTag(eq(null),
                eq(WifiSettingsConfigStore.StoreData.XML_TAG_SECTION_HEADER));
        order.verify(mXmlSerializer).startTag(eq(null), eq("map"));
        order.verify(mXmlSerializer).attribute(eq(null), eq("name"),
                eq(WifiSettingsConfigStore.StoreData.XML_TAG_VALUES));
        for (WifiSettingsConfigStore.Key key : mWifiSettingsConfigStore.getAllKeys()) {
            order.verify(mXmlSerializer).attribute(eq(null), eq("name"), eq(key.getKey()));
        }
        order.verify(mXmlSerializer).endTag(eq(null), eq("map"));
        order.verify(mXmlSerializer).endTag(eq(null),
                eq(WifiSettingsConfigStore.StoreData.XML_TAG_SECTION_HEADER));
        order.verify(mXmlSerializer).endTag(eq(null),
                eq(XML_TAG_SECTION_HEADER_WIFI_SETTINGS_DATA));
    }

    @Test
    public void testRestoreWifiSettings() throws Exception {
        String testBackupMap = "<boolean name=\"wep_allowed\" value=\"true\" />\n";
        mWifiSettingsBackupRestore.restoreSettingsFromBackupData(
                generateTestXmlPullParser(
                        generateTestWifiSettingsTestingXml(testBackupMap).getBytes()),
                        0 /* The depth of Tag: WifiSettingsBackData */);
        verify(mWifiSettingsConfigStore).put(eq(WifiSettingsConfigStore.WIFI_WEP_ALLOWED),
                eq(Boolean.TRUE));
    }

    @Test
    public void testRestoreWifiSettingsWithUnknownTag() throws Exception {
        String testBackupMap = "<boolean name=\"unknown_tag\" value=\"true\" />\n";
        mWifiSettingsBackupRestore.restoreSettingsFromBackupData(
                generateTestXmlPullParser(
                        generateTestWifiSettingsTestingXml(testBackupMap).getBytes()),
                        0 /* The depth of Tag: WifiSettingsBackData */);
        verify(mWifiSettingsConfigStore, never()).put(any(), any());
    }
}
