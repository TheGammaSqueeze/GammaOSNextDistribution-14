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
class ConnectionInfo : public ::android::Parcelable {
public:
  ::std::string ipAddress;
  int32_t port = 0;
  inline bool operator!=(const ConnectionInfo& rhs) const {
    return std::tie(ipAddress, port) != std::tie(rhs.ipAddress, rhs.port);
  }
  inline bool operator<(const ConnectionInfo& rhs) const {
    return std::tie(ipAddress, port) < std::tie(rhs.ipAddress, rhs.port);
  }
  inline bool operator<=(const ConnectionInfo& rhs) const {
    return std::tie(ipAddress, port) <= std::tie(rhs.ipAddress, rhs.port);
  }
  inline bool operator==(const ConnectionInfo& rhs) const {
    return std::tie(ipAddress, port) == std::tie(rhs.ipAddress, rhs.port);
  }
  inline bool operator>(const ConnectionInfo& rhs) const {
    return std::tie(ipAddress, port) > std::tie(rhs.ipAddress, rhs.port);
  }
  inline bool operator>=(const ConnectionInfo& rhs) const {
    return std::tie(ipAddress, port) >= std::tie(rhs.ipAddress, rhs.port);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.os.ConnectionInfo");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "ConnectionInfo{";
    os << "ipAddress: " << ::android::internal::ToString(ipAddress);
    os << ", port: " << ::android::internal::ToString(port);
    os << "}";
    return os.str();
  }
};  // class ConnectionInfo
}  // namespace os
}  // namespace android
