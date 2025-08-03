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
import android.graphics.drawable.Drawable
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.util.AttributeSet
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R

/** A preference that displays the illustration on a Safety Center subpage. */
@RequiresApi(UPSIDE_DOWN_CAKE)
internal class SafetyIllustrationPreference(context: Context, attrs: AttributeSet) :
    Preference(context, attrs) {

    init {
        layoutResource = R.layout.preference_illustration
    }

    var illustrationDrawable: Drawable? = null
        set(drawable: Drawable?) {
            if (drawable !== field) {
                field = drawable
                notifyChanged()
            }
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val illustrationView = holder.findViewById(R.id.illustration_view) as ImageView
        illustrationView.setImageDrawable(illustrationDrawable)
    }
}
