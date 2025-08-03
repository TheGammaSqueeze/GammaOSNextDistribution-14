/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/IDeprecated.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IDeprecated.aidl
 */
#pragma once

#include <binder/IInterface.h>
#include <android/aidl/tests/IDeprecated.h>
#include <android/aidl/tests/BnDeprecated.h>
#include <binder/Delegate.h>


namespace android {
namespace aidl {
namespace tests {
class __attribute__((deprecated("test"))) BnDeprecated : public ::android::BnInterface<IDeprecated> {
public:
  explicit BnDeprecated();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnDeprecated

class __attribute__((deprecated("test"))) IDeprecatedDelegator : public BnDeprecated {
public:
  explicit IDeprecatedDelegator(const ::android::sp<IDeprecated> &impl) : _aidl_delegate(impl) {}

  ::android::sp<IDeprecated> getImpl() { return _aidl_delegate; }
private:
  ::android::sp<IDeprecated> _aidl_delegate;
};  // class IDeprecatedDelegator
}  // namespace tests
}  // namespace aidl
}  // namespace android
