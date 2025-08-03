/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/ICircular.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICircular.aidl
 */
#pragma once

#include <android/aidl/tests/ITestService.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <optional>
#include <utils/StrongPointer.h>

namespace android::aidl::tests {
class ITestService;
}  // namespace android::aidl::tests
namespace android {
namespace aidl {
namespace tests {
class ICircularDelegator;

class ICircular : public ::android::IInterface {
public:
  typedef ICircularDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(Circular)
  virtual ::android::binder::Status GetTestService(::android::sp<::android::aidl::tests::ITestService>* _aidl_return) = 0;
};  // class ICircular

class ICircularDefault : public ICircular {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status GetTestService(::android::sp<::android::aidl::tests::ITestService>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ICircularDefault
}  // namespace tests
}  // namespace aidl
}  // namespace android
