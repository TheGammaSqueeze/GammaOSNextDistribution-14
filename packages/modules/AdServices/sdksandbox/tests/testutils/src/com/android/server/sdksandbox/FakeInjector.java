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

package com.android.server.sdksandbox;

import android.content.Context;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.List;

public class FakeInjector extends SdkSandboxManagerService.Injector {
    private SdkSandboxStorageManager mSdkSandboxStorageManager = null;
    private SdkSandboxServiceProvider mSdkSandboxServiceProvider = null;
    private SdkSandboxPulledAtoms mSdkSandboxPulledAtoms = null;
    private SdkSandboxStatsdLogger mSdkSandboxStatsdLogger = null;
    private ArrayDeque<Long> mLatencyTimeSeries = new ArrayDeque<>();

    FakeInjector(
            Context context,
            SdkSandboxStorageManager sdkSandboxStorageManager,
            SdkSandboxServiceProvider sdkSandboxServiceProvider,
            SdkSandboxPulledAtoms sdkSandboxPulledAtoms,
            SdkSandboxStatsdLogger sdkSandboxStatsdLogger) {
        super(context);
        mSdkSandboxStorageManager = sdkSandboxStorageManager;
        mSdkSandboxServiceProvider = sdkSandboxServiceProvider;
        mSdkSandboxPulledAtoms = sdkSandboxPulledAtoms;
        mSdkSandboxStatsdLogger = sdkSandboxStatsdLogger;
    }

    public FakeInjector(Context spyContext) {
        super(spyContext);
    }

    @Override
    public SdkSandboxServiceProvider getSdkSandboxServiceProvider() {
        return mSdkSandboxServiceProvider;
    }

    @Override
    public SdkSandboxPulledAtoms getSdkSandboxPulledAtoms() {
        return mSdkSandboxPulledAtoms;
    }

    @Override
    public SdkSandboxStorageManager getSdkSandboxStorageManager() {
        return mSdkSandboxStorageManager;
    }

    @Override
    public SdkSandboxStatsdLogger getSdkSandboxStatsdLogger() {
        return mSdkSandboxStatsdLogger;
    }

    @Override
    public long elapsedRealtime() {
        if (mLatencyTimeSeries.isEmpty()) {
            return SystemClock.elapsedRealtime();
        }

        return mLatencyTimeSeries.poll();
    }

    void setLatencyTimeSeries(List<Long> latencyTimeSeries) {
        mLatencyTimeSeries = new ArrayDeque<>(latencyTimeSeries);
    }

    void resetTimeSeries() {
        mLatencyTimeSeries.clear();
    }
}
