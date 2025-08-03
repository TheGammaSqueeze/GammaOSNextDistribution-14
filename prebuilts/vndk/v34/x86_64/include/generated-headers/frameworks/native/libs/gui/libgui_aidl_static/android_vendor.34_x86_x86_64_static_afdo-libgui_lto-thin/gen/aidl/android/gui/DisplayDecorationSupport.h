#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class DisplayDecorationSupport : public ::android::Parcelable {
public:
  int32_t format = 0;
  int32_t alphaInterpretation = 0;
  inline bool operator!=(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) != std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator<(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) < std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator<=(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) <= std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator==(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) == std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator>(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) > std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator>=(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) >= std::tie(rhs.format, rhs.alphaInterpretation);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayDecorationSupport");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayDecorationSupport{";
    os << "format: " << ::android::internal::ToString(format);
    os << ", alphaInterpretation: " << ::android::internal::ToString(alphaInterpretation);
    os << "}";
    return os.str();
  }
};  // class DisplayDecorationSupport
}  // namespace gui
}  // namespace android
