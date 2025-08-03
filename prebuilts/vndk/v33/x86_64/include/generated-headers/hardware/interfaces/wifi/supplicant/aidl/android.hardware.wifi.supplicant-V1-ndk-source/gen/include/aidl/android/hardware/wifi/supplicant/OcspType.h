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
enum class OcspType : int32_t {
  NONE = 0,
  REQUEST_CERT_STATUS = 1,
  REQUIRE_CERT_STATUS = 2,
  REQUIRE_ALL_CERTS_STATUS = 3,
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
[[nodiscard]] static inline std::string toString(OcspType val) {
  switch(val) {
  case OcspType::NONE:
    return "NONE";
  case OcspType::REQUEST_CERT_STATUS:
    return "REQUEST_CERT_STATUS";
  case OcspType::REQUIRE_CERT_STATUS:
    return "REQUIRE_CERT_STATUS";
  case OcspType::REQUIRE_ALL_CERTS_STATUS:
    return "REQUIRE_ALL_CERTS_STATUS";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::OcspType, 4> enum_values<aidl::android::hardware::wifi::supplicant::OcspType> = {
  aidl::android::hardware::wifi::supplicant::OcspType::NONE,
  aidl::android::hardware::wifi::supplicant::OcspType::REQUEST_CERT_STATUS,
  aidl::android::hardware::wifi::supplicant::OcspType::REQUIRE_CERT_STATUS,
  aidl::android::hardware::wifi::supplicant::OcspType::REQUIRE_ALL_CERTS_STATUS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
