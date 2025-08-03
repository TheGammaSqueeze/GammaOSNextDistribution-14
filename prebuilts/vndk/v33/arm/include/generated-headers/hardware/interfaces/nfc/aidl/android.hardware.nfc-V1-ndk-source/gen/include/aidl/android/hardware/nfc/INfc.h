#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/nfc/INfcClientCallback.h>
#include <aidl/android/hardware/nfc/NfcCloseType.h>
#include <aidl/android/hardware/nfc/NfcConfig.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
class INfc : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  INfc();
  virtual ~INfc();

  static const int32_t version = 1;
  static inline const std::string hash = "37acf94ceb095ad537b66c3bd77c30a6ad5f5b0e";
  static constexpr uint32_t TRANSACTION_open = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_coreInitialized = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_factoryReset = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getConfig = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_powerCycle = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_preDiscover = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_write = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_setEnableVerboseLogging = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_isVerboseLoggingEnabled = FIRST_CALL_TRANSACTION + 9;

  static std::shared_ptr<INfc> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<INfc>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<INfc>* instance);
  static bool setDefaultImpl(const std::shared_ptr<INfc>& impl);
  static const std::shared_ptr<INfc>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::nfc::INfcClientCallback>& in_clientCallback) = 0;
  virtual ::ndk::ScopedAStatus close(::aidl::android::hardware::nfc::NfcCloseType in_type) = 0;
  virtual ::ndk::ScopedAStatus coreInitialized() = 0;
  virtual ::ndk::ScopedAStatus factoryReset() = 0;
  virtual ::ndk::ScopedAStatus getConfig(::aidl::android::hardware::nfc::NfcConfig* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus powerCycle() = 0;
  virtual ::ndk::ScopedAStatus preDiscover() = 0;
  virtual ::ndk::ScopedAStatus write(const std::vector<uint8_t>& in_data, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setEnableVerboseLogging(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus isVerboseLoggingEnabled(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<INfc> default_impl;
};
class INfcDefault : public INfc {
public:
  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::nfc::INfcClientCallback>& in_clientCallback) override;
  ::ndk::ScopedAStatus close(::aidl::android::hardware::nfc::NfcCloseType in_type) override;
  ::ndk::ScopedAStatus coreInitialized() override;
  ::ndk::ScopedAStatus factoryReset() override;
  ::ndk::ScopedAStatus getConfig(::aidl::android::hardware::nfc::NfcConfig* _aidl_return) override;
  ::ndk::ScopedAStatus powerCycle() override;
  ::ndk::ScopedAStatus preDiscover() override;
  ::ndk::ScopedAStatus write(const std::vector<uint8_t>& in_data, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus setEnableVerboseLogging(bool in_enable) override;
  ::ndk::ScopedAStatus isVerboseLoggingEnabled(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
