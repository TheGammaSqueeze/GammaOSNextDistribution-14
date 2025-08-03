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
class NrVopsInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t vopsSupported = 0;
  int8_t emcSupported = 0;
  int8_t emfSupported = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NrVopsInfo& rhs) const {
    return std::tie(vopsSupported, emcSupported, emfSupported) != std::tie(rhs.vopsSupported, rhs.emcSupported, rhs.emfSupported);
  }
  inline bool operator<(const NrVopsInfo& rhs) const {
    return std::tie(vopsSupported, emcSupported, emfSupported) < std::tie(rhs.vopsSupported, rhs.emcSupported, rhs.emfSupported);
  }
  inline bool operator<=(const NrVopsInfo& rhs) const {
    return std::tie(vopsSupported, emcSupported, emfSupported) <= std::tie(rhs.vopsSupported, rhs.emcSupported, rhs.emfSupported);
  }
  inline bool operator==(const NrVopsInfo& rhs) const {
    return std::tie(vopsSupported, emcSupported, emfSupported) == std::tie(rhs.vopsSupported, rhs.emcSupported, rhs.emfSupported);
  }
  inline bool operator>(const NrVopsInfo& rhs) const {
    return std::tie(vopsSupported, emcSupported, emfSupported) > std::tie(rhs.vopsSupported, rhs.emcSupported, rhs.emfSupported);
  }
  inline bool operator>=(const NrVopsInfo& rhs) const {
    return std::tie(vopsSupported, emcSupported, emfSupported) >= std::tie(rhs.vopsSupported, rhs.emcSupported, rhs.emfSupported);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int8_t { EMC_INDICATOR_NOT_SUPPORTED = 0 };
  enum : int8_t { EMC_INDICATOR_NR_CONNECTED_TO_5GCN = 1 };
  enum : int8_t { EMC_INDICATOR_EUTRA_CONNECTED_TO_5GCN = 2 };
  enum : int8_t { EMC_INDICATOR_BOTH_NR_EUTRA_CONNECTED_TO_5GCN = 3 };
  enum : int8_t { EMF_INDICATOR_NOT_SUPPORTED = 0 };
  enum : int8_t { EMF_INDICATOR_NR_CONNECTED_TO_5GCN = 1 };
  enum : int8_t { EMF_INDICATOR_EUTRA_CONNECTED_TO_5GCN = 2 };
  enum : int8_t { EMF_INDICATOR_BOTH_NR_EUTRA_CONNECTED_TO_5GCN = 3 };
  enum : int8_t { VOPS_INDICATOR_VOPS_NOT_SUPPORTED = 0 };
  enum : int8_t { VOPS_INDICATOR_VOPS_OVER_3GPP = 1 };
  enum : int8_t { VOPS_INDICATOR_VOPS_OVER_NON_3GPP = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "NrVopsInfo{";
    os << "vopsSupported: " << ::android::internal::ToString(vopsSupported);
    os << ", emcSupported: " << ::android::internal::ToString(emcSupported);
    os << ", emfSupported: " << ::android::internal::ToString(emfSupported);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
