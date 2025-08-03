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

package android.rootlessgpudebug.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.lang.Override;


public class RootlessGpuDebugService extends Service {

    private static final String TAG = RootlessGpuDebugService.class.getSimpleName();

    static {
        System.loadLibrary("ctsgputools_jni");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // The target API is provided via Intent extras
        String API = null;

        if (intent == null) {
            throw new AssertionError("No Intent provided to " + TAG);
        }

        Bundle bundle = intent.getExtras();

        // Ensure the service was started with extras
        if (bundle == null) {
            throw new AssertionError("Failed to get Intent extras for " + TAG);
        }

        API = bundle.getString("API");

        // Without an API to target, we're done
        if (API == null) {
            throw new AssertionError("No API provided for " + TAG);
        }

        // Only three combinations are expected
        Boolean supportedApi = API.equals("Vulkan") ||
                               API.equals("GLES") ||
                               API.equals("Both");
        if (!supportedApi) {
            throw new AssertionError("Unsupported API " + API + " in " + TAG);
        }

        // For each choice, init the target API
        if (API.equals("Vulkan") || API.equals("Both")) {
            String result = nativeInitVulkan();
            Log.i(TAG, result);
        }

        if (API.equals("GLES") || API.equals("Both")) {
            String result = nativeInitGLES();
            Log.i(TAG, result);
        }

        // Mark service completion
        Log.i(TAG, "RootlessGpuDebugService complete");

        // Don't try to restart when this is shut down
        return START_NOT_STICKY;
    }

    private static native String nativeInitVulkan();
    private static native String nativeInitGLES();
}

