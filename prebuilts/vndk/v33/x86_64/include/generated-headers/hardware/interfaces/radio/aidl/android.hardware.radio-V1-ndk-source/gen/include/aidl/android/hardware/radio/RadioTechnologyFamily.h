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
enum class RadioTechnologyFamily : int32_t {
  THREE_GPP = 0,
  THREE_GPP2 = 1,
};

}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
[[nodiscard]] static inline std::string toString(RadioTechnologyFamily val) {
  switch(val) {
  case RadioTechnologyFamily::THREE_GPP:
    return "THREE_GPP";
  case RadioTechnologyFamily::THREE_GPP2:
    return "THREE_GPP2";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::RadioTechnologyFamily, 2> enum_values<aidl::android::hardware::radio::RadioTechnologyFamily> = {
  aidl::android::hardware::radio::RadioTechnologyFamily::THREE_GPP,
  aidl::android::hardware::radio::RadioTechnologyFamily::THREE_GPP2,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
