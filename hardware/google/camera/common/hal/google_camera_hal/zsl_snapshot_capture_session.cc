/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "GCH_ZslSnapshotCaptureSession"
#define ATRACE_TAG ATRACE_TAG_CAMERA

#include "zsl_snapshot_capture_session.h"

#include <dlfcn.h>
#include <log/log.h>
#include <sys/stat.h>
#include <utils/Trace.h>

#include "hal_utils.h"
#include "realtime_zsl_result_request_processor.h"
#include "snapshot_request_processor.h"
#include "snapshot_result_processor.h"
#include "system/graphics-base-v1.0.h"
#include "utils.h"
#include "utils/Errors.h"
#include "vendor_tag_defs.h"

namespace android {
namespace google_camera_hal {
namespace {

#if GCH_HWL_USE_DLOPEN
// HAL external process block library path
#if defined(_LP64)
constexpr char kExternalProcessBlockDir[] =
    "/vendor/lib64/camera/google_proprietary/";
#else  // defined(_LP64)
constexpr char kExternalProcessBlockDir[] =
    "/vendor/lib/camera/google_proprietary/";
#endif
#endif  // GCH_HWL_USE_DLOPEN

bool IsSwDenoiseSnapshotCompatible(const CaptureRequest& request) {
  if (request.settings == nullptr) {
    return false;
  }
  camera_metadata_ro_entry entry;
  if (request.settings->Get(ANDROID_CONTROL_CAPTURE_INTENT, &entry) != OK ||
      *entry.data.u8 != ANDROID_CONTROL_CAPTURE_INTENT_STILL_CAPTURE) {
    ALOGV("%s: ANDROID_CONTROL_CAPTURE_INTENT is not STILL_CAPTURE",
          __FUNCTION__);
    return false;
  }

  if (request.settings->Get(ANDROID_NOISE_REDUCTION_MODE, &entry) != OK ||
      *entry.data.u8 != ANDROID_NOISE_REDUCTION_MODE_HIGH_QUALITY) {
    ALOGI("%s: ANDROID_NOISE_REDUCTION_MODE is not HQ", __FUNCTION__);
    return false;
  }

  if (request.settings->Get(ANDROID_EDGE_MODE, &entry) != OK ||
      *entry.data.u8 != ANDROID_EDGE_MODE_HIGH_QUALITY) {
    ALOGI("%s: ANDROID_EDGE_MODE is not HQ", __FUNCTION__);
    return false;
  }

  if (request.settings->Get(ANDROID_CONTROL_EFFECT_MODE, &entry) != OK ||
      *entry.data.u8 != ANDROID_CONTROL_EFFECT_MODE_OFF) {
    ALOGI("%s: ANDROID_CONTROL_EFFECT_MODE is not off", __FUNCTION__);
    return false;
  }

  if (request.settings->Get(ANDROID_TONEMAP_MODE, &entry) != OK ||
      *entry.data.u8 != ANDROID_TONEMAP_MODE_HIGH_QUALITY) {
    ALOGI("%s: ANDROID_TONEMAP_MODE is not HQ", __FUNCTION__);
    return false;
  }

  return true;
}
}  // namespace

std::unique_ptr<ProcessBlock>
ZslSnapshotCaptureSession::CreateSnapshotProcessBlock() {
  ATRACE_CALL();
#if GCH_HWL_USE_DLOPEN
  bool found_process_block = false;
  for (const auto& lib_path :
       utils::FindLibraryPaths(kExternalProcessBlockDir)) {
    ALOGI("%s: Loading %s", __FUNCTION__, lib_path.c_str());
    void* lib_handle = nullptr;
    lib_handle = dlopen(lib_path.c_str(), RTLD_NOW);
    if (lib_handle == nullptr) {
      ALOGW("Failed loading %s.", lib_path.c_str());
      continue;
    }

    GetProcessBlockFactoryFunc external_process_block_t =
        reinterpret_cast<GetProcessBlockFactoryFunc>(
            dlsym(lib_handle, "GetProcessBlockFactory"));
    if (external_process_block_t == nullptr) {
      ALOGE("%s: dlsym failed (%s) when loading %s.", __FUNCTION__,
            "GetProcessBlockFactoryFunc", lib_path.c_str());
      dlclose(lib_handle);
      lib_handle = nullptr;
      continue;
    }

    if (external_process_block_t()->GetBlockName() == "SnapshotProcessBlock") {
      snapshot_process_block_factory_ = external_process_block_t;
      snapshot_process_block_lib_handle_ = lib_handle;
      found_process_block = true;
      break;
    }
  }
  if (!found_process_block) {
    ALOGE("%s: snapshot process block does not exist", __FUNCTION__);
    return nullptr;
  }

  return snapshot_process_block_factory_()->CreateProcessBlock(
      camera_device_session_hwl_);
#else
  if (GetSnapshotProcessBlockFactory == nullptr) {
    ALOGE("%s: snapshot process block does not exist", __FUNCTION__);
    return nullptr;
  }
  snapshot_process_block_factory_ = GetSnapshotProcessBlockFactory;
  return GetSnapshotProcessBlockFactory()->CreateProcessBlock(
      camera_device_session_hwl_);
#endif
}

std::unique_ptr<ProcessBlock>
ZslSnapshotCaptureSession::CreateDenoiseProcessBlock() {
  ATRACE_CALL();
#if GCH_HWL_USE_DLOPEN
  bool found_process_block = false;
  for (const auto& lib_path :
       utils::FindLibraryPaths(kExternalProcessBlockDir)) {
    ALOGI("%s: Loading %s", __FUNCTION__, lib_path.c_str());
    void* lib_handle = nullptr;
    lib_handle = dlopen(lib_path.c_str(), RTLD_NOW);
    if (lib_handle == nullptr) {
      ALOGW("Failed loading %s.", lib_path.c_str());
      continue;
    }

    GetProcessBlockFactoryFunc external_process_block_t =
        reinterpret_cast<GetProcessBlockFactoryFunc>(
            dlsym(lib_handle, "GetProcessBlockFactory"));
    if (external_process_block_t == nullptr) {
      ALOGE("%s: dlsym failed (%s) when loading %s.", __FUNCTION__,
            "GetProcessBlockFactoryFunc", lib_path.c_str());
      dlclose(lib_handle);
      lib_handle = nullptr;
      continue;
    }

    if (external_process_block_t()->GetBlockName() == "DenoiseProcessBlock") {
      denoise_process_block_factory_ = external_process_block_t;
      denoise_process_block_lib_handle_ = lib_handle;
      found_process_block = true;
      break;
    }
  }
  if (!found_process_block) {
    ALOGE("%s: denoise process block does not exist", __FUNCTION__);
    return nullptr;
  }

  return denoise_process_block_factory_()->CreateProcessBlock(
      camera_device_session_hwl_);
#else
  if (GetDenoiseProcessBlockFactory == nullptr) {
    ALOGE("%s: denoise process block does not exist", __FUNCTION__);
    return nullptr;
  }
  denoise_process_block_factory_ = GetDenoiseProcessBlockFactory;
  return GetDenoiseProcessBlockFactory()->CreateProcessBlock(
      camera_device_session_hwl_);
#endif
}

bool ZslSnapshotCaptureSession::IsStreamConfigurationSupported(
    CameraDeviceSessionHwl* device_session_hwl,
    const StreamConfiguration& stream_config) {
  ATRACE_CALL();
  if (device_session_hwl == nullptr) {
    ALOGE("%s: device_session_hwl is nullptr", __FUNCTION__);
    return false;
  }

  std::unique_ptr<HalCameraMetadata> characteristics;
  status_t res = device_session_hwl->GetCameraCharacteristics(&characteristics);
  if (res != OK) {
    ALOGE("%s: GetCameraCharacteristics failed.", __FUNCTION__);
    return false;
  }

  camera_metadata_ro_entry entry;
  res = characteristics->Get(VendorTagIds::kSwDenoiseEnabled, &entry);
  if (res != OK || entry.data.u8[0] != 1) {
    ALOGI("%s: Software denoised not enabled", __FUNCTION__);
    return false;
  }

  bool has_eligible_snapshot_stream = false;
  bool has_preview_stream = false;
  bool has_hdr_preview_stream = false;
  for (const auto& stream : stream_config.streams) {
    if (stream.is_physical_camera_stream) {
      ALOGI("%s: support logical stream only", __FUNCTION__);
      return false;
    }
    if (utils::IsSecuredStream(stream)) {
      ALOGI("%s: don't support secured stream", __FUNCTION__);
      return false;
    }
    if (utils::IsJPEGSnapshotStream(stream) ||
        utils::IsYUVSnapshotStream(stream)) {
      if (utils::IsSoftwareDenoiseEligibleSnapshotStream(stream)) {
        has_eligible_snapshot_stream = true;
      }
    } else if (utils::IsPreviewStream(stream)) {
      has_preview_stream = true;
      if (utils::IsHdrStream(stream)) {
        has_hdr_preview_stream = true;
      }
    } else {
      ALOGI("%s: only support preview + (snapshot and/or YUV) streams",
            __FUNCTION__);
      return false;
    }
  }
  if (!has_eligible_snapshot_stream) {
    ALOGI("%s: no eligible JPEG or YUV stream", __FUNCTION__);
    return false;
  }

  if (!has_preview_stream) {
    ALOGI("%s: no preview stream", __FUNCTION__);
    return false;
  }
  if (has_hdr_preview_stream) {
    ALOGI("%s: 10-bit HDR preview stream does not support ZSL snapshot",
          __FUNCTION__);
    return false;
  }

  ALOGI("%s: ZslSnapshotCaptureSession supports the stream config",
        __FUNCTION__);
  return true;
}

std::unique_ptr<CaptureSession> ZslSnapshotCaptureSession::Create(
    const StreamConfiguration& stream_config,
    const std::vector<ExternalCaptureSessionFactory*>&
        external_capture_session_entries,
    const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
    HwlSessionCallback hwl_session_callback,
    CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
    CameraDeviceSessionHwl* camera_device_session_hwl,
    std::vector<HalStream>* hal_configured_streams,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify) {
  ATRACE_CALL();
  auto session =
      std::unique_ptr<ZslSnapshotCaptureSession>(new ZslSnapshotCaptureSession(
          external_capture_session_entries, capture_session_entries,
          hwl_session_callback, camera_buffer_allocator_hwl,
          camera_device_session_hwl));
  if (session == nullptr) {
    ALOGE("%s: Creating ZslSnapshotCaptureSession failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res = session->Initialize(camera_device_session_hwl, stream_config,
                                     process_capture_result, notify,
                                     hal_configured_streams);
  if (res != OK) {
    ALOGE("%s: Initializing ZslSnapshotCaptureSession failed: %s (%d).",
          __FUNCTION__, strerror(-res), res);
    return nullptr;
  }
  return session;
}

ZslSnapshotCaptureSession::~ZslSnapshotCaptureSession() {
  auto release_thread = std::thread([this]() {
    ATRACE_NAME("Release snapshot request processor");
    snapshot_request_processor_ = nullptr;
  });
  if (camera_device_session_hwl_ != nullptr) {
    camera_device_session_hwl_->DestroyPipelines();
  }
  // Need to explicitly release SnapshotProcessBlock by releasing
  // SnapshotRequestProcessor before the lib handle is released.
  release_thread.join();
  dlclose(snapshot_process_block_lib_handle_);
  dlclose(denoise_process_block_lib_handle_);

  ALOGI("%s: finished", __FUNCTION__);
}

status_t ZslSnapshotCaptureSession::BuildPipelines(
    ProcessBlock* process_block, ProcessBlock* snapshot_process_block,
    std::vector<HalStream>* hal_configured_streams) {
  ATRACE_CALL();
  if (process_block == nullptr || hal_configured_streams == nullptr ||
      snapshot_process_block == nullptr) {
    ALOGE(
        "%s: process_block (%p) or hal_configured_streams (%p) or "
        "snapshot_process_block (%p) is nullptr",
        __FUNCTION__, process_block, hal_configured_streams,
        snapshot_process_block);
    return BAD_VALUE;
  }

  status_t res;

  std::vector<HalStream> snapshot_hal_configured_streams;
  res = snapshot_process_block->GetConfiguredHalStreams(
      &snapshot_hal_configured_streams);
  if (res != OK) {
    ALOGE("%s: Getting snapshot HAL streams failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  for (uint32_t i = 0; i < hal_configured_streams->size(); i++) {
    if (hal_configured_streams->at(i).id == additional_stream_id_) {
      // Reserve additional buffer(s).
      hal_configured_streams->at(i).max_buffers += kAdditionalBufferNumber;
      // Allocate internal YUV stream buffers
      res = internal_stream_manager_->AllocateBuffers(
          hal_configured_streams->at(i),
          /*additional_num_buffers=*/kAdditionalBufferNumber);
      if (res != OK) {
        ALOGE("%s: AllocateBuffers failed.", __FUNCTION__);
        return UNKNOWN_ERROR;
      }
      break;
    }
  }

  return OK;
}

status_t ZslSnapshotCaptureSession::ConfigureStreams(
    const StreamConfiguration& stream_config,
    RequestProcessor* request_processor, ProcessBlock* process_block,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
    int32_t& additional_stream_id) {
  ATRACE_CALL();
  if (request_processor == nullptr || process_block == nullptr) {
    ALOGE("%s: request_processor (%p) or process_block (%p) is nullptr",
          __FUNCTION__, request_processor, process_block);
    return BAD_VALUE;
  }
  StreamConfiguration process_block_stream_config;
  // Configure streams for request processor
  status_t res = request_processor->ConfigureStreams(
      internal_stream_manager_.get(), stream_config,
      &process_block_stream_config);
  if (res != OK) {
    ALOGE("%s: Configuring stream for request processor failed.", __FUNCTION__);
    return res;
  }

  // Check all streams are configured.
  if (stream_config.streams.size() > process_block_stream_config.streams.size()) {
    ALOGE("%s: stream_config has %zu streams but only configured %zu streams",
          __FUNCTION__, stream_config.streams.size(),
          process_block_stream_config.streams.size());
    return UNKNOWN_ERROR;
  }
  for (auto& stream : stream_config.streams) {
    bool found = false;
    for (auto& configured_stream : process_block_stream_config.streams) {
      if (stream.id == configured_stream.id) {
        found = true;
        break;
      }
    }

    if (!found) {
      ALOGE("%s: Cannot find stream %u in configured streams.", __FUNCTION__,
            stream.id);
      return UNKNOWN_ERROR;
    }
  }

  for (auto& stream : process_block_stream_config.streams) {
    bool found = false;
    for (auto& configured_stream : stream_config.streams) {
      if (stream.id == configured_stream.id) {
        found = true;
        break;
      }
    }
    if (!found) {
      additional_stream_id = stream.id;
      break;
    }
  }

  if (additional_stream_id == -1) {
    ALOGE("%s: Configuring stream fail due to wrong additional_stream_id",
          __FUNCTION__);
    return UNKNOWN_ERROR;
  }

  // Create preview result processor. Stream ID is not set at this stage.

  std::unique_ptr<ResultProcessor> realtime_result_processor;
  if (video_sw_denoise_enabled_) {
    auto processor = RealtimeZslResultRequestProcessor::Create(
        internal_stream_manager_.get(), additional_stream_id,
        HAL_PIXEL_FORMAT_YCBCR_420_888, partial_result_count_);
    realtime_zsl_result_request_processor_ = processor.get();
    realtime_result_processor = std::move(processor);
  } else {
    realtime_result_processor = RealtimeZslResultProcessor::Create(
        internal_stream_manager_.get(), additional_stream_id,
        HAL_PIXEL_FORMAT_YCBCR_420_888, partial_result_count_);
  }

  if (realtime_result_processor == nullptr) {
    ALOGE(
        "%s: Creating "
        "RealtimeZslResultProcessor/RealtimeZslResultRequestProcessor failed.",
        __FUNCTION__);
    return UNKNOWN_ERROR;
  }
  realtime_result_processor->SetResultCallback(
      process_capture_result, notify, /*process_batch_capture_result=*/nullptr);

  res = process_block->SetResultProcessor(std::move(realtime_result_processor));
  if (res != OK) {
    ALOGE("%s: Setting result process in process block failed.", __FUNCTION__);
    return res;
  }

  // Configure streams for process block.
  res = process_block->ConfigureStreams(process_block_stream_config,
                                        stream_config);
  if (res != OK) {
    ALOGE("%s: Configuring stream for process block failed.", __FUNCTION__);
    return res;
  }

  for (auto& hal_stream : *hal_config_) {
    if (hal_stream.id == additional_stream_id) {
      // Set the producer usage so that the buffer will be 64 byte aligned.
      hal_stream.producer_usage |=
          (GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_READ_OFTEN);
    }
  }

  if (video_sw_denoise_enabled_) {
    StreamConfiguration denoise_process_block_stream_config;
    // Configure streams for request processor
    res = realtime_zsl_result_request_processor_->ConfigureStreams(
        internal_stream_manager_.get(), stream_config,
        &denoise_process_block_stream_config);

    if (res != OK) {
      ALOGE(
          "%s: Configuring stream for process block "
          "(RealtimeZslResultRequestProcessor) failed.",
          __FUNCTION__);
      return res;
    }

    std::unique_ptr<ProcessBlock> denoise_processor =
        CreateDenoiseProcessBlock();
    // Create preview result processor. Stream ID is not set at this stage.
    auto basic_result_processor = BasicResultProcessor::Create();
    if (basic_result_processor == nullptr) {
      ALOGE("%s: Creating BasicResultProcessor failed.", __FUNCTION__);
      return UNKNOWN_ERROR;
    }
    basic_result_processor_ = basic_result_processor.get();
    basic_result_processor->SetResultCallback(
        process_capture_result, notify,
        /*process_batch_capture_result=*/nullptr);

    res =
        denoise_processor->SetResultProcessor(std::move(basic_result_processor));
    if (res != OK) {
      ALOGE("%s: Setting result process in process block failed.", __FUNCTION__);
      return res;
    }

    // Configure streams for process block.
    res = denoise_processor->ConfigureStreams(
        denoise_process_block_stream_config, stream_config);
    if (res != OK) {
      ALOGE("%s: Configuring stream for process block failed.", __FUNCTION__);
      return res;
    }

    res = realtime_zsl_result_request_processor_->SetProcessBlock(
        std::move(denoise_processor));
    if (res != OK) {
      ALOGE("%s: Setting process block for RequestProcessor failed: %s(%d)",
            __FUNCTION__, strerror(-res), res);
      return res;
    }
  }

  return OK;
}

status_t ZslSnapshotCaptureSession::ConfigureSnapshotStreams(
    const StreamConfiguration& stream_config) {
  ATRACE_CALL();
  if (snapshot_process_block_ == nullptr ||
      snapshot_request_processor_ == nullptr) {
    ALOGE(
        "%s: snapshot_process_block_ or snapshot_request_processor_ is nullptr",
        __FUNCTION__);
    return BAD_VALUE;
  }

  StreamConfiguration process_block_stream_config;
  // Configure streams for request processor
  status_t res = snapshot_request_processor_->ConfigureStreams(
      internal_stream_manager_.get(), stream_config,
      &process_block_stream_config);
  if (res != OK) {
    ALOGE("%s: Configuring stream for request processor failed.", __FUNCTION__);
    return res;
  }

  // Configure streams for snapshot process block.
  res = snapshot_process_block_->ConfigureStreams(process_block_stream_config,
                                                  stream_config);
  if (res != OK) {
    ALOGE("%s: Configuring snapshot stream for process block failed.",
          __FUNCTION__);
    return res;
  }

  return OK;
}

status_t ZslSnapshotCaptureSession::SetupSnapshotProcessChain(
    const StreamConfiguration& stream_config,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify) {
  ATRACE_CALL();
  if (snapshot_process_block_ != nullptr ||
      snapshot_result_processor_ != nullptr ||
      snapshot_request_processor_ != nullptr) {
    ALOGE(
        "%s: snapshot_process_block_(%p) or snapshot_result_processor_(%p) or "
        "snapshot_request_processor_(%p) is/are "
        "already set",
        __FUNCTION__, snapshot_process_block_, snapshot_result_processor_,
        snapshot_request_processor_.get());
    return BAD_VALUE;
  }

  std::unique_ptr<ProcessBlock> snapshot_process_block =
      CreateSnapshotProcessBlock();
  if (snapshot_process_block == nullptr) {
    ALOGE("%s: Creating SnapshotProcessBlock failed.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }
  snapshot_process_block_ = snapshot_process_block.get();

  snapshot_request_processor_ = SnapshotRequestProcessor::Create(
      camera_device_session_hwl_, hwl_session_callback_, additional_stream_id_);
  if (snapshot_request_processor_ == nullptr) {
    ALOGE("%s: Creating SnapshotRequestProcessor failed.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }

  std::unique_ptr<SnapshotResultProcessor> snapshot_result_processor =
      SnapshotResultProcessor::Create(internal_stream_manager_.get(),
                                      additional_stream_id_);
  if (snapshot_result_processor == nullptr) {
    ALOGE("%s: Creating SnapshotResultProcessor failed.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }
  snapshot_result_processor_ = snapshot_result_processor.get();

  status_t res = snapshot_request_processor_->SetProcessBlock(
      std::move(snapshot_process_block));
  if (res != OK) {
    ALOGE("%s: Setting process block for RequestProcessor failed: %s(%d)",
          __FUNCTION__, strerror(-res), res);
    return res;
  }

  res = snapshot_process_block_->SetResultProcessor(
      std::move(snapshot_result_processor));

  snapshot_result_processor_->SetResultCallback(
      process_capture_result, notify, /*process_batch_capture_result=*/nullptr);
  res = ConfigureSnapshotStreams(stream_config);
  if (res != OK) {
    ALOGE("%s: Configuring snapshot stream failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }
  return OK;
}

status_t ZslSnapshotCaptureSession::SetupRealtimeProcessChain(
    const StreamConfiguration& stream_config,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify) {
  ATRACE_CALL();
  if (realtime_process_block_ != nullptr ||
      realtime_request_processor_ != nullptr) {
    ALOGE(
        "%s: realtime_process_block_(%p) or realtime_request_processor_(%p) "
        "is/are already set",
        __FUNCTION__, realtime_process_block_,
        realtime_request_processor_.get());
    return BAD_VALUE;
  }

  // Create process block
  auto realtime_process_block = CaptureSessionWrapperProcessBlock::Create(
      external_capture_session_entries_, capture_session_entries_,
      hwl_session_callback_, camera_buffer_allocator_hwl_,
      camera_device_session_hwl_, hal_config_);
  if (realtime_process_block == nullptr) {
    ALOGE("%s: Creating RealtimeProcessBlock failed.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }
  realtime_process_block_ = realtime_process_block.get();

  // Create realtime request processor.
  realtime_request_processor_ = RealtimeZslRequestProcessor::Create(
      camera_device_session_hwl_, HAL_PIXEL_FORMAT_YCBCR_420_888);
  if (realtime_request_processor_ == nullptr) {
    ALOGE("%s: Creating RealtimeZslRequestProcessor failed.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }

  // realtime result processor will be created inside ConfigureStreams when the
  // additional stream id is determined.

  status_t res = realtime_request_processor_->SetProcessBlock(
      std::move(realtime_process_block));
  if (res != OK) {
    ALOGE("%s: Setting process block for RequestProcessor failed: %s(%d)",
          __FUNCTION__, strerror(-res), res);
    return res;
  }

  res = ConfigureStreams(stream_config, realtime_request_processor_.get(),
                         realtime_process_block_, process_capture_result,
                         notify, additional_stream_id_);
  if (res != OK) {
    ALOGE("%s: Configuring stream failed: %s(%d)", __FUNCTION__, strerror(-res),
          res);
    return res;
  }
  return OK;
}

status_t ZslSnapshotCaptureSession::PurgeHalConfiguredStream(
    const StreamConfiguration& stream_config,
    std::vector<HalStream>* hal_configured_streams) {
  if (hal_configured_streams == nullptr) {
    ALOGE("%s: HAL configured stream list is null.", __FUNCTION__);
    return BAD_VALUE;
  }

  std::set<int32_t> framework_stream_id_set;
  for (auto& stream : stream_config.streams) {
    framework_stream_id_set.insert(stream.id);
  }

  std::vector<HalStream> configured_streams;
  for (auto& hal_stream : *hal_configured_streams) {
    if (framework_stream_id_set.find(hal_stream.id) !=
        framework_stream_id_set.end()) {
      configured_streams.push_back(hal_stream);
    }
  }
  *hal_configured_streams = configured_streams;
  return OK;
}

ZslSnapshotCaptureSession::ZslSnapshotCaptureSession(
    const std::vector<ExternalCaptureSessionFactory*>&
        external_capture_session_entries,
    const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
    HwlSessionCallback hwl_session_callback,
    CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
    CameraDeviceSessionHwl* camera_device_session_hwl)
    : external_capture_session_entries_(external_capture_session_entries),
      capture_session_entries_(capture_session_entries),
      hwl_session_callback_(hwl_session_callback),
      camera_buffer_allocator_hwl_(camera_buffer_allocator_hwl),
      camera_device_session_hwl_(camera_device_session_hwl) {
}

status_t ZslSnapshotCaptureSession::Initialize(
    CameraDeviceSessionHwl* camera_device_session_hwl,
    const StreamConfiguration& stream_config,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
    std::vector<HalStream>* hal_configured_streams) {
  ATRACE_CALL();
  if (!IsStreamConfigurationSupported(camera_device_session_hwl, stream_config)) {
    ALOGE("%s: stream configuration is not supported.", __FUNCTION__);
    return BAD_VALUE;
  }

  std::unique_ptr<HalCameraMetadata> characteristics;
  status_t res =
      camera_device_session_hwl->GetCameraCharacteristics(&characteristics);
  if (res != OK) {
    ALOGE("%s: GetCameraCharacteristics failed.", __FUNCTION__);
    return BAD_VALUE;
  }

  camera_metadata_ro_entry video_sw_denoise_entry;
  res = characteristics->Get(VendorTagIds::kVideoSwDenoiseEnabled,
                             &video_sw_denoise_entry);
  if (res == OK && video_sw_denoise_entry.data.u8[0] == 1) {
    video_sw_denoise_enabled_ = true;
    ALOGI("%s: video sw denoise is enabled.", __FUNCTION__);
  } else {
    ALOGI("%s: video sw denoise is disabled.", __FUNCTION__);
  }

  for (auto stream : stream_config.streams) {
    if (utils::IsPreviewStream(stream)) {
      hal_preview_stream_id_ = stream.id;
      break;
    }
  }
  camera_device_session_hwl_ = camera_device_session_hwl;
  hal_config_ = hal_configured_streams;

  // Create result dispatcher
  partial_result_count_ = kPartialResult;
  camera_metadata_ro_entry partial_result_entry;
  res = characteristics->Get(ANDROID_REQUEST_PARTIAL_RESULT_COUNT,
                             &partial_result_entry);
  if (res == OK) {
    partial_result_count_ = partial_result_entry.data.i32[0];
  }
  result_dispatcher_ = ZslResultDispatcher::Create(
      partial_result_count_, process_capture_result, notify, stream_config);
  if (result_dispatcher_ == nullptr) {
    ALOGE("%s: Cannot create result dispatcher.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }

  internal_stream_manager_ = InternalStreamManager::Create(
      /*buffer_allocator=*/nullptr, partial_result_count_);
  if (internal_stream_manager_ == nullptr) {
    ALOGE("%s: Cannot create internal stream manager.", __FUNCTION__);
    return UNKNOWN_ERROR;
  }

  device_session_notify_ = notify;
  process_capture_result_ =
      ProcessCaptureResultFunc([this](std::unique_ptr<CaptureResult> result) {
        ProcessCaptureResult(std::move(result));
      });
  notify_ = NotifyFunc(
      [this](const NotifyMessage& message) { NotifyHalMessage(message); });

  // Setup and connect realtime process chain
  res = SetupRealtimeProcessChain(stream_config, process_capture_result_,
                                  notify_);
  if (res != OK) {
    ALOGE("%s: SetupRealtimeProcessChain fail: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  // Setup snapshot process chain
  res = SetupSnapshotProcessChain(stream_config, process_capture_result_,
                                  notify_);
  if (res != OK) {
    ALOGE("%s: SetupSnapshotProcessChain fail: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  // Realtime and snapshot streams are configured
  // Start to build pipleline
  res = BuildPipelines(realtime_process_block_, snapshot_process_block_,
                       hal_configured_streams);
  if (res != OK) {
    ALOGE("%s: Building pipelines failed: %s(%d)", __FUNCTION__, strerror(-res),
          res);
    return res;
  }

  res = PurgeHalConfiguredStream(stream_config, hal_configured_streams);
  if (res != OK) {
    ALOGE("%s: Removing internal streams from configured stream failed: %s(%d)",
          __FUNCTION__, strerror(-res), res);
    return res;
  }

  if (res != OK) {
    ALOGE("%s: Connecting process chain failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  return OK;
}

status_t ZslSnapshotCaptureSession::ProcessRequest(const CaptureRequest& request) {
  ATRACE_CALL();
  bool is_zsl_request = false;
  bool is_preview_intent = false;
  camera_metadata_ro_entry entry;
  if (request.settings != nullptr) {
    if (request.settings->Get(ANDROID_CONTROL_ENABLE_ZSL, &entry) == OK &&
        *entry.data.u8 == ANDROID_CONTROL_ENABLE_ZSL_TRUE) {
      is_zsl_request = true;
    }
    if (request.settings->Get(ANDROID_CONTROL_CAPTURE_INTENT, &entry) == OK &&
        *entry.data.u8 == ANDROID_CONTROL_CAPTURE_INTENT_PREVIEW) {
      is_preview_intent = true;
    }
  }
  status_t res = result_dispatcher_->AddPendingRequest(request, is_zsl_request);
  if (res != OK) {
    ALOGE("%s: frame(%d) fail to AddPendingRequest", __FUNCTION__,
          request.frame_number);
    return BAD_VALUE;
  }
  if (IsSwDenoiseSnapshotCompatible(request)) {
    res = snapshot_request_processor_->ProcessRequest(request);
    if (res != OK) {
      ALOGW(
          "%s: frame (%d) fall back to real time request for snapshot: %s (%d)",
          __FUNCTION__, request.frame_number, strerror(-res), res);
      if (realtime_zsl_result_request_processor_ != nullptr) {
        realtime_zsl_result_request_processor_->UpdateOutputBufferCount(
            request.frame_number, request.output_buffers.size(),
            is_preview_intent);
      }
      res = realtime_request_processor_->ProcessRequest(request);
    }
  } else {
    if (realtime_zsl_result_request_processor_ != nullptr) {
      realtime_zsl_result_request_processor_->UpdateOutputBufferCount(
          request.frame_number, request.output_buffers.size(),
          is_preview_intent);
    }

    res = realtime_request_processor_->ProcessRequest(request);
  }

  if (res != OK) {
    ALOGE("%s: ProcessRequest (%d) fail and remove pending request",
          __FUNCTION__, request.frame_number);
    result_dispatcher_->RemovePendingRequest(request.frame_number);
  }
  return res;
}

status_t ZslSnapshotCaptureSession::Flush() {
  ATRACE_CALL();
  return realtime_request_processor_->Flush();
}

void ZslSnapshotCaptureSession::ProcessCaptureResult(
    std::unique_ptr<CaptureResult> result) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);
  if (result == nullptr) {
    return;
  }

  if (result->result_metadata) {
    camera_device_session_hwl_->FilterResultMetadata(
        result->result_metadata.get());
  }

  status_t res = result_dispatcher_->AddResult(std::move(result));
  if (res != OK) {
    ALOGE("%s: fail to AddResult", __FUNCTION__);
    return;
  }
}

void ZslSnapshotCaptureSession::NotifyHalMessage(const NotifyMessage& message) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);
  if (device_session_notify_ == nullptr) {
    ALOGE("%s: device_session_notify_ is nullptr. Dropping a message.",
          __FUNCTION__);
    return;
  }

  if (message.type == MessageType::kShutter) {
    status_t res = result_dispatcher_->AddShutter(
        message.message.shutter.frame_number,
        message.message.shutter.timestamp_ns,
        message.message.shutter.readout_timestamp_ns);
    if (res != OK) {
      ALOGE("%s: AddShutter for frame %u failed: %s (%d).", __FUNCTION__,
            message.message.shutter.frame_number, strerror(-res), res);
      return;
    }
  } else if (message.type == MessageType::kError) {
    status_t res = result_dispatcher_->AddError(message.message.error);
    if (res != OK) {
      ALOGE("%s: AddError for frame %u failed: %s (%d).", __FUNCTION__,
            message.message.error.frame_number, strerror(-res), res);
      return;
    }
  } else {
    ALOGW("%s: Unsupported message type: %u", __FUNCTION__, message.type);
    device_session_notify_(message);
  }
}

}  // namespace google_camera_hal
}  // namespace android
