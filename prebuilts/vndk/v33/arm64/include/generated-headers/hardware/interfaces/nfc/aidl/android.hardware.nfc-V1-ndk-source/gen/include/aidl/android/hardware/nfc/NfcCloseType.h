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
namespace nfc {
enum class NfcCloseType : int32_t {
  DISABLE = 0,
  HOST_SWITCHED_OFF = 1,
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
[[nodiscard]] static inline std::string toString(NfcCloseType val) {
  switch(val) {
  case NfcCloseType::DISABLE:
    return "DISABLE";
  case NfcCloseType::HOST_SWITCHED_OFF:
    return "HOST_SWITCHED_OFF";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::nfc::NfcCloseType, 2> enum_values<aidl::android::hardware::nfc::NfcCloseType> = {
  aidl::android::hardware::nfc::NfcCloseType::DISABLE,
  aidl::android::hardware::nfc::NfcCloseType::HOST_SWITCHED_OFF,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
