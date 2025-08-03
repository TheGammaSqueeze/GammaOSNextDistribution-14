/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/INewName.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INewName.aidl
 */
#pragma once

#include <binder/IInterface.h>
#include <android/aidl/tests/INewName.h>
#include <android/aidl/tests/BnNewName.h>
#include <binder/Delegate.h>


namespace android {
namespace aidl {
namespace tests {
class BnNewName : public ::android::BnInterface<INewName> {
public:
  static constexpr uint32_t TRANSACTION_RealName = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnNewName();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnNewName

class INewNameDelegator : public BnNewName {
public:
  explicit INewNameDelegator(const ::android::sp<INewName> &impl) : _aidl_delegate(impl) {}

  ::android::sp<INewName> getImpl() { return _aidl_delegate; }
  ::android::binder::Status RealName(::android::String16* _aidl_return) override {
    return _aidl_delegate->RealName(_aidl_return);
  }
private:
  ::android::sp<INewName> _aidl_delegate;
};  // class INewNameDelegator
}  // namespace tests
}  // namespace aidl
}  // namespace android
