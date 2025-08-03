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

package com.android.tv.settings.customization;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.preference.Preference;

import com.android.tv.twopanelsettings.slices.SlicePreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the preference meta-data provided in the customization apk and builds
 * the listed preferences if any.
 */
final class PartnerResourcesParser {
    private static final String TAG = "PartnerResourcesParser";

    private final Context mContext;
    private final String mSettingsScreen;

    static final String PREFERENCE_GROUP_END_INDICATOR = "=";

    /**
     * Create an instance of the parser for the particular settings screen
     * @param context TvSettings application context
     * @param settingsScreen String identifier for the settings screen for which
     *                      the meta data has to be read
     */
    PartnerResourcesParser(Context context, String settingsScreen) {
        mContext = context;
        mSettingsScreen = settingsScreen;
    }

    public List<Preference> buildPreferences() {
        final Integer numberOfPreferences = Partner.getInstance(mContext)
                .getInteger(String.format("%s_num_prefs", mSettingsScreen));
        if (numberOfPreferences == null) {
            Log.i(TAG, "Number of new preferences not provided");
            return new ArrayList<>();
        }
        final List<Preference> preferences = new ArrayList<>(numberOfPreferences);
        for (int i = 1; i <= numberOfPreferences; i++) {
            final String name = String.format("%s_pref%d", mSettingsScreen, i);
            final String uri = Partner.getInstance(mContext).getString(
                    String.format("%s_uri", name));
            final String action = Partner.getInstance(mContext).getString(
                    String.format("%s_intent_action", name));
            if (uri == null && action == null) {
                Log.i(TAG, "Invalid preference, missing uri and action");
                continue;
            }
            if (uri != null) {
                preferences.add(buildSlicePreference(name, uri));
            } else {
                preferences.add(buildPreference(name, action));
            }
        }
        return preferences;
    }

    private SlicePreference buildSlicePreference(String name, String uri) {
        SlicePreference slicePreference = new SlicePreference(mContext);
        slicePreference.setUri(uri);
        final String contentDescription = Partner.getInstance(mContext).getString(
                String.format("%s_content_description", name));
        if (contentDescription != null) {
            slicePreference.setContentDescription(contentDescription);
        }
        parseGenericPreferenceAttributes(name, slicePreference);
        return slicePreference;
    }

    private void parseGenericPreferenceAttributes(String name, Preference preference) {
        preference.setKey(Partner.getInstance(mContext).getString(
                String.format("%s_key", name)));
        final Drawable icon = Partner.getInstance(mContext).getDrawable(
                String.format("%s_icon", name), mContext.getTheme());
        if (icon != null) {
            preference.setIcon(icon);
        }
        final String title = Partner.getInstance(mContext).getString(
                String.format("%s_title", name));
        if (title != null) {
            preference.setTitle(title);
        }
        final String summary = Partner.getInstance(mContext).getString(
                String.format("%s_summary", name));
        if (summary != null) {
            preference.setSummary(summary);
        }
    }

    private Preference buildPreference(String name, String action) {
        final Intent intent = new Intent();
        intent.setAction(action);
        final String targetPackage = Partner.getInstance(mContext).getString(
                String.format("%s_intent_target_package", name));
        if (targetPackage != null) {
            intent.setPackage(targetPackage);
            final String targetClass = Partner.getInstance(mContext).getString(
                    String.format("%s_intent_target_class", name));
            if (targetClass != null) {
                intent.setClassName(targetPackage, targetClass);
            }
        }
        final Preference preference = new Preference(mContext);
        preference.setIntent(intent);
        parseGenericPreferenceAttributes(name, preference);
        return preference;
    }

    String[] getOrderedPreferences() {
        final List<String> orderedPreferences = new ArrayList<>();
        iteratePreferences(orderedPreferences,
                String.format("%s_preferences", mSettingsScreen));
        return orderedPreferences.toArray(String[]::new);
    }

    private void iteratePreferences(List<String> orderedPreferences, String preferencesResource) {
        final String[] preferences = Partner.getInstance(mContext).getArray(preferencesResource);
        if (preferences == null) {
            Log.i(TAG, "Ordered preference list not found");
            return;
        }
        for (final String preference : preferences) {
            orderedPreferences.add(preference);
            // Check to see if it is a PreferenceGroup, in which case, recursively
            // iterate through the PreferenceGroup to list all its Preferences
            final String nestedPreferencesResource = String.format(
                    "%s_%s_preferences", mSettingsScreen, preference);
            final String[] nestedPreferences = Partner.getInstance(mContext)
                    .getArray(nestedPreferencesResource);
            if (nestedPreferences != null && nestedPreferences.length > 0) {
                iteratePreferences(orderedPreferences, nestedPreferencesResource);
            }
        }
        // This is necessary to know when a nested PreferenceGroup ends
        // so the preferences after this are correctly added to the
        // parent PreferenceGroup
        orderedPreferences.add(PREFERENCE_GROUP_END_INDICATOR);
    }
}
