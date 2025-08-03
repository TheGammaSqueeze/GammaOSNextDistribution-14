#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cstdint>
#include <utils/StrongPointer.h>

namespace android {
namespace gui {
class IHdrConversionConstantsDelegator;

class IHdrConversionConstants : public ::android::IInterface {
public:
  typedef IHdrConversionConstantsDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(HdrConversionConstants)
  enum : int32_t { HdrConversionModePassthrough = 1 };
  enum : int32_t { HdrConversionModeAuto = 2 };
  enum : int32_t { HdrConversionModeForce = 3 };
};  // class IHdrConversionConstants

class IHdrConversionConstantsDefault : public IHdrConversionConstants {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
};  // class IHdrConversionConstantsDefault
}  // namespace gui
}  // namespace android
