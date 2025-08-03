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

package com.android.networkstack.apishim.api34;

import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

/**
 * Utility class for defining and importing constants from the Android platform.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ConstantsShim extends com.android.networkstack.apishim.api33.ConstantsShim {
    /**
     * Constant that callers can use to determine what version of the shim they are using.
     * Must be the same as the version of the shims.
     * This should only be used by test code. Production code that uses the shims should be using
     * the shimmed objects and methods themselves.
     */
    @VisibleForTesting
    public static final int VERSION = 34;

    // Constants defined in android.content.pm.PackageManager
    public static final String PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES =
            PackageManager.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES;
}
