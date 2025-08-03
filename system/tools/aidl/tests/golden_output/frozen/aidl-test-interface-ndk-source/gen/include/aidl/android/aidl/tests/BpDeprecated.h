/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/IDeprecated.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IDeprecated.aidl
 */
#pragma once

#include "aidl/android/aidl/tests/IDeprecated.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class __attribute__((deprecated("test"))) BpDeprecated : public ::ndk::BpCInterface<IDeprecated> {
public:
  explicit BpDeprecated(const ::ndk::SpAIBinder& binder);
  virtual ~BpDeprecated();

};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
