#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <tuple>
#include <utils/String16.h>

namespace android {

namespace gui {

class DisplayBrightness : public ::android::Parcelable {
public:
  inline bool operator!=(const DisplayBrightness& rhs) const {
    return std::tie(sdrWhitePoint, sdrWhitePointNits, displayBrightness, displayBrightnessNits) != std::tie(rhs.sdrWhitePoint, rhs.sdrWhitePointNits, rhs.displayBrightness, rhs.displayBrightnessNits);
  }
  inline bool operator<(const DisplayBrightness& rhs) const {
    return std::tie(sdrWhitePoint, sdrWhitePointNits, displayBrightness, displayBrightnessNits) < std::tie(rhs.sdrWhitePoint, rhs.sdrWhitePointNits, rhs.displayBrightness, rhs.displayBrightnessNits);
  }
  inline bool operator<=(const DisplayBrightness& rhs) const {
    return std::tie(sdrWhitePoint, sdrWhitePointNits, displayBrightness, displayBrightnessNits) <= std::tie(rhs.sdrWhitePoint, rhs.sdrWhitePointNits, rhs.displayBrightness, rhs.displayBrightnessNits);
  }
  inline bool operator==(const DisplayBrightness& rhs) const {
    return std::tie(sdrWhitePoint, sdrWhitePointNits, displayBrightness, displayBrightnessNits) == std::tie(rhs.sdrWhitePoint, rhs.sdrWhitePointNits, rhs.displayBrightness, rhs.displayBrightnessNits);
  }
  inline bool operator>(const DisplayBrightness& rhs) const {
    return std::tie(sdrWhitePoint, sdrWhitePointNits, displayBrightness, displayBrightnessNits) > std::tie(rhs.sdrWhitePoint, rhs.sdrWhitePointNits, rhs.displayBrightness, rhs.displayBrightnessNits);
  }
  inline bool operator>=(const DisplayBrightness& rhs) const {
    return std::tie(sdrWhitePoint, sdrWhitePointNits, displayBrightness, displayBrightnessNits) >= std::tie(rhs.sdrWhitePoint, rhs.sdrWhitePointNits, rhs.displayBrightness, rhs.displayBrightnessNits);
  }

  float sdrWhitePoint = float(0.000000f);
  float sdrWhitePointNits = float(-1.000000f);
  float displayBrightness = float(0.000000f);
  float displayBrightnessNits = float(-1.000000f);
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.gui.DisplayBrightness");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayBrightness{";
    os << "sdrWhitePoint: " << ::android::internal::ToString(sdrWhitePoint);
    os << ", sdrWhitePointNits: " << ::android::internal::ToString(sdrWhitePointNits);
    os << ", displayBrightness: " << ::android::internal::ToString(displayBrightness);
    os << ", displayBrightnessNits: " << ::android::internal::ToString(displayBrightnessNits);
    os << "}";
    return os.str();
  }
};  // class DisplayBrightness

}  // namespace gui

}  // namespace android
