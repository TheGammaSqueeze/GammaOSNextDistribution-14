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
#include <aidl/android/hardware/identity/Certificate.h>
namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class SecureAccessControlProfile {
public:
  static const char* descriptor;

  int32_t id = {};
  ::aidl::android::hardware::identity::Certificate readerCertificate;
  bool userAuthenticationRequired = {};
  int64_t timeoutMillis = {};
  int64_t secureUserId = {};
  std::vector<int8_t> mac;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
