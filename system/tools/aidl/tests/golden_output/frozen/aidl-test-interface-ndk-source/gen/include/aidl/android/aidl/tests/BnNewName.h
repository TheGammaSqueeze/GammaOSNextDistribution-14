/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/INewName.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INewName.aidl
 */
#pragma once

#include "aidl/android/aidl/tests/INewName.h"

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
namespace tests {
class BnNewName : public ::ndk::BnCInterface<INewName> {
public:
  BnNewName();
  virtual ~BnNewName();
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class INewNameDelegator : public BnNewName {
public:
  explicit INewNameDelegator(const std::shared_ptr<INewName> &impl) : _impl(impl) {
  }

  ::ndk::ScopedAStatus RealName(std::string* _aidl_return) override {
    return _impl->RealName(_aidl_return);
  }
protected:
private:
  std::shared_ptr<INewName> _impl;
};

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
