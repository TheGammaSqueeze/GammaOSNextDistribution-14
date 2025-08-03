/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef HW_EMULATOR_CAMERA_BASE_H
#define HW_EMULATOR_CAMERA_BASE_H

#include <log/log.h>

#include <memory>

#include "android/hardware/graphics/common/1.1/types.h"
#include "hwl_types.h"

namespace android {

using android::hardware::graphics::common::V1_1::PixelFormat;
using google_camera_hal::HwlPipelineCallback;
using google_camera_hal::StreamBuffer;

struct YCbCrPlanes {
  uint8_t* img_y = nullptr;
  uint8_t* img_cb = nullptr;
  uint8_t* img_cr = nullptr;
  uint32_t y_stride = 0;
  uint32_t cbcr_stride = 0;
  uint32_t cbcr_step = 0;
  size_t bytesPerPixel = 1;
};

struct SinglePlane {
  uint8_t* img = nullptr;
  uint32_t stride_in_bytes = 0;
  uint32_t buffer_size = 0;
};

struct SensorBuffer {
  uint32_t width, height;
  uint32_t frame_number;
  uint32_t pipeline_id;
  uint32_t camera_id;
  PixelFormat format;
  android_dataspace_t dataSpace;
  int32_t color_space;
  int32_t use_case;
  StreamBuffer stream_buffer;
  HwlPipelineCallback callback;
  int acquire_fence_fd;
  bool is_input;
  bool is_failed_request;

  union Plane {
    SinglePlane img;
    YCbCrPlanes img_y_crcb;
  } plane;

  SensorBuffer()
      : width(0),
        height(0),
        frame_number(0),
        pipeline_id(0),
        camera_id(0),
        format(PixelFormat::RGBA_8888),
        dataSpace(HAL_DATASPACE_UNKNOWN),
        color_space(
            ANDROID_REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP_UNSPECIFIED),
        use_case(ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT),
        acquire_fence_fd(-1),
        is_input(false),
        is_failed_request(false),
        plane{} {
  }

  SensorBuffer(const SensorBuffer&) = delete;
  SensorBuffer& operator=(const SensorBuffer&) = delete;

  virtual ~SensorBuffer() {
  }
};

typedef std::vector<std::unique_ptr<SensorBuffer>> Buffers;

}  // namespace android

#endif
