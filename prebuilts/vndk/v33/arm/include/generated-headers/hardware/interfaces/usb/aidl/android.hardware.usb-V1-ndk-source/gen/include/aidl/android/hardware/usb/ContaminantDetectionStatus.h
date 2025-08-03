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
namespace usb {
enum class ContaminantDetectionStatus : int8_t {
  NOT_SUPPORTED = 0,
  DISABLED = 1,
  NOT_DETECTED = 2,
  DETECTED = 3,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(ContaminantDetectionStatus val) {
  switch(val) {
  case ContaminantDetectionStatus::NOT_SUPPORTED:
    return "NOT_SUPPORTED";
  case ContaminantDetectionStatus::DISABLED:
    return "DISABLED";
  case ContaminantDetectionStatus::NOT_DETECTED:
    return "NOT_DETECTED";
  case ContaminantDetectionStatus::DETECTED:
    return "DETECTED";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::usb::ContaminantDetectionStatus, 4> enum_values<aidl::android::hardware::usb::ContaminantDetectionStatus> = {
  aidl::android::hardware::usb::ContaminantDetectionStatus::NOT_SUPPORTED,
  aidl::android::hardware::usb::ContaminantDetectionStatus::DISABLED,
  aidl::android::hardware::usb::ContaminantDetectionStatus::NOT_DETECTED,
  aidl::android::hardware::usb::ContaminantDetectionStatus::DETECTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
