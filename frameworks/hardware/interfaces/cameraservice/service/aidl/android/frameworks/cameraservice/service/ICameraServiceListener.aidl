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

package android.frameworks.cameraservice.service;

import android.frameworks.cameraservice.service.CameraDeviceStatus;

@VintfStability
oneway interface ICameraServiceListener {
    /**
     * Callback called by cameraservice when the status of a physical
     * camera device backing a logical camera changes
     *
     * @param status the current device status
     * @param cameraId the logical camera device that the physical camera
     *      device belongs to
     * @param physicalCameraId the physical camera device whose status
     *      change is being reported
     */
    void onPhysicalCameraStatusChanged(in CameraDeviceStatus status, in String cameraId,
        in String physicalCameraId);

    /**
     * Callback called by cameraservice when the status of a camera device
     * changes
     *
     * @param status the current device status
     * @param cameraId the camera device whose status change is being reported
     */
    void onStatusChanged(in CameraDeviceStatus status, in String cameraId);
}
