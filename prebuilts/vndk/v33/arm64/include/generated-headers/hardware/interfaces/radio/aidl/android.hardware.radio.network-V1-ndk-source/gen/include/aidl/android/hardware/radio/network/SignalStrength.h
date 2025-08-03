#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/CdmaSignalStrength.h>
#include <aidl/android/hardware/radio/network/EvdoSignalStrength.h>
#include <aidl/android/hardware/radio/network/GsmSignalStrength.h>
#include <aidl/android/hardware/radio/network/LteSignalStrength.h>
#include <aidl/android/hardware/radio/network/NrSignalStrength.h>
#include <aidl/android/hardware/radio/network/TdscdmaSignalStrength.h>
#include <aidl/android/hardware/radio/network/WcdmaSignalStrength.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class SignalStrength {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::radio::network::GsmSignalStrength gsm;
  ::aidl::android::hardware::radio::network::CdmaSignalStrength cdma;
  ::aidl::android::hardware::radio::network::EvdoSignalStrength evdo;
  ::aidl::android::hardware::radio::network::LteSignalStrength lte;
  ::aidl::android::hardware::radio::network::TdscdmaSignalStrength tdscdma;
  ::aidl::android::hardware::radio::network::WcdmaSignalStrength wcdma;
  ::aidl::android::hardware::radio::network::NrSignalStrength nr;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SignalStrength& rhs) const {
    return std::tie(gsm, cdma, evdo, lte, tdscdma, wcdma, nr) != std::tie(rhs.gsm, rhs.cdma, rhs.evdo, rhs.lte, rhs.tdscdma, rhs.wcdma, rhs.nr);
  }
  inline bool operator<(const SignalStrength& rhs) const {
    return std::tie(gsm, cdma, evdo, lte, tdscdma, wcdma, nr) < std::tie(rhs.gsm, rhs.cdma, rhs.evdo, rhs.lte, rhs.tdscdma, rhs.wcdma, rhs.nr);
  }
  inline bool operator<=(const SignalStrength& rhs) const {
    return std::tie(gsm, cdma, evdo, lte, tdscdma, wcdma, nr) <= std::tie(rhs.gsm, rhs.cdma, rhs.evdo, rhs.lte, rhs.tdscdma, rhs.wcdma, rhs.nr);
  }
  inline bool operator==(const SignalStrength& rhs) const {
    return std::tie(gsm, cdma, evdo, lte, tdscdma, wcdma, nr) == std::tie(rhs.gsm, rhs.cdma, rhs.evdo, rhs.lte, rhs.tdscdma, rhs.wcdma, rhs.nr);
  }
  inline bool operator>(const SignalStrength& rhs) const {
    return std::tie(gsm, cdma, evdo, lte, tdscdma, wcdma, nr) > std::tie(rhs.gsm, rhs.cdma, rhs.evdo, rhs.lte, rhs.tdscdma, rhs.wcdma, rhs.nr);
  }
  inline bool operator>=(const SignalStrength& rhs) const {
    return std::tie(gsm, cdma, evdo, lte, tdscdma, wcdma, nr) >= std::tie(rhs.gsm, rhs.cdma, rhs.evdo, rhs.lte, rhs.tdscdma, rhs.wcdma, rhs.nr);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SignalStrength{";
    os << "gsm: " << ::android::internal::ToString(gsm);
    os << ", cdma: " << ::android::internal::ToString(cdma);
    os << ", evdo: " << ::android::internal::ToString(evdo);
    os << ", lte: " << ::android::internal::ToString(lte);
    os << ", tdscdma: " << ::android::internal::ToString(tdscdma);
    os << ", wcdma: " << ::android::internal::ToString(wcdma);
    os << ", nr: " << ::android::internal::ToString(nr);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
