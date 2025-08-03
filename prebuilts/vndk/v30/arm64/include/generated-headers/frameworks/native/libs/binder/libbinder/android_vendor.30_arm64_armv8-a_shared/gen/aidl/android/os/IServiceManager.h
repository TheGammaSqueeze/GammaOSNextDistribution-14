#ifndef AIDL_GENERATED_ANDROID_OS_I_SERVICE_MANAGER_H_
#define AIDL_GENERATED_ANDROID_OS_I_SERVICE_MANAGER_H_

#include <android/os/IClientCallback.h>
#include <android/os/IServiceCallback.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <utils/StrongPointer.h>
#include <vector>

namespace android {

namespace os {

class IServiceManager : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(ServiceManager)
  enum  : int32_t {
    DUMP_FLAG_PRIORITY_CRITICAL = 1,
    DUMP_FLAG_PRIORITY_HIGH = 2,
    DUMP_FLAG_PRIORITY_NORMAL = 4,
    DUMP_FLAG_PRIORITY_DEFAULT = 8,
    DUMP_FLAG_PRIORITY_ALL = 15,
    DUMP_FLAG_PROTO = 16,
  };
  virtual ::android::binder::Status getService(const ::std::string& name, ::android::sp<::android::IBinder>* _aidl_return) = 0;
  virtual ::android::binder::Status checkService(const ::std::string& name, ::android::sp<::android::IBinder>* _aidl_return) = 0;
  virtual ::android::binder::Status addService(const ::std::string& name, const ::android::sp<::android::IBinder>& service, bool allowIsolated, int32_t dumpPriority) = 0;
  virtual ::android::binder::Status listServices(int32_t dumpPriority, ::std::vector<::std::string>* _aidl_return) = 0;
  virtual ::android::binder::Status registerForNotifications(const ::std::string& name, const ::android::sp<::android::os::IServiceCallback>& callback) = 0;
  virtual ::android::binder::Status unregisterForNotifications(const ::std::string& name, const ::android::sp<::android::os::IServiceCallback>& callback) = 0;
  virtual ::android::binder::Status isDeclared(const ::std::string& name, bool* _aidl_return) = 0;
  virtual ::android::binder::Status registerClientCallback(const ::std::string& name, const ::android::sp<::android::IBinder>& service, const ::android::sp<::android::os::IClientCallback>& callback) = 0;
  virtual ::android::binder::Status tryUnregisterService(const ::std::string& name, const ::android::sp<::android::IBinder>& service) = 0;
};  // class IServiceManager

class IServiceManagerDefault : public IServiceManager {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status getService(const ::std::string&, ::android::sp<::android::IBinder>*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status checkService(const ::std::string&, ::android::sp<::android::IBinder>*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status addService(const ::std::string&, const ::android::sp<::android::IBinder>&, bool, int32_t) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status listServices(int32_t, ::std::vector<::std::string>*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status registerForNotifications(const ::std::string&, const ::android::sp<::android::os::IServiceCallback>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status unregisterForNotifications(const ::std::string&, const ::android::sp<::android::os::IServiceCallback>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status isDeclared(const ::std::string&, bool*) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status registerClientCallback(const ::std::string&, const ::android::sp<::android::IBinder>&, const ::android::sp<::android::os::IClientCallback>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status tryUnregisterService(const ::std::string&, const ::android::sp<::android::IBinder>&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IServiceManagerDefault

}  // namespace os

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_OS_I_SERVICE_MANAGER_H_
