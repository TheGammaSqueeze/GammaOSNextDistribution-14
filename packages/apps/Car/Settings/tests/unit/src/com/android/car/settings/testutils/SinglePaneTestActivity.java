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

package com.android.car.settings.testutils;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * {@link BaseCarSettingsTestActivity} instance that always single-pane (via manifest meta-data).
 */
public class SinglePaneTestActivity extends BaseCarSettingsTestActivity {

    @Nullable
    @Override
    protected Fragment getInitialFragment() {
        // Specifying an initial fragment for single pane testing since it can't be launched later
        // (because that will launch a new activity)
        return new BaseTestSettingsFragment();
    }
}
