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

package com.android.systemui.car.hvac.referenceui;

import android.annotation.ColorInt;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.car.hvac.TemperatureControlView;

/**
 * {@link TemperatureControlView} with an added feature of the temperature control bar background
 * adjusting based on the temperature set.
 */
public class BackgroundAdjustingTemperatureControlView extends TemperatureControlView {

    private final static String TAG = "BgAdjTemperatureCtlView";

    private View mTemperatureBarView;
    private int[] mUpperLimits;
    private @ColorInt int[] mTempColors;
    private int mOffColor;

    public BackgroundAdjustingTemperatureControlView(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mTemperatureBarView = findViewById(R.id.hvac_temperature_bar);

        Resources res = getResources();
        mUpperLimits = res.getIntArray(R.array.hvac_temperature_control_levels);

        TypedArray colorRes = res.obtainTypedArray(R.array.hvac_temperature_level_backgrounds);
        mTempColors = new int[colorRes.length()];
        for (int i = 0; i < colorRes.length(); i++) {
            mTempColors[i] = colorRes.getColor(i,
                    res.getColor(R.color.hvac_temperature_default_bg_color,
                            getContext().getTheme()));
        }
        colorRes.recycle();
        mOffColor = res.getColor(R.color.hvac_temperature_off_text_bg_color, /* theme= */ null);
    }

    @Override
    protected void updateTemperatureViewUiThread() {
        mTempTextView.setText(getTempInDisplay());
        ((GradientDrawable) mTemperatureBarView.getBackground()).setColor(
                isTemperatureAvailableForChange()
                        ? getTemperatureColor(getCurrentTempC())
                        : mOffColor);
        boolean canChangeTemperature = isTemperatureAvailableForChange();
        mIncreaseButton.setVisibility(canChangeTemperature ? View.VISIBLE : View.INVISIBLE);
        mDecreaseButton.setVisibility(canChangeTemperature ? View.VISIBLE : View.INVISIBLE);
    }

    @VisibleForTesting
    int[] getUpperLimits() {
        return mUpperLimits;
    }

    @VisibleForTesting
    @ColorInt
    int[] getTempColors() {
        return mTempColors;
    }

    @VisibleForTesting
    @ColorInt
    int getTemperatureColor(float temperatureC) {
        for (int i = 0; i < mUpperLimits.length; i++) {
            float upperLimit = mUpperLimits[i] / 10f;
            int tempColor = mTempColors[i];
            if (temperatureC <= upperLimit) {
                return tempColor;
            }
        }
        Log.w(TAG, "getTemperatureColor: Temperature set is not within the range defined. "
                + "Returning the last color defined instead.");
        return mTempColors[mTempColors.length - 1];
    }
}
