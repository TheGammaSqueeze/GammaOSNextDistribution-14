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
namespace service {
enum class CameraDeviceStatus : int32_t {
  STATUS_NOT_AVAILABLE = -2,
  STATUS_UNKNOWN = -1,
  STATUS_NOT_PRESENT = 0,
  STATUS_PRESENT = 1,
  STATUS_ENUMERATING = 2,
};

}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
[[nodiscard]] static inline std::string toString(CameraDeviceStatus val) {
  switch(val) {
  case CameraDeviceStatus::STATUS_NOT_AVAILABLE:
    return "STATUS_NOT_AVAILABLE";
  case CameraDeviceStatus::STATUS_UNKNOWN:
    return "STATUS_UNKNOWN";
  case CameraDeviceStatus::STATUS_NOT_PRESENT:
    return "STATUS_NOT_PRESENT";
  case CameraDeviceStatus::STATUS_PRESENT:
    return "STATUS_PRESENT";
  case CameraDeviceStatus::STATUS_ENUMERATING:
    return "STATUS_ENUMERATING";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::service::CameraDeviceStatus, 5> enum_values<aidl::android::frameworks::cameraservice::service::CameraDeviceStatus> = {
  aidl::android::frameworks::cameraservice::service::CameraDeviceStatus::STATUS_NOT_AVAILABLE,
  aidl::android::frameworks::cameraservice::service::CameraDeviceStatus::STATUS_UNKNOWN,
  aidl::android::frameworks::cameraservice::service::CameraDeviceStatus::STATUS_NOT_PRESENT,
  aidl::android::frameworks::cameraservice::service::CameraDeviceStatus::STATUS_PRESENT,
  aidl::android::frameworks::cameraservice::service::CameraDeviceStatus::STATUS_ENUMERATING,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
