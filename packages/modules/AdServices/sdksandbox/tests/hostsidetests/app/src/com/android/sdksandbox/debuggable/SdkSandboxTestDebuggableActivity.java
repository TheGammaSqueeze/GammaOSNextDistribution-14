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

package com.android.sdksandbox.debuggable;

import android.app.Activity;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.os.Bundle;

public class SdkSandboxTestDebuggableActivity extends Activity {

    private static final String SDK_NAME = "com.android.testcode";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        SdkSandboxManager sdkSandboxManager =
                getApplicationContext().getSystemService(SdkSandboxManager.class);

        Bundle params = new Bundle();
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        assert sdkSandboxManager != null;
        sdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
    }
}
