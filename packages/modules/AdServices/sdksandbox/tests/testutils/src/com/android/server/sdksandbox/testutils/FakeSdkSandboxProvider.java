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

package com.android.server.sdksandbox.testutils;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.util.ArrayMap;

import com.android.sdksandbox.ISdkSandboxService;
import com.android.server.sdksandbox.CallingInfo;
import com.android.server.sdksandbox.SdkSandboxServiceProvider;

import org.mockito.Mockito;

import java.io.PrintWriter;

/** Fake service provider that returns local instance of {@link SdkSandboxServiceProvider} */
public class FakeSdkSandboxProvider implements SdkSandboxServiceProvider {
    private FakeSdkSandboxService mSdkSandboxService;
    private final ArrayMap<CallingInfo, ISdkSandboxService> mService = new ArrayMap<>();
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";

    public static final String FAKE_DUMP_OUTPUT = "FakeDump";

    // When set to true, this will fail the bindService call
    private boolean mFailBinding = false;

    private ServiceConnection mServiceConnection = null;

    public FakeSdkSandboxProvider(FakeSdkSandboxService service) {
        mSdkSandboxService = service;
    }

    /** Disable the binding */
    public void disableBinding() {
        mFailBinding = true;
    }

    /** Restart the sandbox process */
    public FakeSdkSandboxService restartSandbox() {
        mServiceConnection.onServiceDisconnected(null);

        // Create a new sandbox service.
        mSdkSandboxService = Mockito.spy(FakeSdkSandboxService.class);

        // Call onServiceConnected() again with the new fake sandbox service.
        mServiceConnection.onServiceConnected(null, mSdkSandboxService.asBinder());
        return mSdkSandboxService;
    }

    @Override
    public void bindService(CallingInfo callingInfo, ServiceConnection serviceConnection) {
        if (mFailBinding) {
            serviceConnection.onNullBinding(new ComponentName("random", "component"));
            return;
        }

        if (mService.containsKey(callingInfo)) {
            return;
        }
        mService.put(callingInfo, mSdkSandboxService);
        serviceConnection.onServiceConnected(null, mSdkSandboxService.asBinder());
        mServiceConnection = serviceConnection;
    }

    @Override
    public void unbindService(CallingInfo callingInfo) {
        mService.remove(callingInfo);
    }

    @Override
    public void stopSandboxService(CallingInfo callingInfo) {
        mService.remove(callingInfo);
    }

    @Nullable
    @Override
    public ISdkSandboxService getSdkSandboxServiceForApp(CallingInfo callingInfo) {
        return mService.get(callingInfo);
    }

    @Override
    public void onServiceConnected(CallingInfo callingInfo, @NonNull ISdkSandboxService service) {
        mService.put(callingInfo, service);
    }

    @Override
    public void onServiceDisconnected(CallingInfo callingInfo) {
        mService.put(callingInfo, null);
    }

    @Override
    public void onAppDeath(CallingInfo callingInfo) {}

    @Override
    public void onSandboxDeath(CallingInfo callingInfo) {}

    @Override
    public boolean isSandboxBoundForApp(CallingInfo callingInfo) {
        return false;
    }

    @Override
    public int getSandboxStatusForApp(CallingInfo callingInfo) {
        if (mService.containsKey(callingInfo)) {
            return CREATED;
        } else {
            return NON_EXISTENT;
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println(FAKE_DUMP_OUTPUT);
    }

    @NonNull
    @Override
    public String toSandboxProcessName(@NonNull CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException {
        return TEST_PACKAGE + SANDBOX_PROCESS_NAME_SUFFIX;
    }

    @NonNull
    @Override
    public String toSandboxProcessNameForInstrumentation(@NonNull CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException {
        return TEST_PACKAGE + SANDBOX_INSTR_PROCESS_NAME_SUFFIX;
    }
}
