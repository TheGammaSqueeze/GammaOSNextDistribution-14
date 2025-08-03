#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/hostapd/DebugLevel.h>
#include <aidl/android/hardware/wifi/hostapd/IHostapdCallback.h>
#include <aidl/android/hardware/wifi/hostapd/Ieee80211ReasonCode.h>
#include <aidl/android/hardware/wifi/hostapd/IfaceParams.h>
#include <aidl/android/hardware/wifi/hostapd/NetworkParams.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class IHostapd : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IHostapd();
  virtual ~IHostapd();

  static const int32_t version = 1;
  static inline const std::string hash = "370160cd436c80c5d355de5ad11d2aaee684d801";
  static constexpr uint32_t TRANSACTION_addAccessPoint = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_forceClientDisconnect = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_registerCallback = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_removeAccessPoint = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_setDebugParams = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_terminate = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<IHostapd> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IHostapd>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IHostapd>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IHostapd>& impl);
  static const std::shared_ptr<IHostapd>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus addAccessPoint(const ::aidl::android::hardware::wifi::hostapd::IfaceParams& in_ifaceParams, const ::aidl::android::hardware::wifi::hostapd::NetworkParams& in_nwParams) = 0;
  virtual ::ndk::ScopedAStatus forceClientDisconnect(const std::string& in_ifaceName, const std::vector<uint8_t>& in_clientAddress, ::aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode in_reasonCode) = 0;
  virtual ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::hostapd::IHostapdCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus removeAccessPoint(const std::string& in_ifaceName) = 0;
  virtual ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::hostapd::DebugLevel in_level) = 0;
  virtual ::ndk::ScopedAStatus terminate() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IHostapd> default_impl;
};
class IHostapdDefault : public IHostapd {
public:
  ::ndk::ScopedAStatus addAccessPoint(const ::aidl::android::hardware::wifi::hostapd::IfaceParams& in_ifaceParams, const ::aidl::android::hardware::wifi::hostapd::NetworkParams& in_nwParams) override;
  ::ndk::ScopedAStatus forceClientDisconnect(const std::string& in_ifaceName, const std::vector<uint8_t>& in_clientAddress, ::aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode in_reasonCode) override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::hostapd::IHostapdCallback>& in_callback) override;
  ::ndk::ScopedAStatus removeAccessPoint(const std::string& in_ifaceName) override;
  ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::hostapd::DebugLevel in_level) override;
  ::ndk::ScopedAStatus terminate() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
