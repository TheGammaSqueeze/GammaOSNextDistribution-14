#pragma once

#include "aidl/android/hardware/gnss/IGnssPsds.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BnGnssPsds : public ::ndk::BnCInterface<IGnssPsds> {
public:
  BnGnssPsds();
  virtual ~BnGnssPsds();
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
