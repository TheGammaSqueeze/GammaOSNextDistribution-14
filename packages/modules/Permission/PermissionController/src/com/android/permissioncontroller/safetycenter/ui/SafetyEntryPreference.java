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

package com.android.permissioncontroller.safetycenter.ui;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.content.Context;
import android.safetycenter.SafetyCenterEntry;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.permissioncontroller.safetycenter.ui.view.SafetyEntryView;

/** A preference that displays a visual representation of a {@link SafetyCenterEntry}. */
@RequiresApi(TIRAMISU)
public final class SafetyEntryPreference extends Preference implements ComparablePreference {

    private final PositionInCardList mPosition;
    private final SafetyCenterEntry mEntry;
    private final SafetyCenterViewModel mViewModel;
    @Nullable private final Integer mLaunchTaskId;

    public SafetyEntryPreference(
            Context context,
            @Nullable Integer launchTaskId,
            SafetyCenterEntry entry,
            PositionInCardList position,
            SafetyCenterViewModel viewModel) {
        super(context);

        mEntry = entry;
        mPosition = position;
        mViewModel = viewModel;
        mLaunchTaskId = launchTaskId;

        setLayoutResource(R.layout.preference_entry);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ((SafetyEntryView) holder.itemView).showEntry(mEntry, mPosition, mLaunchTaskId, mViewModel);
    }

    @Override
    public boolean isSameItem(Preference other) {
        return other instanceof SafetyEntryPreference
                && TextUtils.equals(mEntry.getId(), ((SafetyEntryPreference) other).mEntry.getId());
    }

    @Override
    public boolean hasSameContents(Preference other) {
        if (other instanceof SafetyEntryPreference) {
            SafetyEntryPreference o = (SafetyEntryPreference) other;
            return mEntry.equals(o.mEntry) && mPosition == o.mPosition;
        }
        return false;
    }
}
