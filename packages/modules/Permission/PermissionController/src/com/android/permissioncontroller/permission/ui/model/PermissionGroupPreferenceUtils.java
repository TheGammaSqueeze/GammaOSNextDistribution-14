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

package com.android.permissioncontroller.permission.ui.model;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utils related to displaying permission groups in preferences.
 */
public final class PermissionGroupPreferenceUtils {

    /**
     * Update a {@link PreferenceGroup} with the specified permission groups.
     */
    public static void updateGroupOfPermissionPreferences(Context context,
            PreferenceGroup preferenceGroup,
            List<PermGroupPackagesUiInfo> permissionGroups) {
        if (!(permissionGroups instanceof ArrayList)) {
            permissionGroups = new ArrayList<>(permissionGroups);
        }
        if (!hasPermissionsListChanged(preferenceGroup, permissionGroups)) {
            // The list of the permission groups has NOT changed. Only need to update summaries.
            updatePreferenceSummaries(preferenceGroup, permissionGroups);
            return;
        }

        // The list of the permission groups has changed.
        // 1. Iterate through the list of the permission groups, for each one check if there is
        //    already exists a PermissionGroupPreference in the PreferenceGroup. If so - hold on to
        //    it. If not - create a new one.
        // 2. Clean the PreferenceGroup of all the Preferences.
        // 3. Add PermissionGroupPreference, we cached or created on the step 1.
        final List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < permissionGroups.size(); i++) {
            final PermGroupPackagesUiInfo info = permissionGroups.get(i);
            PermissionGroupPreference preference = preferenceGroup.findPreference(info.getName());
            if (preference == null) {
                preference = new PermissionGroupPreference(context, info);
            } else {
                preference.updateSummary(info);
                // Reset the ordering back to default, so that when we add it back it falls into the
                // right place, and the preferences are ordered as we add them.
                preference.setOrder(Preference.DEFAULT_ORDER);
            }
            preferences.add(preference);
        }
        preferenceGroup.removeAll();
        for (Preference preference : preferences) {
            preferenceGroup.addPreference(preference);
        }
    }

    private static boolean hasPermissionsListChanged(PreferenceGroup preferenceGroup,
            List<PermGroupPackagesUiInfo> permissionGroups) {
        if (preferenceGroup.getPreferenceCount() != permissionGroups.size()) {
            return true;
        }

        for (int i = 0, size = permissionGroups.size(); i < size; i++) {
            final String key = permissionGroups.get(i).getName();
            // The key of the Preference that is currently at the i-th position in the
            // PreferenceGroup.
            final String existingKey = preferenceGroup.getPreference(i).getKey();
            if (!Objects.equals(key, existingKey)) {
                return true;
            }
        }

        return false;
    }

    private static void updatePreferenceSummaries(PreferenceGroup preferenceGroup,
            List<PermGroupPackagesUiInfo> permissionGroups) {
        for (int i = 0, size = permissionGroups.size(); i < size; i++) {
            final PermissionGroupPreference preference =
                    (PermissionGroupPreference) preferenceGroup.getPreference(i);
            final PermGroupPackagesUiInfo info = permissionGroups.get(i);
            preference.updateSummary(info);
        }
    }

    private PermissionGroupPreferenceUtils() {
    }
}
