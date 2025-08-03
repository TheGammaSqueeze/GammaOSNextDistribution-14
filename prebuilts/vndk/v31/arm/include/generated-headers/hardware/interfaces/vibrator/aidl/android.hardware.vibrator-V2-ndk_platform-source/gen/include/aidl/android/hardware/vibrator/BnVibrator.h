#pragma once

#include "aidl/android/hardware/vibrator/IVibrator.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {
class BnVibrator : public ::ndk::BnCInterface<IVibrator> {
public:
  BnVibrator();
  virtual ~BnVibrator();
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
