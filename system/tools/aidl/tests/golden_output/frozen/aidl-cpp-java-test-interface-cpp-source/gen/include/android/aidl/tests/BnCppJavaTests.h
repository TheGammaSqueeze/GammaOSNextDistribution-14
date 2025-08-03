/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -t --min_sdk_version current -pout/soong/.intermediates/system/tools/aidl/aidl-test-interface_interface/preprocessed.aidl --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/staging/android/aidl/tests/ICppJavaTests.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-cpp-java-test-interface-cpp-source/gen/staging -Iframeworks/native/aidl/binder -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICppJavaTests.aidl
 */
#pragma once

#include <binder/IInterface.h>
#include <android/aidl/tests/ICppJavaTests.h>
#include <android/aidl/tests/BnCppJavaTests.h>
#include <binder/Delegate.h>


namespace android {
namespace aidl {
namespace tests {
class BnCppJavaTests : public ::android::BnInterface<ICppJavaTests> {
public:
  static constexpr uint32_t TRANSACTION_RepeatBadParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_RepeatGenericParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_RepeatPersistableBundle = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_ReversePersistableBundles = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_ReverseUnion = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_ReverseNamedCallbackList = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_RepeatFileDescriptor = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_ReverseFileDescriptorArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 7;
  explicit BnCppJavaTests();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnCppJavaTests

class ICppJavaTestsDelegator : public BnCppJavaTests {
public:
  explicit ICppJavaTestsDelegator(const ::android::sp<ICppJavaTests> &impl) : _aidl_delegate(impl) {}

  ::android::sp<ICppJavaTests> getImpl() { return _aidl_delegate; }
  ::android::binder::Status RepeatBadParcelable(const ::android::aidl::tests::BadParcelable& input, ::android::aidl::tests::BadParcelable* _aidl_return) override {
    return _aidl_delegate->RepeatBadParcelable(input, _aidl_return);
  }
  ::android::binder::Status RepeatGenericParcelable(const ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>& input, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* repeat, ::android::aidl::tests::GenericStructuredParcelable<int32_t, ::android::aidl::tests::StructuredParcelable, ::android::aidl::tests::IntEnum>* _aidl_return) override {
    return _aidl_delegate->RepeatGenericParcelable(input, repeat, _aidl_return);
  }
  ::android::binder::Status RepeatPersistableBundle(const ::android::os::PersistableBundle& input, ::android::os::PersistableBundle* _aidl_return) override {
    return _aidl_delegate->RepeatPersistableBundle(input, _aidl_return);
  }
  ::android::binder::Status ReversePersistableBundles(const ::std::vector<::android::os::PersistableBundle>& input, ::std::vector<::android::os::PersistableBundle>* repeated, ::std::vector<::android::os::PersistableBundle>* _aidl_return) override {
    return _aidl_delegate->ReversePersistableBundles(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseUnion(const ::android::aidl::tests::Union& input, ::android::aidl::tests::Union* repeated, ::android::aidl::tests::Union* _aidl_return) override {
    return _aidl_delegate->ReverseUnion(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseNamedCallbackList(const ::std::vector<::android::sp<::android::IBinder>>& input, ::std::vector<::android::sp<::android::IBinder>>* repeated, ::std::vector<::android::sp<::android::IBinder>>* _aidl_return) override {
    return _aidl_delegate->ReverseNamedCallbackList(input, repeated, _aidl_return);
  }
  ::android::binder::Status RepeatFileDescriptor(::android::base::unique_fd read, ::android::base::unique_fd* _aidl_return) override {
    return _aidl_delegate->RepeatFileDescriptor(std::move(read), _aidl_return);
  }
  ::android::binder::Status ReverseFileDescriptorArray(const ::std::vector<::android::base::unique_fd>& input, ::std::vector<::android::base::unique_fd>* repeated, ::std::vector<::android::base::unique_fd>* _aidl_return) override {
    return _aidl_delegate->ReverseFileDescriptorArray(input, repeated, _aidl_return);
  }
private:
  ::android::sp<ICppJavaTests> _aidl_delegate;
};  // class ICppJavaTestsDelegator
}  // namespace tests
}  // namespace aidl
}  // namespace android
