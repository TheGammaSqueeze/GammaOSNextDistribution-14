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
enum class GroupCipherMask : int32_t {
  WEP40 = 2,
  WEP104 = 4,
  TKIP = 8,
  CCMP = 16,
  GTK_NOT_USED = 16384,
  GCMP_256 = 256,
  SMS4 = 128,
  GCMP_128 = 64,
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
[[nodiscard]] static inline std::string toString(GroupCipherMask val) {
  switch(val) {
  case GroupCipherMask::WEP40:
    return "WEP40";
  case GroupCipherMask::WEP104:
    return "WEP104";
  case GroupCipherMask::TKIP:
    return "TKIP";
  case GroupCipherMask::CCMP:
    return "CCMP";
  case GroupCipherMask::GTK_NOT_USED:
    return "GTK_NOT_USED";
  case GroupCipherMask::GCMP_256:
    return "GCMP_256";
  case GroupCipherMask::SMS4:
    return "SMS4";
  case GroupCipherMask::GCMP_128:
    return "GCMP_128";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::GroupCipherMask, 8> enum_values<aidl::android::hardware::wifi::supplicant::GroupCipherMask> = {
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::WEP40,
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::WEP104,
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::TKIP,
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::CCMP,
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::GTK_NOT_USED,
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::GCMP_256,
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::SMS4,
  aidl::android::hardware::wifi::supplicant::GroupCipherMask::GCMP_128,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
