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

package com.android.server;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Build;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.server.net.NetworkStatsService;

/**
 * NetworkStats service initializer for core networking. This is called by system server to create
 * a new instance of NetworkStatsService.
 */
public final class NetworkStatsServiceInitializer extends SystemService {
    private static final String TAG = NetworkStatsServiceInitializer.class.getSimpleName();
    private static final String ENABLE_NETWORK_TRACING = "enable_network_tracing";
    private final boolean mNetworkTracingFlagEnabled;
    private final NetworkStatsService mStatsService;

    public NetworkStatsServiceInitializer(Context context) {
        super(context);
        // Load JNI libraries used by NetworkStatsService and its dependencies
        System.loadLibrary("service-connectivity");
        mStatsService = maybeCreateNetworkStatsService(context);
        mNetworkTracingFlagEnabled = DeviceConfigUtils.isTetheringFeatureEnabled(
            context, ENABLE_NETWORK_TRACING);
    }

    @Override
    public void onStart() {
        if (mStatsService != null) {
            Log.i(TAG, "Registering " + Context.NETWORK_STATS_SERVICE);
            publishBinderService(Context.NETWORK_STATS_SERVICE, mStatsService,
                    /* allowIsolated= */ false);
            TrafficStats.init(getContext());
        }

        // The following code registers the Perfetto Network Trace Handler. The enhanced tracing
        // is intended to be used for debugging and diagnosing issues. This is enabled by default
        // on userdebug/eng builds and flag protected in user builds.
        if (SdkLevel.isAtLeastU() && (mNetworkTracingFlagEnabled || !Build.TYPE.equals("user"))) {
            Log.i(TAG, "Initializing network tracing hooks");
            NetworkStatsService.nativeInitNetworkTracing();
        }
    }

    @Override
    public void onBootPhase(int phase) {
        // This has to be run before StatsPullAtomService query usage at
        // PHASE_THIRD_PARTY_APPS_CAN_START.
        if (phase == SystemService.PHASE_ACTIVITY_MANAGER_READY && mStatsService != null) {
            mStatsService.systemReady();
        }
    }

    /**
     * Return NetworkStatsService instance, or null if current SDK is lower than T.
     */
    private NetworkStatsService maybeCreateNetworkStatsService(final Context context) {
        if (!SdkLevel.isAtLeastT()) return null;

        return NetworkStatsService.create(context);
    }
}
