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

package com.android.permissioncontroller.permission.ui;

import com.android.permissioncontroller.DeviceUtils;
import com.android.settingslib.collapsingtoolbar.SettingsTransitionActivity;

/**
 * Parent activity that supports transitions
 */
public class SettingsActivity extends SettingsTransitionActivity {
    @Override
    protected boolean isSettingsTransitionEnabled() {
        return super.isSettingsTransitionEnabled() && !(DeviceUtils.isAuto(this)
                || DeviceUtils.isTelevision(this) || DeviceUtils.isWear(this));
    }

}
