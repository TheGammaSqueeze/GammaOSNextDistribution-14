#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class DisplayStatInfo : public ::android::Parcelable {
public:
  int64_t vsyncTime = 0L;
  int64_t vsyncPeriod = 0L;
  inline bool operator!=(const DisplayStatInfo& rhs) const {
    return std::tie(vsyncTime, vsyncPeriod) != std::tie(rhs.vsyncTime, rhs.vsyncPeriod);
  }
  inline bool operator<(const DisplayStatInfo& rhs) const {
    return std::tie(vsyncTime, vsyncPeriod) < std::tie(rhs.vsyncTime, rhs.vsyncPeriod);
  }
  inline bool operator<=(const DisplayStatInfo& rhs) const {
    return std::tie(vsyncTime, vsyncPeriod) <= std::tie(rhs.vsyncTime, rhs.vsyncPeriod);
  }
  inline bool operator==(const DisplayStatInfo& rhs) const {
    return std::tie(vsyncTime, vsyncPeriod) == std::tie(rhs.vsyncTime, rhs.vsyncPeriod);
  }
  inline bool operator>(const DisplayStatInfo& rhs) const {
    return std::tie(vsyncTime, vsyncPeriod) > std::tie(rhs.vsyncTime, rhs.vsyncPeriod);
  }
  inline bool operator>=(const DisplayStatInfo& rhs) const {
    return std::tie(vsyncTime, vsyncPeriod) >= std::tie(rhs.vsyncTime, rhs.vsyncPeriod);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.gui.DisplayStatInfo");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayStatInfo{";
    os << "vsyncTime: " << ::android::internal::ToString(vsyncTime);
    os << ", vsyncPeriod: " << ::android::internal::ToString(vsyncPeriod);
    os << "}";
    return os.str();
  }
};  // class DisplayStatInfo
}  // namespace gui
}  // namespace android
