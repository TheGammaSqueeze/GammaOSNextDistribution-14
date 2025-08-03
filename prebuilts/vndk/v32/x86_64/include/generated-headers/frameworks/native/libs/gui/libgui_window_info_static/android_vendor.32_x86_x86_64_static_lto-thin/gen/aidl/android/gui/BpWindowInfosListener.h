#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IWindowInfosListener.h>

namespace android {

namespace gui {

class BpWindowInfosListener : public ::android::BpInterface<IWindowInfosListener> {
public:
  explicit BpWindowInfosListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpWindowInfosListener() = default;
  ::android::binder::Status onWindowInfosChanged(const ::std::vector<::android::gui::WindowInfo>& windowInfos, const ::android::sp<::android::gui::IWindowInfosReportedListener>& windowInfosReportedListener) override;
};  // class BpWindowInfosListener

}  // namespace gui

}  // namespace android
