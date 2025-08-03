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

package com.android.adservices.service.common;

import android.os.Process;

import com.android.adservices.service.common.compat.ProcessCompatUtils;

/**
 * Utility class to deal with the fact that PPAPI can be called from an app or from the SDK Sandbox.
 */
public class SdkRuntimeUtil {
    /**
     * Utility method to retrieve the UID of the application a PPAPI call has been made for. If the
     * call has been done using the SDK Sandbox this method will return the UID of the originating
     * app otherwise it will return {@code callerProcessUid}.
     *
     * @param callerProcessUid The UID of the calling process
     * @return the UID of the Application this call has ben made for
     */
    public static int getCallingAppUid(int callerProcessUid) {
        if (ProcessCompatUtils.isSdkSandboxUid(callerProcessUid)) {
            return Process.getAppUidForSdkSandboxUid(callerProcessUid);
        } else {
            return callerProcessUid;
        }
    }
}
