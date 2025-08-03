/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.cluster;

/** A class that represents the state and the cancel reason for a command from TF Cluster. */
public class ClusterCommandStatus {
    private final ClusterCommand.State mState;
    private final String mCancelReason;

    /**
     * Construct
     *
     * @param state state of the command.
     * @param cancelReason cancel reason of the command (if canceled).
     */
    public ClusterCommandStatus(ClusterCommand.State state, String cancelReason) {
        mState = state;
        mCancelReason = cancelReason;
    }

    public ClusterCommand.State getState() {
        return mState;
    }

    public String getCancelReason() {
        return mCancelReason;
    }
}
