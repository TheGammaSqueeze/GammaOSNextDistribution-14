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

import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;

import androidx.annotation.CallSuper;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.car.hvac.HvacUtils;
import com.android.systemui.car.hvac.HvacView;

/**
 * An abstract toggle button which changes its drawable based off its state.
 *
 * @param <PropertyType> the type of the car property that is read/written by this toggle button.
 */
public abstract class HvacToggleButton<PropertyType> extends ImageButton implements HvacView {
    protected static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = "HvacToggleButton";
    private static final int INVALID_ID = -1;
    private static final int VEHICLE_AREA_MASK = 0x0f000000;
    private static final int VEHICLE_AREA_SEAT = 0x05000000;

    private int mPropertyId;
    private int mAreaId;
    private boolean mIsOn;
    private boolean mPowerOn = false;
    private boolean mAutoOn = false;
    private boolean mDisableViewIfPowerOff = false;
    private boolean mDisableViewIfAutoOn;
    private Drawable mOnDrawable;
    private Drawable mOffDrawable;
    private float mOnAlpha;
    private float mOffAlpha;
    private HvacPropertySetter mHvacPropertySetter;

    public HvacToggleButton(Context context) {
        super(context);
    }

    public HvacToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        parseAttributes(attrs);
    }

    public HvacToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        parseAttributes(attrs);
    }

    public HvacToggleButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        parseAttributes(attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(v -> {
            if (!shouldAllowControl()) return;
            handleClick(mHvacPropertySetter);
        });
        updateUIState();
    }

    @CallSuper
    protected void parseAttributes(AttributeSet attrs) {
        TypedArray typedArray = mContext.obtainStyledAttributes(attrs, R.styleable.HvacView);
        mPropertyId = typedArray.getInt(R.styleable.HvacView_hvacPropertyId, INVALID_ID);
        mAreaId = typedArray.getInt(R.styleable.HvacView_hvacAreaId, INVALID_ID);
        mOnDrawable = typedArray.getDrawable(R.styleable.HvacView_hvacToggleOnButtonDrawable);
        mOffDrawable = typedArray.getDrawable(R.styleable.HvacView_hvacToggleOffButtonDrawable);
        mDisableViewIfAutoOn =
                typedArray.getBoolean(R.styleable.HvacView_hvacDisableViewIfAutoOn, false);
        mOnAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_on_alpha);
        mOffAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_off_alpha);
        typedArray.recycle();
    }

    @CallSuper
    protected void updateUIState() {
        mContext.getMainThreadHandler().post(() -> {
            setAlpha(shouldAllowControl() ? mOnAlpha : mOffAlpha);
            setImageDrawable(isToggleOn() ? mOnDrawable : mOffDrawable);
            setSelected(isToggleOn());
        });
    }

    /** Handles when the toggle button is clicked. */
    protected abstract void handleClick(HvacPropertySetter propertySetter);

    /**
     * Updates whether the toggle should be on or off. Returns the expected state given the property
     * value.
     */
    protected abstract boolean updateToggleState(PropertyType propertyValue);

    /** Returns whether the toggle is in the enabled state. */
    protected abstract boolean isToggleOn();

    protected boolean shouldAllowControl() {
        if (mHvacPropertySetter == null) {
            return false;
        }

        if ((mPropertyId & VEHICLE_AREA_MASK) == VEHICLE_AREA_SEAT) {
            return HvacUtils.shouldAllowControl(mDisableViewIfPowerOff, mPowerOn,
                    mDisableViewIfAutoOn, mAutoOn);
        }

        return true;
    }

    @Override
    public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
        mHvacPropertySetter = hvacPropertySetter;
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value == null) {
            if (DEBUG) {
                Log.d(TAG, "onPropertyChanged: received null value");
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onPropertyChanged: property ID: "
                    + VehiclePropertyIds.toString(value.getPropertyId()));
            Log.d(TAG, "onPropertyChanged: area ID: 0x" + Integer.toHexString(value.getAreaId()));
            Log.d(TAG, "onPropertyChanged: value: " + value.getValue());
        }

        if (value.getPropertyId() == HVAC_POWER_ON) {
            mPowerOn = (Boolean) value.getValue();
        }

        if (value.getPropertyId() == HVAC_AUTO_ON) {
            mAutoOn = (Boolean) value.getValue();
        }

        if (value.getPropertyId() == getHvacPropertyToView()) {
            mIsOn = updateToggleState((PropertyType) value.getValue());
        }

        updateUIState();
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return mPropertyId;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mAreaId;
    }

    @Override
    public void setDisableViewIfPowerOff(boolean disableViewIfPowerOff) {
        mDisableViewIfPowerOff = disableViewIfPowerOff;
    }
}
