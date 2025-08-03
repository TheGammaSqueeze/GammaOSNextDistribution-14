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
package com.android.server.uwb.correction.pose;

import androidx.annotation.NonNull;

import com.android.server.uwb.correction.math.Pose;

/**
 * Used for receiving notifications from a PoseSource when there is new pose data.
 */
public interface PoseEventListener {
    /**
     * Called when there is an update to the device's pose. The origin is arbitrary, but
     * position could be relative to the starting position, and rotation could be relative
     * to magnetic north and the direction of gravity.
     * @param pose The new location and orientation of the device.
     */
    void onPoseChanged(@NonNull Pose pose);
}
