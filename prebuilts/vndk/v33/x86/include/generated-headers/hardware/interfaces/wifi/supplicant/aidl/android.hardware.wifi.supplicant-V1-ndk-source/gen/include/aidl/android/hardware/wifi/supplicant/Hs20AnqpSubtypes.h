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
namespace wifi {
namespace supplicant {
enum class Hs20AnqpSubtypes : int32_t {
  OPERATOR_FRIENDLY_NAME = 3,
  WAN_METRICS = 4,
  CONNECTION_CAPABILITY = 5,
  OSU_PROVIDERS_LIST = 8,
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
[[nodiscard]] static inline std::string toString(Hs20AnqpSubtypes val) {
  switch(val) {
  case Hs20AnqpSubtypes::OPERATOR_FRIENDLY_NAME:
    return "OPERATOR_FRIENDLY_NAME";
  case Hs20AnqpSubtypes::WAN_METRICS:
    return "WAN_METRICS";
  case Hs20AnqpSubtypes::CONNECTION_CAPABILITY:
    return "CONNECTION_CAPABILITY";
  case Hs20AnqpSubtypes::OSU_PROVIDERS_LIST:
    return "OSU_PROVIDERS_LIST";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes, 4> enum_values<aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes> = {
  aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes::OPERATOR_FRIENDLY_NAME,
  aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes::WAN_METRICS,
  aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes::CONNECTION_CAPABILITY,
  aidl::android::hardware::wifi::supplicant::Hs20AnqpSubtypes::OSU_PROVIDERS_LIST,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
