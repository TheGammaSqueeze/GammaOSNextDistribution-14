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
enum class OsuMethod : int8_t {
  OMA_DM = 0,
  SOAP_XML_SPP = 1,
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
[[nodiscard]] static inline std::string toString(OsuMethod val) {
  switch(val) {
  case OsuMethod::OMA_DM:
    return "OMA_DM";
  case OsuMethod::SOAP_XML_SPP:
    return "SOAP_XML_SPP";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::OsuMethod, 2> enum_values<aidl::android::hardware::wifi::supplicant::OsuMethod> = {
  aidl::android::hardware::wifi::supplicant::OsuMethod::OMA_DM,
  aidl::android::hardware::wifi::supplicant::OsuMethod::SOAP_XML_SPP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
