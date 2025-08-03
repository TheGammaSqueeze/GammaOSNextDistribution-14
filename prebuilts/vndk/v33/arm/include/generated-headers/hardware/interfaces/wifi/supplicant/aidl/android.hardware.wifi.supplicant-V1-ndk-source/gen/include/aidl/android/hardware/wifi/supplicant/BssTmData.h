#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/BssTmDataFlagsMask.h>
#include <aidl/android/hardware/wifi/supplicant/BssTmStatusCode.h>
#include <aidl/android/hardware/wifi/supplicant/MboCellularDataConnectionPrefValue.h>
#include <aidl/android/hardware/wifi/supplicant/MboTransitionReasonCode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BssTmData {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::wifi::supplicant::BssTmStatusCode status = ::aidl::android::hardware::wifi::supplicant::BssTmStatusCode(0);
  ::aidl::android::hardware::wifi::supplicant::BssTmDataFlagsMask flags = ::aidl::android::hardware::wifi::supplicant::BssTmDataFlagsMask(0);
  int32_t assocRetryDelayMs = 0;
  ::aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode mboTransitionReason = ::aidl::android::hardware::wifi::supplicant::MboTransitionReasonCode(0);
  ::aidl::android::hardware::wifi::supplicant::MboCellularDataConnectionPrefValue mboCellPreference = ::aidl::android::hardware::wifi::supplicant::MboCellularDataConnectionPrefValue(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BssTmData& rhs) const {
    return std::tie(status, flags, assocRetryDelayMs, mboTransitionReason, mboCellPreference) != std::tie(rhs.status, rhs.flags, rhs.assocRetryDelayMs, rhs.mboTransitionReason, rhs.mboCellPreference);
  }
  inline bool operator<(const BssTmData& rhs) const {
    return std::tie(status, flags, assocRetryDelayMs, mboTransitionReason, mboCellPreference) < std::tie(rhs.status, rhs.flags, rhs.assocRetryDelayMs, rhs.mboTransitionReason, rhs.mboCellPreference);
  }
  inline bool operator<=(const BssTmData& rhs) const {
    return std::tie(status, flags, assocRetryDelayMs, mboTransitionReason, mboCellPreference) <= std::tie(rhs.status, rhs.flags, rhs.assocRetryDelayMs, rhs.mboTransitionReason, rhs.mboCellPreference);
  }
  inline bool operator==(const BssTmData& rhs) const {
    return std::tie(status, flags, assocRetryDelayMs, mboTransitionReason, mboCellPreference) == std::tie(rhs.status, rhs.flags, rhs.assocRetryDelayMs, rhs.mboTransitionReason, rhs.mboCellPreference);
  }
  inline bool operator>(const BssTmData& rhs) const {
    return std::tie(status, flags, assocRetryDelayMs, mboTransitionReason, mboCellPreference) > std::tie(rhs.status, rhs.flags, rhs.assocRetryDelayMs, rhs.mboTransitionReason, rhs.mboCellPreference);
  }
  inline bool operator>=(const BssTmData& rhs) const {
    return std::tie(status, flags, assocRetryDelayMs, mboTransitionReason, mboCellPreference) >= std::tie(rhs.status, rhs.flags, rhs.assocRetryDelayMs, rhs.mboTransitionReason, rhs.mboCellPreference);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BssTmData{";
    os << "status: " << ::android::internal::ToString(status);
    os << ", flags: " << ::android::internal::ToString(flags);
    os << ", assocRetryDelayMs: " << ::android::internal::ToString(assocRetryDelayMs);
    os << ", mboTransitionReason: " << ::android::internal::ToString(mboTransitionReason);
    os << ", mboCellPreference: " << ::android::internal::ToString(mboCellPreference);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
