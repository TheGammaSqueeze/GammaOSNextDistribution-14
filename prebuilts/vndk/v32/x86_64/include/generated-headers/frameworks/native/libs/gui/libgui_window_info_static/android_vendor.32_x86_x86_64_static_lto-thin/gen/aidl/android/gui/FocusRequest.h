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

class FocusRequest : public ::android::Parcelable {
public:
  inline bool operator!=(const FocusRequest& rhs) const {
    return std::tie(token, windowName, focusedToken, focusedWindowName, timestamp, displayId) != std::tie(rhs.token, rhs.windowName, rhs.focusedToken, rhs.focusedWindowName, rhs.timestamp, rhs.displayId);
  }
  inline bool operator<(const FocusRequest& rhs) const {
    return std::tie(token, windowName, focusedToken, focusedWindowName, timestamp, displayId) < std::tie(rhs.token, rhs.windowName, rhs.focusedToken, rhs.focusedWindowName, rhs.timestamp, rhs.displayId);
  }
  inline bool operator<=(const FocusRequest& rhs) const {
    return std::tie(token, windowName, focusedToken, focusedWindowName, timestamp, displayId) <= std::tie(rhs.token, rhs.windowName, rhs.focusedToken, rhs.focusedWindowName, rhs.timestamp, rhs.displayId);
  }
  inline bool operator==(const FocusRequest& rhs) const {
    return std::tie(token, windowName, focusedToken, focusedWindowName, timestamp, displayId) == std::tie(rhs.token, rhs.windowName, rhs.focusedToken, rhs.focusedWindowName, rhs.timestamp, rhs.displayId);
  }
  inline bool operator>(const FocusRequest& rhs) const {
    return std::tie(token, windowName, focusedToken, focusedWindowName, timestamp, displayId) > std::tie(rhs.token, rhs.windowName, rhs.focusedToken, rhs.focusedWindowName, rhs.timestamp, rhs.displayId);
  }
  inline bool operator>=(const FocusRequest& rhs) const {
    return std::tie(token, windowName, focusedToken, focusedWindowName, timestamp, displayId) >= std::tie(rhs.token, rhs.windowName, rhs.focusedToken, rhs.focusedWindowName, rhs.timestamp, rhs.displayId);
  }

  ::android::sp<::android::IBinder> token;
  ::std::string windowName;
  ::android::sp<::android::IBinder> focusedToken;
  ::std::string focusedWindowName;
  int64_t timestamp = int64_t(0L);
  int32_t displayId = int32_t(0);
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.gui.FocusRequest");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "FocusRequest{";
    os << "token: " << ::android::internal::ToString(token);
    os << ", windowName: " << ::android::internal::ToString(windowName);
    os << ", focusedToken: " << ::android::internal::ToString(focusedToken);
    os << ", focusedWindowName: " << ::android::internal::ToString(focusedWindowName);
    os << ", timestamp: " << ::android::internal::ToString(timestamp);
    os << ", displayId: " << ::android::internal::ToString(displayId);
    os << "}";
    return os.str();
  }
};  // class FocusRequest

}  // namespace gui

}  // namespace android
