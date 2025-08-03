#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IDisplayEventConnection.h>

namespace android {
namespace gui {
class BpDisplayEventConnection : public ::android::BpInterface<IDisplayEventConnection> {
public:
  explicit BpDisplayEventConnection(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpDisplayEventConnection() = default;
  ::android::binder::Status stealReceiveChannel(::android::gui::BitTube* outChannel) override;
  ::android::binder::Status setVsyncRate(int32_t count) override;
  ::android::binder::Status requestNextVsync() override;
  ::android::binder::Status getLatestVsyncEventData(::android::gui::ParcelableVsyncEventData* _aidl_return) override;
};  // class BpDisplayEventConnection
}  // namespace gui
}  // namespace android
