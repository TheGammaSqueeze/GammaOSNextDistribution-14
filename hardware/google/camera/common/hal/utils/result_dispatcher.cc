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
#define LOG_TAG "GCH_ResultDispatcher"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include "result_dispatcher.h"

#include <inttypes.h>
#include <log/log.h>
#include <sys/resource.h>
#include <utils/Trace.h>

#include <string>
#include <string_view>

#include "utils.h"

namespace android {
namespace google_camera_hal {

std::unique_ptr<ResultDispatcher> ResultDispatcher::Create(
    uint32_t partial_result_count,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
    const StreamConfiguration& stream_config, std::string_view name) {
  ATRACE_CALL();
  auto dispatcher = std::unique_ptr<ResultDispatcher>(
      new ResultDispatcher(partial_result_count, process_capture_result, notify,
                           stream_config, name));
  if (dispatcher == nullptr) {
    ALOGE("[%s] %s: Creating ResultDispatcher failed.",
          std::string(name).c_str(), __FUNCTION__);
    return nullptr;
  }

  return dispatcher;
}

ResultDispatcher::ResultDispatcher(
    uint32_t partial_result_count,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
    const StreamConfiguration& stream_config, std::string_view name)
    : kPartialResultCount(partial_result_count),
      name_(name),
      process_capture_result_(process_capture_result),
      notify_(notify) {
  ATRACE_CALL();
  notify_callback_thread_ =
      std::thread([this] { this->NotifyCallbackThreadLoop(); });

  // Assign higher priority to reduce the preemption when CPU usage is high
  //
  // As from b/295977499, we need to make it realtime for priority inheritance
  // to avoid CameraServer thread being the bottleneck
  status_t res =
      utils::SetRealtimeThread(notify_callback_thread_.native_handle());
  if (res != OK) {
    ALOGE("[%s] %s: SetRealtimeThread fail", name_.c_str(), __FUNCTION__);
  } else {
    ALOGI("[%s] %s: SetRealtimeThread OK", name_.c_str(), __FUNCTION__);
  }
  InitializeGroupStreamIdsMap(stream_config);
}

ResultDispatcher::~ResultDispatcher() {
  ATRACE_CALL();
  {
    std::unique_lock<std::mutex> lock(notify_callback_lock_);
    notify_callback_thread_exiting_ = true;
  }

  notify_callback_condition_.notify_one();
  notify_callback_thread_.join();
}

void ResultDispatcher::RemovePendingRequest(uint32_t frame_number) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(result_lock_);
  RemovePendingRequestLocked(frame_number);
}

status_t ResultDispatcher::AddPendingRequest(
    const CaptureRequest& pending_request) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(result_lock_);

  status_t res = AddPendingRequestLocked(pending_request);
  if (res != OK) {
    ALOGE("[%s] %s: Adding a pending request failed: %s(%d).", name_.c_str(),
          __FUNCTION__, strerror(-res), res);
    RemovePendingRequestLocked(pending_request.frame_number);
    return res;
  }

  return OK;
}

status_t ResultDispatcher::AddPendingRequestLocked(
    const CaptureRequest& pending_request) {
  ATRACE_CALL();
  uint32_t frame_number = pending_request.frame_number;

  status_t res = AddPendingShutterLocked(frame_number);
  if (res != OK) {
    ALOGE("[%s] %s: Adding pending shutter for frame %u failed: %s(%d)",
          name_.c_str(), __FUNCTION__, frame_number, strerror(-res), res);
    return res;
  }

  res = AddPendingFinalResultMetadataLocked(frame_number);
  if (res != OK) {
    ALOGE("[%s] %s: Adding pending result metadata for frame %u failed: %s(%d)",
          name_.c_str(), __FUNCTION__, frame_number, strerror(-res), res);
    return res;
  }

  for (auto& buffer : pending_request.input_buffers) {
    res = AddPendingBufferLocked(frame_number, buffer, /*is_input=*/true);
    if (res != OK) {
      ALOGE("[%s] %s: Adding pending input buffer for frame %u failed: %s(%d)",
            name_.c_str(), __FUNCTION__, frame_number, strerror(-res), res);
      return res;
    }
  }

  for (auto& buffer : pending_request.output_buffers) {
    res = AddPendingBufferLocked(frame_number, buffer, /*is_input=*/false);
    if (res != OK) {
      ALOGE("[%s] %s: Adding pending output buffer for frame %u failed: %s(%d)",
            name_.c_str(), __FUNCTION__, frame_number, strerror(-res), res);
      return res;
    }
  }

  return OK;
}

