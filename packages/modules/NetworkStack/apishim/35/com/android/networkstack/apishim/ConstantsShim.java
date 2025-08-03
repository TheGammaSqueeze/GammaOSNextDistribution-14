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

import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.build.SdkLevel;

/**
 * Utility class for defining and importing constants from the Android platform.
 */
// TODO: when available in all active branches: @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@RequiresApi(Build.VERSION_CODES.CUR_DEVELOPMENT)
public class ConstantsShim extends com.android.networkstack.apishim.api34.ConstantsShim {
    /**
     * Constant that callers can use to determine what version of the shim they are using.
     * Must be the same as the version of the shims.
     * This should only be used by test code. Production code that uses the shims should be using
     * the shimmed objects and methods themselves.
     */
    @VisibleForTesting
    public static final int VERSION = 35;

    // When building against the latest shims but running on U (for example building from main
    // and running mainline tests), this shim class will be used. The linter wouldn't be happy
    // about the newer constant being used without a SDK check.
    public static final String REGISTER_NSD_OFFLOAD_ENGINE =
            SdkLevel.isAtLeastV() ? android.Manifest.permission.REGISTER_NSD_OFFLOAD_ENGINE
                 : com.android.networkstack.apishim.api34.ConstantsShim.REGISTER_NSD_OFFLOAD_ENGINE;
}
