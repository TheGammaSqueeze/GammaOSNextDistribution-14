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

import android.frameworks.cameraservice.common.ProviderIdAndVendorTagSections;
import android.frameworks.cameraservice.device.CameraMetadata;
import android.frameworks.cameraservice.device.ICameraDeviceCallback;
import android.frameworks.cameraservice.device.ICameraDeviceUser;
import android.frameworks.cameraservice.service.CameraStatusAndId;
import android.frameworks.cameraservice.service.ICameraServiceListener;

@VintfStability
interface ICameraService {
    /**
     * Add listener for changes to camera device status.
     *
     * Also returns the set of currently-known camera IDs and state of each
     * device. Adding multiple listeners must result in the callbacks defined by
     * ICameraServiceListener being called on all of them, on change of device
     * status.
     *
     * - The listener contains an onPhysicalCameraStatusChanged function,
     * which is called by the camera service when a physical camera backing a
     * logical multi-camera becomes unavailable or available again.
     * - The function returns a vector of the newer version of CameraStatusAndId
     * which contains unavailable physical cameras if the specified camera is a
     * logical multi-camera.
     *
     * @param listener the listener interface to be added. The cameraserver will
     *        call callbacks on this interface when a camera device's status
     *        changes.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return a list of CameraStatusAndIds which stores the deviceIds,
     *         their corresponding statuses, and the unavailable physical camera Ids
     *         if the device is a logical multi-camera.
     */
    CameraStatusAndId[] addListener(in ICameraServiceListener listener);

    /**
     * connectDevice
     *
     * Return an ICameraDeviceUser interface for the requested cameraId.
     *
     * Note: The client must have camera permissions to call this method
     *       successfully.
     *
     * @param callback the ICameraDeviceCallback interface which will get called
     *        the cameraserver when capture is started, results are received
     *        etc.
     * @param cameraId the cameraId of the camera device to connect to.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return ICameraDeviceUser interface to the camera device requested.
     */
    ICameraDeviceUser connectDevice(in ICameraDeviceCallback callback,
        in String cameraId);

    /**
     * Read the static camera metadata for a camera device.
     *
     * @param cameraId the camera id of the camera device, whose metadata is
     *        being requested.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return the static metadata of the camera device requested.
     */
    CameraMetadata getCameraCharacteristics(in String cameraId);

    /**
     * Read in the provider ids and corresponding vendor tag sections from the camera server.
     * Intended to be used by the native code of CameraMetadata to correctly
     * interpret camera metadata with vendor tags.
     *
     * Note: VendorTag caches may be created in process, by clients. An AIDL api
     *       is not provided for this.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     * @return the list of provider ids and corresponding vendor tag sections.
     */
    ProviderIdAndVendorTagSections[] getCameraVendorTagSections();

    /**
     * Remove listener for changes to camera device status.
     *
     * @param listener the listener to be removed from receiving callbacks on
     *        changes to device state.
     *
     * @throws ServiceSpecificException on failure with error code set to Status corresponding to
     *         the specific failure.
     */
    void removeListener(in ICameraServiceListener listener);
}
