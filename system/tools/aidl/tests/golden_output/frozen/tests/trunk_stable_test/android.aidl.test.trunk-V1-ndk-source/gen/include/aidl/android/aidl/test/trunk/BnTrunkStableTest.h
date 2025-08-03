/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk --structured --version 1 --hash 88311b9118fb6fe9eff4a2ca19121de0587f6d5f -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-ndk-source/gen/staging/android/aidl/test/trunk/ITrunkStableTest.cpp.d -h out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-ndk-source/gen/staging -Nsystem/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1 system/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#pragma once

#include "aidl/android/aidl/test/trunk/ITrunkStableTest.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace aidl {
namespace test {
namespace trunk {
class BnTrunkStableTest : public ::ndk::BnCInterface<ITrunkStableTest> {
public:
  BnTrunkStableTest();
  virtual ~BnTrunkStableTest();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
  struct TransactionLog {
    double duration_ms;
    std::string interface_name;
    std::string method_name;
    const void* proxy_address;
    const void* stub_address;
    std::vector<std::pair<std::string, std::string>> input_args;
    std::vector<std::pair<std::string, std::string>> output_args;
    std::string result;
    std::string exception_message;
    int32_t exception_code;
    int32_t transaction_error;
    int32_t service_specific_error_code;
  };
  static std::function<void(const TransactionLog&)> logFunc;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ITrunkStableTestDelegator : public BnTrunkStableTest {
public:
  explicit ITrunkStableTestDelegator(const std::shared_ptr<ITrunkStableTest> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ITrunkStableTest::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override {
    return _impl->repeatParcelable(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override {
    return _impl->repeatEnum(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override {
    return _impl->repeatUnion(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus callMyCallback(const std::shared_ptr<::aidl::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& in_cb) override {
    return _impl->callMyCallback(in_cb);
  }
protected:
private:
  std::shared_ptr<ITrunkStableTest> _impl;
};

}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
