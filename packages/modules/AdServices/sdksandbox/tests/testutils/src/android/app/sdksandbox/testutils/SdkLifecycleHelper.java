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

import android.app.sdksandbox.SdkSandboxManager;
import android.content.Context;

public class SdkLifecycleHelper {

    private static final int WAIT_FOR_UNLOAD_MS = 5000;
    private final SdkSandboxManager mSdkSandboxManager;

    public SdkLifecycleHelper(Context context) {
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
    }

    /**
     * Unload sdk helper method.
     *
     * <p>Unloads the SDK. If the SDK being unloaded is still being loaded, we wait for loading to
     * be complete before unloading it.
     */
    public void unloadSdk(String sdkName) {
        int waitIncrementMs = 100;
        for (int wait = 0; wait <= WAIT_FOR_UNLOAD_MS; wait += waitIncrementMs) {
            try {
                mSdkSandboxManager.unloadSdk(sdkName);
                return;
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("is currently being loaded for ")) {
                    // Wait till SDK is loaded
                    try {
                        Thread.sleep(waitIncrementMs);
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }
                    continue;
                }
                throw e;
            }
        }
    }
}
