#pragma once

#include "aidl/android/hardware/security/keymint/IKeyMintOperation.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace security {
namespace keymint {
class BnKeyMintOperation : public ::ndk::BnCInterface<IKeyMintOperation> {
public:
  BnKeyMintOperation();
  virtual ~BnKeyMintOperation();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
