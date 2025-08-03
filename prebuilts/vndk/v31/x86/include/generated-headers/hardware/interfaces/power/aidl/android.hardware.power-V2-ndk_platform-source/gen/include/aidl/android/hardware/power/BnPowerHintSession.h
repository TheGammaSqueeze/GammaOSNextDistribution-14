#pragma once

#include "aidl/android/hardware/power/IPowerHintSession.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
class BnPowerHintSession : public ::ndk::BnCInterface<IPowerHintSession> {
public:
  BnPowerHintSession();
  virtual ~BnPowerHintSession();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
