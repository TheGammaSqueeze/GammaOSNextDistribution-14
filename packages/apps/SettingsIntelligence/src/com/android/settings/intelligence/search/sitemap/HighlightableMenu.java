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

package com.android.settings.intelligence.search.sitemap;

import android.content.Context;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.settings.intelligence.overlay.FeatureFactory;
import com.android.settings.intelligence.search.indexing.IndexData;
import com.android.settingslib.activityembedding.ActivityEmbeddingUtils;

import java.util.Map;
import java.util.Map.Entry;

public class HighlightableMenu {

    private static final String TAG = "HighlightableMenu";
    private static final boolean DEBUG = false;

    public static final String MENU_KEY_NETWORK = "top_level_network";
    public static final String MENU_KEY_APPS = "top_level_apps";
    public static final String MENU_KEY_ACCESSIBILITY = "top_level_accessibility";
    public static final String MENU_KEY_PRIVACY = "top_level_privacy";
    public static final String MENU_KEY_SYSTEM = "top_level_system";

    private static final Map<String, String> sAuthorityToMenuKeyMap;
    private static final Map<String, String> sPackageToMenuKeyMap;

    static {
        sAuthorityToMenuKeyMap = new ArrayMap<>();
        sAuthorityToMenuKeyMap.put(
                "com.android.permissioncontroller.role", MENU_KEY_APPS); // Default apps

        sPackageToMenuKeyMap = new ArrayMap<>();
        sPackageToMenuKeyMap.put(
                "com.android.settings.network", MENU_KEY_NETWORK); // Settings Network page
        sPackageToMenuKeyMap.put(
                "com.android.permissioncontroller", MENU_KEY_PRIVACY); // Permission manager
    }

    private HighlightableMenu() {
    }

    public static boolean isFeatureEnabled(Context context) {
        boolean enabled = ActivityEmbeddingUtils.isEmbeddingActivityEnabled(context);
        Log.i(TAG, "isFeatureEnabled: " + enabled);
        return enabled;
    }

    public static String getMenuKey(Context context, IndexData row) {
        String menuKey;
        SiteMapManager siteMap = FeatureFactory.get(context).searchFeatureProvider()
                .getSiteMapManager();

        // look up in SiteMap
        SiteMapPair pair = siteMap.getTopLevelPair(context, row.className, row.screenTitle);
        if (pair != null) {
            menuKey = pair.getHighlightableMenuKey();
            if (!TextUtils.isEmpty(menuKey)) {
                return menuKey;
            }
        }

        // look up in custom authority map
        menuKey = sAuthorityToMenuKeyMap.get(row.authority);
        if (!TextUtils.isEmpty(menuKey)) {
            logD("Matched authority, title: " + row.updatedTitle + ", menuKey: " + menuKey);
            return menuKey;
        }

        // look up in custom package map (package match)
        menuKey = sPackageToMenuKeyMap.get(row.packageName);
        if (!TextUtils.isEmpty(menuKey)) {
            logD("Matched package, title: " + row.updatedTitle + ", menuKey: " + menuKey);
            return menuKey;
        }

        // look up in custom package map (target package match)
        menuKey = sPackageToMenuKeyMap.get(row.intentTargetPackage);
        if (!TextUtils.isEmpty(menuKey)) {
            logD("Matched target package, title: " + row.updatedTitle + ", menuKey: " + menuKey);
            return menuKey;
        }

        // look up in custom package map (class prefix match)
        if (!TextUtils.isEmpty(row.className)) {
            for (Entry<String, String> entry : sPackageToMenuKeyMap.entrySet()) {
                if (row.className.startsWith(entry.getKey())) {
                    menuKey = entry.getValue();
                    if (!TextUtils.isEmpty(menuKey)) {
                        logD("Matched class prefix, title: " + row.updatedTitle
                                + ", menuKey: " + menuKey);
                        return menuKey;
                    }
                }
            }
        }

        logD("Cannot get menu key for: " + row.updatedTitle
                + ", data key: " + row.key
                + ", top-level: " + (pair != null ? pair.getParentTitle() : row.screenTitle)
                + ", package: " + row.packageName);
        return menuKey;
    }

    private static void logD(String log) {
        if (DEBUG) {
            Log.d(TAG, log);
        }
    }
}
