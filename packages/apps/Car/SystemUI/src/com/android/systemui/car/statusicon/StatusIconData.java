/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.NonNull;
import android.graphics.drawable.Drawable;

/**
 * Data that pertains to a particular status icon.
 */
public class StatusIconData {
    @NonNull
    private Drawable mIconDrawable;
    @NonNull
    private String mContentDescription = "";
    private boolean mIsIconVisible = true;

    protected Drawable getIconDrawable() {
        return mIconDrawable;
    }

    protected void setIconDrawable(Drawable drawable) {
        mIconDrawable = drawable;
    }

    protected String getContentDescription() {
        return mContentDescription;
    }

    protected void setContentDescription(String str) {
        mContentDescription = str;
    }

    protected boolean getIsIconVisible() {
        return mIsIconVisible;
    }

    protected void setIsIconVisible(boolean isVisible) {
        mIsIconVisible = isVisible;
    }
}
