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

package com.android.systemui.car.distantdisplay.util;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Util class that contains helper method used by System Bar button.
 */
public class AppCategoryDetector {
    private static final String TAG = AppCategoryDetector.class.getSimpleName();
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String TYPE_VIDEO = "video";
    // Max no. of uses tags in automotiveApp XML. This is an arbitrary limit to be defensive
    // to bad input.
    private static final int MAX_APP_TYPES = 64;

    private AppCategoryDetector() {
    }

    /**
     * Returns whether app identified by {@code packageName} declares itself as a video app.
     */
    public static boolean isVideoApp(PackageManager packageManager, String packageName) {
        return getAutomotiveAppTypes(packageManager, packageName).contains(TYPE_VIDEO);
    }

    /**
     * Queries an app manifest and resources to determine the types of AAOS app it declares itself
     * as.
     *
     * @param packageManager {@link PackageManager} to query.
     * @param packageName App package.
     * @return List of AAOS app-types from XML resources.
     */
    private static List<String> getAutomotiveAppTypes(PackageManager packageManager,
            String packageName) {
        ApplicationInfo appInfo;
        Resources appResources;
        try {
            appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            appResources = packageManager.getResourcesForApplication(appInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unexpected package not found for: " + packageName, e);
            return new ArrayList<>();
        }

        int resourceId =
                appInfo.metaData != null
                        ? appInfo.metaData.getInt("com.android.automotive", -1) : -1;
        Log.d(TAG, "Is package automotive type: " + resourceId);
        if (resourceId == -1) {
            Log.d(TAG, "Returning empty automotive app types");
            return new ArrayList<>();
        }
        try (XmlResourceParser parser = appResources.getXml(resourceId)) {
            return parseAutomotiveAppTypes(parser);
        }
    }

    static List<String> parseAutomotiveAppTypes(XmlPullParser parser) {
        try {
            // This pattern for parsing can be seen in Javadocs for XmlPullParser.
            List<String> appTypes = new ArrayList<>();
            ArrayDeque<String> tagStack = new ArrayDeque<>();
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Start tag " + tag);
                    }
                    tagStack.addFirst(tag);
                    if (!validTagStack(tagStack)) {
                        Log.w(TAG, "Invalid XML; tagStack: " + tagStack);
                        return new ArrayList<>();
                    }
                    if (TAG_USES.equals(tag)) {
                        String nameValue =
                                parser.getAttributeValue(/* namespace= */ null , ATTRIBUTE_NAME);
                        if (TextUtils.isEmpty(nameValue)) {
                            Log.w(TAG, "Invalid XML; uses tag with missing/empty name attribute");
                            return new ArrayList<>();
                        }
                        appTypes.add(nameValue);
                        if (appTypes.size() > MAX_APP_TYPES) {
                            Log.w(TAG, "Too many uses tags in automotiveApp tag");
                            return new ArrayList<>();
                        }
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "Found appType: " + nameValue);
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "End tag " + parser.getName());
                    }
                    tagStack.removeFirst();
                }
                eventType = parser.next();
            }
            return appTypes;
        } catch (XmlPullParserException | IOException e) {
            Log.w(TAG, "Unexpected exception whiling parsing XML resource", e);
            return new ArrayList<>();
        }
    }

    private static boolean validTagStack(ArrayDeque<String> tagStack) {
        // Expected to be called after a new tag is pushed on this stack.
        // Ensures that XML is of form:
        // <automotiveApp>
        //     <uses/>
        //     <uses/>
        //     ....
        // </automotiveApp>
        switch (tagStack.size()) {
            case 1:
                return TAG_AUTOMOTIVE_APP.equals(tagStack.peekFirst());
            case 2:
                return TAG_USES.equals(tagStack.peekFirst());
            default:
                return false;
        }
    }

}
