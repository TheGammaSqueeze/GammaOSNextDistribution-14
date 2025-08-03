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

#ifndef EMULATOR_CAMERA_HAL_LOGICAL_REQUEST_STATE_H
#define EMULATOR_CAMERA_HAL_LOGICAL_REQUEST_STATE_H

#include "EmulatedRequestState.h"
#include "hwl_types.h"
#include "utils/HWLUtils.h"

namespace android {

using google_camera_hal::HalCameraMetadata;
using google_camera_hal::HalStream;
using google_camera_hal::HwlPipelineCallback;

struct EmulatedStream : public HalStream {
  uint32_t width, height;
  size_t buffer_size;
  bool is_input;
  int32_t group_id;
  int64_t use_case;
  int32_t color_space;
};

struct EmulatedPipeline {
  HwlPipelineCallback cb;
  // stream id -> stream map
  std::unordered_map<uint32_t, EmulatedStream> streams;
  uint32_t physical_camera_id, pipeline_id;
};

// [physical_camera_id -> [group_id -> stream_id]]
typedef std::map<uint32_t, std::map<uint32_t, int32_t>> DynamicStreamIdMapType;

// Info keeping track of mapping between zoom ratio range, focal length, and
// physical camera Id.
struct ZoomRatioPhysicalCameraInfo {
  float focal_length;
  float min_zoom_ratio;
  float max_zoom_ratio;
  uint32_t physical_camera_id;
};

class EmulatedLogicalRequestState {
 public:
  EmulatedLogicalRequestState(uint32_t camera_id);
  virtual ~EmulatedLogicalRequestState();

  status_t Initialize(std::unique_ptr<EmulatedCameraDeviceInfo> device_info,
                      PhysicalDeviceMapPtr physical_device_map);

  status_t GetDefaultRequest(
      RequestTemplate type,
      std::unique_ptr<HalCameraMetadata>* default_settings /*out*/);

  std::unique_ptr<HwlPipelineResult> InitializeLogicalResult(
      uint32_t pipeline_id, uint32_t frame_number, bool is_partial_result);

  status_t InitializeLogicalSettings(
      std::unique_ptr<HalCameraMetadata> request_settings,
      std::unique_ptr<std::set<uint32_t>> physical_camera_output_ids,
      uint32_t frame_number,
      EmulatedSensor::LogicalCameraSettings* logical_settings /*out*/);

  static std::unique_ptr<HalCameraMetadata> AdaptLogicalCharacteristics(
      std::unique_ptr<HalCameraMetadata> logical_chars,
      PhysicalDeviceMapPtr physical_devices);

  status_t UpdateRequestForDynamicStreams(
      HwlPipelineRequest* request,
      const std::vector<EmulatedPipeline>& pipelines,
      const DynamicStreamIdMapType& dynamic_stream_id_map_type,
      bool use_default_physical_camera);

 private:
  uint32_t logical_camera_id_ = 0;
  std::unique_ptr<EmulatedRequestState> logical_request_state_;
  bool is_logical_device_ = false;
  std::unique_ptr<std::set<uint32_t>> physical_camera_output_ids_;
  PhysicalDeviceMapPtr physical_device_map_;
  // Maps a physical device id to its respective request state
  std::unordered_map<uint32_t, std::unique_ptr<EmulatedRequestState>>
      physical_request_states_;

  // Describes the mapping between particular zoom ratio boundary value and
  // physical device id. The vector is sorted by ascending zoom ratios.
  std::vector<ZoomRatioPhysicalCameraInfo> zoom_ratio_physical_camera_info_;
  uint32_t current_physical_camera_ = 0;

  static std::vector<ZoomRatioPhysicalCameraInfo> GetZoomRatioPhysicalCameraInfo(
      const HalCameraMetadata* logical_chars,
      const PhysicalDeviceMap* physical_devices);
  static void UpdateActivePhysicalId(HalCameraMetadata* result_metadata,
                                     uint32_t device_id);

  EmulatedLogicalRequestState(const EmulatedLogicalRequestState&) = delete;
  EmulatedLogicalRequestState& operator=(const EmulatedLogicalRequestState&) =
      delete;
};

}  // namespace android

#endif  // EMULATOR_CAMERA_HAL_LOGICAL_REQUEST_STATE_H
