/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp --structured --version 1 --hash 88311b9118fb6fe9eff4a2ca19121de0587f6d5f -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-cpp-source/gen/staging/android/aidl/test/trunk/ITrunkStableTest.cpp.d -h out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-cpp-source/gen/staging -Nsystem/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1 system/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#pragma once

#include <binder/IInterface.h>
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <functional>
#include <android/binder_to_string.h>
#include <android/aidl/test/trunk/BnTrunkStableTest.h>
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <binder/Delegate.h>


namespace android {
namespace aidl {
namespace test {
namespace trunk {
class BnTrunkStableTest : public ::android::BnInterface<ITrunkStableTest> {
public:
  static constexpr uint32_t TRANSACTION_repeatParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_repeatEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_repeatUnion = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_callMyCallback = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getInterfaceVersion = ::android::IBinder::FIRST_CALL_TRANSACTION + 16777214;
  static constexpr uint32_t TRANSACTION_getInterfaceHash = ::android::IBinder::FIRST_CALL_TRANSACTION + 16777213;
  explicit BnTrunkStableTest();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
  int32_t getInterfaceVersion();
  std::string getInterfaceHash();
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
};  // class BnTrunkStableTest

class ITrunkStableTestDelegator : public BnTrunkStableTest {
public:
  explicit ITrunkStableTestDelegator(const ::android::sp<ITrunkStableTest> &impl) : _aidl_delegate(impl) {}

  ::android::sp<ITrunkStableTest> getImpl() { return _aidl_delegate; }
  ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override {
    return _aidl_delegate->repeatParcelable(input, _aidl_return);
  }
  ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override {
    return _aidl_delegate->repeatEnum(input, _aidl_return);
  }
  ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override {
    return _aidl_delegate->repeatUnion(input, _aidl_return);
  }
  ::android::binder::Status callMyCallback(const ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& cb) override {
    ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallbackDelegator> _cb;
    if (cb) {
      _cb = ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallbackDelegator>::cast(delegate(cb));
    }
    return _aidl_delegate->callMyCallback(_cb);
  }
  int32_t getInterfaceVersion() override {
    int32_t _delegator_ver = BnTrunkStableTest::getInterfaceVersion();
    int32_t _impl_ver = _aidl_delegate->getInterfaceVersion();
    return _delegator_ver < _impl_ver ? _delegator_ver : _impl_ver;
  }
  std::string getInterfaceHash() override {
    return _aidl_delegate->getInterfaceHash();
  }
private:
  ::android::sp<ITrunkStableTest> _aidl_delegate;
};  // class ITrunkStableTestDelegator
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
