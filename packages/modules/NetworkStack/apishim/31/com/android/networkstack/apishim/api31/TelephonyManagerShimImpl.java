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

package com.android.networkstack.apishim.api31;

import static com.android.modules.utils.build.SdkLevel.isAtLeastS;

import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.TelephonyManagerShim;

/**
 * Implementation of {@link TelephonyManagerShim} for API 31.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class TelephonyManagerShimImpl
        extends com.android.networkstack.apishim.api29.TelephonyManagerShimImpl {
    protected TelephonyManagerShimImpl(TelephonyManager telephonyManager) {
        super(telephonyManager);
    }

    /** Get a new instance of {@link TelephonyManagerShim}. */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static TelephonyManagerShim newInstance(TelephonyManager tm) {
        if (!isAtLeastS()) {
            return com.android.networkstack.apishim.api29.TelephonyManagerShimImpl.newInstance(tm);
        }
        return new TelephonyManagerShimImpl(tm);
    }
}
