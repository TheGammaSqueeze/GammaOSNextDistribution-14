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

package com.android.tv.settings.deviceadmin;

import android.content.Context;

import androidx.annotation.Keep;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for managing device admin apps.
 * Forked from:
 * Settings/src/com/android/settings/applications/specialaccess/deviceadmin/DeviceAdminSettings.java
 */
@Keep
public class DeviceAdminSettings extends PreferenceControllerFragment {
    static final String TAG = "DeviceAdminSettings";

    public static DeviceAdminSettings newInstance() {
        return new DeviceAdminSettings();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.device_admin_settings;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new DeviceAdminListPreferenceController(getContext()));
        return controllers;
    }

    @Override
    protected int getPageId() {
        //TODO: create proper TvSettingsEnums pageId
        return super.getPageId();
    }
}
