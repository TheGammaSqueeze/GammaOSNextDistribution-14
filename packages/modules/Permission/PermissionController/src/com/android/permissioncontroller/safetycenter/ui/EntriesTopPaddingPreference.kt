package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.android.permissioncontroller.R

class EntriesTopPaddingPreference(context: Context, attrs: AttributeSet) :
    Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_entries_top_padding
    }
}
