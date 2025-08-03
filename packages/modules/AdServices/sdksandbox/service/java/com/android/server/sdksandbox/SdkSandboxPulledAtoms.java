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

package com.android.server.sdksandbox;

import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_SDK_STORAGE;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.buildStatsEvent;
import static com.android.server.sdksandbox.SandboxesStorageMetrics.StorageStatsEvent;

import android.app.StatsManager;
import android.content.Context;
import android.util.Log;
import android.util.StatsEvent;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.BackgroundThread;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to initialize and log metrics which will be pulled by StatsD
 *
 * @hide
 */
public class SdkSandboxPulledAtoms {

    private static final String TAG = "SdkSandboxManager";
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mInitialized = false;

    private final SandboxesStorageMetrics mSandboxesStorageMetrics = new SandboxesStorageMetrics();

    /** Initializes the callback which will be called from StatsD */
    public void initialize(Context context) {
        synchronized (mLock) {
            if (mInitialized) {
                return;
            }
            final StatsManager statsManager = context.getSystemService(StatsManager.class);
            if (statsManager == null) {
                Log.e(TAG, "Error retrieving StatsManager. Cannot initialize PulledMetrics.");
                return;
            }
            Log.d(TAG, "Registering callback with StatsManager");

            try {
                // Callback handler for registering for SDK storage atom
                statsManager.setPullAtomCallback(
                        SANDBOX_SDK_STORAGE,
                        /*metadata=*/ null,
                        BackgroundThread.getExecutor(),
                        /**
                         * Class which implements the callback method which will be called by StatsD
                         */
                        (atomTag, data) -> {
                            if (atomTag != SANDBOX_SDK_STORAGE) {
                                Log.e(TAG, "Incorrect atomTag for SandboxSdkStorage");
                                return StatsManager.PULL_SKIP;
                            }

                            final List<StatsEvent> events = new ArrayList<>();
                            for (StorageStatsEvent sandboxStorageStatsEvent :
                                    mSandboxesStorageMetrics.consumeStorageStatsEvents()) {
                                events.add(
                                        buildStatsEvent(
                                                SANDBOX_SDK_STORAGE,
                                                sandboxStorageStatsEvent.mShared,
                                                sandboxStorageStatsEvent.mStorageKb,
                                                sandboxStorageStatsEvent.mUid));
                            }
                            if (events == null) {
                                return StatsManager.PULL_SKIP;
                            }

                            data.addAll(events);
                            return StatsManager.PULL_SUCCESS;
                        });
                mInitialized = true;
            } catch (NullPointerException e) {
                Log.w(TAG, "Pulled metrics not supported. Could not register.", e);
            }
        }
    }

    /**
     * Logs the storage information of SDKs in memory which will later be pulled by StatsD callback
     */
    public void logStorage(int uid, int sharedStorageKb, int sdkStorageKb) {
        mSandboxesStorageMetrics.log(uid, sharedStorageKb, sdkStorageKb);
    }
}
