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

#ifndef HW_EMULATOR_GRALLOC_SENSOR_BUFFER_H
#define HW_EMULATOR_GRALLOC_SENSOR_BUFFER_H

#include <log/log.h>

#include <memory>

#include "Base.h"
#include "HandleImporter.h"

namespace android {

using android::google_camera_hal::BufferStatus;
using android::google_camera_hal::ErrorCode;
using android::google_camera_hal::HwlPipelineResult;
using android::google_camera_hal::MessageType;
using android::google_camera_hal::NotifyMessage;
using android::hardware::camera::common::V1_0::helper::HandleImporter;

class GrallocSensorBuffer : public SensorBuffer {
 public:
  GrallocSensorBuffer(std::shared_ptr<HandleImporter> handle_importer)
      : importer_(handle_importer) {
  }

  GrallocSensorBuffer(const GrallocSensorBuffer&) = delete;
  GrallocSensorBuffer& operator=(const GrallocSensorBuffer&) = delete;

  virtual ~GrallocSensorBuffer() override;

 private:
  std::shared_ptr<HandleImporter> importer_;
};

}  // namespace android

#endif
