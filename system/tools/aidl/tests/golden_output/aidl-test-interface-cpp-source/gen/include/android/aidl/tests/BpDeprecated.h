/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/IDeprecated.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IDeprecated.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/tests/IDeprecated.h>

namespace android {
namespace aidl {
namespace tests {
class __attribute__((deprecated("test"))) BpDeprecated : public ::android::BpInterface<IDeprecated> {
public:
  explicit BpDeprecated(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpDeprecated() = default;
};  // class BpDeprecated
}  // namespace tests
}  // namespace aidl
}  // namespace android
