/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants

/** A preference that displays the Security and Privacy brand name on a Safety Center subpage. */
@RequiresApi(UPSIDE_DOWN_CAKE)
internal class SafetyBrandChipPreference(context: Context, attrs: AttributeSet) :
    Preference(context, attrs) {

    init {
        setLayoutResource(R.layout.preference_brand_chip)
    }

    private var brandChipClickListener: View.OnClickListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val brandChipButton = holder.findViewById(R.id.brand_chip)!!
        brandChipButton.setOnClickListener(brandChipClickListener)
    }

    /**
     * Sets the listener that handles clicks for the brand chip
     *
     * @param activity represents the parent activity of the fragment
     * @param sessionId identifier for the current session
     */
    fun setupListener(activity: FragmentActivity, sessionId: Long) {
        brandChipClickListener = View.OnClickListener { closeSubpage(activity, context, sessionId) }
    }

    companion object {
        /**
         * Closes the subpage and optionally opens up the homepage
         *
         * @param fragmentActivity represents the parent activity of the fragment
         * @param fragmentContext represents the context associated with the fragment
         * @param sessionId identifier for the current session
         */
        fun closeSubpage(
            fragmentActivity: FragmentActivity,
            fragmentContext: Context,
            sessionId: Long
        ) {
            val openedFromHomepage =
                fragmentActivity
                    .getIntent()
                    .getBooleanExtra(SafetyCenterConstants.EXTRA_OPENED_FROM_HOMEPAGE, false)
            if (!openedFromHomepage) {
                val intent = Intent(Intent.ACTION_SAFETY_CENTER)
                intent.putExtra(EXTRA_SESSION_ID, sessionId)
                NavigationSource.SAFETY_CENTER.addToIntent(intent)
                fragmentContext.startActivity(intent)
            }
            fragmentActivity.finish()
        }
    }
}
