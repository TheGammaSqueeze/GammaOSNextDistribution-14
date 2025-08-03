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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_REALTIME_ZSL_RESULT_REQUEST_PROCESSOR_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_REALTIME_ZSL_RESULT_REQUEST_PROCESSOR_H_

#include <cstdint>
#include <shared_mutex>

#include "hal_types.h"
#include "internal_stream_manager.h"
#include "realtime_zsl_result_processor.h"
#include "request_processor.h"
#include "result_processor.h"

namespace android {
namespace google_camera_hal {

// RealtimeZslResultRequestProcessor implements a RealtimeZslResultProcessor
// that return filled raw buffer and metadata to internal stream manager. It
// also implements a RequestProcess to forward the results.
class RealtimeZslResultRequestProcessor : public RealtimeZslResultProcessor,
                                          RequestProcessor {
 public:
  static std::unique_ptr<RealtimeZslResultRequestProcessor> Create(
      InternalStreamManager* internal_stream_manager, int32_t stream_id,
      android_pixel_format_t pixel_format, uint32_t partial_result_count = 1);

  virtual ~RealtimeZslResultRequestProcessor() = default;

  // Override functions of RealtimeZslResultProcessor start.
  void ProcessResult(ProcessBlockResult block_result) override;

  void Notify(const ProcessBlockNotifyMessage& block_message) override;
  // Override functions of RealtimeZslResultProcessor end.

  // Override functions of RequestProcessor start.
  status_t ConfigureStreams(
      InternalStreamManager* internal_stream_manager,
      const StreamConfiguration& stream_config,
      StreamConfiguration* process_block_stream_config) override;

  status_t SetProcessBlock(std::unique_ptr<ProcessBlock> process_block) override;

  status_t ProcessRequest(const CaptureRequest& request) override;

  status_t Flush() override;
  // Override functions of RequestProcessor end.

  void UpdateOutputBufferCount(int32_t frame_number, int output_buffer_count,
                               bool is_preview_intent);

 protected:
  RealtimeZslResultRequestProcessor(
      InternalStreamManager* internal_stream_manager, int32_t stream_id,
      android_pixel_format_t pixel_format, uint32_t partial_result_count);

 private:
  std::shared_mutex process_block_shared_lock_;

  // Protected by process_block_shared_lock_.
  std::unique_ptr<ProcessBlock> process_block_;

  // Simple wrapper struct to add partial result count to CaptureResult
  struct RequestEntry {
    std::unique_ptr<CaptureRequest> capture_request = nullptr;
    uint32_t partial_results_received = 0;
    bool zsl_buffer_received = false;
    int framework_buffer_count = INT_MAX;
    // Whether there were filled raw buffers that have been returned to internal
    // stream manager.
    bool has_returned_output_to_internal_stream_manager = false;
  };

  bool AllDataCollected(const RequestEntry& request_entry) const;

  // A helper function to combine information for the same frame number from
  // `pending_error_frames_` and `pending_frame_number_to_requests_` to the
  // `result`. This is a 3-way update, where `pending_request` info is copied to
  // `error_entry` and `result`, and `pending_request` info gets reset.
  void CombineErrorAndPendingEntriesToResult(
      RequestEntry& error_entry, RequestEntry& pending_request,
      std::unique_ptr<CaptureResult>& result) const;

  // Returns result directly for frames with errors, if applicable. Call site
  // must hold callback_lock_.
  void ReturnResultDirectlyForFramesWithErrorsLocked(
      RequestEntry& error_entry, RequestEntry& pending_request,
      std::unique_ptr<CaptureResult> result);

  // Results collected so far on a valid frame. Results are passed to the
  // processor block once all items in the RequestEntry struct are complete -
  // i.e. all buffers arrived an all partial results arrived.
  std::unordered_map<uint32_t, RequestEntry> pending_frame_number_to_requests_;
  // Results collected so far on a frame with an error. Each result item gets
  // reported to the upper layer as it comes in, and once the RequestEntry
  // struct is complete the entry is removed.
  std::unordered_map<uint32_t, RequestEntry> pending_error_frames_;
};

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_REALTIME_ZSL_RESULT_REQUEST_PROCESSOR_H_
