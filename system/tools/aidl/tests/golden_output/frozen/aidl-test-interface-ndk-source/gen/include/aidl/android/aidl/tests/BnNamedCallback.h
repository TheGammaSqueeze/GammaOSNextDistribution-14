/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/INamedCallback.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INamedCallback.aidl
 */
#pragma once

#include "aidl/android/aidl/tests/INamedCallback.h"

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
class BnNamedCallback : public ::ndk::BnCInterface<INamedCallback> {
public:
  BnNamedCallback();
  virtual ~BnNamedCallback();
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class INamedCallbackDelegator : public BnNamedCallback {
public:
  explicit INamedCallbackDelegator(const std::shared_ptr<INamedCallback> &impl) : _impl(impl) {
  }

  ::ndk::ScopedAStatus GetName(std::string* _aidl_return) override {
    return _impl->GetName(_aidl_return);
  }
protected:
private:
  std::shared_ptr<INamedCallback> _impl;
};

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
