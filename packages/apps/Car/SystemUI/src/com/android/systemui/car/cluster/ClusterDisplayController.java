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

package com.android.systemui.car.cluster;

import static android.car.cluster.ClusterHomeManager.CONFIG_DISPLAY_BOUNDS;
import static android.car.cluster.ClusterHomeManager.CONFIG_DISPLAY_ID;

import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.car.cluster.ClusterState;
import android.graphics.Rect;
import android.util.Slog;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.DisplayAreaInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.systemui.CoreStartable;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;

import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/***
 * Controls the RootTDA of cluster display per CLUSTER_DISPLAY_STATE message.
 */
@SysUISingleton
public class ClusterDisplayController implements CoreStartable {
    private static final String TAG = ClusterDisplayController.class.getSimpleName();
    private static final boolean DBG = false;

    private final RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    private final CarServiceProvider mCarServiceProvider;
    private final Executor mMainExecutor;

    private ClusterHomeManager mClusterHomeManager;
    private WindowContainerToken mRootTDAToken;
    private ClusterState mClusterState;

    @Inject
    public ClusterDisplayController(
            Optional<RootTaskDisplayAreaOrganizer> rootTDAOrganizer,
            CarServiceProvider carServiceProvider, @Main Executor mainExecutor) {
        mRootTDAOrganizer = rootTDAOrganizer.orElse(null);
        mCarServiceProvider = carServiceProvider;
        mMainExecutor = mainExecutor;
    }

    @Override
    public void start() {
        if (mRootTDAOrganizer == null) {
            Slog.w(TAG, "ClusterDisplayController is disabled because of no "
                    + "RootTaskDisplayAreaOrganizer");
            return;
        }
        mCarServiceProvider.addListener(mCarServiceOnConnectedListener);
    }

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
        @Override
        public void onConnected(Car car) {
            mClusterHomeManager = (ClusterHomeManager) car.getCarManager(Car.CLUSTER_HOME_SERVICE);
            if (mClusterHomeManager == null) {
                Slog.w(TAG, "ClusterHomeManager is disabled");
                return;
            }
            mClusterHomeManager.registerClusterStateListener(mMainExecutor, mClusterStateListener);

            mClusterState = mClusterHomeManager.getClusterState();
            if (mClusterState.displayId != Display.INVALID_DISPLAY) {
                mRootTDAOrganizer.registerListener(mClusterState.displayId, mRootTDAListener);
            }
        }
    };

    private final ClusterHomeManager.ClusterStateListener mClusterStateListener =
            new ClusterHomeManager.ClusterStateListener() {
        @Override
        public void onClusterStateChanged(ClusterState state, int changes) {
            // Need to update mClusterState first, since mClusterState will be referenced during
            // registerListener() -> onDisplayAreaAppeared() -> resizeTDA().
            mClusterState = state;
            if (DBG) {
                Slog.d(TAG, "onClusterStateChanged: changes=" + changes
                        + ", displayId=" + state.displayId);
            }
            if ((changes & CONFIG_DISPLAY_ID) != 0) {
                if (state.displayId != Display.INVALID_DISPLAY) {
                    mRootTDAOrganizer.registerListener(state.displayId, mRootTDAListener);
                } else {
                    mRootTDAOrganizer.unregisterListener(mRootTDAListener);
                }
            }
            if ((changes & CONFIG_DISPLAY_BOUNDS) != 0 && mRootTDAToken != null) {
                resizeTDA(mRootTDAToken, state.bounds, state.displayId);
            }
        }
    };

    private final RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener mRootTDAListener =
            new RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener() {
        @Override
        public void onDisplayAreaAppeared(DisplayAreaInfo displayAreaInfo) {
            if (DBG) Slog.d(TAG, "onDisplayAreaAppeared: " + displayAreaInfo);
            if (mClusterState != null && mClusterState.displayId != Display.INVALID_DISPLAY) {
                resizeTDA(displayAreaInfo.token, mClusterState.bounds, mClusterState.displayId);
            }
            mRootTDAToken = displayAreaInfo.token;
        }

        @Override
        public void onDisplayAreaVanished(DisplayAreaInfo displayAreaInfo) {
            if (DBG) Slog.d(TAG, "onDisplayAreaVanished: " + displayAreaInfo);
            mRootTDAToken = null;
        }

        @Override
        public void onDisplayAreaInfoChanged(DisplayAreaInfo displayAreaInfo) {
            if (DBG) Slog.d(TAG, "onDisplayAreaInfoChanged: " + displayAreaInfo);
            mRootTDAToken = displayAreaInfo.token;
        }
    };

    private void resizeTDA(WindowContainerToken token, Rect bounds, int displayId) {
        if (DBG) {
            Slog.d(TAG, "resizeTDA: token=" + token + ", bounds=" + bounds
                    + ", displayId=" + displayId);
        }
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(token, bounds);
        wct.setAppBounds(token, bounds);
        mRootTDAOrganizer.applyTransaction(wct);

        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mRootTDAOrganizer.setPosition(tx, displayId, bounds.left, bounds.top);
        tx.apply();
    }
}
