/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/ITestService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
#pragma once

#include <binder/IInterface.h>
#include <android/aidl/tests/ITestService.h>
#include <android/aidl/tests/BnCircular.h>
#include <android/aidl/tests/BnNamedCallback.h>
#include <android/aidl/tests/BnNewName.h>
#include <android/aidl/tests/BnOldName.h>
#include <android/aidl/tests/BnTestService.h>
#include <android/aidl/tests/ITestService.h>
#include <binder/Delegate.h>


namespace android {
namespace aidl {
namespace tests {
class BnTestService : public ::android::BnInterface<ITestService> {
public:
  static constexpr uint32_t TRANSACTION_UnimplementedMethod = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_Deprecated = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_TestOneway = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_RepeatBoolean = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_RepeatByte = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_RepeatChar = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_RepeatInt = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_RepeatLong = ::android::IBinder::FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_RepeatFloat = ::android::IBinder::FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_RepeatDouble = ::android::IBinder::FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_RepeatString = ::android::IBinder::FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_RepeatByteEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_RepeatIntEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_RepeatLongEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_ReverseBoolean = ::android::IBinder::FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_ReverseByte = ::android::IBinder::FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_ReverseChar = ::android::IBinder::FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_ReverseInt = ::android::IBinder::FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_ReverseLong = ::android::IBinder::FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_ReverseFloat = ::android::IBinder::FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_ReverseDouble = ::android::IBinder::FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_ReverseString = ::android::IBinder::FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_ReverseByteEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_ReverseIntEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_ReverseLongEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_GetOtherTestService = ::android::IBinder::FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_SetOtherTestService = ::android::IBinder::FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_VerifyName = ::android::IBinder::FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_GetInterfaceArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_VerifyNamesWithInterfaceArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_GetNullableInterfaceArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_VerifyNamesWithNullableInterfaceArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_GetInterfaceList = ::android::IBinder::FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_VerifyNamesWithInterfaceList = ::android::IBinder::FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_ReverseStringList = ::android::IBinder::FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_RepeatParcelFileDescriptor = ::android::IBinder::FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_ReverseParcelFileDescriptorArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_ThrowServiceException = ::android::IBinder::FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_RepeatNullableIntArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 38;
  static constexpr uint32_t TRANSACTION_RepeatNullableByteEnumArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 39;
  static constexpr uint32_t TRANSACTION_RepeatNullableIntEnumArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 40;
  static constexpr uint32_t TRANSACTION_RepeatNullableLongEnumArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 41;
  static constexpr uint32_t TRANSACTION_RepeatNullableString = ::android::IBinder::FIRST_CALL_TRANSACTION + 42;
  static constexpr uint32_t TRANSACTION_RepeatNullableStringList = ::android::IBinder::FIRST_CALL_TRANSACTION + 43;
  static constexpr uint32_t TRANSACTION_RepeatNullableParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 44;
  static constexpr uint32_t TRANSACTION_RepeatNullableParcelableArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 45;
  static constexpr uint32_t TRANSACTION_RepeatNullableParcelableList = ::android::IBinder::FIRST_CALL_TRANSACTION + 46;
  static constexpr uint32_t TRANSACTION_TakesAnIBinder = ::android::IBinder::FIRST_CALL_TRANSACTION + 47;
  static constexpr uint32_t TRANSACTION_TakesANullableIBinder = ::android::IBinder::FIRST_CALL_TRANSACTION + 48;
  static constexpr uint32_t TRANSACTION_TakesAnIBinderList = ::android::IBinder::FIRST_CALL_TRANSACTION + 49;
  static constexpr uint32_t TRANSACTION_TakesANullableIBinderList = ::android::IBinder::FIRST_CALL_TRANSACTION + 50;
  static constexpr uint32_t TRANSACTION_RepeatUtf8CppString = ::android::IBinder::FIRST_CALL_TRANSACTION + 51;
  static constexpr uint32_t TRANSACTION_RepeatNullableUtf8CppString = ::android::IBinder::FIRST_CALL_TRANSACTION + 52;
  static constexpr uint32_t TRANSACTION_ReverseUtf8CppString = ::android::IBinder::FIRST_CALL_TRANSACTION + 53;
  static constexpr uint32_t TRANSACTION_ReverseNullableUtf8CppString = ::android::IBinder::FIRST_CALL_TRANSACTION + 54;
  static constexpr uint32_t TRANSACTION_ReverseUtf8CppStringList = ::android::IBinder::FIRST_CALL_TRANSACTION + 55;
  static constexpr uint32_t TRANSACTION_GetCallback = ::android::IBinder::FIRST_CALL_TRANSACTION + 56;
  static constexpr uint32_t TRANSACTION_FillOutStructuredParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 57;
  static constexpr uint32_t TRANSACTION_RepeatExtendableParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 58;
  static constexpr uint32_t TRANSACTION_ReverseList = ::android::IBinder::FIRST_CALL_TRANSACTION + 59;
  static constexpr uint32_t TRANSACTION_ReverseIBinderArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 60;
  static constexpr uint32_t TRANSACTION_ReverseNullableIBinderArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 61;
  static constexpr uint32_t TRANSACTION_RepeatSimpleParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 62;
  static constexpr uint32_t TRANSACTION_ReverseSimpleParcelables = ::android::IBinder::FIRST_CALL_TRANSACTION + 63;
  static constexpr uint32_t TRANSACTION_GetOldNameInterface = ::android::IBinder::FIRST_CALL_TRANSACTION + 64;
  static constexpr uint32_t TRANSACTION_GetNewNameInterface = ::android::IBinder::FIRST_CALL_TRANSACTION + 65;
  static constexpr uint32_t TRANSACTION_GetUnionTags = ::android::IBinder::FIRST_CALL_TRANSACTION + 66;
  static constexpr uint32_t TRANSACTION_GetCppJavaTests = ::android::IBinder::FIRST_CALL_TRANSACTION + 67;
  static constexpr uint32_t TRANSACTION_getBackendType = ::android::IBinder::FIRST_CALL_TRANSACTION + 68;
  static constexpr uint32_t TRANSACTION_GetCircular = ::android::IBinder::FIRST_CALL_TRANSACTION + 69;
  explicit BnTestService();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnTestService

class ITestServiceDelegator : public BnTestService {
public:
  explicit ITestServiceDelegator(const ::android::sp<ITestService> &impl) : _aidl_delegate(impl) {}

