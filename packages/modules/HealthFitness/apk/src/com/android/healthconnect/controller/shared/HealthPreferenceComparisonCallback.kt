package com.android.healthconnect.controller.shared

import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.android.healthconnect.controller.permissions.connectedapps.ComparablePreference

/** A {@link PreferenceComparisonCallback} to identify changed preferences. */
class HealthPreferenceComparisonCallback : PreferenceManager.PreferenceComparisonCallback() {
    override fun arePreferenceItemsTheSame(
        oldPreference: Preference,
        newPreference: Preference
    ): Boolean {
        return if (oldPreference is ComparablePreference) {
            (oldPreference as ComparablePreference).isSameItem(newPreference)
        } else comparePreference(oldPreference, newPreference) == 0
    }

    override fun arePreferenceContentsTheSame(
        oldPreference: Preference,
        newPreference: Preference
    ): Boolean {
        return if (oldPreference is ComparablePreference) {
            (oldPreference as ComparablePreference).hasSameContents(newPreference)
        } else comparePreference(oldPreference, newPreference) == 0
    }

    private fun comparePreference(oldPreference: Preference, newPreference: Preference): Int {
        return if (oldPreference.title == newPreference.title) {
            0
        } else if (oldPreference.title == null) {
            1
        } else {
            -1
        }
    }
}
