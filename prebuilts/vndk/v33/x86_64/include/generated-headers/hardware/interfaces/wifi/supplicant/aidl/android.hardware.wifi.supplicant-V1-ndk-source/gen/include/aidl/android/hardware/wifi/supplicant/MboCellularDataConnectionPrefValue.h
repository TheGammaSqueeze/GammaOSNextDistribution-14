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
enum class MboCellularDataConnectionPrefValue : int32_t {
  EXCLUDED = 0,
  NOT_PREFERRED = 1,
  PREFERRED = 255,
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
[[nodiscard]] static inline std::string toString(MboCellularDataConnectionPrefValue val) {
  switch(val) {
  case MboCellularDataConnectionPrefValue::EXCLUDED:
    return "EXCLUDED";
  case MboCellularDataConnectionPrefValue::NOT_PREFERRED:
    return "NOT_PREFERRED";
  case MboCellularDataConnectionPrefValue::PREFERRED:
    return "PREFERRED";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::MboCellularDataConnectionPrefValue, 3> enum_values<aidl::android::hardware::wifi::supplicant::MboCellularDataConnectionPrefValue> = {
  aidl::android::hardware::wifi::supplicant::MboCellularDataConnectionPrefValue::EXCLUDED,
  aidl::android::hardware::wifi::supplicant::MboCellularDataConnectionPrefValue::NOT_PREFERRED,
  aidl::android::hardware::wifi::supplicant::MboCellularDataConnectionPrefValue::PREFERRED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
