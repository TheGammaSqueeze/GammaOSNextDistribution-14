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
enum class PairwiseCipherMask : int32_t {
  NONE = 1,
  TKIP = 8,
  CCMP = 16,
  GCMP_128 = 64,
  SMS4 = 128,
  GCMP_256 = 256,
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
[[nodiscard]] static inline std::string toString(PairwiseCipherMask val) {
  switch(val) {
  case PairwiseCipherMask::NONE:
    return "NONE";
  case PairwiseCipherMask::TKIP:
    return "TKIP";
  case PairwiseCipherMask::CCMP:
    return "CCMP";
  case PairwiseCipherMask::GCMP_128:
    return "GCMP_128";
  case PairwiseCipherMask::SMS4:
    return "SMS4";
  case PairwiseCipherMask::GCMP_256:
    return "GCMP_256";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::PairwiseCipherMask, 6> enum_values<aidl::android::hardware::wifi::supplicant::PairwiseCipherMask> = {
  aidl::android::hardware::wifi::supplicant::PairwiseCipherMask::NONE,
  aidl::android::hardware::wifi::supplicant::PairwiseCipherMask::TKIP,
  aidl::android::hardware::wifi::supplicant::PairwiseCipherMask::CCMP,
  aidl::android::hardware::wifi::supplicant::PairwiseCipherMask::GCMP_128,
  aidl::android::hardware::wifi::supplicant::PairwiseCipherMask::SMS4,
  aidl::android::hardware::wifi::supplicant::PairwiseCipherMask::GCMP_256,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
