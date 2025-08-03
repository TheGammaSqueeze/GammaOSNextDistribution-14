/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-fixedsizearray-cpp-source/gen/staging/android/aidl/fixedsizearray/FixedSizeArrayExample.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-fixedsizearray-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-fixedsizearray-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/fixedsizearray/FixedSizeArrayExample.aidl
 */
#pragma once

#include <android/aidl/fixedsizearray/FixedSizeArrayExample.h>
#include <android/binder_to_string.h>
#include <array>
#include <binder/Delegate.h>
#include <binder/Enums.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Parcel.h>
#include <binder/ParcelFileDescriptor.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cstdint>
#include <optional>
#include <string>
#include <tuple>
#include <utils/String16.h>
#include <utils/StrongPointer.h>

namespace android {
namespace aidl {
namespace fixedsizearray {
class FixedSizeArrayExample : public ::android::Parcelable {
public:
  class IntParcelable : public ::android::Parcelable {
  public:
    int32_t value = 0;
    inline bool operator==(const IntParcelable& _rhs) const {
      return std::tie(value) == std::tie(_rhs.value);
    }
    inline bool operator<(const IntParcelable& _rhs) const {
      return std::tie(value) < std::tie(_rhs.value);
    }
    inline bool operator!=(const IntParcelable& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const IntParcelable& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const IntParcelable& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const IntParcelable& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "IntParcelable{";
      _aidl_os << "value: " << ::android::internal::ToString(value);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class IntParcelable
  class IRepeatFixedSizeArrayDelegator;

  class IRepeatFixedSizeArray : public ::android::IInterface {
  public:
    typedef IRepeatFixedSizeArrayDelegator DefaultDelegator;
    DECLARE_META_INTERFACE(RepeatFixedSizeArray)
    virtual ::android::binder::Status RepeatBytes(const std::array<uint8_t, 3>& input, std::array<uint8_t, 3>* repeated, std::array<uint8_t, 3>* _aidl_return) = 0;
    virtual ::android::binder::Status RepeatInts(const std::array<int32_t, 3>& input, std::array<int32_t, 3>* repeated, std::array<int32_t, 3>* _aidl_return) = 0;
    virtual ::android::binder::Status RepeatBinders(const std::array<::android::sp<::android::IBinder>, 3>& input, std::array<::android::sp<::android::IBinder>, 3>* repeated, std::array<::android::sp<::android::IBinder>, 3>* _aidl_return) = 0;
    virtual ::android::binder::Status RepeatParcelables(const std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>& input, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* repeated, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* _aidl_return) = 0;
    virtual ::android::binder::Status Repeat2dBytes(const std::array<std::array<uint8_t, 3>, 2>& input, std::array<std::array<uint8_t, 3>, 2>* repeated, std::array<std::array<uint8_t, 3>, 2>* _aidl_return) = 0;
    virtual ::android::binder::Status Repeat2dInts(const std::array<std::array<int32_t, 3>, 2>& input, std::array<std::array<int32_t, 3>, 2>* repeated, std::array<std::array<int32_t, 3>, 2>* _aidl_return) = 0;
    virtual ::android::binder::Status Repeat2dBinders(const std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>& input, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* repeated, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* _aidl_return) = 0;
    virtual ::android::binder::Status Repeat2dParcelables(const std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>& input, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* repeated, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* _aidl_return) = 0;
  };  // class IRepeatFixedSizeArray

  class IRepeatFixedSizeArrayDefault : public IRepeatFixedSizeArray {
  public:
    ::android::IBinder* onAsBinder() override {
      return nullptr;
    }
    ::android::binder::Status RepeatBytes(const std::array<uint8_t, 3>& /*input*/, std::array<uint8_t, 3>* /*repeated*/, std::array<uint8_t, 3>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status RepeatInts(const std::array<int32_t, 3>& /*input*/, std::array<int32_t, 3>* /*repeated*/, std::array<int32_t, 3>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status RepeatBinders(const std::array<::android::sp<::android::IBinder>, 3>& /*input*/, std::array<::android::sp<::android::IBinder>, 3>* /*repeated*/, std::array<::android::sp<::android::IBinder>, 3>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status RepeatParcelables(const std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>& /*input*/, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* /*repeated*/, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status Repeat2dBytes(const std::array<std::array<uint8_t, 3>, 2>& /*input*/, std::array<std::array<uint8_t, 3>, 2>* /*repeated*/, std::array<std::array<uint8_t, 3>, 2>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status Repeat2dInts(const std::array<std::array<int32_t, 3>, 2>& /*input*/, std::array<std::array<int32_t, 3>, 2>* /*repeated*/, std::array<std::array<int32_t, 3>, 2>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status Repeat2dBinders(const std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>& /*input*/, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* /*repeated*/, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status Repeat2dParcelables(const std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>& /*input*/, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* /*repeated*/, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
  };  // class IRepeatFixedSizeArrayDefault
  class BpRepeatFixedSizeArray : public ::android::BpInterface<IRepeatFixedSizeArray> {
  public:
    explicit BpRepeatFixedSizeArray(const ::android::sp<::android::IBinder>& _aidl_impl);
    virtual ~BpRepeatFixedSizeArray() = default;
    ::android::binder::Status RepeatBytes(const std::array<uint8_t, 3>& input, std::array<uint8_t, 3>* repeated, std::array<uint8_t, 3>* _aidl_return) override;
    ::android::binder::Status RepeatInts(const std::array<int32_t, 3>& input, std::array<int32_t, 3>* repeated, std::array<int32_t, 3>* _aidl_return) override;
    ::android::binder::Status RepeatBinders(const std::array<::android::sp<::android::IBinder>, 3>& input, std::array<::android::sp<::android::IBinder>, 3>* repeated, std::array<::android::sp<::android::IBinder>, 3>* _aidl_return) override;
    ::android::binder::Status RepeatParcelables(const std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>& input, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* repeated, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* _aidl_return) override;
    ::android::binder::Status Repeat2dBytes(const std::array<std::array<uint8_t, 3>, 2>& input, std::array<std::array<uint8_t, 3>, 2>* repeated, std::array<std::array<uint8_t, 3>, 2>* _aidl_return) override;
    ::android::binder::Status Repeat2dInts(const std::array<std::array<int32_t, 3>, 2>& input, std::array<std::array<int32_t, 3>, 2>* repeated, std::array<std::array<int32_t, 3>, 2>* _aidl_return) override;
    ::android::binder::Status Repeat2dBinders(const std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>& input, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* repeated, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* _aidl_return) override;
    ::android::binder::Status Repeat2dParcelables(const std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>& input, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* repeated, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* _aidl_return) override;
  };  // class BpRepeatFixedSizeArray
  class BnRepeatFixedSizeArray : public ::android::BnInterface<IRepeatFixedSizeArray> {
  public:
    static constexpr uint32_t TRANSACTION_RepeatBytes = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
    static constexpr uint32_t TRANSACTION_RepeatInts = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
    static constexpr uint32_t TRANSACTION_RepeatBinders = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
    static constexpr uint32_t TRANSACTION_RepeatParcelables = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
    static constexpr uint32_t TRANSACTION_Repeat2dBytes = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
    static constexpr uint32_t TRANSACTION_Repeat2dInts = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
    static constexpr uint32_t TRANSACTION_Repeat2dBinders = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
    static constexpr uint32_t TRANSACTION_Repeat2dParcelables = ::android::IBinder::FIRST_CALL_TRANSACTION + 7;
    explicit BnRepeatFixedSizeArray();
    ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
  };  // class BnRepeatFixedSizeArray

  class IRepeatFixedSizeArrayDelegator : public BnRepeatFixedSizeArray {
  public:
    explicit IRepeatFixedSizeArrayDelegator(const ::android::sp<IRepeatFixedSizeArray> &impl) : _aidl_delegate(impl) {}

    ::android::sp<IRepeatFixedSizeArray> getImpl() { return _aidl_delegate; }
    ::android::binder::Status RepeatBytes(const std::array<uint8_t, 3>& input, std::array<uint8_t, 3>* repeated, std::array<uint8_t, 3>* _aidl_return) override {
      return _aidl_delegate->RepeatBytes(input, repeated, _aidl_return);
    }
    ::android::binder::Status RepeatInts(const std::array<int32_t, 3>& input, std::array<int32_t, 3>* repeated, std::array<int32_t, 3>* _aidl_return) override {
      return _aidl_delegate->RepeatInts(input, repeated, _aidl_return);
    }
    ::android::binder::Status RepeatBinders(const std::array<::android::sp<::android::IBinder>, 3>& input, std::array<::android::sp<::android::IBinder>, 3>* repeated, std::array<::android::sp<::android::IBinder>, 3>* _aidl_return) override {
      return _aidl_delegate->RepeatBinders(input, repeated, _aidl_return);
    }
    ::android::binder::Status RepeatParcelables(const std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>& input, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* repeated, std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>* _aidl_return) override {
      return _aidl_delegate->RepeatParcelables(input, repeated, _aidl_return);
    }
    ::android::binder::Status Repeat2dBytes(const std::array<std::array<uint8_t, 3>, 2>& input, std::array<std::array<uint8_t, 3>, 2>* repeated, std::array<std::array<uint8_t, 3>, 2>* _aidl_return) override {
      return _aidl_delegate->Repeat2dBytes(input, repeated, _aidl_return);
    }
    ::android::binder::Status Repeat2dInts(const std::array<std::array<int32_t, 3>, 2>& input, std::array<std::array<int32_t, 3>, 2>* repeated, std::array<std::array<int32_t, 3>, 2>* _aidl_return) override {
      return _aidl_delegate->Repeat2dInts(input, repeated, _aidl_return);
    }
    ::android::binder::Status Repeat2dBinders(const std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>& input, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* repeated, std::array<std::array<::android::sp<::android::IBinder>, 3>, 2>* _aidl_return) override {
      return _aidl_delegate->Repeat2dBinders(input, repeated, _aidl_return);
    }
    ::android::binder::Status Repeat2dParcelables(const std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>& input, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* repeated, std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 3>, 2>* _aidl_return) override {
      return _aidl_delegate->Repeat2dParcelables(input, repeated, _aidl_return);
    }
  private:
    ::android::sp<IRepeatFixedSizeArray> _aidl_delegate;
  };  // class IRepeatFixedSizeArrayDelegator
  enum class ByteEnum : int8_t {
    A = 0,
  };
  enum class IntEnum : int32_t {
    A = 0,
  };
  enum class LongEnum : int64_t {
    A = 0L,
  };
  class IEmptyInterfaceDelegator;

  class IEmptyInterface : public ::android::IInterface {
  public:
    typedef IEmptyInterfaceDelegator DefaultDelegator;
    DECLARE_META_INTERFACE(EmptyInterface)
  };  // class IEmptyInterface

  class IEmptyInterfaceDefault : public IEmptyInterface {
  public:
    ::android::IBinder* onAsBinder() override {
      return nullptr;
    }
  };  // class IEmptyInterfaceDefault
  class BpEmptyInterface : public ::android::BpInterface<IEmptyInterface> {
  public:
    explicit BpEmptyInterface(const ::android::sp<::android::IBinder>& _aidl_impl);
    virtual ~BpEmptyInterface() = default;
  };  // class BpEmptyInterface
  class BnEmptyInterface : public ::android::BnInterface<IEmptyInterface> {
  public:
    explicit BnEmptyInterface();
    ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
  };  // class BnEmptyInterface

  class IEmptyInterfaceDelegator : public BnEmptyInterface {
  public:
    explicit IEmptyInterfaceDelegator(const ::android::sp<IEmptyInterface> &impl) : _aidl_delegate(impl) {}

    ::android::sp<IEmptyInterface> getImpl() { return _aidl_delegate; }
  private:
    ::android::sp<IEmptyInterface> _aidl_delegate;
  };  // class IEmptyInterfaceDelegator
  std::array<std::array<int32_t, 3>, 2> int2x3 = {{{{1, 2, 3}}, {{4, 5, 6}}}};
  std::array<bool, 2> boolArray = {{}};
  std::array<uint8_t, 2> byteArray = {{}};
  std::array<char16_t, 2> charArray = {{}};
  std::array<int32_t, 2> intArray = {{}};
  std::array<int64_t, 2> longArray = {{}};
  std::array<float, 2> floatArray = {{}};
  std::array<double, 2> doubleArray = {{}};
  std::array<::std::string, 2> stringArray = {{"hello", "world"}};
  std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::ByteEnum, 2> byteEnumArray = {{}};
  std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntEnum, 2> intEnumArray = {{}};
  std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::LongEnum, 2> longEnumArray = {{}};
  std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 2> parcelableArray = {{}};
  std::array<std::array<bool, 2>, 2> boolMatrix = {{}};
  std::array<std::array<uint8_t, 2>, 2> byteMatrix = {{}};
  std::array<std::array<char16_t, 2>, 2> charMatrix = {{}};
  std::array<std::array<int32_t, 2>, 2> intMatrix = {{}};
  std::array<std::array<int64_t, 2>, 2> longMatrix = {{}};
  std::array<std::array<float, 2>, 2> floatMatrix = {{}};
  std::array<std::array<double, 2>, 2> doubleMatrix = {{}};
  std::array<std::array<::std::string, 2>, 2> stringMatrix = {{{{"hello", "world"}}, {{"Ciao", "mondo"}}}};
  std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::ByteEnum, 2>, 2> byteEnumMatrix = {{}};
  std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntEnum, 2>, 2> intEnumMatrix = {{}};
  std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::LongEnum, 2>, 2> longEnumMatrix = {{}};
  std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable, 2>, 2> parcelableMatrix = {{}};
  ::std::optional<std::array<bool, 2>> boolNullableArray;
  ::std::optional<std::array<uint8_t, 2>> byteNullableArray;
  ::std::optional<std::array<char16_t, 2>> charNullableArray;
  ::std::optional<std::array<int32_t, 2>> intNullableArray;
  ::std::optional<std::array<int64_t, 2>> longNullableArray;
  ::std::optional<std::array<float, 2>> floatNullableArray;
  ::std::optional<std::array<double, 2>> doubleNullableArray;
  ::std::optional<std::array<::std::optional<::std::string>, 2>> stringNullableArray = {{{"hello", "world"}}};
  ::std::optional<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::ByteEnum, 2>> byteEnumNullableArray;
  ::std::optional<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntEnum, 2>> intEnumNullableArray;
  ::std::optional<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::LongEnum, 2>> longEnumNullableArray;
  ::std::optional<std::array<::android::sp<::android::IBinder>, 2>> binderNullableArray;
  ::std::optional<std::array<::std::optional<::android::os::ParcelFileDescriptor>, 2>> pfdNullableArray;
  ::std::optional<std::array<::std::optional<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable>, 2>> parcelableNullableArray;
  ::std::optional<std::array<::android::sp<::android::aidl::fixedsizearray::FixedSizeArrayExample::IEmptyInterface>, 2>> interfaceNullableArray;
  ::std::optional<std::array<std::array<bool, 2>, 2>> boolNullableMatrix;
  ::std::optional<std::array<std::array<uint8_t, 2>, 2>> byteNullableMatrix;
  ::std::optional<std::array<std::array<char16_t, 2>, 2>> charNullableMatrix;
  ::std::optional<std::array<std::array<int32_t, 2>, 2>> intNullableMatrix;
  ::std::optional<std::array<std::array<int64_t, 2>, 2>> longNullableMatrix;
  ::std::optional<std::array<std::array<float, 2>, 2>> floatNullableMatrix;
  ::std::optional<std::array<std::array<double, 2>, 2>> doubleNullableMatrix;
  ::std::optional<std::array<std::array<::std::optional<::std::string>, 2>, 2>> stringNullableMatrix = {{{{{"hello", "world"}}, {{"Ciao", "mondo"}}}}};
  ::std::optional<std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::ByteEnum, 2>, 2>> byteEnumNullableMatrix;
  ::std::optional<std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntEnum, 2>, 2>> intEnumNullableMatrix;
  ::std::optional<std::array<std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::LongEnum, 2>, 2>> longEnumNullableMatrix;
  ::std::optional<std::array<std::array<::android::sp<::android::IBinder>, 2>, 2>> binderNullableMatrix;
  ::std::optional<std::array<std::array<::std::optional<::android::os::ParcelFileDescriptor>, 2>, 2>> pfdNullableMatrix;
  ::std::optional<std::array<std::array<::std::optional<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntParcelable>, 2>, 2>> parcelableNullableMatrix;
  ::std::optional<std::array<std::array<::android::sp<::android::aidl::fixedsizearray::FixedSizeArrayExample::IEmptyInterface>, 2>, 2>> interfaceNullableMatrix;
  inline bool operator==(const FixedSizeArrayExample& _rhs) const {
    return std::tie(int2x3, boolArray, byteArray, charArray, intArray, longArray, floatArray, doubleArray, stringArray, byteEnumArray, intEnumArray, longEnumArray, parcelableArray, boolMatrix, byteMatrix, charMatrix, intMatrix, longMatrix, floatMatrix, doubleMatrix, stringMatrix, byteEnumMatrix, intEnumMatrix, longEnumMatrix, parcelableMatrix, boolNullableArray, byteNullableArray, charNullableArray, intNullableArray, longNullableArray, floatNullableArray, doubleNullableArray, stringNullableArray, byteEnumNullableArray, intEnumNullableArray, longEnumNullableArray, binderNullableArray, pfdNullableArray, parcelableNullableArray, interfaceNullableArray, boolNullableMatrix, byteNullableMatrix, charNullableMatrix, intNullableMatrix, longNullableMatrix, floatNullableMatrix, doubleNullableMatrix, stringNullableMatrix, byteEnumNullableMatrix, intEnumNullableMatrix, longEnumNullableMatrix, binderNullableMatrix, pfdNullableMatrix, parcelableNullableMatrix, interfaceNullableMatrix) == std::tie(_rhs.int2x3, _rhs.boolArray, _rhs.byteArray, _rhs.charArray, _rhs.intArray, _rhs.longArray, _rhs.floatArray, _rhs.doubleArray, _rhs.stringArray, _rhs.byteEnumArray, _rhs.intEnumArray, _rhs.longEnumArray, _rhs.parcelableArray, _rhs.boolMatrix, _rhs.byteMatrix, _rhs.charMatrix, _rhs.intMatrix, _rhs.longMatrix, _rhs.floatMatrix, _rhs.doubleMatrix, _rhs.stringMatrix, _rhs.byteEnumMatrix, _rhs.intEnumMatrix, _rhs.longEnumMatrix, _rhs.parcelableMatrix, _rhs.boolNullableArray, _rhs.byteNullableArray, _rhs.charNullableArray, _rhs.intNullableArray, _rhs.longNullableArray, _rhs.floatNullableArray, _rhs.doubleNullableArray, _rhs.stringNullableArray, _rhs.byteEnumNullableArray, _rhs.intEnumNullableArray, _rhs.longEnumNullableArray, _rhs.binderNullableArray, _rhs.pfdNullableArray, _rhs.parcelableNullableArray, _rhs.interfaceNullableArray, _rhs.boolNullableMatrix, _rhs.byteNullableMatrix, _rhs.charNullableMatrix, _rhs.intNullableMatrix, _rhs.longNullableMatrix, _rhs.floatNullableMatrix, _rhs.doubleNullableMatrix, _rhs.stringNullableMatrix, _rhs.byteEnumNullableMatrix, _rhs.intEnumNullableMatrix, _rhs.longEnumNullableMatrix, _rhs.binderNullableMatrix, _rhs.pfdNullableMatrix, _rhs.parcelableNullableMatrix, _rhs.interfaceNullableMatrix);
  }
  inline bool operator<(const FixedSizeArrayExample& _rhs) const {
    return std::tie(int2x3, boolArray, byteArray, charArray, intArray, longArray, floatArray, doubleArray, stringArray, byteEnumArray, intEnumArray, longEnumArray, parcelableArray, boolMatrix, byteMatrix, charMatrix, intMatrix, longMatrix, floatMatrix, doubleMatrix, stringMatrix, byteEnumMatrix, intEnumMatrix, longEnumMatrix, parcelableMatrix, boolNullableArray, byteNullableArray, charNullableArray, intNullableArray, longNullableArray, floatNullableArray, doubleNullableArray, stringNullableArray, byteEnumNullableArray, intEnumNullableArray, longEnumNullableArray, binderNullableArray, pfdNullableArray, parcelableNullableArray, interfaceNullableArray, boolNullableMatrix, byteNullableMatrix, charNullableMatrix, intNullableMatrix, longNullableMatrix, floatNullableMatrix, doubleNullableMatrix, stringNullableMatrix, byteEnumNullableMatrix, intEnumNullableMatrix, longEnumNullableMatrix, binderNullableMatrix, pfdNullableMatrix, parcelableNullableMatrix, interfaceNullableMatrix) < std::tie(_rhs.int2x3, _rhs.boolArray, _rhs.byteArray, _rhs.charArray, _rhs.intArray, _rhs.longArray, _rhs.floatArray, _rhs.doubleArray, _rhs.stringArray, _rhs.byteEnumArray, _rhs.intEnumArray, _rhs.longEnumArray, _rhs.parcelableArray, _rhs.boolMatrix, _rhs.byteMatrix, _rhs.charMatrix, _rhs.intMatrix, _rhs.longMatrix, _rhs.floatMatrix, _rhs.doubleMatrix, _rhs.stringMatrix, _rhs.byteEnumMatrix, _rhs.intEnumMatrix, _rhs.longEnumMatrix, _rhs.parcelableMatrix, _rhs.boolNullableArray, _rhs.byteNullableArray, _rhs.charNullableArray, _rhs.intNullableArray, _rhs.longNullableArray, _rhs.floatNullableArray, _rhs.doubleNullableArray, _rhs.stringNullableArray, _rhs.byteEnumNullableArray, _rhs.intEnumNullableArray, _rhs.longEnumNullableArray, _rhs.binderNullableArray, _rhs.pfdNullableArray, _rhs.parcelableNullableArray, _rhs.interfaceNullableArray, _rhs.boolNullableMatrix, _rhs.byteNullableMatrix, _rhs.charNullableMatrix, _rhs.intNullableMatrix, _rhs.longNullableMatrix, _rhs.floatNullableMatrix, _rhs.doubleNullableMatrix, _rhs.stringNullableMatrix, _rhs.byteEnumNullableMatrix, _rhs.intEnumNullableMatrix, _rhs.longEnumNullableMatrix, _rhs.binderNullableMatrix, _rhs.pfdNullableMatrix, _rhs.parcelableNullableMatrix, _rhs.interfaceNullableMatrix);
  }
  inline bool operator!=(const FixedSizeArrayExample& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const FixedSizeArrayExample& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const FixedSizeArrayExample& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const FixedSizeArrayExample& _rhs) const {
    return !(_rhs < *this);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.fixedsizearray.FixedSizeArrayExample");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "FixedSizeArrayExample{";
    _aidl_os << "int2x3: " << ::android::internal::ToString(int2x3);
    _aidl_os << ", boolArray: " << ::android::internal::ToString(boolArray);
    _aidl_os << ", byteArray: " << ::android::internal::ToString(byteArray);
    _aidl_os << ", charArray: " << ::android::internal::ToString(charArray);
    _aidl_os << ", intArray: " << ::android::internal::ToString(intArray);
    _aidl_os << ", longArray: " << ::android::internal::ToString(longArray);
    _aidl_os << ", floatArray: " << ::android::internal::ToString(floatArray);
    _aidl_os << ", doubleArray: " << ::android::internal::ToString(doubleArray);
    _aidl_os << ", stringArray: " << ::android::internal::ToString(stringArray);
    _aidl_os << ", byteEnumArray: " << ::android::internal::ToString(byteEnumArray);
    _aidl_os << ", intEnumArray: " << ::android::internal::ToString(intEnumArray);
    _aidl_os << ", longEnumArray: " << ::android::internal::ToString(longEnumArray);
    _aidl_os << ", parcelableArray: " << ::android::internal::ToString(parcelableArray);
    _aidl_os << ", boolMatrix: " << ::android::internal::ToString(boolMatrix);
    _aidl_os << ", byteMatrix: " << ::android::internal::ToString(byteMatrix);
    _aidl_os << ", charMatrix: " << ::android::internal::ToString(charMatrix);
    _aidl_os << ", intMatrix: " << ::android::internal::ToString(intMatrix);
    _aidl_os << ", longMatrix: " << ::android::internal::ToString(longMatrix);
    _aidl_os << ", floatMatrix: " << ::android::internal::ToString(floatMatrix);
    _aidl_os << ", doubleMatrix: " << ::android::internal::ToString(doubleMatrix);
    _aidl_os << ", stringMatrix: " << ::android::internal::ToString(stringMatrix);
    _aidl_os << ", byteEnumMatrix: " << ::android::internal::ToString(byteEnumMatrix);
    _aidl_os << ", intEnumMatrix: " << ::android::internal::ToString(intEnumMatrix);
    _aidl_os << ", longEnumMatrix: " << ::android::internal::ToString(longEnumMatrix);
    _aidl_os << ", parcelableMatrix: " << ::android::internal::ToString(parcelableMatrix);
    _aidl_os << ", boolNullableArray: " << ::android::internal::ToString(boolNullableArray);
    _aidl_os << ", byteNullableArray: " << ::android::internal::ToString(byteNullableArray);
    _aidl_os << ", charNullableArray: " << ::android::internal::ToString(charNullableArray);
    _aidl_os << ", intNullableArray: " << ::android::internal::ToString(intNullableArray);
    _aidl_os << ", longNullableArray: " << ::android::internal::ToString(longNullableArray);
    _aidl_os << ", floatNullableArray: " << ::android::internal::ToString(floatNullableArray);
    _aidl_os << ", doubleNullableArray: " << ::android::internal::ToString(doubleNullableArray);
    _aidl_os << ", stringNullableArray: " << ::android::internal::ToString(stringNullableArray);
    _aidl_os << ", byteEnumNullableArray: " << ::android::internal::ToString(byteEnumNullableArray);
    _aidl_os << ", intEnumNullableArray: " << ::android::internal::ToString(intEnumNullableArray);
    _aidl_os << ", longEnumNullableArray: " << ::android::internal::ToString(longEnumNullableArray);
    _aidl_os << ", binderNullableArray: " << ::android::internal::ToString(binderNullableArray);
    _aidl_os << ", pfdNullableArray: " << ::android::internal::ToString(pfdNullableArray);
    _aidl_os << ", parcelableNullableArray: " << ::android::internal::ToString(parcelableNullableArray);
    _aidl_os << ", interfaceNullableArray: " << ::android::internal::ToString(interfaceNullableArray);
    _aidl_os << ", boolNullableMatrix: " << ::android::internal::ToString(boolNullableMatrix);
    _aidl_os << ", byteNullableMatrix: " << ::android::internal::ToString(byteNullableMatrix);
    _aidl_os << ", charNullableMatrix: " << ::android::internal::ToString(charNullableMatrix);
    _aidl_os << ", intNullableMatrix: " << ::android::internal::ToString(intNullableMatrix);
    _aidl_os << ", longNullableMatrix: " << ::android::internal::ToString(longNullableMatrix);
    _aidl_os << ", floatNullableMatrix: " << ::android::internal::ToString(floatNullableMatrix);
    _aidl_os << ", doubleNullableMatrix: " << ::android::internal::ToString(doubleNullableMatrix);
    _aidl_os << ", stringNullableMatrix: " << ::android::internal::ToString(stringNullableMatrix);
    _aidl_os << ", byteEnumNullableMatrix: " << ::android::internal::ToString(byteEnumNullableMatrix);
    _aidl_os << ", intEnumNullableMatrix: " << ::android::internal::ToString(intEnumNullableMatrix);
    _aidl_os << ", longEnumNullableMatrix: " << ::android::internal::ToString(longEnumNullableMatrix);
    _aidl_os << ", binderNullableMatrix: " << ::android::internal::ToString(binderNullableMatrix);
    _aidl_os << ", pfdNullableMatrix: " << ::android::internal::ToString(pfdNullableMatrix);
    _aidl_os << ", parcelableNullableMatrix: " << ::android::internal::ToString(parcelableNullableMatrix);
    _aidl_os << ", interfaceNullableMatrix: " << ::android::internal::ToString(interfaceNullableMatrix);
    _aidl_os << "}";
    return _aidl_os.str();
  }
};  // class FixedSizeArrayExample
}  // namespace fixedsizearray
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace fixedsizearray {
[[nodiscard]] static inline std::string toString(FixedSizeArrayExample::ByteEnum val) {
  switch(val) {
  case FixedSizeArrayExample::ByteEnum::A:
    return "A";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace fixedsizearray
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::ByteEnum, 1> enum_values<::android::aidl::fixedsizearray::FixedSizeArrayExample::ByteEnum> = {
  ::android::aidl::fixedsizearray::FixedSizeArrayExample::ByteEnum::A,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
namespace android {
namespace aidl {
namespace fixedsizearray {
[[nodiscard]] static inline std::string toString(FixedSizeArrayExample::IntEnum val) {
  switch(val) {
  case FixedSizeArrayExample::IntEnum::A:
    return "A";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace fixedsizearray
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntEnum, 1> enum_values<::android::aidl::fixedsizearray::FixedSizeArrayExample::IntEnum> = {
  ::android::aidl::fixedsizearray::FixedSizeArrayExample::IntEnum::A,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
namespace android {
namespace aidl {
namespace fixedsizearray {
[[nodiscard]] static inline std::string toString(FixedSizeArrayExample::LongEnum val) {
  switch(val) {
  case FixedSizeArrayExample::LongEnum::A:
    return "A";
  default:
    return std::to_string(static_cast<int64_t>(val));
  }
}
}  // namespace fixedsizearray
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::fixedsizearray::FixedSizeArrayExample::LongEnum, 1> enum_values<::android::aidl::fixedsizearray::FixedSizeArrayExample::LongEnum> = {
  ::android::aidl::fixedsizearray::FixedSizeArrayExample::LongEnum::A,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
