#pragma once

#include "aidl/android/hardware/gnss/IGnssBatching.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BpGnssBatching : public ::ndk::BpCInterface<IGnssBatching> {
public:
  explicit BpGnssBatching(const ::ndk::SpAIBinder& binder);
  virtual ~BpGnssBatching();

  ::ndk::ScopedAStatus init(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssBatchingCallback>& in_callback) override;
  ::ndk::ScopedAStatus getBatchSize(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus start(const ::aidl::android::hardware::gnss::IGnssBatching::Options& in_options) override;
  ::ndk::ScopedAStatus flush() override;
  ::ndk::ScopedAStatus stop() override;
  ::ndk::ScopedAStatus cleanup() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
