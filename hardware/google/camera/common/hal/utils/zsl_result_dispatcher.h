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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_UTILS_ZSL_RESULT_DISPATCHER_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_UTILS_ZSL_RESULT_DISPATCHER_H_

#include <map>
#include <thread>

#include "hal_types.h"
#include "result_dispatcher.h"

namespace android {
namespace google_camera_hal {

// ZslResultDispatcher dispatches capture results of zsl requests and none-zsl
// requests in the order of frame numbers, including result metadata, shutters,
// and stream buffers.
//
// The client can add results and shutters via AddResult() and AddShutter() in
// any order. ZslResultDispatcher will invoke ProcessCaptureResultFunc and
// NotifyFunc to notify result metadata, shutters, and stream buffers in the
// in the order of increasing frame numbers.
class ZslResultDispatcher {
 public:
  // Create a ZslResultDispatcher.
  // partial_result_count is the partial result count.
  // process_capture_result is the function to notify capture results.
  // notify is the function to notify shutter messages.
  // Treat ZSL requests and normal requests separately.
  // For ZSL requests, it returns zsl shutter and zsl results in order
  // and is not blocked by normal shutter and results.
  // stream_config is the session stream configuration.
  static std::unique_ptr<ZslResultDispatcher> Create(
      uint32_t partial_result_count,
      ProcessCaptureResultFunc process_capture_result, NotifyFunc notify,
      const StreamConfiguration& stream_config);

  virtual ~ZslResultDispatcher() = default;

  // Add a pending request. This tells ZslResultDispatcher to watch for
  // the shutter, result metadata, and stream buffers for this request,
  // that will be added later via AddResult() and AddShutter().
  // Treat the request as zsl request if is_zsl_request is true
  status_t AddPendingRequest(const CaptureRequest& pending_request,
                             bool is_zsl_request);

  // Add a ready result. If the result doesn't belong to a pending request that
  // was previously added via AddPendingRequest(), an error will be returned.
  status_t AddResult(std::unique_ptr<CaptureResult> result);

  // Add a shutter for a frame number. If the frame number doesn't belong to a
  // pending request that was previously added via AddPendingRequest(), an error
  // will be returned.
  status_t AddShutter(uint32_t frame_number, int64_t timestamp_ns,
                      int64_t readout_timestamp_ns);

  // Add an error notification for a frame number. When this is called, we no
  // longer wait for a shutter message or result metadata for the given frame.
  status_t AddError(const ErrorMessage& error);

  // Remove a pending request.
  void RemovePendingRequest(uint32_t frame_number);

 protected:
  ZslResultDispatcher(ProcessCaptureResultFunc process_capture_result,
                      NotifyFunc notify);

 private:
  status_t Initialize(uint32_t partial_result_count,
                      const StreamConfiguration& stream_config);

  // Invoked when receiving a result from ResultDispatcher class.
  void ProcessCaptureResult(std::unique_ptr<CaptureResult> result);

  // Invoked when receiving a message from ResultDispatcher.
  void NotifyHalMessage(const NotifyMessage& message);

  // Return true if this frame is zsl request.
  bool IsZslFrame(uint32_t frame_number);

  std::unique_ptr<ResultDispatcher> normal_result_dispatcher_;
  std::unique_ptr<ResultDispatcher> zsl_result_dispatcher_;

  std::mutex process_capture_result_lock_;
  // The following callbacks must be protected by process_capture_result_lock_.
  // Pass this callback function to ResultDispatcher class
  ProcessCaptureResultFunc process_capture_result_;

  std::mutex result_lock_;
  // The following callbacks must be protected by result_lock_.
  // Pass this callback function to ResultDispatcher class
  NotifyFunc notify_;

  // Record the callback function for framework callback
  ProcessCaptureResultFunc device_session_process_capture_result_;
  NotifyFunc device_session_notify_;

  std::mutex zsl_frames_lock_;
  // Store the frame number of zsl requests
  // Protected by zsl_frames_lock_.
  std::vector<uint32_t> zsl_frames_;
};

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_UTILS_ZSL_RESULT_DISPATCHER_H_
