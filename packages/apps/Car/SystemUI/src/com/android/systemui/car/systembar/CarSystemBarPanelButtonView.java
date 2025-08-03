/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.DimenRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.car.systembar.element.CarSystemBarElement;
import com.android.systemui.car.systembar.element.CarSystemBarElementFlags;
import com.android.systemui.car.systembar.element.CarSystemBarElementResolver;

/** Custom view that provides the layout and attributes for creating system bar panels. */
public class CarSystemBarPanelButtonView extends LinearLayout implements CarSystemBarElement {
    static final int INVALID_RESOURCE_ID = -1;

    private Class<?> mElementControllerClassAttr;
    private int mSystemBarDisableFlags;
    private int mSystemBarDisable2Flags;
    private boolean mDisableForLockTaskModeLocked;

    @LayoutRes
    private int mPanelLayoutRes;
    @DimenRes
    private int mPanelWidthRes;
    @Nullable
    private Integer mXOffset;
    @Nullable
    private Integer mYOffset;
    @Nullable
    private Integer mGravity;
    @Nullable
    private Boolean mDisabledWhileDriving;
    @Nullable
    private Boolean mShowAsDropDown;

    public CarSystemBarPanelButtonView(Context context) {
        super(context);
        init(context, /* attrs= */ null);
    }

    public CarSystemBarPanelButtonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CarSystemBarPanelButtonView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public CarSystemBarPanelButtonView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mElementControllerClassAttr =
                CarSystemBarElementResolver.getElementControllerClassFromAttributes(context, attrs);
        mSystemBarDisableFlags =
                CarSystemBarElementFlags.getStatusBarManagerDisableFlagsFromAttributes(context,
                        attrs);
        mSystemBarDisable2Flags =
                CarSystemBarElementFlags.getStatusBarManagerDisableFlagsFromAttributes(context,
                        attrs);
        mDisableForLockTaskModeLocked =
                CarSystemBarElementFlags.getDisableForLockTaskModeLockedFromAttributes(context,
                        attrs);

        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CarSystemBarPanelButtonView);
        mPanelLayoutRes = typedArray.getResourceId(
                R.styleable.CarSystemBarPanelButtonView_panelLayoutRes,
                INVALID_RESOURCE_ID);
        mPanelWidthRes = typedArray.getResourceId(
                R.styleable.CarSystemBarPanelButtonView_panelWidthRes,
                R.dimen.car_status_icon_panel_default_width);
        mXOffset = typedArray.hasValue(R.styleable.CarSystemBarPanelButtonView_xOffset)
                ? typedArray.getInteger(R.styleable.CarSystemBarPanelButtonView_xOffset, 0) : null;
        mYOffset = typedArray.hasValue(R.styleable.CarSystemBarPanelButtonView_yOffset)
                ? typedArray.getInteger(R.styleable.CarSystemBarPanelButtonView_yOffset, 0) : null;
        mGravity = typedArray.hasValue(R.styleable.CarSystemBarPanelButtonView_gravity)
                ? typedArray.getInteger(R.styleable.CarSystemBarPanelButtonView_gravity, 0) : null;
        mDisabledWhileDriving =
                typedArray.hasValue(R.styleable.CarSystemBarPanelButtonView_disabledWhileDriving)
                        ? typedArray.getBoolean(
                        R.styleable.CarSystemBarPanelButtonView_disabledWhileDriving, false) : null;
        mShowAsDropDown =
                typedArray.hasValue(R.styleable.CarSystemBarPanelButtonView_showAsDropDown)
                        ? typedArray.getBoolean(
                        R.styleable.CarSystemBarPanelButtonView_showAsDropDown, true) : null;
        typedArray.recycle();
    }


    @LayoutRes
    public int getPanelContentLayout() {
        return mPanelLayoutRes;
    }

    @DimenRes
    public int getPanelWidth() {
        return mPanelWidthRes;
    }

    @Nullable
    public Integer getXOffset() {
        return mXOffset;
    }

    @Nullable
    public Integer getYOffset() {
        return mYOffset;
    }

    @Nullable
    public Integer getPanelGravity() {
        return mGravity;
    }

    @Nullable
    public Boolean getDisabledWhileDriving() {
        return mDisabledWhileDriving;
    }

    @Nullable
    public Boolean getShowAsDropDown() {
        return mShowAsDropDown;
    }

    @Override
    public Class<?> getElementControllerClass() {
        if (mElementControllerClassAttr != null) {
            return mElementControllerClassAttr;
        }
        return CarSystemBarPanelButtonViewController.class;
    }

    @Override
    public int getSystemBarDisableFlags() {
        return mSystemBarDisableFlags;
    }

    @Override
    public int getSystemBarDisable2Flags() {
        return mSystemBarDisable2Flags;
    }

    @Override
    public boolean disableForLockTaskModeLocked() {
        return mDisableForLockTaskModeLocked;
    }
}
