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
namespace camera {
namespace common {
enum class TagBoundaryId : int64_t {
  AOSP = 0L,
  VENDOR = 2147483648L,
};

}  // namespace common
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace common {
[[nodiscard]] static inline std::string toString(TagBoundaryId val) {
  switch(val) {
  case TagBoundaryId::AOSP:
    return "AOSP";
  case TagBoundaryId::VENDOR:
    return "VENDOR";
  default:
    return std::to_string(static_cast<int64_t>(val));
  }
}
}  // namespace common
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::common::TagBoundaryId, 2> enum_values<aidl::android::hardware::camera::common::TagBoundaryId> = {
  aidl::android::hardware::camera::common::TagBoundaryId::AOSP,
  aidl::android::hardware::camera::common::TagBoundaryId::VENDOR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
