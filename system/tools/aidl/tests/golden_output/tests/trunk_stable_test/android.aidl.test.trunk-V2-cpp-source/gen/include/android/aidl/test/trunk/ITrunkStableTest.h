/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror --structured --version 2 --hash notfrozen -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/staging/android/aidl/test/trunk/ITrunkStableTest.cpp.d -h out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V2-cpp-source/gen/staging -Nsystem/tools/aidl/tests/trunk_stable_test system/tools/aidl/tests/trunk_stable_test/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#pragma once

#include <android/aidl/test/trunk/ITrunkStableTest.h>
#include <android/binder_to_string.h>
#include <array>
#include <binder/Delegate.h>
#include <binder/Enums.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cassert>
#include <cstdint>
#include <functional>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <variant>

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace android {
namespace aidl {
namespace test {
namespace trunk {
class ITrunkStableTestDelegator;

class ITrunkStableTest : public ::android::IInterface {
public:
  typedef ITrunkStableTestDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(TrunkStableTest)
  static inline const int32_t VERSION = 2;
  static inline const std::string HASH = "notfrozen";
  class MyParcelable : public ::android::Parcelable {
  public:
    int32_t a = 0;
    int32_t b = 0;
    int32_t c = 0;
    inline bool operator==(const MyParcelable& _rhs) const {
      return std::tie(a, b, c) == std::tie(_rhs.a, _rhs.b, _rhs.c);
    }
    inline bool operator<(const MyParcelable& _rhs) const {
      return std::tie(a, b, c) < std::tie(_rhs.a, _rhs.b, _rhs.c);
    }
    inline bool operator!=(const MyParcelable& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const MyParcelable& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const MyParcelable& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const MyParcelable& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.test.trunk.ITrunkStableTest.MyParcelable");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "MyParcelable{";
      _aidl_os << "a: " << ::android::internal::ToString(a);
      _aidl_os << ", b: " << ::android::internal::ToString(b);
      _aidl_os << ", c: " << ::android::internal::ToString(c);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class MyParcelable
  enum class MyEnum : int8_t {
    ZERO = 0,
    ONE = 1,
    TWO = 2,
    THREE = 3,
  };
  class MyUnion : public ::android::Parcelable {
  public:
    enum class Tag : int32_t {
      a = 0,
      b = 1,
      c = 2,
    };
    // Expose tag symbols for legacy code
    static const inline Tag a = Tag::a;
    static const inline Tag b = Tag::b;
    static const inline Tag c = Tag::c;

    template<typename _Tp>
    static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, MyUnion>;

    MyUnion() : _value(std::in_place_index<static_cast<size_t>(a)>, int32_t(0)) { }

    template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
    // NOLINTNEXTLINE(google-explicit-constructor)
    constexpr MyUnion(_Tp&& _arg)
        : _value(std::forward<_Tp>(_arg)) {}

    template <size_t _Np, typename... _Tp>
    constexpr explicit MyUnion(std::in_place_index_t<_Np>, _Tp&&... _args)
        : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

    template <Tag _tag, typename... _Tp>
    static MyUnion make(_Tp&&... _args) {
      return MyUnion(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
    }

    template <Tag _tag, typename _Tp, typename... _Up>
    static MyUnion make(std::initializer_list<_Tp> _il, _Up&&... _args) {
      return MyUnion(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
    }

    Tag getTag() const {
      return static_cast<Tag>(_value.index());
    }

    template <Tag _tag>
    const auto& get() const {
      if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
      return std::get<static_cast<size_t>(_tag)>(_value);
    }

    template <Tag _tag>
    auto& get() {
      if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
      return std::get<static_cast<size_t>(_tag)>(_value);
    }

    template <Tag _tag, typename... _Tp>
    void set(_Tp&&... _args) {
      _value.emplace<static_cast<size_t>(_tag)>(std::forward<_Tp>(_args)...);
    }

    inline bool operator==(const MyUnion& _rhs) const {
      return _value == _rhs._value;
    }
    inline bool operator<(const MyUnion& _rhs) const {
      return _value < _rhs._value;
    }
    inline bool operator!=(const MyUnion& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const MyUnion& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const MyUnion& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const MyUnion& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.test.trunk.ITrunkStableTest.MyUnion");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream os;
      os << "MyUnion{";
      switch (getTag()) {
      case a: os << "a: " << ::android::internal::ToString(get<a>()); break;
      case b: os << "b: " << ::android::internal::ToString(get<b>()); break;
      case c: os << "c: " << ::android::internal::ToString(get<c>()); break;
      }
      os << "}";
      return os.str();
    }
  private:
    std::variant<int32_t, int32_t, int32_t> _value;
  };  // class MyUnion
  class MyOtherParcelable : public ::android::Parcelable {
  public:
    int32_t a = 0;
    int32_t b = 0;
    inline bool operator==(const MyOtherParcelable& _rhs) const {
      return std::tie(a, b) == std::tie(_rhs.a, _rhs.b);
    }
    inline bool operator<(const MyOtherParcelable& _rhs) const {
      return std::tie(a, b) < std::tie(_rhs.a, _rhs.b);
    }
    inline bool operator!=(const MyOtherParcelable& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const MyOtherParcelable& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const MyOtherParcelable& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const MyOtherParcelable& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "MyOtherParcelable{";
      _aidl_os << "a: " << ::android::internal::ToString(a);
      _aidl_os << ", b: " << ::android::internal::ToString(b);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class MyOtherParcelable
  class IMyCallbackDelegator;

  class IMyCallback : public ::android::IInterface {
  public:
    typedef IMyCallbackDelegator DefaultDelegator;
    DECLARE_META_INTERFACE(MyCallback)
    static inline const int32_t VERSION = 2;
    static inline const std::string HASH = "notfrozen";
    virtual ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) = 0;
    virtual ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) = 0;
    virtual ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) = 0;
    virtual ::android::binder::Status repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) = 0;
    virtual int32_t getInterfaceVersion() = 0;
    virtual std::string getInterfaceHash() = 0;
  };  // class IMyCallback

  class IMyCallbackDefault : public IMyCallback {
  public:
    ::android::IBinder* onAsBinder() override {
      return nullptr;
    }
    ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    ::android::binder::Status repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* /*_aidl_return*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
    int32_t getInterfaceVersion() override {
      return 0;
    }
    std::string getInterfaceHash() override {
      return "";
    }
  };  // class IMyCallbackDefault
  class BpMyCallback : public ::android::BpInterface<IMyCallback> {
  public:
    explicit BpMyCallback(const ::android::sp<::android::IBinder>& _aidl_impl);
    virtual ~BpMyCallback() = default;
    ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override;
    ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override;
    ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override;
    ::android::binder::Status repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) override;
    int32_t getInterfaceVersion() override;
    std::string getInterfaceHash() override;
    struct TransactionLog {
      double duration_ms;
      std::string interface_name;
      std::string method_name;
      const void* proxy_address;
      const void* stub_address;
      std::vector<std::pair<std::string, std::string>> input_args;
      std::vector<std::pair<std::string, std::string>> output_args;
      std::string result;
      std::string exception_message;
      int32_t exception_code;
      int32_t transaction_error;
      int32_t service_specific_error_code;
    };
    static std::function<void(const TransactionLog&)> logFunc;
  private:
    int32_t cached_version_ = -1;
    std::string cached_hash_ = "-1";
    std::mutex cached_hash_mutex_;
  };  // class BpMyCallback
  class BnMyCallback : public ::android::BnInterface<IMyCallback> {
  public:
    static constexpr uint32_t TRANSACTION_repeatParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
    static constexpr uint32_t TRANSACTION_repeatEnum = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
    static constexpr uint32_t TRANSACTION_repeatUnion = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
    static constexpr uint32_t TRANSACTION_repeatOtherParcelable = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
    static constexpr uint32_t TRANSACTION_getInterfaceVersion = ::android::IBinder::FIRST_CALL_TRANSACTION + 16777214;
    static constexpr uint32_t TRANSACTION_getInterfaceHash = ::android::IBinder::FIRST_CALL_TRANSACTION + 16777213;
    explicit BnMyCallback();
    ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
    int32_t getInterfaceVersion();
    std::string getInterfaceHash();
    struct TransactionLog {
      double duration_ms;
      std::string interface_name;
      std::string method_name;
      const void* proxy_address;
      const void* stub_address;
      std::vector<std::pair<std::string, std::string>> input_args;
      std::vector<std::pair<std::string, std::string>> output_args;
      std::string result;
      std::string exception_message;
      int32_t exception_code;
      int32_t transaction_error;
      int32_t service_specific_error_code;
    };
    static std::function<void(const TransactionLog&)> logFunc;
  };  // class BnMyCallback

  class IMyCallbackDelegator : public BnMyCallback {
  public:
    explicit IMyCallbackDelegator(const ::android::sp<IMyCallback> &impl) : _aidl_delegate(impl) {}

    ::android::sp<IMyCallback> getImpl() { return _aidl_delegate; }
    ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override {
      return _aidl_delegate->repeatParcelable(input, _aidl_return);
    }
    ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override {
      return _aidl_delegate->repeatEnum(input, _aidl_return);
    }
    ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override {
      return _aidl_delegate->repeatUnion(input, _aidl_return);
    }
    ::android::binder::Status repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) override {
      return _aidl_delegate->repeatOtherParcelable(input, _aidl_return);
    }
    int32_t getInterfaceVersion() override {
      int32_t _delegator_ver = BnMyCallback::getInterfaceVersion();
      int32_t _impl_ver = _aidl_delegate->getInterfaceVersion();
      return _delegator_ver < _impl_ver ? _delegator_ver : _impl_ver;
    }
    std::string getInterfaceHash() override {
      return _aidl_delegate->getInterfaceHash();
    }
  private:
    ::android::sp<IMyCallback> _aidl_delegate;
  };  // class IMyCallbackDelegator
  virtual ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) = 0;
  virtual ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum input, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) = 0;
  virtual ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& input, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) = 0;
  virtual ::android::binder::Status callMyCallback(const ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& cb) = 0;
  virtual ::android::binder::Status repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& input, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* _aidl_return) = 0;
  virtual int32_t getInterfaceVersion() = 0;
  virtual std::string getInterfaceHash() = 0;
};  // class ITrunkStableTest

class ITrunkStableTestDefault : public ITrunkStableTest {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status repeatParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status repeatEnum(::android::aidl::test::trunk::ITrunkStableTest::MyEnum /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyEnum* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status repeatUnion(const ::android::aidl::test::trunk::ITrunkStableTest::MyUnion& /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyUnion* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status callMyCallback(const ::android::sp<::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& /*cb*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status repeatOtherParcelable(const ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable& /*input*/, ::android::aidl::test::trunk::ITrunkStableTest::MyOtherParcelable* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  int32_t getInterfaceVersion() override {
    return 0;
  }
  std::string getInterfaceHash() override {
    return "";
  }
};  // class ITrunkStableTestDefault
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace test {
namespace trunk {
[[nodiscard]] static inline std::string toString(ITrunkStableTest::MyEnum val) {
  switch(val) {
  case ITrunkStableTest::MyEnum::ZERO:
    return "ZERO";
  case ITrunkStableTest::MyEnum::ONE:
    return "ONE";
  case ITrunkStableTest::MyEnum::TWO:
    return "TWO";
  case ITrunkStableTest::MyEnum::THREE:
    return "THREE";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::test::trunk::ITrunkStableTest::MyEnum, 4> enum_values<::android::aidl::test::trunk::ITrunkStableTest::MyEnum> = {
  ::android::aidl::test::trunk::ITrunkStableTest::MyEnum::ZERO,
  ::android::aidl::test::trunk::ITrunkStableTest::MyEnum::ONE,
  ::android::aidl::test::trunk::ITrunkStableTest::MyEnum::TWO,
  ::android::aidl::test::trunk::ITrunkStableTest::MyEnum::THREE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
namespace android {
namespace aidl {
namespace test {
namespace trunk {
[[nodiscard]] static inline std::string toString(ITrunkStableTest::MyUnion::Tag val) {
  switch(val) {
  case ITrunkStableTest::MyUnion::Tag::a:
    return "a";
  case ITrunkStableTest::MyUnion::Tag::b:
    return "b";
  case ITrunkStableTest::MyUnion::Tag::c:
    return "c";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag, 3> enum_values<::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag> = {
  ::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag::a,
  ::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag::b,
  ::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag::c,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
