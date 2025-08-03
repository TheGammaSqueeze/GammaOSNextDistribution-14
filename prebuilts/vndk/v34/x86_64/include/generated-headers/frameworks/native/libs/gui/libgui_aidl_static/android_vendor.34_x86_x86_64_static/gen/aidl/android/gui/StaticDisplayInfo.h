#pragma once

#include <android/binder_to_string.h>
#include <android/gui/DeviceProductInfo.h>
#include <android/gui/DisplayConnectionType.h>
#include <android/gui/Rotation.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <optional>
#include <tuple>
#include <utils/String16.h>

namespace android::gui {
class DeviceProductInfo;
}  // namespace android::gui
namespace android {
namespace gui {
class StaticDisplayInfo : public ::android::Parcelable {
public:
  ::android::gui::DisplayConnectionType connectionType = ::android::gui::DisplayConnectionType::Internal;
  float density = 0.000000f;
  bool secure = false;
  ::std::optional<::android::gui::DeviceProductInfo> deviceProductInfo;
  ::android::gui::Rotation installOrientation = ::android::gui::Rotation::Rotation0;
  inline bool operator!=(const StaticDisplayInfo& rhs) const {
    return std::tie(connectionType, density, secure, deviceProductInfo, installOrientation) != std::tie(rhs.connectionType, rhs.density, rhs.secure, rhs.deviceProductInfo, rhs.installOrientation);
  }
  inline bool operator<(const StaticDisplayInfo& rhs) const {
    return std::tie(connectionType, density, secure, deviceProductInfo, installOrientation) < std::tie(rhs.connectionType, rhs.density, rhs.secure, rhs.deviceProductInfo, rhs.installOrientation);
  }
  inline bool operator<=(const StaticDisplayInfo& rhs) const {
    return std::tie(connectionType, density, secure, deviceProductInfo, installOrientation) <= std::tie(rhs.connectionType, rhs.density, rhs.secure, rhs.deviceProductInfo, rhs.installOrientation);
  }
  inline bool operator==(const StaticDisplayInfo& rhs) const {
    return std::tie(connectionType, density, secure, deviceProductInfo, installOrientation) == std::tie(rhs.connectionType, rhs.density, rhs.secure, rhs.deviceProductInfo, rhs.installOrientation);
  }
  inline bool operator>(const StaticDisplayInfo& rhs) const {
    return std::tie(connectionType, density, secure, deviceProductInfo, installOrientation) > std::tie(rhs.connectionType, rhs.density, rhs.secure, rhs.deviceProductInfo, rhs.installOrientation);
  }
  inline bool operator>=(const StaticDisplayInfo& rhs) const {
    return std::tie(connectionType, density, secure, deviceProductInfo, installOrientation) >= std::tie(rhs.connectionType, rhs.density, rhs.secure, rhs.deviceProductInfo, rhs.installOrientation);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.StaticDisplayInfo");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "StaticDisplayInfo{";
    os << "connectionType: " << ::android::internal::ToString(connectionType);
    os << ", density: " << ::android::internal::ToString(density);
    os << ", secure: " << ::android::internal::ToString(secure);
    os << ", deviceProductInfo: " << ::android::internal::ToString(deviceProductInfo);
    os << ", installOrientation: " << ::android::internal::ToString(installOrientation);
    os << "}";
    return os.str();
  }
};  // class StaticDisplayInfo
}  // namespace gui
}  // namespace android
