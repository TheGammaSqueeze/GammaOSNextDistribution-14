#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/IfaceType.h>
#include <aidl/android/hardware/wifi/supplicant/MacAddress.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicantP2pNetwork : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantP2pNetwork();
  virtual ~ISupplicantP2pNetwork();

  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_getBssid = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getClientList = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getId = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getInterfaceName = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getSsid = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getType = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_isCurrent = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_isGroupOwner = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_isPersistent = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_setClientList = FIRST_CALL_TRANSACTION + 9;

  static std::shared_ptr<ISupplicantP2pNetwork> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantP2pNetwork>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantP2pNetwork>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantP2pNetwork>& impl);
  static const std::shared_ptr<ISupplicantP2pNetwork>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getBssid(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getClientList(std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getId(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceName(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSsid(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isCurrent(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isGroupOwner(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isPersistent(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setClientList(const std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>& in_clients) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantP2pNetwork> default_impl;
};
class ISupplicantP2pNetworkDefault : public ISupplicantP2pNetwork {
public:
  ::ndk::ScopedAStatus getBssid(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getClientList(std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>* _aidl_return) override;
  ::ndk::ScopedAStatus getId(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getSsid(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override;
  ::ndk::ScopedAStatus isCurrent(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isGroupOwner(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isPersistent(bool* _aidl_return) override;
  ::ndk::ScopedAStatus setClientList(const std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>& in_clients) override;
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
