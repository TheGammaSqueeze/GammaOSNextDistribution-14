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
#define LOG_TAG "GCH_RealtimeZslResultRequestProcessor"
#define ATRACE_TAG ATRACE_TAG_CAMERA

#include "realtime_zsl_result_request_processor.h"

#include <inttypes.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <memory>

#include "hal_types.h"
#include "hal_utils.h"
#include "realtime_zsl_result_processor.h"

namespace android {
namespace google_camera_hal {

bool RealtimeZslResultRequestProcessor::AllDataCollected(
    const RequestEntry& request_entry) const {
  return request_entry.zsl_buffer_received &&
         request_entry.framework_buffer_count ==
             static_cast<int>(
                 request_entry.capture_request->output_buffers.size()) &&
         request_entry.partial_results_received == partial_result_count_;
}

std::unique_ptr<RealtimeZslResultRequestProcessor>
RealtimeZslResultRequestProcessor::Create(
    InternalStreamManager* internal_stream_manager, int32_t stream_id,
    android_pixel_format_t pixel_format, uint32_t partial_result_count) {
  ATRACE_CALL();
  if (internal_stream_manager == nullptr) {
    ALOGE("%s: internal_stream_manager is nullptr.", __FUNCTION__);
    return nullptr;
  }

  auto result_processor = std::unique_ptr<RealtimeZslResultRequestProcessor>(
      new RealtimeZslResultRequestProcessor(internal_stream_manager, stream_id,
                                            pixel_format, partial_result_count));
  if (result_processor == nullptr) {
    ALOGE("%s: Creating RealtimeZslResultRequestProcessor failed.",
          __FUNCTION__);
    return nullptr;
  }

  return result_processor;
}

RealtimeZslResultRequestProcessor::RealtimeZslResultRequestProcessor(
    InternalStreamManager* internal_stream_manager, int32_t stream_id,
    android_pixel_format_t pixel_format, uint32_t partial_result_count)
    : RealtimeZslResultProcessor(internal_stream_manager, stream_id,
                                 pixel_format, partial_result_count) {
}

void RealtimeZslResultRequestProcessor::UpdateOutputBufferCount(
    int32_t frame_number, int output_buffer_count, bool is_preview_intent) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);
  // Cache the CaptureRequest in a queue as the metadata and buffers may not
  // come together.
  RequestEntry request_entry = {
      .capture_request = std::make_unique<CaptureRequest>(),
      .framework_buffer_count = output_buffer_count};
  request_entry.capture_request->frame_number = frame_number;
  if (!is_preview_intent) {
    // If no preview intent is provided, RealtimeZslRequestProcessor will not
    // add an internal buffer to the request so there is no ZSL buffer to wait
    // for in that case.
    request_entry.zsl_buffer_received = true;
  }

  pending_frame_number_to_requests_[frame_number] = std::move(request_entry);
}

