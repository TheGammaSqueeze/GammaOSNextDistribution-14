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
enum class AnqpInfoId : int32_t {
  VENUE_NAME = 258,
  ROAMING_CONSORTIUM = 261,
  IP_ADDR_TYPE_AVAILABILITY = 262,
  NAI_REALM = 263,
  ANQP_3GPP_CELLULAR_NETWORK = 264,
  DOMAIN_NAME = 268,
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
[[nodiscard]] static inline std::string toString(AnqpInfoId val) {
  switch(val) {
  case AnqpInfoId::VENUE_NAME:
    return "VENUE_NAME";
  case AnqpInfoId::ROAMING_CONSORTIUM:
    return "ROAMING_CONSORTIUM";
  case AnqpInfoId::IP_ADDR_TYPE_AVAILABILITY:
    return "IP_ADDR_TYPE_AVAILABILITY";
  case AnqpInfoId::NAI_REALM:
    return "NAI_REALM";
  case AnqpInfoId::ANQP_3GPP_CELLULAR_NETWORK:
    return "ANQP_3GPP_CELLULAR_NETWORK";
  case AnqpInfoId::DOMAIN_NAME:
    return "DOMAIN_NAME";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::AnqpInfoId, 6> enum_values<aidl::android::hardware::wifi::supplicant::AnqpInfoId> = {
  aidl::android::hardware::wifi::supplicant::AnqpInfoId::VENUE_NAME,
  aidl::android::hardware::wifi::supplicant::AnqpInfoId::ROAMING_CONSORTIUM,
  aidl::android::hardware::wifi::supplicant::AnqpInfoId::IP_ADDR_TYPE_AVAILABILITY,
  aidl::android::hardware::wifi::supplicant::AnqpInfoId::NAI_REALM,
  aidl::android::hardware::wifi::supplicant::AnqpInfoId::ANQP_3GPP_CELLULAR_NETWORK,
  aidl::android::hardware::wifi::supplicant::AnqpInfoId::DOMAIN_NAME,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
