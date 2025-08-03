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
class DisplayedFrameStats : public ::android::Parcelable {
public:
  int64_t numFrames = 0L;
  ::std::vector<int64_t> component_0_sample;
  ::std::vector<int64_t> component_1_sample;
  ::std::vector<int64_t> component_2_sample;
  ::std::vector<int64_t> component_3_sample;
  inline bool operator!=(const DisplayedFrameStats& rhs) const {
    return std::tie(numFrames, component_0_sample, component_1_sample, component_2_sample, component_3_sample) != std::tie(rhs.numFrames, rhs.component_0_sample, rhs.component_1_sample, rhs.component_2_sample, rhs.component_3_sample);
  }
  inline bool operator<(const DisplayedFrameStats& rhs) const {
    return std::tie(numFrames, component_0_sample, component_1_sample, component_2_sample, component_3_sample) < std::tie(rhs.numFrames, rhs.component_0_sample, rhs.component_1_sample, rhs.component_2_sample, rhs.component_3_sample);
  }
  inline bool operator<=(const DisplayedFrameStats& rhs) const {
    return std::tie(numFrames, component_0_sample, component_1_sample, component_2_sample, component_3_sample) <= std::tie(rhs.numFrames, rhs.component_0_sample, rhs.component_1_sample, rhs.component_2_sample, rhs.component_3_sample);
  }
  inline bool operator==(const DisplayedFrameStats& rhs) const {
    return std::tie(numFrames, component_0_sample, component_1_sample, component_2_sample, component_3_sample) == std::tie(rhs.numFrames, rhs.component_0_sample, rhs.component_1_sample, rhs.component_2_sample, rhs.component_3_sample);
  }
  inline bool operator>(const DisplayedFrameStats& rhs) const {
    return std::tie(numFrames, component_0_sample, component_1_sample, component_2_sample, component_3_sample) > std::tie(rhs.numFrames, rhs.component_0_sample, rhs.component_1_sample, rhs.component_2_sample, rhs.component_3_sample);
  }
  inline bool operator>=(const DisplayedFrameStats& rhs) const {
    return std::tie(numFrames, component_0_sample, component_1_sample, component_2_sample, component_3_sample) >= std::tie(rhs.numFrames, rhs.component_0_sample, rhs.component_1_sample, rhs.component_2_sample, rhs.component_3_sample);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.DisplayedFrameStats");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayedFrameStats{";
    os << "numFrames: " << ::android::internal::ToString(numFrames);
    os << ", component_0_sample: " << ::android::internal::ToString(component_0_sample);
    os << ", component_1_sample: " << ::android::internal::ToString(component_1_sample);
    os << ", component_2_sample: " << ::android::internal::ToString(component_2_sample);
    os << ", component_3_sample: " << ::android::internal::ToString(component_3_sample);
    os << "}";
    return os.str();
  }
};  // class DisplayedFrameStats
}  // namespace gui
}  // namespace android
