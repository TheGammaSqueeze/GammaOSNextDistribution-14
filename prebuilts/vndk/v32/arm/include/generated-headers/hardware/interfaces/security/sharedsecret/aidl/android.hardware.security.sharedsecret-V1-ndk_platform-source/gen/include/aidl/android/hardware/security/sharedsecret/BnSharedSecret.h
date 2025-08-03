#pragma once

#include "aidl/android/hardware/security/sharedsecret/ISharedSecret.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace sharedsecret {
class BnSharedSecret : public ::ndk::BnCInterface<ISharedSecret> {
public:
  BnSharedSecret();
  virtual ~BnSharedSecret();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace sharedsecret
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
