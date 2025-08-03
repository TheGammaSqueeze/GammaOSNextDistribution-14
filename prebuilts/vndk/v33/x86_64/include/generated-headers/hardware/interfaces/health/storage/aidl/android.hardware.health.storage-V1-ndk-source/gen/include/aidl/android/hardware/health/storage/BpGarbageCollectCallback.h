#pragma once

#include "aidl/android/hardware/health/storage/IGarbageCollectCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace health {
namespace storage {
class BpGarbageCollectCallback : public ::ndk::BpCInterface<IGarbageCollectCallback> {
public:
  explicit BpGarbageCollectCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpGarbageCollectCallback();

  ::ndk::ScopedAStatus onFinish(::aidl::android::hardware::health::storage::Result in_result) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace storage
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
