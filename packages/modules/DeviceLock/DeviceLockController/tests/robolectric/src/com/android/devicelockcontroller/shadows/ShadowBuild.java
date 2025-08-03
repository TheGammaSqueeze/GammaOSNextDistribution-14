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

package com.android.devicelockcontroller.shadows;

import android.os.Build;

import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

/**
 * Shadow class which extends {@link org.robolectric.shadows.ShadowBuild}
 */
@Implements(value = Build.class)
public class ShadowBuild extends org.robolectric.shadows.ShadowBuild {

    /**
     * Sets the value of the {@link Build#IS_DEBUGGABLE} field.
     *
     * It will be reset for the next test.
     */
    public static void setIsDebuggable(boolean isDebuggable) {
        ReflectionHelpers.setStaticField(Build.class, "IS_DEBUGGABLE", isDebuggable);
    }
}
