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
enum class DppCurve : int32_t {
  PRIME256V1 = 0,
  SECP384R1 = 1,
  SECP521R1 = 2,
  BRAINPOOLP256R1 = 3,
  BRAINPOOLP384R1 = 4,
  BRAINPOOLP512R1 = 5,
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
[[nodiscard]] static inline std::string toString(DppCurve val) {
  switch(val) {
  case DppCurve::PRIME256V1:
    return "PRIME256V1";
  case DppCurve::SECP384R1:
    return "SECP384R1";
  case DppCurve::SECP521R1:
    return "SECP521R1";
  case DppCurve::BRAINPOOLP256R1:
    return "BRAINPOOLP256R1";
  case DppCurve::BRAINPOOLP384R1:
    return "BRAINPOOLP384R1";
  case DppCurve::BRAINPOOLP512R1:
    return "BRAINPOOLP512R1";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::DppCurve, 6> enum_values<aidl::android::hardware::wifi::supplicant::DppCurve> = {
  aidl::android::hardware::wifi::supplicant::DppCurve::PRIME256V1,
  aidl::android::hardware::wifi::supplicant::DppCurve::SECP384R1,
  aidl::android::hardware::wifi::supplicant::DppCurve::SECP521R1,
  aidl::android::hardware::wifi::supplicant::DppCurve::BRAINPOOLP256R1,
  aidl::android::hardware::wifi::supplicant::DppCurve::BRAINPOOLP384R1,
  aidl::android::hardware::wifi::supplicant::DppCurve::BRAINPOOLP512R1,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
