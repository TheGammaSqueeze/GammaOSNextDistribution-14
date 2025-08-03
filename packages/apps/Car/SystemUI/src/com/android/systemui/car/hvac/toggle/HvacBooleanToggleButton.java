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
import android.util.AttributeSet;

import com.android.systemui.car.hvac.HvacPropertySetter;

/**
 * An implementation of the {@link HvacToggleButton} which toggles between boolean values.
 */
public class HvacBooleanToggleButton extends HvacToggleButton<Boolean> {

    private boolean mCurrentValue;

    public HvacBooleanToggleButton(Context context) {
        super(context);
    }

    public HvacBooleanToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HvacBooleanToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HvacBooleanToggleButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void handleClick(HvacPropertySetter propertySetter) {
        propertySetter.setHvacProperty(getHvacPropertyToView(), getAreaId(), !mCurrentValue);
    }

    @Override
    protected boolean updateToggleState(Boolean propertyValue) {
        mCurrentValue = propertyValue;
        return mCurrentValue;
    }

    @Override
    protected boolean isToggleOn() {
        return mCurrentValue;
    }
}
