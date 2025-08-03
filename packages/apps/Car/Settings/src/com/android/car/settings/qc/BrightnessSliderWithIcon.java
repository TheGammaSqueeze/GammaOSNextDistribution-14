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

package com.android.car.settings.qc;

import static com.android.car.settings.qc.SettingsQCRegistry.BRIGHTNESS_SLIDER_WITH_ICON_URI;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.net.Uri;

import com.android.car.qc.QCRow;
import com.android.car.settings.R;

/**
 *  {@link BrightnessSlider} with an additional start icon.
 */
public class BrightnessSliderWithIcon extends BrightnessSlider {
    public BrightnessSliderWithIcon(Context context) {
        super(context);
    }

    @Override
    Uri getUri() {
        return BRIGHTNESS_SLIDER_WITH_ICON_URI;
    }

    @Override
    protected QCRow.Builder getBrightnessRowBuilder() {
        QCRow.Builder builder = super.getBrightnessRowBuilder();
        builder.setIcon(Icon.createWithResource(getContext(), R.drawable.ic_qc_brightness));
        return builder;
    }
}
