/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/nested/INestedService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
 */
#pragma once

#include "aidl/android/aidl/tests/nested/INestedService.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
namespace nested {
class BpNestedService : public ::ndk::BpCInterface<INestedService> {
public:
  explicit BpNestedService(const ::ndk::SpAIBinder& binder);
  virtual ~BpNestedService();

  ::ndk::ScopedAStatus flipStatus(const ::aidl::android::aidl::tests::nested::ParcelableWithNested& in_p, ::aidl::android::aidl::tests::nested::INestedService::Result* _aidl_return) override;
  ::ndk::ScopedAStatus flipStatusWithCallback(::aidl::android::aidl::tests::nested::ParcelableWithNested::Status in_status, const std::shared_ptr<::aidl::android::aidl::tests::nested::INestedService::ICallback>& in_cb) override;
};
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
