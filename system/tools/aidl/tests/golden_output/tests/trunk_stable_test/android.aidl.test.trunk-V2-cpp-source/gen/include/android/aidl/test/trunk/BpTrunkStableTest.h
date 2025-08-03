/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror --structured --version 2 --hash notfrozen -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/staging/android/aidl/test/trunk/ITrunkStableTest.cpp.d -h out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/staging -Nsystem/tools/aidl/tests/trunk_stable_test system/tools/aidl/tests/trunk_stable_test/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <functional>
#include <android/binder_to_string.h>

namespace android {
namespace aidl {
namespace test {
namespace trunk {
class BpTrunkStableTest : public ::android::BpInterface<ITrunkStableTest> {
public:
  explicit BpTrunkStableTest(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpTrunkStableTest() = default;
  ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override;
  ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override;
  ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override;
  ::android::binder::Status callMyCallback(const ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& cb) override;
  ::android::binder::Status repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) override;
  int32_t getInterfaceVersion() override;
  std::string getInterfaceHash() override;
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
private:
  int32_t cached_version_ = -1;
  std::string cached_hash_ = "-1";
  std::mutex cached_hash_mutex_;
};  // class BpTrunkStableTest
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
