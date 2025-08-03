#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/os/IServiceManager.h>

namespace android {

namespace os {

class BpServiceManager : public ::android::BpInterface<IServiceManager> {
public:
  explicit BpServiceManager(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpServiceManager() = default;
  ::android::binder::Status getService(const ::std::string& name, ::android::sp<::android::IBinder>* _aidl_return) override;
  ::android::binder::Status checkService(const ::std::string& name, ::android::sp<::android::IBinder>* _aidl_return) override;
  ::android::binder::Status addService(const ::std::string& name, const ::android::sp<::android::IBinder>& service, bool allowIsolated, int32_t dumpPriority) override;
  ::android::binder::Status listServices(int32_t dumpPriority, ::std::vector<::std::string>* _aidl_return) override;
  ::android::binder::Status registerForNotifications(const ::std::string& name, const ::android::sp<::android::os::IServiceCallback>& callback) override;
  ::android::binder::Status unregisterForNotifications(const ::std::string& name, const ::android::sp<::android::os::IServiceCallback>& callback) override;
  ::android::binder::Status isDeclared(const ::std::string& name, bool* _aidl_return) override;
  ::android::binder::Status getDeclaredInstances(const ::std::string& iface, ::std::vector<::std::string>* _aidl_return) override;
  ::android::binder::Status updatableViaApex(const ::std::string& name, ::std::optional<::std::string>* _aidl_return) override;
  ::android::binder::Status registerClientCallback(const ::std::string& name, const ::android::sp<::android::IBinder>& service, const ::android::sp<::android::os::IClientCallback>& callback) override;
  ::android::binder::Status tryUnregisterService(const ::std::string& name, const ::android::sp<::android::IBinder>& service) override;
  ::android::binder::Status getServiceDebugInfo(::std::vector<::android::os::ServiceDebugInfo>* _aidl_return) override;
};  // class BpServiceManager

}  // namespace os

}  // namespace android
