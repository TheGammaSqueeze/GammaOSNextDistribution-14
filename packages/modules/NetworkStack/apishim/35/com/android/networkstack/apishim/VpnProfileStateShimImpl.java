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
package com.android.networkstack.apishim;

import android.net.VpnProfileState;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.VpnProfileStateShim;

/** Implementation of {@link VpnProfileStateShim} for API 35. */
// TODO: when available in all active branches: @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@RequiresApi(Build.VERSION_CODES.CUR_DEVELOPMENT)
public class VpnProfileStateShimImpl
        extends com.android.networkstack.apishim.api34.VpnProfileStateShimImpl {
    // Currently identical to the API 34 shim, so inherit everything
    protected VpnProfileStateShimImpl(@NonNull VpnProfileState profileState) {
        super(profileState);
    }
}
