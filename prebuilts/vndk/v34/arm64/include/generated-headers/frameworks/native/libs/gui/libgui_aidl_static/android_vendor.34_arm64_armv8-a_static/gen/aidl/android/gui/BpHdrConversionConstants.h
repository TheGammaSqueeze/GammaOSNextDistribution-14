#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IHdrConversionConstants.h>

namespace android {
namespace gui {
class BpHdrConversionConstants : public ::android::BpInterface<IHdrConversionConstants> {
public:
  explicit BpHdrConversionConstants(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpHdrConversionConstants() = default;
};  // class BpHdrConversionConstants
}  // namespace gui
}  // namespace android
