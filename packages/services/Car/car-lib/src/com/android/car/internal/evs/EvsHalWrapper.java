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

package com.android.car.internal.evs;

import android.hardware.HardwareBuffer;

/**
 * Abstracts EVS HAL. This is used as an interface between updatable and builtin.
 *
 * @hide
 */
public abstract class EvsHalWrapper {
    /** Callback for events from HAL */
    public interface HalEventCallback {
        /** EVS stream event handler called after a native handler */
        void onHalEvent(int eventType);

        /** EVS frame handler called after a native handler */
        void onFrameEvent(int id, HardwareBuffer buffer);

        /** EVS service death handler called after a native handler */
        void onHalDeath();
    }

    /** Initialize HAL */
    public boolean init() {
        return false;
    }

    /** Release HAL */
    public void release() {
    }

    /** is connected to HAL */
    public boolean isConnected() {
        return false;
    }

    /** Attempts to connect to the HAL service if it has not done yet */
    public boolean connectToHalServiceIfNecessary() {
        return false;
    }

    /** Attempts to disconnect from the HAL service */
    public void disconnectFromHalService() {
    }

    /** Attempts to open a target camera device */
    public boolean openCamera(String cameraId) {
        return false;
    }

    /** Requests to close a target camera device */
    public void closeCamera() {
    }

    /** Requests to start a video stream */
    public boolean requestToStartVideoStream() {
        return false;
    }

    /** Requests to stop a video stream */
    public void requestToStopVideoStream() {
    }

    /** Request to return an used buffer */
    public void doneWithFrame(int bufferId) {
    }
}
