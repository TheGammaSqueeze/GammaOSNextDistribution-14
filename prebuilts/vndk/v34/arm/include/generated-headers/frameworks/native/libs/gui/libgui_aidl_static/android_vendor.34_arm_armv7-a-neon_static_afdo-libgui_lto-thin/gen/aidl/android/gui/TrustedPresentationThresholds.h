#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class TrustedPresentationThresholds : public ::android::Parcelable {
public:
  float minAlpha = -1.000000f;
  float minFractionRendered = -1.000000f;
  int32_t stabilityRequirementMs = 0;
  inline bool operator!=(const TrustedPresentationThresholds& rhs) const {
    return std::tie(minAlpha, minFractionRendered, stabilityRequirementMs) != std::tie(rhs.minAlpha, rhs.minFractionRendered, rhs.stabilityRequirementMs);
  }
  inline bool operator<(const TrustedPresentationThresholds& rhs) const {
    return std::tie(minAlpha, minFractionRendered, stabilityRequirementMs) < std::tie(rhs.minAlpha, rhs.minFractionRendered, rhs.stabilityRequirementMs);
  }
  inline bool operator<=(const TrustedPresentationThresholds& rhs) const {
    return std::tie(minAlpha, minFractionRendered, stabilityRequirementMs) <= std::tie(rhs.minAlpha, rhs.minFractionRendered, rhs.stabilityRequirementMs);
  }
  inline bool operator==(const TrustedPresentationThresholds& rhs) const {
    return std::tie(minAlpha, minFractionRendered, stabilityRequirementMs) == std::tie(rhs.minAlpha, rhs.minFractionRendered, rhs.stabilityRequirementMs);
  }
  inline bool operator>(const TrustedPresentationThresholds& rhs) const {
    return std::tie(minAlpha, minFractionRendered, stabilityRequirementMs) > std::tie(rhs.minAlpha, rhs.minFractionRendered, rhs.stabilityRequirementMs);
  }
  inline bool operator>=(const TrustedPresentationThresholds& rhs) const {
    return std::tie(minAlpha, minFractionRendered, stabilityRequirementMs) >= std::tie(rhs.minAlpha, rhs.minFractionRendered, rhs.stabilityRequirementMs);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.TrustedPresentationThresholds");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "TrustedPresentationThresholds{";
    os << "minAlpha: " << ::android::internal::ToString(minAlpha);
    os << ", minFractionRendered: " << ::android::internal::ToString(minFractionRendered);
    os << ", stabilityRequirementMs: " << ::android::internal::ToString(stabilityRequirementMs);
    os << "}";
    return os.str();
  }
};  // class TrustedPresentationThresholds
}  // namespace gui
}  // namespace android
