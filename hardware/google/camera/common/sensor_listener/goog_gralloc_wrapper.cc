/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "goog_gralloc_wrapper"

#include "goog_gralloc_wrapper.h"

#include <utils/Log.h>

namespace android {
namespace camera_sensor_listener {

using ::android::hardware::hidl_handle;
using ::android::hardware::graphics::allocator::V3_0::IAllocator;
using ::android::hardware::graphics::mapper::V3_0::BufferDescriptor;
using ::android::hardware::graphics::mapper::V3_0::Error;
using ::android::hardware::graphics::mapper::V3_0::IMapper;

GoogGrallocWrapper::GoogGrallocWrapper() {
  allocator_ = IAllocator::getService();
  if (allocator_ == nullptr) {
    ALOGE("%s %d Failed to get allocator service", __func__, __LINE__);
  }

  mapper_ = IMapper::getService();
  if (mapper_ == nullptr) {
    ALOGE("%s %d Failed to get mapper service", __func__, __LINE__);
  }
  if (mapper_->isRemote()) {
    ALOGE("%s %d Mapper is not in passthrough mode", __func__, __LINE__);
  }
}

GoogGrallocWrapper::~GoogGrallocWrapper() {
  for (auto buffer_handle : cloned_buffers_) {
    auto buffer = const_cast<native_handle_t*>(buffer_handle);
    native_handle_close(buffer);
    native_handle_delete(buffer);
  }
  cloned_buffers_.clear();

  for (auto buffer_handle : imported_buffers_) {
    auto buffer = const_cast<native_handle_t*>(buffer_handle);
    if (mapper_->freeBuffer(buffer) != Error::NONE) {
      ALOGE("%s %d Failed to free buffer %p", __func__, __LINE__, buffer);
    }
  }
  imported_buffers_.clear();
}

sp<IAllocator> GoogGrallocWrapper::GetAllocator() const {
  return allocator_;
}

std::string GoogGrallocWrapper::DumpDebugInfo() const {
  std::string debug_info;
  allocator_->dumpDebugInfo([&debug_info](const auto& tmp_debug_info) {
    debug_info = tmp_debug_info.c_str();
  });

  return debug_info;
}

const native_handle_t* GoogGrallocWrapper::CloneBuffer(
    const hidl_handle& raw_handle) {
  const native_handle_t* buffer_handle =
      native_handle_clone(raw_handle.getNativeHandle());

  if (buffer_handle) {
    cloned_buffers_.insert(buffer_handle);
  }
  return buffer_handle;
}

// When import is false, this simply calls IAllocator::allocate. When import
// is true, the returned buffers are also imported into the mapper.
// Either case, the returned buffers must be freed with freeBuffer.
std::vector<const native_handle_t*> GoogGrallocWrapper::Allocate(
    const BufferDescriptor& descriptor, uint32_t count, bool import,
    uint32_t* out_stride) {
  std::vector<const native_handle_t*> buffer_handles;
  buffer_handles.reserve(count);
  allocator_->allocate(
      descriptor, count,
      [&](const auto& tmp_error, const auto& tmp_stride,
          const auto& tmp_buffers) {
        if (tmp_error != Error::NONE) {
          ALOGE("%s %d Failed to allocate buffers", __func__, __LINE__);
        }
        if (count != tmp_buffers.size()) {
          ALOGE("%s %d Invalid buffer array", __func__, __LINE__);
        }

        for (uint32_t i = 0; i < count; i++) {
          if (import) {
            buffer_handles.push_back(ImportBuffer(tmp_buffers[i]));
          } else {
            buffer_handles.push_back(CloneBuffer(tmp_buffers[i]));
          }
        }

        if (out_stride) {
          *out_stride = tmp_stride;
        }
      });

  return buffer_handles;
}

const native_handle_t* GoogGrallocWrapper::AllocateOneBuffer(
    const IMapper::BufferDescriptorInfo& descriptor_info, bool import,
    uint32_t* out_stride) {
  BufferDescriptor descriptor = CreateDescriptor(descriptor_info);
  auto buffers = Allocate(descriptor, 1, import, out_stride);
  return buffers[0];
}

sp<IMapper> GoogGrallocWrapper::GetMapper() const {
  return mapper_;
}

BufferDescriptor GoogGrallocWrapper::CreateDescriptor(
    const IMapper::BufferDescriptorInfo& descriptor_info) {
  BufferDescriptor descriptor;
  mapper_->createDescriptor(
      descriptor_info,
      [&descriptor](const auto& tmp_error, const auto& tmp_descriptor) {
        if (tmp_error != Error::NONE) {
          ALOGE("Failed to create descriptor");
        }
        descriptor = tmp_descriptor;
      });

  return descriptor;
}

const native_handle_t* GoogGrallocWrapper::ImportBuffer(
    const hidl_handle& raw_handle) {
  const native_handle_t* buffer_handle = nullptr;
  mapper_->importBuffer(
      raw_handle, [&buffer_handle, &raw_handle](const auto& tmp_error,
                                                const auto& tmp_buffer) {
        if (tmp_error != Error::NONE) {
          ALOGE("%s %d Failed to import buffer %p", __func__, __LINE__,
                raw_handle.getNativeHandle());
        }
        buffer_handle = static_cast<const native_handle_t*>(tmp_buffer);
      });

  if (buffer_handle) {
    imported_buffers_.insert(buffer_handle);
  }

  return buffer_handle;
}

void GoogGrallocWrapper::FreeBuffer(const native_handle_t* buffer_handle) {
  auto buffer = const_cast<native_handle_t*>(buffer_handle);

  if (imported_buffers_.erase(buffer_handle)) {
    Error error = mapper_->freeBuffer(buffer);
    if (error != Error::NONE) {
      ALOGE("%s %d Failed to free %p", __func__, __LINE__, buffer);
    }
  } else {
    cloned_buffers_.erase(buffer_handle);
    native_handle_close(buffer);
    native_handle_delete(buffer);
  }
}

// We use fd instead of ::android::hardware::hidl_handle in these functions to
// pass fences in and out of the mapper.  The ownership of the fd is always
// transferred with each of these functions.
void* GoogGrallocWrapper::Lock(const native_handle_t* buffer_handle,
                               uint64_t cpu_usage,
                               const IMapper::Rect& access_region,
                               int acquire_fence) {
  auto buffer = const_cast<native_handle_t*>(buffer_handle);

  NATIVE_HANDLE_DECLARE_STORAGE(acquire_fence_storage, 1, 0);
  hidl_handle acquire_fence_handle;
  if (acquire_fence >= 0) {
    auto h = native_handle_init(acquire_fence_storage, 1, 0);
    h->data[0] = acquire_fence;
    acquire_fence_handle = h;
  }

  void* data = nullptr;
  mapper_->lock(buffer, cpu_usage, access_region, acquire_fence_handle,
                [&buffer, &data](const auto& tmp_error, const auto& tmp_data,
                                 const auto& /*bytesPerPixel*/,
                                 const auto& /*bytesPerStride*/) {
                  if (tmp_error != Error::NONE) {
                    ALOGE("Failed to lock buffer %p", buffer);
                  } else {
                    data = tmp_data;
                  }
                });

  if (acquire_fence >= 0) {
    close(acquire_fence);
  }

  return data;
}

int GoogGrallocWrapper::Unlock(const native_handle_t* buffer_handle) {
  auto buffer = const_cast<native_handle_t*>(buffer_handle);

  int release_fence = -1;
  mapper_->unlock(buffer,
                  [&buffer, &release_fence](const auto& tmp_error,
                                            const auto& tmp_release_fence) {
                    if (tmp_error != Error::NONE) {
                      ALOGE("Failed to unlock buffer %p", buffer);
                    }

                    auto fence_handle = tmp_release_fence.getNativeHandle();
                    if (fence_handle) {
                      if (fence_handle->numInts != 0) {
                        ALOGE("Invalid fence handle %p", fence_handle);
                      }
                      if (fence_handle->numFds == 1) {
                        release_fence = dup(fence_handle->data[0]);
                        if (release_fence < 0) {
                          ALOGE("Failed to dup fence fd");
                        }
                      } else {
                        if (fence_handle->numFds != 0) {
                          ALOGE("Invalid fence handle %p", fence_handle);
                        }
                      }
                    }
                  });

  return release_fence;
}

}  // namespace camera_sensor_listener
}  // namespace android
