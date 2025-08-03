#pragma once

#include "aidl/android/hardware/vibrator/IVibratorManager.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class BnVibratorManager : public ::ndk::BnCInterface<IVibratorManager> {
public:
  BnVibratorManager();
  virtual ~BnVibratorManager();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
