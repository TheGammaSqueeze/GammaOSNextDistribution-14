/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/INamedCallback.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INamedCallback.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/tests/INamedCallback.h>

namespace android {
namespace aidl {
namespace tests {
class BpNamedCallback : public ::android::BpInterface<INamedCallback> {
public:
  explicit BpNamedCallback(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpNamedCallback() = default;
  ::android::binder::Status GetName(::android::String16* _aidl_return) override;
};  // class BpNamedCallback
}  // namespace tests
}  // namespace aidl
}  // namespace android
