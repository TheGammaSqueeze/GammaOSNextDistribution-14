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

#ifndef EMULATOR_CAMERA_HAL_HWL_CAMERA_DEVICE_SESSION_HWL_IMPL_H
#define EMULATOR_CAMERA_HAL_HWL_CAMERA_DEVICE_SESSION_HWL_IMPL_H

#include <camera_device_session_hwl.h>

#include <memory>
#include <set>

#include "EmulatedCameraDeviceHWLImpl.h"
#include "EmulatedRequestProcessor.h"
#include "EmulatedTorchState.h"
#include "multicam_coordinator_hwl.h"
#include "utils/StreamConfigurationMap.h"

namespace android {

using google_camera_hal::CameraDeviceHwl;
using google_camera_hal::CameraDeviceSessionHwl;
using google_camera_hal::CaptureRequest;
using google_camera_hal::CaptureResult;
using google_camera_hal::Dimension;
using google_camera_hal::HalStream;
using google_camera_hal::HwlOfflinePipelineRole;
using google_camera_hal::HwlPipelineCallback;
using google_camera_hal::HwlPipelineRequest;
using google_camera_hal::HwlSessionCallback;
using google_camera_hal::IMulticamCoordinatorHwl;
using google_camera_hal::RequestTemplate;
using google_camera_hal::SessionDataKey;
using google_camera_hal::Stream;
using google_camera_hal::StreamConfiguration;
using google_camera_hal::ZoomRatioMapperHwl;

class EmulatedCameraZoomRatioMapperHwlImpl : public ZoomRatioMapperHwl {
 public:
  EmulatedCameraZoomRatioMapperHwlImpl(
      const std::unordered_map<uint32_t, std::pair<Dimension, Dimension>>& dims);
  virtual ~EmulatedCameraZoomRatioMapperHwlImpl() = default;

  // Limit zoom ratio if concurrent mode is on
  virtual void LimitZoomRatioIfConcurrent(float*) const override{};

  // Get the array dimensions to be used for this capture request / result
  virtual bool GetActiveArrayDimensionToBeUsed(
      uint32_t camera_id, const HalCameraMetadata* settings,
      Dimension* active_array_dimension) const override;
  // Apply zoom ratio to capture request
  virtual void UpdateCaptureRequest(CaptureRequest*) override{};

  // Apply zoom ratio to capture result
  virtual void UpdateCaptureResult(CaptureResult*) override{};

  static std::unique_ptr<EmulatedCameraZoomRatioMapperHwlImpl> Create(
      const std::unordered_map<uint32_t, std::pair<Dimension, Dimension>>& dims);

 private:
  // camera id -> {max res dimension (array size), default dimension }
  std::unordered_map<uint32_t, std::pair<Dimension, Dimension>>
      camera_ids_to_dimensions_;
};

// Implementation of CameraDeviceSessionHwl interface
class EmulatedCameraDeviceSessionHwlImpl : public CameraDeviceSessionHwl {
 public:
  static std::unique_ptr<EmulatedCameraDeviceSessionHwlImpl> Create(
      uint32_t camera_id, std::unique_ptr<EmulatedCameraDeviceInfo> device_info,
      PhysicalDeviceMapPtr physical_devices,
      std::shared_ptr<EmulatedTorchState> torch_state);

  virtual ~EmulatedCameraDeviceSessionHwlImpl();

  // Override functions in CameraDeviceSessionHwl
  status_t ConstructDefaultRequestSettings(
      RequestTemplate type,
      std::unique_ptr<HalCameraMetadata>* default_settings) override;

  status_t PrepareConfigureStreams(
      const StreamConfiguration& /*request_config*/) override {
    return OK;
  }  // Noop for now

  status_t ConfigurePipeline(uint32_t physical_camera_id,
                             HwlPipelineCallback hwl_pipeline_callback,
                             const StreamConfiguration& request_config,
                             const StreamConfiguration& overall_config,
                             uint32_t* pipeline_id) override;

  status_t BuildPipelines() override;

  std::set<int32_t> GetHalBufferManagedStreams(
      const StreamConfiguration& config) override;

  status_t PreparePipeline(uint32_t /*pipeline_id*/,
                           uint32_t /*frame_number*/) override {
    return OK;
  }  // Noop for now

