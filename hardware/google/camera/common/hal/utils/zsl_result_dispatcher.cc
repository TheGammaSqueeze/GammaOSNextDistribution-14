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

//#define LOG_NDEBUG 0
#define LOG_TAG "GCH_ZslResultDispatcher"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include <log/log.h>
#include <utils/Trace.h>

#include <inttypes.h>

#include "utils.h"
#include "zsl_result_dispatcher.h"

namespace android {
namespace google_camera_hal {

std::unique_ptr<ZslResultDispatcher> ZslResultDispatcher::Create(
    uint32_t partial_result_count,
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
    const StreamConfiguration& stream_config) {
  ATRACE_CALL();
  auto dispatcher = std::unique_ptr<ZslResultDispatcher>(
      new ZslResultDispatcher(process_capture_result, notify));
  if (dispatcher == nullptr) {
    ALOGE("%s: Creating ZslResultDispatcher failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res = dispatcher->Initialize(partial_result_count, stream_config);
  if (res != OK) {
    ALOGE("%s: Initialize failed.", __FUNCTION__);
    return nullptr;
  }

  return dispatcher;
}

ZslResultDispatcher::ZslResultDispatcher(
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify)
    : device_session_process_capture_result_(process_capture_result),
      device_session_notify_(notify) {
}

status_t ZslResultDispatcher::Initialize(
    uint32_t partial_result_count, const StreamConfiguration& stream_config) {
  ATRACE_CALL();
  process_capture_result_ =
      ProcessCaptureResultFunc([this](std::unique_ptr<CaptureResult> result) {
        ProcessCaptureResult(std::move(result));
      });
  notify_ = NotifyFunc(
      [this](const NotifyMessage& message) { NotifyHalMessage(message); });

  normal_result_dispatcher_ = std::unique_ptr<ResultDispatcher>(
      new ResultDispatcher(partial_result_count, process_capture_result_,
                           notify_, stream_config, "ZslNormalDispatcher"));
  if (normal_result_dispatcher_ == nullptr) {
    ALOGE("%s: Creating normal_result_dispatcher_ failed.", __FUNCTION__);
    return BAD_VALUE;
  }

  zsl_result_dispatcher_ = std::unique_ptr<ResultDispatcher>(
      new ResultDispatcher(partial_result_count, process_capture_result_,
                           notify_, stream_config, "ZslZslDispatcher"));
  if (zsl_result_dispatcher_ == nullptr) {
    ALOGE("%s: Creating zsl_result_dispatcher_ failed.", __FUNCTION__);
    return BAD_VALUE;
  }

  return OK;
}

void ZslResultDispatcher::ProcessCaptureResult(
    std::unique_ptr<CaptureResult> result) {
  std::lock_guard<std::mutex> lock(process_capture_result_lock_);
  device_session_process_capture_result_(std::move(result));
}

bool ZslResultDispatcher::IsZslFrame(uint32_t frame_number) {
  std::lock_guard<std::mutex> lock(zsl_frames_lock_);
  if (zsl_frames_.empty()) {
    return false;
  }
  if (std::find(zsl_frames_.begin(), zsl_frames_.end(), frame_number) ==
      zsl_frames_.end()) {
    return false;
  }
  return true;
}

void ZslResultDispatcher::NotifyHalMessage(const NotifyMessage& message) {
  std::lock_guard<std::mutex> lock(result_lock_);
  device_session_notify_(message);
}

status_t ZslResultDispatcher::AddPendingRequest(
    const CaptureRequest& pending_request, bool is_zsl_request) {
  ATRACE_CALL();
  if (is_zsl_request) {
    uint32_t frame_number = pending_request.frame_number;
    {
      std::lock_guard<std::mutex> lock(zsl_frames_lock_);
      zsl_frames_.push_back(frame_number);
    }

    status_t res = zsl_result_dispatcher_->AddPendingRequest(pending_request);
    if (res != OK) {
      std::lock_guard<std::mutex> lock(zsl_frames_lock_);
      zsl_frames_.erase(
          std::find(zsl_frames_.begin(), zsl_frames_.end(), frame_number));
    }
    return res;
  } else {
    return normal_result_dispatcher_->AddPendingRequest(pending_request);
  }
}

status_t ZslResultDispatcher::AddResult(std::unique_ptr<CaptureResult> result) {
  ATRACE_CALL();
  if (result == nullptr) {
    ALOGE("%s: result is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }
  uint32_t frame_number = result->frame_number;
  bool is_zsl_request = IsZslFrame(frame_number);
  if (is_zsl_request) {
    return zsl_result_dispatcher_->AddResult(std::move(result));
  } else {
    return normal_result_dispatcher_->AddResult(std::move(result));
  }
}

status_t ZslResultDispatcher::AddShutter(uint32_t frame_number,
                                         int64_t timestamp_ns,
                                         int64_t readout_timestamp_ns) {
  ATRACE_CALL();
  bool is_zsl_request = IsZslFrame(frame_number);
  if (is_zsl_request) {
    return zsl_result_dispatcher_->AddShutter(frame_number, timestamp_ns,
                                              readout_timestamp_ns);
  } else {
    return normal_result_dispatcher_->AddShutter(frame_number, timestamp_ns,
                                                 readout_timestamp_ns);
  }
}

status_t ZslResultDispatcher::AddError(const ErrorMessage& error) {
  ATRACE_CALL();
  uint32_t frame_number = error.frame_number;
  bool is_zsl_request = IsZslFrame(frame_number);
  if (is_zsl_request) {
    return zsl_result_dispatcher_->AddError(error);
  } else {
    return normal_result_dispatcher_->AddError(error);
  }
}

void ZslResultDispatcher::RemovePendingRequest(uint32_t frame_number) {
  ATRACE_CALL();
  bool is_zsl_request = IsZslFrame(frame_number);
  if (is_zsl_request) {
    zsl_result_dispatcher_->RemovePendingRequest(frame_number);
    std::lock_guard<std::mutex> lock(zsl_frames_lock_);
    zsl_frames_.erase(
        std::find(zsl_frames_.begin(), zsl_frames_.end(), frame_number));
  } else {
    normal_result_dispatcher_->RemovePendingRequest(frame_number);
  }
}

}  // namespace google_camera_hal
}  // namespace android
