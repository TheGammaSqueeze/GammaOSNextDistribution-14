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

package com.android.systemui.car.statusicon;


import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.systemui.car.systembar.element.CarSystemBarElement;
import com.android.systemui.car.systembar.element.CarSystemBarElementFlags;
import com.android.systemui.car.systembar.element.CarSystemBarElementResolver;

public class StatusIconView extends ImageView implements CarSystemBarElement {
    private static final String TAG = StatusIconView.class.getSimpleName();

    private Class<?> mElementControllerClassAttr;
    private int mSystemBarDisableFlags;
    private int mSystemBarDisable2Flags;
    private boolean mDisableForLockTaskModeLocked;

    public StatusIconView(Context context) {
        super(context);
        init(context, /* attrs= */ null);
    }

    public StatusIconView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public StatusIconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public StatusIconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        mElementControllerClassAttr =
                CarSystemBarElementResolver.getElementControllerClassFromAttributes(context, attrs);
        mSystemBarDisableFlags =
                CarSystemBarElementFlags.getStatusBarManagerDisableFlagsFromAttributes(context,
                        attrs);
        mSystemBarDisable2Flags =
                CarSystemBarElementFlags.getStatusBarManagerDisable2FlagsFromAttributes(context,
                        attrs);
        mDisableForLockTaskModeLocked =
                CarSystemBarElementFlags.getDisableForLockTaskModeLockedFromAttributes(context,
                        attrs);
    }

    @Override
    public Class<?> getElementControllerClass() {
        if (mElementControllerClassAttr != null) {
            if (StatusIconViewController.class.isAssignableFrom(mElementControllerClassAttr)) {
                return mElementControllerClassAttr;
            } else {
                Log.w(TAG, "Class " + mElementControllerClassAttr
                        + " is not of type StatusIconViewController - returning null");
            }
        }
        return null;
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