status_t ResultDispatcher::AddPendingShutterLocked(uint32_t frame_number) {
  ATRACE_CALL();
  if (pending_shutters_.find(frame_number) != pending_shutters_.end()) {
    ALOGE("[%s] %s: Pending shutter for frame %u already exists.",
          name_.c_str(), __FUNCTION__, frame_number);
    return ALREADY_EXISTS;
  }

  pending_shutters_[frame_number] = PendingShutter();
  return OK;
}

status_t ResultDispatcher::AddPendingFinalResultMetadataLocked(
    uint32_t frame_number) {
  ATRACE_CALL();
  if (pending_final_metadata_.find(frame_number) !=
      pending_final_metadata_.end()) {
    ALOGE("[%s] %s: Pending final result metadata for frame %u already exists.",
          name_.c_str(), __FUNCTION__, frame_number);
    return ALREADY_EXISTS;
  }

  pending_final_metadata_[frame_number] = PendingFinalResultMetadata();
  return OK;
}

status_t ResultDispatcher::AddPendingBufferLocked(uint32_t frame_number,
                                                  const StreamBuffer& buffer,
                                                  bool is_input) {
  ATRACE_CALL();
  StreamKey stream_key = CreateStreamKey(buffer.stream_id);
  if (stream_pending_buffers_map_.find(stream_key) ==
      stream_pending_buffers_map_.end()) {
    stream_pending_buffers_map_[stream_key] =
        std::map<uint32_t, PendingBuffer>();
  }

  if (stream_pending_buffers_map_[stream_key].find(frame_number) !=
      stream_pending_buffers_map_[stream_key].end()) {
    ALOGE("[%s] %s: Pending buffer of stream %s for frame %u already exists.",
          name_.c_str(), __FUNCTION__, DumpStreamKey(stream_key).c_str(),
          frame_number);
    return ALREADY_EXISTS;
  }

  PendingBuffer pending_buffer = {.is_input = is_input};
  stream_pending_buffers_map_[stream_key][frame_number] = pending_buffer;
  return OK;
}

void ResultDispatcher::RemovePendingRequestLocked(uint32_t frame_number) {
  ATRACE_CALL();
  pending_shutters_.erase(frame_number);
  pending_final_metadata_.erase(frame_number);

  for (auto& pending_buffers : stream_pending_buffers_map_) {
    pending_buffers.second.erase(frame_number);
  }
}

status_t ResultDispatcher::AddResult(std::unique_ptr<CaptureResult> result) {
  ATRACE_CALL();
  status_t res;
  bool failed = false;
  uint32_t frame_number = result->frame_number;

  if (result->result_metadata != nullptr) {
    res = AddResultMetadata(frame_number, std::move(result->result_metadata),
                            std::move(result->physical_metadata),
                            result->partial_result);
    if (res != OK) {
      ALOGE("[%s] %s: Adding result metadata failed: %s (%d)", name_.c_str(),
            __FUNCTION__, strerror(-res), res);
      failed = true;
    }
  }

  for (auto& buffer : result->output_buffers) {
    res = AddBuffer(frame_number, buffer);
    if (res != OK) {
      ALOGE("[%s] %s: Adding an output buffer failed: %s (%d)", name_.c_str(),
            __FUNCTION__, strerror(-res), res);
      failed = true;
    }
  }

  for (auto& buffer : result->input_buffers) {
    res = AddBuffer(frame_number, buffer);
    if (res != OK) {
      ALOGE("[%s] %s: Adding an input buffer failed: %s (%d)", name_.c_str(),
            __FUNCTION__, strerror(-res), res);
      failed = true;
    }
  }
  {
    std::unique_lock<std::mutex> lock(notify_callback_lock_);
    is_result_shutter_updated_ = true;
    notify_callback_condition_.notify_one();
  }
  return failed ? UNKNOWN_ERROR : OK;
}

status_t ResultDispatcher::AddShutter(uint32_t frame_number,
                                      int64_t timestamp_ns,
                                      int64_t readout_timestamp_ns) {
  ATRACE_CALL();
  {
    std::lock_guard<std::mutex> lock(result_lock_);

    auto shutter_it = pending_shutters_.find(frame_number);
    if (shutter_it == pending_shutters_.end()) {
      ALOGE("[%s] %s: Cannot find the pending shutter for frame %u",
            name_.c_str(), __FUNCTION__, frame_number);
      return NAME_NOT_FOUND;
    }

    if (shutter_it->second.ready) {
      ALOGE("[%s] %s: Already received shutter (%" PRId64
            ") for frame %u. New timestamp %" PRId64,
            name_.c_str(), __FUNCTION__, shutter_it->second.timestamp_ns,
            frame_number, timestamp_ns);
      return ALREADY_EXISTS;
    }

    shutter_it->second.timestamp_ns = timestamp_ns;
    shutter_it->second.readout_timestamp_ns = readout_timestamp_ns;
    shutter_it->second.ready = true;
  }
  {
    std::unique_lock<std::mutex> lock(notify_callback_lock_);
    is_result_shutter_updated_ = true;
    notify_callback_condition_.notify_one();
  }
  return OK;
}

