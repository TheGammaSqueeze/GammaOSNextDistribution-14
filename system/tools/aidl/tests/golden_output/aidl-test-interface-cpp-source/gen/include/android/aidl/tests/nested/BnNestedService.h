/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/nested/INestedService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
 */
#pragma once

#include <binder/IInterface.h>
#include <android/aidl/tests/nested/INestedService.h>
#include <android/aidl/tests/nested/BnNestedService.h>
#include <android/aidl/tests/nested/INestedService.h>
#include <binder/Delegate.h>


namespace android {
namespace aidl {
namespace tests {
namespace nested {
class BnNestedService : public ::android::BnInterface<INestedService> {
public:
  static constexpr uint32_t TRANSACTION_flipStatus = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_flipStatusWithCallback = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  explicit BnNestedService();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnNestedService

class INestedServiceDelegator : public BnNestedService {
public:
  explicit INestedServiceDelegator(const ::android::sp<INestedService> &impl) : _aidl_delegate(impl) {}

  ::android::sp<INestedService> getImpl() { return _aidl_delegate; }
  ::android::binder::Status flipStatus(const ::android::aidl::tests::nested::ParcelableWithNested& p, ::android::aidl::tests::nested::INestedService::Result* _aidl_return) override {
    return _aidl_delegate->flipStatus(p, _aidl_return);
  }
  ::android::binder::Status flipStatusWithCallback(::android::aidl::tests::nested::ParcelableWithNested::Status status, const ::android::sp<::android::aidl::tests::nested::INestedService::ICallback>& cb) override {
    ::android::sp<::android::aidl::tests::nested::INestedService::ICallbackDelegator> _cb;
    if (cb) {
      _cb = ::android::sp<::android::aidl::tests::nested::INestedService::ICallbackDelegator>::cast(delegate(cb));
    }
    return _aidl_delegate->flipStatusWithCallback(status, _cb);
  }
private:
  ::android::sp<INestedService> _aidl_delegate;
};  // class INestedServiceDelegator
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
