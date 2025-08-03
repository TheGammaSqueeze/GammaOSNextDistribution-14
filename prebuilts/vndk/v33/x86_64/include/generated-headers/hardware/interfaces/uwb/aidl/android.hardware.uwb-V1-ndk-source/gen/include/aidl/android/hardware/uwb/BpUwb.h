#pragma once

#include "aidl/android/hardware/uwb/IUwb.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
class BpUwb : public ::ndk::BpCInterface<IUwb> {
public:
  explicit BpUwb(const ::ndk::SpAIBinder& binder);
  virtual ~BpUwb();

  ::ndk::ScopedAStatus getChips(std::vector<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus getChip(const std::string& in_name, std::shared_ptr<::aidl::android::hardware::uwb::IUwbChip>* _aidl_return) override;
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
