#pragma once

#include "aidl/android/hardware/power/stats/IPowerStats.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace stats {
class BnPowerStats : public ::ndk::BnCInterface<IPowerStats> {
public:
  BnPowerStats();
  virtual ~BnPowerStats();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
