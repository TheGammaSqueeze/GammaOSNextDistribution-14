#pragma once

#include "aidl/android/hardware/identity/IWritableIdentityCredential.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class BnWritableIdentityCredential : public ::ndk::BnCInterface<IWritableIdentityCredential> {
public:
  BnWritableIdentityCredential();
  virtual ~BnWritableIdentityCredential();
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
