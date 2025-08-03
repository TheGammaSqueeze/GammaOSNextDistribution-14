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
namespace frameworks {
namespace cameraservice {
namespace common {
enum class TagBoundaryId : int64_t {
  AOSP = 0L,
  VENDOR = 2147483648L,
};

}  // namespace common
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
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
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::common::TagBoundaryId, 2> enum_values<aidl::android::frameworks::cameraservice::common::TagBoundaryId> = {
  aidl::android::frameworks::cameraservice::common::TagBoundaryId::AOSP,
  aidl::android::frameworks::cameraservice::common::TagBoundaryId::VENDOR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
