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

package com.android.systemui.car.hvac.custom;

import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_FAN_SPEED;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.widget.SeekBar;

import androidx.annotation.ArrayRes;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.car.hvac.HvacUtils;
import com.android.systemui.car.hvac.HvacView;

/** Custom seek bar to control fan speed. */
public class FanSpeedSeekBar extends SeekBar implements HvacView {

    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = "FanSpeedSeekBar";

    private final SparseArray<Drawable> mIcons = new SparseArray<>();

    private HvacPropertySetter mHvacPropertySetter;
    private int mHvacGlobalAreaId;

    private boolean mPowerOn;
    private boolean mAutoOn;
    private boolean mDisableViewIfPowerOff = false;

    private float mOnAlpha;
    private float mOffAlpha;

    private final OnSeekBarChangeListener mSeekBarChangeListener = new OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int prevProgress = getProgress();
            // Limit updates to the hvac property to be only those that come from the user in order
            // to avoid an infinite loop.
            if (shouldAllowControl() && fromUser && progress == prevProgress) {
                mHvacPropertySetter.setHvacProperty(HVAC_FAN_SPEED, getAreaId(), progress);
            } else if (progress != prevProgress) {
                // There is an edge case with seek bar touch handling that can lead to an
                // inconsistent state of the progress state and UI. We need to set the progress to
                // a different value before setting it to the value we expect in order to ensure
                // that the update doesn't get dropped.
                setProgress(progress);
                setProgress(prevProgress);
                updateUI();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // no-op.
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // no-op.
        }
    };

    public FanSpeedSeekBar(Context context) {
        super(context);
        init(null);
    }

    public FanSpeedSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public FanSpeedSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public FanSpeedSeekBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        int speeds = mContext.getResources().getInteger(R.integer.hvac_num_fan_speeds);
        if (speeds < 1) {
            throw new IllegalArgumentException("The nuer of fan speeds should be > 1");
        }

        setMin(1);
        incrementProgressBy(1);
        setMax(speeds);
        int thumbRadius = mContext.getResources().getDimensionPixelSize(
                R.dimen.hvac_panel_seek_bar_radius);
        setPadding(thumbRadius, 0, thumbRadius, 0);
        mHvacGlobalAreaId = mContext.getResources().getInteger(R.integer.hvac_global_area_id);

        mOnAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_on_alpha);
        mOffAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_off_alpha);

        if (attrs == null) {
            return;
        }

        // Get fan speed thumb drawables.
        TypedArray typedArray = mContext.obtainStyledAttributes(attrs, R.styleable.FanSpeedSeekBar);
        @ArrayRes int drawableListRes = typedArray.getResourceId(
                R.styleable.FanSpeedSeekBar_fanSpeedThumbIcons,
                R.array.hvac_fan_speed_icons);

        TypedArray fanSpeedThumbIcons = mContext.getResources().obtainTypedArray(drawableListRes);
        if (fanSpeedThumbIcons.length() != speeds) {
            throw new IllegalArgumentException(
                    "R.styeable.SeatHeatLevelButton_seatHeaterIconDrawableList should have the "
                            + "same length as R.integer.hvac_seat_heat_level_count");
        }

        for (int i = 0; i < speeds; i++) {
            mIcons.set(i + 1, fanSpeedThumbIcons.getDrawable(i));
        }
        fanSpeedThumbIcons.recycle();
        typedArray.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnSeekBarChangeListener(mSeekBarChangeListener);
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
    public void setConfigInfo(CarPropertyConfig<?> carPropertyConfig) {
        // If there are different min/max values between area IDs,
        // use the highest min value and lowest max value so the
        // value can be set across all area IDs.
        Integer highestMinValue = HvacUtils.getHighestMinValueForAllAreaIds(carPropertyConfig);
        Integer lowestMaxValue = HvacUtils.getLowestMaxValueForAllAreaIds(carPropertyConfig);
        if (highestMinValue != null) {
            setMin(highestMinValue);
        }
        if (lowestMaxValue != null) {
            // The number of fan speeds cannot exceed the number of icons that represent
            // the levels.
            setMax(Math.min(lowestMaxValue, mIcons.size()));
        }
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value == null) {
            if (DEBUG) {
                Log.w(TAG, "onPropertyChanged: received null value");
            }
            return;
        }

        if (DEBUG) {
            Log.w(TAG, "onPropertyChanged: property id: " + value.getPropertyId());
            Log.w(TAG, "onPropertyChanged: area id: " + value.getAreaId());
            Log.w(TAG, "onPropertyChanged: value: " + value.getValue());
        }

        if (value.getPropertyId() == HVAC_FAN_SPEED) {
            int level = (int) value.getValue();
            setProgress(level, /* animate= */ true);
        }

        if (value.getPropertyId() == HVAC_POWER_ON) {
            mPowerOn = (boolean) value.getValue();
        }

        if (value.getPropertyId() == HVAC_AUTO_ON) {
            mAutoOn = (boolean) value.getValue();
        }

        updateUI();
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return HVAC_FAN_SPEED;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mHvacGlobalAreaId;
    }

    private void updateUI() {
        int progress = getProgress();
        setThumb(mIcons.get(progress));
        setSelected(progress > 0);
        setAlpha(shouldAllowControl() ? mOnAlpha : mOffAlpha);
        // Steal touch events if shouldn't allow control.
        setOnTouchListener(shouldAllowControl() ? null : (v, event) -> true);
    }

    private boolean shouldAllowControl() {
        return HvacUtils.shouldAllowControl(mDisableViewIfPowerOff, mPowerOn, mAutoOn);
    }
}
