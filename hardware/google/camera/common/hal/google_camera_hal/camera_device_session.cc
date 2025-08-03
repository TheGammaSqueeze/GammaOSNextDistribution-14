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

// #define LOG_NDEBUG 0
#define LOG_TAG "GCH_CameraDeviceSession"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include "camera_device_session.h"

#include <inttypes.h>
#include <log/log.h>
#include <utils/Trace.h>

#include "basic_capture_session.h"
#include "capture_session_utils.h"
#include "dual_ir_capture_session.h"
#include "hal_types.h"
#include "hal_utils.h"
#include "hdrplus_capture_session.h"
#include "rgbird_capture_session.h"
#include "system/camera_metadata.h"
#include "ui/GraphicBufferMapper.h"
#include "vendor_tag_defs.h"
#include "vendor_tag_types.h"
#include "vendor_tags.h"
#include "zsl_snapshot_capture_session.h"

namespace android {
namespace google_camera_hal {

constexpr char kMeasureBufferAllocationProp[] =
    "persist.vendor.camera.measure_buffer_allocation";

static constexpr int64_t kNsPerSec = 1000000000;
static constexpr int64_t kAllocationThreshold = 33000000;  // 33ms

std::vector<CaptureSessionEntryFuncs>
    CameraDeviceSession::kCaptureSessionEntries = {
        {.IsStreamConfigurationSupported =
             HdrplusCaptureSession::IsStreamConfigurationSupported,
         .CreateSession = HdrplusCaptureSession::Create},
        {.IsStreamConfigurationSupported =
             RgbirdCaptureSession::IsStreamConfigurationSupported,
         .CreateSession = RgbirdCaptureSession::Create},
        {.IsStreamConfigurationSupported =
             DualIrCaptureSession::IsStreamConfigurationSupported,
         .CreateSession = DualIrCaptureSession::Create},
        // BasicCaptureSession is supposed to be the last resort.
        {.IsStreamConfigurationSupported =
             BasicCaptureSession::IsStreamConfigurationSupported,
         .CreateSession = BasicCaptureSession::Create}};

std::vector<WrapperCaptureSessionEntryFuncs>
    CameraDeviceSession::kWrapperCaptureSessionEntries = {
        {.IsStreamConfigurationSupported =
             ZslSnapshotCaptureSession::IsStreamConfigurationSupported,
         .CreateSession = ZslSnapshotCaptureSession::Create}};

std::unique_ptr<CameraDeviceSession> CameraDeviceSession::Create(
    std::unique_ptr<CameraDeviceSessionHwl> device_session_hwl,
    std::vector<GetCaptureSessionFactoryFunc> external_session_factory_entries,
    CameraBufferAllocatorHwl* camera_allocator_hwl) {
  ATRACE_CALL();
  if (device_session_hwl == nullptr) {
    ALOGE("%s: device_session_hwl is nullptr", __FUNCTION__);
    return nullptr;
  }

  uint32_t camera_id = device_session_hwl->GetCameraId();
  std::vector<uint32_t> physical_camera_ids =
      device_session_hwl->GetPhysicalCameraIds();

  auto session = std::unique_ptr<CameraDeviceSession>(new CameraDeviceSession());
  if (session == nullptr) {
    ALOGE("%s: Creating CameraDeviceSession failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res =
      session->Initialize(std::move(device_session_hwl), camera_allocator_hwl,
                          external_session_factory_entries);
  if (res != OK) {
    ALOGE("%s: Initializing CameraDeviceSession failed: %s (%d).", __FUNCTION__,
          strerror(-res), res);
    return nullptr;
  }

  // Construct a string of physical camera IDs.
  std::string physical_camera_ids_string;
  if (physical_camera_ids.size() > 0) {
    physical_camera_ids_string += ": ";

    for (auto& id : physical_camera_ids) {
      physical_camera_ids_string += std::to_string(id) + " ";
    }
  }

  ALOGI(
      "%s: Created a device session for camera %d with %zu physical cameras%s",
      __FUNCTION__, camera_id, physical_camera_ids.size(),
      physical_camera_ids_string.c_str());

  return session;
}

status_t CameraDeviceSession::UpdatePendingRequest(CaptureResult* result) {
  std::lock_guard<std::mutex> lock(request_record_lock_);
  if (result == nullptr) {
    ALOGE("%s: result is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  if (result->output_buffers.empty()) {
    // Nothing to do if the result doesn't contain any output buffers.
    return OK;
  }
  bool frame_has_hal_buffer_managed_buffer = false;
  for (const auto& buffer : result->output_buffers) {
    if (hal_buffer_managed_stream_ids_.find(buffer.stream_id) !=
        hal_buffer_managed_stream_ids_.end()) {
      frame_has_hal_buffer_managed_buffer = true;
      break;
    }
  }

  // There's no HAL buffer managed buffer in the frame, we don't need to track
  // it through pending_request_streams_.
  if (!frame_has_hal_buffer_managed_buffer) {
    return OK;
  }

  // Update inflight request records and notify SBC for flushing if needed
  uint32_t frame_number = result->frame_number;

  if (pending_request_streams_.find(frame_number) ==
      pending_request_streams_.end()) {
    ALOGE("%s: Can't find frame %u in result holder.", __FUNCTION__,
          frame_number);
    return UNKNOWN_ERROR;
  }

  // Remove streams from pending request streams for buffers in the result.
  auto& streams = pending_request_streams_.at(frame_number);
  for (auto& stream_buffer : result->output_buffers) {
    int32_t stream_id = stream_buffer.stream_id;
    if (streams.find(stream_id) == streams.end()) {
      // If stream_id belongs to a stream group, the HWL may choose to output
      // buffers to a different stream in the same group.
      if (grouped_stream_id_map_.count(stream_id) == 1) {
        int32_t stream_id_for_group = grouped_stream_id_map_.at(stream_id);
        if (streams.find(stream_id_for_group) != streams.end()) {
          streams.erase(stream_id_for_group);
        } else {
          ALOGE(
              "%s: Can't find stream_id_for_group %d for stream %d in frame %u "
              "result holder. It may have been returned or have not been "
              "requested.",
              __FUNCTION__, stream_id_for_group, stream_id, frame_number);
        }
      } else {
        ALOGE(
            "%s: Can't find stream %d in frame %u result holder. It may"
            " have been returned or have not been requested.",
            __FUNCTION__, stream_id, frame_number);
      }
      // Ignore this buffer and continue handling other buffers in the
      // result.
    } else {
      streams.erase(stream_id);
    }
  }

  if (streams.empty()) {
    pending_request_streams_.erase(frame_number);
  }

  if (pending_request_streams_.empty()) {
    status_t res = stream_buffer_cache_manager_->NotifyFlushingAll();
    if (res != OK) {
      ALOGE("%s: Failed to notify SBC manager to flush all streams.",
            __FUNCTION__);
    }
    ALOGI(
        "%s: [sbc] All inflight requests/streams cleared. Notified SBC for "
        "flushing.",
        __FUNCTION__);
  }
  return OK;
}

void CameraDeviceSession::ProcessCaptureResult(
    std::unique_ptr<CaptureResult> result) {
  if (TryHandleCaptureResult(result)) return;

  // Update pending request tracker with returned buffers.
  std::vector<StreamBuffer> buffers;
  buffers.insert(buffers.end(), result->output_buffers.begin(),
                 result->output_buffers.end());

  if (result->result_metadata) {
    std::lock_guard<std::mutex> lock(request_record_lock_);
    pending_results_.erase(result->frame_number);
  }

  {
    std::shared_lock lock(session_callback_lock_);
    session_callback_.process_capture_result(std::move(result));
  }

  TrackReturnedBuffers(buffers);
}

void CameraDeviceSession::ProcessBatchCaptureResult(
    std::vector<std::unique_ptr<CaptureResult>> results) {
  std::vector<std::unique_ptr<CaptureResult>> results_to_callback;
  results_to_callback.reserve(results.size());
  std::vector<StreamBuffer> buffers;
  for (auto& result : results) {
    if (TryHandleCaptureResult(result)) continue;

    // Update pending request tracker with returned buffers.
    buffers.insert(buffers.end(), result->output_buffers.begin(),
                   result->output_buffers.end());

    if (result->result_metadata) {
      std::lock_guard<std::mutex> lock(request_record_lock_);
      pending_results_.erase(result->frame_number);
    }

    results_to_callback.push_back(std::move(result));
  }

  {
    std::shared_lock lock(session_callback_lock_);
    session_callback_.process_batch_capture_result(
        std::move(results_to_callback));
  }

  TrackReturnedBuffers(buffers);
}

void CameraDeviceSession::Notify(const NotifyMessage& result) {
  {
    uint32_t frame_number = 0;
    if (result.type == MessageType::kError) {
      frame_number = result.message.error.frame_number;
    } else if (result.type == MessageType::kShutter) {
      frame_number = result.message.shutter.frame_number;
    }
    std::lock_guard<std::mutex> lock(request_record_lock_);
    // Strip out results for frame number that has been notified
    // ErrorCode::kErrorResult and ErrorCode::kErrorBuffer
    if ((error_notified_requests_.find(frame_number) !=
         error_notified_requests_.end()) &&
        (result.type != MessageType::kShutter)) {
      return;
    }

    if (result.type == MessageType::kError &&
        result.message.error.error_code == ErrorCode::kErrorResult) {
      pending_results_.erase(frame_number);

      if (ignore_shutters_.find(frame_number) == ignore_shutters_.end()) {
        ignore_shutters_.insert(frame_number);
      }
    }

    if (result.type == MessageType::kShutter) {
      if (ignore_shutters_.find(frame_number) != ignore_shutters_.end()) {
        ignore_shutters_.erase(frame_number);
        return;
      }
    }
  }

  if (ATRACE_ENABLED() && result.type == MessageType::kShutter) {
    int64_t timestamp_ns_diff = 0;
    int64_t current_timestamp_ns = result.message.shutter.timestamp_ns;
    if (last_timestamp_ns_for_trace_ != 0) {
      timestamp_ns_diff = current_timestamp_ns - last_timestamp_ns_for_trace_;
    }

    last_timestamp_ns_for_trace_ = current_timestamp_ns;

    ATRACE_INT64("sensor_timestamp_diff", timestamp_ns_diff);
    ATRACE_INT("timestamp_frame_number", result.message.shutter.frame_number);
  }

  std::shared_lock lock(session_callback_lock_);
  session_callback_.notify(result);
}

void CameraDeviceSession::InitializeCallbacks() {
  std::lock_guard lock(session_callback_lock_);

  // Initialize callback to
  session_callback_.process_capture_result =
      ProcessCaptureResultFunc([](std::unique_ptr<CaptureResult> /*result*/) {
        ALOGW("%s: No session callback was set.", __FUNCTION__);
      });

  session_callback_.notify = NotifyFunc([](const NotifyMessage& /*message*/) {
    ALOGW("%s: No session callback was set.", __FUNCTION__);
  });

  session_callback_.request_stream_buffers = RequestStreamBuffersFunc(
      [](const std::vector<BufferRequest>& /*hal_buffer_requests*/,
         std::vector<BufferReturn>* /*hal_buffer_returns*/) {
        ALOGW("%s: No session callback was set.", __FUNCTION__);
        return google_camera_hal::BufferRequestStatus::kFailedUnknown;
      });

  session_callback_.return_stream_buffers = ReturnStreamBuffersFunc(
      [](const std::vector<StreamBuffer>& /*return_hal_buffers*/) {
        ALOGW("%s: No session callback was set.", __FUNCTION__);
        return google_camera_hal::BufferRequestStatus::kFailedUnknown;
      });

  camera_device_session_callback_.process_capture_result =
      ProcessCaptureResultFunc([this](std::unique_ptr<CaptureResult> result) {
        ProcessCaptureResult(std::move(result));
      });

  camera_device_session_callback_.process_batch_capture_result =
      ProcessBatchCaptureResultFunc(
          [this](std::vector<std::unique_ptr<CaptureResult>> results) {
            ProcessBatchCaptureResult(std::move(results));
          });

  camera_device_session_callback_.notify =
      NotifyFunc([this](const NotifyMessage& result) { Notify(result); });

  hwl_session_callback_.request_stream_buffers = HwlRequestBuffersFunc(
      [this](int32_t stream_id, uint32_t num_buffers,
             std::vector<StreamBuffer>* buffers, uint32_t frame_number) {
        return RequestBuffersFromStreamBufferCacheManager(
            stream_id, num_buffers, buffers, frame_number);
      });

  hwl_session_callback_.return_stream_buffers =
      HwlReturnBuffersFunc([this](const std::vector<StreamBuffer>& buffers) {
        return ReturnStreamBuffers(buffers);
      });

  device_session_hwl_->SetSessionCallback(hwl_session_callback_);
}

status_t CameraDeviceSession::InitializeBufferManagement(
    HalCameraMetadata* characteristics) {
  ATRACE_CALL();

  if (characteristics == nullptr) {
    ALOGE("%s: characteristics cannot be nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  camera_metadata_ro_entry entry = {};
  status_t res = characteristics->Get(
      ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION, &entry);
  if (res == OK && entry.count > 0) {
    buffer_management_used_ =
        (entry.data.u8[0] ==
         ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION_HIDL_DEVICE_3_5);
    session_buffer_management_supported_ =
        (entry.data.u8[0] ==
         ANDROID_INFO_SUPPORTED_BUFFER_MANAGEMENT_VERSION_SESSION_CONFIGURABLE);
  }

  return OK;
}

status_t CameraDeviceSession::Initialize(
    std::unique_ptr<CameraDeviceSessionHwl> device_session_hwl,
    CameraBufferAllocatorHwl* camera_allocator_hwl,
    std::vector<GetCaptureSessionFactoryFunc> external_session_factory_entries) {
  ATRACE_CALL();
  if (device_session_hwl == nullptr) {
    ALOGE("%s: device_session_hwl cannot be nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }
  measure_buffer_allocation_time_ =
      property_get_bool(kMeasureBufferAllocationProp, false);
  ALOGI("%s: measure buffer allocation time: %d ", __FUNCTION__,
        measure_buffer_allocation_time_);

  camera_id_ = device_session_hwl->GetCameraId();
  device_session_hwl_ = std::move(device_session_hwl);
  camera_allocator_hwl_ = camera_allocator_hwl;

  GraphicBufferMapper::preloadHal();
  InitializeCallbacks();

  std::unique_ptr<google_camera_hal::HalCameraMetadata> characteristics;
  status_t res = device_session_hwl_->GetCameraCharacteristics(&characteristics);
  if (res != OK) {
    ALOGE("%s: Get camera characteristics failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  res = utils::GetStreamUseCases(characteristics.get(), &stream_use_cases_);
  if (res != OK) {
    ALOGE("%s: Initializing stream use case failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  res = InitializeBufferManagement(characteristics.get());
  if (res != OK) {
    ALOGE("%s: Initialize buffer management failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  res = LoadExternalCaptureSession(external_session_factory_entries);
  if (res != OK) {
    ALOGE("%s: Loading external capture sessions failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  InitializeZoomRatioMapper(characteristics.get());

  return OK;
}

status_t GetMaxResDimension(const HalCameraMetadata* characteristics,
                            Dimension& max_res_dimension) {
  Rect active_array_maximum_resolution_size;
  status_t max_res_status = utils::GetSensorActiveArraySize(
      characteristics, &active_array_maximum_resolution_size,
      /*maximum_resolution*/ true);
  if (max_res_status == OK) {
    max_res_dimension = {active_array_maximum_resolution_size.right -
                             active_array_maximum_resolution_size.left + 1,
                         active_array_maximum_resolution_size.bottom -
                             active_array_maximum_resolution_size.top + 1};
  }
  return max_res_status;
}

void CameraDeviceSession::InitializeZoomRatioMapper(
    HalCameraMetadata* characteristics) {
  if (characteristics == nullptr) {
    ALOGE("%s: characteristics cannot be nullptr.", __FUNCTION__);
    return;
  }

  Rect active_array_size;
  status_t res =
      utils::GetSensorActiveArraySize(characteristics, &active_array_size,
                                      /*maximum_resolution*/ false);
  if (res != OK) {
    ALOGE("%s: Failed to get the active array size: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return;
  }

  ZoomRatioMapper::InitParams params;
  params.camera_id = camera_id_;
  params.active_array_dimension = {
      active_array_size.right - active_array_size.left + 1,
      active_array_size.bottom - active_array_size.top + 1};

  // Populate max-res dimension only if the logical camera have max-res resolution
  (void)GetMaxResDimension(characteristics,
                           params.active_array_maximum_resolution_dimension);

  std::vector<uint32_t> physical_camera_ids =
      device_session_hwl_->GetPhysicalCameraIds();
  for (uint32_t id : physical_camera_ids) {
    std::unique_ptr<google_camera_hal::HalCameraMetadata>
        physical_cam_characteristics;
    res = device_session_hwl_->GetPhysicalCameraCharacteristics(
        id, &physical_cam_characteristics);
    if (res != OK) {
      ALOGE("%s: Get camera: %u characteristics failed: %s(%d)", __FUNCTION__,
            id, strerror(-res), res);
      return;
    }

    res = utils::GetSensorActiveArraySize(physical_cam_characteristics.get(),
                                          &active_array_size,
                                          /*maximum_resolution*/ false);
    if (res != OK) {
      ALOGE("%s: Failed to get cam: %u, active array size: %s(%d)",
            __FUNCTION__, id, strerror(-res), res);
      return;
    }
    Dimension active_array_dimension = {
        active_array_size.right - active_array_size.left + 1,
        active_array_size.bottom - active_array_size.top + 1};
    params.physical_cam_active_array_dimension.emplace(id,
                                                       active_array_dimension);
    Dimension max_res_dimension;
    if (GetMaxResDimension(physical_cam_characteristics.get(),
                           max_res_dimension) == OK) {
      params.physical_cam_active_array_maximum_resolution_dimension.emplace(
          id, max_res_dimension);
    }
  }

  res = utils::GetZoomRatioRange(characteristics, &params.zoom_ratio_range);
  if (res != OK) {
    ALOGW("%s: Failed to get the zoom ratio range: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return;
  }

  params.zoom_ratio_mapper_hwl = device_session_hwl_->GetZoomRatioMapperHwl();

  zoom_ratio_mapper_.Initialize(&params);
}

void CameraDeviceSession::DeriveGroupedStreamIdMap() {
  // Group stream ids by stream group id
  std::unordered_map<int32_t, std::vector<int32_t>> group_to_streams_map;
  for (const auto& [stream_id, stream] : configured_streams_map_) {
    if (stream.stream_type == StreamType::kOutput && stream.group_id != -1) {
      group_to_streams_map[stream.group_id].push_back(stream_id);
    }
  }

  // For each stream group, map all the streams' ids to one id
  for (const auto& [group_id, stream_ids] : group_to_streams_map) {
    for (size_t i = 1; i < stream_ids.size(); i++) {
      grouped_stream_id_map_[stream_ids[i]] = stream_ids[0];
    }
  }
}

status_t CameraDeviceSession::LoadExternalCaptureSession(
    std::vector<GetCaptureSessionFactoryFunc> external_session_factory_entries) {
  ATRACE_CALL();

  if (external_capture_session_entries_.size() > 0) {
    ALOGI("%s: External capture session libraries already loaded; skip.",
          __FUNCTION__);
    return OK;
  }

  for (const auto& external_session_factory_t :
       external_session_factory_entries) {
    ExternalCaptureSessionFactory* external_session =
        external_session_factory_t();
    if (!external_session) {
      ALOGE("%s: External session may be incomplete", __FUNCTION__);
      continue;
    }

    external_capture_session_entries_.push_back(external_session);
  }

  return OK;
}

CameraDeviceSession::~CameraDeviceSession() {
  UnregisterThermalCallback();

  capture_session_ = nullptr;
  device_session_hwl_ = nullptr;

  for (auto external_session : external_capture_session_entries_) {
    delete external_session;
  }
  external_capture_session_entries_.clear();

  FreeImportedBufferHandles();
}

void CameraDeviceSession::UnregisterThermalCallback() {
  std::shared_lock lock(session_callback_lock_);
  if (thermal_callback_.unregister_thermal_changed_callback != nullptr) {
    thermal_callback_.unregister_thermal_changed_callback();
  }
}

void CameraDeviceSession::SetSessionCallback(
    const CameraDeviceSessionCallback& session_callback,
    const ThermalCallback& thermal_callback) {
  ATRACE_CALL();
  std::lock_guard lock(session_callback_lock_);
  session_callback_ = session_callback;
  thermal_callback_ = thermal_callback;

  status_t res = thermal_callback_.register_thermal_changed_callback(
      NotifyThrottlingFunc([this](const Temperature& temperature) {
        NotifyThrottling(temperature);
      }),
      /*filter_type=*/false,
      /*type=*/TemperatureType::kUnknown);
  if (res != OK) {
    ALOGW("%s: Registering thermal callback failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
  }
}

void CameraDeviceSession::NotifyThrottling(const Temperature& temperature) {
  switch (temperature.throttling_status) {
    case ThrottlingSeverity::kNone:
    case ThrottlingSeverity::kLight:
    case ThrottlingSeverity::kModerate:
      ALOGI("%s: temperature type: %d, severity: %u, value: %f", __FUNCTION__,
            temperature.type, temperature.throttling_status, temperature.value);
      return;
    case ThrottlingSeverity::kSevere:
    case ThrottlingSeverity::kCritical:
    case ThrottlingSeverity::kEmergency:
    case ThrottlingSeverity::kShutdown:
      ALOGW("%s: temperature type: %d, severity: %u, value: %f", __FUNCTION__,
            temperature.type, temperature.throttling_status, temperature.value);
      {
        std::lock_guard<std::mutex> lock(session_lock_);
        thermal_throttling_ = true;
      }
      return;
    default:
      ALOGE("%s: Unknown throttling status %u for type %d", __FUNCTION__,
            temperature.throttling_status, temperature.type);
      return;
  }
}

status_t CameraDeviceSession::ConstructDefaultRequestSettings(
    RequestTemplate type, std::unique_ptr<HalCameraMetadata>* default_settings) {
  ATRACE_CALL();
  status_t res = device_session_hwl_->ConstructDefaultRequestSettings(
      type, default_settings);
  if (res != OK) {
    ALOGE("%s: Construct default settings for type %d failed: %s(%d)",
          __FUNCTION__, type, strerror(-res), res);
    return res;
  }

  return hal_vendor_tag_utils::ModifyDefaultRequestSettings(
      type, default_settings->get());
}

status_t CameraDeviceSession::ConfigureStreams(
    const StreamConfiguration& stream_config, bool v2,
    ConfigureStreamsReturn* configured_streams) {
  ATRACE_CALL();
  bool set_realtime_thread = false;
  int32_t schedule_policy;
  struct sched_param schedule_param = {0};
  if (configured_streams == nullptr) {
    ALOGE("%s: configured_streams output is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }
  std::vector<HalStream>& hal_config = configured_streams->hal_streams;
  if (utils::SupportRealtimeThread()) {
    bool get_thread_schedule = false;
    if (pthread_getschedparam(pthread_self(), &schedule_policy,
                              &schedule_param) == 0) {
      get_thread_schedule = true;
    } else {
      ALOGE("%s: pthread_getschedparam fail", __FUNCTION__);
    }

    if (get_thread_schedule) {
      status_t res = utils::SetRealtimeThread(pthread_self());
      if (res != OK) {
        ALOGE("%s: SetRealtimeThread fail", __FUNCTION__);
      } else {
        set_realtime_thread = true;
      }
    }
  }

  std::lock_guard<std::mutex> lock(session_lock_);

  std::lock_guard lock_capture_session(capture_session_lock_);
  if (capture_session_ != nullptr) {
    ATRACE_NAME("CameraDeviceSession::DestroyOldSession");
    capture_session_ = nullptr;
  }

  pending_requests_tracker_ = nullptr;

  if (!configured_streams_map_.empty()) {
    CleanupStaleStreamsLocked(stream_config.streams);
  }
  hal_buffer_managed_stream_ids_.clear();

  hal_utils::DumpStreamConfiguration(stream_config, "App stream configuration");

  multi_res_reprocess_ = stream_config.multi_resolution_input_image;

  // TODO: We would ideally want this to be a part of CreateCaptureSession,
  // which internally calls IsStreamCombinationSupported. However this
  // IsStreamCombinationSupported doesn't match the
  // CameraDevice::IsStreamCombination. We should look at unifying the two for a
  // potentially cleaner code-base.
  if (!utils::IsStreamUseCaseSupported(stream_config, stream_use_cases_)) {
    return BAD_VALUE;
  }
  device_session_hwl_->setConfigureStreamsV2(v2);
  bool multi_resolution_stream_used = false;
  for (const auto& stream : stream_config.streams) {
    if (stream.group_id != -1) {
      multi_resolution_stream_used = true;
      break;
    }
  }
  capture_session_ = CreateCaptureSession(
      stream_config, kWrapperCaptureSessionEntries,
      external_capture_session_entries_, kCaptureSessionEntries,
      hwl_session_callback_, camera_allocator_hwl_, device_session_hwl_.get(),
      &hal_config, camera_device_session_callback_.process_capture_result,
      camera_device_session_callback_.notify,
      camera_device_session_callback_.process_batch_capture_result);

  if (capture_session_ == nullptr) {
    ALOGE("%s: Cannot find a capture session compatible with stream config",
          __FUNCTION__);
    if (set_realtime_thread) {
      utils::UpdateThreadSched(pthread_self(), schedule_policy, &schedule_param);
    }
    return BAD_VALUE;
  }
  // Backup the streams received from frameworks into configured_streams_map_,
  // and we can find out specific streams through stream id in output_buffers.
  for (auto& stream : stream_config.streams) {
    configured_streams_map_[stream.id] = stream;
  }
  if (session_buffer_management_supported_ && v2) {
    std::set<int32_t> hal_buffer_managed_stream_ids =
        device_session_hwl_->GetHalBufferManagedStreams(stream_config);
    hal_buffer_managed_stream_ids_ = hal_buffer_managed_stream_ids;
    for (auto& hal_stream : hal_config) {
      if (hal_buffer_managed_stream_ids.find(hal_stream.id) !=
          hal_buffer_managed_stream_ids.end()) {
        hal_stream.is_hal_buffer_managed = true;
      }
    }
  } else if (buffer_management_used_ || multi_resolution_stream_used) {
    // No session specific hal buffer manager supported, all streams are
    // hal buffer managed. In the case of multi resolution streams we also
    // are mandated to use hal buffer manager (VTS asserts for this)
    for (auto& hal_stream : hal_config) {
      if (configured_streams_map_.find(hal_stream.id) ==
          configured_streams_map_.end()) {
        ALOGE("%s: HalStream id %d not found in configured streams map",
              __FUNCTION__, hal_stream.id);
        return UNKNOWN_ERROR;
      }
      if (configured_streams_map_[hal_stream.id].stream_type ==
          StreamType::kInput) {
        continue;
      }
      hal_stream.is_hal_buffer_managed = true;
      hal_buffer_managed_stream_ids_.insert(hal_stream.id);
    }
  }

  stream_buffer_cache_manager_ =
      StreamBufferCacheManager::Create(hal_buffer_managed_stream_ids_);
  if (stream_buffer_cache_manager_ == nullptr) {
    ALOGE("%s: Failed to create stream buffer cache manager.", __FUNCTION__);
    if (set_realtime_thread) {
      utils::UpdateThreadSched(pthread_self(), schedule_policy, &schedule_param);
    }
    return UNKNOWN_ERROR;
  }
  if (hal_buffer_managed_stream_ids_.size() != 0) {
    status_t res =
        RegisterStreamsIntoCacheManagerLocked(stream_config, hal_config);
    if (res != OK) {
      ALOGE("%s: Failed to register streams into stream buffer cache manager.",
            __FUNCTION__);
      if (set_realtime_thread) {
        utils::UpdateThreadSched(pthread_self(), schedule_policy,
                                 &schedule_param);
      }
      return res;
    }
  }

  // (b/129561652): Framework assumes HalStream is sorted.
  std::sort(hal_config.begin(), hal_config.end(),
            [](const HalStream& a, const HalStream& b) { return a.id < b.id; });

  // Derives all stream ids within a group to a representative stream id
  DeriveGroupedStreamIdMap();

  // If buffer management is support, create a pending request tracker for
  // capture request throttling.
  pending_requests_tracker_ = PendingRequestsTracker::Create(
      hal_config, grouped_stream_id_map_, hal_buffer_managed_stream_ids_);
  if (pending_requests_tracker_ == nullptr) {
    ALOGE("%s: Cannot create a pending request tracker.", __FUNCTION__);
    if (set_realtime_thread) {
      utils::UpdateThreadSched(pthread_self(), schedule_policy, &schedule_param);
    }
    return UNKNOWN_ERROR;
  }

  {
    std::lock_guard<std::mutex> request_lock(request_record_lock_);
    pending_request_streams_.clear();
    error_notified_requests_.clear();
    dummy_buffer_observed_.clear();
    pending_results_.clear();
    ignore_shutters_.clear();
  }

  has_valid_settings_ = false;
  thermal_throttling_ = false;
  thermal_throttling_notified_ = false;
  last_request_settings_ = nullptr;
  last_timestamp_ns_for_trace_ = 0;

  if (set_realtime_thread) {
    utils::UpdateThreadSched(pthread_self(), schedule_policy, &schedule_param);
  }
  return OK;
}

status_t CameraDeviceSession::UpdateBufferHandlesLocked(
    std::vector<StreamBuffer>* buffers, bool update_hal_buffer_managed_streams) {
  ATRACE_CALL();
  if (buffers == nullptr) {
    ALOGE("%s: buffers cannot be nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  for (auto& buffer : *buffers) {
    bool is_hal_buffer_managed =
        hal_buffer_managed_stream_ids_.find(buffer.stream_id) !=
        hal_buffer_managed_stream_ids_.end();
    // Skip the update if import_hal_buffer_managed_streams doesn't match the
    //  stream ids hal buffer manager behavior.
    bool skip = (!is_hal_buffer_managed && update_hal_buffer_managed_streams) ||
                (!update_hal_buffer_managed_streams && is_hal_buffer_managed);
    if (skip) {
      continue;
    }
    // Get the buffer handle from buffer handle map.
    BufferCache buffer_cache = {buffer.stream_id, buffer.buffer_id};
    auto buffer_handle_it = imported_buffer_handle_map_.find(buffer_cache);
    if (buffer_handle_it == imported_buffer_handle_map_.end()) {
      ALOGE("%s: Cannot find buffer handle for stream %u, buffer %" PRIu64,
            __FUNCTION__, buffer.stream_id, buffer.buffer_id);
      return NAME_NOT_FOUND;
    }

    buffer.buffer = buffer_handle_it->second;
  }

  return OK;
}

status_t CameraDeviceSession::CreateCaptureRequestLocked(
    const CaptureRequest& request, CaptureRequest* updated_request) {
  ATRACE_CALL();
  if (updated_request == nullptr) {
    return BAD_VALUE;
  }

  if (request.settings != nullptr) {
    last_request_settings_ = HalCameraMetadata::Clone(request.settings.get());
  }

  updated_request->frame_number = request.frame_number;
  updated_request->settings = HalCameraMetadata::Clone(request.settings.get());
  updated_request->input_buffers = request.input_buffers;
  updated_request->input_buffer_metadata.clear();
  updated_request->output_buffers = request.output_buffers;
  std::vector<uint32_t> physical_camera_ids =
      device_session_hwl_->GetPhysicalCameraIds();
  for (auto& [camid, physical_setting] : request.physical_camera_settings) {
    if (std::find(physical_camera_ids.begin(), physical_camera_ids.end(),
                  camid) == physical_camera_ids.end()) {
      ALOGE("%s: Pyhsical camera id %d in request had not registered",
            __FUNCTION__, camid);
      return BAD_VALUE;
    }
    updated_request->physical_camera_settings[camid] =
        HalCameraMetadata::Clone(physical_setting.get());
  }
  updated_request->input_width = request.input_width;
  updated_request->input_height = request.input_height;

  // Returns -1 if kThermalThrottling is not defined, skip following process.
  if (get_camera_metadata_tag_type(VendorTagIds::kThermalThrottling) != -1) {
    // Create settings to set thermal throttling key if needed.
    if (thermal_throttling_ && !thermal_throttling_notified_ &&
        updated_request->settings == nullptr) {
      updated_request->settings =
          HalCameraMetadata::Clone(last_request_settings_.get());
      thermal_throttling_notified_ = true;
    }

    if (updated_request->settings != nullptr) {
      status_t res = updated_request->settings->Set(
          VendorTagIds::kThermalThrottling, &thermal_throttling_,
          /*data_count=*/1);
      if (res != OK) {
        ALOGE("%s: Setting thermal throttling key failed: %s(%d)", __FUNCTION__,
              strerror(-res), res);
        return res;
      }
    }
  }

  AppendOutputIntentToSettingsLocked(request, updated_request);

  {
    std::lock_guard<std::mutex> lock(imported_buffer_handle_map_lock_);

    status_t res = UpdateBufferHandlesLocked(&updated_request->input_buffers);
    if (res != OK) {
      ALOGE("%s: Updating input buffer handles failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return res;
    }

    res = UpdateBufferHandlesLocked(&updated_request->output_buffers);
    if (res != OK) {
      ALOGE("%s: Updating output buffer handles failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return res;
    }
  }

  zoom_ratio_mapper_.UpdateCaptureRequest(updated_request);

  return OK;
}

status_t CameraDeviceSession::ImportBufferHandleLocked(
    const StreamBuffer& buffer) {
  ATRACE_CALL();
  buffer_handle_t imported_buffer_handle;

  status_t status = GraphicBufferMapper::get().importBufferNoValidate(
      buffer.buffer, &imported_buffer_handle);
  if (status != OK) {
    ALOGE("%s: Importing buffer failed: %s", __FUNCTION__,
          ::android::statusToString(status).c_str());
    return UNKNOWN_ERROR;
  }

  BufferCache buffer_cache = {buffer.stream_id, buffer.buffer_id};
  return AddImportedBufferHandlesLocked(buffer_cache, imported_buffer_handle);
}

status_t CameraDeviceSession::ImportBufferHandles(
    const std::vector<StreamBuffer>& buffers) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(imported_buffer_handle_map_lock_);

  // Import buffers that are new to HAL.
  for (auto& buffer : buffers) {
    bool is_hal_buffer_managed =
        hal_buffer_managed_stream_ids_.find(buffer.stream_id) !=
        hal_buffer_managed_stream_ids_.end();
    // Skip the update if import_hal_buffer_managed_streams doesn't match the
    //  stream ids hal buffer manager behavior.
    if (is_hal_buffer_managed) {
      ALOGV(
          "%s: Buffer management is enabled. Skip importing buffer for stream "
          "id"
          " %d in request ",
          __FUNCTION__, buffer.stream_id);
      continue;
    }
    if (!IsBufferImportedLocked(buffer.stream_id, buffer.buffer_id)) {
      status_t res = ImportBufferHandleLocked(buffer);

      if (res != OK) {
        ALOGE("%s: Importing buffer %" PRIu64 " from stream %d failed: %s(%d)",
              __FUNCTION__, buffer.buffer_id, buffer.stream_id, strerror(-res),
              res);
        return res;
      }
    }
  }

  return OK;
}

status_t CameraDeviceSession::ImportRequestBufferHandles(
    const CaptureRequest& request) {
  ATRACE_CALL();

  status_t res = ImportBufferHandles(request.input_buffers);
  if (res != OK) {
    ALOGE("%s: Importing input buffer handles failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  res = ImportBufferHandles(request.output_buffers);
  if (res != OK) {
    ALOGE("%s: Importing output buffer handles failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  return OK;
}

void CameraDeviceSession::NotifyErrorMessage(uint32_t frame_number,
                                             int32_t stream_id,
                                             ErrorCode error_code) {
  ALOGI("%s: [sbc] Request %d with stream (%d), return error code (%d)",
        __FUNCTION__, frame_number, stream_id, error_code);

  if ((error_code == ErrorCode::kErrorResult ||
       error_code == ErrorCode::kErrorRequest) &&
      stream_id != kInvalidStreamId) {
    ALOGW("%s: [sbc] Request %d reset setream id again", __FUNCTION__,
          frame_number);
    stream_id = kInvalidStreamId;
  }
  NotifyMessage message = {.type = MessageType::kError,
                           .message.error = {.frame_number = frame_number,
                                             .error_stream_id = stream_id,
                                             .error_code = error_code}};

  std::shared_lock lock(session_callback_lock_);
  session_callback_.notify(message);
}

status_t CameraDeviceSession::TryHandleDummyResult(CaptureResult* result,
                                                   bool* result_handled) {
  if (result == nullptr || result_handled == nullptr) {
    ALOGE("%s: result or result_handled is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  uint32_t frame_number = result->frame_number;
  *result_handled = false;
  bool need_to_handle_result = false;
  bool need_to_notify_error_result = false;
  {
    std::lock_guard<std::mutex> lock(request_record_lock_);
    if (error_notified_requests_.find(frame_number) ==
        error_notified_requests_.end()) {
      for (auto& stream_buffer : result->output_buffers) {
        if (dummy_buffer_observed_.find(stream_buffer.buffer) !=
            dummy_buffer_observed_.end()) {
          error_notified_requests_.insert(frame_number);
          if (pending_results_.find(frame_number) != pending_results_.end()) {
            need_to_notify_error_result = true;
            pending_results_.erase(frame_number);
            if (ignore_shutters_.find(frame_number) == ignore_shutters_.end()) {
              ignore_shutters_.insert(frame_number);
            }
          }
          need_to_handle_result = true;
          break;
        }
      }
    } else {
      need_to_handle_result = true;
    }
  }

  if (need_to_notify_error_result) {
    NotifyErrorMessage(frame_number, kInvalidStreamId, ErrorCode::kErrorResult);
  }

  if (need_to_handle_result) {
    for (auto& stream_buffer : result->output_buffers) {
      bool is_dummy_buffer = false;
      if (hal_buffer_managed_stream_ids_.find(stream_buffer.stream_id) ==
          hal_buffer_managed_stream_ids_.end()) {
        // No need to handle non HAL buffer managed streams here
        continue;
      }
      {
        std::lock_guard<std::mutex> lock(request_record_lock_);
        is_dummy_buffer = (dummy_buffer_observed_.find(stream_buffer.buffer) !=
                           dummy_buffer_observed_.end());
      }

      uint64_t buffer_id = (is_dummy_buffer ? /*Use invalid for dummy*/ 0
                                            : stream_buffer.buffer_id);
      // To avoid publishing duplicated error buffer message, only publish
      // it here when getting normal buffer status from HWL
      if (stream_buffer.status == BufferStatus::kOk) {
        NotifyErrorMessage(frame_number, stream_buffer.stream_id,
                           ErrorCode::kErrorBuffer);
      }
      NotifyBufferError(frame_number, stream_buffer.stream_id, buffer_id);
    }

    std::vector<StreamBuffer> buffers;
    buffers.insert(buffers.end(), result->output_buffers.begin(),
                   result->output_buffers.end());

    if (!buffers.empty()) {
      if (pending_requests_tracker_->TrackReturnedResultBuffers(buffers) != OK) {
        ALOGE("%s: Tracking requested quota buffers failed", __FUNCTION__);
      }
      std::vector<StreamBuffer> acquired_buffers;
      {
        std::lock_guard<std::mutex> lock(request_record_lock_);
        for (auto& buffer : buffers) {
          if (hal_buffer_managed_stream_ids_.find(buffer.stream_id) ==
              hal_buffer_managed_stream_ids_.end()) {
            // non HAL buffer managed stream buffers are not tracked by pending
            // requests tracker
            continue;
          }
          if (dummy_buffer_observed_.find(buffer.buffer) ==
              dummy_buffer_observed_.end()) {
            acquired_buffers.push_back(buffer);
          }
        }
      }

      if (pending_requests_tracker_->TrackReturnedAcquiredBuffers(
              acquired_buffers) != OK) {
        ALOGE("%s: Tracking requested acquired buffers failed", __FUNCTION__);
      }
    }
  }

  *result_handled = need_to_handle_result;
  return OK;
}

void CameraDeviceSession::NotifyBufferError(const CaptureRequest& request) {
  ALOGI("%s: [sbc] Return Buffer Error Status for frame %d", __FUNCTION__,
        request.frame_number);

  auto result = std::make_unique<CaptureResult>(CaptureResult({}));
  result->frame_number = request.frame_number;
  for (auto& stream_buffer : request.output_buffers) {
    StreamBuffer buffer;
    buffer.stream_id = stream_buffer.stream_id;
    buffer.status = BufferStatus::kError;
    result->output_buffers.push_back(buffer);
  }
  for (auto& stream_buffer : request.input_buffers) {
    result->input_buffers.push_back(stream_buffer);
  }
  result->partial_result = 1;

  std::shared_lock lock(session_callback_lock_);
  session_callback_.process_capture_result(std::move(result));
}

void CameraDeviceSession::NotifyBufferError(uint32_t frame_number,
                                            int32_t stream_id,
                                            uint64_t buffer_id) {
  ALOGI("%s: [sbc] Return Buffer Error Status for frame %d stream %d",
        __FUNCTION__, frame_number, stream_id);

  auto result = std::make_unique<CaptureResult>(CaptureResult({}));
  result->frame_number = frame_number;
  StreamBuffer stream_buffer;
  stream_buffer.buffer_id = buffer_id;
  stream_buffer.stream_id = stream_id;
  stream_buffer.status = BufferStatus::kError;
  result->output_buffers.push_back(stream_buffer);
  result->partial_result = 1;

  std::shared_lock lock(session_callback_lock_);
  session_callback_.process_capture_result(std::move(result));
}

status_t CameraDeviceSession::HandleSBCInactiveStreams(
    const CaptureRequest& request, bool* all_active) {
  if (all_active == nullptr) {
    ALOGE("%s: all_active is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  *all_active = true;
  for (auto& stream_buffer : request.output_buffers) {
    bool is_hal_buffer_managed =
        hal_buffer_managed_stream_ids_.find(stream_buffer.stream_id) !=
        hal_buffer_managed_stream_ids_.end();
    if (!is_hal_buffer_managed) {
      continue;
    }
    bool is_active = true;
    status_t res = stream_buffer_cache_manager_->IsStreamActive(
        stream_buffer.stream_id, &is_active);
    if (res != OK) {
      ALOGE("%s: Failed to check if stream is active, error status: %s(%d)",
            __FUNCTION__, strerror(-res), res);
      return UNKNOWN_ERROR;
    }
    if (!is_active) {
      ALOGI("%s: Stream %d is not active when submitting frame %d request.",
            __FUNCTION__, stream_buffer.stream_id, request.frame_number);
      *all_active = false;
      break;
    }
  }
  if (*all_active == false) {
    NotifyErrorMessage(request.frame_number, kInvalidStreamId,
                       ErrorCode::kErrorRequest);
    NotifyBufferError(request);
  }

  return OK;
}

void CameraDeviceSession::CheckRequestForStreamBufferCacheManager(
    const CaptureRequest& request, bool* need_to_process) {
  ATRACE_CALL();

  // If any stream in the stream buffer cache manager has been labeld as inactive,
  // return ERROR_REQUEST immediately. No need to send the request to HWL.
  status_t res = HandleSBCInactiveStreams(request, need_to_process);
  if (res != OK) {
    ALOGE("%s: Failed to check if streams are active.", __FUNCTION__);
    return;
  }

  // Note: This function should only be called if buffer_management_used_
  // is true.
  if (pending_request_streams_.empty()) {
    pending_requests_tracker_->OnBufferCacheFlushed();
  }

  // Add streams into pending_request_streams_
  uint32_t frame_number = request.frame_number;
  if (*need_to_process) {
    std::lock_guard<std::mutex> lock(request_record_lock_);
    pending_results_.insert(frame_number);
    for (auto& stream_buffer : request.output_buffers) {
      bool is_hal_buffer_managed =
          hal_buffer_managed_stream_ids_.find(stream_buffer.stream_id) !=
          hal_buffer_managed_stream_ids_.end();
      if (!is_hal_buffer_managed) {
        // pending_request_streams_ tracks only hal buffer managed streams.
        continue;
      }
      if (grouped_stream_id_map_.count(stream_buffer.stream_id) == 1) {
        pending_request_streams_[frame_number].insert(
            grouped_stream_id_map_.at(stream_buffer.stream_id));
      } else {
        pending_request_streams_[frame_number].insert(stream_buffer.stream_id);
      }
    }
  }
}

status_t CameraDeviceSession::ValidateRequestLocked(
    const CaptureRequest& request) {
  // First request must have valid settings.
  if (!has_valid_settings_) {
    if (request.settings == nullptr ||
        request.settings->GetCameraMetadataSize() == 0) {
      ALOGE("%s: First request must have valid settings", __FUNCTION__);
      return BAD_VALUE;
    }

    has_valid_settings_ = true;
  }

  if (request.output_buffers.empty()) {
    ALOGE("%s: there is no output buffer.", __FUNCTION__);
    return BAD_VALUE;
  }

  // Check all output streams are configured.
  for (auto& buffer : request.input_buffers) {
    if (configured_streams_map_.find(buffer.stream_id) ==
        configured_streams_map_.end()) {
      ALOGE("%s: input stream %d is not configured.", __FUNCTION__,
            buffer.stream_id);
      return BAD_VALUE;
    }
    const Stream& input_stream = configured_streams_map_.at(buffer.stream_id);
    if (!multi_res_reprocess_ && (request.input_width != input_stream.width ||
                                  request.input_height != input_stream.height)) {
      ALOGE("%s: Invalid input size [%d, %d], expected [%d, %d]", __FUNCTION__,
            request.input_width, request.input_height, input_stream.width,
            input_stream.height);
      return BAD_VALUE;
    }
  }
  if (request.input_buffers.size() > 0) {
    if (multi_res_reprocess_ &&
        (request.input_width == 0 || request.input_height == 0)) {
      ALOGE(
          "%s: Session is a multi-res input session, but has invalid input "
          "size [%d, %d]",
          __FUNCTION__, request.input_width, request.input_height);
      return BAD_VALUE;
    }
  }

  for (auto& buffer : request.output_buffers) {
    if (configured_streams_map_.find(buffer.stream_id) ==
        configured_streams_map_.end()) {
      ALOGE("%s: output stream %d is not configured.", __FUNCTION__,
            buffer.stream_id);
      return BAD_VALUE;
    }
  }

  return OK;
}

status_t CameraDeviceSession::ProcessCaptureRequest(
    const std::vector<CaptureRequest>& requests,
    uint32_t* num_processed_requests) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(session_lock_);
  if (num_processed_requests == nullptr) {
    return BAD_VALUE;
  }

  if (requests.empty()) {
    ALOGE("%s: requests is empty.", __FUNCTION__);
    return BAD_VALUE;
  }

  status_t res;
  *num_processed_requests = 0;

  for (auto& request : requests) {
    if (ATRACE_ENABLED()) {
      ATRACE_INT("request_frame_number", request.frame_number);
    }

    res = ValidateRequestLocked(request);
    if (res != OK) {
      ALOGE("%s: Request %d is not valid.", __FUNCTION__, request.frame_number);
      return res;
    }

    res = ImportRequestBufferHandles(request);
    if (res != OK) {
      ALOGE("%s: Importing request buffer handles failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return res;
    }

    CaptureRequest updated_request;
    res = CreateCaptureRequestLocked(request, &updated_request);
    if (res != OK) {
      ALOGE("%s: Updating buffer handles failed for frame %u", __FUNCTION__,
            request.frame_number);
      return res;
    }

    bool need_to_process = true;
    // If a processCaptureRequest() call is made during flushing,
    // notify CAMERA3_MSG_ERROR_REQUEST directly.
    if (is_flushing_) {
      NotifyErrorMessage(request.frame_number, kInvalidStreamId,
                         ErrorCode::kErrorRequest);
      NotifyBufferError(request);
      need_to_process = false;
    } else if (hal_buffer_managed_stream_ids_.size() != 0) {
      CheckRequestForStreamBufferCacheManager(updated_request, &need_to_process);
    }

    if (need_to_process) {
      // For HAL buffer managed streams, framework does not throttle requests
      // with stream's max buffers. We need to throttle on our own.
      std::vector<int32_t> first_requested_stream_ids;

      res = pending_requests_tracker_->WaitAndTrackRequestBuffers(
          updated_request, &first_requested_stream_ids);
      if (res != OK) {
        ALOGE("%s: Waiting until capture ready failed: %s(%d)", __FUNCTION__,
              strerror(-res), res);
        return res;
      }

      for (auto& stream_id : first_requested_stream_ids) {
        ALOGI("%s: [sbc] Stream %d 1st req arrived, notify SBC Manager.",
              __FUNCTION__, stream_id);
        res = stream_buffer_cache_manager_->NotifyProviderReadiness(stream_id);
        if (res != OK) {
          ALOGE("%s: Notifying provider readiness failed: %s(%d)", __FUNCTION__,
                strerror(-res), res);
          return res;
        }
      }

      // Check the flush status again to prevent flush being called while we are
      // waiting for the request buffers(request throttling).
      if (is_flushing_) {
        std::vector<StreamBuffer> buffers = updated_request.output_buffers;
        {
          std::lock_guard<std::mutex> request_lock(request_record_lock_);
          pending_request_streams_.erase(updated_request.frame_number);
          pending_results_.erase(updated_request.frame_number);
        }
        NotifyErrorMessage(updated_request.frame_number, kInvalidStreamId,
                           ErrorCode::kErrorRequest);
        NotifyBufferError(updated_request);
        if (pending_requests_tracker_->TrackReturnedResultBuffers(buffers) !=
            OK) {
          ALOGE("%s: Tracking requested quota buffers failed", __FUNCTION__);
        }
      } else {
        std::shared_lock session_lock(capture_session_lock_);
        if (capture_session_ == nullptr) {
          ALOGE("%s: Capture session wasn't created.", __FUNCTION__);
          return NO_INIT;
        }

        res = capture_session_->ProcessRequest(updated_request);
        if (res != OK) {
          ALOGE("%s: Submitting request to HWL session failed: %s (%d)",
                __FUNCTION__, strerror(-res), res);
          return res;
        }
      }
    }

    (*num_processed_requests)++;
  }

  return OK;
}

bool CameraDeviceSession::IsBufferImportedLocked(int32_t stream_id,
                                                 uint32_t buffer_id) {
  BufferCache buffer_cache = {stream_id, buffer_id};
  return imported_buffer_handle_map_.find(buffer_cache) !=
         imported_buffer_handle_map_.end();
}

status_t CameraDeviceSession::AddImportedBufferHandlesLocked(
    const BufferCache& buffer_cache, buffer_handle_t buffer_handle) {
  ATRACE_CALL();
  auto buffer_handle_it = imported_buffer_handle_map_.find(buffer_cache);
  if (buffer_handle_it == imported_buffer_handle_map_.end()) {
    // Add a new buffer cache if it doesn't exist.
    imported_buffer_handle_map_.emplace(buffer_cache, buffer_handle);
  } else if (buffer_handle_it->second != buffer_handle) {
    ALOGE(
        "%s: Cached buffer handle %p doesn't match %p for stream %u buffer "
        "%" PRIu64,
        __FUNCTION__, buffer_handle_it->second, buffer_handle,
        buffer_cache.stream_id, buffer_cache.buffer_id);
    return BAD_VALUE;
  }

  return OK;
}

void CameraDeviceSession::RemoveBufferCache(
    const std::vector<BufferCache>& buffer_caches) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(imported_buffer_handle_map_lock_);

  for (auto& buffer_cache : buffer_caches) {
    auto buffer_handle_it = imported_buffer_handle_map_.find(buffer_cache);
    if (buffer_handle_it == imported_buffer_handle_map_.end()) {
      ALOGW("%s: Could not find buffer cache for stream %u buffer %" PRIu64,
            __FUNCTION__, buffer_cache.stream_id, buffer_cache.buffer_id);
      continue;
    }

    device_session_hwl_->RemoveCachedBuffers(buffer_handle_it->second);

    status_t res =
        GraphicBufferMapper::get().freeBuffer(buffer_handle_it->second);
    if (res != OK) {
      ALOGE("%s: Freeing imported buffer failed: %s", __FUNCTION__,
            ::android::statusToString(res).c_str());
    }

    imported_buffer_handle_map_.erase(buffer_handle_it);
  }
}

void CameraDeviceSession::FreeBufferHandlesLocked(int32_t stream_id) {
  for (auto buffer_handle_it = imported_buffer_handle_map_.begin();
       buffer_handle_it != imported_buffer_handle_map_.end();) {
    if (buffer_handle_it->first.stream_id == stream_id) {
      status_t res =
          GraphicBufferMapper::get().freeBuffer(buffer_handle_it->second);
      if (res != OK) {
        ALOGE("%s: Freeing imported buffer failed: %s", __FUNCTION__,
              ::android::statusToString(res).c_str());
      }
      buffer_handle_it = imported_buffer_handle_map_.erase(buffer_handle_it);
    } else {
      buffer_handle_it++;
    }
  }
}

void CameraDeviceSession::FreeImportedBufferHandles() {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(imported_buffer_handle_map_lock_);

  auto& mapper = GraphicBufferMapper::get();
  for (auto buffer_handle_it : imported_buffer_handle_map_) {
    status_t status = mapper.freeBuffer(buffer_handle_it.second);
    if (status != OK) {
      ALOGE("%s: Freeing imported buffer failed: %s", __FUNCTION__,
            ::android::statusToString(status).c_str());
    }
  }

  imported_buffer_handle_map_.clear();
}

void CameraDeviceSession::CleanupStaleStreamsLocked(
    const std::vector<Stream>& new_streams) {
  for (auto stream_it = configured_streams_map_.begin();
       stream_it != configured_streams_map_.end();) {
    int32_t stream_id = stream_it->first;
    bool found = false;
    for (const Stream& stream : new_streams) {
      if (stream.id == stream_id) {
        found = true;
        break;
      }
    }
    if (!found) {
      std::lock_guard<std::mutex> lock(imported_buffer_handle_map_lock_);
      stream_it = configured_streams_map_.erase(stream_it);
      FreeBufferHandlesLocked(stream_id);
    } else {
      stream_it++;
    }
  }
}

status_t CameraDeviceSession::Flush() {
  ATRACE_CALL();
  std::shared_lock lock(capture_session_lock_);
  if (capture_session_ == nullptr) {
    return OK;
  }

  is_flushing_ = true;
  status_t res = capture_session_->Flush();
  is_flushing_ = false;

  return res;
}

void CameraDeviceSession::AppendOutputIntentToSettingsLocked(
    const CaptureRequest& request, CaptureRequest* updated_request) {
  if (updated_request == nullptr || updated_request->settings == nullptr) {
    // The frameworks may have no settings and just do nothing here.
    return;
  }

  bool has_video = false;
  bool has_snapshot = false;
  bool has_zsl = false;

  // From request output_buffers to find stream id and then find the stream.
  for (auto& buffer : request.output_buffers) {
    auto stream = configured_streams_map_.find(buffer.stream_id);
    if (stream != configured_streams_map_.end()) {
      if (utils::IsVideoStream(stream->second)) {
        has_video = true;
      } else if (utils::IsJPEGSnapshotStream(stream->second)) {
        has_snapshot = true;
      }
    }
  }

  for (auto& buffer : request.input_buffers) {
    auto stream = configured_streams_map_.find(buffer.stream_id);
    if (stream != configured_streams_map_.end()) {
      if ((stream->second.usage & GRALLOC_USAGE_HW_CAMERA_ZSL) != 0) {
        has_zsl = true;
        break;
      }
    }
  }

  uint8_t output_intent = static_cast<uint8_t>(OutputIntent::kPreview);

  if (has_video && has_snapshot) {
    output_intent = static_cast<uint8_t>(OutputIntent::kVideoSnapshot);
  } else if (has_snapshot) {
    output_intent = static_cast<uint8_t>(OutputIntent::kSnapshot);
  } else if (has_video) {
    output_intent = static_cast<uint8_t>(OutputIntent::kVideo);
  } else if (has_zsl) {
    output_intent = static_cast<uint8_t>(OutputIntent::kZsl);
  }

  // Used to indicate the possible start and end of video recording in traces
  if (has_video && !prev_output_intent_has_video_) {
    ATRACE_NAME("Start Video Streaming");
  } else if (prev_output_intent_has_video_ && !has_video) {
    ATRACE_NAME("Stop Video Streaming");
  }

  prev_output_intent_has_video_ = has_video;

  status_t res = updated_request->settings->Set(VendorTagIds::kOutputIntent,
                                                &output_intent,
                                                /*data_count=*/1);
  if (res != OK) {
    ALOGE("%s: Failed to set vendor tag OutputIntent: %s(%d).", __FUNCTION__,
          strerror(-res), res);
  }
}

status_t CameraDeviceSession::IsReconfigurationRequired(
    const HalCameraMetadata* old_session, const HalCameraMetadata* new_session,
    bool* reconfiguration_required) {
  if (old_session == nullptr || new_session == nullptr ||
      reconfiguration_required == nullptr) {
    ALOGE(
        "%s: old_session or new_session or reconfiguration_required is "
        "nullptr.",
        __FUNCTION__);
    return BAD_VALUE;
  }

  return device_session_hwl_->IsReconfigurationRequired(
      old_session, new_session, reconfiguration_required);
}

status_t CameraDeviceSession::UpdateRequestedBufferHandles(
    std::vector<StreamBuffer>* buffers) {
  if (buffers == nullptr) {
    ALOGE("%s: buffer is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  std::lock_guard<std::mutex> lock(imported_buffer_handle_map_lock_);

  status_t res;
  for (auto& buffer : *buffers) {
    // If buffer handle is not nullptr, we need to add the new buffer handle
    // to buffer cache.
    if (buffer.buffer != nullptr) {
      BufferCache buffer_cache = {buffer.stream_id, buffer.buffer_id};
      res = AddImportedBufferHandlesLocked(buffer_cache, buffer.buffer);
      if (res != OK) {
        ALOGE("%s: Adding imported buffer handle failed: %s(%d)", __FUNCTION__,
              strerror(-res), res);
        return res;
      }
    }
  }

  res = UpdateBufferHandlesLocked(buffers,
                                  /*update_hal_buffer_managed_streams=*/true);
  if (res != OK) {
    ALOGE("%s: Updating output buffer handles failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  return OK;
}

status_t CameraDeviceSession::RegisterStreamsIntoCacheManagerLocked(
    const StreamConfiguration& stream_config,
    const std::vector<HalStream>& hal_streams) {
  ATRACE_CALL();

  for (auto& stream : stream_config.streams) {
    uint64_t producer_usage = 0;
    uint64_t consumer_usage = 0;
    int32_t stream_id = -1;
    for (auto& hal_stream : hal_streams) {
      if (hal_stream.id == stream.id) {
        producer_usage = hal_stream.producer_usage;
        consumer_usage = hal_stream.consumer_usage;
        stream_id = hal_stream.id;
      }
    }
    if (stream_id == -1) {
      ALOGE("%s: Could not fine framework stream in hal configured stream list",
            __FUNCTION__);
      return UNKNOWN_ERROR;
    }
    // Input stream buffers are always allocated by the camera service, not by
    // request_stream_buffers() callback from the HAL.
    if (stream.stream_type != StreamType::kOutput) {
      continue;
    }

    // The stream is not HAL buffer managed, so no need to register with SBC.
    if (hal_buffer_managed_stream_ids_.find(stream_id) ==
        hal_buffer_managed_stream_ids_.end()) {
      continue;
    }
    StreamBufferRequestFunc session_request_func = StreamBufferRequestFunc(
        [this, stream_id](uint32_t num_buffer,
                          std::vector<StreamBuffer>* buffers,
                          StreamBufferRequestError* status) -> status_t {
          ATRACE_NAME("StreamBufferRequestFunc");
          if (buffers == nullptr) {
            ALOGE("%s: buffers is nullptr.", __FUNCTION__);
            return BAD_VALUE;
          }

          if (num_buffer == 0) {
            ALOGE("%s: num_buffer is 0", __FUNCTION__);
            return BAD_VALUE;
          }

          if (status == nullptr) {
            ALOGE("%s: status is nullptr.", __FUNCTION__);
            return BAD_VALUE;
          }

          return RequestStreamBuffers(stream_id, num_buffer, buffers, status);
        });

    StreamBufferReturnFunc session_return_func = StreamBufferReturnFunc(
        [this](const std::vector<StreamBuffer>& buffers) -> status_t {
          ReturnStreamBuffers(buffers);

          for (auto& stream_buffer : buffers) {
            ALOGI("%s: [sbc] Flushed buf[%p] bid[%" PRIu64 "] strm[%d] frm[xx]",
                  __FUNCTION__, stream_buffer.buffer, stream_buffer.buffer_id,
                  stream_buffer.stream_id);
          }

          return OK;
        });

    StreamBufferCacheRegInfo reg_info = {.request_func = session_request_func,
                                         .return_func = session_return_func,
                                         .stream_id = stream_id,
                                         .width = stream.width,
                                         .height = stream.height,
                                         .format = stream.format,
                                         .producer_flags = producer_usage,
                                         .consumer_flags = consumer_usage,
                                         .num_buffers_to_cache = 1};

    status_t res = stream_buffer_cache_manager_->RegisterStream(reg_info);
    if (res != OK) {
      ALOGE(
          "%s: Failed to register stream into stream buffer cache manager, "
          "error status: %s(%d)",
          __FUNCTION__, strerror(-res), res);
      return UNKNOWN_ERROR;
    }
    ALOGI("%s: [sbc] Registered stream %d into SBC manager.", __FUNCTION__,
          stream.id);
  }

  return OK;
}

status_t CameraDeviceSession::RequestBuffersFromStreamBufferCacheManager(
    int32_t stream_id, uint32_t num_buffers, std::vector<StreamBuffer>* buffers,
    uint32_t frame_number) {
  if (num_buffers != 1) {
    ALOGE(
        "%s: Only one buffer per request can be handled now. num_buffers = %d",
        __FUNCTION__, num_buffers);
    // TODO(b/127988765): handle multiple buffers from multiple streams if
    //                    HWL needs this feature.
    return BAD_VALUE;
  }

  StreamBufferRequestResult buffer_request_result;

  status_t res = this->stream_buffer_cache_manager_->GetStreamBuffer(
      stream_id, &buffer_request_result);
  if (res != OK) {
    ALOGE(
        "%s: Failed to get stream buffer from SBC manager, error status: "
        "%s(%d).",
        __FUNCTION__, strerror(-res), res);
    return UNKNOWN_ERROR;
  }

  // This function fulfills requests from lower HAL level. It is hard for some
  // implementation of lower HAL level to handle the case of a request failure.
  // In case a framework buffer can not be delivered to the lower level, a dummy
  // buffer will be returned by the stream buffer cache manager.
  // The client at lower level can use that dummy buffer as a normal buffer for
  // writing and so forth. But that buffer will not be returned to the
  // framework. This avoids the troublesome for lower level to handle such
  // situation. An ERROR_REQUEST needs to be returned to the framework according
  // to ::android::hardware::camera::device::V3_5::StreamBufferRequestError.
  if (buffer_request_result.is_dummy_buffer) {
    ALOGI("%s: [sbc] Dummy buffer returned for stream: %d, frame: %d",
          __FUNCTION__, stream_id, frame_number);
    {
      std::lock_guard<std::mutex> lock(request_record_lock_);
      dummy_buffer_observed_.insert(buffer_request_result.buffer.buffer);
    }
  }

  ALOGV("%s: [sbc] => HWL Acquired buf[%p] buf_id[%" PRIu64
        "] strm[%d] frm[%u] dummy[%d]",
        __FUNCTION__, buffer_request_result.buffer.buffer,
        buffer_request_result.buffer.buffer_id, stream_id, frame_number,
        buffer_request_result.is_dummy_buffer);

  buffers->push_back(buffer_request_result.buffer);
  return OK;
}

status_t CameraDeviceSession::RequestStreamBuffers(
    int32_t stream_id, uint32_t num_buffers, std::vector<StreamBuffer>* buffers,
    StreamBufferRequestError* request_status) {
  if (buffers == nullptr) {
    ALOGE("%s: buffers is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  if (num_buffers == 0) {
    ALOGE("%s: num_buffers is 0", __FUNCTION__);
    return BAD_VALUE;
  }

  if (request_status == nullptr) {
    ALOGE("%s: request_status is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  *request_status = StreamBufferRequestError::kOk;
  status_t res = pending_requests_tracker_->WaitAndTrackAcquiredBuffers(
      stream_id, num_buffers);
  if (res != OK) {
    ALOGW("%s: Waiting until available buffer failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    *request_status = StreamBufferRequestError::kNoBufferAvailable;
    return res;
  }

  std::vector<BufferReturn> buffer_returns;
  std::vector<BufferRequest> buffer_requests = {{
      .stream_id = stream_id,
      .num_buffers_requested = num_buffers,
  }};

  BufferRequestStatus status = BufferRequestStatus::kOk;
  {
    int64_t start_timestamp;
    std::shared_lock lock(session_callback_lock_);
    if (measure_buffer_allocation_time_) {
      struct timespec start_time;
      if (clock_gettime(CLOCK_BOOTTIME, &start_time)) {
        ALOGE("%s: Getting start_time failed.", __FUNCTION__);
      } else {
        start_timestamp = start_time.tv_sec * kNsPerSec + start_time.tv_nsec;
      }
    }
    status = session_callback_.request_stream_buffers(buffer_requests,
                                                      &buffer_returns);
    if (measure_buffer_allocation_time_) {
      int64_t end_timestamp;
      struct timespec end_time;
      if (clock_gettime(CLOCK_BOOTTIME, &end_time)) {
        ALOGE("%s: Getting end_time failed.", __FUNCTION__);
      } else {
        end_timestamp = end_time.tv_sec * kNsPerSec + end_time.tv_nsec;
        int64_t elapsed_timestamp = end_timestamp - start_timestamp;
        if (elapsed_timestamp > kAllocationThreshold) {
          ALOGW("%s: buffer allocation time: %" PRIu64 " ms", __FUNCTION__,
                elapsed_timestamp / 1000000);
        }
      }
    }
  }

  // need this information when status is not kOk
  if (buffer_returns.size() > 0) {
    *request_status = buffer_returns[0].val.error;
  }

  if (status != BufferRequestStatus::kOk || buffer_returns.size() != 1) {
    ALOGW(
        "%s: Requesting stream buffer failed. (buffer_returns has %zu "
        "entries; status is %s(%d)).",
        __FUNCTION__, buffer_returns.size(), strerror(-res), status);
    for (auto& buffer_return : buffer_returns) {
      ALOGI("%s: stream %d, buffer request error %d", __FUNCTION__,
            buffer_return.stream_id, buffer_return.val.error);
    }
    pending_requests_tracker_->TrackBufferAcquisitionFailure(stream_id,
                                                             num_buffers);
    pending_requests_tracker_->DumpStatus();
    // TODO(b/129362905): Return partial buffers.
    return UNKNOWN_ERROR;
  }

  *buffers = buffer_returns[0].val.buffers;

  res = UpdateRequestedBufferHandles(buffers);
  if (res != OK) {
    ALOGE("%s: Updating requested buffer handles failed: %s(%d).", __FUNCTION__,
          strerror(-res), res);
    // TODO(b/129362905): Return partial buffers.
    return res;
  }

  ALOGV("%s: [sbc] => CDS Acquired buf[%p] buf_id[%" PRIu64 "] strm[%d]",
        __FUNCTION__, buffers->at(0).buffer, buffers->at(0).buffer_id,
        stream_id);

  return OK;
}

void CameraDeviceSession::ReturnStreamBuffers(
    const std::vector<StreamBuffer>& buffers) {
  {
    std::shared_lock lock(session_callback_lock_);
    session_callback_.return_stream_buffers(buffers);
  }

  for (auto& stream_buffer : buffers) {
    ALOGV("%s: [sbc] <= Return extra buf[%p], bid[%" PRIu64 "], strm[%d]",
          __FUNCTION__, stream_buffer.buffer, stream_buffer.buffer_id,
          stream_buffer.stream_id);
  }

  if (pending_requests_tracker_->TrackReturnedAcquiredBuffers(buffers) != OK) {
    ALOGE("%s: Tracking requested buffers failed.", __FUNCTION__);
  }
}

std::unique_ptr<google::camera_common::Profiler>
CameraDeviceSession::GetProfiler(uint32_t camera_id, int option) {
  return device_session_hwl_->GetProfiler(camera_id, option);
}

bool CameraDeviceSession::TryHandleCaptureResult(
    std::unique_ptr<CaptureResult>& result) {
  if (result == nullptr) {
    ALOGE("%s: result is nullptr", __FUNCTION__);
    return true;
  }
  zoom_ratio_mapper_.UpdateCaptureResult(result.get());

  status_t res = UpdatePendingRequest(result.get());
  if (res != OK) {
    ALOGE("%s: Updating inflight requests/streams failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return true;
  }

  for (auto& stream_buffer : result->output_buffers) {
    ALOGV("%s: [sbc] <= Return result output buf[%p], bid[%" PRIu64
          "], strm[%d], frm[%u]",
          __FUNCTION__, stream_buffer.buffer, stream_buffer.buffer_id,
          stream_buffer.stream_id, result->frame_number);
  }
  for (auto& stream_buffer : result->input_buffers) {
    ALOGV("%s: [sbc] <= Return result input buf[%p], bid[%" PRIu64
          "], strm[%d], frm[%u]",
          __FUNCTION__, stream_buffer.buffer, stream_buffer.buffer_id,
          stream_buffer.stream_id, result->frame_number);
  }

  // If there is placeholder buffer or a placeholder buffer has been observed of
  // this frame, handle the capture result specifically.
  bool result_handled = false;
  res = TryHandleDummyResult(result.get(), &result_handled);
  if (res != OK) {
    ALOGE("%s: Failed to handle placeholder result.", __FUNCTION__);
    return true;
  }
  return result_handled;
}

void CameraDeviceSession::TrackReturnedBuffers(
    const std::vector<StreamBuffer>& buffers) {
  if (!buffers.empty()) {
    if (pending_requests_tracker_->TrackReturnedAcquiredBuffers(buffers) != OK) {
      ALOGE("%s: Tracking requested acquired buffers failed", __FUNCTION__);
    }
    if (pending_requests_tracker_->TrackReturnedResultBuffers(buffers) != OK) {
      ALOGE("%s: Tracking requested quota buffers failed", __FUNCTION__);
    }
  }
}

}  // namespace google_camera_hal
}  // namespace android
