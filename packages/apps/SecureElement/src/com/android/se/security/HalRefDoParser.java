/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.se.security;

import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for HAL_UID_MAP.XML
 * Parses the xml file and collects HAL references (UUID) to identify the corresponding
 * access rules for the HAL services.
 */
public class HalRefDoParser {

    private static final boolean DEBUG = Build.isDebuggable();
    private final String mTag = "SecureElement-HalRefDoParser";

    private static final String PROP_PRODUCT_HARDWARE_SKU = "ro.boot.product.hardware.sku";
    private static final String UUID_MAPPING_CONFIG_PREFIX = "hal_uuid_map_";
    private static final String UUID_MAPPING_CONFIG_EXT = ".xml";
    private static final String[] UUID_MAPPING_CONFIG_PATHS = {"/odm/etc/", "/vendor/etc/",
                                                               "/etc/"};

    // Holds UUID to UIDs mapping
    private final Map<Integer, byte[]> mUUIDMap = new HashMap<Integer, byte[]>();

    private static final String REF_DO = "ref_do";
    private static final String UUID_REF_DO = "uuid_ref_do";
    private static final String UUID = "uuid";
    private static final String UIDS = "uids";
    private static final String UID = "uid";

    private static final byte[] PADDING_BYTES = {
        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};

    private HalRefDoParser() {
        parseUuidMappings();
    }

    private static class HalRefParserSingleton {
        private static final HalRefDoParser INSTANCE = new HalRefDoParser();
    }

    public static HalRefDoParser getInstance() {
        return HalRefParserSingleton.INSTANCE;
    }

    private File getUuidMapConfigFile() {
        // default file name: hal_uuid_map_config.xml
        String uuid_map_config_file_name = UUID_MAPPING_CONFIG_PREFIX
                + SystemProperties.get(PROP_PRODUCT_HARDWARE_SKU, "config")
                + UUID_MAPPING_CONFIG_EXT;
        String uuid_map_config_path = null;

        try {
            // Search in predefined folders
            for (String path : UUID_MAPPING_CONFIG_PATHS) {
                uuid_map_config_path = path + uuid_map_config_file_name;
                File confFile = new File(uuid_map_config_path);
                if (confFile.exists()) {
                    Log.d(mTag, "UUID mapping config file path: " + uuid_map_config_path);
                    return confFile;
                }
            }
        } catch (Exception e) {
            Log.e(mTag, "Error in finding UUID mapping config file path: " + uuid_map_config_path);
        }

        return null;
    }

    /**
     * Parses the below mapping structure -
     *
     * <ref_do>
     *    <uuid_ref_do>
     *        <uids>
     *            <uid>1000</uid>
     *        </uids>
     *        <uuid>a9b7ba70783b317e9998dc4dd82eb3c5</uuid>
     *      </uuid_ref_do>
     * </ref_do>
     */
    private void parse(InputStream is) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            String text = null;
            List<Integer> uids = null;
            byte[] uuid = null;

            parser.setInput(is, null);

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagname = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if (tagname.equalsIgnoreCase(UIDS)) {
                            uids = new ArrayList<Integer>();
                        } else if (tagname.equalsIgnoreCase(UUID)) {
                            uuid = null;
                        }
                        break;

                    case XmlPullParser.TEXT:
                        text = parser.getText();
                        break;

                    case XmlPullParser.END_TAG:
                        if (tagname.equalsIgnoreCase(UUID_REF_DO)) {
                            if (uuid != null) {
                                for (int uid : uids) {
                                    mUUIDMap.put(uid, uuid);
                                }
                            }
                        } else if (tagname.equalsIgnoreCase(UID)) {
                            uids.add(Integer.parseInt(text));
                        } else if (tagname.equalsIgnoreCase(UUID)) {
                            byte[] uuidValue = decodeHexUUID(text);
                            uuid = new byte[uuidValue.length + PADDING_BYTES.length];
                            System.arraycopy(PADDING_BYTES, 0, uuid, 0, PADDING_BYTES.length);
                            System.arraycopy(uuidValue, 0, uuid, PADDING_BYTES.length,
                                    uuidValue.length);

                        }
                        break;

                    default:
                        break;
                }
                eventType = parser.next();
            }

        } catch (XmlPullParserException e) {
            Log.e(mTag, "Error while parsing hal uuid mappings");
            Log.e(mTag, e.getMessage());
        } catch (IOException e) {
            Log.e(mTag, "IO error while parsing hal uuid mappings");
            Log.e(mTag, e.getMessage());
        }
    }

    /**
     * Finds the uuid mapping config file path from predefined folders
     * Parses the uuid mapping config file
     */
    private void parseUuidMappings() {
        try {
            File uuid_map_file = getUuidMapConfigFile();
            if (uuid_map_file == null) {
                Log.e(mTag, "Unable to determine UUID mapping config file path");
                return;
            }

            parse(new FileInputStream(uuid_map_file));
        } catch (Exception e) {
            Log.e(mTag, "Unable to parse hal uuid mappings");
            Log.e(mTag, e.getMessage());
        }

        if (DEBUG) {
            for (Map.Entry<Integer, byte[]> entry : mUUIDMap.entrySet()) {
                Log.d(mTag, "UID: " + entry.getKey());
                Log.d(mTag, "UUID: " + Arrays.toString(entry.getValue()));
            }
        }
    }

    /**
     * Finds UUID for the give UID
     */
    public byte[] findUUID(int uid) {
        return mUUIDMap.get(uid);
    }

    /**
     * Convert char to hex digit
     * @param hexChar
     * @return hex digit
     */
    private int toDigit(char hexChar) {
        int digit = Character.digit(hexChar, 16);
        if (digit == -1) {
            throw new IllegalArgumentException(
                    "Invalid Hexadecimal Character: " + hexChar);
        }
        return digit;
    }

    /**
     * Convert hex digits string to bytes
     * @param hextText
     * @return hex byte
     */
    private byte hexToByte(char ch1, char ch2) {
        int firstDigit = toDigit(ch1);
        int secondDigit = toDigit(ch2);
        return (byte) ((firstDigit << 4) + secondDigit);
    }

    /**
     * Convert hex string to hex byte array
     * @param hextText
     * @return hex bytes
     */
    private byte[] decodeHexUUID(String hextText) {
        if (hextText == null || hextText.length() != 32) {
            throw new IllegalArgumentException(
                    "Invalid UUID supplied");
        }

        byte[] bytes = new byte[hextText.length() / 2];
        for (int i = 0; i < hextText.length(); i += 2) {
            bytes[i / 2] = hexToByte(hextText.charAt(i), hextText.charAt(i + 1));
        }
        return bytes;
    }

}
