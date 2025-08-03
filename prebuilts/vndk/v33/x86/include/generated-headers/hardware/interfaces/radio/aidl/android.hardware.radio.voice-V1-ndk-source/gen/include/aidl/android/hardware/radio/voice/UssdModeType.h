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
enum class UssdModeType : int32_t {
  NOTIFY = 0,
  REQUEST = 1,
  NW_RELEASE = 2,
  LOCAL_CLIENT = 3,
  NOT_SUPPORTED = 4,
  NW_TIMEOUT = 5,
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
[[nodiscard]] static inline std::string toString(UssdModeType val) {
  switch(val) {
  case UssdModeType::NOTIFY:
    return "NOTIFY";
  case UssdModeType::REQUEST:
    return "REQUEST";
  case UssdModeType::NW_RELEASE:
    return "NW_RELEASE";
  case UssdModeType::LOCAL_CLIENT:
    return "LOCAL_CLIENT";
  case UssdModeType::NOT_SUPPORTED:
    return "NOT_SUPPORTED";
  case UssdModeType::NW_TIMEOUT:
    return "NW_TIMEOUT";
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
constexpr inline std::array<aidl::android::hardware::radio::voice::UssdModeType, 6> enum_values<aidl::android::hardware::radio::voice::UssdModeType> = {
  aidl::android::hardware::radio::voice::UssdModeType::NOTIFY,
  aidl::android::hardware::radio::voice::UssdModeType::REQUEST,
  aidl::android::hardware::radio::voice::UssdModeType::NW_RELEASE,
  aidl::android::hardware::radio::voice::UssdModeType::LOCAL_CLIENT,
  aidl::android::hardware::radio::voice::UssdModeType::NOT_SUPPORTED,
  aidl::android::hardware::radio::voice::UssdModeType::NW_TIMEOUT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
