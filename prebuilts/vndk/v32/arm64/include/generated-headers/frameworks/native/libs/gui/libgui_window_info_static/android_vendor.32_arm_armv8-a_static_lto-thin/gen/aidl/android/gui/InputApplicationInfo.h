#pragma once

#include <android/binder_to_string.h>
#include <binder/IBinder.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <tuple>
#include <utils/String16.h>

namespace android {

namespace gui {

class InputApplicationInfo : public ::android::Parcelable {
public:
  inline bool operator!=(const InputApplicationInfo& rhs) const {
    return std::tie(token, name, dispatchingTimeoutMillis) != std::tie(rhs.token, rhs.name, rhs.dispatchingTimeoutMillis);
  }
  inline bool operator<(const InputApplicationInfo& rhs) const {
    return std::tie(token, name, dispatchingTimeoutMillis) < std::tie(rhs.token, rhs.name, rhs.dispatchingTimeoutMillis);
  }
  inline bool operator<=(const InputApplicationInfo& rhs) const {
    return std::tie(token, name, dispatchingTimeoutMillis) <= std::tie(rhs.token, rhs.name, rhs.dispatchingTimeoutMillis);
  }
  inline bool operator==(const InputApplicationInfo& rhs) const {
    return std::tie(token, name, dispatchingTimeoutMillis) == std::tie(rhs.token, rhs.name, rhs.dispatchingTimeoutMillis);
  }
  inline bool operator>(const InputApplicationInfo& rhs) const {
    return std::tie(token, name, dispatchingTimeoutMillis) > std::tie(rhs.token, rhs.name, rhs.dispatchingTimeoutMillis);
  }
  inline bool operator>=(const InputApplicationInfo& rhs) const {
    return std::tie(token, name, dispatchingTimeoutMillis) >= std::tie(rhs.token, rhs.name, rhs.dispatchingTimeoutMillis);
  }

  ::android::sp<::android::IBinder> token;
  ::std::string name;
  int64_t dispatchingTimeoutMillis = int64_t(0L);
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.gui.InputApplicationInfo");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "InputApplicationInfo{";
    os << "token: " << ::android::internal::ToString(token);
    os << ", name: " << ::android::internal::ToString(name);
    os << ", dispatchingTimeoutMillis: " << ::android::internal::ToString(dispatchingTimeoutMillis);
    os << "}";
    return os.str();
  }
};  // class InputApplicationInfo

}  // namespace gui

}  // namespace android
