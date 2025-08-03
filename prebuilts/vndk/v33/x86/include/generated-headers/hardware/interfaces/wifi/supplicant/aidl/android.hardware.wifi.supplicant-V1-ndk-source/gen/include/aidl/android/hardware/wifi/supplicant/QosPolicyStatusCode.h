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
enum class QosPolicyStatusCode : int8_t {
  QOS_POLICY_SUCCESS = 0,
  QOS_POLICY_REQUEST_DECLINED = 1,
  QOS_POLICY_CLASSIFIER_NOT_SUPPORTED = 2,
  QOS_POLICY_INSUFFICIENT_RESOURCES = 3,
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
[[nodiscard]] static inline std::string toString(QosPolicyStatusCode val) {
  switch(val) {
  case QosPolicyStatusCode::QOS_POLICY_SUCCESS:
    return "QOS_POLICY_SUCCESS";
  case QosPolicyStatusCode::QOS_POLICY_REQUEST_DECLINED:
    return "QOS_POLICY_REQUEST_DECLINED";
  case QosPolicyStatusCode::QOS_POLICY_CLASSIFIER_NOT_SUPPORTED:
    return "QOS_POLICY_CLASSIFIER_NOT_SUPPORTED";
  case QosPolicyStatusCode::QOS_POLICY_INSUFFICIENT_RESOURCES:
    return "QOS_POLICY_INSUFFICIENT_RESOURCES";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode, 4> enum_values<aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode> = {
  aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode::QOS_POLICY_SUCCESS,
  aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode::QOS_POLICY_REQUEST_DECLINED,
  aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode::QOS_POLICY_CLASSIFIER_NOT_SUPPORTED,
  aidl::android::hardware::wifi::supplicant::QosPolicyStatusCode::QOS_POLICY_INSUFFICIENT_RESOURCES,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
