#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace audio {
namespace common {
class AudioUuid {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t timeLow = 0;
  int32_t timeMid = 0;
  int32_t timeHiAndVersion = 0;
  int32_t clockSeq = 0;
  std::vector<uint8_t> node;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const AudioUuid& rhs) const {
    return std::tie(timeLow, timeMid, timeHiAndVersion, clockSeq, node) != std::tie(rhs.timeLow, rhs.timeMid, rhs.timeHiAndVersion, rhs.clockSeq, rhs.node);
  }
  inline bool operator<(const AudioUuid& rhs) const {
    return std::tie(timeLow, timeMid, timeHiAndVersion, clockSeq, node) < std::tie(rhs.timeLow, rhs.timeMid, rhs.timeHiAndVersion, rhs.clockSeq, rhs.node);
  }
  inline bool operator<=(const AudioUuid& rhs) const {
    return std::tie(timeLow, timeMid, timeHiAndVersion, clockSeq, node) <= std::tie(rhs.timeLow, rhs.timeMid, rhs.timeHiAndVersion, rhs.clockSeq, rhs.node);
  }
  inline bool operator==(const AudioUuid& rhs) const {
    return std::tie(timeLow, timeMid, timeHiAndVersion, clockSeq, node) == std::tie(rhs.timeLow, rhs.timeMid, rhs.timeHiAndVersion, rhs.clockSeq, rhs.node);
  }
  inline bool operator>(const AudioUuid& rhs) const {
    return std::tie(timeLow, timeMid, timeHiAndVersion, clockSeq, node) > std::tie(rhs.timeLow, rhs.timeMid, rhs.timeHiAndVersion, rhs.clockSeq, rhs.node);
  }
  inline bool operator>=(const AudioUuid& rhs) const {
    return std::tie(timeLow, timeMid, timeHiAndVersion, clockSeq, node) >= std::tie(rhs.timeLow, rhs.timeMid, rhs.timeHiAndVersion, rhs.clockSeq, rhs.node);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "AudioUuid{";
    os << "timeLow: " << ::android::internal::ToString(timeLow);
    os << ", timeMid: " << ::android::internal::ToString(timeMid);
    os << ", timeHiAndVersion: " << ::android::internal::ToString(timeHiAndVersion);
    os << ", clockSeq: " << ::android::internal::ToString(clockSeq);
    os << ", node: " << ::android::internal::ToString(node);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace audio
}  // namespace media
}  // namespace android
}  // namespace aidl
