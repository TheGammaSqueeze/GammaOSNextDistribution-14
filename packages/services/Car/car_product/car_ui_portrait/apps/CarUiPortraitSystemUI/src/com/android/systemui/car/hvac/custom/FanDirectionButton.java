/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.car.VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE;

import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.ArraySet;
import android.util.AttributeSet;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.car.hvac.toggle.HvacToggleButton;

import java.util.Set;

/**
 * An implementation of the {@link HvacToggleButton} which behaves like a bitmask toggle.
 */
public class FanDirectionButton extends HvacToggleButton<Integer> {
    private static final int DEFAULT_INVALID_VALUE = -1;
    private static final String TAG = FanDirectionButton.class.getSimpleName();

    private Set<Integer> mHvacFanDirectionAvailable = Set.of();
    private int mSingleHvacDirectionBit;
    private int mCurrentHvacFanDirectionValue;

    public FanDirectionButton(Context context) {
        super(context);
    }

    public FanDirectionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FanDirectionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FanDirectionButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value.getPropertyId() == HVAC_FAN_DIRECTION_AVAILABLE
                && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE) {
            mHvacFanDirectionAvailable = new ArraySet((Integer[]) value.getValue());
            // Unknown is not a settable value
            mHvacFanDirectionAvailable.remove(CarHvacFanDirection.UNKNOWN);
        }
        super.onPropertyChanged(value);
    }

    @Override
    protected void parseAttributes(AttributeSet attrs) {
        super.parseAttributes(attrs);
        TypedArray typedArray = mContext.obtainStyledAttributes(attrs,
                R.styleable.FanDirectionButton);
        mSingleHvacDirectionBit = typedArray.getInt(
                R.styleable.FanDirectionButton_singleHvacDirectionBit, DEFAULT_INVALID_VALUE);
        typedArray.recycle();
    }

    @Override
    protected void handleClick(HvacPropertySetter propertySetter) {
        int newFanDirection = mCurrentHvacFanDirectionValue ^ mSingleHvacDirectionBit;
        if (!mHvacFanDirectionAvailable.contains(newFanDirection)) {
            newFanDirection = mSingleHvacDirectionBit;
        }
        propertySetter.setHvacProperty(getHvacPropertyToView(), getAreaId(), newFanDirection);
    }

    @Override
    protected boolean updateToggleState(Integer propertyValue) {
        mCurrentHvacFanDirectionValue = propertyValue;
        return isToggleOn();
    }

    @Override
    protected boolean isToggleOn() {
        return (mCurrentHvacFanDirectionValue & mSingleHvacDirectionBit) == mSingleHvacDirectionBit;
    }
}
