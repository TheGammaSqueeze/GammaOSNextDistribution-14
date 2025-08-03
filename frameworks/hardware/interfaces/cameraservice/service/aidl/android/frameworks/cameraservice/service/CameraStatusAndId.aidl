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

/**
 * The camera Id and its corresponding CameraDeviceStatus
 */
@VintfStability
parcelable CameraStatusAndId {
    CameraDeviceStatus deviceStatus;
    String cameraId;
    /**
     * The physical cameras that are unavailable to use (via physical streams)
     * for this logical multi-camera.
     */
    String[] unavailPhysicalCameraIds;
}
