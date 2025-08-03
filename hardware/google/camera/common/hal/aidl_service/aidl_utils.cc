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

#define LOG_TAG "GCH_AidlUtils"
// #define LOG_NDEBUG 0
#include "aidl_utils.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <log/log.h>
#include <system/camera_metadata.h>

#include <regex>

#include "aidl_camera_device.h"
#include "aidl_camera_provider.h"

namespace android {
namespace hardware {
namespace camera {
namespace implementation {
namespace aidl_utils {

using AidlCameraProvider = provider::implementation::AidlCameraProvider;
using AidlCameraDevice = device::implementation::AidlCameraDevice;
using AidlStatus = aidl::android::hardware::camera::common::Status;
using DynamicRangeProfile = google_camera_hal::DynamicRangeProfile;
using ColorSpaceProfile = google_camera_hal::ColorSpaceProfile;

ScopedAStatus ConvertToAidlReturn(status_t hal_status) {
  switch (hal_status) {
    case OK:
      return ScopedAStatus::ok();
    case BAD_VALUE:
      return ScopedAStatus::fromServiceSpecificError(
          static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
    case -EBUSY:
      return ScopedAStatus::fromServiceSpecificError(
          static_cast<int32_t>(Status::CAMERA_IN_USE));
    case -EUSERS:
      return ScopedAStatus::fromServiceSpecificError(
          static_cast<int32_t>(Status::MAX_CAMERAS_IN_USE));
    case UNKNOWN_TRANSACTION:
    case INVALID_OPERATION:
      return ScopedAStatus::fromServiceSpecificError(
          static_cast<int32_t>(Status::OPERATION_NOT_SUPPORTED));
    case DEAD_OBJECT:
      return ScopedAStatus::fromServiceSpecificError(
          static_cast<int32_t>(Status::CAMERA_DISCONNECTED));
    default:
      return ScopedAStatus::fromServiceSpecificError(
          static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
}

status_t ConvertToAidlVendorTagType(
    google_camera_hal::CameraMetadataType hal_type,
    CameraMetadataType* aidl_type) {
  if (aidl_type == nullptr) {
    ALOGE("%s: aidl_type is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (hal_type) {
    case google_camera_hal::CameraMetadataType::kByte:
      *aidl_type = CameraMetadataType::BYTE;
      break;
    case google_camera_hal::CameraMetadataType::kInt32:
      *aidl_type = CameraMetadataType::INT32;
      break;
    case google_camera_hal::CameraMetadataType::kFloat:
      *aidl_type = CameraMetadataType::FLOAT;
      break;
    case google_camera_hal::CameraMetadataType::kInt64:
      *aidl_type = CameraMetadataType::INT64;
      break;
    case google_camera_hal::CameraMetadataType::kDouble:
      *aidl_type = CameraMetadataType::DOUBLE;
      break;
    case google_camera_hal::CameraMetadataType::kRational:
      *aidl_type = CameraMetadataType::RATIONAL;
      break;
    default:
      ALOGE("%s: Unknown google_camera_hal::CameraMetadataType: %u",
            __FUNCTION__, hal_type);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToAidlVendorTagSections(
    const std::vector<google_camera_hal::VendorTagSection>& hal_sections,
    std::vector<VendorTagSection>* aidl_sections) {
  if (aidl_sections == nullptr) {
    ALOGE("%s: aidl_sections is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  aidl_sections->resize(hal_sections.size());
  for (uint32_t i = 0; i < hal_sections.size(); i++) {
    (*aidl_sections)[i].sectionName = hal_sections[i].section_name;
    (*aidl_sections)[i].tags.resize(hal_sections[i].tags.size());

    for (uint32_t j = 0; j < hal_sections[i].tags.size(); j++) {
      (*aidl_sections)[i].tags[j].tagId = hal_sections[i].tags[j].tag_id;
      (*aidl_sections)[i].tags[j].tagName = hal_sections[i].tags[j].tag_name;
      status_t res =
          ConvertToAidlVendorTagType(hal_sections[i].tags[j].tag_type,
                                     &(*aidl_sections)[i].tags[j].tagType);
      if (res != OK) {
        ALOGE("%s: Converting to aidl vendor tag type failed. ", __FUNCTION__);
        return res;
      }
    }
  }
  return OK;
}

status_t ConvertToAidlResourceCost(
    const google_camera_hal::CameraResourceCost& hal_cost,
    CameraResourceCost* aidl_cost) {
  if (aidl_cost == nullptr) {
    ALOGE("%s: aidl_cost is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  aidl_cost->resourceCost = hal_cost.resource_cost;
  aidl_cost->conflictingDevices.resize(hal_cost.conflicting_devices.size());

  for (uint32_t i = 0; i < hal_cost.conflicting_devices.size(); i++) {
    aidl_cost->conflictingDevices[i] =
        "device@" + AidlCameraDevice::kDeviceVersion + "/" +
        AidlCameraProvider::kProviderName + "/" +
        std::to_string(hal_cost.conflicting_devices[i]);
  }

  return OK;
}

status_t ConvertToHalTemplateType(
    RequestTemplate aidl_template,
    google_camera_hal::RequestTemplate* hal_template) {
  if (hal_template == nullptr) {
    ALOGE("%s: hal_template is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (aidl_template) {
    case RequestTemplate::PREVIEW:
      *hal_template = google_camera_hal::RequestTemplate::kPreview;
      break;
    case RequestTemplate::STILL_CAPTURE:
      *hal_template = google_camera_hal::RequestTemplate::kStillCapture;
      break;
    case RequestTemplate::VIDEO_RECORD:
      *hal_template = google_camera_hal::RequestTemplate::kVideoRecord;
      break;
    case RequestTemplate::VIDEO_SNAPSHOT:
      *hal_template = google_camera_hal::RequestTemplate::kVideoSnapshot;
      break;
    case RequestTemplate::ZERO_SHUTTER_LAG:
      *hal_template = google_camera_hal::RequestTemplate::kZeroShutterLag;
      break;
    case RequestTemplate::MANUAL:
      *hal_template = google_camera_hal::RequestTemplate::kManual;
      break;
    default:
      ALOGE("%s: Unknown AIDL RequestTemplate: %u", __FUNCTION__, aidl_template);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToAidlHalStreamConfig(
    const google_camera_hal::ConfigureStreamsReturn& hal_config,
    ConfigureStreamsRet* aidl_config) {
  if (aidl_config == nullptr) {
    ALOGE("%s: aidl_config is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  aidl_config->halStreams.resize(hal_config.hal_streams.size());

  for (uint32_t i = 0; i < hal_config.hal_streams.size(); i++) {
    auto& dst = aidl_config->halStreams[i];
    dst.supportOffline = false;
    if (hal_config.hal_streams[i].is_physical_camera_stream) {
      dst.physicalCameraId =
          std::to_string(hal_config.hal_streams[i].physical_camera_id);
    }

    dst.overrideDataSpace =
        static_cast<aidl::android::hardware::graphics::common::Dataspace>(
            hal_config.hal_streams[i].override_data_space);

    dst.id = hal_config.hal_streams[i].id;

    dst.overrideFormat =
        static_cast<aidl::android::hardware::graphics::common::PixelFormat>(
            hal_config.hal_streams[i].override_format);

    dst.producerUsage =
        static_cast<aidl::android::hardware::graphics::common::BufferUsage>(
            hal_config.hal_streams[i].producer_usage);

    dst.consumerUsage =
        static_cast<aidl::android::hardware::graphics::common::BufferUsage>(
            hal_config.hal_streams[i].consumer_usage);

    dst.maxBuffers = hal_config.hal_streams[i].max_buffers;
    dst.enableHalBufferManager = hal_config.hal_streams[i].is_hal_buffer_managed;
  }
  return OK;
}

status_t WriteToResultMetadataQueue(
    camera_metadata_t* metadata,
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* result_metadata_queue) {
  if (result_metadata_queue == nullptr) {
    return BAD_VALUE;
  }

  if (result_metadata_queue->availableToWrite() <= 0) {
    ALOGW("%s: result_metadata_queue is not available to write", __FUNCTION__);
    return BAD_VALUE;
  }

  uint32_t size = get_camera_metadata_size(metadata);
  bool success = result_metadata_queue->write(
      reinterpret_cast<const int8_t*>(metadata), size);
  if (!success) {
    ALOGW("%s: Writing to result metadata queue failed. (size=%u)",
          __FUNCTION__, size);
    return INVALID_OPERATION;
  }

  return OK;
}

// Try writing result metadata to result metadata queue. If failed, return
// the metadata to the caller in out_hal_metadata.
status_t TryWritingToResultMetadataQueue(
    std::unique_ptr<google_camera_hal::HalCameraMetadata> hal_metadata,
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* result_metadata_queue,
    uint64_t* fmq_result_size,
    std::unique_ptr<google_camera_hal::HalCameraMetadata>* out_hal_metadata) {
  if (out_hal_metadata == nullptr) {
    ALOGE("%s: out_hal_metadata is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  *out_hal_metadata = std::move(hal_metadata);

  if (fmq_result_size == nullptr) {
    ALOGE("%s: fmq_result_size is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  *fmq_result_size = 0;
  if (*out_hal_metadata == nullptr) {
    return OK;
  }

  camera_metadata_t* metadata = (*out_hal_metadata)->ReleaseCameraMetadata();
  // Temporarily use the raw metadata to write to metadata queue.
  status_t res = WriteToResultMetadataQueue(metadata, result_metadata_queue);
  *out_hal_metadata = google_camera_hal::HalCameraMetadata::Create(metadata);

  if (res != OK) {
    ALOGW("%s: Writing to result metadata queue failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  *fmq_result_size = (*out_hal_metadata)->GetCameraMetadataSize();
  *out_hal_metadata = nullptr;
  return OK;
}

status_t ConvertToAidlResultMetadata(
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* result_metadata_queue,
    std::unique_ptr<google_camera_hal::HalCameraMetadata> hal_metadata,
    std::vector<uint8_t>* aidl_metadata, uint64_t* fmq_result_size) {
  if (TryWritingToResultMetadataQueue(std::move(hal_metadata),
                                      result_metadata_queue, fmq_result_size,
                                      &hal_metadata) == OK) {
    return OK;
  }

  // If writing to metadata queue failed, attach the metadata to aidl_metadata.
  if (aidl_metadata == nullptr) {
    ALOGE("%s: aidl_metadata is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  uint32_t metadata_size = hal_metadata->GetCameraMetadataSize();
  const auto* metadata_p =
      reinterpret_cast<const uint8_t*>(hal_metadata->GetRawCameraMetadata());
  // TODO: Do we reallly need to copy here ?
  aidl_metadata->assign(metadata_p, metadata_p + metadata_size);

  return OK;
}

status_t ConvertToAidlBufferStatus(google_camera_hal::BufferStatus hal_status,
                                   BufferStatus* aidl_status) {
  if (aidl_status == nullptr) {
    ALOGE("%s: aidl_status is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (hal_status) {
    case google_camera_hal::BufferStatus::kOk:
      *aidl_status = BufferStatus::OK;
      break;
    case google_camera_hal::BufferStatus::kError:
      *aidl_status = BufferStatus::ERROR;
      break;
    default:
      ALOGE("%s: Unknown HAL buffer status: %u", __FUNCTION__, hal_status);
      return BAD_VALUE;
  }

  return OK;
}

aidl::android::hardware::common::NativeHandle makeToAidlIfNotNull(
    const native_handle_t* nh) {
  if (nh == nullptr) {
    return aidl::android::hardware::common::NativeHandle();
  }
  return makeToAidl(nh);
}

status_t ConvertToAidlStreamBuffer(
    const google_camera_hal::StreamBuffer& hal_buffer,
    StreamBuffer* aidl_buffer) {
  if (aidl_buffer == nullptr) {
    ALOGE("%s: aidl_buffer is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  aidl_buffer->streamId = hal_buffer.stream_id;
  aidl_buffer->bufferId = hal_buffer.buffer_id;
  aidl_buffer->buffer = aidl::android::hardware::common::NativeHandle();

  status_t res =
      ConvertToAidlBufferStatus(hal_buffer.status, &aidl_buffer->status);
  if (res != OK) {
    ALOGE("%s: Converting to AIDL buffer status failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  aidl_buffer->acquireFence = aidl::android::hardware::common::NativeHandle();
  aidl_buffer->releaseFence = makeToAidlIfNotNull(hal_buffer.release_fence);
  return OK;
}

status_t ConvertToAidlCaptureResultInternal(
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* result_metadata_queue,
    google_camera_hal::CaptureResult* hal_result, CaptureResult* aidl_result) {
  if (aidl_result == nullptr) {
    ALOGE("%s: aidl_result is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  if (hal_result == nullptr) {
    ALOGE("%s: hal_result is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  aidl_result->frameNumber = hal_result->frame_number;

  status_t res = ConvertToAidlResultMetadata(
      result_metadata_queue, std::move(hal_result->result_metadata),
      &aidl_result->result.metadata, (uint64_t*)&aidl_result->fmqResultSize);
  if (res != OK) {
    ALOGE("%s: Converting to AIDL result metadata failed: %s(%d).",
          __FUNCTION__, strerror(-res), res);
    return res;
  }

  aidl_result->outputBuffers.resize(hal_result->output_buffers.size());
  for (uint32_t i = 0; i < aidl_result->outputBuffers.size(); i++) {
    res = ConvertToAidlStreamBuffer(hal_result->output_buffers[i],
                                    &aidl_result->outputBuffers[i]);
    if (res != OK) {
      ALOGE("%s: Converting to AIDL output stream buffer failed: %s(%d)",
            __FUNCTION__, strerror(-res), res);
      return res;
    }
  }

  uint32_t num_input_buffers = hal_result->input_buffers.size();
  if (num_input_buffers > 0) {
    if (num_input_buffers > 1) {
      ALOGW("%s: HAL result should not have more than 1 input buffer. (=%u)",
            __FUNCTION__, num_input_buffers);
    }

    res = ConvertToAidlStreamBuffer(hal_result->input_buffers[0],
                                    &aidl_result->inputBuffer);
    if (res != OK) {
      ALOGE("%s: Converting to AIDL input stream buffer failed: %s(%d)",
            __FUNCTION__, strerror(-res), res);
      return res;
    }
  } else {
    aidl_result->inputBuffer.streamId = -1;
  }

  aidl_result->partialResult = hal_result->partial_result;
  return OK;
}

status_t ConvertToAidlCaptureResult(
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* result_metadata_queue,
    std::unique_ptr<google_camera_hal::CaptureResult> hal_result,
    CaptureResult* aidl_result) {
  if (aidl_result == nullptr) {
    ALOGE("%s: aidl_result is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  if (hal_result == nullptr) {
    ALOGE("%s: hal_result is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  status_t res = ConvertToAidlCaptureResultInternal(
      result_metadata_queue, hal_result.get(), aidl_result);
  if (res != OK) {
    ALOGE("%s: Converting to AIDL result internal failed: %s(%d).",
          __FUNCTION__, strerror(-res), res);
    return res;
  }

  uint32_t num_physical_metadata = hal_result->physical_metadata.size();
  aidl_result->physicalCameraMetadata.resize(num_physical_metadata);

  for (uint32_t i = 0; i < num_physical_metadata; i++) {
    aidl_result->physicalCameraMetadata[i].physicalCameraId =
        std::to_string(hal_result->physical_metadata[i].physical_camera_id);

    res = ConvertToAidlResultMetadata(
        result_metadata_queue,
        std::move(hal_result->physical_metadata[i].metadata),
        &aidl_result->physicalCameraMetadata[i].metadata.metadata,
        (uint64_t*)&aidl_result->physicalCameraMetadata[i].fmqMetadataSize);
    if (res != OK) {
      ALOGE("%s: Converting to AIDL physical metadata failed: %s(%d).",
            __FUNCTION__, strerror(-res), res);
      return res;
    }
  }

  return OK;
}

status_t ConvertToAidlErrorMessage(
    const google_camera_hal::ErrorMessage& hal_error, NotifyMsg* aidl_msg) {
  using Tag = aidl::android::hardware::camera::device::NotifyMsg::Tag;
  if (aidl_msg == nullptr) {
    ALOGE("%s: aidl_msg is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  ErrorMsg aidl_error;
  aidl_error.frameNumber = hal_error.frame_number;
  aidl_error.errorStreamId = hal_error.error_stream_id;

  switch (hal_error.error_code) {
    case google_camera_hal::ErrorCode::kErrorDevice:
      aidl_error.errorCode = ErrorCode::ERROR_DEVICE;
      break;
    case google_camera_hal::ErrorCode::kErrorRequest:
      aidl_error.errorCode = ErrorCode::ERROR_REQUEST;
      break;
    case google_camera_hal::ErrorCode::kErrorResult:
      aidl_error.errorCode = ErrorCode::ERROR_RESULT;
      break;
    case google_camera_hal::ErrorCode::kErrorBuffer:
      aidl_error.errorCode = ErrorCode::ERROR_BUFFER;
      break;
    default:
      ALOGE("%s: Unknown error code: %u", __FUNCTION__, hal_error.error_code);
      return BAD_VALUE;
  }
  aidl_msg->set<Tag::error>(aidl_error);
  return OK;
}

status_t ConvertToAidlShutterMessage(
    const google_camera_hal::ShutterMessage& hal_shutter, NotifyMsg* aidl_msg) {
  using Tag = aidl::android::hardware::camera::device::NotifyMsg::Tag;
  if (aidl_msg == nullptr) {
    ALOGE("%s: aidl_msg is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }
  ShutterMsg aidl_shutter;
  aidl_shutter.frameNumber = hal_shutter.frame_number;
  aidl_shutter.timestamp = hal_shutter.timestamp_ns;
  aidl_shutter.readoutTimestamp = hal_shutter.readout_timestamp_ns;
  aidl_msg->set<Tag::shutter>(aidl_shutter);
  return OK;
}

status_t ConverToAidlNotifyMessage(
    const google_camera_hal::NotifyMessage& hal_message,
    NotifyMsg* aidl_message) {
  if (aidl_message == nullptr) {
    ALOGE("%s: aidl_message is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  status_t res;
  switch (hal_message.type) {
    case google_camera_hal::MessageType::kError:
      res = ConvertToAidlErrorMessage(hal_message.message.error, aidl_message);
      if (res != OK) {
        ALOGE("%s: Converting to AIDL error message failed: %s(%d)",
              __FUNCTION__, strerror(-res), res);
        return res;
      }
      break;
    case google_camera_hal::MessageType::kShutter:
      res = ConvertToAidlShutterMessage(hal_message.message.shutter,
                                        aidl_message);
      if (res != OK) {
        ALOGE("%s: Converting to AIDL shutter message failed: %s(%d)",
              __FUNCTION__, strerror(-res), res);
        return res;
      }
      break;
    default:
      ALOGE("%s: Unknown message type: %u", __FUNCTION__, hal_message.type);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToAidlCameraDeviceStatus(
    google_camera_hal::CameraDeviceStatus hal_camera_device_status,
    CameraDeviceStatus* aidl_camera_device_status) {
  if (aidl_camera_device_status == nullptr) {
    ALOGE("%s: aidl_camera_device_status is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (hal_camera_device_status) {
    case google_camera_hal::CameraDeviceStatus::kNotPresent:
      *aidl_camera_device_status = CameraDeviceStatus::NOT_PRESENT;
      break;
    case google_camera_hal::CameraDeviceStatus::kPresent:
      *aidl_camera_device_status = CameraDeviceStatus::PRESENT;
      break;
    case google_camera_hal::CameraDeviceStatus::kEnumerating:
      *aidl_camera_device_status = CameraDeviceStatus::ENUMERATING;
      break;
    default:
      ALOGE("%s: Unknown HAL camera device status: %u", __FUNCTION__,
            hal_camera_device_status);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToAidlTorchModeStatus(
    google_camera_hal::TorchModeStatus hal_torch_status,
    TorchModeStatus* aidl_torch_status) {
  if (aidl_torch_status == nullptr) {
    ALOGE("%s: aidl_torch_status is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (hal_torch_status) {
    case google_camera_hal::TorchModeStatus::kNotAvailable:
      *aidl_torch_status = TorchModeStatus::NOT_AVAILABLE;
      break;
    case google_camera_hal::TorchModeStatus::kAvailableOff:
      *aidl_torch_status = TorchModeStatus::AVAILABLE_OFF;
      break;
    case google_camera_hal::TorchModeStatus::kAvailableOn:
      *aidl_torch_status = TorchModeStatus::AVAILABLE_ON;
      break;
    default:
      ALOGE("%s: Unknown HAL torch mode status: %u", __FUNCTION__,
            hal_torch_status);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToAidlBufferRequest(
    const std::vector<google_camera_hal::BufferRequest>& hal_buffer_requests,
    std::vector<BufferRequest>* aidl_buffer_requests) {
  if (aidl_buffer_requests == nullptr) {
    ALOGE("%s: aidl_buffer_request is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  aidl_buffer_requests->resize(hal_buffer_requests.size());
  for (uint32_t i = 0; i < hal_buffer_requests.size(); i++) {
    (*aidl_buffer_requests)[i].streamId = hal_buffer_requests[i].stream_id;
    (*aidl_buffer_requests)[i].numBuffersRequested =
        hal_buffer_requests[i].num_buffers_requested;
  }
  return OK;
}

status_t ConvertToHalBufferStatus(BufferStatus aidl_status,
                                  google_camera_hal::BufferStatus* hal_status) {
  if (hal_status == nullptr) {
    ALOGE("%s: hal_status is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (aidl_status) {
    case BufferStatus::OK:
      *hal_status = google_camera_hal::BufferStatus::kOk;
      break;
    case BufferStatus::ERROR:
      *hal_status = google_camera_hal::BufferStatus::kError;
      break;
    default:
      ALOGE("%s: Unknown AIDL buffer status: %u", __FUNCTION__, aidl_status);
      return BAD_VALUE;
  }

  return OK;
}

bool IsAidlNativeHandleNull(const NativeHandle& handle) {
  return (handle.fds.size() == 0 && handle.ints.size() == 0);
}

native_handle_t* makeFromAidlIfNotNull(const NativeHandle& handle) {
  if (IsAidlNativeHandleNull(handle)) {
    return nullptr;
  }
  return makeFromAidl(handle);
}

// We have a handles_to_delete parameter since makeFromAidl creates a
// native_handle_t
status_t ConvertToHalStreamBuffer(
    const StreamBuffer& aidl_buffer, google_camera_hal::StreamBuffer* hal_buffer,
    std::vector<native_handle_t*>* handles_to_delete) {
  if (hal_buffer == nullptr || handles_to_delete == nullptr) {
    ALOGE("%s: hal_buffer / handles_to_delete is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  hal_buffer->stream_id = aidl_buffer.streamId;
  hal_buffer->buffer_id = aidl_buffer.bufferId;
  native_handle_t* buf_handle = makeFromAidlIfNotNull(aidl_buffer.buffer);
  hal_buffer->buffer = buf_handle;
  if (buf_handle != nullptr) {
    handles_to_delete->emplace_back(buf_handle);
  }

  status_t res =
      ConvertToHalBufferStatus(aidl_buffer.status, &hal_buffer->status);
  if (res != OK) {
    ALOGE("%s: Converting to HAL buffer status failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  native_handle_t* acquire_handle =
      makeFromAidlIfNotNull(aidl_buffer.acquireFence);
  native_handle_t* release_handle =
      makeFromAidlIfNotNull(aidl_buffer.releaseFence);
  hal_buffer->acquire_fence = acquire_handle;
  hal_buffer->release_fence = release_handle;
  if (acquire_handle != nullptr) {
    handles_to_delete->emplace_back(acquire_handle);
  }

  if (release_handle != nullptr) {
    handles_to_delete->emplace_back(release_handle);
  }

  return OK;
}

status_t ConvertToHalMetadata(
    uint32_t message_queue_setting_size,
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* request_metadata_queue,
    const std::vector<uint8_t>& request_settings,
    std::unique_ptr<google_camera_hal::HalCameraMetadata>* hal_metadata) {
  if (hal_metadata == nullptr) {
    ALOGE("%s: hal_metadata is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  const camera_metadata_t* metadata = nullptr;
  std::vector<int8_t> metadata_queue_settings;
  const size_t min_camera_metadata_size =
      calculate_camera_metadata_size(/*entry_count=*/0, /*data_count=*/0);

  if (message_queue_setting_size == 0) {
    // Use the settings in the request.
    if (request_settings.size() != 0) {
      if (request_settings.size() < min_camera_metadata_size) {
        ALOGE("%s: The size of request_settings is %zu, which is not valid",
              __FUNCTION__, request_settings.size());
        return BAD_VALUE;
      }

      metadata =
          reinterpret_cast<const camera_metadata_t*>(request_settings.data());

      size_t metadata_size = get_camera_metadata_size(metadata);
      if (metadata_size != request_settings.size()) {
        ALOGE(
            "%s: Mismatch between camera metadata size (%zu) and request "
            "setting size (%zu)",
            __FUNCTION__, metadata_size, request_settings.size());
        return BAD_VALUE;
      }
    }
  } else {
    // Read the settings from request metadata queue.
    if (request_metadata_queue == nullptr) {
      ALOGE("%s: request_metadata_queue is nullptr", __FUNCTION__);
      return BAD_VALUE;
    }
    if (message_queue_setting_size < min_camera_metadata_size) {
      ALOGE("%s: invalid message queue setting size: %u", __FUNCTION__,
            message_queue_setting_size);
      return BAD_VALUE;
    }

    metadata_queue_settings.resize(message_queue_setting_size);
    bool success = request_metadata_queue->read(metadata_queue_settings.data(),
                                                message_queue_setting_size);
    if (!success) {
      ALOGE("%s: Failed to read from request metadata queue.", __FUNCTION__);
      return BAD_VALUE;
    }

    metadata = reinterpret_cast<const camera_metadata_t*>(
        metadata_queue_settings.data());

    size_t metadata_size = get_camera_metadata_size(metadata);
    if (metadata_size != message_queue_setting_size) {
      ALOGE(
          "%s: Mismatch between camera metadata size (%zu) and message "
          "queue setting size (%u)",
          __FUNCTION__, metadata_size, message_queue_setting_size);
      return BAD_VALUE;
    }
  }

  if (metadata == nullptr) {
    *hal_metadata = nullptr;
    return OK;
  }

  // Validates the injected metadata structure. This prevents memory access
  // violation that could be introduced by malformed metadata.
  // (b/236688120) In general we trust metadata sent from Framework, but this is
  // to defend an exploit chain that skips Framework's validation.
  if (validate_camera_metadata_structure(metadata, /*expected_size=*/NULL) !=
      OK) {
    ALOGE("%s: Failed to validate the metadata structure", __FUNCTION__);
    return BAD_VALUE;
  }

  *hal_metadata = google_camera_hal::HalCameraMetadata::Clone(metadata);
  return OK;
}

status_t ConvertToHalCaptureRequest(
    const CaptureRequest& aidl_request,
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* request_metadata_queue,
    google_camera_hal::CaptureRequest* hal_request,
    std::vector<native_handle_t*>* handles_to_delete) {
  if (hal_request == nullptr) {
    ALOGE("%s: hal_request is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  hal_request->frame_number = aidl_request.frameNumber;

  status_t res = ConvertToHalMetadata(
      aidl_request.fmqSettingsSize, request_metadata_queue,
      aidl_request.settings.metadata, &hal_request->settings);
  if (res != OK) {
    ALOGE("%s: Converting metadata failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  google_camera_hal::StreamBuffer hal_buffer = {};

  if (aidl_request.inputBuffer.streamId != -1) {
    res = ConvertToHalStreamBuffer(aidl_request.inputBuffer, &hal_buffer,
                                   handles_to_delete);
    if (res != OK) {
      ALOGE("%s: Converting hal stream buffer failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return res;
    }

    hal_request->input_buffers.push_back(hal_buffer);
    hal_request->input_width = aidl_request.inputWidth;
    hal_request->input_height = aidl_request.inputHeight;
  }

  for (auto& buffer : aidl_request.outputBuffers) {
    hal_buffer = {};
    status_t res =
        ConvertToHalStreamBuffer(buffer, &hal_buffer, handles_to_delete);
    if (res != OK) {
      ALOGE("%s: Converting hal stream buffer failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return res;
    }

    hal_request->output_buffers.push_back(hal_buffer);
  }

  for (auto aidl_physical_settings : aidl_request.physicalCameraSettings) {
    std::unique_ptr<google_camera_hal::HalCameraMetadata> hal_physical_settings;
    res = ConvertToHalMetadata(
        aidl_physical_settings.fmqSettingsSize, request_metadata_queue,
        aidl_physical_settings.settings.metadata, &hal_physical_settings);
    if (res != OK) {
      ALOGE("%s: Converting to HAL metadata failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return res;
    }

    uint32_t camera_id = std::stoul(aidl_physical_settings.physicalCameraId);
    hal_request->physical_camera_settings.emplace(
        camera_id, std::move(hal_physical_settings));
  }

  return OK;
}

status_t ConvertToHalBufferCaches(
    const std::vector<BufferCache>& aidl_buffer_caches,
    std::vector<google_camera_hal::BufferCache>* hal_buffer_caches) {
  if (hal_buffer_caches == nullptr) {
    ALOGE("%s: hal_buffer_caches is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  for (auto aidl_cache : aidl_buffer_caches) {
    google_camera_hal::BufferCache hal_cache;
    hal_cache.stream_id = aidl_cache.streamId;
    hal_cache.buffer_id = aidl_cache.bufferId;

    hal_buffer_caches->push_back(hal_cache);
  }

  return OK;
}

status_t ConvertToHalStreamConfigurationMode(
    StreamConfigurationMode aidl_mode,
    google_camera_hal::StreamConfigurationMode* hal_mode) {
  if (hal_mode == nullptr) {
    ALOGE("%s: hal_mode is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (aidl_mode) {
    case StreamConfigurationMode::NORMAL_MODE:
      *hal_mode = google_camera_hal::StreamConfigurationMode::kNormal;
      break;
    case StreamConfigurationMode::CONSTRAINED_HIGH_SPEED_MODE:
      *hal_mode =
          google_camera_hal::StreamConfigurationMode::kConstrainedHighSpeed;
      break;
    default:
      ALOGE("%s: Unknown configuration mode %u", __FUNCTION__, aidl_mode);
      return BAD_VALUE;
  }

  return OK;
}

static bool sensorPixelModeContains(const Stream& aidl_stream, uint32_t key) {
  using aidl::android::hardware::camera::metadata::SensorPixelMode;
  for (auto& i : aidl_stream.sensorPixelModesUsed) {
    if (i == static_cast<SensorPixelMode>(key)) {
      return true;
    }
  }
  return false;
}

void FixSensorPixelModesInStreamConfig(
    StreamConfiguration* out_aidl_stream_config) {
  if (out_aidl_stream_config == nullptr) {
    ALOGE("%s: input stream config is NULL", __FUNCTION__);
    return;
  }

  // Get the sensor pixel modes in the stream config, do one pass and check if
  // default is present in all.
  using SensorPixelMode =
      aidl::android::hardware::camera::metadata::SensorPixelMode;
  for (const auto& stream : out_aidl_stream_config->streams) {
    const auto& sensorPixelModes = stream.sensorPixelModesUsed;
    if ((std::count(sensorPixelModes.begin(), sensorPixelModes.end(),
                    static_cast<SensorPixelMode>(
                        ANDROID_SENSOR_PIXEL_MODE_DEFAULT)) == 0)) {
      return;
    }
  }

  // All of them contain DEFAULT, just override them to be default only.
  for (auto& stream : out_aidl_stream_config->streams) {
    stream.sensorPixelModesUsed.clear();
    stream.sensorPixelModesUsed.push_back(
        static_cast<SensorPixelMode>(ANDROID_SENSOR_PIXEL_MODE_DEFAULT));
  }
}

status_t ConvertToHalStreamConfig(
    const StreamConfiguration& aidl_stream_config,
    google_camera_hal::StreamConfiguration* hal_stream_config) {
  if (hal_stream_config == nullptr) {
    ALOGE("%s: hal_stream_config is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  status_t res;

  for (auto aidl_stream : aidl_stream_config.streams) {
    google_camera_hal::Stream hal_stream;
    res = ConvertToHalStream(aidl_stream, &hal_stream);
    if (res != OK) {
      ALOGE("%s: Converting to HAL stream failed: %s(%d)", __FUNCTION__,
            strerror(-res), res);
      return res;
    }
    hal_stream_config->streams.push_back(hal_stream);
  }

  res = ConvertToHalStreamConfigurationMode(aidl_stream_config.operationMode,
                                            &hal_stream_config->operation_mode);
  if (res != OK) {
    ALOGE("%s: Converting to HAL opeation mode failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  res = ConvertToHalMetadata(0, nullptr,
                             aidl_stream_config.sessionParams.metadata,
                             &hal_stream_config->session_params);
  if (res != OK) {
    ALOGE("%s: Converting to HAL metadata failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  hal_stream_config->stream_config_counter =
      aidl_stream_config.streamConfigCounter;
  hal_stream_config->multi_resolution_input_image =
      aidl_stream_config.multiResolutionInputImage;
  hal_stream_config->log_id = aidl_stream_config.logId;

  return OK;
}

status_t ConvertToHalStreamType(StreamType aidl_stream_type,
                                google_camera_hal::StreamType* hal_stream_type) {
  if (hal_stream_type == nullptr) {
    ALOGE("%s: hal_stream_type is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (aidl_stream_type) {
    case StreamType::OUTPUT:
      *hal_stream_type = google_camera_hal::StreamType::kOutput;
      break;
    case StreamType::INPUT:
      *hal_stream_type = google_camera_hal::StreamType::kInput;
      break;
    default:
      ALOGE("%s: Unknown stream type: %u", __FUNCTION__, aidl_stream_type);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToHalStreamRotation(
    StreamRotation aidl_stream_rotation,
    google_camera_hal::StreamRotation* hal_stream_rotation) {
  if (hal_stream_rotation == nullptr) {
    ALOGE("%s: hal_stream_rotation is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (aidl_stream_rotation) {
    case StreamRotation::ROTATION_0:
      *hal_stream_rotation = google_camera_hal::StreamRotation::kRotation0;
      break;
    case StreamRotation::ROTATION_90:
      *hal_stream_rotation = google_camera_hal::StreamRotation::kRotation90;
      break;
    case StreamRotation::ROTATION_180:
      *hal_stream_rotation = google_camera_hal::StreamRotation::kRotation180;
      break;
    case StreamRotation::ROTATION_270:
      *hal_stream_rotation = google_camera_hal::StreamRotation::kRotation270;
      break;
    default:
      ALOGE("%s: Unknown stream rotation: %u", __FUNCTION__,
            aidl_stream_rotation);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToHalStream(const Stream& aidl_stream,
                            google_camera_hal::Stream* hal_stream) {
  if (hal_stream == nullptr) {
    ALOGE("%s: hal_stream is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  *hal_stream = {};

  hal_stream->id = aidl_stream.id;

  status_t res =
      ConvertToHalStreamType(aidl_stream.streamType, &hal_stream->stream_type);
  if (res != OK) {
    ALOGE("%s: Converting to HAL stream type failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  hal_stream->width = aidl_stream.width;
  hal_stream->height = aidl_stream.height;
  hal_stream->format = (android_pixel_format_t)aidl_stream.format;
  hal_stream->usage = (uint64_t)aidl_stream.usage;
  hal_stream->data_space = (android_dataspace_t)aidl_stream.dataSpace;

  res = ConvertToHalStreamRotation(aidl_stream.rotation, &hal_stream->rotation);
  if (res != OK) {
    ALOGE("%s: Converting to HAL stream rotation failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  if (aidl_stream.physicalCameraId.empty()) {
    hal_stream->is_physical_camera_stream = false;
  } else {
    hal_stream->is_physical_camera_stream = true;
    hal_stream->physical_camera_id = std::stoul(aidl_stream.physicalCameraId);
  }

  hal_stream->buffer_size = aidl_stream.bufferSize;
  hal_stream->group_id = aidl_stream.groupId;

  hal_stream->intended_for_max_resolution_mode = sensorPixelModeContains(
      aidl_stream, ANDROID_SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
  hal_stream->intended_for_default_resolution_mode =
      aidl_stream.sensorPixelModesUsed.size() > 0
          ? sensorPixelModeContains(aidl_stream,
                                    ANDROID_SENSOR_PIXEL_MODE_DEFAULT)
          : true;
  hal_stream->dynamic_profile =
      static_cast<DynamicRangeProfile>(aidl_stream.dynamicRangeProfile);
  hal_stream->color_space =
      static_cast<ColorSpaceProfile>(aidl_stream.colorSpace);

  hal_stream->use_case =
      static_cast<camera_metadata_enum_android_scaler_available_stream_use_cases>(
          aidl_stream.useCase);

  return OK;
}

status_t ConvertToHalBufferRequestStatus(
    const BufferRequestStatus& aidl_buffer_request_status,
    google_camera_hal::BufferRequestStatus* hal_buffer_request_status) {
  if (hal_buffer_request_status == nullptr) {
    ALOGE("%s: hal_buffer_request_status is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  switch (aidl_buffer_request_status) {
    case BufferRequestStatus::OK:
      *hal_buffer_request_status = google_camera_hal::BufferRequestStatus::kOk;
      break;
    case BufferRequestStatus::FAILED_PARTIAL:
      *hal_buffer_request_status =
          google_camera_hal::BufferRequestStatus::kFailedPartial;
      break;
    case BufferRequestStatus::FAILED_CONFIGURING:
      *hal_buffer_request_status =
          google_camera_hal::BufferRequestStatus::kFailedConfiguring;
      break;
    case BufferRequestStatus::FAILED_ILLEGAL_ARGUMENTS:
      *hal_buffer_request_status =
          google_camera_hal::BufferRequestStatus::kFailedIllegalArgs;
      break;
    case BufferRequestStatus::FAILED_UNKNOWN:
      *hal_buffer_request_status =
          google_camera_hal::BufferRequestStatus::kFailedUnknown;
      break;
    default:
      ALOGE("%s: Failed unknown buffer request error code %d", __FUNCTION__,
            aidl_buffer_request_status);
      return BAD_VALUE;
  }

  return OK;
}

status_t ConvertToHalBufferReturnStatus(
    const StreamBufferRet& aidl_stream_buffer_return,
    google_camera_hal::BufferReturn* hal_buffer_return) {
  using Tag = aidl::android::hardware::camera::device::StreamBuffersVal::Tag;
  if (hal_buffer_return == nullptr) {
    ALOGE("%s: hal_buffer_return is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  if (aidl_stream_buffer_return.val.getTag() == Tag::error) {
    switch (aidl_stream_buffer_return.val.get<Tag::error>()) {
      case StreamBufferRequestError::NO_BUFFER_AVAILABLE:
        hal_buffer_return->val.error =
            google_camera_hal::StreamBufferRequestError::kNoBufferAvailable;
        break;
      case StreamBufferRequestError::MAX_BUFFER_EXCEEDED:
        hal_buffer_return->val.error =
            google_camera_hal::StreamBufferRequestError::kMaxBufferExceeded;
        break;
      case StreamBufferRequestError::STREAM_DISCONNECTED:
        hal_buffer_return->val.error =
            google_camera_hal::StreamBufferRequestError::kStreamDisconnected;
        break;
      case StreamBufferRequestError::UNKNOWN_ERROR:
        hal_buffer_return->val.error =
            google_camera_hal::StreamBufferRequestError::kUnknownError;
        break;
      default:
        ALOGE("%s: Unknown StreamBufferRequestError %d", __FUNCTION__,
              aidl_stream_buffer_return.val.get<Tag::error>());
        return BAD_VALUE;
    }
  } else {
    hal_buffer_return->val.error =
        google_camera_hal::StreamBufferRequestError::kOk;
  }

  return OK;
}

status_t ConvertToHalDeviceState(
    int64_t aidl_device_state,
    google_camera_hal::DeviceState& hal_device_state) {
  switch (aidl_device_state) {
    case ICameraProvider::DEVICE_STATE_NORMAL:
      hal_device_state = google_camera_hal::DeviceState::kNormal;
      break;
    case ICameraProvider::DEVICE_STATE_BACK_COVERED:
      hal_device_state = google_camera_hal::DeviceState::kBackCovered;
      break;
    case ICameraProvider::DEVICE_STATE_FRONT_COVERED:
      hal_device_state = google_camera_hal::DeviceState::kFrontCovered;
      break;
    case ICameraProvider::DEVICE_STATE_FOLDED:
      hal_device_state = google_camera_hal::DeviceState::kFolded;
      break;
    default:
      ALOGE("%s: Failed unknown device state", __FUNCTION__);
      return BAD_VALUE;
  }

  return OK;
}

}  // namespace aidl_utils
}  // namespace implementation
}  // namespace camera
}  // namespace hardware
}  // namespace android
