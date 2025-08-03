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
enum class CdmaOtaProvisionStatus : int32_t {
  SPL_UNLOCKED = 0,
  SPC_RETRIES_EXCEEDED = 1,
  A_KEY_EXCHANGED = 2,
  SSD_UPDATED = 3,
  NAM_DOWNLOADED = 4,
  MDN_DOWNLOADED = 5,
  IMSI_DOWNLOADED = 6,
  PRL_DOWNLOADED = 7,
  COMMITTED = 8,
  OTAPA_STARTED = 9,
  OTAPA_STOPPED = 10,
  OTAPA_ABORTED = 11,
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
[[nodiscard]] static inline std::string toString(CdmaOtaProvisionStatus val) {
  switch(val) {
  case CdmaOtaProvisionStatus::SPL_UNLOCKED:
    return "SPL_UNLOCKED";
  case CdmaOtaProvisionStatus::SPC_RETRIES_EXCEEDED:
    return "SPC_RETRIES_EXCEEDED";
  case CdmaOtaProvisionStatus::A_KEY_EXCHANGED:
    return "A_KEY_EXCHANGED";
  case CdmaOtaProvisionStatus::SSD_UPDATED:
    return "SSD_UPDATED";
  case CdmaOtaProvisionStatus::NAM_DOWNLOADED:
    return "NAM_DOWNLOADED";
  case CdmaOtaProvisionStatus::MDN_DOWNLOADED:
    return "MDN_DOWNLOADED";
  case CdmaOtaProvisionStatus::IMSI_DOWNLOADED:
    return "IMSI_DOWNLOADED";
  case CdmaOtaProvisionStatus::PRL_DOWNLOADED:
    return "PRL_DOWNLOADED";
  case CdmaOtaProvisionStatus::COMMITTED:
    return "COMMITTED";
  case CdmaOtaProvisionStatus::OTAPA_STARTED:
    return "OTAPA_STARTED";
  case CdmaOtaProvisionStatus::OTAPA_STOPPED:
    return "OTAPA_STOPPED";
  case CdmaOtaProvisionStatus::OTAPA_ABORTED:
    return "OTAPA_ABORTED";
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
constexpr inline std::array<aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus, 12> enum_values<aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus> = {
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::SPL_UNLOCKED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::SPC_RETRIES_EXCEEDED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::A_KEY_EXCHANGED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::SSD_UPDATED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::NAM_DOWNLOADED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::MDN_DOWNLOADED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::IMSI_DOWNLOADED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::PRL_DOWNLOADED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::COMMITTED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::OTAPA_STARTED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::OTAPA_STOPPED,
  aidl::android::hardware::radio::voice::CdmaOtaProvisionStatus::OTAPA_ABORTED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
