#pragma once
#include <android/binder_interface_utils.h>

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/keymaster/SecurityLevel.h>
#include <aidl/android/hardware/keymaster/Timestamp.h>
namespace aidl {
namespace android {
namespace hardware {
namespace keymaster {
class VerificationToken {
public:
  static const char* descriptor;

  int64_t challenge = {};
  ::aidl::android::hardware::keymaster::Timestamp timestamp;
  ::aidl::android::hardware::keymaster::SecurityLevel securityLevel = ::aidl::android::hardware::keymaster::SecurityLevel(0);
  std::vector<int8_t> mac;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace keymaster
}  // namespace hardware
}  // namespace android
}  // namespace aidl
