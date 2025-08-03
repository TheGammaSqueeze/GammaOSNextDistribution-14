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

package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.permissioncontroller.R

/** A {@link PreferenceCategory} that implements {@link ComparablePreference} interface. */
internal class ComparablePreferenceCategory
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int =
        getAttr(
            context,
            androidx.preference.R.attr.preferenceCategoryStyle,
            android.R.attr.preferenceCategoryStyle
        ),
    defStyleRes: Int = 0
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes), ComparablePreference {

    private companion object {
        /* PreferenceCategory falls back to using TypedArrayUtils.getAttr()
         * when no defStyleAttr is set. This method reuses the logic
         * from library-private TypedArrayUtils. */
        private fun getAttr(context: Context, attr: Int, fallbackAttr: Int): Int {
            val value = TypedValue()
            context.theme.resolveAttribute(attr, value, true)
            return if (value.resourceId != 0) attr else fallbackAttr
        }
    }

    override fun isSameItem(preference: Preference): Boolean =
        preference is ComparablePreferenceCategory && TextUtils.equals(key, preference.key)

    override fun hasSameContents(preference: Preference): Boolean =
        preference is ComparablePreferenceCategory && TextUtils.equals(title, preference.title)
}
