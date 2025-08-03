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
#define LOG_TAG "GCH_SnapshotResultProcessor"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include "snapshot_result_processor.h"

#include <inttypes.h>
#include <log/log.h>
#include <utils/Trace.h>

#include "hal_utils.h"

namespace android {
namespace google_camera_hal {

std::unique_ptr<SnapshotResultProcessor> SnapshotResultProcessor::Create(
    InternalStreamManager* internal_stream_manager, int32_t yuv_stream_id) {
  ATRACE_CALL();
  if (internal_stream_manager == nullptr) {
    ALOGE("%s: internal_stream_manager nullptr.", __FUNCTION__);
    return nullptr;
  }

  auto result_processor = std::unique_ptr<SnapshotResultProcessor>(
      new SnapshotResultProcessor(internal_stream_manager, yuv_stream_id));
  if (result_processor == nullptr) {
    ALOGE("%s: Creating SnapshotResultProcessor failed.", __FUNCTION__);
    return nullptr;
  }

  return result_processor;
}

SnapshotResultProcessor::SnapshotResultProcessor(
    InternalStreamManager* internal_stream_manager, int32_t yuv_stream_id) {
  internal_stream_manager_ = internal_stream_manager;
  yuv_stream_id_ = yuv_stream_id;
}
void SnapshotResultProcessor::SetResultCallback(
    ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
    ProcessBatchCaptureResultFunc /*process_batch_capture_result*/) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);
  process_capture_result_ = process_capture_result;
  notify_ = notify;
}

status_t SnapshotResultProcessor::AddPendingRequests(
    const std::vector<ProcessBlockRequest>& process_block_requests,
    const CaptureRequest& remaining_session_request) {
  ATRACE_CALL();
  // This is the last result processor. Validity check if requests contains
  // all remaining output buffers.
  if (!hal_utils::AreAllRemainingBuffersRequested(process_block_requests,
                                                  remaining_session_request)) {
    ALOGE("%s: Some output buffers will not be completed.", __FUNCTION__);
    return BAD_VALUE;
  }

  return OK;
}

void SnapshotResultProcessor::ProcessResult(ProcessBlockResult block_result) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);

  std::unique_ptr<CaptureResult> result = std::move(block_result.result);
  if (result == nullptr) {
    ALOGW("%s: Received a nullptr result.", __FUNCTION__);
    return;
  }

  if (process_capture_result_ == nullptr) {
    ALOGE("%s: process_capture_result_ is nullptr. Dropping a result.",
          __FUNCTION__);
    return;
  }

  // Return yuv buffer to internal stream manager and remove it from result
  status_t res;
  if (result->output_buffers.size() != 0 &&
      internal_stream_manager_->IsPendingBufferEmpty(yuv_stream_id_) == false) {
    res = internal_stream_manager_->ReturnZslStreamBuffers(result->frame_number,
                                                           yuv_stream_id_);
    if (res != OK) {
      ALOGE("%s: (%d)ReturnZslStreamBuffers fail", __FUNCTION__,
            result->frame_number);
    } else {
      ALOGI("%s: (%d)ReturnZslStreamBuffers ok", __FUNCTION__,
            result->frame_number);
    }
    result->input_buffers.clear();
  }

  if (result->result_metadata) {
    res = hal_utils::SetEnableZslMetadata(result->result_metadata.get(), true);
    if (res != OK) {
      ALOGW("%s: SetEnableZslMetadata (%d) fail", __FUNCTION__,
            result->frame_number);
    }
  }

  process_capture_result_(std::move(result));
}

void SnapshotResultProcessor::Notify(
    const ProcessBlockNotifyMessage& block_message) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);
  if (notify_ == nullptr) {
    ALOGE("%s: notify_ is nullptr. Dropping a message.", __FUNCTION__);
    return;
  }

  notify_(block_message.message);
}

status_t SnapshotResultProcessor::FlushPendingRequests() {
  ATRACE_CALL();
  return INVALID_OPERATION;
}

}  // namespace google_camera_hal
}  // namespace android