status_t ResultDispatcher::AddError(const ErrorMessage& error) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(result_lock_);
  uint32_t frame_number = error.frame_number;
  // No need to deliver the shutter message on an error
  if (error.error_code == ErrorCode::kErrorDevice ||
      error.error_code == ErrorCode::kErrorResult ||
      error.error_code == ErrorCode::kErrorRequest) {
    pending_shutters_.erase(frame_number);
  }
  // No need to deliver the result metadata on a result metadata error
  if (error.error_code == ErrorCode::kErrorResult ||
      error.error_code == ErrorCode::kErrorRequest) {
    pending_final_metadata_.erase(frame_number);
  }

  NotifyMessage message = {.type = MessageType::kError, .message.error = error};
  ALOGV("[%s] %s: Notify error %u for frame %u stream %d", name_.c_str(),
        __FUNCTION__, error.error_code, frame_number, error.error_stream_id);
  notify_(message);

  return OK;
}

void ResultDispatcher::NotifyResultMetadata(
    uint32_t frame_number, std::unique_ptr<HalCameraMetadata> metadata,
    std::vector<PhysicalCameraMetadata> physical_metadata,
    uint32_t partial_result) {
  ATRACE_CALL();
  auto result = std::make_unique<CaptureResult>(CaptureResult({}));
  result->frame_number = frame_number;
  result->result_metadata = std::move(metadata);
  result->physical_metadata = std::move(physical_metadata);
  result->partial_result = partial_result;

  std::lock_guard<std::mutex> lock(process_capture_result_lock_);
  process_capture_result_(std::move(result));
}

status_t ResultDispatcher::AddFinalResultMetadata(
    uint32_t frame_number, std::unique_ptr<HalCameraMetadata> final_metadata,
    std::vector<PhysicalCameraMetadata> physical_metadata) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(result_lock_);

  auto metadata_it = pending_final_metadata_.find(frame_number);
  if (metadata_it == pending_final_metadata_.end()) {
    ALOGE("[%s] %s: Cannot find the pending result metadata for frame %u",
          name_.c_str(), __FUNCTION__, frame_number);
    return NAME_NOT_FOUND;
  }

  if (metadata_it->second.ready) {
    ALOGE("[%s] %s: Already received final result metadata for frame %u.",
          name_.c_str(), __FUNCTION__, frame_number);
    return ALREADY_EXISTS;
  }

  metadata_it->second.metadata = std::move(final_metadata);
  metadata_it->second.physical_metadata = std::move(physical_metadata);
  metadata_it->second.ready = true;
  return OK;
}

status_t ResultDispatcher::AddResultMetadata(
    uint32_t frame_number, std::unique_ptr<HalCameraMetadata> metadata,
    std::vector<PhysicalCameraMetadata> physical_metadata,
    uint32_t partial_result) {
  ATRACE_CALL();
  if (metadata == nullptr) {
    ALOGE("[%s] %s: metadata is nullptr.", name_.c_str(), __FUNCTION__);
    return BAD_VALUE;
  }

  if (partial_result > kPartialResultCount) {
    ALOGE(
        "[%s] %s: partial_result %u cannot be larger than partial result count "
        "%u",
        name_.c_str(), __FUNCTION__, partial_result, kPartialResultCount);
    return BAD_VALUE;
  }

  if (partial_result < kPartialResultCount) {
    // Send out partial results immediately.
    NotifyResultMetadata(frame_number, std::move(metadata),
                         std::move(physical_metadata), partial_result);
    return OK;
  }

  return AddFinalResultMetadata(frame_number, std::move(metadata),
                                std::move(physical_metadata));
}

