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

package com.android.server;

import android.annotation.RequiresApi;
import android.os.IBinder;
import android.os.Build;
import android.os.ServiceManager;

/** Provides a way to access {@link ServiceManager#waitForService} API. */
@RequiresApi(Build.VERSION_CODES.S)
public final class ServiceManagerWrapper {
    static {
        System.loadLibrary("service-connectivity");
    }

    private ServiceManagerWrapper() {}

    /**
     * Returns the specified service from the service manager.
     *
     * If the service is not running, service manager will attempt to start it, and this function
     * will wait for it to be ready.
     *
     * @return {@code null} only if there are permission problems or fatal errors
     */
    public static IBinder waitForService(String serviceName) {
        return nativeWaitForService(serviceName);
    }

    private static native IBinder nativeWaitForService(String serviceName);
}
