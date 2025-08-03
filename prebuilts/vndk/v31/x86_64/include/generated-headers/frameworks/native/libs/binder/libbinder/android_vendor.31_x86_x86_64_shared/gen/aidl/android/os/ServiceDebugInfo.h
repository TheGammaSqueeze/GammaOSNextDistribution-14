#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <tuple>
#include <utils/String16.h>

namespace android {

namespace os {

class ServiceDebugInfo : public ::android::Parcelable {
public:
  inline bool operator!=(const ServiceDebugInfo& rhs) const {
    return std::tie(name, debugPid) != std::tie(rhs.name, rhs.debugPid);
  }
  inline bool operator<(const ServiceDebugInfo& rhs) const {
    return std::tie(name, debugPid) < std::tie(rhs.name, rhs.debugPid);
  }
  inline bool operator<=(const ServiceDebugInfo& rhs) const {
    return std::tie(name, debugPid) <= std::tie(rhs.name, rhs.debugPid);
  }
  inline bool operator==(const ServiceDebugInfo& rhs) const {
    return std::tie(name, debugPid) == std::tie(rhs.name, rhs.debugPid);
  }
  inline bool operator>(const ServiceDebugInfo& rhs) const {
    return std::tie(name, debugPid) > std::tie(rhs.name, rhs.debugPid);
  }
  inline bool operator>=(const ServiceDebugInfo& rhs) const {
    return std::tie(name, debugPid) >= std::tie(rhs.name, rhs.debugPid);
  }

  ::std::string name;
  int32_t debugPid = int32_t(0);
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.os.ServiceDebugInfo");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "ServiceDebugInfo{";
    os << "name: " << ::android::internal::ToString(name);
    os << ", debugPid: " << ::android::internal::ToString(debugPid);
    os << "}";
    return os.str();
  }
};  // class ServiceDebugInfo

}  // namespace os

}  // namespace android
