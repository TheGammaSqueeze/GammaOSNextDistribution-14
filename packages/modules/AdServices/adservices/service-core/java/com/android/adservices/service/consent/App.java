/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.consent;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;

import com.android.adservices.service.common.compat.PackageManagerCompatUtils;

/**
 * POJO Represents a App.
 *
 * @hide
 */
public class App {

    private final String mPackageName;

    App(String packageName) {
        this.mPackageName = packageName;
    }

    /** Creates an instance of an App. */
    @NonNull
    public static App create(String packageName) {
        return new App(packageName);
    }

    /** Returns a String represents the app identifier (i.e. packageName). */
    public String getPackageName() {
        return mPackageName;
    }

    /** @return an application name using provided {@link PackageManager}. */
    public String getAppDisplayName(@NonNull PackageManager packageManager) {
        ApplicationInfo ai;
        try {
            ai = PackageManagerCompatUtils.getApplicationInfo(packageManager, getPackageName(), 0);
        } catch (NameNotFoundException e) {
            return "";
        }
        return packageManager.getApplicationLabel(ai).toString();
    }

    /**
     * @return an application icon using provided {@link PackageManager} or null if operation
     *     failed.
     */
    public Drawable getAppIcon(@NonNull Context context) {
        try {
            return context.getPackageManager().getApplicationIcon(getPackageName());
        } catch (Exception e) {
            return null;
        }
    }
}