  ::android::sp<ITestService> getImpl() { return _aidl_delegate; }
  ::android::binder::Status UnimplementedMethod(int32_t arg, int32_t* _aidl_return) override {
    return _aidl_delegate->UnimplementedMethod(arg, _aidl_return);
  }
  ::android::binder::Status Deprecated() override __attribute__((deprecated("to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"))) {
    return _aidl_delegate->Deprecated();
  }
  ::android::binder::Status TestOneway() override {
    return _aidl_delegate->TestOneway();
  }
  ::android::binder::Status RepeatBoolean(bool token, bool* _aidl_return) override {
    return _aidl_delegate->RepeatBoolean(token, _aidl_return);
  }
  ::android::binder::Status RepeatByte(int8_t token, int8_t* _aidl_return) override {
    return _aidl_delegate->RepeatByte(token, _aidl_return);
  }
  ::android::binder::Status RepeatChar(char16_t token, char16_t* _aidl_return) override {
    return _aidl_delegate->RepeatChar(token, _aidl_return);
  }
  ::android::binder::Status RepeatInt(int32_t token, int32_t* _aidl_return) override {
    return _aidl_delegate->RepeatInt(token, _aidl_return);
  }
  ::android::binder::Status RepeatLong(int64_t token, int64_t* _aidl_return) override {
    return _aidl_delegate->RepeatLong(token, _aidl_return);
  }
  ::android::binder::Status RepeatFloat(float token, float* _aidl_return) override {
    return _aidl_delegate->RepeatFloat(token, _aidl_return);
  }
  ::android::binder::Status RepeatDouble(double token, double* _aidl_return) override {
    return _aidl_delegate->RepeatDouble(token, _aidl_return);
  }
  ::android::binder::Status RepeatString(const ::android::String16& token, ::android::String16* _aidl_return) override {
    return _aidl_delegate->RepeatString(token, _aidl_return);
  }
  ::android::binder::Status RepeatByteEnum(::android::aidl::tests::ByteEnum token, ::android::aidl::tests::ByteEnum* _aidl_return) override {
    return _aidl_delegate->RepeatByteEnum(token, _aidl_return);
  }
  ::android::binder::Status RepeatIntEnum(::android::aidl::tests::IntEnum token, ::android::aidl::tests::IntEnum* _aidl_return) override {
    return _aidl_delegate->RepeatIntEnum(token, _aidl_return);
  }
  ::android::binder::Status RepeatLongEnum(::android::aidl::tests::LongEnum token, ::android::aidl::tests::LongEnum* _aidl_return) override {
    return _aidl_delegate->RepeatLongEnum(token, _aidl_return);
  }
  ::android::binder::Status ReverseBoolean(const ::std::vector<bool>& input, ::std::vector<bool>* repeated, ::std::vector<bool>* _aidl_return) override {
    return _aidl_delegate->ReverseBoolean(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseByte(const ::std::vector<uint8_t>& input, ::std::vector<uint8_t>* repeated, ::std::vector<uint8_t>* _aidl_return) override {
    return _aidl_delegate->ReverseByte(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseChar(const ::std::vector<char16_t>& input, ::std::vector<char16_t>* repeated, ::std::vector<char16_t>* _aidl_return) override {
    return _aidl_delegate->ReverseChar(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseInt(const ::std::vector<int32_t>& input, ::std::vector<int32_t>* repeated, ::std::vector<int32_t>* _aidl_return) override {
    return _aidl_delegate->ReverseInt(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseLong(const ::std::vector<int64_t>& input, ::std::vector<int64_t>* repeated, ::std::vector<int64_t>* _aidl_return) override {
    return _aidl_delegate->ReverseLong(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseFloat(const ::std::vector<float>& input, ::std::vector<float>* repeated, ::std::vector<float>* _aidl_return) override {
    return _aidl_delegate->ReverseFloat(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseDouble(const ::std::vector<double>& input, ::std::vector<double>* repeated, ::std::vector<double>* _aidl_return) override {
    return _aidl_delegate->ReverseDouble(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseString(const ::std::vector<::android::String16>& input, ::std::vector<::android::String16>* repeated, ::std::vector<::android::String16>* _aidl_return) override {
    return _aidl_delegate->ReverseString(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseByteEnum(const ::std::vector<::android::aidl::tests::ByteEnum>& input, ::std::vector<::android::aidl::tests::ByteEnum>* repeated, ::std::vector<::android::aidl::tests::ByteEnum>* _aidl_return) override {
    return _aidl_delegate->ReverseByteEnum(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseIntEnum(const ::std::vector<::android::aidl::tests::IntEnum>& input, ::std::vector<::android::aidl::tests::IntEnum>* repeated, ::std::vector<::android::aidl::tests::IntEnum>* _aidl_return) override {
    return _aidl_delegate->ReverseIntEnum(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseLongEnum(const ::std::vector<::android::aidl::tests::LongEnum>& input, ::std::vector<::android::aidl::tests::LongEnum>* repeated, ::std::vector<::android::aidl::tests::LongEnum>* _aidl_return) override {
    return _aidl_delegate->ReverseLongEnum(input, repeated, _aidl_return);
  }
  ::android::binder::Status GetOtherTestService(const ::android::String16& name, ::android::sp<::android::aidl::tests::INamedCallback>* _aidl_return) override {
    auto _status = _aidl_delegate->GetOtherTestService(name, _aidl_return);
    if (*_aidl_return) {
      *_aidl_return = ::android::sp<::android::aidl::tests::INamedCallbackDelegator>::cast(delegate(*_aidl_return));
    }
    return _status;
  }
  ::android::binder::Status SetOtherTestService(const ::android::String16& name, const ::android::sp<::android::aidl::tests::INamedCallback>& service, bool* _aidl_return) override {
    ::android::sp<::android::aidl::tests::INamedCallbackDelegator> _service;
    if (service) {
      _service = ::android::sp<::android::aidl::tests::INamedCallbackDelegator>::cast(delegate(service));
    }
    return _aidl_delegate->SetOtherTestService(name, _service, _aidl_return);
  }
  ::android::binder::Status VerifyName(const ::android::sp<::android::aidl::tests::INamedCallback>& service, const ::android::String16& name, bool* _aidl_return) override {
    ::android::sp<::android::aidl::tests::INamedCallbackDelegator> _service;
    if (service) {
      _service = ::android::sp<::android::aidl::tests::INamedCallbackDelegator>::cast(delegate(service));
    }
    return _aidl_delegate->VerifyName(_service, name, _aidl_return);
  }
  ::android::binder::Status GetInterfaceArray(const ::std::vector<::android::String16>& names, ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>* _aidl_return) override {
    return _aidl_delegate->GetInterfaceArray(names, _aidl_return);
  }
  ::android::binder::Status VerifyNamesWithInterfaceArray(const ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>& services, const ::std::vector<::android::String16>& names, bool* _aidl_return) override {
    return _aidl_delegate->VerifyNamesWithInterfaceArray(services, names, _aidl_return);
  }
  ::android::binder::Status GetNullableInterfaceArray(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* _aidl_return) override {
    return _aidl_delegate->GetNullableInterfaceArray(names, _aidl_return);
  }
  ::android::binder::Status VerifyNamesWithNullableInterfaceArray(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& services, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, bool* _aidl_return) override {
    return _aidl_delegate->VerifyNamesWithNullableInterfaceArray(services, names, _aidl_return);
  }
  ::android::binder::Status GetInterfaceList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* _aidl_return) override {
    return _aidl_delegate->GetInterfaceList(names, _aidl_return);
  }
  ::android::binder::Status VerifyNamesWithInterfaceList(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& services, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, bool* _aidl_return) override {
    return _aidl_delegate->VerifyNamesWithInterfaceList(services, names, _aidl_return);
  }
  ::android::binder::Status ReverseStringList(const ::std::vector<::android::String16>& input, ::std::vector<::android::String16>* repeated, ::std::vector<::android::String16>* _aidl_return) override {
    return _aidl_delegate->ReverseStringList(input, repeated, _aidl_return);
  }
  ::android::binder::Status RepeatParcelFileDescriptor(const ::android::os::ParcelFileDescriptor& read, ::android::os::ParcelFileDescriptor* _aidl_return) override {
    return _aidl_delegate->RepeatParcelFileDescriptor(read, _aidl_return);
  }
  ::android::binder::Status ReverseParcelFileDescriptorArray(const ::std::vector<::android::os::ParcelFileDescriptor>& input, ::std::vector<::android::os::ParcelFileDescriptor>* repeated, ::std::vector<::android::os::ParcelFileDescriptor>* _aidl_return) override {
    return _aidl_delegate->ReverseParcelFileDescriptorArray(input, repeated, _aidl_return);
  }
  ::android::binder::Status ThrowServiceException(int32_t code) override {
    return _aidl_delegate->ThrowServiceException(code);
  }
  ::android::binder::Status RepeatNullableIntArray(const ::std::optional<::std::vector<int32_t>>& input, ::std::optional<::std::vector<int32_t>>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableIntArray(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableByteEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableByteEnumArray(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableIntEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableIntEnumArray(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableLongEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableLongEnumArray(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableString(const ::std::optional<::android::String16>& input, ::std::optional<::android::String16>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableString(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableStringList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& input, ::std::optional<::std::vector<::std::optional<::android::String16>>>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableStringList(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableParcelable(const ::std::optional<::android::aidl::tests::ITestService::Empty>& input, ::std::optional<::android::aidl::tests::ITestService::Empty>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableParcelable(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableParcelableArray(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& input, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableParcelableArray(input, _aidl_return);
  }
  ::android::binder::Status RepeatNullableParcelableList(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& input, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableParcelableList(input, _aidl_return);
  }
  ::android::binder::Status TakesAnIBinder(const ::android::sp<::android::IBinder>& input) override {
    return _aidl_delegate->TakesAnIBinder(input);
  }
  ::android::binder::Status TakesANullableIBinder(const ::android::sp<::android::IBinder>& input) override {
    return _aidl_delegate->TakesANullableIBinder(input);
  }
  ::android::binder::Status TakesAnIBinderList(const ::std::vector<::android::sp<::android::IBinder>>& input) override {
    return _aidl_delegate->TakesAnIBinderList(input);
  }
  ::android::binder::Status TakesANullableIBinderList(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& input) override {
    return _aidl_delegate->TakesANullableIBinderList(input);
  }
  ::android::binder::Status RepeatUtf8CppString(const ::std::string& token, ::std::string* _aidl_return) override {
    return _aidl_delegate->RepeatUtf8CppString(token, _aidl_return);
  }
  ::android::binder::Status RepeatNullableUtf8CppString(const ::std::optional<::std::string>& token, ::std::optional<::std::string>* _aidl_return) override {
    return _aidl_delegate->RepeatNullableUtf8CppString(token, _aidl_return);
  }
  ::android::binder::Status ReverseUtf8CppString(const ::std::vector<::std::string>& input, ::std::vector<::std::string>* repeated, ::std::vector<::std::string>* _aidl_return) override {
    return _aidl_delegate->ReverseUtf8CppString(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseNullableUtf8CppString(const ::std::optional<::std::vector<::std::optional<::std::string>>>& input, ::std::optional<::std::vector<::std::optional<::std::string>>>* repeated, ::std::optional<::std::vector<::std::optional<::std::string>>>* _aidl_return) override {
    return _aidl_delegate->ReverseNullableUtf8CppString(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseUtf8CppStringList(const ::std::optional<::std::vector<::std::optional<::std::string>>>& input, ::std::optional<::std::vector<::std::optional<::std::string>>>* repeated, ::std::optional<::std::vector<::std::optional<::std::string>>>* _aidl_return) override {
    return _aidl_delegate->ReverseUtf8CppStringList(input, repeated, _aidl_return);
  }
  ::android::binder::Status GetCallback(bool return_null, ::android::sp<::android::aidl::tests::INamedCallback>* _aidl_return) override {
    auto _status = _aidl_delegate->GetCallback(return_null, _aidl_return);
    if (*_aidl_return) {
      *_aidl_return = ::android::sp<::android::aidl::tests::INamedCallbackDelegator>::cast(delegate(*_aidl_return));
    }
    return _status;
  }
  ::android::binder::Status FillOutStructuredParcelable(::android::aidl::tests::StructuredParcelable* parcel) override {
    return _aidl_delegate->FillOutStructuredParcelable(parcel);
  }
  ::android::binder::Status RepeatExtendableParcelable(const ::android::aidl::tests::extension::ExtendableParcelable& ep, ::android::aidl::tests::extension::ExtendableParcelable* ep2) override {
    return _aidl_delegate->RepeatExtendableParcelable(ep, ep2);
  }
  ::android::binder::Status ReverseList(const ::android::aidl::tests::RecursiveList& list, ::android::aidl::tests::RecursiveList* _aidl_return) override {
    return _aidl_delegate->ReverseList(list, _aidl_return);
  }
  ::android::binder::Status ReverseIBinderArray(const ::std::vector<::android::sp<::android::IBinder>>& input, ::std::vector<::android::sp<::android::IBinder>>* repeated, ::std::vector<::android::sp<::android::IBinder>>* _aidl_return) override {
    return _aidl_delegate->ReverseIBinderArray(input, repeated, _aidl_return);
  }
  ::android::binder::Status ReverseNullableIBinderArray(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& input, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* repeated, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* _aidl_return) override {
    return _aidl_delegate->ReverseNullableIBinderArray(input, repeated, _aidl_return);
  }
  ::android::binder::Status RepeatSimpleParcelable(const ::android::aidl::tests::SimpleParcelable& input, ::android::aidl::tests::SimpleParcelable* repeat, ::android::aidl::tests::SimpleParcelable* _aidl_return) override {
    return _aidl_delegate->RepeatSimpleParcelable(input, repeat, _aidl_return);
  }
  ::android::binder::Status ReverseSimpleParcelables(const ::std::vector<::android::aidl::tests::SimpleParcelable>& input, ::std::vector<::android::aidl::tests::SimpleParcelable>* repeated, ::std::vector<::android::aidl::tests::SimpleParcelable>* _aidl_return) override {
    return _aidl_delegate->ReverseSimpleParcelables(input, repeated, _aidl_return);
  }
  ::android::binder::Status GetOldNameInterface(::android::sp<::android::aidl::tests::IOldName>* _aidl_return) override {
    auto _status = _aidl_delegate->GetOldNameInterface(_aidl_return);
    if (*_aidl_return) {
      *_aidl_return = ::android::sp<::android::aidl::tests::IOldNameDelegator>::cast(delegate(*_aidl_return));
    }
    return _status;
  }
  ::android::binder::Status GetNewNameInterface(::android::sp<::android::aidl::tests::INewName>* _aidl_return) override {
    auto _status = _aidl_delegate->GetNewNameInterface(_aidl_return);
    if (*_aidl_return) {
      *_aidl_return = ::android::sp<::android::aidl::tests::INewNameDelegator>::cast(delegate(*_aidl_return));
    }
    return _status;
  }
  ::android::binder::Status GetUnionTags(const ::std::vector<::android::aidl::tests::Union>& input, ::std::vector<::android::aidl::tests::Union::Tag>* _aidl_return) override {
    return _aidl_delegate->GetUnionTags(input, _aidl_return);
  }
  ::android::binder::Status GetCppJavaTests(::android::sp<::android::IBinder>* _aidl_return) override {
    return _aidl_delegate->GetCppJavaTests(_aidl_return);
  }
  ::android::binder::Status getBackendType(::android::aidl::tests::BackendType* _aidl_return) override {
    return _aidl_delegate->getBackendType(_aidl_return);
  }
  ::android::binder::Status GetCircular(::android::aidl::tests::CircularParcelable* cp, ::android::sp<::android::aidl::tests::ICircular>* _aidl_return) override {
    auto _status = _aidl_delegate->GetCircular(cp, _aidl_return);
    if (*_aidl_return) {
      *_aidl_return = ::android::sp<::android::aidl::tests::ICircularDelegator>::cast(delegate(*_aidl_return));
    }
    return _status;
  }
private:
  ::android::sp<ITestService> _aidl_delegate;
};  // class ITestServiceDelegator
}  // namespace tests
}  // namespace aidl
}  // namespace android
