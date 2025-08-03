#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/PdpProtocolType.h>
#include <aidl/android/hardware/radio/data/SliceInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class RouteSelectionDescriptor {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t precedence = 0;
  ::aidl::android::hardware::radio::data::PdpProtocolType sessionType = ::aidl::android::hardware::radio::data::PdpProtocolType(0);
  int8_t sscMode = 0;
  std::vector<::aidl::android::hardware::radio::data::SliceInfo> sliceInfo;
  std::vector<std::string> dnn;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RouteSelectionDescriptor& rhs) const {
    return std::tie(precedence, sessionType, sscMode, sliceInfo, dnn) != std::tie(rhs.precedence, rhs.sessionType, rhs.sscMode, rhs.sliceInfo, rhs.dnn);
  }
  inline bool operator<(const RouteSelectionDescriptor& rhs) const {
    return std::tie(precedence, sessionType, sscMode, sliceInfo, dnn) < std::tie(rhs.precedence, rhs.sessionType, rhs.sscMode, rhs.sliceInfo, rhs.dnn);
  }
  inline bool operator<=(const RouteSelectionDescriptor& rhs) const {
    return std::tie(precedence, sessionType, sscMode, sliceInfo, dnn) <= std::tie(rhs.precedence, rhs.sessionType, rhs.sscMode, rhs.sliceInfo, rhs.dnn);
  }
  inline bool operator==(const RouteSelectionDescriptor& rhs) const {
    return std::tie(precedence, sessionType, sscMode, sliceInfo, dnn) == std::tie(rhs.precedence, rhs.sessionType, rhs.sscMode, rhs.sliceInfo, rhs.dnn);
  }
  inline bool operator>(const RouteSelectionDescriptor& rhs) const {
    return std::tie(precedence, sessionType, sscMode, sliceInfo, dnn) > std::tie(rhs.precedence, rhs.sessionType, rhs.sscMode, rhs.sliceInfo, rhs.dnn);
  }
  inline bool operator>=(const RouteSelectionDescriptor& rhs) const {
    return std::tie(precedence, sessionType, sscMode, sliceInfo, dnn) >= std::tie(rhs.precedence, rhs.sessionType, rhs.sscMode, rhs.sliceInfo, rhs.dnn);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int8_t { SSC_MODE_UNKNOWN = -1 };
  enum : int8_t { SSC_MODE_1 = 1 };
  enum : int8_t { SSC_MODE_2 = 2 };
  enum : int8_t { SSC_MODE_3 = 3 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "RouteSelectionDescriptor{";
    os << "precedence: " << ::android::internal::ToString(precedence);
    os << ", sessionType: " << ::android::internal::ToString(sessionType);
    os << ", sscMode: " << ::android::internal::ToString(sscMode);
    os << ", sliceInfo: " << ::android::internal::ToString(sliceInfo);
    os << ", dnn: " << ::android::internal::ToString(dnn);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
