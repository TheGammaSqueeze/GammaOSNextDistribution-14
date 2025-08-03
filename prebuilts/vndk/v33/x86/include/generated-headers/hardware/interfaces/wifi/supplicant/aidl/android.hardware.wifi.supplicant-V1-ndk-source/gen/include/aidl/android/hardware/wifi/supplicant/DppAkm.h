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
enum class DppAkm : int32_t {
  PSK = 0,
  PSK_SAE = 1,
  SAE = 2,
  DPP = 3,
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
[[nodiscard]] static inline std::string toString(DppAkm val) {
  switch(val) {
  case DppAkm::PSK:
    return "PSK";
  case DppAkm::PSK_SAE:
    return "PSK_SAE";
  case DppAkm::SAE:
    return "SAE";
  case DppAkm::DPP:
    return "DPP";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::DppAkm, 4> enum_values<aidl::android::hardware::wifi::supplicant::DppAkm> = {
  aidl::android::hardware::wifi::supplicant::DppAkm::PSK,
  aidl::android::hardware::wifi::supplicant::DppAkm::PSK_SAE,
  aidl::android::hardware::wifi::supplicant::DppAkm::SAE,
  aidl::android::hardware::wifi::supplicant::DppAkm::DPP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
