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
enum class ApnTypes : int32_t {
  NONE = 0,
  DEFAULT = 1,
  MMS = 2,
  SUPL = 4,
  DUN = 8,
  HIPRI = 16,
  FOTA = 32,
  IMS = 64,
  CBS = 128,
  IA = 256,
  EMERGENCY = 512,
  MCX = 1024,
  XCAP = 2048,
  VSIM = 4096,
  BIP = 8192,
  ENTERPRISE = 16384,
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
[[nodiscard]] static inline std::string toString(ApnTypes val) {
  switch(val) {
  case ApnTypes::NONE:
    return "NONE";
  case ApnTypes::DEFAULT:
    return "DEFAULT";
  case ApnTypes::MMS:
    return "MMS";
  case ApnTypes::SUPL:
    return "SUPL";
  case ApnTypes::DUN:
    return "DUN";
  case ApnTypes::HIPRI:
    return "HIPRI";
  case ApnTypes::FOTA:
    return "FOTA";
  case ApnTypes::IMS:
    return "IMS";
  case ApnTypes::CBS:
    return "CBS";
  case ApnTypes::IA:
    return "IA";
  case ApnTypes::EMERGENCY:
    return "EMERGENCY";
  case ApnTypes::MCX:
    return "MCX";
  case ApnTypes::XCAP:
    return "XCAP";
  case ApnTypes::VSIM:
    return "VSIM";
  case ApnTypes::BIP:
    return "BIP";
  case ApnTypes::ENTERPRISE:
    return "ENTERPRISE";
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
constexpr inline std::array<aidl::android::hardware::radio::data::ApnTypes, 16> enum_values<aidl::android::hardware::radio::data::ApnTypes> = {
  aidl::android::hardware::radio::data::ApnTypes::NONE,
  aidl::android::hardware::radio::data::ApnTypes::DEFAULT,
  aidl::android::hardware::radio::data::ApnTypes::MMS,
  aidl::android::hardware::radio::data::ApnTypes::SUPL,
  aidl::android::hardware::radio::data::ApnTypes::DUN,
  aidl::android::hardware::radio::data::ApnTypes::HIPRI,
  aidl::android::hardware::radio::data::ApnTypes::FOTA,
  aidl::android::hardware::radio::data::ApnTypes::IMS,
  aidl::android::hardware::radio::data::ApnTypes::CBS,
  aidl::android::hardware::radio::data::ApnTypes::IA,
  aidl::android::hardware::radio::data::ApnTypes::EMERGENCY,
  aidl::android::hardware::radio::data::ApnTypes::MCX,
  aidl::android::hardware::radio::data::ApnTypes::XCAP,
  aidl::android::hardware::radio::data::ApnTypes::VSIM,
  aidl::android::hardware::radio::data::ApnTypes::BIP,
  aidl::android::hardware::radio::data::ApnTypes::ENTERPRISE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
