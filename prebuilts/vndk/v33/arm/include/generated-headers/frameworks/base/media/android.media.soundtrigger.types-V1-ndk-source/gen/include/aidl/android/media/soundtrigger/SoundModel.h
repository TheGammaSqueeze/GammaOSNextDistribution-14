#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/media/soundtrigger/SoundModelType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class SoundModel {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::media::soundtrigger::SoundModelType type = ::aidl::android::media::soundtrigger::SoundModelType::INVALID;
  std::string uuid;
  std::string vendorUuid;
  ::ndk::ScopedFileDescriptor data;
  int32_t dataSize = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SoundModel& rhs) const {
    return std::tie(type, uuid, vendorUuid, data, dataSize) != std::tie(rhs.type, rhs.uuid, rhs.vendorUuid, rhs.data, rhs.dataSize);
  }
  inline bool operator<(const SoundModel& rhs) const {
    return std::tie(type, uuid, vendorUuid, data, dataSize) < std::tie(rhs.type, rhs.uuid, rhs.vendorUuid, rhs.data, rhs.dataSize);
  }
  inline bool operator<=(const SoundModel& rhs) const {
    return std::tie(type, uuid, vendorUuid, data, dataSize) <= std::tie(rhs.type, rhs.uuid, rhs.vendorUuid, rhs.data, rhs.dataSize);
  }
  inline bool operator==(const SoundModel& rhs) const {
    return std::tie(type, uuid, vendorUuid, data, dataSize) == std::tie(rhs.type, rhs.uuid, rhs.vendorUuid, rhs.data, rhs.dataSize);
  }
  inline bool operator>(const SoundModel& rhs) const {
    return std::tie(type, uuid, vendorUuid, data, dataSize) > std::tie(rhs.type, rhs.uuid, rhs.vendorUuid, rhs.data, rhs.dataSize);
  }
  inline bool operator>=(const SoundModel& rhs) const {
    return std::tie(type, uuid, vendorUuid, data, dataSize) >= std::tie(rhs.type, rhs.uuid, rhs.vendorUuid, rhs.data, rhs.dataSize);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "SoundModel{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", uuid: " << ::android::internal::ToString(uuid);
    os << ", vendorUuid: " << ::android::internal::ToString(vendorUuid);
    os << ", data: " << ::android::internal::ToString(data);
    os << ", dataSize: " << ::android::internal::ToString(dataSize);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
