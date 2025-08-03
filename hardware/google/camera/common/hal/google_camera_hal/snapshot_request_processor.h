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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_SNAPSHOT_REQUEST_PROCESSOR_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_SNAPSHOT_REQUEST_PROCESSOR_H_

#include "process_block.h"
#include "request_processor.h"

namespace android {
namespace google_camera_hal {

// SnapshotRequestProcessor implements a RequestProcessor that adds
// internal yuv stream as input stream to request and forwards the request to
// its ProcessBlock.
class SnapshotRequestProcessor : public RequestProcessor {
 public:
  // device_session_hwl is owned by the caller and must be valid during the
  // lifetime of this SnapshotRequestProcessor.
  static std::unique_ptr<SnapshotRequestProcessor> Create(
      CameraDeviceSessionHwl* device_session_hwl,
      HwlSessionCallback session_callback, int32_t yuv_stream_id);

  virtual ~SnapshotRequestProcessor() = default;

  // Override functions of RequestProcessor start.
  status_t ConfigureStreams(
      InternalStreamManager* internal_stream_manager,
      const StreamConfiguration& stream_config,
      StreamConfiguration* process_block_stream_config) override;

  status_t SetProcessBlock(std::unique_ptr<ProcessBlock> process_block) override;

  // Adds internal yuv stream as input stream to request and forwards the
  // request to its ProcessBlock.
  status_t ProcessRequest(const CaptureRequest& request) override;

  status_t Flush() override;
  // Override functions of RequestProcessor end.

 protected:
  explicit SnapshotRequestProcessor(HwlSessionCallback session_callback)
      : session_callback_(session_callback) {
  }

 private:
  status_t Initialize(CameraDeviceSessionHwl* device_session_hwl,
                      int32_t yuv_stream_id);
  bool IsReadyForNextRequest();

  static constexpr int kZslBufferSize = 3;
  std::mutex process_block_lock_;

  // Protected by process_block_lock_.
  std::unique_ptr<ProcessBlock> process_block_;

  InternalStreamManager* internal_stream_manager_ = nullptr;
  int32_t yuv_stream_id_ = -1;
  uint32_t active_array_width_ = 0;
  uint32_t active_array_height_ = 0;

  HwlSessionCallback session_callback_;
};

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_SNAPSHOT_REQUEST_PROCESSOR_H_
