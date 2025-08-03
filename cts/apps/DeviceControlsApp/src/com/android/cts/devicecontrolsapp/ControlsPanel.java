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

package com.android.cts.devicecontrolsapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.service.controls.ControlsProviderService;
import android.text.Html;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class ControlsPanel extends Activity {

    private static final int DEFAULT_CONTROLS_SURFACE_VALUE = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_controls_app);
        TextView lockscreen_setting_placeholder = findViewById(R.id.lockscreen_setting_placeholder);
        TextView surface_placeholder = findViewById(R.id.surface_placeholder);

        boolean settingAllowTrivialControls = getIntent().getBooleanExtra(
                ControlsProviderService.EXTRA_LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS, false);

        int settingDreamExtra = getIntent().getIntExtra(
                ControlsProviderService.EXTRA_CONTROLS_SURFACE,
                DEFAULT_CONTROLS_SURFACE_VALUE
        );

        // Set values to the TextViews
        lockscreen_setting_placeholder.setText(Html.fromHtml(
                getString(R.string.panel_content, settingAllowTrivialControls),
                Html.FROM_HTML_MODE_LEGACY));
        surface_placeholder.setText(Html.fromHtml(
                getString(R.string.home_control_panel_surface,
                        readableSetting(this, settingDreamExtra)),
                Html.FROM_HTML_MODE_LEGACY));
    }

    private static String readableSetting(Context context, int settingDreamExtra) {
        switch (settingDreamExtra) {
            case ControlsProviderService.CONTROLS_SURFACE_ACTIVITY_PANEL:
                return context.getString(R.string.not_hosted_in_dream_result);
            case ControlsProviderService.CONTROLS_SURFACE_DREAM:
                return context.getString(R.string.hosted_in_dream_result);
            default:
                return context.getString(R.string.unknown_result);
        }
    }
}