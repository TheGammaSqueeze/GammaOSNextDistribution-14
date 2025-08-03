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

package com.android.networkstack.apishim.api33;

import androidx.annotation.VisibleForTesting;

/**
 * Utility class for defining and importing constants from the Android platform.
 */
public class ConstantsShim extends com.android.networkstack.apishim.api31.ConstantsShim {
    /**
     * Constant that callers can use to determine what version of the shim they are using.
     * Must be the same as the version of the shims.
     * This should only be used by test code. Production code that uses the shims should be using
     * the shimmed objects and methods themselves.
     */
    @VisibleForTesting
    public static final int VERSION = 33;

    // Constant defined in android.app.BroadcastOptions.
    public static final int DELIVERY_GROUP_POLICY_ALL = 0;
    // Constant defined in android.app.BroadcastOptions.
    public static final int DELIVERY_GROUP_POLICY_MOST_RECENT = 1;

    // Constant defined in android.app.BroadcastOptions.
    public static final int DEFERRAL_POLICY_DEFAULT = 0;
    // Constant defined in android.app.BroadcastOptions.
    public static final int DEFERRAL_POLICY_NONE = 1;
    // Constant defined in android.app.BroadcastOptions.
    public static final int DEFERRAL_POLICY_UNTIL_ACTIVE = 2;
    // Const defined in  android.Manifest.permission
    public static final String REGISTER_NSD_OFFLOAD_ENGINE =
            "android.permission.REGISTER_NSD_OFFLOAD_ENGINE";
}
