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

import java.util.EnumSet;

/**
 * Provides pose update information and a way for subscribers to listen for them.
 */
public interface IPoseSource extends AutoCloseable {
    /** The shortest practical update interval for a pose source. */
    int MIN_INTERVAL_MS = 1000 / 60; // 60Hz

    /** The longest practical update interval for a pose source. */
    int MAX_INTERVAL_MS = 10000; // 0.1Hz.

    /**
     * A set of all possible pose source capabilities.
     */
    enum Capabilities {
        YAW, PITCH, ROLL, X, Y, Z,
        /**
         * Indicates that a pitch and roll of 0 means that the phone is upright. If this flag
         * is not present, pitch and roll changes are only relative.
         */
        UPRIGHT;

        public static final EnumSet<Capabilities> ALL = EnumSet.allOf(Capabilities.class);
        public static final EnumSet<Capabilities> NONE = EnumSet.noneOf(Capabilities.class);
        public static final EnumSet<Capabilities> ROTATION = EnumSet.of(
                Capabilities.YAW,
                Capabilities.PITCH,
                Capabilities.ROLL
        );
        public static final EnumSet<Capabilities> UPRIGHT_ROTATION = EnumSet.of(
                Capabilities.YAW,
                Capabilities.PITCH,
                Capabilities.ROLL,
                Capabilities.UPRIGHT);
        public static final EnumSet<Capabilities> TRANSLATION = EnumSet.of(
                Capabilities.X,
                Capabilities.Y,
                Capabilities.Z);
    }

    /**
     * Stops the pose sensing and removes all listeners.
     */
    @Override
    void close();

    /**
     * Registers a listener for the pose updates.
     * @param listener The PoseEventListener that will be notified when the pose changes.
     */
    void registerListener(@NonNull PoseEventListener listener);

    /**
     * Unregisters a listener from the pose updates.
     * @param listener The PoseEventListener that will no longer be notified when the pose changes.
     * @return True if successfully removed. Note that a listener may be prematurely removed if it
     *         has thrown an error.
     */
    boolean unregisterListener(@NonNull PoseEventListener listener);

    /**
     * Gets the current pose.
     * @return The current pose. May be null.
     */
    Pose getPose();

    /**
     * Gets the capabilities of this pose source.
     * @return An EnumSet of Capabilities.
     */
    @NonNull
    EnumSet<Capabilities> getCapabilities();
}
