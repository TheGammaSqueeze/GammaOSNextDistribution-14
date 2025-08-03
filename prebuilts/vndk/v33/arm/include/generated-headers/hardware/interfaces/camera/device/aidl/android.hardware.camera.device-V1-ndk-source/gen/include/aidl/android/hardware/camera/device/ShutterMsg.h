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
namespace hardware {
namespace camera {
namespace device {
class ShutterMsg {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t frameNumber = 0;
  int64_t timestamp = 0L;
  int64_t readoutTimestamp = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ShutterMsg& rhs) const {
    return std::tie(frameNumber, timestamp, readoutTimestamp) != std::tie(rhs.frameNumber, rhs.timestamp, rhs.readoutTimestamp);
  }
  inline bool operator<(const ShutterMsg& rhs) const {
    return std::tie(frameNumber, timestamp, readoutTimestamp) < std::tie(rhs.frameNumber, rhs.timestamp, rhs.readoutTimestamp);
  }
  inline bool operator<=(const ShutterMsg& rhs) const {
    return std::tie(frameNumber, timestamp, readoutTimestamp) <= std::tie(rhs.frameNumber, rhs.timestamp, rhs.readoutTimestamp);
  }
  inline bool operator==(const ShutterMsg& rhs) const {
    return std::tie(frameNumber, timestamp, readoutTimestamp) == std::tie(rhs.frameNumber, rhs.timestamp, rhs.readoutTimestamp);
  }
  inline bool operator>(const ShutterMsg& rhs) const {
    return std::tie(frameNumber, timestamp, readoutTimestamp) > std::tie(rhs.frameNumber, rhs.timestamp, rhs.readoutTimestamp);
  }
  inline bool operator>=(const ShutterMsg& rhs) const {
    return std::tie(frameNumber, timestamp, readoutTimestamp) >= std::tie(rhs.frameNumber, rhs.timestamp, rhs.readoutTimestamp);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ShutterMsg{";
    os << "frameNumber: " << ::android::internal::ToString(frameNumber);
    os << ", timestamp: " << ::android::internal::ToString(timestamp);
    os << ", readoutTimestamp: " << ::android::internal::ToString(readoutTimestamp);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
