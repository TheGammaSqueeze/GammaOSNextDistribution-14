#pragma once

#include <android/binder_to_string.h>
#include <android/gui/Size.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>
#include <vector>

namespace android::gui {
class Size;
}  // namespace android::gui
namespace android {
namespace gui {
class DisplayMode : public ::android::Parcelable {
public:
  int32_t id = 0;
  ::android::gui::Size resolution;
  float xDpi = 0.000000f;
  float yDpi = 0.000000f;
  ::std::vector<int32_t> supportedHdrTypes;
  float refreshRate = 0.000000f;
  int64_t appVsyncOffset = 0L;
  int64_t sfVsyncOffset = 0L;
  int64_t presentationDeadline = 0L;
  int32_t group = -1;
  inline bool operator!=(const DisplayMode& rhs) const {
    return std::tie(id, resolution, xDpi, yDpi, supportedHdrTypes, refreshRate, appVsyncOffset, sfVsyncOffset, presentationDeadline, group) != std::tie(rhs.id, rhs.resolution, rhs.xDpi, rhs.yDpi, rhs.supportedHdrTypes, rhs.refreshRate, rhs.appVsyncOffset, rhs.sfVsyncOffset, rhs.presentationDeadline, rhs.group);
  }
  inline bool operator<(const DisplayMode& rhs) const {
    return std::tie(id, resolution, xDpi, yDpi, supportedHdrTypes, refreshRate, appVsyncOffset, sfVsyncOffset, presentationDeadline, group) < std::tie(rhs.id, rhs.resolution, rhs.xDpi, rhs.yDpi, rhs.supportedHdrTypes, rhs.refreshRate, rhs.appVsyncOffset, rhs.sfVsyncOffset, rhs.presentationDeadline, rhs.group);
  }
  inline bool operator<=(const DisplayMode& rhs) const {
    return std::tie(id, resolution, xDpi, yDpi, supportedHdrTypes, refreshRate, appVsyncOffset, sfVsyncOffset, presentationDeadline, group) <= std::tie(rhs.id, rhs.resolution, rhs.xDpi, rhs.yDpi, rhs.supportedHdrTypes, rhs.refreshRate, rhs.appVsyncOffset, rhs.sfVsyncOffset, rhs.presentationDeadline, rhs.group);
  }
  inline bool operator==(const DisplayMode& rhs) const {
    return std::tie(id, resolution, xDpi, yDpi, supportedHdrTypes, refreshRate, appVsyncOffset, sfVsyncOffset, presentationDeadline, group) == std::tie(rhs.id, rhs.resolution, rhs.xDpi, rhs.yDpi, rhs.supportedHdrTypes, rhs.refreshRate, rhs.appVsyncOffset, rhs.sfVsyncOffset, rhs.presentationDeadline, rhs.group);
  }
  inline bool operator>(const DisplayMode& rhs) const {
    return std::tie(id, resolution, xDpi, yDpi, supportedHdrTypes, refreshRate, appVsyncOffset, sfVsyncOffset, presentationDeadline, group) > std::tie(rhs.id, rhs.resolution, rhs.xDpi, rhs.yDpi, rhs.supportedHdrTypes, rhs.refreshRate, rhs.appVsyncOffset, rhs.sfVsyncOffset, rhs.presentationDeadline, rhs.group);
  }
  inline bool operator>=(const DisplayMode& rhs) const {
    return std::tie(id, resolution, xDpi, yDpi, supportedHdrTypes, refreshRate, appVsyncOffset, sfVsyncOffset, presentationDeadline, group) >= std::tie(rhs.id, rhs.resolution, rhs.xDpi, rhs.yDpi, rhs.supportedHdrTypes, rhs.refreshRate, rhs.appVsyncOffset, rhs.sfVsyncOffset, rhs.presentationDeadline, rhs.group);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayMode");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayMode{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", resolution: " << ::android::internal::ToString(resolution);
    os << ", xDpi: " << ::android::internal::ToString(xDpi);
    os << ", yDpi: " << ::android::internal::ToString(yDpi);
    os << ", supportedHdrTypes: " << ::android::internal::ToString(supportedHdrTypes);
    os << ", refreshRate: " << ::android::internal::ToString(refreshRate);
    os << ", appVsyncOffset: " << ::android::internal::ToString(appVsyncOffset);
    os << ", sfVsyncOffset: " << ::android::internal::ToString(sfVsyncOffset);
    os << ", presentationDeadline: " << ::android::internal::ToString(presentationDeadline);
    os << ", group: " << ::android::internal::ToString(group);
    os << "}";
    return os.str();
  }
};  // class DisplayMode
}  // namespace gui
}  // namespace android
