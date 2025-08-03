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
namespace device {
enum class TemplateId : int32_t {
  PREVIEW = 1,
  STILL_CAPTURE = 2,
  RECORD = 3,
  VIDEO_SNAPSHOT = 4,
  ZERO_SHUTTER_LAG = 5,
  MANUAL = 6,
};

}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
[[nodiscard]] static inline std::string toString(TemplateId val) {
  switch(val) {
  case TemplateId::PREVIEW:
    return "PREVIEW";
  case TemplateId::STILL_CAPTURE:
    return "STILL_CAPTURE";
  case TemplateId::RECORD:
    return "RECORD";
  case TemplateId::VIDEO_SNAPSHOT:
    return "VIDEO_SNAPSHOT";
  case TemplateId::ZERO_SHUTTER_LAG:
    return "ZERO_SHUTTER_LAG";
  case TemplateId::MANUAL:
    return "MANUAL";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::device::TemplateId, 6> enum_values<aidl::android::frameworks::cameraservice::device::TemplateId> = {
  aidl::android::frameworks::cameraservice::device::TemplateId::PREVIEW,
  aidl::android::frameworks::cameraservice::device::TemplateId::STILL_CAPTURE,
  aidl::android::frameworks::cameraservice::device::TemplateId::RECORD,
  aidl::android::frameworks::cameraservice::device::TemplateId::VIDEO_SNAPSHOT,
  aidl::android::frameworks::cameraservice::device::TemplateId::ZERO_SHUTTER_LAG,
  aidl::android::frameworks::cameraservice::device::TemplateId::MANUAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
