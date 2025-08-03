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

package com.android.tv.settings.device.apps;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import com.android.settingslib.Utils;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

@Implements(Utils.class)
public class ShadowUtils {
    private static boolean sSystemPackage = false;

    public static void setSystemPackage(boolean value) {
        sSystemPackage = value;
    }

    @Resetter
    public static synchronized void reset() {
        sSystemPackage = false;
    }

    @Implementation
    public static boolean isSystemPackage(Resources resources, PackageManager pm, PackageInfo pkg) {
        return sSystemPackage;
    }
}
