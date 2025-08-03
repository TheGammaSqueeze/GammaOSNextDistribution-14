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

package com.android.systemui.car.hvac.toggle;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacPropertySetter;

/**
 * An implementation of the {@link HvacToggleButton} which toggles between two {@code int} values.
 */
public class HvacIntegerToggleButton extends HvacToggleButton<Integer> {
    private static final int DEFAULT_INVALID_VALUE = -1;

    private int mOnValue;
    private int mOffValue;
    private int mCurrentValue;
    private boolean mPreventToggleOff;

    public HvacIntegerToggleButton(Context context) {
        super(context);
    }

    public HvacIntegerToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HvacIntegerToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HvacIntegerToggleButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void parseAttributes(AttributeSet attrs) {
        super.parseAttributes(attrs);
        TypedArray typedArray = mContext.obtainStyledAttributes(attrs,
                R.styleable.HvacIntegerToggleButton);
        int invalidValue = typedArray.getInt(R.styleable.HvacIntegerToggleButton_invalidValue,
                DEFAULT_INVALID_VALUE);
        mOnValue = typedArray.getInt(R.styleable.HvacIntegerToggleButton_onValue, invalidValue);
        mOffValue = typedArray.getInt(R.styleable.HvacIntegerToggleButton_offValue, invalidValue);
        mPreventToggleOff = typedArray.getBoolean(
                R.styleable.HvacIntegerToggleButton_preventToggleOff, false);
        typedArray.recycle();
    }

    @Override
    protected void handleClick(HvacPropertySetter propertySetter) {
        // If {@code mPreventToggleOff} is {@code true}, then this toggle button is meant to be used
        // like a radio button. If the toggle is already on, don't do anything.
        if (mPreventToggleOff && isToggleOn()) {
            return;
        }

        int newValue = isToggleOn() ? mOffValue : mOnValue;
        propertySetter.setHvacProperty(getHvacPropertyToView(), getAreaId(), newValue);
    }

    @Override
    protected boolean updateToggleState(Integer propertyValue) {
        mCurrentValue = propertyValue;
        return mCurrentValue == mOnValue;
    }

    @Override
    protected boolean isToggleOn() {
        return mCurrentValue == mOnValue;
    }
}
