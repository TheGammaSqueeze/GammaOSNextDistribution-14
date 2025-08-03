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
namespace messaging {
enum class SmsAcknowledgeFailCause : int32_t {
  MEMORY_CAPACITY_EXCEEDED = 211,
  UNSPECIFIED_ERROR = 255,
};

}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace messaging {
[[nodiscard]] static inline std::string toString(SmsAcknowledgeFailCause val) {
  switch(val) {
  case SmsAcknowledgeFailCause::MEMORY_CAPACITY_EXCEEDED:
    return "MEMORY_CAPACITY_EXCEEDED";
  case SmsAcknowledgeFailCause::UNSPECIFIED_ERROR:
    return "UNSPECIFIED_ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace messaging
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::messaging::SmsAcknowledgeFailCause, 2> enum_values<aidl::android::hardware::radio::messaging::SmsAcknowledgeFailCause> = {
  aidl::android::hardware::radio::messaging::SmsAcknowledgeFailCause::MEMORY_CAPACITY_EXCEEDED,
  aidl::android::hardware::radio::messaging::SmsAcknowledgeFailCause::UNSPECIFIED_ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
