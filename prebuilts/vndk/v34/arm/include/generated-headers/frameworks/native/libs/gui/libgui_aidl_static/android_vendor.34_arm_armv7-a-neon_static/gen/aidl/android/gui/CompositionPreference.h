#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class CompositionPreference : public ::android::Parcelable {
public:
  int32_t defaultDataspace = 0;
  int32_t defaultPixelFormat = 0;
  int32_t wideColorGamutDataspace = 0;
  int32_t wideColorGamutPixelFormat = 0;
  inline bool operator!=(const CompositionPreference& rhs) const {
    return std::tie(defaultDataspace, defaultPixelFormat, wideColorGamutDataspace, wideColorGamutPixelFormat) != std::tie(rhs.defaultDataspace, rhs.defaultPixelFormat, rhs.wideColorGamutDataspace, rhs.wideColorGamutPixelFormat);
  }
  inline bool operator<(const CompositionPreference& rhs) const {
    return std::tie(defaultDataspace, defaultPixelFormat, wideColorGamutDataspace, wideColorGamutPixelFormat) < std::tie(rhs.defaultDataspace, rhs.defaultPixelFormat, rhs.wideColorGamutDataspace, rhs.wideColorGamutPixelFormat);
  }
  inline bool operator<=(const CompositionPreference& rhs) const {
    return std::tie(defaultDataspace, defaultPixelFormat, wideColorGamutDataspace, wideColorGamutPixelFormat) <= std::tie(rhs.defaultDataspace, rhs.defaultPixelFormat, rhs.wideColorGamutDataspace, rhs.wideColorGamutPixelFormat);
  }
  inline bool operator==(const CompositionPreference& rhs) const {
    return std::tie(defaultDataspace, defaultPixelFormat, wideColorGamutDataspace, wideColorGamutPixelFormat) == std::tie(rhs.defaultDataspace, rhs.defaultPixelFormat, rhs.wideColorGamutDataspace, rhs.wideColorGamutPixelFormat);
  }
  inline bool operator>(const CompositionPreference& rhs) const {
    return std::tie(defaultDataspace, defaultPixelFormat, wideColorGamutDataspace, wideColorGamutPixelFormat) > std::tie(rhs.defaultDataspace, rhs.defaultPixelFormat, rhs.wideColorGamutDataspace, rhs.wideColorGamutPixelFormat);
  }
  inline bool operator>=(const CompositionPreference& rhs) const {
    return std::tie(defaultDataspace, defaultPixelFormat, wideColorGamutDataspace, wideColorGamutPixelFormat) >= std::tie(rhs.defaultDataspace, rhs.defaultPixelFormat, rhs.wideColorGamutDataspace, rhs.wideColorGamutPixelFormat);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.CompositionPreference");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "CompositionPreference{";
    os << "defaultDataspace: " << ::android::internal::ToString(defaultDataspace);
    os << ", defaultPixelFormat: " << ::android::internal::ToString(defaultPixelFormat);
    os << ", wideColorGamutDataspace: " << ::android::internal::ToString(wideColorGamutDataspace);
    os << ", wideColorGamutPixelFormat: " << ::android::internal::ToString(wideColorGamutPixelFormat);
    os << "}";
    return os.str();
  }
};  // class CompositionPreference
}  // namespace gui
}  // namespace android