status_t ResultDispatcher::AddBuffer(uint32_t frame_number,
                                     StreamBuffer buffer) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(result_lock_);

  StreamKey stream_key = CreateStreamKey(buffer.stream_id);
  auto pending_buffers_it = stream_pending_buffers_map_.find(stream_key);
  if (pending_buffers_it == stream_pending_buffers_map_.end()) {
    ALOGE("[%s] %s: Cannot find the pending buffer for stream %s",
          name_.c_str(), __FUNCTION__, DumpStreamKey(stream_key).c_str());
    return NAME_NOT_FOUND;
  }

  auto pending_buffer_it = pending_buffers_it->second.find(frame_number);
  if (pending_buffer_it == pending_buffers_it->second.end()) {
    ALOGE("[%s] %s: Cannot find the pending buffer for stream %s for frame %u",
          name_.c_str(), __FUNCTION__, DumpStreamKey(stream_key).c_str(),
          frame_number);
    return NAME_NOT_FOUND;
  }

  if (pending_buffer_it->second.ready) {
    ALOGE("[%s] %s: Already received a buffer for stream %s for frame %u",
          name_.c_str(), __FUNCTION__, DumpStreamKey(stream_key).c_str(),
          frame_number);
    return ALREADY_EXISTS;
  }

  pending_buffer_it->second.buffer = std::move(buffer);
  pending_buffer_it->second.ready = true;

  return OK;
}

void ResultDispatcher::NotifyCallbackThreadLoop() {
  // '\0' counts toward the 16-character restriction.
  constexpr int kPthreadNameLenMinusOne = 16 - 1;
  pthread_setname_np(
      pthread_self(),
      name_.substr(/*pos=*/0, /*count=*/kPthreadNameLenMinusOne).c_str());

  while (1) {
    NotifyShutters();
    NotifyFinalResultMetadata();
    NotifyBuffers();

    std::unique_lock<std::mutex> lock(notify_callback_lock_);
    if (notify_callback_thread_exiting_) {
      ALOGV("[%s] %s: NotifyCallbackThreadLoop exits.", name_.c_str(),
            __FUNCTION__);
      return;
    }
    if (!is_result_shutter_updated_) {
      if (notify_callback_condition_.wait_for(
              lock, std::chrono::milliseconds(kCallbackThreadTimeoutMs)) ==
          std::cv_status::timeout) {
        PrintTimeoutMessages();
      }
    }
    is_result_shutter_updated_ = false;
  }
}

void ResultDispatcher::PrintTimeoutMessages() {
  std::lock_guard<std::mutex> lock(result_lock_);
  for (auto& [frame_number, shutter] : pending_shutters_) {
    ALOGW("[%s] %s: pending shutter for frame %u ready %d", name_.c_str(),
          __FUNCTION__, frame_number, shutter.ready);
  }

  for (auto& [frame_number, final_metadata] : pending_final_metadata_) {
    ALOGW("[%s] %s: pending final result metadaata for frame %u ready %d",
          name_.c_str(), __FUNCTION__, frame_number, final_metadata.ready);
  }

  for (auto& [stream_key, pending_buffers] : stream_pending_buffers_map_) {
    for (auto& [frame_number, pending_buffer] : pending_buffers) {
      ALOGW("[%s] %s: pending buffer of stream %s for frame %u ready %d",
            name_.c_str(), __FUNCTION__, DumpStreamKey(stream_key).c_str(),
            frame_number, pending_buffer.ready);
    }
  }
}

void ResultDispatcher::InitializeGroupStreamIdsMap(
    const StreamConfiguration& stream_config) {
  std::lock_guard<std::mutex> lock(result_lock_);
  for (const auto& stream : stream_config.streams) {
    if (stream.group_id != -1) {
      group_stream_map_[stream.id] = stream.group_id;
    }
  }
}

ResultDispatcher::StreamKey ResultDispatcher::CreateStreamKey(
    int32_t stream_id) const {
  if (group_stream_map_.count(stream_id) == 0) {
    return StreamKey(stream_id, StreamKeyType::kSingleStream);
  } else {
    return StreamKey(group_stream_map_.at(stream_id),
                     StreamKeyType::kGroupStream);
  }
}

std::string ResultDispatcher::DumpStreamKey(const StreamKey& stream_key) const {
  switch (stream_key.second) {
    case StreamKeyType::kSingleStream:
      return std::to_string(stream_key.first);
    case StreamKeyType::kGroupStream:
      return "group " + std::to_string(stream_key.first);
    default:
      return "Invalid stream key type";
  }
}

