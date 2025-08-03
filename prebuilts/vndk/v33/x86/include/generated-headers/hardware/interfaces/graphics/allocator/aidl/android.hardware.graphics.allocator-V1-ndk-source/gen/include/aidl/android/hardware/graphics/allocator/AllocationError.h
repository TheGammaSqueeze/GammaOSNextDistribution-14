#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace allocator {
enum class AllocationError : int32_t {
  BAD_DESCRIPTOR = 0,
  NO_RESOURCES = 1,
  UNSUPPORTED = 2,
};

}  // namespace allocator
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace allocator {
[[nodiscard]] static inline std::string toString(AllocationError val) {
  switch(val) {
  case AllocationError::BAD_DESCRIPTOR:
    return "BAD_DESCRIPTOR";
  case AllocationError::NO_RESOURCES:
    return "NO_RESOURCES";
  case AllocationError::UNSUPPORTED:
    return "UNSUPPORTED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace allocator
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::allocator::AllocationError, 3> enum_values<aidl::android::hardware::graphics::allocator::AllocationError> = {
  aidl::android::hardware::graphics::allocator::AllocationError::BAD_DESCRIPTOR,
  aidl::android::hardware::graphics::allocator::AllocationError::NO_RESOURCES,
  aidl::android::hardware::graphics::allocator::AllocationError::UNSUPPORTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
