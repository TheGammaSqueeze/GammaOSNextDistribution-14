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
namespace data {
enum class ApnAuthType : int32_t {
  NO_PAP_NO_CHAP = 0,
  PAP_NO_CHAP = 1,
  NO_PAP_CHAP = 2,
  PAP_CHAP = 3,
};

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
[[nodiscard]] static inline std::string toString(ApnAuthType val) {
  switch(val) {
  case ApnAuthType::NO_PAP_NO_CHAP:
    return "NO_PAP_NO_CHAP";
  case ApnAuthType::PAP_NO_CHAP:
    return "PAP_NO_CHAP";
  case ApnAuthType::NO_PAP_CHAP:
    return "NO_PAP_CHAP";
  case ApnAuthType::PAP_CHAP:
    return "PAP_CHAP";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::data::ApnAuthType, 4> enum_values<aidl::android::hardware::radio::data::ApnAuthType> = {
  aidl::android::hardware::radio::data::ApnAuthType::NO_PAP_NO_CHAP,
  aidl::android::hardware::radio::data::ApnAuthType::PAP_NO_CHAP,
  aidl::android::hardware::radio::data::ApnAuthType::NO_PAP_CHAP,
  aidl::android::hardware::radio::data::ApnAuthType::PAP_CHAP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
