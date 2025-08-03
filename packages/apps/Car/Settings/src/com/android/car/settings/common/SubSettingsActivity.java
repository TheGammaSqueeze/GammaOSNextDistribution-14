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

package com.android.car.settings.common;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Activity class for launching a SubSetting as a separate activity.
 */
public class SubSettingsActivity extends BaseCarSettingsActivity {

    private static final String KEY_SUB_SETTINGS_FRAGMENT = "key_sub_settings_fragment";
    private static final String KEY_SUB_SETTINGS_FRAGMENT_ARGS = "key_sub_settings_fragment_args";
    private static final Logger LOG = new Logger(SubSettingsActivity.class);

    /**
     * Create a new SubSettingsActivity instance for the given fragment.
     */
    public static Intent newInstance(@NonNull Context context, @NonNull Fragment fragment) {
        String fragmentClass = fragment.getClass().getName();
        Intent intent = new Intent(context, SubSettingsActivity.class);
        intent.putExtra(KEY_SUB_SETTINGS_FRAGMENT, fragmentClass);
        intent.putExtra(KEY_SUB_SETTINGS_FRAGMENT_ARGS, fragment.getArguments());
        return intent;
    }

    @Nullable
    @Override
    protected Fragment getInitialFragment() {
        try {
            String fragmentClass = getIntent().getStringExtra(KEY_SUB_SETTINGS_FRAGMENT);
            Bundle fragmentArgs = getIntent().getBundleExtra(KEY_SUB_SETTINGS_FRAGMENT_ARGS);
            Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                    getClassLoader(), fragmentClass);
            fragment.setArguments(fragmentArgs);
            return fragment;
        } catch (Fragment.InstantiationException e) {
            LOG.e("Unable to instantiate fragment", e);
            return null;
        }
    }
}
