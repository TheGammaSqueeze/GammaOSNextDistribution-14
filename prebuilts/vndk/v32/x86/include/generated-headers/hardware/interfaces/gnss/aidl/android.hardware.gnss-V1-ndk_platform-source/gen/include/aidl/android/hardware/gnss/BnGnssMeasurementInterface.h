#pragma once

#include "aidl/android/hardware/gnss/IGnssMeasurementInterface.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class BnGnssMeasurementInterface : public ::ndk::BnCInterface<IGnssMeasurementInterface> {
public:
  BnGnssMeasurementInterface();
  virtual ~BnGnssMeasurementInterface();
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
