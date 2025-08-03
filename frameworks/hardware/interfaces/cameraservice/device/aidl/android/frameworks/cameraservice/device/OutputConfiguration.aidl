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

import android.hardware.common.NativeHandle;
import android.view.Surface;

/**
 * This describes camera output. It has configurations specific to a
 * capture session.
 */
@VintfStability
parcelable OutputConfiguration {
    /**
     * Rotation values for camera output
     */
    @VintfStability
    @Backing(type="int")
    enum Rotation {
        R0 = 0,
        R90 = 1,
        R180 = 2,
        R270 = 3,
    }
    @VintfStability
    @Backing(type="int")
    enum WindowGroupId {
        NONE = -1,
    }
    /**
     * These must be handles to ANativeWindows owned by AImageReader,
     * obtained by using AImageReader_getWindowNativeHandle. Ref:
     * (frameworks/av/media/ndk/include/media/NdkImageReader.h).
     * When this vector has more than one window handle, native window surface
     * sharing is enabled. Clients may take advantage of this in advanced use
     * cases when they would require create more streams than the limits the
     * camera device imposes [1]. In this case, more than one window must be
     * attached to an OutputConfiguration so that they map to one camera stream.
     * The outputs will share memory buffers whenever possible. Due to buffer
     * sharing, client should be careful while adding native window outputs that
     * modify their input data. If such cases exist, client must have additional
     * mechanisms to synchronize read and write accesses between consumers.
     * [1]: Ref : frameworks/av/camera/ndk/include/camera/NdkCameraDevice.h
     *
     * @deprecated Use surfaces instead.
     */
    NativeHandle[] windowHandles;
    /**
     * The rotation value for the camera output for this configuration.
     * Only Rotation::R0 is guaranteed to be supported.
     */
    Rotation rotation;
    /**
     * A windowGroupId is used to identify which window group this output window belongs to. A
     * window group is a group of output windows that are not intended to receive camera output
     * buffer streams simultaneously. The ICameraDevice may be able to share the buffers used
     * by all the windows from the same window group, therefore may reduce the overall memory
     * footprint. The client must only set the same set id for the streams that are not
     * simultaneously streaming. For OutputConfigurations not belonging to any
     * window group the client must set windowGroupId to WindowGroupId::NONE.
     */
    int windowGroupId;
    /**
     * The id of the physical camera id, that this OutputConfiguration is meant
     * for. If the no physical camera id is expected, this must be an empty
     * string.
     */
    String physicalCameraId;
    /**
     * The width of the output stream.
     *
     * Note: this must only be used when using deferred streams. Otherwise, it
     *       must be set to 0.
     */
    int width;
    /**
     * The height of the output stream.
     *
     * Note: this must only be used when using deferred streams. Otherwise, it
     *       must be set to 0.
     */
    int height;
    /**
     * This must be set to true, if this OutputConfiguration contains handles to
     * deferred native windows.
     * Ref:frameworks/base/core/java/android/hardware/camera2/params/OutputConfiguration.java
     */
    boolean isDeferred;
    /**
     * These must be Surfaces owned by AImageReader, acquired using AImageReader_getWindow()[1].
     *
     * When this vector has more than one entry, surface sharing is enabled. Clients may take
     * advantage of this in advanced use cases when they would require create more streams
     * than the limits the camera device imposes [2]. In this case, more than one window
     * must be attached to an OutputConfiguration so that they map to one camera stream.
     * The outputs will share memory buffers whenever possible. Due to buffer
     * sharing, client should be careful while adding Surface outputs that
     * modify their input data. If such cases exist, client must have additional
     * mechanisms to synchronize read and write accesses between consumers.
     *
     * [1]: Ref: frameworks/av/media/ndk/include/media/NdkImageReader.h
     * [2]: Ref: frameworks/av/camera/ndk/include/camera/NdkCameraDevice.h
     */
    Surface[] surfaces = {};
}
