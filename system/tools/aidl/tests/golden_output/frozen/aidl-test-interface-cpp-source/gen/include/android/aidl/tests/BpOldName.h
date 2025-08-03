/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/IOldName.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IOldName.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/tests/IOldName.h>

namespace android {
namespace aidl {
namespace tests {
class BpOldName : public ::android::BpInterface<IOldName> {
public:
  explicit BpOldName(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpOldName() = default;
  ::android::binder::Status RealName(::android::String16* _aidl_return) override;
};  // class BpOldName
}  // namespace tests
}  // namespace aidl
}  // namespace android
