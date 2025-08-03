#pragma once

#include "aidl/android/hardware/vibrator/IVibratorCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class BnVibratorCallback : public ::ndk::BnCInterface<IVibratorCallback> {
public:
  BnVibratorCallback();
  virtual ~BnVibratorCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final override;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