status_t ResultDispatcher::GetReadyShutterMessage(NotifyMessage* message) {
  ATRACE_CALL();
  if (message == nullptr) {
    ALOGE("[%s] %s: message is nullptr", name_.c_str(), __FUNCTION__);
    return BAD_VALUE;
  }

  auto shutter_it = pending_shutters_.begin();
  if (shutter_it == pending_shutters_.end() || !shutter_it->second.ready) {
    // The first pending shutter is not ready.
    return NAME_NOT_FOUND;
  }

  message->type = MessageType::kShutter;
  message->message.shutter.frame_number = shutter_it->first;
  message->message.shutter.timestamp_ns = shutter_it->second.timestamp_ns;
  message->message.shutter.readout_timestamp_ns =
      shutter_it->second.readout_timestamp_ns;
  pending_shutters_.erase(shutter_it);

  return OK;
}

void ResultDispatcher::NotifyShutters() {
  ATRACE_CALL();
  NotifyMessage message = {};
  while (true) {
    std::lock_guard<std::mutex> lock(result_lock_);
    if (GetReadyShutterMessage(&message) != OK) {
      break;
    }
    ALOGV("[%s] %s: Notify shutter for frame %u timestamp %" PRIu64
          " readout_timestamp %" PRIu64,
          name_.c_str(), __FUNCTION__, message.message.shutter.frame_number,
          message.message.shutter.timestamp_ns,
          message.message.shutter.readout_timestamp_ns);
    notify_(message);
  }
}

status_t ResultDispatcher::GetReadyFinalMetadata(
    uint32_t* frame_number, std::unique_ptr<HalCameraMetadata>* final_metadata,
    std::vector<PhysicalCameraMetadata>* physical_metadata) {
  ATRACE_CALL();
  if (final_metadata == nullptr || frame_number == nullptr) {
    ALOGE("[%s] %s: final_metadata (%p) or frame_number (%p) is nullptr",
          name_.c_str(), __FUNCTION__, final_metadata, frame_number);
    return BAD_VALUE;
  }

  std::lock_guard<std::mutex> lock(result_lock_);

  auto final_metadata_it = pending_final_metadata_.begin();
  if (final_metadata_it == pending_final_metadata_.end() ||
      !final_metadata_it->second.ready) {
    // The first pending final metadata is not ready.
    return NAME_NOT_FOUND;
  }

  *frame_number = final_metadata_it->first;
  *final_metadata = std::move(final_metadata_it->second.metadata);
  *physical_metadata = std::move(final_metadata_it->second.physical_metadata);
  pending_final_metadata_.erase(final_metadata_it);

  return OK;
}

void ResultDispatcher::NotifyFinalResultMetadata() {
  ATRACE_CALL();
  uint32_t frame_number;
  std::unique_ptr<HalCameraMetadata> final_metadata;
  std::vector<PhysicalCameraMetadata> physical_metadata;

  while (GetReadyFinalMetadata(&frame_number, &final_metadata,
                               &physical_metadata) == OK) {
    ALOGV("[%s] %s: Notify final metadata for frame %u", name_.c_str(),
          __FUNCTION__, frame_number);
    NotifyResultMetadata(frame_number, std::move(final_metadata),
                         std::move(physical_metadata), kPartialResultCount);
  }
}

status_t ResultDispatcher::GetReadyBufferResult(
    std::unique_ptr<CaptureResult>* result) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(result_lock_);
  if (result == nullptr) {
    ALOGE("[%s] %s: result is nullptr.", name_.c_str(), __FUNCTION__);
    return BAD_VALUE;
  }

  *result = nullptr;

  for (auto& pending_buffers : stream_pending_buffers_map_) {
    auto buffer_it = pending_buffers.second.begin();
    while (buffer_it != pending_buffers.second.end()) {
      if (!buffer_it->second.ready) {
        // No more buffer ready.
        break;
      }

      auto buffer_result = std::make_unique<CaptureResult>(CaptureResult({}));

      buffer_result->frame_number = buffer_it->first;
      if (buffer_it->second.is_input) {
        buffer_result->input_buffers.push_back(buffer_it->second.buffer);
      } else {
        buffer_result->output_buffers.push_back(buffer_it->second.buffer);
      }

      pending_buffers.second.erase(buffer_it);
      *result = std::move(buffer_result);
      return OK;
    }
  }

  return NAME_NOT_FOUND;
}

void ResultDispatcher::NotifyBuffers() {
  ATRACE_CALL();
  std::unique_ptr<CaptureResult> result;

  while (GetReadyBufferResult(&result) == OK) {
    if (result == nullptr) {
      ALOGE("[%s] %s: result is nullptr", name_.c_str(), __FUNCTION__);
      return;
    }
    std::lock_guard<std::mutex> lock(process_capture_result_lock_);
    process_capture_result_(std::move(result));
  }
}

}  // namespace google_camera_hal
}  // namespace android
