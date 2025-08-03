/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -t --min_sdk_version current -pout/soong/.intermediates/system/tools/aidl/aidl-test-interface_interface/preprocessed.aidl --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/staging/android/aidl/tests/ICppJavaTests.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/staging -Iframeworks/native/aidl/binder -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICppJavaTests.aidl
 */
#pragma once

#include <android-base/unique_fd.h>
#include <android/aidl/tests/GenericStructuredParcelable.h>
#include <android/aidl/tests/IntEnum.h>
#include <android/aidl/tests/StructuredParcelable.h>
#include <android/aidl/tests/Union.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/PersistableBundle.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cstdint>
#include <tests/bad_parcelable.h>
#include <utils/StrongPointer.h>
#include <vector>

namespace android::aidl::tests {
template <typename T, typename U, typename B>
class GenericStructuredParcelable;
class StructuredParcelable;
}  // namespace android::aidl::tests
namespace android {
namespace aidl {
namespace tests {
class ICppJavaTestsDelegator;

class ICppJavaTests : public ::android::IInterface {
public:
  typedef ICppJavaTestsDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(CppJavaTests)
  virtual ::android::binder::Status RepeatBadParcelable(const ::android::aidl::tests::BadParcelable& input, ::android::aidl::tests::BadParcelable* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatGenericParcelable(const ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>& input, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* repeat, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatPersistableBundle(const ::android::os::PersistableBundle& input, ::android::os::PersistableBundle* _aidl_return) = 0;
  virtual ::android::binder::Status ReversePersistableBundles(const ::std::vector<::android::os::PersistableBundle>& input, ::std::vector<::android::os::PersistableBundle>* repeated, ::std::vector<::android::os::PersistableBundle>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseUnion(const ::android::aidl::tests::Union& input, ::android::aidl::tests::Union* repeated, ::android::aidl::tests::Union* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseNamedCallbackList(const ::std::vector<::android::sp<::android::IBinder>>& input, ::std::vector<::android::sp<::android::IBinder>>* repeated, ::std::vector<::android::sp<::android::IBinder>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatFileDescriptor(::android::base::unique_fd read, ::android::base::unique_fd* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseFileDescriptorArray(const ::std::vector<::android::base::unique_fd>& input, ::std::vector<::android::base::unique_fd>* repeated, ::std::vector<::android::base::unique_fd>* _aidl_return) = 0;
};  // class ICppJavaTests

class ICppJavaTestsDefault : public ICppJavaTests {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status RepeatBadParcelable(const ::android::aidl::tests::BadParcelable& /*input*/, ::android::aidl::tests::BadParcelable* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatGenericParcelable(const ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>& /*input*/, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* /*repeat*/, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatPersistableBundle(const ::android::os::PersistableBundle& /*input*/, ::android::os::PersistableBundle* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReversePersistableBundles(const ::std::vector<::android::os::PersistableBundle>& /*input*/, ::std::vector<::android::os::PersistableBundle>* /*repeated*/, ::std::vector<::android::os::PersistableBundle>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseUnion(const ::android::aidl::tests::Union& /*input*/, ::android::aidl::tests::Union* /*repeated*/, ::android::aidl::tests::Union* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseNamedCallbackList(const ::std::vector<::android::sp<::android::IBinder>>& /*input*/, ::std::vector<::android::sp<::android::IBinder>>* /*repeated*/, ::std::vector<::android::sp<::android::IBinder>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatFileDescriptor(::android::base::unique_fd /*read*/, ::android::base::unique_fd* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseFileDescriptorArray(const ::std::vector<::android::base::unique_fd>& /*input*/, ::std::vector<::android::base::unique_fd>* /*repeated*/, ::std::vector<::android::base::unique_fd>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ICppJavaTestsDefault
}  // namespace tests
}  // namespace aidl
}  // namespace android
