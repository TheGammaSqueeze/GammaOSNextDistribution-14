/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/ITestService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
#pragma once

#include <android/aidl/tests/BackendType.h>
#include <android/aidl/tests/ByteEnum.h>
#include <android/aidl/tests/CircularParcelable.h>
#include <android/aidl/tests/ICircular.h>
#include <android/aidl/tests/INamedCallback.h>
#include <android/aidl/tests/INewName.h>
#include <android/aidl/tests/IOldName.h>
#include <android/aidl/tests/ITestService.h>
#include <android/aidl/tests/IntEnum.h>
#include <android/aidl/tests/LongEnum.h>
#include <android/aidl/tests/RecursiveList.h>
#include <android/aidl/tests/StructuredParcelable.h>
#include <android/aidl/tests/Union.h>
#include <android/aidl/tests/extension/ExtendableParcelable.h>
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
#include <cassert>
#include <cstdint>
#include <optional>
#include <string>
#include <tests/simple_parcelable.h>
#include <tuple>
#include <type_traits>
#include <utility>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <variant>
#include <vector>

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace android::aidl::tests {
class CircularParcelable;
class ICircular;
class INamedCallback;
class INewName;
class IOldName;
class RecursiveList;
class StructuredParcelable;
}  // namespace android::aidl::tests
namespace android::aidl::tests::extension {
class ExtendableParcelable;
}  // namespace android::aidl::tests::extension
namespace android {
namespace aidl {
namespace tests {
class ITestServiceDelegator;

class ITestService : public ::android::IInterface {
public:
  typedef ITestServiceDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(TestService)
  class Empty : public ::android::Parcelable {
  public:
    inline bool operator==(const Empty&) const {
      return std::tie() == std::tie();
    }
    inline bool operator<(const Empty&) const {
      return std::tie() < std::tie();
    }
    inline bool operator!=(const Empty& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const Empty& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const Empty& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const Empty& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.ITestService.Empty");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "Empty{";
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class Empty
  class CompilerChecks : public ::android::Parcelable {
  public:
    class IFooDelegator;

    class IFoo : public ::android::IInterface {
    public:
      typedef IFooDelegator DefaultDelegator;
      DECLARE_META_INTERFACE(Foo)
    };  // class IFoo

    class IFooDefault : public IFoo {
    public:
      ::android::IBinder* onAsBinder() override {
        return nullptr;
      }
    };  // class IFooDefault
    class BpFoo : public ::android::BpInterface<IFoo> {
    public:
      explicit BpFoo(const ::android::sp<::android::IBinder>& _aidl_impl);
      virtual ~BpFoo() = default;
    };  // class BpFoo
    class BnFoo : public ::android::BnInterface<IFoo> {
    public:
      explicit BnFoo();
      ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
    };  // class BnFoo

    class IFooDelegator : public BnFoo {
    public:
      explicit IFooDelegator(const ::android::sp<IFoo> &impl) : _aidl_delegate(impl) {}

      ::android::sp<IFoo> getImpl() { return _aidl_delegate; }
    private:
      ::android::sp<IFoo> _aidl_delegate;
    };  // class IFooDelegator
    #pragma clang diagnostic push
    #pragma clang diagnostic ignored "-Wdeprecated-declarations"
    class HasDeprecated : public ::android::Parcelable {
    public:
      int32_t __attribute__((deprecated("field"))) deprecated = 0;
      inline bool operator==(const HasDeprecated& _rhs) const {
        return std::tie(deprecated) == std::tie(_rhs.deprecated);
      }
      inline bool operator<(const HasDeprecated& _rhs) const {
        return std::tie(deprecated) < std::tie(_rhs.deprecated);
      }
      inline bool operator!=(const HasDeprecated& _rhs) const {
        return !(*this == _rhs);
      }
      inline bool operator>(const HasDeprecated& _rhs) const {
        return _rhs < *this;
      }
      inline bool operator>=(const HasDeprecated& _rhs) const {
        return !(*this < _rhs);
      }
      inline bool operator<=(const HasDeprecated& _rhs) const {
        return !(_rhs < *this);
      }

      ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
      ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
      static const ::android::String16& getParcelableDescriptor() {
        static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.ITestService.CompilerChecks.HasDeprecated");
        return DESCRIPTOR;
      }
      inline std::string toString() const {
        std::ostringstream _aidl_os;
        _aidl_os << "HasDeprecated{";
        _aidl_os << "deprecated: " << ::android::internal::ToString(deprecated);
        _aidl_os << "}";
        return _aidl_os.str();
      }
    };  // class HasDeprecated
    #pragma clang diagnostic pop
    class UsingHasDeprecated : public ::android::Parcelable {
    public:
      enum class Tag : int32_t {
        n = 0,
        m = 1,
      };
      // Expose tag symbols for legacy code
      static const inline Tag n = Tag::n;
      static const inline Tag m = Tag::m;

      template<typename _Tp>
      static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, UsingHasDeprecated>;

      UsingHasDeprecated() : _value(std::in_place_index<static_cast<size_t>(n)>, int32_t(0)) { }

      template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
      // NOLINTNEXTLINE(google-explicit-constructor)
      constexpr UsingHasDeprecated(_Tp&& _arg)
          : _value(std::forward<_Tp>(_arg)) {}

      template <size_t _Np, typename... _Tp>
      constexpr explicit UsingHasDeprecated(std::in_place_index_t<_Np>, _Tp&&... _args)
          : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

      template <Tag _tag, typename... _Tp>
      static UsingHasDeprecated make(_Tp&&... _args) {
        return UsingHasDeprecated(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
      }

      template <Tag _tag, typename _Tp, typename... _Up>
      static UsingHasDeprecated make(std::initializer_list<_Tp> _il, _Up&&... _args) {
        return UsingHasDeprecated(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

      inline bool operator==(const UsingHasDeprecated& _rhs) const {
        return _value == _rhs._value;
      }
      inline bool operator<(const UsingHasDeprecated& _rhs) const {
        return _value < _rhs._value;
      }
      inline bool operator!=(const UsingHasDeprecated& _rhs) const {
        return !(*this == _rhs);
      }
      inline bool operator>(const UsingHasDeprecated& _rhs) const {
        return _rhs < *this;
      }
      inline bool operator>=(const UsingHasDeprecated& _rhs) const {
        return !(*this < _rhs);
      }
      inline bool operator<=(const UsingHasDeprecated& _rhs) const {
        return !(_rhs < *this);
      }

      ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
      ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
      static const ::android::String16& getParcelableDescriptor() {
        static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.ITestService.CompilerChecks.UsingHasDeprecated");
        return DESCRIPTOR;
      }
      inline std::string toString() const {
        std::ostringstream os;
        os << "UsingHasDeprecated{";
        switch (getTag()) {
        case n: os << "n: " << ::android::internal::ToString(get<n>()); break;
        case m: os << "m: " << ::android::internal::ToString(get<m>()); break;
        }
        os << "}";
        return os.str();
      }
    private:
      std::variant<int32_t, ::android::aidl::tests::ITestService::CompilerChecks::HasDeprecated> _value;
    };  // class UsingHasDeprecated
    class INoPrefixInterfaceDelegator;

    class INoPrefixInterface : public ::android::IInterface {
    public:
      typedef INoPrefixInterfaceDelegator DefaultDelegator;
      DECLARE_META_INTERFACE(NoPrefixInterface)
      class Nested : public ::android::Parcelable {
      public:
        inline bool operator==(const Nested&) const {
          return std::tie() == std::tie();
        }
        inline bool operator<(const Nested&) const {
          return std::tie() < std::tie();
        }
        inline bool operator!=(const Nested& _rhs) const {
          return !(*this == _rhs);
        }
        inline bool operator>(const Nested& _rhs) const {
          return _rhs < *this;
        }
        inline bool operator>=(const Nested& _rhs) const {
          return !(*this < _rhs);
        }
        inline bool operator<=(const Nested& _rhs) const {
          return !(_rhs < *this);
        }

        ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
        ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
        static const ::android::String16& getParcelableDescriptor() {
          static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.Nested");
          return DESCRIPTOR;
        }
        inline std::string toString() const {
          std::ostringstream _aidl_os;
          _aidl_os << "Nested{";
          _aidl_os << "}";
          return _aidl_os.str();
        }
      };  // class Nested
      class INestedNoPrefixInterfaceDelegator;

      class INestedNoPrefixInterface : public ::android::IInterface {
      public:
        typedef INestedNoPrefixInterfaceDelegator DefaultDelegator;
        DECLARE_META_INTERFACE(NestedNoPrefixInterface)
        virtual ::android::binder::Status foo() = 0;
      };  // class INestedNoPrefixInterface

      class INestedNoPrefixInterfaceDefault : public INestedNoPrefixInterface {
      public:
        ::android::IBinder* onAsBinder() override {
          return nullptr;
        }
        ::android::binder::Status foo() override {
          return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
        }
      };  // class INestedNoPrefixInterfaceDefault
      class BpNestedNoPrefixInterface : public ::android::BpInterface<INestedNoPrefixInterface> {
      public:
        explicit BpNestedNoPrefixInterface(const ::android::sp<::android::IBinder>& _aidl_impl);
        virtual ~BpNestedNoPrefixInterface() = default;
        ::android::binder::Status foo() override;
      };  // class BpNestedNoPrefixInterface
      class BnNestedNoPrefixInterface : public ::android::BnInterface<INestedNoPrefixInterface> {
      public:
        static constexpr uint32_t TRANSACTION_foo = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
        explicit BnNestedNoPrefixInterface();
        ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
      };  // class BnNestedNoPrefixInterface

      class INestedNoPrefixInterfaceDelegator : public BnNestedNoPrefixInterface {
      public:
        explicit INestedNoPrefixInterfaceDelegator(const ::android::sp<INestedNoPrefixInterface> &impl) : _aidl_delegate(impl) {}

        ::android::sp<INestedNoPrefixInterface> getImpl() { return _aidl_delegate; }
        ::android::binder::Status foo() override {
          return _aidl_delegate->foo();
        }
      private:
        ::android::sp<INestedNoPrefixInterface> _aidl_delegate;
      };  // class INestedNoPrefixInterfaceDelegator
      virtual ::android::binder::Status foo() = 0;
    };  // class INoPrefixInterface

    class INoPrefixInterfaceDefault : public INoPrefixInterface {
    public:
      ::android::IBinder* onAsBinder() override {
        return nullptr;
      }
      ::android::binder::Status foo() override {
        return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
      }
    };  // class INoPrefixInterfaceDefault
    class BpNoPrefixInterface : public ::android::BpInterface<INoPrefixInterface> {
    public:
      explicit BpNoPrefixInterface(const ::android::sp<::android::IBinder>& _aidl_impl);
      virtual ~BpNoPrefixInterface() = default;
      ::android::binder::Status foo() override;
    };  // class BpNoPrefixInterface
    class BnNoPrefixInterface : public ::android::BnInterface<INoPrefixInterface> {
    public:
      static constexpr uint32_t TRANSACTION_foo = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
      explicit BnNoPrefixInterface();
      ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
    };  // class BnNoPrefixInterface

    class INoPrefixInterfaceDelegator : public BnNoPrefixInterface {
    public:
      explicit INoPrefixInterfaceDelegator(const ::android::sp<INoPrefixInterface> &impl) : _aidl_delegate(impl) {}

      ::android::sp<INoPrefixInterface> getImpl() { return _aidl_delegate; }
      ::android::binder::Status foo() override {
        return _aidl_delegate->foo();
      }
    private:
      ::android::sp<INoPrefixInterface> _aidl_delegate;
    };  // class INoPrefixInterfaceDelegator
    ::android::sp<::android::IBinder> binder;
    ::android::sp<::android::IBinder> nullable_binder;
    ::std::vector<::android::sp<::android::IBinder>> binder_array;
    ::std::optional<::std::vector<::android::sp<::android::IBinder>>> nullable_binder_array;
    ::std::vector<::android::sp<::android::IBinder>> binder_list;
    ::std::optional<::std::vector<::android::sp<::android::IBinder>>> nullable_binder_list;
    ::android::os::ParcelFileDescriptor pfd;
    ::std::optional<::android::os::ParcelFileDescriptor> nullable_pfd;
    ::std::vector<::android::os::ParcelFileDescriptor> pfd_array;
    ::std::optional<::std::vector<::std::optional<::android::os::ParcelFileDescriptor>>> nullable_pfd_array;
    ::std::vector<::android::os::ParcelFileDescriptor> pfd_list;
    ::std::optional<::std::vector<::std::optional<::android::os::ParcelFileDescriptor>>> nullable_pfd_list;
    ::android::aidl::tests::ITestService::Empty parcel;
    ::std::optional<::android::aidl::tests::ITestService::Empty> nullable_parcel;
    ::std::vector<::android::aidl::tests::ITestService::Empty> parcel_array;
    ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>> nullable_parcel_array;
    ::std::vector<::android::aidl::tests::ITestService::Empty> parcel_list;
    ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>> nullable_parcel_list;
    inline bool operator==(const CompilerChecks& _rhs) const {
      return std::tie(binder, nullable_binder, binder_array, nullable_binder_array, binder_list, nullable_binder_list, pfd, nullable_pfd, pfd_array, nullable_pfd_array, pfd_list, nullable_pfd_list, parcel, nullable_parcel, parcel_array, nullable_parcel_array, parcel_list, nullable_parcel_list) == std::tie(_rhs.binder, _rhs.nullable_binder, _rhs.binder_array, _rhs.nullable_binder_array, _rhs.binder_list, _rhs.nullable_binder_list, _rhs.pfd, _rhs.nullable_pfd, _rhs.pfd_array, _rhs.nullable_pfd_array, _rhs.pfd_list, _rhs.nullable_pfd_list, _rhs.parcel, _rhs.nullable_parcel, _rhs.parcel_array, _rhs.nullable_parcel_array, _rhs.parcel_list, _rhs.nullable_parcel_list);
    }
    inline bool operator<(const CompilerChecks& _rhs) const {
      return std::tie(binder, nullable_binder, binder_array, nullable_binder_array, binder_list, nullable_binder_list, pfd, nullable_pfd, pfd_array, nullable_pfd_array, pfd_list, nullable_pfd_list, parcel, nullable_parcel, parcel_array, nullable_parcel_array, parcel_list, nullable_parcel_list) < std::tie(_rhs.binder, _rhs.nullable_binder, _rhs.binder_array, _rhs.nullable_binder_array, _rhs.binder_list, _rhs.nullable_binder_list, _rhs.pfd, _rhs.nullable_pfd, _rhs.pfd_array, _rhs.nullable_pfd_array, _rhs.pfd_list, _rhs.nullable_pfd_list, _rhs.parcel, _rhs.nullable_parcel, _rhs.parcel_array, _rhs.nullable_parcel_array, _rhs.parcel_list, _rhs.nullable_parcel_list);
    }
    inline bool operator!=(const CompilerChecks& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const CompilerChecks& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const CompilerChecks& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const CompilerChecks& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.ITestService.CompilerChecks");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "CompilerChecks{";
      _aidl_os << "binder: " << ::android::internal::ToString(binder);
      _aidl_os << ", nullable_binder: " << ::android::internal::ToString(nullable_binder);
      _aidl_os << ", binder_array: " << ::android::internal::ToString(binder_array);
      _aidl_os << ", nullable_binder_array: " << ::android::internal::ToString(nullable_binder_array);
      _aidl_os << ", binder_list: " << ::android::internal::ToString(binder_list);
      _aidl_os << ", nullable_binder_list: " << ::android::internal::ToString(nullable_binder_list);
      _aidl_os << ", pfd: " << ::android::internal::ToString(pfd);
      _aidl_os << ", nullable_pfd: " << ::android::internal::ToString(nullable_pfd);
      _aidl_os << ", pfd_array: " << ::android::internal::ToString(pfd_array);
      _aidl_os << ", nullable_pfd_array: " << ::android::internal::ToString(nullable_pfd_array);
      _aidl_os << ", pfd_list: " << ::android::internal::ToString(pfd_list);
      _aidl_os << ", nullable_pfd_list: " << ::android::internal::ToString(nullable_pfd_list);
      _aidl_os << ", parcel: " << ::android::internal::ToString(parcel);
      _aidl_os << ", nullable_parcel: " << ::android::internal::ToString(nullable_parcel);
      _aidl_os << ", parcel_array: " << ::android::internal::ToString(parcel_array);
      _aidl_os << ", nullable_parcel_array: " << ::android::internal::ToString(nullable_parcel_array);
      _aidl_os << ", parcel_list: " << ::android::internal::ToString(parcel_list);
      _aidl_os << ", nullable_parcel_list: " << ::android::internal::ToString(nullable_parcel_list);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class CompilerChecks
  enum : int32_t { CONSTANT = 42 };
  enum : int32_t { CONSTANT2 = -42 };
  enum : int32_t { CONSTANT3 = 42 };
  enum : int32_t { CONSTANT4 = 4 };
  enum : int32_t { CONSTANT5 = -4 };
  enum : int32_t { CONSTANT6 = 0 };
  enum : int32_t { CONSTANT7 = 0 };
  enum : int32_t { CONSTANT8 = 0 };
  enum : int32_t { CONSTANT9 = 86 };
  enum : int32_t { CONSTANT10 = 165 };
  enum : int32_t { CONSTANT11 = 250 };
  enum : int32_t { CONSTANT12 = -1 };
  enum : int8_t { BYTE_CONSTANT = 17 };
  enum : int64_t { LONG_CONSTANT = 1099511627776L };
  static const ::android::String16& STRING_CONSTANT();
  static const ::android::String16& STRING_CONSTANT2();
  static constexpr float FLOAT_CONSTANT = 1.000000f;
  static constexpr float FLOAT_CONSTANT2 = -1.000000f;
  static constexpr float FLOAT_CONSTANT3 = 1.000000f;
  static constexpr float FLOAT_CONSTANT4 = 2.200000f;
  static constexpr float FLOAT_CONSTANT5 = -2.200000f;
  static constexpr float FLOAT_CONSTANT6 = -0.000000f;
  static constexpr float FLOAT_CONSTANT7 = 0.000000f;
  static constexpr double DOUBLE_CONSTANT = 1.000000;
  static constexpr double DOUBLE_CONSTANT2 = -1.000000;
  static constexpr double DOUBLE_CONSTANT3 = 1.000000;
  static constexpr double DOUBLE_CONSTANT4 = 2.200000;
  static constexpr double DOUBLE_CONSTANT5 = -2.200000;
  static constexpr double DOUBLE_CONSTANT6 = -0.000000;
  static constexpr double DOUBLE_CONSTANT7 = 0.000000;
  static constexpr double DOUBLE_CONSTANT8 = 1.100000;
  static constexpr double DOUBLE_CONSTANT9 = -1.100000;
  static const ::std::string& STRING_CONSTANT_UTF8();
  enum : int32_t { A1 = 1 };
  enum : int32_t { A2 = 1 };
  enum : int32_t { A3 = 1 };
  enum : int32_t { A4 = 1 };
  enum : int32_t { A5 = 1 };
  enum : int32_t { A6 = 1 };
  enum : int32_t { A7 = 1 };
  enum : int32_t { A8 = 1 };
  enum : int32_t { A9 = 1 };
  enum : int32_t { A10 = 1 };
  enum : int32_t { A11 = 1 };
  enum : int32_t { A12 = 1 };
  enum : int32_t { A13 = 1 };
  enum : int32_t { A14 = 1 };
  enum : int32_t { A15 = 1 };
  enum : int32_t { A16 = 1 };
  enum : int32_t { A17 = 1 };
  enum : int32_t { A18 = 1 };
  enum : int32_t { A19 = 1 };
  enum : int32_t { A20 = 1 };
  enum : int32_t { A21 = 1 };
  enum : int32_t { A22 = 1 };
  enum : int32_t { A23 = 1 };
  enum : int32_t { A24 = 1 };
  enum : int32_t { A25 = 1 };
  enum : int32_t { A26 = 1 };
  enum : int32_t { A27 = 1 };
  enum : int32_t { A28 = 1 };
  enum : int32_t { A29 = 1 };
  enum : int32_t { A30 = 1 };
  enum : int32_t { A31 = 1 };
  enum : int32_t { A32 = 1 };
  enum : int32_t { A33 = 1 };
  enum : int32_t { A34 = 1 };
  enum : int32_t { A35 = 1 };
  enum : int32_t { A36 = 1 };
  enum : int32_t { A37 = 1 };
  enum : int32_t { A38 = 1 };
  enum : int32_t { A39 = 1 };
  enum : int32_t { A40 = 1 };
  enum : int32_t { A41 = 1 };
  enum : int32_t { A42 = 1 };
  enum : int32_t { A43 = 1 };
  enum : int32_t { A44 = 1 };
  enum : int32_t { A45 = 1 };
  enum : int32_t { A46 = 1 };
  enum : int32_t { A47 = 1 };
  enum : int32_t { A48 = 1 };
  enum : int32_t { A49 = 1 };
  enum : int32_t { A50 = 1 };
  enum : int32_t { A51 = 1 };
  enum : int32_t { A52 = 1 };
  enum : int32_t { A53 = 1 };
  enum : int32_t { A54 = 1 };
  enum : int32_t { A55 = 1 };
  enum : int32_t { A56 = 1 };
  enum : int32_t { A57 = 1 };
  virtual ::android::binder::Status UnimplementedMethod(int32_t arg, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status Deprecated() __attribute__((deprecated("to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"))) = 0;
  virtual ::android::binder::Status TestOneway() = 0;
  virtual ::android::binder::Status RepeatBoolean(bool token, bool* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatByte(int8_t token, int8_t* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatChar(char16_t token, char16_t* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatInt(int32_t token, int32_t* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatLong(int64_t token, int64_t* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatFloat(float token, float* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatDouble(double token, double* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatString(const ::android::String16& token, ::android::String16* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatByteEnum(::android::aidl::tests::ByteEnum token, ::android::aidl::tests::ByteEnum* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatIntEnum(::android::aidl::tests::IntEnum token, ::android::aidl::tests::IntEnum* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatLongEnum(::android::aidl::tests::LongEnum token, ::android::aidl::tests::LongEnum* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseBoolean(const ::std::vector<bool>& input, ::std::vector<bool>* repeated, ::std::vector<bool>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseByte(const ::std::vector<uint8_t>& input, ::std::vector<uint8_t>* repeated, ::std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseChar(const ::std::vector<char16_t>& input, ::std::vector<char16_t>* repeated, ::std::vector<char16_t>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseInt(const ::std::vector<int32_t>& input, ::std::vector<int32_t>* repeated, ::std::vector<int32_t>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseLong(const ::std::vector<int64_t>& input, ::std::vector<int64_t>* repeated, ::std::vector<int64_t>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseFloat(const ::std::vector<float>& input, ::std::vector<float>* repeated, ::std::vector<float>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseDouble(const ::std::vector<double>& input, ::std::vector<double>* repeated, ::std::vector<double>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseString(const ::std::vector<::android::String16>& input, ::std::vector<::android::String16>* repeated, ::std::vector<::android::String16>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseByteEnum(const ::std::vector<::android::aidl::tests::ByteEnum>& input, ::std::vector<::android::aidl::tests::ByteEnum>* repeated, ::std::vector<::android::aidl::tests::ByteEnum>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseIntEnum(const ::std::vector<::android::aidl::tests::IntEnum>& input, ::std::vector<::android::aidl::tests::IntEnum>* repeated, ::std::vector<::android::aidl::tests::IntEnum>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseLongEnum(const ::std::vector<::android::aidl::tests::LongEnum>& input, ::std::vector<::android::aidl::tests::LongEnum>* repeated, ::std::vector<::android::aidl::tests::LongEnum>* _aidl_return) = 0;
  virtual ::android::binder::Status GetOtherTestService(const ::android::String16& name, ::android::sp<::android::aidl::tests::INamedCallback>* _aidl_return) = 0;
  virtual ::android::binder::Status SetOtherTestService(const ::android::String16& name, const ::android::sp<::android::aidl::tests::INamedCallback>& service, bool* _aidl_return) = 0;
  virtual ::android::binder::Status VerifyName(const ::android::sp<::android::aidl::tests::INamedCallback>& service, const ::android::String16& name, bool* _aidl_return) = 0;
  virtual ::android::binder::Status GetInterfaceArray(const ::std::vector<::android::String16>& names, ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>* _aidl_return) = 0;
  virtual ::android::binder::Status VerifyNamesWithInterfaceArray(const ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>& services, const ::std::vector<::android::String16>& names, bool* _aidl_return) = 0;
  virtual ::android::binder::Status GetNullableInterfaceArray(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* _aidl_return) = 0;
  virtual ::android::binder::Status VerifyNamesWithNullableInterfaceArray(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& services, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, bool* _aidl_return) = 0;
  virtual ::android::binder::Status GetInterfaceList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* _aidl_return) = 0;
  virtual ::android::binder::Status VerifyNamesWithInterfaceList(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& services, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& names, bool* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseStringList(const ::std::vector<::android::String16>& input, ::std::vector<::android::String16>* repeated, ::std::vector<::android::String16>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatParcelFileDescriptor(const ::android::os::ParcelFileDescriptor& read, ::android::os::ParcelFileDescriptor* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseParcelFileDescriptorArray(const ::std::vector<::android::os::ParcelFileDescriptor>& input, ::std::vector<::android::os::ParcelFileDescriptor>* repeated, ::std::vector<::android::os::ParcelFileDescriptor>* _aidl_return) = 0;
  virtual ::android::binder::Status ThrowServiceException(int32_t code) = 0;
  virtual ::android::binder::Status RepeatNullableIntArray(const ::std::optional<::std::vector<int32_t>>& input, ::std::optional<::std::vector<int32_t>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableByteEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableIntEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableLongEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>& input, ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableString(const ::std::optional<::android::String16>& input, ::std::optional<::android::String16>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableStringList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& input, ::std::optional<::std::vector<::std::optional<::android::String16>>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableParcelable(const ::std::optional<::android::aidl::tests::ITestService::Empty>& input, ::std::optional<::android::aidl::tests::ITestService::Empty>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableParcelableArray(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& input, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableParcelableList(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& input, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* _aidl_return) = 0;
  virtual ::android::binder::Status TakesAnIBinder(const ::android::sp<::android::IBinder>& input) = 0;
  virtual ::android::binder::Status TakesANullableIBinder(const ::android::sp<::android::IBinder>& input) = 0;
  virtual ::android::binder::Status TakesAnIBinderList(const ::std::vector<::android::sp<::android::IBinder>>& input) = 0;
  virtual ::android::binder::Status TakesANullableIBinderList(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& input) = 0;
  virtual ::android::binder::Status RepeatUtf8CppString(const ::std::string& token, ::std::string* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatNullableUtf8CppString(const ::std::optional<::std::string>& token, ::std::optional<::std::string>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseUtf8CppString(const ::std::vector<::std::string>& input, ::std::vector<::std::string>* repeated, ::std::vector<::std::string>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseNullableUtf8CppString(const ::std::optional<::std::vector<::std::optional<::std::string>>>& input, ::std::optional<::std::vector<::std::optional<::std::string>>>* repeated, ::std::optional<::std::vector<::std::optional<::std::string>>>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseUtf8CppStringList(const ::std::optional<::std::vector<::std::optional<::std::string>>>& input, ::std::optional<::std::vector<::std::optional<::std::string>>>* repeated, ::std::optional<::std::vector<::std::optional<::std::string>>>* _aidl_return) = 0;
  virtual ::android::binder::Status GetCallback(bool return_null, ::android::sp<::android::aidl::tests::INamedCallback>* _aidl_return) = 0;
  virtual ::android::binder::Status FillOutStructuredParcelable(::android::aidl::tests::StructuredParcelable* parcel) = 0;
  virtual ::android::binder::Status RepeatExtendableParcelable(const ::android::aidl::tests::extension::ExtendableParcelable& ep, ::android::aidl::tests::extension::ExtendableParcelable* ep2) = 0;
  virtual ::android::binder::Status ReverseList(const ::android::aidl::tests::RecursiveList& list, ::android::aidl::tests::RecursiveList* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseIBinderArray(const ::std::vector<::android::sp<::android::IBinder>>& input, ::std::vector<::android::sp<::android::IBinder>>* repeated, ::std::vector<::android::sp<::android::IBinder>>* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseNullableIBinderArray(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& input, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* repeated, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* _aidl_return) = 0;
  virtual ::android::binder::Status RepeatSimpleParcelable(const ::android::aidl::tests::SimpleParcelable& input, ::android::aidl::tests::SimpleParcelable* repeat, ::android::aidl::tests::SimpleParcelable* _aidl_return) = 0;
  virtual ::android::binder::Status ReverseSimpleParcelables(const ::std::vector<::android::aidl::tests::SimpleParcelable>& input, ::std::vector<::android::aidl::tests::SimpleParcelable>* repeated, ::std::vector<::android::aidl::tests::SimpleParcelable>* _aidl_return) = 0;
  virtual ::android::binder::Status GetOldNameInterface(::android::sp<::android::aidl::tests::IOldName>* _aidl_return) = 0;
  virtual ::android::binder::Status GetNewNameInterface(::android::sp<::android::aidl::tests::INewName>* _aidl_return) = 0;
  virtual ::android::binder::Status GetUnionTags(const ::std::vector<::android::aidl::tests::Union>& input, ::std::vector<::android::aidl::tests::Union::Tag>* _aidl_return) = 0;
  virtual ::android::binder::Status GetCppJavaTests(::android::sp<::android::IBinder>* _aidl_return) = 0;
  virtual ::android::binder::Status getBackendType(::android::aidl::tests::BackendType* _aidl_return) = 0;
  virtual ::android::binder::Status GetCircular(::android::aidl::tests::CircularParcelable* cp, ::android::sp<::android::aidl::tests::ICircular>* _aidl_return) = 0;
};  // class ITestService

class ITestServiceDefault : public ITestService {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status UnimplementedMethod(int32_t /*arg*/, int32_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status Deprecated() override __attribute__((deprecated("to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"))) {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status TestOneway() override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatBoolean(bool /*token*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatByte(int8_t /*token*/, int8_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatChar(char16_t /*token*/, char16_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatInt(int32_t /*token*/, int32_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatLong(int64_t /*token*/, int64_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatFloat(float /*token*/, float* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatDouble(double /*token*/, double* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatString(const ::android::String16& /*token*/, ::android::String16* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatByteEnum(::android::aidl::tests::ByteEnum /*token*/, ::android::aidl::tests::ByteEnum* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatIntEnum(::android::aidl::tests::IntEnum /*token*/, ::android::aidl::tests::IntEnum* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatLongEnum(::android::aidl::tests::LongEnum /*token*/, ::android::aidl::tests::LongEnum* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseBoolean(const ::std::vector<bool>& /*input*/, ::std::vector<bool>* /*repeated*/, ::std::vector<bool>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseByte(const ::std::vector<uint8_t>& /*input*/, ::std::vector<uint8_t>* /*repeated*/, ::std::vector<uint8_t>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseChar(const ::std::vector<char16_t>& /*input*/, ::std::vector<char16_t>* /*repeated*/, ::std::vector<char16_t>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseInt(const ::std::vector<int32_t>& /*input*/, ::std::vector<int32_t>* /*repeated*/, ::std::vector<int32_t>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseLong(const ::std::vector<int64_t>& /*input*/, ::std::vector<int64_t>* /*repeated*/, ::std::vector<int64_t>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseFloat(const ::std::vector<float>& /*input*/, ::std::vector<float>* /*repeated*/, ::std::vector<float>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseDouble(const ::std::vector<double>& /*input*/, ::std::vector<double>* /*repeated*/, ::std::vector<double>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseString(const ::std::vector<::android::String16>& /*input*/, ::std::vector<::android::String16>* /*repeated*/, ::std::vector<::android::String16>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseByteEnum(const ::std::vector<::android::aidl::tests::ByteEnum>& /*input*/, ::std::vector<::android::aidl::tests::ByteEnum>* /*repeated*/, ::std::vector<::android::aidl::tests::ByteEnum>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseIntEnum(const ::std::vector<::android::aidl::tests::IntEnum>& /*input*/, ::std::vector<::android::aidl::tests::IntEnum>* /*repeated*/, ::std::vector<::android::aidl::tests::IntEnum>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseLongEnum(const ::std::vector<::android::aidl::tests::LongEnum>& /*input*/, ::std::vector<::android::aidl::tests::LongEnum>* /*repeated*/, ::std::vector<::android::aidl::tests::LongEnum>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetOtherTestService(const ::android::String16& /*name*/, ::android::sp<::android::aidl::tests::INamedCallback>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status SetOtherTestService(const ::android::String16& /*name*/, const ::android::sp<::android::aidl::tests::INamedCallback>& /*service*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status VerifyName(const ::android::sp<::android::aidl::tests::INamedCallback>& /*service*/, const ::android::String16& /*name*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetInterfaceArray(const ::std::vector<::android::String16>& /*names*/, ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status VerifyNamesWithInterfaceArray(const ::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>& /*services*/, const ::std::vector<::android::String16>& /*names*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetNullableInterfaceArray(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& /*names*/, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status VerifyNamesWithNullableInterfaceArray(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& /*services*/, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& /*names*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetInterfaceList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& /*names*/, ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status VerifyNamesWithInterfaceList(const ::std::optional<::std::vector<::android::sp<::android::aidl::tests::INamedCallback>>>& /*services*/, const ::std::optional<::std::vector<::std::optional<::android::String16>>>& /*names*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseStringList(const ::std::vector<::android::String16>& /*input*/, ::std::vector<::android::String16>* /*repeated*/, ::std::vector<::android::String16>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatParcelFileDescriptor(const ::android::os::ParcelFileDescriptor& /*read*/, ::android::os::ParcelFileDescriptor* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseParcelFileDescriptorArray(const ::std::vector<::android::os::ParcelFileDescriptor>& /*input*/, ::std::vector<::android::os::ParcelFileDescriptor>* /*repeated*/, ::std::vector<::android::os::ParcelFileDescriptor>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ThrowServiceException(int32_t /*code*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableIntArray(const ::std::optional<::std::vector<int32_t>>& /*input*/, ::std::optional<::std::vector<int32_t>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableByteEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>& /*input*/, ::std::optional<::std::vector<::android::aidl::tests::ByteEnum>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableIntEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>& /*input*/, ::std::optional<::std::vector<::android::aidl::tests::IntEnum>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableLongEnumArray(const ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>& /*input*/, ::std::optional<::std::vector<::android::aidl::tests::LongEnum>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableString(const ::std::optional<::android::String16>& /*input*/, ::std::optional<::android::String16>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableStringList(const ::std::optional<::std::vector<::std::optional<::android::String16>>>& /*input*/, ::std::optional<::std::vector<::std::optional<::android::String16>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableParcelable(const ::std::optional<::android::aidl::tests::ITestService::Empty>& /*input*/, ::std::optional<::android::aidl::tests::ITestService::Empty>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableParcelableArray(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& /*input*/, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableParcelableList(const ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>& /*input*/, ::std::optional<::std::vector<::std::optional<::android::aidl::tests::ITestService::Empty>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status TakesAnIBinder(const ::android::sp<::android::IBinder>& /*input*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status TakesANullableIBinder(const ::android::sp<::android::IBinder>& /*input*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status TakesAnIBinderList(const ::std::vector<::android::sp<::android::IBinder>>& /*input*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status TakesANullableIBinderList(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& /*input*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatUtf8CppString(const ::std::string& /*token*/, ::std::string* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatNullableUtf8CppString(const ::std::optional<::std::string>& /*token*/, ::std::optional<::std::string>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseUtf8CppString(const ::std::vector<::std::string>& /*input*/, ::std::vector<::std::string>* /*repeated*/, ::std::vector<::std::string>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseNullableUtf8CppString(const ::std::optional<::std::vector<::std::optional<::std::string>>>& /*input*/, ::std::optional<::std::vector<::std::optional<::std::string>>>* /*repeated*/, ::std::optional<::std::vector<::std::optional<::std::string>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseUtf8CppStringList(const ::std::optional<::std::vector<::std::optional<::std::string>>>& /*input*/, ::std::optional<::std::vector<::std::optional<::std::string>>>* /*repeated*/, ::std::optional<::std::vector<::std::optional<::std::string>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetCallback(bool /*return_null*/, ::android::sp<::android::aidl::tests::INamedCallback>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status FillOutStructuredParcelable(::android::aidl::tests::StructuredParcelable* /*parcel*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatExtendableParcelable(const ::android::aidl::tests::extension::ExtendableParcelable& /*ep*/, ::android::aidl::tests::extension::ExtendableParcelable* /*ep2*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseList(const ::android::aidl::tests::RecursiveList& /*list*/, ::android::aidl::tests::RecursiveList* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseIBinderArray(const ::std::vector<::android::sp<::android::IBinder>>& /*input*/, ::std::vector<::android::sp<::android::IBinder>>* /*repeated*/, ::std::vector<::android::sp<::android::IBinder>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseNullableIBinderArray(const ::std::optional<::std::vector<::android::sp<::android::IBinder>>>& /*input*/, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* /*repeated*/, ::std::optional<::std::vector<::android::sp<::android::IBinder>>>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status RepeatSimpleParcelable(const ::android::aidl::tests::SimpleParcelable& /*input*/, ::android::aidl::tests::SimpleParcelable* /*repeat*/, ::android::aidl::tests::SimpleParcelable* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status ReverseSimpleParcelables(const ::std::vector<::android::aidl::tests::SimpleParcelable>& /*input*/, ::std::vector<::android::aidl::tests::SimpleParcelable>* /*repeated*/, ::std::vector<::android::aidl::tests::SimpleParcelable>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetOldNameInterface(::android::sp<::android::aidl::tests::IOldName>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetNewNameInterface(::android::sp<::android::aidl::tests::INewName>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetUnionTags(const ::std::vector<::android::aidl::tests::Union>& /*input*/, ::std::vector<::android::aidl::tests::Union::Tag>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetCppJavaTests(::android::sp<::android::IBinder>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getBackendType(::android::aidl::tests::BackendType* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status GetCircular(::android::aidl::tests::CircularParcelable* /*cp*/, ::android::sp<::android::aidl::tests::ICircular>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ITestServiceDefault
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(ITestService::CompilerChecks::UsingHasDeprecated::Tag val) {
  switch(val) {
  case ITestService::CompilerChecks::UsingHasDeprecated::Tag::n:
    return "n";
  case ITestService::CompilerChecks::UsingHasDeprecated::Tag::m:
    return "m";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag, 2> enum_values<::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag> = {
  ::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag::n,
  ::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag::m,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
