#pragma once

#include "aidl/android/hardware/nfc/INfc.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
class BpNfc : public ::ndk::BpCInterface<INfc> {
public:
  explicit BpNfc(const ::ndk::SpAIBinder& binder);
  virtual ~BpNfc();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
