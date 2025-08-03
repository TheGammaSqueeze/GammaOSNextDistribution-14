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
class OfflineStream {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  int32_t numOutstandingBuffers = 0;
  std::vector<int64_t> circulatingBufferIds;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OfflineStream& rhs) const {
    return std::tie(id, numOutstandingBuffers, circulatingBufferIds) != std::tie(rhs.id, rhs.numOutstandingBuffers, rhs.circulatingBufferIds);
  }
  inline bool operator<(const OfflineStream& rhs) const {
    return std::tie(id, numOutstandingBuffers, circulatingBufferIds) < std::tie(rhs.id, rhs.numOutstandingBuffers, rhs.circulatingBufferIds);
  }
  inline bool operator<=(const OfflineStream& rhs) const {
    return std::tie(id, numOutstandingBuffers, circulatingBufferIds) <= std::tie(rhs.id, rhs.numOutstandingBuffers, rhs.circulatingBufferIds);
  }
  inline bool operator==(const OfflineStream& rhs) const {
    return std::tie(id, numOutstandingBuffers, circulatingBufferIds) == std::tie(rhs.id, rhs.numOutstandingBuffers, rhs.circulatingBufferIds);
  }
  inline bool operator>(const OfflineStream& rhs) const {
    return std::tie(id, numOutstandingBuffers, circulatingBufferIds) > std::tie(rhs.id, rhs.numOutstandingBuffers, rhs.circulatingBufferIds);
  }
  inline bool operator>=(const OfflineStream& rhs) const {
    return std::tie(id, numOutstandingBuffers, circulatingBufferIds) >= std::tie(rhs.id, rhs.numOutstandingBuffers, rhs.circulatingBufferIds);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OfflineStream{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", numOutstandingBuffers: " << ::android::internal::ToString(numOutstandingBuffers);
    os << ", circulatingBufferIds: " << ::android::internal::ToString(circulatingBufferIds);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
