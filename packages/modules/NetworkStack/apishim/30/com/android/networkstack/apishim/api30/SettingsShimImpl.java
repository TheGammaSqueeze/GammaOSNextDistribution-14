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

package com.android.networkstack.apishim.api30;

import static com.android.modules.utils.build.SdkLevel.isAtLeastR;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.SettingsShim;

/**
 * Implementation of {@link SettingsShim} for API 30.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class SettingsShimImpl extends com.android.networkstack.apishim.api29.SettingsShimImpl {
    protected SettingsShimImpl() { }

    /**
     * Get a new instance of {@link SettingsShim}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static SettingsShim newInstance() {
        if (!isAtLeastR()) {
            return com.android.networkstack.apishim.api29.SettingsShimImpl.newInstance();
        }
        return new SettingsShimImpl();
    }

    @Override
    public boolean checkAndNoteWriteSettingsOperation(@NonNull Context context, int uid,
            @NonNull String callingPackage, @Nullable String callingAttributionTag,
            boolean throwException) {
        return Settings.checkAndNoteWriteSettingsOperation(context, uid, callingPackage,
                throwException);
    }
}
