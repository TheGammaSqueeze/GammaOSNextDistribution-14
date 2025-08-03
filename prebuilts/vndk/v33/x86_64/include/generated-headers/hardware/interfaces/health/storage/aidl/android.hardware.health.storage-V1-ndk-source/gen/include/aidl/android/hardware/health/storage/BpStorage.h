#pragma once

#include "aidl/android/hardware/health/storage/IStorage.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace health {
namespace storage {
class BpStorage : public ::ndk::BpCInterface<IStorage> {
public:
  explicit BpStorage(const ::ndk::SpAIBinder& binder);
  virtual ~BpStorage();

  ::ndk::ScopedAStatus garbageCollect(int64_t in_timeoutSeconds, const std::shared_ptr<::aidl::android::hardware::health::storage::IGarbageCollectCallback>& in_callback) override;
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
