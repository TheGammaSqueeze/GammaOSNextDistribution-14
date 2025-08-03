/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk --structured --version 1 --hash 88311b9118fb6fe9eff4a2ca19121de0587f6d5f -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-ndk-source/gen/staging/android/aidl/test/trunk/ITrunkStableTest.cpp.d -h out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/tests/trunk_stable_test/android.aidl.test.trunk-V1-ndk-source/gen/staging -Nsystem/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1 system/tools/aidl/tests/trunk_stable_test/aidl_api/android.aidl.test.trunk/1/android/aidl/test/trunk/ITrunkStableTest.aidl
 */
#pragma once

#include <array>
#include <cassert>
#include <chrono>
#include <cstdint>
#include <functional>
#include <memory>
#include <optional>
#include <sstream>
#include <string>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_ibinder.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <android/trace.h>
#include <aidl/android/aidl/test/trunk/ITrunkStableTest.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace aidl {
namespace test {
namespace trunk {
class ITrunkStableTestDelegator;

class ITrunkStableTest : public ::ndk::ICInterface {
public:
  typedef ITrunkStableTestDelegator DefaultDelegator;
  static const char* descriptor;
  ITrunkStableTest();
  virtual ~ITrunkStableTest();

  class MyParcelable {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int32_t a = 0;
    int32_t b = 0;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator==(const MyParcelable& _rhs) const {
      return std::tie(a, b) == std::tie(_rhs.a, _rhs.b);
    }
    inline bool operator<(const MyParcelable& _rhs) const {
      return std::tie(a, b) < std::tie(_rhs.a, _rhs.b);
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

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "MyParcelable{";
      _aidl_os << "a: " << ::android::internal::ToString(a);
      _aidl_os << ", b: " << ::android::internal::ToString(b);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };
  enum class MyEnum : int8_t {
    ZERO = 0,
    ONE = 1,
    TWO = 2,
  };

  class MyUnion {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    enum class Tag : int32_t {
      a = 0,
      b = 1,
    };

    // Expose tag symbols for legacy code
    static const inline Tag a = Tag::a;
    static const inline Tag b = Tag::b;

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

    binder_status_t readFromParcel(const AParcel* _parcel);
    binder_status_t writeToParcel(AParcel* _parcel) const;

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

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream os;
      os << "MyUnion{";
      switch (getTag()) {
      case a: os << "a: " << ::android::internal::ToString(get<a>()); break;
      case b: os << "b: " << ::android::internal::ToString(get<b>()); break;
      }
      os << "}";
      return os.str();
    }
  private:
    std::variant<int32_t, int32_t> _value;
  };
  class IMyCallbackDelegator;

  class IMyCallback : public ::ndk::ICInterface {
  public:
    typedef IMyCallbackDelegator DefaultDelegator;
    static const char* descriptor;
    IMyCallback();
    virtual ~IMyCallback();

    static inline const int32_t version = 1;
    static inline const std::string hash = "88311b9118fb6fe9eff4a2ca19121de0587f6d5f";
    static constexpr uint32_t TRANSACTION_repeatParcelable = FIRST_CALL_TRANSACTION + 0;
    static constexpr uint32_t TRANSACTION_repeatEnum = FIRST_CALL_TRANSACTION + 1;
    static constexpr uint32_t TRANSACTION_repeatUnion = FIRST_CALL_TRANSACTION + 2;

    static std::shared_ptr<IMyCallback> fromBinder(const ::ndk::SpAIBinder& binder);
    static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IMyCallback>& instance);
    static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IMyCallback>* instance);
    static bool setDefaultImpl(const std::shared_ptr<IMyCallback>& impl);
    static const std::shared_ptr<IMyCallback>& getDefaultImpl();
    virtual ::ndk::ScopedAStatus repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
  private:
    static std::shared_ptr<IMyCallback> default_impl;
  };
  class IMyCallbackDefault : public IMyCallback {
  public:
    ::ndk::ScopedAStatus repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override;
    ::ndk::ScopedAStatus repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override;
    ::ndk::ScopedAStatus repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override;
    ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
    ::ndk::SpAIBinder asBinder() override;
    bool isRemote() override;
  };
  class BpMyCallback : public ::ndk::BpCInterface<IMyCallback> {
  public:
    explicit BpMyCallback(const ::ndk::SpAIBinder& binder);
    virtual ~BpMyCallback();

    ::ndk::ScopedAStatus repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override;
    ::ndk::ScopedAStatus repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override;
    ::ndk::ScopedAStatus repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override;
    ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
    int32_t _aidl_cached_version = -1;
    std::string _aidl_cached_hash = "-1";
    std::mutex _aidl_cached_hash_mutex;
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
  };
  class BnMyCallback : public ::ndk::BnCInterface<IMyCallback> {
  public:
    BnMyCallback();
    virtual ~BnMyCallback();
    ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
    ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
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
  protected:
    ::ndk::SpAIBinder createBinder() override;
  private:
  };
  static inline const int32_t version = 1;
  static inline const std::string hash = "88311b9118fb6fe9eff4a2ca19121de0587f6d5f";
  static constexpr uint32_t TRANSACTION_repeatParcelable = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_repeatEnum = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_repeatUnion = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_callMyCallback = FIRST_CALL_TRANSACTION + 3;

  static std::shared_ptr<ITrunkStableTest> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ITrunkStableTest>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ITrunkStableTest>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ITrunkStableTest>& impl);
  static const std::shared_ptr<ITrunkStableTest>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus callMyCallback(const std::shared_ptr<::aidl::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& in_cb) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ITrunkStableTest> default_impl;
};
class ITrunkStableTestDefault : public ITrunkStableTest {
public:
  ::ndk::ScopedAStatus repeatParcelable(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyParcelable* _aidl_return) override;
  ::ndk::ScopedAStatus repeatEnum(::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum* _aidl_return) override;
  ::ndk::ScopedAStatus repeatUnion(const ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion& in_input, ::aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion* _aidl_return) override;
  ::ndk::ScopedAStatus callMyCallback(const std::shared_ptr<::aidl::android::aidl::test::trunk::ITrunkStableTest::IMyCallback>& in_cb) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
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
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum, 3> enum_values<aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum> = {
  aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum::ZERO,
  aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum::ONE,
  aidl::android::aidl::test::trunk::ITrunkStableTest::MyEnum::TWO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
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
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace trunk
}  // namespace test
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag, 2> enum_values<aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag> = {
  aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag::a,
  aidl::android::aidl::test::trunk::ITrunkStableTest::MyUnion::Tag::b,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
