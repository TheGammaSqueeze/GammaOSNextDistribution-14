#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class Color : public ::android::Parcelable {
public:
  float r = 0.000000f;
  float g = 0.000000f;
  float b = 0.000000f;
  float a = 0.000000f;
  inline bool operator!=(const Color& rhs) const {
    return std::tie(r, g, b, a) != std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator<(const Color& rhs) const {
    return std::tie(r, g, b, a) < std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator<=(const Color& rhs) const {
    return std::tie(r, g, b, a) <= std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator==(const Color& rhs) const {
    return std::tie(r, g, b, a) == std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator>(const Color& rhs) const {
    return std::tie(r, g, b, a) > std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }
  inline bool operator>=(const Color& rhs) const {
    return std::tie(r, g, b, a) >= std::tie(rhs.r, rhs.g, rhs.b, rhs.a);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.Color");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "Color{";
    os << "r: " << ::android::internal::ToString(r);
    os << ", g: " << ::android::internal::ToString(g);
    os << ", b: " << ::android::internal::ToString(b);
    os << ", a: " << ::android::internal::ToString(a);
    os << "}";
    return os.str();
  }
};  // class Color
}  // namespace gui
}  // namespace android
