#pragma once

#include "aidl/android/hardware/light/ILights.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace light {
class BnLights : public ::ndk::BnCInterface<ILights> {
public:
  BnLights();
  virtual ~BnLights();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final override;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
