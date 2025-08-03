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

#include "GrallocSensorBuffer.h"

namespace android {

GrallocSensorBuffer::~GrallocSensorBuffer() {
  if (stream_buffer.buffer != nullptr) {
    importer_->unlock(stream_buffer.buffer);
  }

  if (acquire_fence_fd >= 0) {
    importer_->closeFence(acquire_fence_fd);
  }

  if ((stream_buffer.status != BufferStatus::kOk) &&
      (callback.notify != nullptr) && (!is_failed_request)) {
    NotifyMessage msg = {
        .type = MessageType::kError,
        .message.error = {.frame_number = frame_number,
                          .error_stream_id = stream_buffer.stream_id,
                          .error_code = ErrorCode::kErrorBuffer}};
    callback.notify(pipeline_id, msg);
  }

  if (callback.process_pipeline_result != nullptr) {
    auto result = std::make_unique<HwlPipelineResult>();
    result->camera_id = camera_id;
    result->pipeline_id = pipeline_id;
    result->frame_number = frame_number;
    result->partial_result = 0;

    stream_buffer.acquire_fence = stream_buffer.release_fence = nullptr;
    if (is_input) {
      result->input_buffers.push_back(stream_buffer);
    } else {
      result->output_buffers.push_back(stream_buffer);
    }
    callback.process_pipeline_result(std::move(result));
  }
}

}  // namespace android
