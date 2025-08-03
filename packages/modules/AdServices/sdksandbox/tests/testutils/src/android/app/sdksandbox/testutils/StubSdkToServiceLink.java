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

package android.app.sdksandbox.testutils;

import android.annotation.NonNull;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;
import android.os.Binder;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class StubSdkToServiceLink extends ISdkToServiceCallback.Stub {

    @Override
    @NonNull
    public List<AppOwnedSdkSandboxInterface> getAppOwnedSdkSandboxInterfaces(String clientName) {
        return new ArrayList<>();
    }

    @Override
    @NonNull
    public List<SandboxedSdk> getSandboxedSdks(String clientName) {
        ArrayList<SandboxedSdk> list = new ArrayList<>();
        SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        list.add(sandboxedSdk);
        return list;
    }

    @Override
    public void loadSdk(
            String callingPackageName,
            String sdkName,
            SandboxLatencyInfo sandboxLatencyInfo,
            Bundle params,
            ILoadSdkCallback callback) {
        return;
    }

    @Override
    public void logLatenciesFromSandbox(SandboxLatencyInfo sandboxLatencyInfo) {}

    @Override
    public void logSandboxActivityApiLatencyFromSandbox(
            int method, int callResult, int latencyMillis) {}
}