void RealtimeZslResultRequestProcessor::ProcessResult(
    ProcessBlockResult block_result) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);
  std::unique_ptr<CaptureResult> result = std::move(block_result.result);
  if (result == nullptr) {
    ALOGW("%s: Received a nullptr result.", __FUNCTION__);
    return;
  }

  // May change to ALOGD for per-frame results.
  ALOGV(
      "%s: Received result at frame: %d, has metadata (%s), output buffer "
      "counts: %zu, input buffer counts: %zu",
      __FUNCTION__, result->frame_number,
      (result->result_metadata ? "yes" : "no"), result->output_buffers.size(),
      result->input_buffers.size());

  // Pending request should always exist
  RequestEntry& pending_request =
      pending_frame_number_to_requests_[result->frame_number];
  if (pending_request.capture_request == nullptr) {
    pending_request.capture_request = std::make_unique<CaptureRequest>();
    pending_request.capture_request->frame_number = result->frame_number;
  }

  // Return filled raw buffer to internal stream manager
  // And remove raw buffer from result
  status_t res;
  std::vector<StreamBuffer> modified_output_buffers;
  for (uint32_t i = 0; i < result->output_buffers.size(); i++) {
    if (stream_id_ == result->output_buffers[i].stream_id) {
      pending_request.has_returned_output_to_internal_stream_manager = true;
      res = internal_stream_manager_->ReturnFilledBuffer(
          result->frame_number, result->output_buffers[i]);
      if (res != OK) {
        ALOGW("%s: (%d)ReturnStreamBuffer fail", __FUNCTION__,
              result->frame_number);
      }
      pending_request.zsl_buffer_received = true;
    } else {
      modified_output_buffers.push_back(result->output_buffers[i]);
    }
  }

  if (result->output_buffers.size() > 0) {
    result->output_buffers.clear();
    result->output_buffers = modified_output_buffers;
  }

  if (result->result_metadata) {
    result->result_metadata->Erase(ANDROID_CONTROL_ENABLE_ZSL);

    res = internal_stream_manager_->ReturnMetadata(
        stream_id_, result->frame_number, result->result_metadata.get(),
        result->partial_result);
    if (res != OK) {
      ALOGW("%s: (%d)ReturnMetadata fail", __FUNCTION__, result->frame_number);
    }

    if (result->partial_result == partial_result_count_) {
      res =
          hal_utils::SetEnableZslMetadata(result->result_metadata.get(), false);
      if (res != OK) {
        ALOGW("%s: SetEnableZslMetadata (%d) fail", __FUNCTION__,
              result->frame_number);
      }
    }
  }

  // Return directly for frames with errors.
  if (pending_error_frames_.find(result->frame_number) !=
      pending_error_frames_.end()) {
    RequestEntry& error_entry = pending_error_frames_[result->frame_number];
    return ReturnResultDirectlyForFramesWithErrorsLocked(
        error_entry, pending_request, std::move(result));
  }

  // Fill in final result metadata
  if (result->result_metadata != nullptr) {
    pending_request.partial_results_received++;
    if (result->partial_result < partial_result_count_) {
      // Early result, clone it
      pending_request.capture_request->settings =
          HalCameraMetadata::Clone(result->result_metadata.get());
    } else {
      // Final result, early result may or may not exist
      if (pending_request.capture_request->settings == nullptr) {
        // No early result, i.e. partial results disabled. Clone final result
        pending_request.capture_request->settings =
            HalCameraMetadata::Clone(result->result_metadata.get());
      } else {
        // Append early result to final result
        pending_request.capture_request->settings->Append(
            result->result_metadata->GetRawCameraMetadata());
      }
    }
  }

  // Fill in output buffers
  if (!result->output_buffers.empty()) {
    auto& output_buffers = pending_request.capture_request->output_buffers;
    output_buffers.insert(output_buffers.begin(), result->output_buffers.begin(),
                          result->output_buffers.end());
  }

  // Fill in input buffers
  if (!result->input_buffers.empty()) {
    auto& input_buffers = pending_request.capture_request->input_buffers;
    input_buffers.insert(input_buffers.begin(), result->input_buffers.begin(),
                         result->input_buffers.end());
  }

  // Submit the request and remove the request from the cache when all data is collected.
  if (AllDataCollected(pending_request)) {
    res = ProcessRequest(*pending_request.capture_request);
    pending_frame_number_to_requests_.erase(result->frame_number);
    if (res != OK) {
      ALOGE("%s: ProcessRequest fail", __FUNCTION__);
      return;
    }
  }
}

