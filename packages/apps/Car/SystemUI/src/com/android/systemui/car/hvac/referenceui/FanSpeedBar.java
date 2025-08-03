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

import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_FAN_SPEED;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.car.hvac.HvacUtils;
import com.android.systemui.car.hvac.HvacView;

import java.util.ArrayList;
import java.util.List;

public class FanSpeedBar extends RelativeLayout implements HvacView {

    private static final int BAR_SEGMENT_ANIMATION_DELAY_MS = 50;
    private static final int BAR_SEGMENT_ANIMATION_PERIOD_MS = 100;

    private HvacPropertySetter mHvacPropertySetter;

    private int mHvacGlobalAreaId;

    private int mButtonActiveTextColor;
    private int mButtonInactiveTextColor;

    private int mFanOffActiveBgColor;
    private int mFanMaxActiveBgColor;

    private float mCornerRadius;

    private TextView mMaxButton;
    private TextView mOffButton;

    private boolean mPowerOn = false;
    private boolean mAutoOn = false;
    private boolean mDisableViewIfPowerOff = false;

    private float mOnAlpha;
    private float mOffAlpha;
    private int mMinFanSpeedSupportedByUi;
    private int mMaxFanSpeedSupportedByUi;
    private int mCurrentFanSpeed;

    /**
     * List of all fan buttons in the order they are displayed in the UI left to right. In other
     * words, first the off button, then the {@link FanSpeedBarSegment} buttons, and lastly the max
     * button.
     */
    private final List<View> mFanSpeedButtons = new ArrayList<>();

    public FanSpeedBar(Context context) {
        super(context);
        init();
    }

