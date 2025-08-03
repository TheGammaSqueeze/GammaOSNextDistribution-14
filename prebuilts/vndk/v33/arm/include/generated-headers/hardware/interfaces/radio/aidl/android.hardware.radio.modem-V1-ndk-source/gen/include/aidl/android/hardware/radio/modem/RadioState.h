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
namespace radio {
namespace modem {
enum class RadioState : int32_t {
  OFF = 0,
  UNAVAILABLE = 1,
  ON = 10,
};

}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
[[nodiscard]] static inline std::string toString(RadioState val) {
  switch(val) {
  case RadioState::OFF:
    return "OFF";
  case RadioState::UNAVAILABLE:
    return "UNAVAILABLE";
  case RadioState::ON:
    return "ON";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::modem::RadioState, 3> enum_values<aidl::android::hardware::radio::modem::RadioState> = {
  aidl::android::hardware::radio::modem::RadioState::OFF,
  aidl::android::hardware::radio::modem::RadioState::UNAVAILABLE,
  aidl::android::hardware::radio::modem::RadioState::ON,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
