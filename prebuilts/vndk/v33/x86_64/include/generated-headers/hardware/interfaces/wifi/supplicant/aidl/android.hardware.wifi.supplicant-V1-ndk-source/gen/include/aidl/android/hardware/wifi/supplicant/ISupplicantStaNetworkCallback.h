#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/NetworkRequestEapSimGsmAuthParams.h>
#include <aidl/android/hardware/wifi/supplicant/NetworkRequestEapSimUmtsAuthParams.h>
#include <aidl/android/hardware/wifi/supplicant/TransitionDisableIndication.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicantStaNetworkCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantStaNetworkCallback();
  virtual ~ISupplicantStaNetworkCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_onNetworkEapIdentityRequest = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onNetworkEapSimGsmAuthRequest = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onNetworkEapSimUmtsAuthRequest = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onTransitionDisable = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_onServerCertificateAvailable = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<ISupplicantStaNetworkCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantStaNetworkCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantStaNetworkCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantStaNetworkCallback>& impl);
  static const std::shared_ptr<ISupplicantStaNetworkCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onNetworkEapIdentityRequest() = 0;
  virtual ::ndk::ScopedAStatus onNetworkEapSimGsmAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimGsmAuthParams& in_params) = 0;
  virtual ::ndk::ScopedAStatus onNetworkEapSimUmtsAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimUmtsAuthParams& in_params) = 0;
  virtual ::ndk::ScopedAStatus onTransitionDisable(::aidl::android::hardware::wifi::supplicant::TransitionDisableIndication in_ind) = 0;
  virtual ::ndk::ScopedAStatus onServerCertificateAvailable(int32_t in_depth, const std::vector<uint8_t>& in_subject, const std::vector<uint8_t>& in_certHash, const std::vector<uint8_t>& in_certBlob) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantStaNetworkCallback> default_impl;
};
class ISupplicantStaNetworkCallbackDefault : public ISupplicantStaNetworkCallback {
public:
  ::ndk::ScopedAStatus onNetworkEapIdentityRequest() override;
  ::ndk::ScopedAStatus onNetworkEapSimGsmAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimGsmAuthParams& in_params) override;
  ::ndk::ScopedAStatus onNetworkEapSimUmtsAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimUmtsAuthParams& in_params) override;
  ::ndk::ScopedAStatus onTransitionDisable(::aidl::android::hardware::wifi::supplicant::TransitionDisableIndication in_ind) override;
  ::ndk::ScopedAStatus onServerCertificateAvailable(int32_t in_depth, const std::vector<uint8_t>& in_subject, const std::vector<uint8_t>& in_certHash, const std::vector<uint8_t>& in_certBlob) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
