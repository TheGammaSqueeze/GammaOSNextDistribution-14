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
enum class AutomotiveLensFacing : int32_t {
  ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_OTHER = 0,
  ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_FRONT = 1,
  ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_REAR = 2,
  ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_LEFT = 3,
  ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_RIGHT = 4,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_OTHER = 5,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_LEFT = 6,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_CENTER = 7,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_RIGHT = 8,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_LEFT = 9,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_CENTER = 10,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_RIGHT = 11,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_LEFT = 12,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_CENTER = 13,
  ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_RIGHT = 14,
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
[[nodiscard]] static inline std::string toString(AutomotiveLensFacing val) {
  switch(val) {
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_OTHER:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_OTHER";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_FRONT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_FRONT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_REAR:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_REAR";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_LEFT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_LEFT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_RIGHT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_RIGHT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_OTHER:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_OTHER";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_LEFT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_LEFT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_CENTER:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_CENTER";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_RIGHT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_RIGHT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_LEFT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_LEFT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_CENTER:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_CENTER";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_RIGHT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_RIGHT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_LEFT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_LEFT";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_CENTER:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_CENTER";
  case AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_RIGHT:
    return "ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_RIGHT";
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
constexpr inline std::array<aidl::android::hardware::camera::metadata::AutomotiveLensFacing, 15> enum_values<aidl::android::hardware::camera::metadata::AutomotiveLensFacing> = {
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_OTHER,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_FRONT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_REAR,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_LEFT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_EXTERIOR_RIGHT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_OTHER,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_LEFT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_CENTER,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_RIGHT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_LEFT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_CENTER,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_RIGHT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_LEFT,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_CENTER,
  aidl::android::hardware::camera::metadata::AutomotiveLensFacing::ANDROID_AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_RIGHT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
