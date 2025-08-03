/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.accounts;

import static com.android.tv.settings.overlay.FlavorUtils.X_EXPERIENCE_FLAVORS_MASK;

import android.text.TextUtils;

import com.android.tv.settings.R;
import com.android.tv.settings.slice.SliceActivity;

/** Activity for showing users services settings. */
public class ManageUserServicesSliceActivity extends SliceActivity {
    private static final String KEY_USER_ID = "userId";

    @Override
    public String getSliceUri() {
        String id = getIntent().getStringExtra(KEY_USER_ID);
        return TextUtils.isEmpty(id)
                ? null
                : getString(R.string.manage_user_services_slice_uri, id);
    }

    @Override
    public String getScreenTitle() {
        return getString(R.string.manage_user_services_slice_title);
    }

    @Override
    protected int getAvailableFlavors() {
        return X_EXPERIENCE_FLAVORS_MASK;
    }
}
