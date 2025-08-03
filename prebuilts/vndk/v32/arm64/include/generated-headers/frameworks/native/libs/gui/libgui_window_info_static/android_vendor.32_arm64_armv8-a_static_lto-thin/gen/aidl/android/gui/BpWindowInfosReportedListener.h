#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IWindowInfosReportedListener.h>

namespace android {

namespace gui {

class BpWindowInfosReportedListener : public ::android::BpInterface<IWindowInfosReportedListener> {
public:
  explicit BpWindowInfosReportedListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpWindowInfosReportedListener() = default;
  ::android::binder::Status onWindowInfosReported() override;
};  // class BpWindowInfosReportedListener

}  // namespace gui

}  // namespace android
