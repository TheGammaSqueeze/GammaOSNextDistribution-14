#ifndef AIDL_GENERATED_ANDROID_CONTENT_PM_BN_PACKAGE_CHANGE_OBSERVER_H_
#define AIDL_GENERATED_ANDROID_CONTENT_PM_BN_PACKAGE_CHANGE_OBSERVER_H_

#include <binder/IInterface.h>
#include <android/content/pm/IPackageChangeObserver.h>

namespace android {

namespace content {

namespace pm {

class BnPackageChangeObserver : public ::android::BnInterface<IPackageChangeObserver> {
public:
  explicit BnPackageChangeObserver();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnPackageChangeObserver

}  // namespace pm

}  // namespace content

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_CONTENT_PM_BN_PACKAGE_CHANGE_OBSERVER_H_
