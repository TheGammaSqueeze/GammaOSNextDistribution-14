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

package com.android.systemui.car.systembar.element;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.android.systemui.R;

/** Helper class for resolving element controllers */
public class CarSystemBarElementResolver {
    private static final String TAG = CarSystemBarElementResolver.class.getSimpleName();

    /** Convert a class string to a class instance of CarSystemBarElementController */
    public static Class<?> getElementControllerClassFromString(String str) {
        if (!TextUtils.isEmpty(str)) {
            try {
                Class<?> clazz = Class.forName(str);
                if (clazz != null && CarSystemBarElementController.class.isAssignableFrom(clazz)) {
                    return clazz;
                }
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "cannot find class for string " + str, e);
            }
        }
        return null;
    }

    /** Get the element controller class from the specified view attributes */
    @Nullable
    public static Class<?> getElementControllerClassFromAttributes(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return null;
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CarSystemBarElement);
        String str = typedArray.getString(R.styleable.CarSystemBarElement_controller);
        typedArray.recycle();
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        return getElementControllerClassFromString(str);
    }
}
