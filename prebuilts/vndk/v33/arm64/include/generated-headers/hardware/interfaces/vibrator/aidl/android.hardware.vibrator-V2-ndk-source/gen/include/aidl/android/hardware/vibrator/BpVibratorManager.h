#pragma once

#include "aidl/android/hardware/vibrator/IVibratorManager.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class BpVibratorManager : public ::ndk::BpCInterface<IVibratorManager> {
public:
  explicit BpVibratorManager(const ::ndk::SpAIBinder& binder);
  virtual ~BpVibratorManager();

  ::ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getVibratorIds(std::vector<int32_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getVibrator(int32_t in_vibratorId, std::shared_ptr<::aidl::android::hardware::vibrator::IVibrator>* _aidl_return) override;
  ::ndk::ScopedAStatus prepareSynced(const std::vector<int32_t>& in_vibratorIds) override;
  ::ndk::ScopedAStatus triggerSynced(const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override;
  ::ndk::ScopedAStatus cancelSynced() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