status_t RealtimeZslResultRequestProcessor::ConfigureStreams(
    InternalStreamManager* /*internal_stream_manager*/,
    const StreamConfiguration& stream_config,
    StreamConfiguration* process_block_stream_config) {
  ATRACE_CALL();
  if (process_block_stream_config == nullptr) {
    ALOGE("%s: process_block_stream_config is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  process_block_stream_config->streams = stream_config.streams;
  process_block_stream_config->operation_mode = stream_config.operation_mode;
  process_block_stream_config->session_params =
      HalCameraMetadata::Clone(stream_config.session_params.get());
  process_block_stream_config->stream_config_counter =
      stream_config.stream_config_counter;
  process_block_stream_config->multi_resolution_input_image =
      stream_config.multi_resolution_input_image;
  process_block_stream_config->log_id = stream_config.log_id;

  return OK;
}

status_t RealtimeZslResultRequestProcessor::SetProcessBlock(
    std::unique_ptr<ProcessBlock> process_block) {
  ATRACE_CALL();
  if (process_block == nullptr) {
    ALOGE("%s: process_block is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  std::lock_guard lock(process_block_shared_lock_);
  if (process_block_ != nullptr) {
    ALOGE("%s: Already configured.", __FUNCTION__);
    return ALREADY_EXISTS;
  }

  process_block_ = std::move(process_block);
  return OK;
}

status_t RealtimeZslResultRequestProcessor::ProcessRequest(
    const CaptureRequest& request) {
  ATRACE_CALL();
  std::shared_lock lock(process_block_shared_lock_);
  if (process_block_ == nullptr) {
    ALOGE("%s: Not configured yet.", __FUNCTION__);
    return NO_INIT;
  }

  CaptureRequest block_request;
  block_request.frame_number = request.frame_number;
  block_request.settings = HalCameraMetadata::Clone(request.settings.get());
  block_request.input_buffers = request.input_buffers;
  block_request.input_width = request.input_width;
  block_request.input_height = request.input_height;

  for (auto& metadata : request.input_buffer_metadata) {
    block_request.input_buffer_metadata.push_back(
        HalCameraMetadata::Clone(metadata.get()));
  }

  block_request.output_buffers = request.output_buffers;
  for (auto& [camera_id, physical_metadata] : request.physical_camera_settings) {
    block_request.physical_camera_settings[camera_id] =
        HalCameraMetadata::Clone(physical_metadata.get());
  }

  std::vector<ProcessBlockRequest> block_requests(1);
  block_requests[0].request = std::move(block_request);

  return process_block_->ProcessRequests(block_requests, request);
}

status_t RealtimeZslResultRequestProcessor::Flush() {
  ATRACE_CALL();
  std::shared_lock lock(process_block_shared_lock_);
  if (process_block_ == nullptr) {
    return OK;
  }

  return process_block_->Flush();
}

void RealtimeZslResultRequestProcessor::Notify(
    const ProcessBlockNotifyMessage& block_message) {
  ATRACE_CALL();
  std::lock_guard<std::mutex> lock(callback_lock_);
  const NotifyMessage& message = block_message.message;
  if (notify_ == nullptr) {
    ALOGE("%s: notify_ is nullptr. Dropping a message.", __FUNCTION__);
    return;
  }

  // Will return buffer for kErrorRequest and kErrorBuffer.
  if (message.type == MessageType::kError) {
    // May change to ALOGD for per-frame error messages.
    ALOGV("%s: Received error message at frame: %d, error code (%d)",
          __FUNCTION__, message.message.error.frame_number,
          static_cast<int>(message.message.error.error_code));
    if (message.message.error.error_code == ErrorCode::kErrorRequest ||
        message.message.error.error_code == ErrorCode::kErrorBuffer) {
      pending_error_frames_.try_emplace(
          message.message.error.frame_number,
          RequestEntry{.capture_request = std::make_unique<CaptureRequest>()});
      if (message.message.error.error_code == ErrorCode::kErrorRequest) {
        // ProcessCaptureResult is not called in the case of metadata error.
        // Therefore, treat it as if a metadata callback arrived so that we can
        // know when the request is complete.
        pending_error_frames_[message.message.error.frame_number]
            .partial_results_received++;
      }
    }
    // Gives latched results (those that have arrived but are waiting for
    // AllDataCollected()) a chance to return their valid buffer.
    uint32_t frame_number = message.message.error.frame_number;
    auto result = std::make_unique<CaptureResult>();
    result->frame_number = frame_number;
    if (pending_frame_number_to_requests_.find(frame_number) !=
        pending_frame_number_to_requests_.end()) {
      RequestEntry& pending_request =
          pending_frame_number_to_requests_[frame_number];
      if (pending_request.zsl_buffer_received) {
        ReturnResultDirectlyForFramesWithErrorsLocked(
            pending_error_frames_[frame_number], pending_request,
            std::move(result));
      }
    }
  } else {
    // May change to ALOGD for per-frame shutter messages.
    ALOGV("%s: Received shutter message for frame %d, timestamp_ns: %" PRId64
          ", readout_timestamp_ns: %" PRId64,
          __FUNCTION__, message.message.shutter.frame_number,
          message.message.shutter.timestamp_ns,
          message.message.shutter.readout_timestamp_ns);
  }

  // Do not notify errors for internal streams
  if (message.type == MessageType::kError &&
      message.message.error.error_stream_id == stream_id_) {
    return;
  }

  notify_(message);
}

void RealtimeZslResultRequestProcessor::CombineErrorAndPendingEntriesToResult(
    RequestEntry& error_entry, RequestEntry& pending_request,
    std::unique_ptr<CaptureResult>& result) const {
  result->output_buffers.insert(
      result->output_buffers.end(),
      pending_request.capture_request->output_buffers.begin(),
      pending_request.capture_request->output_buffers.end());
  result->input_buffers.insert(
      result->input_buffers.end(),
      pending_request.capture_request->input_buffers.begin(),
      pending_request.capture_request->input_buffers.end());
  error_entry.capture_request->output_buffers = result->output_buffers;
  error_entry.capture_request->input_buffers = result->input_buffers;
  error_entry.zsl_buffer_received = pending_request.zsl_buffer_received;
  error_entry.framework_buffer_count = pending_request.framework_buffer_count;
  if (pending_request.capture_request->settings != nullptr) {
    if (result->result_metadata == nullptr) {
      // result is a buffer-only result and we have early metadata sitting in
      // pending_request. Copy this early metadata and its partial_result count.
      result->result_metadata = HalCameraMetadata::Clone(
          pending_request.capture_request->settings.get());
      result->partial_result = pending_request.partial_results_received;
    } else {
      // result carries final metadata and we have early metadata sitting in
      // pending_request. Append the early metadata but keep the
      // partial_result count to reflect that this is the final metadata.
      result->result_metadata->Append(
          pending_request.capture_request->settings->GetRawCameraMetadata());
    }
    error_entry.partial_results_received += result->partial_result;
  }

  // Reset capture request for pending request as all data has been
  // transferred to error_entry already.
  pending_request.capture_request = std::make_unique<CaptureRequest>();
  pending_request.capture_request->frame_number = result->frame_number;
}

void RealtimeZslResultRequestProcessor::ReturnResultDirectlyForFramesWithErrorsLocked(
    RequestEntry& error_entry, RequestEntry& pending_request,
    std::unique_ptr<CaptureResult> result) {
  // Also need to process pending buffers and metadata for the frame if exists.
  // If the result is complete (buffers and all partial results arrived), send
  // the callback directly. Otherwise wait until the missing pieces arrive.
  CombineErrorAndPendingEntriesToResult(error_entry, pending_request, result);

  const bool has_returned_output_to_internal_stream_manager =
      pending_request.has_returned_output_to_internal_stream_manager;

  if (AllDataCollected(error_entry)) {
    pending_error_frames_.erase(result->frame_number);
    pending_frame_number_to_requests_.erase(result->frame_number);
  }

  // Don't send result to framework if only internal raw callback
  if (has_returned_output_to_internal_stream_manager &&
      result->result_metadata == nullptr && result->output_buffers.size() == 0) {
    return;
  }
  ALOGV("%s: Returning capture result for frame %d due to existing errors.",
        __FUNCTION__, result->frame_number);
  process_capture_result_(std::move(result));
  return;
}

}  // namespace google_camera_hal
}  // namespace android
