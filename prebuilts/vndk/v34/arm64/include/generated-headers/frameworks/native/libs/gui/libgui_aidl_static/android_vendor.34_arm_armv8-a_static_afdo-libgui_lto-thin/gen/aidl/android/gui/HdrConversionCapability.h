#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class HdrConversionCapability : public ::android::Parcelable {
public:
  int32_t sourceType = 0;
  int32_t outputType = 0;
  bool addsLatency = false;
  inline bool operator!=(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) != std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator<(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) < std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator<=(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) <= std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator==(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) == std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator>(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) > std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator>=(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) >= std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.HdrConversionCapability");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "HdrConversionCapability{";
    os << "sourceType: " << ::android::internal::ToString(sourceType);
    os << ", outputType: " << ::android::internal::ToString(outputType);
    os << ", addsLatency: " << ::android::internal::ToString(addsLatency);
    os << "}";
    return os.str();
  }
};  // class HdrConversionCapability
}  // namespace gui
}  // namespace android
