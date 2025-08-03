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
namespace sim {
enum class PbReceivedStatus : int8_t {
  PB_RECEIVED_OK = 1,
  PB_RECEIVED_ERROR = 2,
  PB_RECEIVED_ABORT = 3,
  PB_RECEIVED_FINAL = 4,
};

}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
[[nodiscard]] static inline std::string toString(PbReceivedStatus val) {
  switch(val) {
  case PbReceivedStatus::PB_RECEIVED_OK:
    return "PB_RECEIVED_OK";
  case PbReceivedStatus::PB_RECEIVED_ERROR:
    return "PB_RECEIVED_ERROR";
  case PbReceivedStatus::PB_RECEIVED_ABORT:
    return "PB_RECEIVED_ABORT";
  case PbReceivedStatus::PB_RECEIVED_FINAL:
    return "PB_RECEIVED_FINAL";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::sim::PbReceivedStatus, 4> enum_values<aidl::android::hardware::radio::sim::PbReceivedStatus> = {
  aidl::android::hardware::radio::sim::PbReceivedStatus::PB_RECEIVED_OK,
  aidl::android::hardware::radio::sim::PbReceivedStatus::PB_RECEIVED_ERROR,
  aidl::android::hardware::radio::sim::PbReceivedStatus::PB_RECEIVED_ABORT,
  aidl::android::hardware::radio::sim::PbReceivedStatus::PB_RECEIVED_FINAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
