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

package com.android.networkstack.apishim.api31;

import android.bluetooth.BluetoothPan;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * Implementation of {@link com.android.networkstack.apishim.common.BluetoothPanShim}.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class BluetoothPanShimImpl extends
        com.android.networkstack.apishim.api30.BluetoothPanShimImpl {
    protected BluetoothPanShimImpl(BluetoothPan pan) {
        super(pan);
    }
}
