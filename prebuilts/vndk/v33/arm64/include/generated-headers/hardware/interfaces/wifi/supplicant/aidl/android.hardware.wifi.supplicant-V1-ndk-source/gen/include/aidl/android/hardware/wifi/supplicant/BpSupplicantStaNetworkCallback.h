#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaNetworkCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantStaNetworkCallback : public ::ndk::BpCInterface<ISupplicantStaNetworkCallback> {
public:
  explicit BpSupplicantStaNetworkCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantStaNetworkCallback();

  ::ndk::ScopedAStatus onNetworkEapIdentityRequest() override;
  ::ndk::ScopedAStatus onNetworkEapSimGsmAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimGsmAuthParams& in_params) override;
  ::ndk::ScopedAStatus onNetworkEapSimUmtsAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimUmtsAuthParams& in_params) override;
  ::ndk::ScopedAStatus onTransitionDisable(::aidl::android::hardware::wifi::supplicant::TransitionDisableIndication in_ind) override;
  ::ndk::ScopedAStatus onServerCertificateAvailable(int32_t in_depth, const std::vector<uint8_t>& in_subject, const std::vector<uint8_t>& in_certHash, const std::vector<uint8_t>& in_certBlob) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
