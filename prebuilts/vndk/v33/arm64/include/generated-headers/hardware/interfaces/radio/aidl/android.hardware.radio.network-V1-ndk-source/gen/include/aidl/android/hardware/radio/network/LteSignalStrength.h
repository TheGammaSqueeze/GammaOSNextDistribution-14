#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class LteSignalStrength {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t signalStrength = 0;
  int32_t rsrp = 0;
  int32_t rsrq = 0;
  int32_t rssnr = 0;
  int32_t cqi = 0;
  int32_t timingAdvance = 0;
  int32_t cqiTableIndex = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LteSignalStrength& rhs) const {
    return std::tie(signalStrength, rsrp, rsrq, rssnr, cqi, timingAdvance, cqiTableIndex) != std::tie(rhs.signalStrength, rhs.rsrp, rhs.rsrq, rhs.rssnr, rhs.cqi, rhs.timingAdvance, rhs.cqiTableIndex);
  }
  inline bool operator<(const LteSignalStrength& rhs) const {
    return std::tie(signalStrength, rsrp, rsrq, rssnr, cqi, timingAdvance, cqiTableIndex) < std::tie(rhs.signalStrength, rhs.rsrp, rhs.rsrq, rhs.rssnr, rhs.cqi, rhs.timingAdvance, rhs.cqiTableIndex);
  }
  inline bool operator<=(const LteSignalStrength& rhs) const {
    return std::tie(signalStrength, rsrp, rsrq, rssnr, cqi, timingAdvance, cqiTableIndex) <= std::tie(rhs.signalStrength, rhs.rsrp, rhs.rsrq, rhs.rssnr, rhs.cqi, rhs.timingAdvance, rhs.cqiTableIndex);
  }
  inline bool operator==(const LteSignalStrength& rhs) const {
    return std::tie(signalStrength, rsrp, rsrq, rssnr, cqi, timingAdvance, cqiTableIndex) == std::tie(rhs.signalStrength, rhs.rsrp, rhs.rsrq, rhs.rssnr, rhs.cqi, rhs.timingAdvance, rhs.cqiTableIndex);
  }
  inline bool operator>(const LteSignalStrength& rhs) const {
    return std::tie(signalStrength, rsrp, rsrq, rssnr, cqi, timingAdvance, cqiTableIndex) > std::tie(rhs.signalStrength, rhs.rsrp, rhs.rsrq, rhs.rssnr, rhs.cqi, rhs.timingAdvance, rhs.cqiTableIndex);
  }
  inline bool operator>=(const LteSignalStrength& rhs) const {
    return std::tie(signalStrength, rsrp, rsrq, rssnr, cqi, timingAdvance, cqiTableIndex) >= std::tie(rhs.signalStrength, rhs.rsrp, rhs.rsrq, rhs.rssnr, rhs.cqi, rhs.timingAdvance, rhs.cqiTableIndex);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LteSignalStrength{";
    os << "signalStrength: " << ::android::internal::ToString(signalStrength);
    os << ", rsrp: " << ::android::internal::ToString(rsrp);
    os << ", rsrq: " << ::android::internal::ToString(rsrq);
    os << ", rssnr: " << ::android::internal::ToString(rssnr);
    os << ", cqi: " << ::android::internal::ToString(cqi);
    os << ", timingAdvance: " << ::android::internal::ToString(timingAdvance);
    os << ", cqiTableIndex: " << ::android::internal::ToString(cqiTableIndex);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
