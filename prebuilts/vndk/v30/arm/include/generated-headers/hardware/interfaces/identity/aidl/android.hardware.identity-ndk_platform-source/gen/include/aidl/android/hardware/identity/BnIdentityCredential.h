#pragma once

#include "aidl/android/hardware/identity/IIdentityCredential.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class BnIdentityCredential : public ::ndk::BnCInterface<IIdentityCredential> {
public:
  BnIdentityCredential();
  virtual ~BnIdentityCredential();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final override;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
