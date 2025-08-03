package com.android.healthconnect.controller.permissions.connectedapps.searchapps

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R

/** Custom preference for displaying no search result. */
class NoSearchResultPreference
constructor(
    context: Context,
) : Preference(context) {

    init {
        layoutResource = R.layout.widget_no_search_result
        key = "no_search_result_preference"
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
    }
}
