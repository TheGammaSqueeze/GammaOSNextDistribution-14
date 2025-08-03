/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ITestService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
#pragma once

#include "aidl/android/aidl/tests/ITestService.h"

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
class BnTestService : public ::ndk::BnCInterface<ITestService> {
public:
  BnTestService();
  virtual ~BnTestService();
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ITestServiceDelegator : public BnTestService {
public:
  explicit ITestServiceDelegator(const std::shared_ptr<ITestService> &impl) : _impl(impl) {
  }

  ::ndk::ScopedAStatus UnimplementedMethod(int32_t in_arg, int32_t* _aidl_return) override {
    return _impl->UnimplementedMethod(in_arg, _aidl_return);
  }
  ::ndk::ScopedAStatus Deprecated() override __attribute__((deprecated("to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"))) {
    return _impl->Deprecated();
  }
  ::ndk::ScopedAStatus TestOneway() override {
    return _impl->TestOneway();
  }
  ::ndk::ScopedAStatus RepeatBoolean(bool in_token, bool* _aidl_return) override {
    return _impl->RepeatBoolean(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatByte(int8_t in_token, int8_t* _aidl_return) override {
    return _impl->RepeatByte(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatChar(char16_t in_token, char16_t* _aidl_return) override {
    return _impl->RepeatChar(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatInt(int32_t in_token, int32_t* _aidl_return) override {
    return _impl->RepeatInt(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatLong(int64_t in_token, int64_t* _aidl_return) override {
    return _impl->RepeatLong(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatFloat(float in_token, float* _aidl_return) override {
    return _impl->RepeatFloat(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatDouble(double in_token, double* _aidl_return) override {
    return _impl->RepeatDouble(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatString(const std::string& in_token, std::string* _aidl_return) override {
    return _impl->RepeatString(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatByteEnum(::aidl::android::aidl::tests::ByteEnum in_token, ::aidl::android::aidl::tests::ByteEnum* _aidl_return) override {
    return _impl->RepeatByteEnum(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatIntEnum(::aidl::android::aidl::tests::IntEnum in_token, ::aidl::android::aidl::tests::IntEnum* _aidl_return) override {
    return _impl->RepeatIntEnum(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatLongEnum(::aidl::android::aidl::tests::LongEnum in_token, ::aidl::android::aidl::tests::LongEnum* _aidl_return) override {
    return _impl->RepeatLongEnum(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseBoolean(const std::vector<bool>& in_input, std::vector<bool>* out_repeated, std::vector<bool>* _aidl_return) override {
    return _impl->ReverseBoolean(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseByte(const std::vector<uint8_t>& in_input, std::vector<uint8_t>* out_repeated, std::vector<uint8_t>* _aidl_return) override {
    return _impl->ReverseByte(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseChar(const std::vector<char16_t>& in_input, std::vector<char16_t>* out_repeated, std::vector<char16_t>* _aidl_return) override {
    return _impl->ReverseChar(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseInt(const std::vector<int32_t>& in_input, std::vector<int32_t>* out_repeated, std::vector<int32_t>* _aidl_return) override {
    return _impl->ReverseInt(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseLong(const std::vector<int64_t>& in_input, std::vector<int64_t>* out_repeated, std::vector<int64_t>* _aidl_return) override {
    return _impl->ReverseLong(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseFloat(const std::vector<float>& in_input, std::vector<float>* out_repeated, std::vector<float>* _aidl_return) override {
    return _impl->ReverseFloat(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseDouble(const std::vector<double>& in_input, std::vector<double>* out_repeated, std::vector<double>* _aidl_return) override {
    return _impl->ReverseDouble(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseString(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) override {
    return _impl->ReverseString(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseByteEnum(const std::vector<::aidl::android::aidl::tests::ByteEnum>& in_input, std::vector<::aidl::android::aidl::tests::ByteEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::ByteEnum>* _aidl_return) override {
    return _impl->ReverseByteEnum(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseIntEnum(const std::vector<::aidl::android::aidl::tests::IntEnum>& in_input, std::vector<::aidl::android::aidl::tests::IntEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::IntEnum>* _aidl_return) override {
    return _impl->ReverseIntEnum(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseLongEnum(const std::vector<::aidl::android::aidl::tests::LongEnum>& in_input, std::vector<::aidl::android::aidl::tests::LongEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::LongEnum>* _aidl_return) override {
    return _impl->ReverseLongEnum(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus GetOtherTestService(const std::string& in_name, std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>* _aidl_return) override {
    return _impl->GetOtherTestService(in_name, _aidl_return);
  }
  ::ndk::ScopedAStatus SetOtherTestService(const std::string& in_name, const std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>& in_service, bool* _aidl_return) override {
    return _impl->SetOtherTestService(in_name, in_service, _aidl_return);
  }
  ::ndk::ScopedAStatus VerifyName(const std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>& in_service, const std::string& in_name, bool* _aidl_return) override {
    return _impl->VerifyName(in_service, in_name, _aidl_return);
  }
  ::ndk::ScopedAStatus GetInterfaceArray(const std::vector<std::string>& in_names, std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>* _aidl_return) override {
    return _impl->GetInterfaceArray(in_names, _aidl_return);
  }
  ::ndk::ScopedAStatus VerifyNamesWithInterfaceArray(const std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>& in_services, const std::vector<std::string>& in_names, bool* _aidl_return) override {
    return _impl->VerifyNamesWithInterfaceArray(in_services, in_names, _aidl_return);
  }
  ::ndk::ScopedAStatus GetNullableInterfaceArray(const std::optional<std::vector<std::optional<std::string>>>& in_names, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>* _aidl_return) override {
    return _impl->GetNullableInterfaceArray(in_names, _aidl_return);
  }
  ::ndk::ScopedAStatus VerifyNamesWithNullableInterfaceArray(const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>& in_services, const std::optional<std::vector<std::optional<std::string>>>& in_names, bool* _aidl_return) override {
    return _impl->VerifyNamesWithNullableInterfaceArray(in_services, in_names, _aidl_return);
  }
  ::ndk::ScopedAStatus GetInterfaceList(const std::optional<std::vector<std::optional<std::string>>>& in_names, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>* _aidl_return) override {
    return _impl->GetInterfaceList(in_names, _aidl_return);
  }
  ::ndk::ScopedAStatus VerifyNamesWithInterfaceList(const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>& in_services, const std::optional<std::vector<std::optional<std::string>>>& in_names, bool* _aidl_return) override {
    return _impl->VerifyNamesWithInterfaceList(in_services, in_names, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseStringList(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) override {
    return _impl->ReverseStringList(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatParcelFileDescriptor(const ::ndk::ScopedFileDescriptor& in_read, ::ndk::ScopedFileDescriptor* _aidl_return) override {
    return _impl->RepeatParcelFileDescriptor(in_read, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseParcelFileDescriptorArray(const std::vector<::ndk::ScopedFileDescriptor>& in_input, std::vector<::ndk::ScopedFileDescriptor>* out_repeated, std::vector<::ndk::ScopedFileDescriptor>* _aidl_return) override {
    return _impl->ReverseParcelFileDescriptorArray(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ThrowServiceException(int32_t in_code) override {
    return _impl->ThrowServiceException(in_code);
  }
  ::ndk::ScopedAStatus RepeatNullableIntArray(const std::optional<std::vector<int32_t>>& in_input, std::optional<std::vector<int32_t>>* _aidl_return) override {
    return _impl->RepeatNullableIntArray(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableByteEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::ByteEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::ByteEnum>>* _aidl_return) override {
    return _impl->RepeatNullableByteEnumArray(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableIntEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::IntEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::IntEnum>>* _aidl_return) override {
    return _impl->RepeatNullableIntEnumArray(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableLongEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::LongEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::LongEnum>>* _aidl_return) override {
    return _impl->RepeatNullableLongEnumArray(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableString(const std::optional<std::string>& in_input, std::optional<std::string>* _aidl_return) override {
    return _impl->RepeatNullableString(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableStringList(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) override {
    return _impl->RepeatNullableStringList(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableParcelable(const std::optional<::aidl::android::aidl::tests::ITestService::Empty>& in_input, std::optional<::aidl::android::aidl::tests::ITestService::Empty>* _aidl_return) override {
    return _impl->RepeatNullableParcelable(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableParcelableArray(const std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>& in_input, std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override {
    return _impl->RepeatNullableParcelableArray(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableParcelableList(const std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>& in_input, std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override {
    return _impl->RepeatNullableParcelableList(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus TakesAnIBinder(const ::ndk::SpAIBinder& in_input) override {
    return _impl->TakesAnIBinder(in_input);
  }
  ::ndk::ScopedAStatus TakesANullableIBinder(const ::ndk::SpAIBinder& in_input) override {
    return _impl->TakesANullableIBinder(in_input);
  }
  ::ndk::ScopedAStatus TakesAnIBinderList(const std::vector<::ndk::SpAIBinder>& in_input) override {
    return _impl->TakesAnIBinderList(in_input);
  }
  ::ndk::ScopedAStatus TakesANullableIBinderList(const std::optional<std::vector<::ndk::SpAIBinder>>& in_input) override {
    return _impl->TakesANullableIBinderList(in_input);
  }
  ::ndk::ScopedAStatus RepeatUtf8CppString(const std::string& in_token, std::string* _aidl_return) override {
    return _impl->RepeatUtf8CppString(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatNullableUtf8CppString(const std::optional<std::string>& in_token, std::optional<std::string>* _aidl_return) override {
    return _impl->RepeatNullableUtf8CppString(in_token, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseUtf8CppString(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) override {
    return _impl->ReverseUtf8CppString(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseNullableUtf8CppString(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* out_repeated, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) override {
    return _impl->ReverseNullableUtf8CppString(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseUtf8CppStringList(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* out_repeated, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) override {
    return _impl->ReverseUtf8CppStringList(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus GetCallback(bool in_return_null, std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>* _aidl_return) override {
    return _impl->GetCallback(in_return_null, _aidl_return);
  }
  ::ndk::ScopedAStatus FillOutStructuredParcelable(::aidl::android::aidl::tests::StructuredParcelable* in_parcel) override {
    return _impl->FillOutStructuredParcelable(in_parcel);
  }
  ::ndk::ScopedAStatus RepeatExtendableParcelable(const ::aidl::android::aidl::tests::extension::ExtendableParcelable& in_ep, ::aidl::android::aidl::tests::extension::ExtendableParcelable* out_ep2) override {
    return _impl->RepeatExtendableParcelable(in_ep, out_ep2);
  }
  ::ndk::ScopedAStatus ReverseList(const ::aidl::android::aidl::tests::RecursiveList& in_list, ::aidl::android::aidl::tests::RecursiveList* _aidl_return) override {
    return _impl->ReverseList(in_list, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseIBinderArray(const std::vector<::ndk::SpAIBinder>& in_input, std::vector<::ndk::SpAIBinder>* out_repeated, std::vector<::ndk::SpAIBinder>* _aidl_return) override {
    return _impl->ReverseIBinderArray(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseNullableIBinderArray(const std::optional<std::vector<::ndk::SpAIBinder>>& in_input, std::optional<std::vector<::ndk::SpAIBinder>>* out_repeated, std::optional<std::vector<::ndk::SpAIBinder>>* _aidl_return) override {
    return _impl->ReverseNullableIBinderArray(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus RepeatSimpleParcelable(const ::aidl::android::aidl::tests::SimpleParcelable& in_input, ::aidl::android::aidl::tests::SimpleParcelable* out_repeat, ::aidl::android::aidl::tests::SimpleParcelable* _aidl_return) override {
    return _impl->RepeatSimpleParcelable(in_input, out_repeat, _aidl_return);
  }
  ::ndk::ScopedAStatus ReverseSimpleParcelables(const std::vector<::aidl::android::aidl::tests::SimpleParcelable>& in_input, std::vector<::aidl::android::aidl::tests::SimpleParcelable>* out_repeated, std::vector<::aidl::android::aidl::tests::SimpleParcelable>* _aidl_return) override {
    return _impl->ReverseSimpleParcelables(in_input, out_repeated, _aidl_return);
  }
  ::ndk::ScopedAStatus GetOldNameInterface(std::shared_ptr<::aidl::android::aidl::tests::IOldName>* _aidl_return) override {
    return _impl->GetOldNameInterface(_aidl_return);
  }
  ::ndk::ScopedAStatus GetNewNameInterface(std::shared_ptr<::aidl::android::aidl::tests::INewName>* _aidl_return) override {
    return _impl->GetNewNameInterface(_aidl_return);
  }
  ::ndk::ScopedAStatus GetUnionTags(const std::vector<::aidl::android::aidl::tests::Union>& in_input, std::vector<::aidl::android::aidl::tests::Union::Tag>* _aidl_return) override {
    return _impl->GetUnionTags(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus GetCppJavaTests(::ndk::SpAIBinder* _aidl_return) override {
    return _impl->GetCppJavaTests(_aidl_return);
  }
  ::ndk::ScopedAStatus getBackendType(::aidl::android::aidl::tests::BackendType* _aidl_return) override {
    return _impl->getBackendType(_aidl_return);
  }
  ::ndk::ScopedAStatus GetCircular(::aidl::android::aidl::tests::CircularParcelable* out_cp, std::shared_ptr<::aidl::android::aidl::tests::ICircular>* _aidl_return) override {
    return _impl->GetCircular(out_cp, _aidl_return);
  }
protected:
private:
  std::shared_ptr<ITestService> _impl;
};

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
