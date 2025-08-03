#pragma once

#include <binder/IInterface.h>
#include <android/os/IServiceManager.h>

namespace android {
namespace os {
class BnServiceManager : public ::android::BnInterface<IServiceManager> {
public:
  static constexpr uint32_t TRANSACTION_getService = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_checkService = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_addService = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_listServices = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_registerForNotifications = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_unregisterForNotifications = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_isDeclared = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getDeclaredInstances = ::android::IBinder::FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_updatableViaApex = ::android::IBinder::FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getConnectionInfo = ::android::IBinder::FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_registerClientCallback = ::android::IBinder::FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_tryUnregisterService = ::android::IBinder::FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getServiceDebugInfo = ::android::IBinder::FIRST_CALL_TRANSACTION + 12;
  explicit BnServiceManager();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnServiceManager

class IServiceManagerDelegator : public BnServiceManager {
public:
  explicit IServiceManagerDelegator(::android::sp<IServiceManager> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status getService(const ::std::string& name, ::android::sp<::android::IBinder>* _aidl_return) override {
    return _aidl_delegate->getService(name, _aidl_return);
  }
  ::android::binder::Status checkService(const ::std::string& name, ::android::sp<::android::IBinder>* _aidl_return) override {
    return _aidl_delegate->checkService(name, _aidl_return);
  }
  ::android::binder::Status addService(const ::std::string& name, const ::android::sp<::android::IBinder>& service, bool allowIsolated, int32_t dumpPriority) override {
    return _aidl_delegate->addService(name, service, allowIsolated, dumpPriority);
  }
  ::android::binder::Status listServices(int32_t dumpPriority, ::std::vector<::std::string>* _aidl_return) override {
    return _aidl_delegate->listServices(dumpPriority, _aidl_return);
  }
  ::android::binder::Status registerForNotifications(const ::std::string& name, const ::android::sp<::android::os::IServiceCallback>& callback) override {
    return _aidl_delegate->registerForNotifications(name, callback);
  }
  ::android::binder::Status unregisterForNotifications(const ::std::string& name, const ::android::sp<::android::os::IServiceCallback>& callback) override {
    return _aidl_delegate->unregisterForNotifications(name, callback);
  }
  ::android::binder::Status isDeclared(const ::std::string& name, bool* _aidl_return) override {
    return _aidl_delegate->isDeclared(name, _aidl_return);
  }
  ::android::binder::Status getDeclaredInstances(const ::std::string& iface, ::std::vector<::std::string>* _aidl_return) override {
    return _aidl_delegate->getDeclaredInstances(iface, _aidl_return);
  }
  ::android::binder::Status updatableViaApex(const ::std::string& name, ::std::optional<::std::string>* _aidl_return) override {
    return _aidl_delegate->updatableViaApex(name, _aidl_return);
  }
  ::android::binder::Status getConnectionInfo(const ::std::string& name, ::std::optional<::android::os::ConnectionInfo>* _aidl_return) override {
    return _aidl_delegate->getConnectionInfo(name, _aidl_return);
  }
  ::android::binder::Status registerClientCallback(const ::std::string& name, const ::android::sp<::android::IBinder>& service, const ::android::sp<::android::os::IClientCallback>& callback) override {
    return _aidl_delegate->registerClientCallback(name, service, callback);
  }
  ::android::binder::Status tryUnregisterService(const ::std::string& name, const ::android::sp<::android::IBinder>& service) override {
    return _aidl_delegate->tryUnregisterService(name, service);
  }
  ::android::binder::Status getServiceDebugInfo(::std::vector<::android::os::ServiceDebugInfo>* _aidl_return) override {
    return _aidl_delegate->getServiceDebugInfo(_aidl_return);
  }
private:
  ::android::sp<IServiceManager> _aidl_delegate;
};  // class IServiceManagerDelegator
}  // namespace os
}  // namespace android
