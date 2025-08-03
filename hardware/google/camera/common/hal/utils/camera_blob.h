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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_UTILS_CAMERA_BLOB_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_UTILS_CAMERA_BLOB_H_

#include <cstdint>

namespace android {
namespace google_camera_hal {

/**
 * CameraBlob:
 *
 * Transport header for camera blob types; generally compressed JPEG buffers in
 * output streams.
 *
 * To capture JPEG images, a stream is created using the pixel format
 * HAL_PIXEL_FORMAT_BLOB and dataspace HAL_DATASPACE_V0_JFIF. The buffer size
 * for the stream is calculated by the framework, based on the static metadata
 * field android.jpeg.maxSize. Since compressed JPEG images are of variable
 * size, the HAL needs to include the final size of the compressed image using
 * this structure inside the output stream buffer. The camera blob ID field must
 * be set to CameraBlobId::JPEG.
 *
 * The transport header must be at the end of the JPEG output stream
 * buffer. That means the jpegBlobId must start at byte[buffer_size -
 * sizeof(CameraBlob)], where the buffer_size is the size of gralloc
 * buffer. Any HAL using this transport header must account for it in
 * android.jpeg.maxSize. The JPEG data itself starts at the beginning of the
 * buffer and must be blobSize bytes long.
 *
 * Copied from hardware/interfaces/camera/device/aidl/CameraBlobId.aidl
 */
enum CameraBlobId : uint32_t {
  JPEG = 0x00FF,
};

struct CameraBlob {
  CameraBlobId blob_id;
  uint32_t blob_size;
};

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_UTILS_CAMERA_BLOB_H_
