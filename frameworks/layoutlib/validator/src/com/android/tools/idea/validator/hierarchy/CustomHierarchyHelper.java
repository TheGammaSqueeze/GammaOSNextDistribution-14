/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.idea.validator.hierarchy;

import com.android.ide.common.rendering.api.LayoutlibCallback;

import android.view.View;
import android.widget.Checkable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Helper for support lib dependencies. */
public class CustomHierarchyHelper {
    public static LayoutlibCallback sLayoutlibCallback;

    /** Get the class instance from the studio based on the string class name. */
    public static Class<?> getClassByName(String className) {
        try {
            return sLayoutlibCallback.findClass(className);
        } catch (ClassNotFoundException ignore) {
        }
        return null;
    }

    /** Returns true if the view is of {@link Checkable} instance. False otherwise. */
    public static boolean isCheckable(View fromView) {
        LayoutlibCallback callback = sLayoutlibCallback;
        if (callback == null) {
            return false;
        }

        try {
            // This is required as layoutlib does not know the support library such as
            // MaterialButton. LayoutlibCallback calls for studio which understands all the maven
            // pulled library.
            Class<?> button = callback.findClass(
                    "com.google.android.material.button.MaterialButton");
            if (button.isInstance(fromView)) {
                Method isCheckable = button.getMethod("isCheckable");
                Object toReturn = isCheckable.invoke(fromView);
                return (toReturn instanceof Boolean) && ((Boolean) toReturn);
            }

            Class<?> card = callback.findClass(
                    "com.google.android.material.card.MaterialCardView");
            if (card.isInstance(fromView)) {
                Method isCheckable = card.getMethod("isCheckable");
                Object toReturn = isCheckable.invoke(fromView);
                return (toReturn instanceof Boolean) && ((Boolean) toReturn);
            }

            Class<?> chip = callback.findClass(
                    "com.google.android.material.chip.Chip");
            if (chip.isInstance(fromView)) {
                Method isCheckable = chip.getMethod("isCheckable");
                Object toReturn = isCheckable.invoke(fromView);
                return (toReturn instanceof Boolean) && ((Boolean) toReturn);
            }
        } catch (ClassNotFoundException |
                 NoSuchMethodException |
                 IllegalAccessException |
                 InvocationTargetException ignore) {
        }
        return fromView instanceof Checkable;
    }
}
