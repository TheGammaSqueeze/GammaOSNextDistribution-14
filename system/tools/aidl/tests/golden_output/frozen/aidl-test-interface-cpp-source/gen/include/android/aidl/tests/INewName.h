/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/INewName.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INewName.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>

namespace android {
namespace aidl {
namespace tests {
class INewNameDelegator;

class INewName : public ::android::IInterface {
public:
  typedef INewNameDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(NewName)
  virtual ::android::binder::Status RealName(::android::String16* _aidl_return) = 0;
};  // class INewName

class INewNameDefault : public INewName {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status RealName(::android::String16* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class INewNameDefault
}  // namespace tests
}  // namespace aidl
}  // namespace android
