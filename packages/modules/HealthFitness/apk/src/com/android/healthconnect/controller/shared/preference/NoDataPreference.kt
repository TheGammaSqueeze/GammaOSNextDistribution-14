package com.android.healthconnect.controller.shared.preference

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R

/** Custom preference for displaying no search result. */
class NoDataPreference
constructor(
    context: Context,
) : Preference(context) {

    init {
        layoutResource = R.layout.widget_no_data
        key = "no_data_preference"
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
    }
}
