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
namespace network {
enum class PhoneRestrictedState : int32_t {
  NONE = 0,
  CS_EMERGENCY = 1,
  CS_NORMAL = 2,
  CS_ALL = 4,
  PS_ALL = 16,
};

}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
[[nodiscard]] static inline std::string toString(PhoneRestrictedState val) {
  switch(val) {
  case PhoneRestrictedState::NONE:
    return "NONE";
  case PhoneRestrictedState::CS_EMERGENCY:
    return "CS_EMERGENCY";
  case PhoneRestrictedState::CS_NORMAL:
    return "CS_NORMAL";
  case PhoneRestrictedState::CS_ALL:
    return "CS_ALL";
  case PhoneRestrictedState::PS_ALL:
    return "PS_ALL";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::network::PhoneRestrictedState, 5> enum_values<aidl::android::hardware::radio::network::PhoneRestrictedState> = {
  aidl::android::hardware::radio::network::PhoneRestrictedState::NONE,
  aidl::android::hardware::radio::network::PhoneRestrictedState::CS_EMERGENCY,
  aidl::android::hardware::radio::network::PhoneRestrictedState::CS_NORMAL,
  aidl::android::hardware::radio::network::PhoneRestrictedState::CS_ALL,
  aidl::android::hardware::radio::network::PhoneRestrictedState::PS_ALL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
