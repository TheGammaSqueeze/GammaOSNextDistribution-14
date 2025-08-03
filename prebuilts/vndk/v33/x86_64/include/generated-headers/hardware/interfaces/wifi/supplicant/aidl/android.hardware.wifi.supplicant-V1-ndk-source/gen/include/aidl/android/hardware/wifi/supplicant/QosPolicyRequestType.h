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
enum class QosPolicyRequestType : int8_t {
  QOS_POLICY_ADD = 0,
  QOS_POLICY_REMOVE = 1,
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
[[nodiscard]] static inline std::string toString(QosPolicyRequestType val) {
  switch(val) {
  case QosPolicyRequestType::QOS_POLICY_ADD:
    return "QOS_POLICY_ADD";
  case QosPolicyRequestType::QOS_POLICY_REMOVE:
    return "QOS_POLICY_REMOVE";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::QosPolicyRequestType, 2> enum_values<aidl::android::hardware::wifi::supplicant::QosPolicyRequestType> = {
  aidl::android::hardware::wifi::supplicant::QosPolicyRequestType::QOS_POLICY_ADD,
  aidl::android::hardware::wifi::supplicant::QosPolicyRequestType::QOS_POLICY_REMOVE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
