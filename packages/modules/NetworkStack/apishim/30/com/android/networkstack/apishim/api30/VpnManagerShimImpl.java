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

package com.android.networkstack.apishim.api30;

import static com.android.modules.utils.build.SdkLevel.isAtLeastR;

import android.content.Context;
import android.net.VpnManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.apishim.common.VpnManagerShim;

/**
 * Implementation of {@link VpnManagerShim} for API 30.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class VpnManagerShimImpl extends com.android.networkstack.apishim.api29.VpnManagerShimImpl {
    protected final VpnManager mVm;
    protected VpnManagerShimImpl(Context context) {
        super(context);
        mVm = context.getSystemService(VpnManager.class);
    }

    /**
     * Get a new instance of {@link VpnManagerShimImpl}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static VpnManagerShim newInstance(Context context) throws UnsupportedApiLevelException {
        if (!isAtLeastR()) {
            return com.android.networkstack.apishim.api29.VpnManagerShimImpl.newInstance(context);
        }
        return new VpnManagerShimImpl(context);
    }
}
