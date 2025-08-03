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
package com.android.systemui.car.displaycompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.android.systemui.R;
import com.android.systemui.car.systembar.CarSystemBarView;

/**
 *
 */
public class CarDisplayCompatSystemBarView extends CarSystemBarView {

    public static final String DISPLAYCOMPAT_SYSTEM_FEATURE = "android.car.displaycompatibility";

    public CarDisplayCompatSystemBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CarDisplayCompatSystemBarView, 0, 0);
        int defaultLayoutId =
                a.getResourceId(R.styleable.CarDisplayCompatSystemBarView_default_layout, 0);
        if (defaultLayoutId == 0) {
            throw new IllegalArgumentException("default_layout attribute is not set");
        }
        int displayCompatLayoutId =
                a.getResourceId(R.styleable.CarDisplayCompatSystemBarView_displaycompat_layout, 0);
        int displayCompatConfig = context.getResources()
                .getInteger(R.integer.config_showDisplayCompatToolbarOnSystemBar);
        int compatDisplaySide = a.getInt(
                R.styleable.CarDisplayCompatSystemBarView_displaycompat_side, 0);
        a.recycle();

        PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(DISPLAYCOMPAT_SYSTEM_FEATURE)
                && displayCompatConfig != 0
                && displayCompatConfig == compatDisplaySide) {
            inflate(context, displayCompatLayoutId, this);
        } else {
            inflate(context, defaultLayoutId, this);
        }
    }
}
