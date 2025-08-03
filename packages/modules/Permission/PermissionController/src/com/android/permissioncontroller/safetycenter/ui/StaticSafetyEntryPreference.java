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

import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PERSONAL_PROFILE_SUFFIX;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.WORK_PROFILE_SUFFIX;

import android.content.Context;
import android.os.UserManager;
import android.safetycenter.SafetyCenterStaticEntry;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;

import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.safetycenter.internaldata.SafetyCenterEntryId;

/** A preference which displays a visual representation of a {@link SafetyCenterStaticEntry}. */
@RequiresApi(TIRAMISU)
public class StaticSafetyEntryPreference extends Preference implements ComparablePreference {

    private static final String TAG = StaticSafetyEntryPreference.class.getSimpleName();

    private final SafetyCenterStaticEntry mEntry;
    private final SafetyCenterViewModel mViewModel;

    public StaticSafetyEntryPreference(
            Context context,
            @Nullable Integer launchTaskId,
            SafetyCenterStaticEntry entry,
            @Nullable SafetyCenterEntryId entryId,
            SafetyCenterViewModel viewModel) {
        super(context);
        mEntry = entry;
        mViewModel = viewModel;
        setTitle(entry.getTitle());
        setSummary(entry.getSummary());
        if (entry.getPendingIntent() != null) {
            setOnPreferenceClickListener(
                    unused -> {
                        try {
                            PendingIntentSender.send(mEntry.getPendingIntent(), launchTaskId);
                        } catch (Exception ex) {
                            Log.e(
                                    TAG,
                                    String.format(
                                            "Failed to execute pending intent for static entry: %s",
                                            mEntry),
                                    ex);
                        }

                        // SafetyCenterStaticEntry does not expose an ID, so we're unable to log
                        // what source this static entry belonged to.
                        mViewModel.getInteractionLogger().record(Action.STATIC_ENTRY_CLICKED);

                        return true;
                    });
        }
        if (entryId != null) {
            setupPreferenceKey(entryId);
        }
    }

    private void setupPreferenceKey(SafetyCenterEntryId entryId) {
        boolean isWorkProfile =
                getContext()
                        .getSystemService(UserManager.class)
                        .isManagedProfile(entryId.getUserId());
        if (isWorkProfile) {
            setKey(String.format("%s_%s", entryId.getSafetySourceId(), WORK_PROFILE_SUFFIX));
        } else {
            setKey(String.format("%s_%s", entryId.getSafetySourceId(), PERSONAL_PROFILE_SUFFIX));
        }
    }

    @Override
    public boolean isSameItem(Preference preference) {
        return preference instanceof StaticSafetyEntryPreference
                && TextUtils.equals(
                        mEntry.getTitle(),
                        ((StaticSafetyEntryPreference) preference).mEntry.getTitle());
    }

    @Override
    public boolean hasSameContents(Preference preference) {
        return preference instanceof StaticSafetyEntryPreference
                && mEntry.equals(((StaticSafetyEntryPreference) preference).mEntry);
    }
}
