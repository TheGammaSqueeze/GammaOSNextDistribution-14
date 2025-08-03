#pragma once

#include "aidl/android/hardware/uwb/IUwbChip.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
class BpUwbChip : public ::ndk::BpCInterface<IUwbChip> {
public:
  explicit BpUwbChip(const ::ndk::SpAIBinder& binder);
  virtual ~BpUwbChip();

  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::uwb::IUwbClientCallback>& in_clientCallback) override;
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus coreInit() override;
  ::ndk::ScopedAStatus sessionInit(int32_t in_sessionId) override;
  ::ndk::ScopedAStatus getSupportedAndroidUciVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus sendUciMessage(const std::vector<uint8_t>& in_data, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
