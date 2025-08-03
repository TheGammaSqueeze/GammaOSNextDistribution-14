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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * This is responsible for building the PreferenceScreen according to the
 * Partner provided ordered preference list.
 */
public final class PartnerPreferencesMerger {
    private static final String TAG = "PartnerPreferencesMerger";

    public static void mergePreferences(
            Context context, PreferenceScreen preferenceScreen, String settingsScreen) {
        /*
        High level algorithm of adding new preferences in the desired order
        1. Build partner provided new preferences if any.

        2. Add the preferences in 1. to the existing TvSettings PreferenceScreen.

        3. Recursively expand and parse the partner provided ordered string array
        of preference keys. Each preference key can either be that of a PreferenceGroup
        or a base preference. For every PreferenceGroup there is an array listing the
        preferences it contains.

        4. Recursively clone every preference in current TvSettings PreferenceScreen.
        The preference can either be a preference or a PreferenceGroup. Remove all the
        preferences in each PreferenceGroup.

        5. Iterate through the ordered array in step 3, recursively adding preferences
        in all PreferenceGroups.
         */
        final PartnerResourcesParser partnerResourcesParser = new PartnerResourcesParser(
                context, settingsScreen);
        for (final Preference newPartnerPreference : partnerResourcesParser.buildPreferences()) {
            preferenceScreen.addPreference(newPartnerPreference);
        }

        final String[] orderedPreferenceKeys = partnerResourcesParser.getOrderedPreferences();

        // Clone the existing tv settings PreferenceScreen. All the preferences
        // will be removed from this screen to avoid multiple re-orderings as
        // the ordered preferences are being built
        final Preference[] combinedSettingsPreferences = clonePreferenceScreen(preferenceScreen);
        preferenceScreen.removeAll();

        addPreferences(
                Arrays.stream(orderedPreferenceKeys).iterator(),
                preferenceScreen,
                combinedSettingsPreferences
        );

        // PreferenceScreen preferences are re-ordered whenever the notifyHierarchyChanged()
        // method is invoked. It is package private and thus indirectly triggered by removing
        // a preference that does not exist. Adding / removing a new preference always invokes
        // notifyHierarchyChanged()
        final Preference triggerReorderPreference = new Preference(preferenceScreen.getContext());
        preferenceScreen.removePreference(triggerReorderPreference);
    }

    /**
     * Recursively iterates through all the preferences in PreferenceScreen and all
     * PreferenceGroups in it doing a clone by reference.
     * @param preferenceScreen current Tv Settings screen shown to the user
     * @return Array of all preferences in present in the preferenceScreen
     */
    private static Preference[] clonePreferenceScreen(PreferenceScreen preferenceScreen) {
        return clonePreferencesInPreferenceGroup(preferenceScreen)
                .toArray(Preference[]::new);
    }

    private static List<Preference> clonePreferencesInPreferenceGroup(
            PreferenceGroup preferenceGroup) {
        final List<Preference> preferences = new ArrayList<>();
        for (int index = 0; index < preferenceGroup.getPreferenceCount(); index++) {
            final Preference preference = preferenceGroup.getPreference(index);
            if (preference instanceof PreferenceGroup) {
                final List<Preference> nestedPreferences =
                        clonePreferencesInPreferenceGroup((PreferenceGroup) preference);
                // Remove all preferences in the PreferenceGroup since the logic
                // to sort the preferences involves iterating through each preference
                // key. Having these preferences in a PreferenceGroup will result
                // in these nested preferences being added twice in the final list
                // of ordered preferences.
                ((PreferenceGroup) preference).removeAll();
                preferences.add(preference);
                preferences.addAll(nestedPreferences);
            } else {
                preferences.add(preference);
            }
        }
        return preferences;
    }

    private static void addPreferences(
            Iterator<String> partnerPreferenceKeyIterator,
            PreferenceGroup preferenceGroup,
            Preference[] tvSettingsPreferences) {
        int order = 0;
        while (partnerPreferenceKeyIterator.hasNext()) {
            final String preferenceKey = partnerPreferenceKeyIterator.next();
            if (preferenceKey.equals(PartnerResourcesParser.PREFERENCE_GROUP_END_INDICATOR)) {
                break;
            }

            final Preference preference = findPreference(preferenceKey, tvSettingsPreferences);
            if (preference == null) {
                Log.i(TAG, "Partner provided preference key: "
                        + preferenceKey + " is not defined anywhere");
                continue;
            }
            if (preference instanceof PreferenceGroup) {
                addPreferences(partnerPreferenceKeyIterator,
                        (PreferenceGroup) preference, tvSettingsPreferences);
            }
            preference.setOrder(++order);
            preferenceGroup.addPreference(preference);
        }
    }

    @Nullable
    private static Preference findPreference(String key, Preference[] preferences) {
        for (final Preference preference : preferences) {
            if (preference.getKey().equals(key)) {
                return preference;
            }
        }
        return null;
    }
}
