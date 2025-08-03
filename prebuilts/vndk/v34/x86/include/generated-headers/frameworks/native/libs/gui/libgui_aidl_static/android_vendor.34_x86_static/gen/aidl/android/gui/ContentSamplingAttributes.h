#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class ContentSamplingAttributes : public ::android::Parcelable {
public:
  int32_t format = 0;
  int32_t dataspace = 0;
  int8_t componentMask = 0;
  inline bool operator!=(const ContentSamplingAttributes& rhs) const {
    return std::tie(format, dataspace, componentMask) != std::tie(rhs.format, rhs.dataspace, rhs.componentMask);
  }
  inline bool operator<(const ContentSamplingAttributes& rhs) const {
    return std::tie(format, dataspace, componentMask) < std::tie(rhs.format, rhs.dataspace, rhs.componentMask);
  }
  inline bool operator<=(const ContentSamplingAttributes& rhs) const {
    return std::tie(format, dataspace, componentMask) <= std::tie(rhs.format, rhs.dataspace, rhs.componentMask);
  }
  inline bool operator==(const ContentSamplingAttributes& rhs) const {
    return std::tie(format, dataspace, componentMask) == std::tie(rhs.format, rhs.dataspace, rhs.componentMask);
  }
  inline bool operator>(const ContentSamplingAttributes& rhs) const {
    return std::tie(format, dataspace, componentMask) > std::tie(rhs.format, rhs.dataspace, rhs.componentMask);
  }
  inline bool operator>=(const ContentSamplingAttributes& rhs) const {
    return std::tie(format, dataspace, componentMask) >= std::tie(rhs.format, rhs.dataspace, rhs.componentMask);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.ContentSamplingAttributes");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "ContentSamplingAttributes{";
    os << "format: " << ::android::internal::ToString(format);
    os << ", dataspace: " << ::android::internal::ToString(dataspace);
    os << ", componentMask: " << ::android::internal::ToString(componentMask);
    os << "}";
    return os.str();
  }
};  // class ContentSamplingAttributes
}  // namespace gui
}  // namespace android
