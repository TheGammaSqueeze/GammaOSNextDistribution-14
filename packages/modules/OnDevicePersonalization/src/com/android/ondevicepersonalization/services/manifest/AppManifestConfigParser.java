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



import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** Parser and validator for OnDevicePersonalization app manifest configs. */
public class AppManifestConfigParser {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "AppManifestConfigParser";
    private static final String TAG_ON_DEVICE_PERSONALIZATION_CONFIG = "on-device-personalization";
    private static final String TAG_DOWNLOAD_SETTINGS = "download-settings";
    private static final String TAG_FEDERATED_COMPUTE_SETTINGS = "federated-compute-settings";
    private static final String TAG_SERVICE = "service";
    private static final String ATTR_DOWNLOAD_URL = "url";
    private static final String ATTR_FC_URL = "url";
    private static final String ATTR_NAME = "name";

    private AppManifestConfigParser() {
    }

    /**
     * Parses and validates given XML resource
     *
     * @param parser the XmlParser representing the OnDevicePersonalization app manifest config
     */
    public static AppManifestConfig getConfig(XmlPullParser parser) throws IOException,
            XmlPullParserException {
        String downloadUrl = null;
        String serviceName = null;
        String fcServerUrl = null;

        while (parser.getEventType() != XmlPullParser.START_TAG) {
            parser.next();
        }
        parser.require(XmlPullParser.START_TAG, null, TAG_ON_DEVICE_PERSONALIZATION_CONFIG);
        parser.next();
        while (parser.getEventType() != XmlPullParser.START_TAG) {
            parser.next();
        }
        parser.require(XmlPullParser.START_TAG, null, TAG_SERVICE);
        serviceName = parser.getAttributeValue(null, ATTR_NAME);
        parser.next();

        // Walk through the config to parse required values.
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                parser.next();
                continue;
            }
            switch (parser.getName()) {
                case TAG_DOWNLOAD_SETTINGS:
                    downloadUrl = parser.getAttributeValue(null, ATTR_DOWNLOAD_URL);
                    break;
                case TAG_FEDERATED_COMPUTE_SETTINGS:
                    fcServerUrl = parser.getAttributeValue(null, ATTR_FC_URL);
                    break;
                default:
                    sLogger.i(TAG + ": Unknown tag: " + parser.getName());
            }
            parser.next();
        }

        return new AppManifestConfig(downloadUrl, serviceName, fcServerUrl);
    }
}
