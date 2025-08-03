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
enum class RegState : int32_t {
  NOT_REG_MT_NOT_SEARCHING_OP = 0,
  REG_HOME = 1,
  NOT_REG_MT_SEARCHING_OP = 2,
  REG_DENIED = 3,
  UNKNOWN = 4,
  REG_ROAMING = 5,
  NOT_REG_MT_NOT_SEARCHING_OP_EM = 10,
  NOT_REG_MT_SEARCHING_OP_EM = 12,
  REG_DENIED_EM = 13,
  UNKNOWN_EM = 14,
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
[[nodiscard]] static inline std::string toString(RegState val) {
  switch(val) {
  case RegState::NOT_REG_MT_NOT_SEARCHING_OP:
    return "NOT_REG_MT_NOT_SEARCHING_OP";
  case RegState::REG_HOME:
    return "REG_HOME";
  case RegState::NOT_REG_MT_SEARCHING_OP:
    return "NOT_REG_MT_SEARCHING_OP";
  case RegState::REG_DENIED:
    return "REG_DENIED";
  case RegState::UNKNOWN:
    return "UNKNOWN";
  case RegState::REG_ROAMING:
    return "REG_ROAMING";
  case RegState::NOT_REG_MT_NOT_SEARCHING_OP_EM:
    return "NOT_REG_MT_NOT_SEARCHING_OP_EM";
  case RegState::NOT_REG_MT_SEARCHING_OP_EM:
    return "NOT_REG_MT_SEARCHING_OP_EM";
  case RegState::REG_DENIED_EM:
    return "REG_DENIED_EM";
  case RegState::UNKNOWN_EM:
    return "UNKNOWN_EM";
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
constexpr inline std::array<aidl::android::hardware::radio::network::RegState, 10> enum_values<aidl::android::hardware::radio::network::RegState> = {
  aidl::android::hardware::radio::network::RegState::NOT_REG_MT_NOT_SEARCHING_OP,
  aidl::android::hardware::radio::network::RegState::REG_HOME,
  aidl::android::hardware::radio::network::RegState::NOT_REG_MT_SEARCHING_OP,
  aidl::android::hardware::radio::network::RegState::REG_DENIED,
  aidl::android::hardware::radio::network::RegState::UNKNOWN,
  aidl::android::hardware::radio::network::RegState::REG_ROAMING,
  aidl::android::hardware::radio::network::RegState::NOT_REG_MT_NOT_SEARCHING_OP_EM,
  aidl::android::hardware::radio::network::RegState::NOT_REG_MT_SEARCHING_OP_EM,
  aidl::android::hardware::radio::network::RegState::REG_DENIED_EM,
  aidl::android::hardware::radio::network::RegState::UNKNOWN_EM,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
