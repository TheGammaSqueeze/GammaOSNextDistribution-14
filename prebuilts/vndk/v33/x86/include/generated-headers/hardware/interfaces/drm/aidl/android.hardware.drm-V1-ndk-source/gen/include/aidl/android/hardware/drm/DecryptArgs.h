#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/drm/DestinationBuffer.h>
#include <aidl/android/hardware/drm/Mode.h>
#include <aidl/android/hardware/drm/Pattern.h>
#include <aidl/android/hardware/drm/SharedBuffer.h>
#include <aidl/android/hardware/drm/SubSample.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class DecryptArgs {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool secure = false;
  std::vector<uint8_t> keyId;
  std::vector<uint8_t> iv;
  ::aidl::android::hardware::drm::Mode mode = ::aidl::android::hardware::drm::Mode(0);
  ::aidl::android::hardware::drm::Pattern pattern;
  std::vector<::aidl::android::hardware::drm::SubSample> subSamples;
  ::aidl::android::hardware::drm::SharedBuffer source;
  int64_t offset = 0L;
  ::aidl::android::hardware::drm::DestinationBuffer destination;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DecryptArgs& rhs) const {
    return std::tie(secure, keyId, iv, mode, pattern, subSamples, source, offset, destination) != std::tie(rhs.secure, rhs.keyId, rhs.iv, rhs.mode, rhs.pattern, rhs.subSamples, rhs.source, rhs.offset, rhs.destination);
  }
  inline bool operator<(const DecryptArgs& rhs) const {
    return std::tie(secure, keyId, iv, mode, pattern, subSamples, source, offset, destination) < std::tie(rhs.secure, rhs.keyId, rhs.iv, rhs.mode, rhs.pattern, rhs.subSamples, rhs.source, rhs.offset, rhs.destination);
  }
  inline bool operator<=(const DecryptArgs& rhs) const {
    return std::tie(secure, keyId, iv, mode, pattern, subSamples, source, offset, destination) <= std::tie(rhs.secure, rhs.keyId, rhs.iv, rhs.mode, rhs.pattern, rhs.subSamples, rhs.source, rhs.offset, rhs.destination);
  }
  inline bool operator==(const DecryptArgs& rhs) const {
    return std::tie(secure, keyId, iv, mode, pattern, subSamples, source, offset, destination) == std::tie(rhs.secure, rhs.keyId, rhs.iv, rhs.mode, rhs.pattern, rhs.subSamples, rhs.source, rhs.offset, rhs.destination);
  }
  inline bool operator>(const DecryptArgs& rhs) const {
    return std::tie(secure, keyId, iv, mode, pattern, subSamples, source, offset, destination) > std::tie(rhs.secure, rhs.keyId, rhs.iv, rhs.mode, rhs.pattern, rhs.subSamples, rhs.source, rhs.offset, rhs.destination);
  }
  inline bool operator>=(const DecryptArgs& rhs) const {
    return std::tie(secure, keyId, iv, mode, pattern, subSamples, source, offset, destination) >= std::tie(rhs.secure, rhs.keyId, rhs.iv, rhs.mode, rhs.pattern, rhs.subSamples, rhs.source, rhs.offset, rhs.destination);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DecryptArgs{";
    os << "secure: " << ::android::internal::ToString(secure);
    os << ", keyId: " << ::android::internal::ToString(keyId);
    os << ", iv: " << ::android::internal::ToString(iv);
    os << ", mode: " << ::android::internal::ToString(mode);
    os << ", pattern: " << ::android::internal::ToString(pattern);
    os << ", subSamples: " << ::android::internal::ToString(subSamples);
    os << ", source: " << ::android::internal::ToString(source);
    os << ", offset: " << ::android::internal::ToString(offset);
    os << ", destination: " << ::android::internal::ToString(destination);
    os << "}";
    return os.str();
  }
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
