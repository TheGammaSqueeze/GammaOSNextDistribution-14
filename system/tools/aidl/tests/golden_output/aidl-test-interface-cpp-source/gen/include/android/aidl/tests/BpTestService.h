/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/ITestService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/aidl/tests/ITestService.h>

namespace android {
namespace aidl {
namespace tests {
class BpTestService : public ::android::BpInterface<ITestService> {
public:
  explicit BpTestService(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpTestService() = default;
  ::android::binder::Status UnimplementedMethod(int32_t arg, int32_t* _aidl_return) override;
  ::android::binder::Status Deprecated() override __attribute__((deprecated("to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens")));
  ::android::binder::Status TestOneway() override;
  ::android::binder::Status RepeatBoolean(bool token, bool* _aidl_return) override;
  ::android::binder::Status RepeatByte(int8_t token, int8_t* _aidl_return) override;
  ::android::binder::Status RepeatChar(char16_t token, char16_t* _aidl_return) override;
  ::android::binder::Status RepeatInt(int32_t token, int32_t* _aidl_return) override;
  ::android::binder::Status RepeatLong(int64_t token, int64_t* _aidl_return) override;
  ::android::binder::Status RepeatFloat(float token, float* _aidl_return) override;
  ::android::binder::Status RepeatDouble(double token, double* _aidl_return) override;
  ::android::binder::Status RepeatString(const ::android::String16& token, ::android::String16* _aidl_return) override;
  ::android::binder::Status RepeatByteEnum(::android::aidl::tests::ByteEnum token, ::android::aidl::tests::ByteEnum* _aidl_return) override;
  ::android::binder::Status RepeatIntEnum(::android::aidl::tests::IntEnum token, ::android::aidl::tests::IntEnum* _aidl_return) override;
  ::android::binder::Status RepeatLongEnum(::android::aidl::tests::LongEnum token, ::android::aidl::tests::LongEnum* _aidl_return) override;
  ::android::binder::Status ReverseBoolean(const ::std::vector<bool>& input, ::std::vector<bool>* repeated, ::std::vector<bool>* _aidl_return) override;
  ::android::binder::Status ReverseByte(const ::std::vector<uint8_t>& input, ::std::vector<uint8_t>* repeated, ::std::vector<uint8_t>* _aidl_return) override;
  ::android::binder::Status ReverseChar(const ::std::vector<char16_t>& input, ::std::vector<char16_t>* repeated, ::std::vector<char16_t>* _aidl_return) override;
  ::android::binder::Status ReverseInt(const ::std::vector<int32_t>& input, ::std::vector<int32_t>* repeated, ::std::vector<int32_t>* _aidl_return) override;
  ::android::binder::Status ReverseLong(const ::std::vector<int64_t>& input, ::std::vector<int64_t>* repeated, ::std::vector<int64_t>* _aidl_return) override;
  ::android::binder::Status ReverseFloat(const ::std::vector<float>& input, ::std::vector<float>* repeated, ::std::vector<float>* _aidl_return) override;
  ::android::binder::Status ReverseDouble(const ::std::vector<double>& input, ::std::vector<double>* repeated, ::std::vector<double>* _aidl_return) override;
  ::android::binder::Status ReverseString(const ::std::vector<::android::String16>& input, ::std::vector<::android::String16>* repeated, ::std::vector<::android::String16>* _aidl_return) override;
  ::android::binder::Status ReverseByteEnum(const ::std::vector<::android::aidl::tests::ByteEnum>& input, ::std::vector<::android::aidl::tests::ByteEnum>* repeated, ::std::vector<::android::aidl::tests::ByteEnum>* _aidl_return) override;
  ::android::binder::Status ReverseIntEnum(const ::std::vector<::android::aidl::tests::IntEnum>& input, ::std::vector<::android::aidl::tests::IntEnum>* repeated, ::std::vector<::android::aidl::tests::IntEnum>* _aidl_return) override;
  ::android::binder::Status ReverseLongEnum(const ::std::vector<::android::aidl::tests::LongEnum>& input, ::std::vector<::android::aidl::tests::LongEnum>* repeated, ::std::vector<::android::aidl::tests::LongEnum>* _aidl_return) override;
  ::android::binder::Status GetOtherTestService(const ::android::String16& name, ::android::sp<::android::aidl::tests::INamedCallback>* _aidl_return) override;
  ::android::binder::Status SetOtherTestService(const ::android::String16& name, const ::android::sp<::android::aidl::tests::INamedCallback>& service, bool* _aidl_return) override;
  ::android::binder::Status VerifyName(const ::android::sp<::android::aidl::tests::INamedCallback>& service, const ::android::String16& name, bool* _aidl_return) override;
  ::android::binder::Status GetInterfaceArray(const ::std::vector<::android::String16>& names, ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>* _aidl_return) override;
  ::android::binder::Status VerifyNamesWithInterfaceArray(const ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>& services, const ::std::vector<::android::String16>& names, bool* _aidl_return) override;
  ::android::binder::Status GetNullableInterfaceArray(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* _aidl_return) override;
  ::android::binder::Status VerifyNamesWithNullableInterfaceArray(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& services, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, bool* _aidl_return) override;
  ::android::binder::Status GetInterfaceList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* _aidl_return) override;
  ::android::binder::Status VerifyNamesWithInterfaceList(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& services, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, bool* _aidl_return) override;
  ::android::binder::Status ReverseStringList(const ::std::vector<::android::String16>& input, ::std::vector<::android::String16>* repeated, ::std::vector<::android::String16>* _aidl_return) override;
  ::android::binder::Status RepeatParcelFileDescriptor(const ::android::os::ParcelFileDescriptor& read, ::android::os::ParcelFileDescriptor* _aidl_return) override;
  ::android::binder::Status ReverseParcelFileDescriptorArray(const ::std::vector<::android::os::ParcelFileDescriptor>& input, ::std::vector<::android::os::ParcelFileDescriptor>* repeated, ::std::vector<::android::os::ParcelFileDescriptor>* _aidl_return) override;
  ::android::binder::Status ThrowServiceException(int32_t code) override;
  ::android::binder::Status RepeatNullableIntArray(const ::std::optional<::std::vector<int32_t>>& input, ::std::optional<::std::vector<int32_t>>* _aidl_return) override;
  ::android::binder::Status RepeatNullableByteEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>* _aidl_return) override;
  ::android::binder::Status RepeatNullableIntEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>* _aidl_return) override;
  ::android::binder::Status RepeatNullableLongEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>* _aidl_return) override;
  ::android::binder::Status RepeatNullableString(const ::std::optional<::android::String16>& input, ::std::optional<::android::String16>* _aidl_return) override;
  ::android::binder::Status RepeatNullableStringList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& input, ::std::optional<::std::vector<::std::optional<::android::String16>>>* _aidl_return) override;
  ::android::binder::Status RepeatNullableParcelable(const ::std::optional<::android::aidl::tests::ITestService::Empty>& input, ::std::optional<::android::aidl::tests::ITestService::Empty>* _aidl_return) override;
  ::android::binder::Status RepeatNullableParcelableArray(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& input, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override;
  ::android::binder::Status RepeatNullableParcelableList(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& input, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override;
  ::android::binder::Status TakesAnIBinder(const ::android::sp<::android::IBinder>& input) override;
  ::android::binder::Status TakesANullableIBinder(const ::android::sp<::android::IBinder>& input) override;
  ::android::binder::Status TakesAnIBinderList(const ::std::vector<::android::sp<::android::IBinder>>& input) override;
  ::android::binder::Status TakesANullableIBinderList(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& input) override;
  ::android::binder::Status RepeatUtf8CppString(const ::std::string& token, ::std::string* _aidl_return) override;
  ::android::binder::Status RepeatNullableUtf8CppString(const ::std::optional<::std::string>& token, ::std::optional<::std::string>* _aidl_return) override;
  ::android::binder::Status ReverseUtf8CppString(const ::std::vector<::std::string>& input, ::std::vector<::std::string>* repeated, ::std::vector<::std::string>* _aidl_return) override;
  ::android::binder::Status ReverseNullableUtf8CppString(const ::std::optional<::std::vector<::std::optional<::std::string>>>& input, ::std::optional<::std::vector<::std::optional<::std::string>>>* repeated, ::std::optional<::std::vector<::std::optional<::std::string>>>* _aidl_return) override;
  ::android::binder::Status ReverseUtf8CppStringList(const ::std::optional<::std::vector<::std::optional<::std::string>>>& input, ::std::optional<::std::vector<::std::optional<::std::string>>>* repeated, ::std::optional<::std::vector<::std::optional<::std::string>>>* _aidl_return) override;
  ::android::binder::Status GetCallback(bool return_null, ::android::sp<::android::aidl::tests::INamedCallback>* _aidl_return) override;
  ::android::binder::Status FillOutStructuredParcelable(::android::aidl::tests::StructuredParcelable* parcel) override;
  ::android::binder::Status RepeatExtendableParcelable(const ::android::aidl::tests::extension::ExtendableParcelable& ep, ::android::aidl::tests::extension::ExtendableParcelable* ep2) override;
  ::android::binder::Status ReverseList(const ::android::aidl::tests::RecursiveList& list, ::android::aidl::tests::RecursiveList* _aidl_return) override;
  ::android::binder::Status ReverseIBinderArray(const ::std::vector<::android::sp<::android::IBinder>>& input, ::std::vector<::android::sp<::android::IBinder>>* repeated, ::std::vector<::android::sp<::android::IBinder>>* _aidl_return) override;
  ::android::binder::Status ReverseNullableIBinderArray(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& input, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* repeated, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* _aidl_return) override;
  ::android::binder::Status RepeatSimpleParcelable(const ::android::aidl::tests::SimpleParcelable& input, ::android::aidl::tests::SimpleParcelable* repeat, ::android::aidl::tests::SimpleParcelable* _aidl_return) override;
  ::android::binder::Status ReverseSimpleParcelables(const ::std::vector<::android::aidl::tests::SimpleParcelable>& input, ::std::vector<::android::aidl::tests::SimpleParcelable>* repeated, ::std::vector<::android::aidl::tests::SimpleParcelable>* _aidl_return) override;
  ::android::binder::Status GetOldNameInterface(::android::sp<::android::aidl::tests::IOldName>* _aidl_return) override;
  ::android::binder::Status GetNewNameInterface(::android::sp<::android::aidl::tests::INewName>* _aidl_return) override;
  ::android::binder::Status GetUnionTags(const ::std::vector<::android::aidl::tests::Union>& input, ::std::vector<::android::aidl::tests::Union::Tag>* _aidl_return) override;
  ::android::binder::Status GetCppJavaTests(::android::sp<::android::IBinder>* _aidl_return) override;
  ::android::binder::Status getBackendType(::android::aidl::tests::BackendType* _aidl_return) override;
  ::android::binder::Status GetCircular(::android::aidl::tests::CircularParcelable* cp, ::android::sp<::android::aidl::tests::ICircular>* _aidl_return) override;
};  // class BpTestService
}  // namespace tests
}  // namespace aidl
}  // namespace android
