#pragma once

#include <android/gui/IWindowInfosReportedListener.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <gui/DisplayInfo.h>
#include <gui/WindowInfo.h>
#include <optional>
#include <utils/StrongPointer.h>
#include <vector>

namespace android {
namespace gui {
class IWindowInfosListener : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(WindowInfosListener)
  virtual ::android::binder::Status onWindowInfosChanged(const ::std::vector<::android::gui::WindowInfo>& windowInfos, const ::std::vector<::android::gui::DisplayInfo>& displayInfos, const ::android::sp<::android::gui::IWindowInfosReportedListener>& windowInfosReportedListener) = 0;
};  // class IWindowInfosListener

class IWindowInfosListenerDefault : public IWindowInfosListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onWindowInfosChanged(const ::std::vector<::android::gui::WindowInfo>& /*windowInfos*/, const ::std::vector<::android::gui::DisplayInfo>& /*displayInfos*/, const ::android::sp<::android::gui::IWindowInfosReportedListener>& /*windowInfosReportedListener*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IWindowInfosListenerDefault
}  // namespace gui
}  // namespace android
