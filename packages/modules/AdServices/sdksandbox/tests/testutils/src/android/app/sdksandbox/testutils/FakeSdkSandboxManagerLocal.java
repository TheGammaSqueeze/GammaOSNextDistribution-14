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

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.os.IBinder;

import androidx.annotation.NonNull;

import com.android.server.sdksandbox.SdkSandboxManagerLocal;

public class FakeSdkSandboxManagerLocal implements SdkSandboxManagerLocal {

    private boolean mInstrumentationRunning = false;

    @Override
    public void enforceAllowedToSendBroadcast(@NonNull Intent intent) {}

    @Override
    public boolean canSendBroadcast(@NonNull Intent intent) {
        return false;
    }

    @Override
    public void enforceAllowedToStartActivity(@NonNull Intent intent) {}

    @Override
    public void enforceAllowedToStartOrBindService(@NonNull Intent intent) {}

    @NonNull
    @Override
    public String getSdkSandboxProcessNameForInstrumentation(
            @NonNull ApplicationInfo clientAppInfo) {
        return clientAppInfo.processName + "_sdk_sandbox_instr";
    }

    @NonNull
    @Override
    public ApplicationInfo getSdkSandboxApplicationInfoForInstrumentation(
            @NonNull ApplicationInfo clientAppInfo, boolean isSdkInSandbox) {
        clientAppInfo.processName = getSdkSandboxProcessNameForInstrumentation(clientAppInfo);
        return clientAppInfo;
    }

    @Override
    public void notifyInstrumentationStarted(
            @NonNull String clientAppPackageName, int clientAppUid) {
        mInstrumentationRunning = true;
    }

    @Override
    public void notifyInstrumentationFinished(
            @NonNull String clientAppPackageName, int clientAppUid) {
        mInstrumentationRunning = false;
    }

    @Override
    public boolean isInstrumentationRunning(
            @NonNull String clientAppPackageName, int clientAppUid) {
        return mInstrumentationRunning;
    }

    @Override
    public void registerAdServicesManagerService(IBinder iBinder, boolean published) {}

    @Override
    public boolean canRegisterBroadcastReceiver(
            @NonNull IntentFilter intentFilter, int flags, boolean onlyProtectedBroadcasts) {
        return true;
    }

    @Override
    public boolean canAccessContentProviderFromSdkSandbox(@NonNull ProviderInfo providerInfo) {
        return true;
    }

    @Override
    public void enforceAllowedToHostSandboxedActivity(
            @NonNull Intent intent, int clientAppUid, @NonNull String clientAppPackageName) {}

    @Override
    public int getEffectiveTargetSdkVersion(int sdkSandboxUid) throws NameNotFoundException {
        return 0;
    }
}
