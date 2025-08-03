#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/DebugLevel.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantCallback.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantP2pIface.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantStaIface.h>
#include <aidl/android/hardware/wifi/supplicant/IfaceInfo.h>
#include <aidl/android/hardware/wifi/supplicant/IfaceType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicant : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicant();
  virtual ~ISupplicant();

  enum : int32_t { EXT_RADIO_WORK_TIMEOUT_IN_SECS = 10 };
  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_addP2pInterface = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_addStaInterface = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getDebugLevel = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getP2pInterface = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getStaInterface = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_isDebugShowKeysEnabled = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_isDebugShowTimestampEnabled = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_listInterfaces = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_registerCallback = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_removeInterface = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_setConcurrencyPriority = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_setDebugParams = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_terminate = FIRST_CALL_TRANSACTION + 12;

  static std::shared_ptr<ISupplicant> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicant>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicant>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicant>& impl);
  static const std::shared_ptr<ISupplicant>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus addP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus addStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getDebugLevel(::aidl::android::hardware::wifi::supplicant::DebugLevel* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isDebugShowKeysEnabled(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus isDebugShowTimestampEnabled(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus listInterfaces(std::vector<::aidl::android::hardware::wifi::supplicant::IfaceInfo>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus removeInterface(const ::aidl::android::hardware::wifi::supplicant::IfaceInfo& in_ifaceInfo) = 0;
  virtual ::ndk::ScopedAStatus setConcurrencyPriority(::aidl::android::hardware::wifi::supplicant::IfaceType in_type) = 0;
  virtual ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::supplicant::DebugLevel in_level, bool in_showTimestamp, bool in_showKeys) = 0;
  virtual ::ndk::ScopedAStatus terminate() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicant> default_impl;
};
class ISupplicantDefault : public ISupplicant {
public:
  ::ndk::ScopedAStatus addP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) override;
  ::ndk::ScopedAStatus addStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) override;
  ::ndk::ScopedAStatus getDebugLevel(::aidl::android::hardware::wifi::supplicant::DebugLevel* _aidl_return) override;
  ::ndk::ScopedAStatus getP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) override;
  ::ndk::ScopedAStatus getStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) override;
  ::ndk::ScopedAStatus isDebugShowKeysEnabled(bool* _aidl_return) override;
  ::ndk::ScopedAStatus isDebugShowTimestampEnabled(bool* _aidl_return) override;
  ::ndk::ScopedAStatus listInterfaces(std::vector<::aidl::android::hardware::wifi::supplicant::IfaceInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantCallback>& in_callback) override;
  ::ndk::ScopedAStatus removeInterface(const ::aidl::android::hardware::wifi::supplicant::IfaceInfo& in_ifaceInfo) override;
  ::ndk::ScopedAStatus setConcurrencyPriority(::aidl::android::hardware::wifi::supplicant::IfaceType in_type) override;
  ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::supplicant::DebugLevel in_level, bool in_showTimestamp, bool in_showKeys) override;
  ::ndk::ScopedAStatus terminate() override;
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