    public FanSpeedBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FanSpeedBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.fan_speed, this);

        Resources res = getContext().getResources();
        // The fanspeed bar is set as height 72dp to match min tap target size. However it is
        // inset by fan speed inset to make it appear thinner.
        int barHeight = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_bar_height);
        int insetHeight = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_bar_vertical_inset);
        mCornerRadius = (float) (barHeight - 2 * insetHeight) / 2;

        mFanOffActiveBgColor = res.getColor(R.color.hvac_fanspeed_off_active_bg);

        mButtonActiveTextColor = res.getColor(R.color.hvac_fanspeed_off_active_text_color);
        mButtonInactiveTextColor = res.getColor(R.color.hvac_fanspeed_off_inactive_text_color);
        mFanMaxActiveBgColor = res.getColor(R.color.hvac_fanspeed_segment_color);
        mHvacGlobalAreaId = res.getInteger(R.integer.hvac_global_area_id);
        mMinFanSpeedSupportedByUi = res.getInteger(R.integer.hvac_min_fan_speed);
        mMaxFanSpeedSupportedByUi = res.getInteger(R.integer.hvac_max_fan_speed);
    }

    @Override
    public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
        mHvacPropertySetter = hvacPropertySetter;
    }

    @Override
    public void setDisableViewIfPowerOff(boolean disableViewIfPowerOff) {
        mDisableViewIfPowerOff = disableViewIfPowerOff;
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value.getPropertyId() == HVAC_FAN_SPEED) {
            int fanSpeed = (Integer) value.getValue();
            // Sanitize the fan speed value to not exceed the number of
            // fan buttons.
            if (fanSpeed > mMaxFanSpeedSupportedByUi) {
                fanSpeed = mMaxFanSpeedSupportedByUi;
            }
            if (mCurrentFanSpeed == fanSpeed) {
                return;
            }
            mCurrentFanSpeed = fanSpeed;
            int fanSpeedIndex = fanSpeed - mMinFanSpeedSupportedByUi;
            int delay = 0;
            // Animate segments turning on when the fan speed is increased.
            // Start from index 1 to ignore off button.
            for (int i = 1; i < fanSpeedIndex + 1; i++) {
                if (!(mFanSpeedButtons.get(i) instanceof FanSpeedBarSegment fanSpeedButton)) {
                    continue;
                }
                if (!fanSpeedButton.isTurnedOn()) {
                    fanSpeedButton.playTurnOnAnimation(delay, BAR_SEGMENT_ANIMATION_PERIOD_MS);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }
            delay = 0;
            // Animate segments turning off when the fan speed is decreased.
            // Start from 2nd to last index to ignore max button.
            for (int i = mFanSpeedButtons.size() - 2; i > fanSpeedIndex; i--) {
                if (!(mFanSpeedButtons.get(i) instanceof FanSpeedBarSegment fanSpeedButton)) {
                    continue;
                }
                if (fanSpeedButton.isTurnedOn()) {
                    fanSpeedButton.playTurnOffAnimation(delay, BAR_SEGMENT_ANIMATION_PERIOD_MS);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }
            setOffAndMaxButtonsActiveState(fanSpeed);
            return;
        }

        if (value.getPropertyId() == HVAC_POWER_ON) {
            mPowerOn = (Boolean) value.getValue();
        }

        if (value.getPropertyId() == HVAC_AUTO_ON) {
            mAutoOn = (Boolean) value.getValue();
        }

        updateViewPerAvailability();
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return HVAC_FAN_SPEED;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mHvacGlobalAreaId;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOnAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_on_alpha);
        mOffAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_off_alpha);

        // Buttons are added to list in the order that they are displayed
        // in the UI from left to right.
        mOffButton = findViewById(R.id.fan_off);
        mFanSpeedButtons.add(mOffButton);
        mFanSpeedButtons.add(findViewById(R.id.fan_speed_1));
        mFanSpeedButtons.add(findViewById(R.id.fan_speed_2));
        mFanSpeedButtons.add(findViewById(R.id.fan_speed_3));
        mFanSpeedButtons.add(findViewById(R.id.fan_speed_4));
        mMaxButton = findViewById(R.id.fan_max);
        mFanSpeedButtons.add(mMaxButton);
        setFanSpeedButtonListeners();

        // Set the corner radius of the off/max button based on the height of the bar to get a
        // pill-shaped border.
        GradientDrawable offButtonBg = new GradientDrawable();
        offButtonBg.setCornerRadii(new float[]{mCornerRadius, mCornerRadius, 0, 0,
                0, 0, mCornerRadius, mCornerRadius});
        mOffButton.setBackground(offButtonBg);
        mOffButton.setTextColor(mButtonInactiveTextColor);

        GradientDrawable maxButtonBg = new GradientDrawable();
        maxButtonBg.setCornerRadii(new float[]{0, 0, mCornerRadius, mCornerRadius,
                mCornerRadius, mCornerRadius, 0, 0});
        mMaxButton.setBackground(maxButtonBg);
        mMaxButton.setTextColor(mButtonInactiveTextColor);
        updateViewPerAvailability();
    }

    @Override
    public void setConfigInfo(CarPropertyConfig<?> carPropertyConfig) {
        // If there are different min/max values between area IDs,
        // use the highest min value and lowest max value so the
        // value can be set across all area IDs.
        Integer highestMinValue = HvacUtils.getHighestMinValueForAllAreaIds(carPropertyConfig);
        Integer lowestMaxValue = HvacUtils.getLowestMaxValueForAllAreaIds(carPropertyConfig);
        if (highestMinValue != null) {
            mMinFanSpeedSupportedByUi = highestMinValue;
        }
        if (lowestMaxValue != null) {
            // The number of fan speeds cannot exceed the number of icons that represent
            // the levels.
            mMaxFanSpeedSupportedByUi = Math.min(lowestMaxValue,
                    mMinFanSpeedSupportedByUi + mFanSpeedButtons.size() - 1);
        }
        setFanSpeedButtonListeners();
    }

    private void setOffAndMaxButtonsActiveState(int fanSpeed) {
        setOffButtonActive(fanSpeed == mMinFanSpeedSupportedByUi);
        setMaxButtonActive(fanSpeed == mMaxFanSpeedSupportedByUi);
    }

    private void setMaxButtonActive(boolean active) {
        GradientDrawable background = (GradientDrawable) mMaxButton.getBackground();
        if (active) {
            background.setColor(mFanMaxActiveBgColor);
            mMaxButton.setTextColor(mButtonActiveTextColor);
        } else {
            background.setColor(Color.TRANSPARENT);
            mMaxButton.setTextColor(mButtonInactiveTextColor);
        }
    }

    private void setOffButtonActive(boolean active) {
        GradientDrawable background = (GradientDrawable) mOffButton.getBackground();
        if (active) {
            background.setColor(mFanOffActiveBgColor);
            mOffButton.setTextColor(mButtonActiveTextColor);
        } else {
            background.setColor(Color.TRANSPARENT);
            mOffButton.setTextColor(mButtonInactiveTextColor);
        }
    }

    private OnClickListener getOnClickListener(int fanSpeed) {
        return v -> {
            if (shouldAllowControl()) {
                mHvacPropertySetter.setHvacProperty(HVAC_FAN_SPEED, getAreaId(), fanSpeed);
            }
        };
    }

    private void updateViewPerAvailability() {
        setAlpha(shouldAllowControl() ? mOnAlpha : mOffAlpha);
    }

    private boolean shouldAllowControl() {
        return HvacUtils.shouldAllowControl(mDisableViewIfPowerOff, mPowerOn, mAutoOn);
    }

    private void setFanSpeedButtonListeners() {
        for (int i = 0; i < mFanSpeedButtons.size(); i++) {
            mFanSpeedButtons.get(i).setOnClickListener(
                    getOnClickListener(/* fanSpeed =*/ mMinFanSpeedSupportedByUi + i));
        }
    }
}