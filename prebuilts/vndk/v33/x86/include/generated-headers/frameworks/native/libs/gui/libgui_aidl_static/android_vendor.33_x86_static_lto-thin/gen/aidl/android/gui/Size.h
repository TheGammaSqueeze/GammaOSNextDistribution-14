#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class Size : public ::android::Parcelable {
public:
  int32_t width = -1;
  int32_t height = -1;
  inline bool operator!=(const Size& rhs) const {
    return std::tie(width, height) != std::tie(rhs.width, rhs.height);
  }
  inline bool operator<(const Size& rhs) const {
    return std::tie(width, height) < std::tie(rhs.width, rhs.height);
  }
  inline bool operator<=(const Size& rhs) const {
    return std::tie(width, height) <= std::tie(rhs.width, rhs.height);
  }
  inline bool operator==(const Size& rhs) const {
    return std::tie(width, height) == std::tie(rhs.width, rhs.height);
  }
  inline bool operator>(const Size& rhs) const {
    return std::tie(width, height) > std::tie(rhs.width, rhs.height);
  }
  inline bool operator>=(const Size& rhs) const {
    return std::tie(width, height) >= std::tie(rhs.width, rhs.height);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.gui.Size");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "Size{";
    os << "width: " << ::android::internal::ToString(width);
    os << ", height: " << ::android::internal::ToString(height);
    os << "}";
    return os.str();
  }
};  // class Size
}  // namespace gui
}  // namespace android
