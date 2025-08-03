/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp --structured --version 3 --hash 70d76c61eb0c82288e924862c10b910d1b7d8cf8 -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V3-cpp-source/gen/staging/android/aidl/versioned/tests/IFooInterface.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V3-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-versioned-interface-V3-cpp-source/gen/staging -Nsystem/tools/aidl/aidl_api/aidl-test-versioned-interface/3 system/tools/aidl/aidl_api/aidl-test-versioned-interface/3/android/aidl/versioned/tests/IFooInterface.aidl
 */
#pragma once

#include <android/aidl/versioned/tests/BazUnion.h>
#include <android/aidl/versioned/tests/Foo.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cstdint>
#include <string>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <vector>

namespace android::aidl::versioned::tests {
class Foo;
}  // namespace android::aidl::versioned::tests
namespace android {
namespace aidl {
namespace versioned {
namespace tests {
class IFooInterfaceDelegator;

class IFooInterface : public ::android::IInterface {
public:
  typedef IFooInterfaceDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(FooInterface)
  static inline const int32_t VERSION = 3;
  static inline const std::string HASH = "70d76c61eb0c82288e924862c10b910d1b7d8cf8";
  virtual ::android::binder::Status originalApi() = 0;
  virtual ::android::binder::Status acceptUnionAndReturnString(const ::android::aidl::versioned::tests::BazUnion& u, ::std::string* _aidl_return) = 0;
  virtual ::android::binder::Status ignoreParcelablesAndRepeatInt(const ::android::aidl::versioned::tests::Foo& inFoo, ::android::aidl::versioned::tests::Foo* inoutFoo, ::android::aidl::versioned::tests::Foo* outFoo, int32_t value, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status returnsLengthOfFooArray(const ::std::vector<::android::aidl::versioned::tests::Foo>& foos, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status newApi() = 0;
  virtual int32_t getInterfaceVersion() = 0;
  virtual std::string getInterfaceHash() = 0;
};  // class IFooInterface

class IFooInterfaceDefault : public IFooInterface {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status originalApi() override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status acceptUnionAndReturnString(const ::android::aidl::versioned::tests::BazUnion& /*u*/, ::std::string* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ignoreParcelablesAndRepeatInt(const ::android::aidl::versioned::tests::Foo& /*inFoo*/, ::android::aidl::versioned::tests::Foo* /*inoutFoo*/, ::android::aidl::versioned::tests::Foo* /*outFoo*/, int32_t /*value*/, int32_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status returnsLengthOfFooArray(const ::std::vector<::android::aidl::versioned::tests::Foo>& /*foos*/, int32_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status newApi() override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  int32_t getInterfaceVersion() override {
    return 0;
  }
  std::string getInterfaceHash() override {
    return "";
  }
};  // class IFooInterfaceDefault
}  // namespace tests
}  // namespace versioned
}  // namespace aidl
}  // namespace android
