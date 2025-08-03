#pragma once

#include "aidl/android/hardware/power/IPower.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
class BnPower : public ::ndk::BnCInterface<IPower> {
public:
  BnPower();
  virtual ~BnPower();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final override;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
