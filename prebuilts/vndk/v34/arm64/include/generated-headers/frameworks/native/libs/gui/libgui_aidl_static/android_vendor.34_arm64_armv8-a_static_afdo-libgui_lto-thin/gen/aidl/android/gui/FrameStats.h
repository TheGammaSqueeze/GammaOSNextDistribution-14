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
class FrameStats : public ::android::Parcelable {
public:
  int64_t refreshPeriodNano = 0L;
  ::std::vector<int64_t> desiredPresentTimesNano;
  ::std::vector<int64_t> actualPresentTimesNano;
  ::std::vector<int64_t> frameReadyTimesNano;
  inline bool operator!=(const FrameStats& rhs) const {
    return std::tie(refreshPeriodNano, desiredPresentTimesNano, actualPresentTimesNano, frameReadyTimesNano) != std::tie(rhs.refreshPeriodNano, rhs.desiredPresentTimesNano, rhs.actualPresentTimesNano, rhs.frameReadyTimesNano);
  }
  inline bool operator<(const FrameStats& rhs) const {
    return std::tie(refreshPeriodNano, desiredPresentTimesNano, actualPresentTimesNano, frameReadyTimesNano) < std::tie(rhs.refreshPeriodNano, rhs.desiredPresentTimesNano, rhs.actualPresentTimesNano, rhs.frameReadyTimesNano);
  }
  inline bool operator<=(const FrameStats& rhs) const {
    return std::tie(refreshPeriodNano, desiredPresentTimesNano, actualPresentTimesNano, frameReadyTimesNano) <= std::tie(rhs.refreshPeriodNano, rhs.desiredPresentTimesNano, rhs.actualPresentTimesNano, rhs.frameReadyTimesNano);
  }
  inline bool operator==(const FrameStats& rhs) const {
    return std::tie(refreshPeriodNano, desiredPresentTimesNano, actualPresentTimesNano, frameReadyTimesNano) == std::tie(rhs.refreshPeriodNano, rhs.desiredPresentTimesNano, rhs.actualPresentTimesNano, rhs.frameReadyTimesNano);
  }
  inline bool operator>(const FrameStats& rhs) const {
    return std::tie(refreshPeriodNano, desiredPresentTimesNano, actualPresentTimesNano, frameReadyTimesNano) > std::tie(rhs.refreshPeriodNano, rhs.desiredPresentTimesNano, rhs.actualPresentTimesNano, rhs.frameReadyTimesNano);
  }
  inline bool operator>=(const FrameStats& rhs) const {
    return std::tie(refreshPeriodNano, desiredPresentTimesNano, actualPresentTimesNano, frameReadyTimesNano) >= std::tie(rhs.refreshPeriodNano, rhs.desiredPresentTimesNano, rhs.actualPresentTimesNano, rhs.frameReadyTimesNano);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.FrameStats");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "FrameStats{";
    os << "refreshPeriodNano: " << ::android::internal::ToString(refreshPeriodNano);
    os << ", desiredPresentTimesNano: " << ::android::internal::ToString(desiredPresentTimesNano);
    os << ", actualPresentTimesNano: " << ::android::internal::ToString(actualPresentTimesNano);
    os << ", frameReadyTimesNano: " << ::android::internal::ToString(frameReadyTimesNano);
    os << "}";
    return os.str();
  }
};  // class FrameStats
}  // namespace gui
}  // namespace android
