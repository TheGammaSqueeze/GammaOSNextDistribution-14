#pragma once

#include "aidl/android/hardware/identity/IIdentityCredentialStore.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class BnIdentityCredentialStore : public ::ndk::BnCInterface<IIdentityCredentialStore> {
public:
  BnIdentityCredentialStore();
  virtual ~BnIdentityCredentialStore();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
