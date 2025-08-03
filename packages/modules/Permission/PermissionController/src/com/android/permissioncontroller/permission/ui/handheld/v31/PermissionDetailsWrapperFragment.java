/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity;
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment;

/**
 * Wrapper over PermissionDetailsFragment
 */
@RequiresApi(Build.VERSION_CODES.S)
public class PermissionDetailsWrapperFragment extends PermissionsCollapsingToolbarBaseFragment {
    @NonNull
    @Override
    public PreferenceFragmentCompat createPreferenceFragment() {
        return new PermissionUsageDetailsFragment();
    }

    /**
     * Construct a new instance of PermissionDetailsFragment
     */
    public static @NonNull PermissionDetailsWrapperFragment newInstance(@Nullable String groupName,
            long numMillis, boolean showSystem, long sessionId, boolean show7Days) {
        PermissionDetailsWrapperFragment fragment = new PermissionDetailsWrapperFragment();
        Bundle arguments = new Bundle();
        if (groupName != null) {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putLong(Intent.EXTRA_DURATION_MILLIS, numMillis);
        arguments.putBoolean(ManagePermissionsActivity.EXTRA_SHOW_SYSTEM, showSystem);
        arguments.putBoolean(ManagePermissionsActivity.EXTRA_SHOW_7_DAYS, show7Days);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
    }
}
