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
enum class ContaminantProtectionStatus : int8_t {
  NONE = 0,
  FORCE_SINK = 1,
  FORCE_SOURCE = 2,
  FORCE_DISABLE = 3,
  DISABLED = 4,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(ContaminantProtectionStatus val) {
  switch(val) {
  case ContaminantProtectionStatus::NONE:
    return "NONE";
  case ContaminantProtectionStatus::FORCE_SINK:
    return "FORCE_SINK";
  case ContaminantProtectionStatus::FORCE_SOURCE:
    return "FORCE_SOURCE";
  case ContaminantProtectionStatus::FORCE_DISABLE:
    return "FORCE_DISABLE";
  case ContaminantProtectionStatus::DISABLED:
    return "DISABLED";
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
constexpr inline std::array<aidl::android::hardware::usb::ContaminantProtectionStatus, 5> enum_values<aidl::android::hardware::usb::ContaminantProtectionStatus> = {
  aidl::android::hardware::usb::ContaminantProtectionStatus::NONE,
  aidl::android::hardware::usb::ContaminantProtectionStatus::FORCE_SINK,
  aidl::android::hardware::usb::ContaminantProtectionStatus::FORCE_SOURCE,
  aidl::android::hardware::usb::ContaminantProtectionStatus::FORCE_DISABLE,
  aidl::android::hardware::usb::ContaminantProtectionStatus::DISABLED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
