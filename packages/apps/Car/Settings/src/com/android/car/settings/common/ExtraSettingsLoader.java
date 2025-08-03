/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.common;

import static com.android.settingslib.drawer.CategoryKey.CATEGORY_DEVICE;
import static com.android.settingslib.drawer.TileUtils.META_DATA_KEY_ORDER;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_KEYHINT;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY_URI;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_TITLE;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_TITLE_URI;

import static java.lang.String.CASE_INSENSITIVE_ORDER;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiPreference;
import com.android.settingslib.drawer.TileUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads Activity with TileUtils.EXTRA_SETTINGS_ACTION.
 */
// TODO: investigate using SettingsLib Tiles.
public class ExtraSettingsLoader {
    static final String META_DATA_PREFERENCE_IS_TOP_LEVEL = "injectedTopLevelPreference";
    private static final Logger LOG = new Logger(ExtraSettingsLoader.class);
    private static final String META_DATA_PREFERENCE_CATEGORY = "com.android.settings.category";
    private final Context mContext;
    private final Set<String> mTopLevelCategories;
    private final boolean mIsTopLevelSummariesEnabled;
    private Map<Preference, Bundle> mPreferenceBundleMap;
    private PackageManager mPm;

    public ExtraSettingsLoader(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
        mPreferenceBundleMap = new LinkedHashMap<>();
        mTopLevelCategories = Set.of(mContext.getResources().getStringArray(
                R.array.config_top_level_injection_categories));
        mIsTopLevelSummariesEnabled = mContext.getResources().getBoolean(
                R.bool.config_top_level_injection_enable_summaries);
    }

    @VisibleForTesting
    void setPackageManager(PackageManager pm) {
        mPm = pm;
    }

    /**
     * Returns a map of {@link Preference} and {@link Bundle} representing settings injected from
     * system apps and their metadata. The given intent must specify the action to use for
     * resolving activities and a category with the key "com.android.settings.category" and one of
     * the values in {@link com.android.settingslib.drawer.CategoryKey}.
     *
     * {@link com.android.settingslib.drawer.TileUtils#EXTRA_SETTINGS_ACTION} is automatically added
     * for backwards compatibility. Please make sure to use
     * {@link com.android.settingslib.drawer.TileUtils#IA_SETTINGS_ACTION} instead.
     *
     * @param intent intent specifying the extra settings category to load
     */
    public Map<Preference, Bundle> loadPreferences(Intent intent) {
        intent.setAction(TileUtils.IA_SETTINGS_ACTION);
        List<ResolveInfo> results = mPm.queryIntentActivitiesAsUser(intent,
                PackageManager.GET_META_DATA, ActivityManager.getCurrentUser());

        intent.setAction(TileUtils.EXTRA_SETTINGS_ACTION);
        List<ResolveInfo> extra_settings_results = mPm.queryIntentActivitiesAsUser(intent,
                PackageManager.GET_META_DATA, ActivityManager.getCurrentUser());
        for (ResolveInfo extra_settings_resolveInfo : extra_settings_results) {
            if (!results.contains(extra_settings_resolveInfo)) {
                results.add(extra_settings_resolveInfo);
            }
        }

        String extraCategory = intent.getStringExtra(META_DATA_PREFERENCE_CATEGORY);

        // Filter to only include valid results and then sort the results
        // Filter criteria: must be a system application and must have metaData
        // Sort criteria: sort results based on [order, package within order]
        results = results.stream()
                .filter(r -> r.system && r.activityInfo != null && r.activityInfo.metaData != null)
                .sorted((r1, r2) -> {
                    // First sort by order
                    int orderCompare = r2.activityInfo.metaData.getInt(META_DATA_KEY_ORDER)
                            - r1.activityInfo.metaData.getInt(META_DATA_KEY_ORDER);
                    if (orderCompare != 0) {
                        return orderCompare;
                    }

                    // Then sort by package name
                    String package1 = r1.activityInfo.packageName;
                    String package2 = r2.activityInfo.packageName;
                    return CASE_INSENSITIVE_ORDER.compare(package1, package2);
                })
                .collect(Collectors.toList());

        for (ResolveInfo resolved : results) {
            String key = null;
            String title = null;
            String summary = null;
            String category = null;
            ActivityInfo activityInfo = resolved.activityInfo;
            Bundle metaData = activityInfo.metaData;
            try {
                Resources res = mPm.getResourcesForApplication(activityInfo.packageName);
                if (metaData.containsKey(META_DATA_PREFERENCE_KEYHINT)) {
                    key = extractMetaDataString(metaData, META_DATA_PREFERENCE_KEYHINT, res);
                }
                if (!metaData.containsKey(META_DATA_PREFERENCE_TITLE_URI)) {
                    title = extractMetaDataString(metaData, META_DATA_PREFERENCE_TITLE, res);
                    if (TextUtils.isEmpty(title)) {
                        LOG.d("no title.");
                        title = activityInfo.loadLabel(mPm).toString();
                    }
                }
                if (!metaData.containsKey(META_DATA_PREFERENCE_SUMMARY_URI)) {
                    summary = extractMetaDataString(metaData, META_DATA_PREFERENCE_SUMMARY, res);
                    if (TextUtils.isEmpty(summary)) {
                        LOG.d("no description.");
                    }
                }
                category = extractMetaDataString(metaData, META_DATA_PREFERENCE_CATEGORY, res);
                if (TextUtils.isEmpty(category)) {
                    LOG.d("no category.");
                }
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                LOG.d("Couldn't find info", e);
            }
            Intent extraSettingIntent =
                    new Intent().setClassName(activityInfo.packageName, activityInfo.name);
            if (category == null) {
                // If category is not specified or not supported, default to device.
                category = CATEGORY_DEVICE;
            }
            boolean isTopLevel = mTopLevelCategories.contains(category);
            metaData.putBoolean(META_DATA_PREFERENCE_IS_TOP_LEVEL, isTopLevel);
            Drawable icon = ExtraSettingsUtil.createIcon(mContext, metaData,
                    activityInfo.packageName);

            if (!TextUtils.equals(extraCategory, category)) {
                continue;
            }
            CarUiPreference preference;
            if (isTopLevel) {
                preference = new TopLevelPreference(mContext);
                if (!mIsTopLevelSummariesEnabled) {
                    // remove summary data
                    summary = null;
                    metaData.remove(META_DATA_PREFERENCE_SUMMARY_URI);
                }
            } else {
                preference = new CarUiPreference(mContext);
            }
            preference.setTitle(title);
            preference.setSummary(summary);
            if (key != null) {
                preference.setKey(key);
            }
            if (icon != null) {
                preference.setIcon(icon);
            }
            preference.setIntent(extraSettingIntent);
            mPreferenceBundleMap.put(preference, metaData);
        }
        return mPreferenceBundleMap;
    }

    /**
     * Extracts the value in the metadata specified by the key.
     * If it is resource, resolve the string and return. Otherwise, return the string itself.
     */
    private String extractMetaDataString(Bundle metaData, String key, Resources res) {
        if (metaData.containsKey(key)) {
            if (metaData.get(key) instanceof Integer) {
                return res.getString(metaData.getInt(key));
            }
            return metaData.getString(key);
        }
        return null;
    }
}
