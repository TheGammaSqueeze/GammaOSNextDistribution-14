#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IFpsListener.h>

namespace android {

namespace gui {

class BpFpsListener : public ::android::BpInterface<IFpsListener> {
public:
  explicit BpFpsListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpFpsListener() = default;
  ::android::binder::Status onFpsReported(float fps) override;
};  // class BpFpsListener

}  // namespace gui

}  // namespace android
