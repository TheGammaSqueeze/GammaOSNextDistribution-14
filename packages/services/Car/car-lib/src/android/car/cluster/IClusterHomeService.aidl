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

package android.car.cluster;

import android.car.cluster.ClusterState;
import android.car.cluster.IClusterStateListener;
import android.car.cluster.IClusterNavigationStateListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.SurfaceControl;

/** @hide */
interface IClusterHomeService {
    /**
     * Reports the current UI state in cluster display.
     */
    void reportState(int uiTypeMain, int uiTypeSub, in byte[] uiAvailability) = 0;
    /**
     * Requests to turn the cluster display on to show some ClusterUI.
     */
    void requestDisplay(int uiType) = 1;
    /**
     * Start an activity as specified user. The activity is considered as in fixed mode for
     * the cluster display and will be re-launched if the activity crashes, the package
     * is updated or goes to background for whatever reason.
     * Only one activity can exist in fixed mode for the display and calling this multiple
     * times with different {@code Intent} will lead into making all previous activities into
     * non-fixed normal state (= will not be re-launched.)
     */
    boolean startFixedActivityModeAsUser(in Intent intent,
            in Bundle activityOptionsBundle, int userId) = 2;
    /**
     * The activity launched on the cluster display is no longer in fixed mode. Re-launching or
     * finishing should not trigger re-launching any more. Note that Activity for non-current user
     * will be auto-stopped and there is no need to call this for user switching. Note that this
     * does not stop the activity but it will not be re-launched any more.
     */
    void stopFixedActivityMode() = 3;
    // 4 removed. Do not use - void registerCallback(in IClusterHomeCallback callback) = 4;
    // 5 removed. Do not use - void unregisterCallback(in IClusterHomeCallback callback) = 5;
    /** Returns the current {@code ClusterDisplayState}. */
    ClusterState getClusterState() = 6;
    /** Registers a listener to listen for cluster state changes. */
    void registerClusterStateListener(in IClusterStateListener listener) = 7;
    /** Unregisters a cluster state listener. */
    void unregisterClusterStateListener(in IClusterStateListener listener) = 8;
    /** Registers a listener to lsiten for clustser navigation state changes. */
    void registerClusterNavigationStateListener(in IClusterNavigationStateListener listener) = 9;
    /** Unregisters a cluster navigation state listener. */
    void unregisterClusterNavigationStateListener(in IClusterNavigationStateListener listener) = 10;
    /** Sends a heartbeat. */
    void sendHeartbeat(long epochTimeNs, in byte[] appMetadata) = 11;
    /** Starts the visibility monitoring of the given Surface. */
    void startVisibilityMonitoring(in SurfaceControl sc) = 12;
    /** Stops the visibility monitoring. */
    void stopVisibilityMonitoring() = 13;
}
