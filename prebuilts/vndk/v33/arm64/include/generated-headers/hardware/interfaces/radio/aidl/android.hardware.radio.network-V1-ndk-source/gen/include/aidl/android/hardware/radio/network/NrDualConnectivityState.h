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
enum class NrDualConnectivityState : int8_t {
  ENABLE = 1,
  DISABLE = 2,
  DISABLE_IMMEDIATE = 3,
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
[[nodiscard]] static inline std::string toString(NrDualConnectivityState val) {
  switch(val) {
  case NrDualConnectivityState::ENABLE:
    return "ENABLE";
  case NrDualConnectivityState::DISABLE:
    return "DISABLE";
  case NrDualConnectivityState::DISABLE_IMMEDIATE:
    return "DISABLE_IMMEDIATE";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::radio::network::NrDualConnectivityState, 3> enum_values<aidl::android::hardware::radio::network::NrDualConnectivityState> = {
  aidl::android::hardware::radio::network::NrDualConnectivityState::ENABLE,
  aidl::android::hardware::radio::network::NrDualConnectivityState::DISABLE,
  aidl::android::hardware::radio::network::NrDualConnectivityState::DISABLE_IMMEDIATE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
