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
namespace voice {
enum class TtyMode : int32_t {
  OFF = 0,
  FULL = 1,
  HCO = 2,
  VCO = 3,
};

}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
[[nodiscard]] static inline std::string toString(TtyMode val) {
  switch(val) {
  case TtyMode::OFF:
    return "OFF";
  case TtyMode::FULL:
    return "FULL";
  case TtyMode::HCO:
    return "HCO";
  case TtyMode::VCO:
    return "VCO";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::voice::TtyMode, 4> enum_values<aidl::android::hardware::radio::voice::TtyMode> = {
  aidl::android::hardware::radio::voice::TtyMode::OFF,
  aidl::android::hardware::radio::voice::TtyMode::FULL,
  aidl::android::hardware::radio::voice::TtyMode::HCO,
  aidl::android::hardware::radio::voice::TtyMode::VCO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
