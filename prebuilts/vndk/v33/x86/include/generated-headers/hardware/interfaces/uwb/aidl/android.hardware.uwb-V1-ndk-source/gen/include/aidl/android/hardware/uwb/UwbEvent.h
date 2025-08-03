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
namespace uwb {
enum class UwbEvent : int32_t {
  OPEN_CPLT = 0,
  CLOSE_CPLT = 1,
  POST_INIT_CPLT = 2,
  ERROR = 3,
};

}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
[[nodiscard]] static inline std::string toString(UwbEvent val) {
  switch(val) {
  case UwbEvent::OPEN_CPLT:
    return "OPEN_CPLT";
  case UwbEvent::CLOSE_CPLT:
    return "CLOSE_CPLT";
  case UwbEvent::POST_INIT_CPLT:
    return "POST_INIT_CPLT";
  case UwbEvent::ERROR:
    return "ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::uwb::UwbEvent, 4> enum_values<aidl::android::hardware::uwb::UwbEvent> = {
  aidl::android::hardware::uwb::UwbEvent::OPEN_CPLT,
  aidl::android::hardware::uwb::UwbEvent::CLOSE_CPLT,
  aidl::android::hardware::uwb::UwbEvent::POST_INIT_CPLT,
  aidl::android::hardware::uwb::UwbEvent::ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
