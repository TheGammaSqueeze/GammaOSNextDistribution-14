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
import android.content.Intent
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterManager
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants
import java.util.Objects

/**
 * A preference that displays a visual representation of a {@link SafetyCenterEntryGroup} on the
 * Safety Center homepage.
 */
@RequiresApi(UPSIDE_DOWN_CAKE)
internal class SafetyHomepageEntryPreference(
    context: Context,
    private val entryGroup: SafetyCenterEntryGroup,
    sessionId: Long
) : Preference(context), ComparablePreference {

    init {
        setTitle(entryGroup.title)
        setSummary(entryGroup.summary)
        setIcon(
            SeverityIconPicker.selectIconResId(
                entryGroup.id,
                entryGroup.severityLevel,
                entryGroup.severityUnspecifiedIconType
            )
        )

        val intent = Intent(Intent.ACTION_SAFETY_CENTER)
        intent.putExtra(SafetyCenterManager.EXTRA_SAFETY_SOURCES_GROUP_ID, entryGroup.id)
        intent.putExtra(SafetyCenterConstants.EXTRA_OPENED_FROM_HOMEPAGE, true)
        intent.putExtra(EXTRA_SESSION_ID, sessionId)
        NavigationSource.SAFETY_CENTER.addToIntent(intent)
        setIntent(intent)
        setKey(entryGroup.id)
    }

    override fun isSameItem(preference: Preference): Boolean =
        preference is SafetyHomepageEntryPreference && entryGroup.id == preference.entryGroup.id

    override fun hasSameContents(preference: Preference): Boolean =
        preference is SafetyHomepageEntryPreference &&
            Objects.equals(entryGroup.id, preference.entryGroup.id) &&
            TextUtils.equals(entryGroup.title, preference.entryGroup.title) &&
            TextUtils.equals(entryGroup.summary, preference.entryGroup.summary) &&
            entryGroup.severityLevel == preference.entryGroup.severityLevel &&
            entryGroup.severityUnspecifiedIconType ==
                preference.entryGroup.severityUnspecifiedIconType
}
