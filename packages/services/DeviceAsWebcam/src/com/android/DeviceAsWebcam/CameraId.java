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

package com.android.DeviceAsWebcam;

import android.hardware.camera2.CameraManager;

import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;

import java.util.regex.Pattern;

/**
 * An identifier composed by a pair of camera device id and its underlying physical camera id.
 */
public class CameraId {
    private static final String CAMERA_ID_SPLITTER = "-";
    public final String mainCameraId;
    @Nullable
    public final String physicalCameraId;
    private final String identifier;

    /**
     * Constructor
     *
     * @param mainCameraId     the main camera id retrieved via
     *                         {@link CameraManager#getCameraIdList()}.
     * @param physicalCameraId the physical camera id if the main camera is a logical camera. This
     *                         can be {@code null}.
     */
    public CameraId(String mainCameraId, @Nullable String physicalCameraId) {
        Preconditions.checkNotNull(mainCameraId, "The specified id can't be null!");
        this.mainCameraId = mainCameraId;
        this.physicalCameraId = physicalCameraId;
        identifier = createIdentifier(mainCameraId, physicalCameraId);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof CameraId dest)) {
            return false;
        }

        return identifier.equals(dest.identifier);
    }

    @Override
    public String toString() {
        return identifier;
    }

    /**
     * Creates an identifier string to represent the camera.
     */
    public static String createIdentifier(String cameraId, @Nullable String physicalCameraId) {
        return cameraId + CAMERA_ID_SPLITTER + physicalCameraId;
    }

    /**
     * Returns the CameraId restored from the specified identifier.
     *
     * <p>The identifier should be created by the {@link #createIdentifier(String, String)}
     * function. If the identifier format is not matched, {@code null} will be returned by this
     * function.
     */
    @Nullable
    public static CameraId fromCameraIdString(@Nullable String identifier) {
        if (identifier == null) {
            return null;
        }

        String idPattern = "\\d+" + CAMERA_ID_SPLITTER + "(?:\\d+|null)";
        if (!Pattern.matches(idPattern, identifier)) {
            return null;
        }

        int index = identifier.indexOf(CAMERA_ID_SPLITTER);
        String mainCameraId = identifier.substring(0, index);
        String physicalCameraId = identifier.substring(index + CAMERA_ID_SPLITTER.length());

        return new CameraId(mainCameraId,
                physicalCameraId.equals("null") ? null : physicalCameraId);
    }
}
