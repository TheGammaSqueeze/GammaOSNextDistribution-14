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

package com.android.adservices.service.topics;

import android.annotation.NonNull;

import java.util.Objects;

/**
 * POJO Represents an AppInfo.
 */
public final class AppInfo {

    private final String mAppName;
    private final String mAppDescription;

    public AppInfo(@NonNull String appName, @NonNull String appDescription) {
        Objects.requireNonNull(appName);
        Objects.requireNonNull(appDescription);
        mAppName = appName;
        mAppDescription = appDescription;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AppInfo)) {
            return false;
        }
        AppInfo appInfo = (AppInfo) object;
        return mAppName.equals(appInfo.mAppName) && mAppDescription.equals(appInfo.mAppDescription);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), mAppName, mAppDescription);
    }

    /**
     * Gets the appName.
     */
    @NonNull
    public String getAppName() {
        return mAppName;
    }

    /**
     * Gets the appDescription.
     */
    @NonNull
    public String getAppDescription() {
        return mAppDescription;
    }

}
