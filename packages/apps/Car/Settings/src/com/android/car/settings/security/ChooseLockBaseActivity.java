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

package com.android.car.settings.security;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.common.BaseCarSettingsActivity;

/**
 * Abstract base activity class for lock choosing activities.
 */
public abstract class ChooseLockBaseActivity extends BaseCarSettingsActivity {

    /**
     * Lock type fragment specified here will have {@link PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK}
     * added as an argument and then opened as the initial fragment.
     */
    protected abstract Fragment getChooseLockFragment();

    @Nullable
    @Override
    protected Fragment getInitialFragment() {
        Intent intent = getIntent();
        Fragment fragment = getChooseLockFragment();
        Bundle args = fragment.getArguments();
        if (args == null) {
            args = new Bundle();
        }
        args.putParcelable(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK,
                intent.getParcelableExtra(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK));
        fragment.setArguments(args);
        return fragment;
    }
}
