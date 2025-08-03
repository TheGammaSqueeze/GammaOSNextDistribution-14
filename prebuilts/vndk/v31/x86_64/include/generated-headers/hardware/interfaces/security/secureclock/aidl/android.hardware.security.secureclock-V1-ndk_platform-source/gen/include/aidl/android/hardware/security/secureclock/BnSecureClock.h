#pragma once

#include "aidl/android/hardware/security/secureclock/ISecureClock.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace secureclock {
class BnSecureClock : public ::ndk::BnCInterface<ISecureClock> {
public:
  BnSecureClock();
  virtual ~BnSecureClock();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace secureclock
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
