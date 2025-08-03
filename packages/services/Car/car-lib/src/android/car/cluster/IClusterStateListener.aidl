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

/** @hide */
oneway interface IClusterStateListener {
    /**
     * Called when ClusterOS changes the cluster display state, the geometry of cluster display,
     * or the uiType.
     */
    void onClusterStateChanged(in ClusterState state, int changes) = 1;

    // 2 removed. Do not use - void onNavigationStateChanged(in byte[] navigationState) = 2;
}
