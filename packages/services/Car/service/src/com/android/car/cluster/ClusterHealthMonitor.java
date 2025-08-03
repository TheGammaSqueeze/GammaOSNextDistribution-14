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

package com.android.car.cluster;

import static com.android.car.internal.common.CommonConstants.EMPTY_BYTE_ARRAY;

import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.SurfaceControl;

import com.android.car.CarLog;
import com.android.car.R;
import com.android.car.hal.ClusterHalService;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.function.Consumer;

/**
 * Provides the functionalities regarding to the health monitoring and the heartbeat.
 */
final class ClusterHealthMonitor {
    private static final String TAG = CarLog.TAG_CLUSTER;
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private final ClusterHalService mClusterHalService;
    private final float mTplThresholdMinAlpha;
    private final float mTplThresholdMinFractionRendered;
    private final int mTplThresholdStabilityMs;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private SurfaceControl mClusterActivitySurface;
    @GuardedBy("mLock")
    private volatile boolean mClusterActivityVisible;
    @GuardedBy("mLock")
    private int mTrustedPresentationListenerCount;

    ClusterHealthMonitor(Context context, ClusterHalService clusterHalService) {
        mContext = context;
        mClusterHalService = clusterHalService;

        Resources resources = mContext.getResources();
        mTplThresholdMinAlpha = resources.getFraction(
                R.fraction.config_clusterHomeVisibility_minAlpha, /* base= */ 1, /* pbase= */ 1);
        mTplThresholdMinFractionRendered = resources.getFraction(
                R.fraction.config_clusterHomeVisibility_minRendered, /* base= */ 1, /* pbase= */ 1);
        mTplThresholdStabilityMs = resources.getInteger(
                R.integer.config_clusterHomeVisibility_stabilityMs);
    }

    void dump(IndentingPrintWriter writer) {
        writer.println("*ClusterHealthMonitor*");
        writer.increaseIndent();
        synchronized (mLock) {
            writer.printf("mClusterActivitySurface: %s\n", mClusterActivitySurface);
            writer.printf("mClusterActivityVisible: %b\n", mClusterActivityVisible);
            writer.printf("mTrustedPresentationListenerCount: %d\n",
                    mTrustedPresentationListenerCount);
        }
        writer.printf("mTplThresholdMinAlpha: %f\n", mTplThresholdMinAlpha);
        writer.printf("mTplThresholdMinFractionRendered: %f\n", mTplThresholdMinFractionRendered);
        writer.printf("mTplThresholdStabilityMs: %d\n", mTplThresholdStabilityMs);
        writer.decreaseIndent();
    }

    void sendHeartbeat(long epochTimeNs, byte[] appMetadata) {
        if (appMetadata == null) {
            appMetadata = EMPTY_BYTE_ARRAY;
        }
        boolean visible;
        synchronized (mLock) {
            visible = mClusterActivityVisible;
        }
        sendHeartbeatInternal(epochTimeNs, visible, appMetadata);
    }

    private void sendHeartbeatInternal(long epochTimeNs, boolean visible, byte[] appMetadata) {
        mClusterHalService.sendHeartbeat(epochTimeNs, visible ? 1 : 0, appMetadata);
    }

    void startVisibilityMonitoring(SurfaceControl surface) {
        if (DBG) {
            Slogf.d(TAG, "startVisibilityMonitoring: surface=%s", surface);
        }
        synchronized (mLock) {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            if (mClusterActivitySurface != null) {
                t.clearTrustedPresentationCallback(mClusterActivitySurface);
            }
            t.setTrustedPresentationCallback(surface,
                    new SurfaceControl.TrustedPresentationThresholds(
                            mTplThresholdMinAlpha, mTplThresholdMinFractionRendered,
                            mTplThresholdStabilityMs),
                    mContext.getMainExecutor(), mTrustedPresentationListener);
            t.apply();
            mClusterActivitySurface = surface;
            // The callback is expected to be called right away if the Surface is already visible.
            mClusterActivityVisible = false;
        }
    }

    private final Consumer<Boolean> mTrustedPresentationListener = inTrustedPresentationState -> {
        if (DBG) {
            Slogf.d(TAG, "inTrustedPresentationState=%b", inTrustedPresentationState);
        }
        synchronized (mLock) {
            ++mTrustedPresentationListenerCount;
            mClusterActivityVisible = inTrustedPresentationState;
        }
        sendHeartbeatInternal(System.nanoTime(), inTrustedPresentationState, EMPTY_BYTE_ARRAY);
    };

    void stopVisibilityMonitoring() {
        if (DBG) {
            Slogf.d(TAG, "stopVisibilityMonitoring");
        }
        synchronized (mLock) {
            if (mClusterActivitySurface != null) {
                SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                t.clearTrustedPresentationCallback(mClusterActivitySurface);
                t.apply();
            }
            mClusterActivitySurface = null;
            mClusterActivityVisible = false;
        }
    }
}
