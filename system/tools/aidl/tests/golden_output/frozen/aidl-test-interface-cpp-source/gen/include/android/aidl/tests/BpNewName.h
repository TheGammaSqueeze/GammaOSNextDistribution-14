/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/INewName.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INewName.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/tests/INewName.h>

namespace android {
namespace aidl {
namespace tests {
class BpNewName : public ::android::BpInterface<INewName> {
public:
  explicit BpNewName(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpNewName() = default;
  ::android::binder::Status RealName(::android::String16* _aidl_return) override;
};  // class BpNewName
}  // namespace tests
}  // namespace aidl
}  // namespace android
