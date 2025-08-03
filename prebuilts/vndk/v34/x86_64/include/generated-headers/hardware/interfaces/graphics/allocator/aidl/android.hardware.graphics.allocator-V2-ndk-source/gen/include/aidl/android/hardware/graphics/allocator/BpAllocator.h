#pragma once

#include "aidl/android/hardware/graphics/allocator/IAllocator.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace allocator {
class BpAllocator : public ::ndk::BpCInterface<IAllocator> {
public:
  explicit BpAllocator(const ::ndk::SpAIBinder& binder);
  virtual ~BpAllocator();

  ::ndk::ScopedAStatus allocate(const std::vector<uint8_t>& in_descriptor, int32_t in_count, ::aidl::android::hardware::graphics::allocator::AllocationResult* _aidl_return) override __attribute__((deprecated("As of android.hardware.graphics.allocator-V2 in combination with AIMAPPER_VERSION_5 this is deprecated & replaced with allocate2. If android.hardware.graphics.mapper@4 is still in use, however, this is still required to be implemented.")));
  ::ndk::ScopedAStatus allocate2(const ::aidl::android::hardware::graphics::allocator::BufferDescriptorInfo& in_descriptor, int32_t in_count, ::aidl::android::hardware::graphics::allocator::AllocationResult* _aidl_return) override;
  ::ndk::ScopedAStatus isSupported(const ::aidl::android::hardware::graphics::allocator::BufferDescriptorInfo& in_descriptor, bool* _aidl_return) override;
  ::ndk::ScopedAStatus getIMapperLibrarySuffix(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace allocator
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
