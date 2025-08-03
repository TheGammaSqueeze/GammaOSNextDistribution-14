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

import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;

import com.android.server.uwb.correction.math.Pose;

import java.util.EnumSet;

@Presubmit
public class NullPoseSource extends PoseSourceBase {

    private EnumSet<Capabilities> mCapabilities = Capabilities.ALL;

    /**
     * Gets the capabilities of this pose source.
     *
     * @return An EnumSet of Capabilities.
     */
    @NonNull
    @Override
    public EnumSet<Capabilities> getCapabilities() {
        return mCapabilities;
    }

    /**
     * Starts the pose source. Called by the {@link PoseSourceBase} when the first listener
     * subscribes.
     */
    @Override
    protected void start() {

    }

    /**
     * Stops the pose source. Called by the {@link PoseSourceBase} when the last listener
     * unsubscribes.
     */
    @Override
    protected void stop() {

    }

    public void changePose(Pose pose) {
        publish(pose);
    }

    public void setCapabilities(EnumSet<Capabilities> mCapabilities) {
        this.mCapabilities = mCapabilities;
    }
}
