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
#define LOG_TAG "GCH_GrallocBufferAllocator"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include <log/log.h>
#include <utils/Trace.h>

#include <grallocusage/GrallocUsageConversion.h>
#include <ui/GraphicBufferAllocator.h>

#include "gralloc_buffer_allocator.h"

namespace android {
namespace google_camera_hal {

std::unique_ptr<IHalBufferAllocator> GrallocBufferAllocator::Create() {
  ATRACE_CALL();
  auto gralloc_buffer_allocator =
      std::unique_ptr<GrallocBufferAllocator>(new GrallocBufferAllocator());
  if (gralloc_buffer_allocator == nullptr) {
    ALOGE("%s: Creating gralloc_buffer_allocator failed.", __FUNCTION__);
    return nullptr;
  }

  std::unique_ptr<IHalBufferAllocator> base_allocator;
  base_allocator.reset(gralloc_buffer_allocator.release());

  return base_allocator;
}

void GrallocBufferAllocator::ConvertHalBufferDescriptor(
    const HalBufferDescriptor& hal_buffer_descriptor,
    BufferDescriptor* gralloc_buffer_descriptor) {
  ATRACE_CALL();
  // For BLOB format, the gralloc buffer width should be the actual size, and
  // height should be 1.
  if (hal_buffer_descriptor.format == HAL_PIXEL_FORMAT_BLOB) {
    gralloc_buffer_descriptor->width =
        hal_buffer_descriptor.width * hal_buffer_descriptor.height;
    gralloc_buffer_descriptor->height = 1;
  } else {
    gralloc_buffer_descriptor->width = hal_buffer_descriptor.width;
    gralloc_buffer_descriptor->height = hal_buffer_descriptor.height;
  }
  gralloc_buffer_descriptor->format = hal_buffer_descriptor.format;
  gralloc_buffer_descriptor->producer_flags =
      hal_buffer_descriptor.producer_flags;
  gralloc_buffer_descriptor->consumer_flags =
      hal_buffer_descriptor.consumer_flags;
  gralloc_buffer_descriptor->num_buffers =
      hal_buffer_descriptor.immediate_num_buffers;
}

status_t GrallocBufferAllocator::AllocateBuffers(
    const HalBufferDescriptor& buffer_descriptor,
    std::vector<buffer_handle_t>* buffers) {
  ATRACE_CALL();

  BufferDescriptor gralloc_buffer_descriptor{0};
  ConvertHalBufferDescriptor(buffer_descriptor, &gralloc_buffer_descriptor);

  status_t err = OK;
  uint32_t stride = 0;
  for (uint32_t i = 0; i < gralloc_buffer_descriptor.num_buffers; i++) {
    buffer_handle_t buffer;
    err = GraphicBufferAllocator::get().allocate(
        gralloc_buffer_descriptor.width, gralloc_buffer_descriptor.height,
        gralloc_buffer_descriptor.format, /*layerCount*/ 1u,
        android_convertGralloc1To0Usage(
            gralloc_buffer_descriptor.producer_flags,
            gralloc_buffer_descriptor.consumer_flags),
        &buffer, &stride, "GCHGrallocBufferAllocator");
    if (err != OK) {
      ALOGE("%s: Failed to allocate gralloc buffer: %s(%d)", __FUNCTION__,
            strerror(-err), err);
      break;
    }
    buffers->push_back(buffer);
  }

  if (err != OK) {
    FreeBuffers(buffers);
    return INVALID_OPERATION;
  }

  return OK;
}

void GrallocBufferAllocator::FreeBuffers(std::vector<buffer_handle_t>* buffers) {
  ATRACE_CALL();
  for (auto buffer : *buffers) {
    if (buffer != nullptr) {
      GraphicBufferAllocator::get().free(buffer);
    }
  }
  buffers->clear();
}

}  // namespace google_camera_hal
}  // namespace android