  status_t GetRequiredIntputStreams(const StreamConfiguration& /*overall_config*/,
                                    HwlOfflinePipelineRole /*pipeline_role*/,
                                    std::vector<Stream>* /*streams*/) override {
    // N/A
    return INVALID_OPERATION;
  }

  status_t GetConfiguredHalStream(
      uint32_t pipeline_id, std::vector<HalStream>* hal_streams) const override;

  void DestroyPipelines() override;

  status_t SubmitRequests(uint32_t frame_number,
                          std::vector<HwlPipelineRequest>& requests) override;

  status_t Flush() override;

  uint32_t GetCameraId() const override;

  std::vector<uint32_t> GetPhysicalCameraIds() const override;

  status_t GetCameraCharacteristics(
      std::unique_ptr<HalCameraMetadata>* characteristics) const override;

  status_t GetPhysicalCameraCharacteristics(
      uint32_t physical_camera_id,
      std::unique_ptr<HalCameraMetadata>* characteristics) const override;

  status_t SetSessionData(SessionDataKey /*key*/
                                    ,
                                    void* /*value*/) override {
    return OK;
  }  // Noop for now

  status_t GetSessionData(SessionDataKey /*key*/,
                          void** /*value*/) const override {
    return OK;
  }  // Noop for now

  void SetSessionCallback(
      const HwlSessionCallback& hwl_session_callback) override;

  status_t FilterResultMetadata(HalCameraMetadata* /*metadata*/) const override {
    return OK;
  }  // Noop for now

  std::unique_ptr<IMulticamCoordinatorHwl> CreateMulticamCoordinatorHwl()
      override {
    return nullptr;
  }

  status_t IsReconfigurationRequired(
      const HalCameraMetadata* /*old_session*/,
      const HalCameraMetadata* /*new_session*/,
      bool* reconfiguration_required) const override {
    if (reconfiguration_required == nullptr) {
      return BAD_VALUE;
    }
    *reconfiguration_required = true;
    return OK;
  }

  std::unique_ptr<google_camera_hal::ZoomRatioMapperHwl> GetZoomRatioMapperHwl()
      override {
    return std::move(zoom_ratio_mapper_hwl_impl_);
  }
  // End override functions in CameraDeviceSessionHwl

 private:
  status_t Initialize(uint32_t camera_id,
                      std::unique_ptr<EmulatedCameraDeviceInfo> device_info);
  status_t InitializeRequestProcessor();

  status_t CheckOutputFormatsForInput(
      const HwlPipelineRequest& request,
      const std::unordered_map<uint32_t, EmulatedStream>& streams,
      const std::unique_ptr<StreamConfigurationMap>& stream_configuration_map,
      android_pixel_format_t input_format);

  EmulatedCameraDeviceSessionHwlImpl(
      PhysicalDeviceMapPtr physical_devices,
      std::shared_ptr<EmulatedTorchState> torch_state)
      : torch_state_(torch_state),
        physical_device_map_(std::move(physical_devices)) {
  }

  uint8_t max_pipeline_depth_ = 0;

  // Protects the API entry points
  mutable std::mutex api_mutex_;
  uint32_t camera_id_ = 0;
  bool error_state_ = false;
  bool pipelines_built_ = false;
  bool has_raw_stream_ = false;
  bool supports_session_hal_buf_manager_ = false;
  std::unique_ptr<EmulatedCameraDeviceInfo> device_info_;
  std::vector<EmulatedPipeline> pipelines_;
  std::shared_ptr<EmulatedRequestProcessor> request_processor_;
  std::unique_ptr<StreamConfigurationMap> stream_configuration_map_;
  PhysicalStreamConfigurationMap physical_stream_configuration_map_;
  PhysicalStreamConfigurationMap physical_stream_configuration_map_max_resolution_;
  std::unique_ptr<StreamConfigurationMap> stream_configuration_map_max_resolution_;
  SensorCharacteristics sensor_chars_;
  std::shared_ptr<EmulatedTorchState> torch_state_;
  PhysicalDeviceMapPtr physical_device_map_;
  LogicalCharacteristics logical_chars_;
  HwlSessionCallback session_callback_;
  DynamicStreamIdMapType dynamic_stream_id_map_;
  std::unique_ptr<EmulatedCameraZoomRatioMapperHwlImpl> zoom_ratio_mapper_hwl_impl_;
};

}  // namespace android

#endif
