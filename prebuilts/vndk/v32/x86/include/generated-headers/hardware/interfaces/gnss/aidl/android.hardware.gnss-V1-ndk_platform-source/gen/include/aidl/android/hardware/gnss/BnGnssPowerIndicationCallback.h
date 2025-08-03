#pragma once

#include "aidl/android/hardware/gnss/IGnssPowerIndicationCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BnGnssPowerIndicationCallback : public ::ndk::BnCInterface<IGnssPowerIndicationCallback> {
public:
  BnGnssPowerIndicationCallback();
  virtual ~BnGnssPowerIndicationCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
