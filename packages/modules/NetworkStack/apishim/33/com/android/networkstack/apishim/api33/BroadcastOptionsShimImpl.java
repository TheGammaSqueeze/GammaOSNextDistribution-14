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

import android.app.BroadcastOptions;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.BroadcastOptionsShim;

/**
 * Compatibility implementation of {@link BroadcastOptionsShim}.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class BroadcastOptionsShimImpl
        extends com.android.networkstack.apishim.api29.BroadcastOptionsShimImpl {
    protected BroadcastOptionsShimImpl(@NonNull BroadcastOptions options) {
        super(options);
    }

    /**
     * Get a new instance of {@link BroadcastOptionsShimImpl}.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static BroadcastOptionsShim newInstance(@NonNull BroadcastOptions options) {
        if (!isAtLeastT()) {
            return com.android.networkstack.apishim.api29.BroadcastOptionsShimImpl.newInstance(
                    options);
        }
        return new BroadcastOptionsShimImpl(options);
    }
}
