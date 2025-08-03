/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.manifest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

@RunWith(JUnit4.class)
public class AppManifestConfigParserTests {
    private static final String sXml =
            "<on-device-personalization>"
            + "  <service name=\"com.example.TestService\" >"
            + "    <download-settings url=\"http://example.com/get\" />"
            + "    <federated-compute-settings url=\"http://google.com/get\" />"
            + "  </service>"
            + "</on-device-personalization>";


    @Test
    public void testParseManifest() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser xpp = factory.newPullParser();

        xpp.setInput(new StringReader(sXml));
        AppManifestConfig config = AppManifestConfigParser.getConfig(xpp);
        assertEquals("com.example.TestService", config.getServiceName());
        assertEquals("http://example.com/get", config.getDownloadUrl());
        assertEquals("http://google.com/get", config.getFcRemoteServerUrl());
    }
}
