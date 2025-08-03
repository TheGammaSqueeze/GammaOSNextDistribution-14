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

package android.frameworks.cameraservice.device;

import android.frameworks.cameraservice.device.CameraMetadata;

/**
 * Either size of the capture request / result metadata sent through FMQ or
 * the request / result metadata itself.
 * If the server of the metadata chooses to use FMQ, it must set the
 * fmqMetadataSize field to the size(> 0) of the metadata held by the FMQ.
 * Otherwise, the metadata field must contain the metadata.
 */
@VintfStability
union CaptureMetadataInfo {
    long fmqMetadataSize;
    CameraMetadata metadata;
}
