/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.about;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;

import androidx.annotation.Keep;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.tv.settings.NopePreferenceController;
import com.android.tv.settings.PreferenceControllerFragment;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for showing device hardware info, such as MAC addresses and serial numbers
 */
@Keep
public class StatusFragment extends PreferenceControllerFragment {

    private static final String KEY_BATTERY_STATUS = "battery_status";
    private static final String KEY_BATTERY_LEVEL = "battery_level";

    public static StatusFragment newInstance() {
        return new StatusFragment();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.device_info_status;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>(10);
        final Lifecycle lifecycle = getSettingsLifecycle();

        // TODO: detect if we have a battery or not
        controllers.add(new NopePreferenceController(context, KEY_BATTERY_LEVEL));
        controllers.add(new BatteryStatusPreferenceController(context, lifecycle));

        controllers.add(new SerialNumberPreferenceController(context));
        controllers.add(new UptimePreferenceController(context, lifecycle));
        controllers.add(new BluetoothAddressPreferenceController(context, lifecycle));
        controllers.add(new IpAddressPreferenceController(context, lifecycle));
        controllers.add(new MacAddressPreferenceController(context, lifecycle));
        controllers.add(new ImsStatusPreferenceController(context, lifecycle));

        return controllers;
    }

    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_ABOUT_STATUS;
    }
}
