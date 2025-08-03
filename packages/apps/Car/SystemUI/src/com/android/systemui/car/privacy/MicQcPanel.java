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
package com.android.systemui.car.privacy;

import android.content.Context;

import androidx.annotation.DrawableRes;

import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.systemui.R;
import com.android.systemui.car.systembar.MicPrivacyChipViewController;

import javax.inject.Inject;

/**
 * A {@link BaseLocalQCProvider} that builds the microphone privacy panel.
 */
public class MicQcPanel extends SensorQcPanel {

    private static final String SENSOR_NAME = "microphone";
    private static final String SENSOR_SHORT_NAME = "mic";
    private static final String SENSOR_NAME_WITH_FIRST_LETTER_CAPITALIZED = "Microphone";

    @Inject
    public MicQcPanel(Context context,
            MicPrivacyChipViewController micPrivacyChipViewController,
            MicPrivacyElementsProviderImpl micPrivacyElementsProvider) {
        super(context, micPrivacyChipViewController, micPrivacyElementsProvider);
    }

    @Override
    protected String getSensorShortName() {
        return SENSOR_SHORT_NAME;
    }

    @Override
    protected String getSensorName() {
        return SENSOR_NAME;
    }

    @Override
    protected String getSensorNameWithFirstLetterCapitalized() {
        return SENSOR_NAME_WITH_FIRST_LETTER_CAPITALIZED;
    }

    @Override
    protected @DrawableRes int getSensorOnIconResourceId() {
        return R.drawable.ic_mic_light;
    }

    @Override
    protected @DrawableRes int getSensorOffIconResourceId() {
        return R.drawable.ic_mic_off_light;
    }
}
