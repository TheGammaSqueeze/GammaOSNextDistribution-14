#ifndef AIDL_GENERATED_ANDROID_CONTENT_PM_BP_PACKAGE_CHANGE_OBSERVER_H_
#define AIDL_GENERATED_ANDROID_CONTENT_PM_BP_PACKAGE_CHANGE_OBSERVER_H_

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/content/pm/IPackageChangeObserver.h>

namespace android {

namespace content {

namespace pm {

class BpPackageChangeObserver : public ::android::BpInterface<IPackageChangeObserver> {
public:
  explicit BpPackageChangeObserver(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpPackageChangeObserver() = default;
  ::android::binder::Status onPackageChanged(const ::android::content::pm::PackageChangeEvent& event) override;
};  // class BpPackageChangeObserver

}  // namespace pm

}  // namespace content

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_CONTENT_PM_BP_PACKAGE_CHANGE_OBSERVER_H_
