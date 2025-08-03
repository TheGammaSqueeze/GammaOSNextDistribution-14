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
enum class CdmaRoamingType : int32_t {
  HOME_NETWORK = 0,
  AFFILIATED_ROAM = 1,
  ANY_ROAM = 2,
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
[[nodiscard]] static inline std::string toString(CdmaRoamingType val) {
  switch(val) {
  case CdmaRoamingType::HOME_NETWORK:
    return "HOME_NETWORK";
  case CdmaRoamingType::AFFILIATED_ROAM:
    return "AFFILIATED_ROAM";
  case CdmaRoamingType::ANY_ROAM:
    return "ANY_ROAM";
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
constexpr inline std::array<aidl::android::hardware::radio::network::CdmaRoamingType, 3> enum_values<aidl::android::hardware::radio::network::CdmaRoamingType> = {
  aidl::android::hardware::radio::network::CdmaRoamingType::HOME_NETWORK,
  aidl::android::hardware::radio::network::CdmaRoamingType::AFFILIATED_ROAM,
  aidl::android::hardware::radio::network::CdmaRoamingType::ANY_ROAM,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
