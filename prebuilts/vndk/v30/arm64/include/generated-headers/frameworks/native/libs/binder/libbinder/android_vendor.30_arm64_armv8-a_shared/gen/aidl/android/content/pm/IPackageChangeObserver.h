#ifndef AIDL_GENERATED_ANDROID_CONTENT_PM_I_PACKAGE_CHANGE_OBSERVER_H_
#define AIDL_GENERATED_ANDROID_CONTENT_PM_I_PACKAGE_CHANGE_OBSERVER_H_

#include <android/content/pm/PackageChangeEvent.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <utils/StrongPointer.h>

namespace android {

namespace content {

namespace pm {

class IPackageChangeObserver : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(PackageChangeObserver)
  virtual ::android::binder::Status onPackageChanged(const ::android::content::pm::PackageChangeEvent& event) = 0;
};  // class IPackageChangeObserver

class IPackageChangeObserverDefault : public IPackageChangeObserver {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onPackageChanged(const ::android::content::pm::PackageChangeEvent&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IPackageChangeObserverDefault

}  // namespace pm

}  // namespace content

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_CONTENT_PM_I_PACKAGE_CHANGE_OBSERVER_H_
