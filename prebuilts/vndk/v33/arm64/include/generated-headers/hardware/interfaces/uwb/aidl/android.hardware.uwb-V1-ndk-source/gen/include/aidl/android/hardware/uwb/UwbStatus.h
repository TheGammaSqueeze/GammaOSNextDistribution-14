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
enum class UwbStatus : int32_t {
  OK = 0,
  FAILED = 1,
  ERR_TRANSPORT = 2,
  ERR_CMD_TIMEOUT = 3,
  REFUSED = 4,
};

}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
[[nodiscard]] static inline std::string toString(UwbStatus val) {
  switch(val) {
  case UwbStatus::OK:
    return "OK";
  case UwbStatus::FAILED:
    return "FAILED";
  case UwbStatus::ERR_TRANSPORT:
    return "ERR_TRANSPORT";
  case UwbStatus::ERR_CMD_TIMEOUT:
    return "ERR_CMD_TIMEOUT";
  case UwbStatus::REFUSED:
    return "REFUSED";
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
constexpr inline std::array<aidl::android::hardware::uwb::UwbStatus, 5> enum_values<aidl::android::hardware::uwb::UwbStatus> = {
  aidl::android::hardware::uwb::UwbStatus::OK,
  aidl::android::hardware::uwb::UwbStatus::FAILED,
  aidl::android::hardware::uwb::UwbStatus::ERR_TRANSPORT,
  aidl::android::hardware::uwb::UwbStatus::ERR_CMD_TIMEOUT,
  aidl::android::hardware::uwb::UwbStatus::REFUSED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
