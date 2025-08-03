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
enum class SrvccState : int32_t {
  HANDOVER_STARTED = 0,
  HANDOVER_COMPLETED = 1,
  HANDOVER_FAILED = 2,
  HANDOVER_CANCELED = 3,
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
[[nodiscard]] static inline std::string toString(SrvccState val) {
  switch(val) {
  case SrvccState::HANDOVER_STARTED:
    return "HANDOVER_STARTED";
  case SrvccState::HANDOVER_COMPLETED:
    return "HANDOVER_COMPLETED";
  case SrvccState::HANDOVER_FAILED:
    return "HANDOVER_FAILED";
  case SrvccState::HANDOVER_CANCELED:
    return "HANDOVER_CANCELED";
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
constexpr inline std::array<aidl::android::hardware::radio::voice::SrvccState, 4> enum_values<aidl::android::hardware::radio::voice::SrvccState> = {
  aidl::android::hardware::radio::voice::SrvccState::HANDOVER_STARTED,
  aidl::android::hardware::radio::voice::SrvccState::HANDOVER_COMPLETED,
  aidl::android::hardware::radio::voice::SrvccState::HANDOVER_FAILED,
  aidl::android::hardware::radio::voice::SrvccState::HANDOVER_CANCELED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
