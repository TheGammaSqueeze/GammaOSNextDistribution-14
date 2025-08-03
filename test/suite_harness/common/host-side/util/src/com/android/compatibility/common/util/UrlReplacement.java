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

package com.android.compatibility.common.util;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads URL replacement configurations from the config file UrlReplacement.xml in the
 * android-cts/tools directory (if it exists), or from the file path set in the env variable
 * URL_REPLACEMENT_PATH.
 *
 * <p>Example usage: UrlReplacement.init(); // MUST be the first call to this class. String
 * dynamicConfigUrl = UrlReplacement.getDynamicConfigServerUrl(); ...
 * UrlReplacement.getUrlReplacementMap();
 */
public final class UrlReplacement {
    private static final String NS = null;
    private static final String DYNAMIC_CONFIG_URL_TAG = "dynamicConfigUrl";
    private static final String URL_REPLACEMENT_TAG = "urlReplacement";
    private static final String ENTRY_TAG = "entry";
    private static final String REPLACEMENT_TAG = "replacement";
    private static final String URL_ATTR = "url";
    private static final String URL_REPLACEMENT_FILENAME = "UrlReplacement.xml";

    private static final String URL_REPLACEMENT_PATH_ENV = "URL_REPLACEMENT_PATH";

    private static String dynamicConfigServerUrl = null;
    private static Map<String, String> urlReplacementMap = new HashMap<>();

    @VisibleForTesting static Boolean initialized = false;

    /** Initializes the Dynamic Config Server URL and URL replacements from the xml file. */
    public static void init() throws TargetSetupError {
        init(getUrlReplacementConfigFile());
    }

    /** Gets the URL for the DynamicConfigServer. Must be called after init(). */
    public static String getDynamicConfigServerUrl() {
        return dynamicConfigServerUrl;
    }

    /** Gets the URL replacement map. Must be called after init(). */
    public static Map<String, String> getUrlReplacementMap() {
        return new HashMap<>(urlReplacementMap);
    }

    @VisibleForTesting
    static void init(File urlReplacementConfigFile) throws TargetSetupError {
        if (initialized) {
            return;
        }
        synchronized (UrlReplacement.class) {
            if (initialized) {
                return;
            }
            try {
                if (urlReplacementConfigFile.exists()) {
                    InputStream inputStream = new FileInputStream(urlReplacementConfigFile);
                    XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                    parser.setInput(new InputStreamReader(inputStream));
                    parser.nextTag();

                    parser.require(XmlPullParser.START_TAG, NS, URL_REPLACEMENT_TAG);
                    parser.nextTag();

                    parser.require(XmlPullParser.START_TAG, NS, DYNAMIC_CONFIG_URL_TAG);
                    dynamicConfigServerUrl = parser.nextText();
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
                            urlReplacementMap.put(key, value);
                        }
                    }

                    parser.require(XmlPullParser.END_TAG, NS, URL_REPLACEMENT_TAG);
                } else {
                    CLog.i(
                            "UrlReplacement file [%s] does not exist",
                            urlReplacementConfigFile.getAbsolutePath());
                }
                initialized = true;
            } catch (XmlPullParserException | IOException e) {
                throw new TargetSetupError(
                        "Failed to parse URL replacement config", e, (DeviceDescriptor) null);
            }
        }
    }

    @VisibleForTesting
    static File getUrlReplacementConfigFile() throws TargetSetupError {
        // Try to get the config file from the .jar directory.
        CodeSource codeSource = UrlReplacement.class.getProtectionDomain().getCodeSource();
        String toolDirectory;
        try {
            toolDirectory = new File(codeSource.getLocation().toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new TargetSetupError(
                    "Failed to locate urlReplacement.xml file", e, (DeviceDescriptor) null);
        }
        File file = new File(toolDirectory + File.separator + URL_REPLACEMENT_FILENAME);

        // Try to get the config file from environment variable.
        if (!file.exists()) {
            String urlReplacementPathEnv = System.getenv(URL_REPLACEMENT_PATH_ENV);
            if (urlReplacementPathEnv != null) {
                return new File(urlReplacementPathEnv);
            }
        }
        return file;
    }

    private UrlReplacement() {}
}
