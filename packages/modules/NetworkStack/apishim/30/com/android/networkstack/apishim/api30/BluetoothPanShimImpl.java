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

import android.bluetooth.BluetoothPan;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.BluetoothPanShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link BluetoothPanShimImpl} for API 30.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class BluetoothPanShimImpl implements BluetoothPanShim {
    protected final BluetoothPan mPan;
    protected BluetoothPanShimImpl(BluetoothPan pan) {
        mPan = pan;
    }

    /**
     * Get a new instance of {@link BluetoothPanShimImpl}.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public static BluetoothPanShim newInstance(final BluetoothPan pan) {
        return new BluetoothPanShimImpl(pan);
    }

    @Override
    public TetheredInterfaceRequestShim requestTetheredInterface(@NonNull final Executor executor,
            @NonNull final TetheredInterfaceCallbackShim callback)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException(
                "requestTetheredInterface does not exist before API 32");
    }
}
