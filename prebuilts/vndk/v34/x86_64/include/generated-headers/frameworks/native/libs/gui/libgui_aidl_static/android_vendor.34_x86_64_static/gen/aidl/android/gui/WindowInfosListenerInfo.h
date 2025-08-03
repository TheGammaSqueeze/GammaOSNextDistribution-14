#pragma once

#include <android/binder_to_string.h>
#include <android/gui/IWindowInfosPublisher.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android::gui {
class IWindowInfosPublisher;
}  // namespace android::gui
namespace android {
namespace gui {
class WindowInfosListenerInfo : public ::android::Parcelable {
public:
  int64_t listenerId = 0L;
  ::android::sp<::android::gui::IWindowInfosPublisher> windowInfosPublisher;
  inline bool operator!=(const WindowInfosListenerInfo& rhs) const {
    return std::tie(listenerId, windowInfosPublisher) != std::tie(rhs.listenerId, rhs.windowInfosPublisher);
  }
  inline bool operator<(const WindowInfosListenerInfo& rhs) const {
    return std::tie(listenerId, windowInfosPublisher) < std::tie(rhs.listenerId, rhs.windowInfosPublisher);
  }
  inline bool operator<=(const WindowInfosListenerInfo& rhs) const {
    return std::tie(listenerId, windowInfosPublisher) <= std::tie(rhs.listenerId, rhs.windowInfosPublisher);
  }
  inline bool operator==(const WindowInfosListenerInfo& rhs) const {
    return std::tie(listenerId, windowInfosPublisher) == std::tie(rhs.listenerId, rhs.windowInfosPublisher);
  }
  inline bool operator>(const WindowInfosListenerInfo& rhs) const {
    return std::tie(listenerId, windowInfosPublisher) > std::tie(rhs.listenerId, rhs.windowInfosPublisher);
  }
  inline bool operator>=(const WindowInfosListenerInfo& rhs) const {
    return std::tie(listenerId, windowInfosPublisher) >= std::tie(rhs.listenerId, rhs.windowInfosPublisher);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.WindowInfosListenerInfo");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "WindowInfosListenerInfo{";
    os << "listenerId: " << ::android::internal::ToString(listenerId);
    os << ", windowInfosPublisher: " << ::android::internal::ToString(windowInfosPublisher);
    os << "}";
    return os.str();
  }
};  // class WindowInfosListenerInfo
}  // namespace gui
}  // namespace android
