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
namespace voice {
class UusInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t uusType = 0;
  int32_t uusDcs = 0;
  std::string uusData;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const UusInfo& rhs) const {
    return std::tie(uusType, uusDcs, uusData) != std::tie(rhs.uusType, rhs.uusDcs, rhs.uusData);
  }
  inline bool operator<(const UusInfo& rhs) const {
    return std::tie(uusType, uusDcs, uusData) < std::tie(rhs.uusType, rhs.uusDcs, rhs.uusData);
  }
  inline bool operator<=(const UusInfo& rhs) const {
    return std::tie(uusType, uusDcs, uusData) <= std::tie(rhs.uusType, rhs.uusDcs, rhs.uusData);
  }
  inline bool operator==(const UusInfo& rhs) const {
    return std::tie(uusType, uusDcs, uusData) == std::tie(rhs.uusType, rhs.uusDcs, rhs.uusData);
  }
  inline bool operator>(const UusInfo& rhs) const {
    return std::tie(uusType, uusDcs, uusData) > std::tie(rhs.uusType, rhs.uusDcs, rhs.uusData);
  }
  inline bool operator>=(const UusInfo& rhs) const {
    return std::tie(uusType, uusDcs, uusData) >= std::tie(rhs.uusType, rhs.uusDcs, rhs.uusData);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { UUS_DCS_USP = 0 };
  enum : int32_t { UUS_DCS_OSIHLP = 1 };
  enum : int32_t { UUS_DCS_X244 = 2 };
  enum : int32_t { UUS_DCS_RMCF = 3 };
  enum : int32_t { UUS_DCS_IA5C = 4 };
  enum : int32_t { UUS_TYPE_TYPE1_IMPLICIT = 0 };
  enum : int32_t { UUS_TYPE_TYPE1_REQUIRED = 1 };
  enum : int32_t { UUS_TYPE_TYPE1_NOT_REQUIRED = 2 };
  enum : int32_t { UUS_TYPE_TYPE2_REQUIRED = 3 };
  enum : int32_t { UUS_TYPE_TYPE2_NOT_REQUIRED = 4 };
  enum : int32_t { UUS_TYPE_TYPE3_REQUIRED = 5 };
  enum : int32_t { UUS_TYPE_TYPE3_NOT_REQUIRED = 6 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "UusInfo{";
    os << "uusType: " << ::android::internal::ToString(uusType);
    os << ", uusDcs: " << ::android::internal::ToString(uusDcs);
    os << ", uusData: " << ::android::internal::ToString(uusData);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
