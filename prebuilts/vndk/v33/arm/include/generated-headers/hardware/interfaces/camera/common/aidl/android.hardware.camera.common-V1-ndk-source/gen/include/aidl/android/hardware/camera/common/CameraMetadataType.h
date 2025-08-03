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
enum class CameraMetadataType : int32_t {
  BYTE = 0,
  INT32 = 1,
  FLOAT = 2,
  INT64 = 3,
  DOUBLE = 4,
  RATIONAL = 5,
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
[[nodiscard]] static inline std::string toString(CameraMetadataType val) {
  switch(val) {
  case CameraMetadataType::BYTE:
    return "BYTE";
  case CameraMetadataType::INT32:
    return "INT32";
  case CameraMetadataType::FLOAT:
    return "FLOAT";
  case CameraMetadataType::INT64:
    return "INT64";
  case CameraMetadataType::DOUBLE:
    return "DOUBLE";
  case CameraMetadataType::RATIONAL:
    return "RATIONAL";
  default:
    return std::to_string(static_cast<int32_t>(val));
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
constexpr inline std::array<aidl::android::hardware::camera::common::CameraMetadataType, 6> enum_values<aidl::android::hardware::camera::common::CameraMetadataType> = {
  aidl::android::hardware::camera::common::CameraMetadataType::BYTE,
  aidl::android::hardware::camera::common::CameraMetadataType::INT32,
  aidl::android::hardware::camera::common::CameraMetadataType::FLOAT,
  aidl::android::hardware::camera::common::CameraMetadataType::INT64,
  aidl::android::hardware::camera::common::CameraMetadataType::DOUBLE,
  aidl::android::hardware::camera::common::CameraMetadataType::RATIONAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
