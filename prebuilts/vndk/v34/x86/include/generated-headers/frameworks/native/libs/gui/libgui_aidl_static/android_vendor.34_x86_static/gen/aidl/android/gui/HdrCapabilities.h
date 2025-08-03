#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>
#include <vector>

namespace android {
namespace gui {
class HdrCapabilities : public ::android::Parcelable {
public:
  ::std::vector<int32_t> supportedHdrTypes;
  float maxLuminance = 0.000000f;
  float maxAverageLuminance = 0.000000f;
  float minLuminance = 0.000000f;
  inline bool operator!=(const HdrCapabilities& rhs) const {
    return std::tie(supportedHdrTypes, maxLuminance, maxAverageLuminance, minLuminance) != std::tie(rhs.supportedHdrTypes, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator<(const HdrCapabilities& rhs) const {
    return std::tie(supportedHdrTypes, maxLuminance, maxAverageLuminance, minLuminance) < std::tie(rhs.supportedHdrTypes, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator<=(const HdrCapabilities& rhs) const {
    return std::tie(supportedHdrTypes, maxLuminance, maxAverageLuminance, minLuminance) <= std::tie(rhs.supportedHdrTypes, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator==(const HdrCapabilities& rhs) const {
    return std::tie(supportedHdrTypes, maxLuminance, maxAverageLuminance, minLuminance) == std::tie(rhs.supportedHdrTypes, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator>(const HdrCapabilities& rhs) const {
    return std::tie(supportedHdrTypes, maxLuminance, maxAverageLuminance, minLuminance) > std::tie(rhs.supportedHdrTypes, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }
  inline bool operator>=(const HdrCapabilities& rhs) const {
    return std::tie(supportedHdrTypes, maxLuminance, maxAverageLuminance, minLuminance) >= std::tie(rhs.supportedHdrTypes, rhs.maxLuminance, rhs.maxAverageLuminance, rhs.minLuminance);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.HdrCapabilities");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "HdrCapabilities{";
    os << "supportedHdrTypes: " << ::android::internal::ToString(supportedHdrTypes);
    os << ", maxLuminance: " << ::android::internal::ToString(maxLuminance);
    os << ", maxAverageLuminance: " << ::android::internal::ToString(maxAverageLuminance);
    os << ", minLuminance: " << ::android::internal::ToString(minLuminance);
    os << "}";
    return os.str();
  }
};  // class HdrCapabilities
}  // namespace gui
}  // namespace android
