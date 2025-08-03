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
#include <aidl/android/hardware/vibrator/IVibrator.h>
#include <aidl/android/hardware/vibrator/IVibratorCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class IVibratorManager : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IVibratorManager();
  virtual ~IVibratorManager();

  enum : int32_t { CAP_SYNC = 1 };
  enum : int32_t { CAP_PREPARE_ON = 2 };
  enum : int32_t { CAP_PREPARE_PERFORM = 4 };
  enum : int32_t { CAP_PREPARE_COMPOSE = 8 };
  enum : int32_t { CAP_MIXED_TRIGGER_ON = 16 };
  enum : int32_t { CAP_MIXED_TRIGGER_PERFORM = 32 };
  enum : int32_t { CAP_MIXED_TRIGGER_COMPOSE = 64 };
  enum : int32_t { CAP_TRIGGER_CALLBACK = 128 };
  static const int32_t version = 2;
  static inline const std::string hash = "ea8742d6993e1a82917da38b9938e537aa7fcb54";
  static constexpr uint32_t TRANSACTION_getCapabilities = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getVibratorIds = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getVibrator = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_prepareSynced = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_triggerSynced = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_cancelSynced = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<IVibratorManager> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IVibratorManager>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IVibratorManager>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IVibratorManager>& impl);
  static const std::shared_ptr<IVibratorManager>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getVibratorIds(std::vector<int32_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getVibrator(int32_t in_vibratorId, std::shared_ptr<::aidl::android::hardware::vibrator::IVibrator>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus prepareSynced(const std::vector<int32_t>& in_vibratorIds) = 0;
  virtual ::ndk::ScopedAStatus triggerSynced(const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus cancelSynced() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IVibratorManager> default_impl;
};
class IVibratorManagerDefault : public IVibratorManager {
public:
  ::ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getVibratorIds(std::vector<int32_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getVibrator(int32_t in_vibratorId, std::shared_ptr<::aidl::android::hardware::vibrator::IVibrator>* _aidl_return) override;
  ::ndk::ScopedAStatus prepareSynced(const std::vector<int32_t>& in_vibratorIds) override;
  ::ndk::ScopedAStatus triggerSynced(const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override;
  ::ndk::ScopedAStatus cancelSynced() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
