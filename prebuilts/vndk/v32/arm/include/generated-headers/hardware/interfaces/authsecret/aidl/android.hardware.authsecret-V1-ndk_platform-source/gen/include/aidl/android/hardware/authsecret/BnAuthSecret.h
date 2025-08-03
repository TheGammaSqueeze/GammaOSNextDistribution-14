#pragma once

#include "aidl/android/hardware/authsecret/IAuthSecret.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace authsecret {
class BnAuthSecret : public ::ndk::BnCInterface<IAuthSecret> {
public:
  BnAuthSecret();
  virtual ~BnAuthSecret();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace authsecret
}  // namespace hardware
}  // namespace android
}  // namespace aidl
