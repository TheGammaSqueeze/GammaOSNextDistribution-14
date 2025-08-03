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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_UTILS_GRALLOC_BUFFER_ALLOCATOR_H
#define HARDWARE_GOOGLE_CAMERA_HAL_UTILS_GRALLOC_BUFFER_ALLOCATOR_H

#include "hal_buffer_allocator.h"

#include <hardware/gralloc1.h>
#include <utils/Errors.h>
#include <vector>

namespace android {
namespace google_camera_hal {

// Gralloc buffer data structure
struct BufferDescriptor {
  uint32_t width = 0;
  uint32_t height = 0;
  int32_t format = -1;
  uint64_t producer_flags = 0;
  uint64_t consumer_flags = 0;
  uint32_t num_buffers = 0;
};

class GrallocBufferAllocator : IHalBufferAllocator {
 public:
  // Creates GrallocBuffer and allocate buffers
  static std::unique_ptr<IHalBufferAllocator> Create();

  virtual ~GrallocBufferAllocator() = default;

  // Allocate buffers and return buffer via buffers.
  // The buffers is owned by caller
  status_t AllocateBuffers(const HalBufferDescriptor& buffer_descriptor,
                           std::vector<buffer_handle_t>* buffers);

  void FreeBuffers(std::vector<buffer_handle_t>* buffers);

 protected:
  GrallocBufferAllocator() = default;

 private:

  // Do not support the copy constructor or assignment operator
  GrallocBufferAllocator(const GrallocBufferAllocator&) = delete;
  GrallocBufferAllocator& operator=(const GrallocBufferAllocator&) = delete;

  // Convert HAL buffer descriptor to gralloc descriptor
  void ConvertHalBufferDescriptor(
      const HalBufferDescriptor& hal_buffer_descriptor,
      BufferDescriptor* gralloc_buffer_descriptor);
};

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_UTILS_GRALLOC_BUFFER_ALLOCATOR_H
