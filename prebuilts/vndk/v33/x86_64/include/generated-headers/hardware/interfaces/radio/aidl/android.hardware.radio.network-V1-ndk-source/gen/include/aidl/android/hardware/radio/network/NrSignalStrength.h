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
class NrSignalStrength {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t ssRsrp = 0;
  int32_t ssRsrq = 0;
  int32_t ssSinr = 0;
  int32_t csiRsrp = 0;
  int32_t csiRsrq = 0;
  int32_t csiSinr = 0;
  int32_t csiCqiTableIndex = 0;
  std::vector<uint8_t> csiCqiReport;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NrSignalStrength& rhs) const {
    return std::tie(ssRsrp, ssRsrq, ssSinr, csiRsrp, csiRsrq, csiSinr, csiCqiTableIndex, csiCqiReport) != std::tie(rhs.ssRsrp, rhs.ssRsrq, rhs.ssSinr, rhs.csiRsrp, rhs.csiRsrq, rhs.csiSinr, rhs.csiCqiTableIndex, rhs.csiCqiReport);
  }
  inline bool operator<(const NrSignalStrength& rhs) const {
    return std::tie(ssRsrp, ssRsrq, ssSinr, csiRsrp, csiRsrq, csiSinr, csiCqiTableIndex, csiCqiReport) < std::tie(rhs.ssRsrp, rhs.ssRsrq, rhs.ssSinr, rhs.csiRsrp, rhs.csiRsrq, rhs.csiSinr, rhs.csiCqiTableIndex, rhs.csiCqiReport);
  }
  inline bool operator<=(const NrSignalStrength& rhs) const {
    return std::tie(ssRsrp, ssRsrq, ssSinr, csiRsrp, csiRsrq, csiSinr, csiCqiTableIndex, csiCqiReport) <= std::tie(rhs.ssRsrp, rhs.ssRsrq, rhs.ssSinr, rhs.csiRsrp, rhs.csiRsrq, rhs.csiSinr, rhs.csiCqiTableIndex, rhs.csiCqiReport);
  }
  inline bool operator==(const NrSignalStrength& rhs) const {
    return std::tie(ssRsrp, ssRsrq, ssSinr, csiRsrp, csiRsrq, csiSinr, csiCqiTableIndex, csiCqiReport) == std::tie(rhs.ssRsrp, rhs.ssRsrq, rhs.ssSinr, rhs.csiRsrp, rhs.csiRsrq, rhs.csiSinr, rhs.csiCqiTableIndex, rhs.csiCqiReport);
  }
  inline bool operator>(const NrSignalStrength& rhs) const {
    return std::tie(ssRsrp, ssRsrq, ssSinr, csiRsrp, csiRsrq, csiSinr, csiCqiTableIndex, csiCqiReport) > std::tie(rhs.ssRsrp, rhs.ssRsrq, rhs.ssSinr, rhs.csiRsrp, rhs.csiRsrq, rhs.csiSinr, rhs.csiCqiTableIndex, rhs.csiCqiReport);
  }
  inline bool operator>=(const NrSignalStrength& rhs) const {
    return std::tie(ssRsrp, ssRsrq, ssSinr, csiRsrp, csiRsrq, csiSinr, csiCqiTableIndex, csiCqiReport) >= std::tie(rhs.ssRsrp, rhs.ssRsrq, rhs.ssSinr, rhs.csiRsrp, rhs.csiRsrq, rhs.csiSinr, rhs.csiCqiTableIndex, rhs.csiCqiReport);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NrSignalStrength{";
    os << "ssRsrp: " << ::android::internal::ToString(ssRsrp);
    os << ", ssRsrq: " << ::android::internal::ToString(ssRsrq);
    os << ", ssSinr: " << ::android::internal::ToString(ssSinr);
    os << ", csiRsrp: " << ::android::internal::ToString(csiRsrp);
    os << ", csiRsrq: " << ::android::internal::ToString(csiRsrq);
    os << ", csiSinr: " << ::android::internal::ToString(csiSinr);
    os << ", csiCqiTableIndex: " << ::android::internal::ToString(csiCqiTableIndex);
    os << ", csiCqiReport: " << ::android::internal::ToString(csiCqiReport);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
