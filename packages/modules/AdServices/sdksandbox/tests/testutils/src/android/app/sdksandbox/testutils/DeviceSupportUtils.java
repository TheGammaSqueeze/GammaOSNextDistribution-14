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

package android.app.sdksandbox.testutils;

import android.content.Context;


import com.android.adservices.common.AdServicesSupportHelper;

// TODO(b/284971005): remove once all callers use the rule
/** Utility class to control which devices SDK sandbox tests run on. */
public final class DeviceSupportUtils {

    /**
     * @deprecated - use {@link SdkSandboxDeviceSupportedRule} instead
     */
    @Deprecated
    public static boolean isSdkSandboxSupported(Context context) {
        return AdServicesSupportHelper.getInstance().isDeviceSupported();
    }
}
