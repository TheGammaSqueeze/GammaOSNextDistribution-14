#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/hostapd/ApInfo.h>
#include <aidl/android/hardware/wifi/hostapd/ClientInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
class IHostapdCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IHostapdCallback();
  virtual ~IHostapdCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "370160cd436c80c5d355de5ad11d2aaee684d801";
  static constexpr uint32_t TRANSACTION_onApInstanceInfoChanged = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onConnectedClientsChanged = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onFailure = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<IHostapdCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IHostapdCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IHostapdCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IHostapdCallback>& impl);
  static const std::shared_ptr<IHostapdCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onApInstanceInfoChanged(const ::aidl::android::hardware::wifi::hostapd::ApInfo& in_apInfo) = 0;
  virtual ::ndk::ScopedAStatus onConnectedClientsChanged(const ::aidl::android::hardware::wifi::hostapd::ClientInfo& in_clientInfo) = 0;
  virtual ::ndk::ScopedAStatus onFailure(const std::string& in_ifaceName, const std::string& in_instanceName) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IHostapdCallback> default_impl;
};
class IHostapdCallbackDefault : public IHostapdCallback {
public:
  ::ndk::ScopedAStatus onApInstanceInfoChanged(const ::aidl::android::hardware::wifi::hostapd::ApInfo& in_apInfo) override;
  ::ndk::ScopedAStatus onConnectedClientsChanged(const ::aidl::android::hardware::wifi::hostapd::ClientInfo& in_clientInfo) override;
  ::ndk::ScopedAStatus onFailure(const std::string& in_ifaceName, const std::string& in_instanceName) override;
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
