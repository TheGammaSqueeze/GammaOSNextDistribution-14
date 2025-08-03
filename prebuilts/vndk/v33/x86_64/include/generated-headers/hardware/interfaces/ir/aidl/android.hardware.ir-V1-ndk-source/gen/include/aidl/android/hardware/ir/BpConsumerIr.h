#pragma once

#include "aidl/android/hardware/ir/IConsumerIr.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace ir {
class BpConsumerIr : public ::ndk::BpCInterface<IConsumerIr> {
public:
  explicit BpConsumerIr(const ::ndk::SpAIBinder& binder);
  virtual ~BpConsumerIr();

  ::ndk::ScopedAStatus getCarrierFreqs(std::vector<::aidl::android::hardware::ir::ConsumerIrFreqRange>* _aidl_return) override;
  ::ndk::ScopedAStatus transmit(int32_t in_carrierFreqHz, const std::vector<int32_t>& in_pattern) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace ir
}  // namespace hardware
}  // namespace android
}  // namespace aidl
