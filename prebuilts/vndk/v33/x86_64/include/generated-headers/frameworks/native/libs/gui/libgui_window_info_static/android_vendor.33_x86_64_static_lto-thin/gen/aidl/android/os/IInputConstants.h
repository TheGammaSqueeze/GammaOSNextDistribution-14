#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <cstdint>
#include <utils/StrongPointer.h>

namespace android {
namespace os {
class IInputConstants : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(InputConstants)
  enum : int32_t { UNMULTIPLIED_DEFAULT_DISPATCHING_TIMEOUT_MILLIS = 5000 };
  enum : int32_t { INVALID_BATTERY_CAPACITY = -1 };
  enum : int32_t { INVALID_INPUT_EVENT_ID = 0 };
  enum : int32_t { POLICY_FLAG_INJECTED_FROM_ACCESSIBILITY = 131072 };
  enum : int32_t { INPUT_EVENT_FLAG_IS_ACCESSIBILITY_EVENT = 2048 };
  enum : int32_t { DEFAULT_POINTER_ACCELERATION = 3 };
};  // class IInputConstants

class IInputConstantsDefault : public IInputConstants {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
};  // class IInputConstantsDefault
}  // namespace os
}  // namespace android
