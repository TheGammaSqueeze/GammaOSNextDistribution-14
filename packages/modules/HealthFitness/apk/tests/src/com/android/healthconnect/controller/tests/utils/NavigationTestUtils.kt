package com.android.healthconnect.controller.tests.utils

import android.content.Context
import com.android.healthconnect.controller.shared.Constants.ONBOARDING_SHOWN_PREF_KEY
import com.android.healthconnect.controller.shared.Constants.USER_ACTIVITY_TRACKER

fun showOnboarding(context: Context, show: Boolean) {
    val sharedPreference = context.getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
    val editor = sharedPreference.edit()
    editor.putBoolean(ONBOARDING_SHOWN_PREF_KEY, !show)
    editor.apply()
}
