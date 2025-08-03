#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace gui {
class FrameTimelineInfo : public ::android::Parcelable {
public:
  int64_t vsyncId = -1L;
  int32_t inputEventId = 0;
  int64_t startTimeNanos = 0L;
  bool useForRefreshRateSelection = false;
  inline bool operator!=(const FrameTimelineInfo& rhs) const {
    return std::tie(vsyncId, inputEventId, startTimeNanos, useForRefreshRateSelection) != std::tie(rhs.vsyncId, rhs.inputEventId, rhs.startTimeNanos, rhs.useForRefreshRateSelection);
  }
  inline bool operator<(const FrameTimelineInfo& rhs) const {
    return std::tie(vsyncId, inputEventId, startTimeNanos, useForRefreshRateSelection) < std::tie(rhs.vsyncId, rhs.inputEventId, rhs.startTimeNanos, rhs.useForRefreshRateSelection);
  }
  inline bool operator<=(const FrameTimelineInfo& rhs) const {
    return std::tie(vsyncId, inputEventId, startTimeNanos, useForRefreshRateSelection) <= std::tie(rhs.vsyncId, rhs.inputEventId, rhs.startTimeNanos, rhs.useForRefreshRateSelection);
  }
  inline bool operator==(const FrameTimelineInfo& rhs) const {
    return std::tie(vsyncId, inputEventId, startTimeNanos, useForRefreshRateSelection) == std::tie(rhs.vsyncId, rhs.inputEventId, rhs.startTimeNanos, rhs.useForRefreshRateSelection);
  }
  inline bool operator>(const FrameTimelineInfo& rhs) const {
    return std::tie(vsyncId, inputEventId, startTimeNanos, useForRefreshRateSelection) > std::tie(rhs.vsyncId, rhs.inputEventId, rhs.startTimeNanos, rhs.useForRefreshRateSelection);
  }
  inline bool operator>=(const FrameTimelineInfo& rhs) const {
    return std::tie(vsyncId, inputEventId, startTimeNanos, useForRefreshRateSelection) >= std::tie(rhs.vsyncId, rhs.inputEventId, rhs.startTimeNanos, rhs.useForRefreshRateSelection);
  }

  enum : int64_t { INVALID_VSYNC_ID = -1L };
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.gui.FrameTimelineInfo");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "FrameTimelineInfo{";
    os << "vsyncId: " << ::android::internal::ToString(vsyncId);
    os << ", inputEventId: " << ::android::internal::ToString(inputEventId);
    os << ", startTimeNanos: " << ::android::internal::ToString(startTimeNanos);
    os << ", useForRefreshRateSelection: " << ::android::internal::ToString(useForRefreshRateSelection);
    os << "}";
    return os.str();
  }
};  // class FrameTimelineInfo
}  // namespace gui
}  // namespace android
