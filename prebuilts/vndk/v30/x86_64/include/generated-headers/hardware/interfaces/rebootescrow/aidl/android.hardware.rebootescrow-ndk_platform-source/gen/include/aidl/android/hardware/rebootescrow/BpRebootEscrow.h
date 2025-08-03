#pragma once

#include "aidl/android/hardware/rebootescrow/IRebootEscrow.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace rebootescrow {
class BpRebootEscrow : public ::ndk::BpCInterface<IRebootEscrow> {
public:
  BpRebootEscrow(const ::ndk::SpAIBinder& binder);
  virtual ~BpRebootEscrow();

  ::ndk::ScopedAStatus storeKey(const std::vector<int8_t>& in_kek) override;
  ::ndk::ScopedAStatus retrieveKey(std::vector<int8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace rebootescrow
}  // namespace hardware
}  // namespace android
}  // namespace aidl
