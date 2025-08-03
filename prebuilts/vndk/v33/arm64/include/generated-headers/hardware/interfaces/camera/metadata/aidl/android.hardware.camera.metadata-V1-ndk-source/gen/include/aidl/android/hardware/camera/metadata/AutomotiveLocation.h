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
namespace metadata {
enum class AutomotiveLocation : int32_t {
  ANDROID_AUTOMOTIVE_LOCATION_INTERIOR = 0,
  ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_OTHER = 1,
  ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_FRONT = 2,
  ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_REAR = 3,
  ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_LEFT = 4,
  ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_RIGHT = 5,
  ANDROID_AUTOMOTIVE_LOCATION_EXTRA_OTHER = 6,
  ANDROID_AUTOMOTIVE_LOCATION_EXTRA_FRONT = 7,
  ANDROID_AUTOMOTIVE_LOCATION_EXTRA_REAR = 8,
  ANDROID_AUTOMOTIVE_LOCATION_EXTRA_LEFT = 9,
  ANDROID_AUTOMOTIVE_LOCATION_EXTRA_RIGHT = 10,
};

}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace metadata {
[[nodiscard]] static inline std::string toString(AutomotiveLocation val) {
  switch(val) {
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_INTERIOR:
    return "ANDROID_AUTOMOTIVE_LOCATION_INTERIOR";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_OTHER:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_OTHER";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_FRONT:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_FRONT";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_REAR:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_REAR";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_LEFT:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_LEFT";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_RIGHT:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_RIGHT";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_OTHER:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTRA_OTHER";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_FRONT:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTRA_FRONT";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_REAR:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTRA_REAR";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_LEFT:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTRA_LEFT";
  case AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_RIGHT:
    return "ANDROID_AUTOMOTIVE_LOCATION_EXTRA_RIGHT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace metadata
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::camera::metadata::AutomotiveLocation, 11> enum_values<aidl::android::hardware::camera::metadata::AutomotiveLocation> = {
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_INTERIOR,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_OTHER,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_FRONT,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_REAR,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_LEFT,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTERIOR_RIGHT,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_OTHER,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_FRONT,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_REAR,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_LEFT,
  aidl::android::hardware::camera::metadata::AutomotiveLocation::ANDROID_AUTOMOTIVE_LOCATION_EXTRA_RIGHT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
