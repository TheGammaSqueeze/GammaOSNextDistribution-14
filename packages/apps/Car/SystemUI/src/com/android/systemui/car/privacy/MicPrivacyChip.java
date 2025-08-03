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
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.R;

/** Car optimized Mic Privacy Chip View that is shown when microphone is being used. */
public class MicPrivacyChip extends PrivacyChip {

    private static final String SENSOR_NAME = "microphone";
    private static final String SENSOR_NAME_WITH_FIRST_LETTER_CAPITALIZED = "Microphone";

    public MicPrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public MicPrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public MicPrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
    }

    @Override
    protected @DrawableRes int getLightMutedIconResourceId() {
        return R.drawable.ic_mic_off_light;
    }

    @Override
    protected @DrawableRes int getDarkMutedIconResourceId() {
        return R.drawable.ic_mic_off_dark;
    }

    @Override
    protected @DrawableRes int getLightIconResourceId() {
        return R.drawable.ic_mic_light;
    }

    @Override
    protected @DrawableRes int getDarkIconResourceId() {
        return R.drawable.ic_mic_dark;
    }

    @Override
    protected String getSensorName() {
        return SENSOR_NAME;
    }

    @Override
    protected String getSensorNameWithFirstLetterCapitalized() {
        return SENSOR_NAME_WITH_FIRST_LETTER_CAPITALIZED;
    }
}
