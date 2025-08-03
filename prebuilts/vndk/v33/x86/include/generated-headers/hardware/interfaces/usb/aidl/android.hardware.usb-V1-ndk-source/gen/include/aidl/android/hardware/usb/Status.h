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
namespace usb {
enum class Status : int32_t {
  SUCCESS = 0,
  ERROR = 1,
  INVALID_ARGUMENT = 2,
  UNRECOGNIZED_ROLE = 3,
  NOT_SUPPORTED = 4,
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace usb {
[[nodiscard]] static inline std::string toString(Status val) {
  switch(val) {
  case Status::SUCCESS:
    return "SUCCESS";
  case Status::ERROR:
    return "ERROR";
  case Status::INVALID_ARGUMENT:
    return "INVALID_ARGUMENT";
  case Status::UNRECOGNIZED_ROLE:
    return "UNRECOGNIZED_ROLE";
  case Status::NOT_SUPPORTED:
    return "NOT_SUPPORTED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::usb::Status, 5> enum_values<aidl::android::hardware::usb::Status> = {
  aidl::android::hardware::usb::Status::SUCCESS,
  aidl::android::hardware::usb::Status::ERROR,
  aidl::android::hardware::usb::Status::INVALID_ARGUMENT,
  aidl::android::hardware::usb::Status::UNRECOGNIZED_ROLE,
  aidl::android::hardware::usb::Status::NOT_SUPPORTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
