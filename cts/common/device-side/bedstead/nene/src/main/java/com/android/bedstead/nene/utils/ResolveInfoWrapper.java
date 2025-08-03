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

package com.android.bedstead.nene.utils;

import android.content.pm.ActivityInfo;

/** Wrapper class for {@link android.content.pm.ResolveInfo} */
public final class ResolveInfoWrapper {
    private final ActivityInfo mActivityInfo;
    private final int mMatch;

    public ResolveInfoWrapper(ActivityInfo activityInfo, int match) {
        mActivityInfo = activityInfo;
        mMatch = match;
    }

    public ActivityInfo activityInfo() {
        return mActivityInfo;
    }

    public int match() {
        return mMatch;
    }
}
