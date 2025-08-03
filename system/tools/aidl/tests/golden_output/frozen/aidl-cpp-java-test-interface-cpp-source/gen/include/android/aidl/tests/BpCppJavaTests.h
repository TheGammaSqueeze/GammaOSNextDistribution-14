/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -t --min_sdk_version current -pout/soong/.intermediates/system/tools/aidl/aidl-test-interface_interface/preprocessed.aidl --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/staging/android/aidl/tests/ICppJavaTests.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/staging -Iframeworks/native/aidl/binder -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICppJavaTests.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/tests/ICppJavaTests.h>

namespace android {
namespace aidl {
namespace tests {
class BpCppJavaTests : public ::android::BpInterface<ICppJavaTests> {
public:
  explicit BpCppJavaTests(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpCppJavaTests() = default;
  ::android::binder::Status RepeatBadParcelable(const ::android::aidl::tests::BadParcelable& input, ::android::aidl::tests::BadParcelable* _aidl_return) override;
  ::android::binder::Status RepeatGenericParcelable(const ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>& input, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* repeat, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* _aidl_return) override;
  ::android::binder::Status RepeatPersistableBundle(const ::android::os::PersistableBundle& input, ::android::os::PersistableBundle* _aidl_return) override;
  ::android::binder::Status ReversePersistableBundles(const ::std::vector<::android::os::PersistableBundle>& input, ::std::vector<::android::os::PersistableBundle>* repeated, ::std::vector<::android::os::PersistableBundle>* _aidl_return) override;
  ::android::binder::Status ReverseUnion(const ::android::aidl::tests::Union& input, ::android::aidl::tests::Union* repeated, ::android::aidl::tests::Union* _aidl_return) override;
  ::android::binder::Status ReverseNamedCallbackList(const ::std::vector<::android::sp<::android::IBinder>>& input, ::std::vector<::android::sp<::android::IBinder>>* repeated, ::std::vector<::android::sp<::android::IBinder>>* _aidl_return) override;
  ::android::binder::Status RepeatFileDescriptor(::android::base::unique_fd read, ::android::base::unique_fd* _aidl_return) override;
  ::android::binder::Status ReverseFileDescriptorArray(const ::std::vector<::android::base::unique_fd>& input, ::std::vector<::android::base::unique_fd>* repeated, ::std::vector<::android::base::unique_fd>* _aidl_return) override;
};  // class BpCppJavaTests
}  // namespace tests
}  // namespace aidl
}  // namespace android
