/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp --structured --version 2 --hash da8c4bc94ca7feff0e0a65563a466787698b5891 -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-cpp-source/gen/staging/android/aidl/versioned/tests/IFooInterface.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V2-cpp-source/gen/staging -Nsystem/tools/aidl/aidl_api/aidl-test-versioned-interface/2 system/tools/aidl/aidl_api/aidl-test-versioned-interface/2/android/aidl/versioned/tests/IFooInterface.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/versioned/tests/IFooInterface.h>

namespace android {
namespace aidl {
namespace versioned {
namespace tests {
class BpFooInterface : public ::android::BpInterface<IFooInterface> {
public:
  explicit BpFooInterface(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpFooInterface() = default;
  ::android::binder::Status originalApi() override;
  ::android::binder::Status acceptUnionAndReturnString(const ::android::aidl::versioned::tests::BazUnion& u, ::std::string* _aidl_return) override;
  ::android::binder::Status ignoreParcelablesAndRepeatInt(const ::android::aidl::versioned::tests::Foo& inFoo, ::android::aidl::versioned::tests::Foo* inoutFoo, ::android::aidl::versioned::tests::Foo* outFoo, int32_t value, int32_t* _aidl_return) override;
  ::android::binder::Status returnsLengthOfFooArray(const ::std::vector<::android::aidl::versioned::tests::Foo>& foos, int32_t* _aidl_return) override;
  ::android::binder::Status newApi() override;
  int32_t getInterfaceVersion() override;
  std::string getInterfaceHash() override;
private:
  int32_t cached_version_ = -1;
  std::string cached_hash_ = "-1";
  std::mutex cached_hash_mutex_;
};  // class BpFooInterface
}  // namespace tests
}  // namespace versioned
}  // namespace aidl
}  // namespace android
