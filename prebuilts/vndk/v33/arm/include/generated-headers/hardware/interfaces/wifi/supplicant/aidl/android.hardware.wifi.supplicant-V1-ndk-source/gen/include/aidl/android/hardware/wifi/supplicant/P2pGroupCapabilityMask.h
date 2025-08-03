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
enum class P2pGroupCapabilityMask : int32_t {
  GROUP_OWNER = 1,
  PERSISTENT_GROUP = 2,
  GROUP_LIMIT = 4,
  INTRA_BSS_DIST = 8,
  CROSS_CONN = 16,
  PERSISTENT_RECONN = 32,
  GROUP_FORMATION = 64,
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
[[nodiscard]] static inline std::string toString(P2pGroupCapabilityMask val) {
  switch(val) {
  case P2pGroupCapabilityMask::GROUP_OWNER:
    return "GROUP_OWNER";
  case P2pGroupCapabilityMask::PERSISTENT_GROUP:
    return "PERSISTENT_GROUP";
  case P2pGroupCapabilityMask::GROUP_LIMIT:
    return "GROUP_LIMIT";
  case P2pGroupCapabilityMask::INTRA_BSS_DIST:
    return "INTRA_BSS_DIST";
  case P2pGroupCapabilityMask::CROSS_CONN:
    return "CROSS_CONN";
  case P2pGroupCapabilityMask::PERSISTENT_RECONN:
    return "PERSISTENT_RECONN";
  case P2pGroupCapabilityMask::GROUP_FORMATION:
    return "GROUP_FORMATION";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask, 7> enum_values<aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask> = {
  aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask::GROUP_OWNER,
  aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask::PERSISTENT_GROUP,
  aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask::GROUP_LIMIT,
  aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask::INTRA_BSS_DIST,
  aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask::CROSS_CONN,
  aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask::PERSISTENT_RECONN,
  aidl::android::hardware::wifi::supplicant::P2pGroupCapabilityMask::GROUP_FORMATION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
