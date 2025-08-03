#pragma once

#include <android/binder_to_string.h>
#include <android/gui/DisplayPrimaries.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class DisplayPrimaries : public ::android::Parcelable {
public:
  class CieXyz : public ::android::Parcelable {
  public:
    float X = 0.000000f;
    float Y = 0.000000f;
    float Z = 0.000000f;
    inline bool operator!=(const CieXyz& rhs) const {
      return std::tie(X, Y, Z) != std::tie(rhs.X, rhs.Y, rhs.Z);
    }
    inline bool operator<(const CieXyz& rhs) const {
      return std::tie(X, Y, Z) < std::tie(rhs.X, rhs.Y, rhs.Z);
    }
    inline bool operator<=(const CieXyz& rhs) const {
      return std::tie(X, Y, Z) <= std::tie(rhs.X, rhs.Y, rhs.Z);
    }
    inline bool operator==(const CieXyz& rhs) const {
      return std::tie(X, Y, Z) == std::tie(rhs.X, rhs.Y, rhs.Z);
    }
    inline bool operator>(const CieXyz& rhs) const {
      return std::tie(X, Y, Z) > std::tie(rhs.X, rhs.Y, rhs.Z);
    }
    inline bool operator>=(const CieXyz& rhs) const {
      return std::tie(X, Y, Z) >= std::tie(rhs.X, rhs.Y, rhs.Z);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayPrimaries.CieXyz");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream os;
      os << "CieXyz{";
      os << "X: " << ::android::internal::ToString(X);
      os << ", Y: " << ::android::internal::ToString(Y);
      os << ", Z: " << ::android::internal::ToString(Z);
      os << "}";
      return os.str();
    }
  };  // class CieXyz
  ::android::gui::DisplayPrimaries::CieXyz red;
  ::android::gui::DisplayPrimaries::CieXyz green;
  ::android::gui::DisplayPrimaries::CieXyz blue;
  ::android::gui::DisplayPrimaries::CieXyz white;
  inline bool operator!=(const DisplayPrimaries& rhs) const {
    return std::tie(red, green, blue, white) != std::tie(rhs.red, rhs.green, rhs.blue, rhs.white);
  }
  inline bool operator<(const DisplayPrimaries& rhs) const {
    return std::tie(red, green, blue, white) < std::tie(rhs.red, rhs.green, rhs.blue, rhs.white);
  }
  inline bool operator<=(const DisplayPrimaries& rhs) const {
    return std::tie(red, green, blue, white) <= std::tie(rhs.red, rhs.green, rhs.blue, rhs.white);
  }
  inline bool operator==(const DisplayPrimaries& rhs) const {
    return std::tie(red, green, blue, white) == std::tie(rhs.red, rhs.green, rhs.blue, rhs.white);
  }
  inline bool operator>(const DisplayPrimaries& rhs) const {
    return std::tie(red, green, blue, white) > std::tie(rhs.red, rhs.green, rhs.blue, rhs.white);
  }
  inline bool operator>=(const DisplayPrimaries& rhs) const {
    return std::tie(red, green, blue, white) >= std::tie(rhs.red, rhs.green, rhs.blue, rhs.white);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayPrimaries");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayPrimaries{";
    os << "red: " << ::android::internal::ToString(red);
    os << ", green: " << ::android::internal::ToString(green);
    os << ", blue: " << ::android::internal::ToString(blue);
    os << ", white: " << ::android::internal::ToString(white);
    os << "}";
    return os.str();
  }
};  // class DisplayPrimaries
}  // namespace gui
}  // namespace android
