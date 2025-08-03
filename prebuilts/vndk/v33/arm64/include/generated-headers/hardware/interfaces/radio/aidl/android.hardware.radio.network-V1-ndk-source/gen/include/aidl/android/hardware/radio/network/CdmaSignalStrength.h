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
class CdmaSignalStrength {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t dbm = 0;
  int32_t ecio = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const CdmaSignalStrength& rhs) const {
    return std::tie(dbm, ecio) != std::tie(rhs.dbm, rhs.ecio);
  }
  inline bool operator<(const CdmaSignalStrength& rhs) const {
    return std::tie(dbm, ecio) < std::tie(rhs.dbm, rhs.ecio);
  }
  inline bool operator<=(const CdmaSignalStrength& rhs) const {
    return std::tie(dbm, ecio) <= std::tie(rhs.dbm, rhs.ecio);
  }
  inline bool operator==(const CdmaSignalStrength& rhs) const {
    return std::tie(dbm, ecio) == std::tie(rhs.dbm, rhs.ecio);
  }
  inline bool operator>(const CdmaSignalStrength& rhs) const {
    return std::tie(dbm, ecio) > std::tie(rhs.dbm, rhs.ecio);
  }
  inline bool operator>=(const CdmaSignalStrength& rhs) const {
    return std::tie(dbm, ecio) >= std::tie(rhs.dbm, rhs.ecio);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "CdmaSignalStrength{";
    os << "dbm: " << ::android::internal::ToString(dbm);
    os << ", ecio: " << ::android::internal::ToString(ecio);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
