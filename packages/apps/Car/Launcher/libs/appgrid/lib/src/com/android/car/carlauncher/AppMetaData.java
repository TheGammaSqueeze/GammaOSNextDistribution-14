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

package com.android.car.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.function.Consumer;

/**
 * Meta data of an app including the display name, the component name, the icon drawable, and an
 * intent to either open the app or the media center (for media services).
 */

public final class AppMetaData {
    // The display name of the app
    @Nullable
    private final String mDisplayName;
    // The component name of the app
    private final ComponentName mComponentName;
    private final Drawable mIcon;
    private final boolean mIsDistractionOptimized;
    private final boolean mIsMirroring;
    private final boolean mIsDisabledByTos;
    private final Consumer<Context> mLaunchCallback;
    private final Consumer<Pair<Context, View>> mAlternateLaunchCallback;

    /**
     * AppMetaData
     *
     * @param displayName            the name to display in the launcher
     * @param componentName          the component name
     * @param icon                   the application's icon
     * @param isDistractionOptimized whether mainLaunchIntent is safe for driving
     * @param isDisabledByTos        whether app is disabled by tos
     * @param launchCallback         action to execute to launch this app
     * @param alternateLaunchCallback  temporary alternative action to execute (e.g.: for media apps
     *                               this allows opening their own UI).
     */
    public AppMetaData(
            CharSequence displayName,
            ComponentName componentName,
            Drawable icon,
            boolean isDistractionOptimized,
            boolean isMirroring,
            boolean isDisabledByTos,
            Consumer<Context> launchCallback,
            Consumer<Pair<Context, View>> alternateLaunchCallback) {
        mDisplayName = displayName == null ? "" : displayName.toString();
        mComponentName = componentName;
        mIcon = icon;
        mIsDistractionOptimized = isDistractionOptimized;
        mIsMirroring = isMirroring;
        mIsDisabledByTos = isDisabledByTos;
        mLaunchCallback = launchCallback;
        mAlternateLaunchCallback = alternateLaunchCallback;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getClassName() {
        return getComponentName().getClassName();
    }

    public String getPackageName() {
        return getComponentName().getPackageName();
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Consumer<Context> getLaunchCallback() {
        return mLaunchCallback;
    }

    public Consumer<Pair<Context, View>> getAlternateLaunchCallback() {
        return mAlternateLaunchCallback;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public boolean getIsDistractionOptimized() {
        return mIsDistractionOptimized;
    }

    public boolean getIsDisabledByTos() {
        return mIsDisabledByTos;
    }

    public boolean getIsMirroring() {
        return mIsMirroring;
    }

    /**
     * The equality of two AppMetaData is determined by whether the component names are the same.
     *
     * @param o Object that this AppMetaData object is compared against
     * @return {@code true} when two AppMetaData have the same component name
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AppMetaData)) {
            return false;
        } else {
            return ((AppMetaData) o).getComponentName().equals(mComponentName)
                    && ((AppMetaData) o).getIsMirroring() == mIsMirroring;
        }
    }

    @Override
    public int hashCode() {
        return mComponentName.hashCode();
    }
}
