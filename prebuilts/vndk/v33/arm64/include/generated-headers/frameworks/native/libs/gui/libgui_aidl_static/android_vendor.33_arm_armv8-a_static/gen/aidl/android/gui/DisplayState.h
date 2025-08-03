#pragma once

#include <android/binder_to_string.h>
#include <android/gui/Rotation.h>
#include <android/gui/Size.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class DisplayState : public ::android::Parcelable {
public:
  int32_t layerStack = 0;
  ::android::gui::Rotation orientation = ::android::gui::Rotation::Rotation0;
  ::android::gui::Size layerStackSpaceRect;
  inline bool operator!=(const DisplayState& rhs) const {
    return std::tie(layerStack, orientation, layerStackSpaceRect) != std::tie(rhs.layerStack, rhs.orientation, rhs.layerStackSpaceRect);
  }
  inline bool operator<(const DisplayState& rhs) const {
    return std::tie(layerStack, orientation, layerStackSpaceRect) < std::tie(rhs.layerStack, rhs.orientation, rhs.layerStackSpaceRect);
  }
  inline bool operator<=(const DisplayState& rhs) const {
    return std::tie(layerStack, orientation, layerStackSpaceRect) <= std::tie(rhs.layerStack, rhs.orientation, rhs.layerStackSpaceRect);
  }
  inline bool operator==(const DisplayState& rhs) const {
    return std::tie(layerStack, orientation, layerStackSpaceRect) == std::tie(rhs.layerStack, rhs.orientation, rhs.layerStackSpaceRect);
  }
  inline bool operator>(const DisplayState& rhs) const {
    return std::tie(layerStack, orientation, layerStackSpaceRect) > std::tie(rhs.layerStack, rhs.orientation, rhs.layerStackSpaceRect);
  }
  inline bool operator>=(const DisplayState& rhs) const {
    return std::tie(layerStack, orientation, layerStackSpaceRect) >= std::tie(rhs.layerStack, rhs.orientation, rhs.layerStackSpaceRect);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.gui.DisplayState");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayState{";
    os << "layerStack: " << ::android::internal::ToString(layerStack);
    os << ", orientation: " << ::android::internal::ToString(orientation);
    os << ", layerStackSpaceRect: " << ::android::internal::ToString(layerStackSpaceRect);
    os << "}";
    return os.str();
  }
};  // class DisplayState
}  // namespace gui
}  // namespace android
