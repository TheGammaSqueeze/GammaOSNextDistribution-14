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

package com.android.systemui.car.hvac;

import static android.car.VehiclePropertyIds.HVAC_SEAT_TEMPERATURE;

import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageButton;

import androidx.annotation.ArrayRes;

import com.android.systemui.R;

/**
 * An image button that allows for multiple seat heating/cooling states based on
 * {@link R.integer.hvac_seat_heat_level_count}.
 */
public class SeatTemperatureLevelButton extends ImageButton implements HvacView {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = "SeatTemperatureLevelButton";

    private static final int INVALID_ID = -1;
    private static final int HEATING = 1;
    private static final int COOLING = -1;

    private final SparseArray<Drawable> mIcons = new SparseArray<>();

    private int mAreaId;
    private HvacPropertySetter mHvacPropertySetter;
    private Drawable mCurrentIcon;
    private int mCurrentLevel = -1;
    private int mTotalLevelCount;
    private int mTemperatureLevelType;

    public SeatTemperatureLevelButton(Context context) {
        super(context);
    }

    public SeatTemperatureLevelButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        parseAttributes(attrs);
    }

    public SeatTemperatureLevelButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        parseAttributes(attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(v -> {
            if (mHvacPropertySetter == null) {
                return;
            }

            int newLevel = mTemperatureLevelType * (mCurrentLevel + 1) % (mTotalLevelCount + 1);
            if (DEBUG) {
                Log.d(TAG, "setOnClickListener: new seat temperature level: " + newLevel);
            }
            mHvacPropertySetter.setHvacProperty(getHvacPropertyToView(), mAreaId, newLevel);
        });
        setOnLongClickListener(v -> {
            if (mHvacPropertySetter == null) {
                return false;
            }

            int newLevel = mTemperatureLevelType * (mCurrentLevel == 0 ? mTotalLevelCount : 0);
            if (DEBUG) {
                Log.d(TAG, "setOnLongClickListener: new seat temperature level: " + newLevel);
            }
            mHvacPropertySetter.setHvacProperty(getHvacPropertyToView(), mAreaId, newLevel);
            return true;
        });
        updateIcon();
    }

    @Override
    public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
        mHvacPropertySetter = hvacPropertySetter;
    }

    @Override
    public void setConfigInfo(CarPropertyConfig<?> carPropertyConfig) {
        AreaIdConfig<Integer> areaIdConfig = (AreaIdConfig<Integer>)
                carPropertyConfig.getAreaIdConfig(mAreaId);
        // The number of seat temperature levels cannot exceed the number of icons that represent
        // the levels.
        mTotalLevelCount = Math.min(areaIdConfig.getMaxValue(), mIcons.size());
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

        if (value.getPropertyId() == getHvacPropertyToView() && value.getAreaId() == getAreaId()) {
            mCurrentLevel = (int) value.getValue();
            if (mTemperatureLevelType == COOLING && mCurrentLevel > 0) {
                mCurrentLevel = 0;
            } else if (mTemperatureLevelType == HEATING && mCurrentLevel < 0) {
                mCurrentLevel = 0;
            }

            mCurrentLevel = Math.abs(mCurrentLevel);

            mCurrentIcon = mIcons.get(mCurrentLevel);
            updateIcon();
        }
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return HVAC_SEAT_TEMPERATURE;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mAreaId;
    }

    private void updateIcon() {
        mContext.getMainThreadHandler().post(() -> {
            setSelected(mCurrentLevel != 0);
            setImageDrawable(mCurrentIcon);
        });
    }

    private void parseAttributes(AttributeSet attrs) {
        mIcons.clear();

        mTotalLevelCount = mContext.getResources().getInteger(R.integer.hvac_seat_heat_level_count);

        TypedArray typedArray = mContext.obtainStyledAttributes(attrs, R.styleable.HvacView);
        mAreaId = typedArray.getInt(R.styleable.HvacView_hvacAreaId, INVALID_ID);
        typedArray.recycle();

        typedArray = mContext.obtainStyledAttributes(attrs, R.styleable.SeatTemperatureLevelButton);
        mTemperatureLevelType = typedArray.getInt(
                R.styleable.SeatTemperatureLevelButton_seatTemperatureType, HEATING);

        @ArrayRes int drawableListRes = typedArray.getResourceId(
                R.styleable.SeatTemperatureLevelButton_seatTemperatureIconDrawableList,
                R.array.hvac_heated_seat_default_icons);

        TypedArray seatTemperatureIcons = mContext.getResources().obtainTypedArray(drawableListRes);
        if (seatTemperatureIcons.length() != mTotalLevelCount) {
            throw new IllegalArgumentException(
                    "R.styeable.SeatHeatLevelButton_seatHeaterIconDrawableList should have the "
                            + "same length as R.integer.hvac_seat_heat_level_count");
        }

        for (int i = 0; i < mTotalLevelCount; i++) {
            mIcons.set(i, seatTemperatureIcons.getDrawable(i));
        }
        seatTemperatureIcons.recycle();
        typedArray.recycle();
    }
}
