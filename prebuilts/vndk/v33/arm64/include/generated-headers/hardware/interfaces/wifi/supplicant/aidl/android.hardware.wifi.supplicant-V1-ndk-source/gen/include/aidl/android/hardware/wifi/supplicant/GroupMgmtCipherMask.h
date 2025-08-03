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
enum class GroupMgmtCipherMask : int32_t {
  BIP_GMAC_128 = 2048,
  BIP_GMAC_256 = 4096,
  BIP_CMAC_256 = 8192,
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
[[nodiscard]] static inline std::string toString(GroupMgmtCipherMask val) {
  switch(val) {
  case GroupMgmtCipherMask::BIP_GMAC_128:
    return "BIP_GMAC_128";
  case GroupMgmtCipherMask::BIP_GMAC_256:
    return "BIP_GMAC_256";
  case GroupMgmtCipherMask::BIP_CMAC_256:
    return "BIP_CMAC_256";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask, 3> enum_values<aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask> = {
  aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask::BIP_GMAC_128,
  aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask::BIP_GMAC_256,
  aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask::BIP_CMAC_256,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
