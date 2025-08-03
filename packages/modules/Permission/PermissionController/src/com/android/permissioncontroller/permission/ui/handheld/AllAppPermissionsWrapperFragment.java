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

package com.android.permissioncontroller.permission.ui.handheld;

import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Wrapper over AllAppPermissionsFragment
 */
public class AllAppPermissionsWrapperFragment extends PermissionsCollapsingToolbarBaseFragment {

    @NonNull
    @Override
    public PreferenceFragmentCompat createPreferenceFragment() {
        return new AllAppPermissionsFragment();
    }

    /**
     * Returns a new instance of AllAppPermissionsPreferenceFragment with arguments
     */
    public static AllAppPermissionsWrapperFragment newInstance(@NonNull String packageName,
            @Nullable String filterGroup, @NonNull UserHandle userHandle) {
        AllAppPermissionsWrapperFragment instance = new AllAppPermissionsWrapperFragment();
        instance.setArguments(AllAppPermissionsFragment.createArgs(
                packageName, filterGroup, userHandle));
        return instance;
    }
}
