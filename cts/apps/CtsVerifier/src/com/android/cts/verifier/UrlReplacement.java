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

package com.android.cts.verifier;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads URL replacement configurations from the give config file, and provides the method to do the
 * URL replacement.
 */
public class UrlReplacement {
    private static final String LOG_TAG = UrlReplacement.class.getName();

    private static final String NS = null;
    private static final String DYNAMIC_CONFIG_URL_TAG = "dynamicConfigUrl";
    private static final String URL_REPLACEMENT_TAG = "urlReplacement";
    private static final String ENTRY_TAG = "entry";
    private static final String REPLACEMENT_TAG = "replacement";
    private static final String URL_ATTR = "url";
    private static final String URL_REPLACEMENT_FILE_PATH = "/sdcard/UrlReplacement.xml";
    private static final Map<String, String> REPLACEMENT_MAP = new HashMap<>();

    static {
        File urlReplacementConfigFile = new File(URL_REPLACEMENT_FILE_PATH);
        if (urlReplacementConfigFile.exists()) {
            Log.i(LOG_TAG, String.format("Parsing URL replacement config file %s ...",
                    URL_REPLACEMENT_FILE_PATH));
            try {
                InputStream inputStream = new FileInputStream(urlReplacementConfigFile);
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                parser.setInput(new InputStreamReader(inputStream));
                parser.nextTag();

                parser.require(XmlPullParser.START_TAG, NS, URL_REPLACEMENT_TAG);
                parser.nextTag();

                parser.require(XmlPullParser.START_TAG, NS, DYNAMIC_CONFIG_URL_TAG);
                // Ignore the DynamicConfig server URL.
                parser.nextText();
                parser.require(XmlPullParser.END_TAG, NS, DYNAMIC_CONFIG_URL_TAG);

                while (parser.nextTag() == XmlPullParser.START_TAG) {
                    parser.require(XmlPullParser.START_TAG, NS, ENTRY_TAG);
                    String key = parser.getAttributeValue(NS, URL_ATTR);
                    parser.nextTag();
                    parser.require(XmlPullParser.START_TAG, NS, REPLACEMENT_TAG);
                    String value = parser.nextText();
                    parser.require(XmlPullParser.END_TAG, NS, REPLACEMENT_TAG);
                    parser.nextTag();
                    parser.require(XmlPullParser.END_TAG, NS, ENTRY_TAG);
                    if (key != null && value != null) {
                        REPLACEMENT_MAP.put(key, value);
                    }
                }

                parser.require(XmlPullParser.END_TAG, NS, URL_REPLACEMENT_TAG);

                Log.i(LOG_TAG, String.format("Parsed URL replacement config: %s",
                        REPLACEMENT_MAP));
            } catch (XmlPullParserException | IOException e) {
                Log.w(LOG_TAG, String.format("Failed to parse URL replacement config file: %s.",
                        e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * Replaces the URLs that existed in the key set of {@code REPLACEMENT_MAP} with the
     * corresponding value, and returns the final result.
     */
    public static String getFinalUri(String originalUrl) {
        String url = originalUrl;
        for (Map.Entry<String, String> entry : REPLACEMENT_MAP.entrySet()) {
            url.replace(entry.getKey(), entry.getValue());
        }
        return url;
    }
}
