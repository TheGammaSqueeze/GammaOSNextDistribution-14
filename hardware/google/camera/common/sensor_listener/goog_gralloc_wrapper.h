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

#ifndef VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_GRALLOC_WRAPPER_H_
#define VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_GRALLOC_WRAPPER_H_

#include <unordered_set>

#include <android/frameworks/sensorservice/1.0/ISensorManager.h>
#include <android/hardware/graphics/allocator/3.0/IAllocator.h>
#include <android/hardware/graphics/mapper/3.0/IMapper.h>
#include <sys/mman.h>
#include <utils/StrongPointer.h>

namespace android {
namespace camera_sensor_listener {

// GoogGrallocWrapper is a wrapper class to
// ::android::hardware::graphics::allocator::V3_0::IAllocator and
// ::android::hardware::graphics::mapper::V3_0::IMapper.
// It can used by direct channel based sensor listener class.
// Modified from //hardware/interfaces/graphics/mapper/2.0/utils/vts
// /include/mapper-vts/2.0/MapperVts.h.
class GoogGrallocWrapper {
 public:
  // Constructor.
  GoogGrallocWrapper();

  // Destructor.
  ~GoogGrallocWrapper();

  // Get StrongPointer to IAllocator.
  sp<::android::hardware::graphics::allocator::V3_0::IAllocator> GetAllocator()
      const;

  // Wrapper of IAllocator dumpDebugInfo method.
  std::string DumpDebugInfo() const;

  // Wrapper of IAllocator allocate method.
  std::vector<const native_handle_t*> Allocate(
      const ::android::hardware::graphics::mapper::V3_0::BufferDescriptor&
          descriptor,
      uint32_t count, bool import = true, uint32_t* out_stride = nullptr);

  // Special case of Allocate, where allocated buffer count is 1.
  const native_handle_t* AllocateOneBuffer(
      const ::android::hardware::graphics::mapper::V3_0::IMapper::
          BufferDescriptorInfo& descriptor_info,
      bool import = true, uint32_t* out_stride = nullptr);

  // Get StrongPointer to IMapper.
  sp<::android::hardware::graphics::mapper::V3_0::IMapper> GetMapper() const;

  // Wrapper of IMapper createDescriptor method.
  ::android::hardware::graphics::mapper::V3_0::BufferDescriptor CreateDescriptor(
      const ::android::hardware::graphics::mapper::V3_0::IMapper::
          BufferDescriptorInfo& descriptor_info);

  // Wrapper of IMapper importBuffer method.
  const native_handle_t* ImportBuffer(
      const ::android::hardware::hidl_handle& raw_handle);

  // Wrapper of IMapper freeBuffer method.
  void FreeBuffer(const native_handle_t* buffer_handle);

  // Wrapper of Imapper lock method.
  // We use fd instead of hardware::hidl_handle in these functions to pass
  // fences in and out of the mapper.  The ownership of the fd is always
  // transferred with each of these functions.
  void* Lock(const native_handle_t* buffer_handle, uint64_t cpu_usage,
             const ::android::hardware::graphics::mapper::V3_0::IMapper::Rect&
                 access_region,
             int acquire_fence);

  // Wrapper of Imapper unlock method.
  int Unlock(const native_handle_t* buffer_handle);

 private:
  const native_handle_t* CloneBuffer(const hardware::hidl_handle& raw_handle);

  // StrongPointer to IAllocator.
  sp<::android::hardware::graphics::allocator::V3_0::IAllocator> allocator_;

  // StrongPointer to IMapper.
  sp<::android::hardware::graphics::mapper::V3_0::IMapper> mapper_;

  // Set of cloned buffer handles.
  // Keep track of all cloned and imported handles.  When a test fails with
  // ASSERT_*, the destructor will free the handles for the test.
  std::unordered_set<const native_handle_t*> cloned_buffers_;

  // Set of imported buffer handles.
  std::unordered_set<const native_handle_t*> imported_buffers_;
};

}  // namespace camera_sensor_listener
}  // namespace android

#endif  // VENDOR_GOOGLE_CAMERA_SENSOR_LISTENER_GOOG_GRALLOC_WRAPPER_H_
