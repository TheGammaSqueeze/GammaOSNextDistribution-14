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
import static android.car.VehiclePropertyIds.HVAC_FAN_DIRECTION;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.car.hvac.HvacUtils;
import com.android.systemui.car.hvac.HvacView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A group of buttons that allows users to select the fan direction. Only one button can be
 * activated at a time.
 */
public class FanDirectionButtons extends LinearLayout implements HvacView {

    /**
     * NOTE: The integers below are not arbitrarily assigned and represent the corresponding values
     * implemented in VHAL.
     */
    public static final int FAN_DIRECTION_FACE = 1;
    public static final int FAN_DIRECTION_FACE_FLOOR = 3;
    public static final int FAN_DIRECTION_FLOOR = 2;
    public static final int FAN_DIRECTION_FLOOR_DEFROSTER = 6;
    public static final int FAN_DIRECTION_COUNT = 4;

    private static final int INVALID_ID = -1;
    private static final String TAG = FanDirectionButtons.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;

    @IntDef({FAN_DIRECTION_FACE, FAN_DIRECTION_FACE_FLOOR,
            FAN_DIRECTION_FLOOR, FAN_DIRECTION_FLOOR_DEFROSTER})
    public @interface FanDirection {
    }

    /**
     * A resource ID array for all fan direction buttons.
     */
    private static final int[] FAN_DIRECTION_BUTTON_IDS = {R.id.direction_face,
            R.id.direction_face_and_floor, R.id.direction_floor, R.id.direction_defrost};

    private final List<ImageView> mButtons = new ArrayList<ImageView>();
    private final List<Integer> mButtonDirections = new ArrayList<>();
    private final Map<Integer, Integer> mButtonIndicesByDirection = new HashMap<>();

    private HvacPropertySetter mHvacPropertySetter;
    private boolean mPowerOn = false;
    private boolean mAutoOn = false;
    private boolean mDisableViewIfPowerOff = false;
    private float mOnAlpha;
    private float mOffAlpha;
    private int mCurrentDirection = INVALID_ID;
    private int mHvacGlobalAreaId;

    public FanDirectionButtons(Context context) {
        super(context);
        init();
    }

    public FanDirectionButtons(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FanDirectionButtons(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOnAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_on_alpha);
        mOffAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_off_alpha);

        for (int i = 0; i < FAN_DIRECTION_COUNT; i++) {
            ImageView button = (ImageView) findViewById(FAN_DIRECTION_BUTTON_IDS[i]);
            int fanDirection = mButtonDirections.get(i);
            button.setOnClickListener(v -> {
                if (!shouldAllowControl()) return;
                if (fanDirection != mCurrentDirection) {
                    mHvacPropertySetter.setHvacProperty(HVAC_FAN_DIRECTION, getAreaId(),
                            fanDirection);
                }
            });
            mButtons.add(button);
        }
        updateViewPerAvailability();
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
        if (value.getPropertyId() == HVAC_FAN_DIRECTION) {
            int newDirection = INVALID_ID;
            if (value.getValue() instanceof Integer) {
                newDirection = (Integer) value.getValue();
            }
            if (!mButtonDirections.contains(newDirection)) {
                if (DEBUG) {
                    Log.d(TAG, "Button is not defined for direction: " + newDirection);
                }
                return;
            }

            if (mCurrentDirection != INVALID_ID) {
                updateFanDirectionButtonSelectedOnUiThread(mCurrentDirection, false);
            }
            mCurrentDirection = newDirection;
            updateFanDirectionButtonSelectedOnUiThread(mCurrentDirection, true);

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

    private void updateFanDirectionButtonSelectedOnUiThread(int currentDirection,
            boolean selected) {
        mContext.getMainExecutor().execute(() -> {
            mButtons.get(mButtonIndicesByDirection.get(currentDirection))
                    .setSelected(selected);
        });
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return HVAC_FAN_DIRECTION;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mHvacGlobalAreaId;
    }

    private void init() {
        inflate(getContext(), R.layout.fan_direction, this);
        mHvacGlobalAreaId = getContext().getResources().getInteger(R.integer.hvac_global_area_id);
        mButtonDirections.add(FAN_DIRECTION_FACE);
        mButtonDirections.add(FAN_DIRECTION_FACE_FLOOR);
        mButtonDirections.add(FAN_DIRECTION_FLOOR);
        mButtonDirections.add(FAN_DIRECTION_FLOOR_DEFROSTER);

        mButtonIndicesByDirection.put(FAN_DIRECTION_FACE, 0);
        mButtonIndicesByDirection.put(FAN_DIRECTION_FACE_FLOOR, 1);
        mButtonIndicesByDirection.put(FAN_DIRECTION_FLOOR, 2);
        mButtonIndicesByDirection.put(FAN_DIRECTION_FLOOR_DEFROSTER, 3);
    }

    private void updateViewPerAvailability() {
        setAlpha(shouldAllowControl() ? mOnAlpha : mOffAlpha);
    }

    private boolean shouldAllowControl() {
        return HvacUtils.shouldAllowControl(mDisableViewIfPowerOff, mPowerOn, mAutoOn);
    }
}
