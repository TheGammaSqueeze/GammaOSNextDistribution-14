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

package com.android.networkstack.apishim.api33;

import static com.android.modules.utils.build.SdkLevel.isAtLeastT;

import android.content.Context;
import android.net.VpnProfileState;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.apishim.common.VpnManagerShim;
import com.android.networkstack.apishim.common.VpnProfileStateShim;

/**
 * Compatibility implementation of {@link VpnManagerShim}.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class VpnManagerShimImpl extends com.android.networkstack.apishim.api31.VpnManagerShimImpl {
    protected VpnManagerShimImpl(Context context) {
        super(context);
    }

    /**
     * Get a new instance of {@link VpnManagerShimImpl}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static VpnManagerShim newInstance(Context context) throws UnsupportedApiLevelException {
        if (!isAtLeastT()) {
            return com.android.networkstack.apishim.api31.VpnManagerShimImpl.newInstance(context);
        }
        return new VpnManagerShimImpl(context);
    }

    /**
     * See android.net.VpnManager#startProvisionedVpnProfileSession
     */
    @Override
    public String startProvisionedVpnProfileSession() {
        return mVm.startProvisionedVpnProfileSession();
    }

    /**
     * See android.net.VpnManager#getProvisionedVpnProfileState
     */
    @Override
    @Nullable
    public VpnProfileStateShim getProvisionedVpnProfileState() {
        final VpnProfileState profileState = mVm.getProvisionedVpnProfileState();
        return (profileState == null) ? null : new VpnProfileStateShimImpl(profileState);
    }
}
