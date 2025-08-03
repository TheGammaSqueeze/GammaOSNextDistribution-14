/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ITestService.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
#pragma once

#include <array>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <tests/simple_parcelable_ndk.h>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_ibinder.h>
#include <android/binder_ibinder_platform.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcel_platform.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/tests/BackendType.h>
#include <aidl/android/aidl/tests/ByteEnum.h>
#include <aidl/android/aidl/tests/CircularParcelable.h>
#include <aidl/android/aidl/tests/ICircular.h>
#include <aidl/android/aidl/tests/INamedCallback.h>
#include <aidl/android/aidl/tests/INewName.h>
#include <aidl/android/aidl/tests/IOldName.h>
#include <aidl/android/aidl/tests/ITestService.h>
#include <aidl/android/aidl/tests/IntEnum.h>
#include <aidl/android/aidl/tests/LongEnum.h>
#include <aidl/android/aidl/tests/RecursiveList.h>
#include <aidl/android/aidl/tests/StructuredParcelable.h>
#include <aidl/android/aidl/tests/Union.h>
#include <aidl/android/aidl/tests/extension/ExtendableParcelable.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl::android::aidl::tests {
class CircularParcelable;
class ICircular;
class INamedCallback;
class INewName;
class IOldName;
class RecursiveList;
class StructuredParcelable;
}  // namespace aidl::android::aidl::tests
namespace aidl::android::aidl::tests::extension {
class ExtendableParcelable;
}  // namespace aidl::android::aidl::tests::extension
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class ITestServiceDelegator;

class ITestService : public ::ndk::ICInterface {
public:
  typedef ITestServiceDelegator DefaultDelegator;
  static const char* descriptor;
  ITestService();
  virtual ~ITestService();

  class Empty {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;


    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

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

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "Empty{";
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };
  class CompilerChecks {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    class IFooDelegator;

    class IFoo : public ::ndk::ICInterface {
    public:
      typedef IFooDelegator DefaultDelegator;
      static const char* descriptor;
      IFoo();
      virtual ~IFoo();


      static std::shared_ptr<IFoo> fromBinder(const ::ndk::SpAIBinder& binder);
      static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IFoo>& instance);
      static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IFoo>* instance);
      static bool setDefaultImpl(const std::shared_ptr<IFoo>& impl);
      static const std::shared_ptr<IFoo>& getDefaultImpl();
    private:
      static std::shared_ptr<IFoo> default_impl;
    };
    class IFooDefault : public IFoo {
    public:
      ::ndk::SpAIBinder asBinder() override;
      bool isRemote() override;
    };
    class BpFoo : public ::ndk::BpCInterface<IFoo> {
    public:
      explicit BpFoo(const ::ndk::SpAIBinder& binder);
      virtual ~BpFoo();

    };
    class BnFoo : public ::ndk::BnCInterface<IFoo> {
    public:
      BnFoo();
      virtual ~BnFoo();
    protected:
      ::ndk::SpAIBinder createBinder() override;
    private:
    };
    #pragma clang diagnostic push
    #pragma clang diagnostic ignored "-Wdeprecated-declarations"
    class HasDeprecated {
    public:
      typedef std::false_type fixed_size;
      static const char* descriptor;

      int32_t __attribute__((deprecated("field"))) deprecated = 0;

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

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

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
      inline std::string toString() const {
        std::ostringstream _aidl_os;
        _aidl_os << "HasDeprecated{";
        _aidl_os << "deprecated: " << ::android::internal::ToString(deprecated);
        _aidl_os << "}";
        return _aidl_os.str();
      }
    };
    #pragma clang diagnostic pop
    class UsingHasDeprecated {
    public:
      typedef std::false_type fixed_size;
      static const char* descriptor;

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

      binder_status_t readFromParcel(const AParcel* _parcel);
      binder_status_t writeToParcel(AParcel* _parcel) const;

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

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
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
      std::variant<int32_t, ::aidl::android::aidl::tests::ITestService::CompilerChecks::HasDeprecated> _value;
    };
    class INoPrefixInterfaceDelegator;

    class INoPrefixInterface : public ::ndk::ICInterface {
    public:
      typedef INoPrefixInterfaceDelegator DefaultDelegator;
      static const char* descriptor;
      INoPrefixInterface();
      virtual ~INoPrefixInterface();

      class Nested {
      public:
        typedef std::false_type fixed_size;
        static const char* descriptor;


        binder_status_t readFromParcel(const AParcel* parcel);
        binder_status_t writeToParcel(AParcel* parcel) const;

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

        static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
        inline std::string toString() const {
          std::ostringstream _aidl_os;
          _aidl_os << "Nested{";
          _aidl_os << "}";
          return _aidl_os.str();
        }
      };
      class INestedNoPrefixInterfaceDelegator;

      class INestedNoPrefixInterface : public ::ndk::ICInterface {
      public:
        typedef INestedNoPrefixInterfaceDelegator DefaultDelegator;
        static const char* descriptor;
        INestedNoPrefixInterface();
        virtual ~INestedNoPrefixInterface();

        static constexpr uint32_t TRANSACTION_foo = FIRST_CALL_TRANSACTION + 0;

        static std::shared_ptr<INestedNoPrefixInterface> fromBinder(const ::ndk::SpAIBinder& binder);
        static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<INestedNoPrefixInterface>& instance);
        static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<INestedNoPrefixInterface>* instance);
        static bool setDefaultImpl(const std::shared_ptr<INestedNoPrefixInterface>& impl);
        static const std::shared_ptr<INestedNoPrefixInterface>& getDefaultImpl();
        virtual ::ndk::ScopedAStatus foo() = 0;
      private:
        static std::shared_ptr<INestedNoPrefixInterface> default_impl;
      };
      class INestedNoPrefixInterfaceDefault : public INestedNoPrefixInterface {
      public:
        ::ndk::ScopedAStatus foo() override;
        ::ndk::SpAIBinder asBinder() override;
        bool isRemote() override;
      };
      class BpNestedNoPrefixInterface : public ::ndk::BpCInterface<INestedNoPrefixInterface> {
      public:
        explicit BpNestedNoPrefixInterface(const ::ndk::SpAIBinder& binder);
        virtual ~BpNestedNoPrefixInterface();

        ::ndk::ScopedAStatus foo() override;
      };
      class BnNestedNoPrefixInterface : public ::ndk::BnCInterface<INestedNoPrefixInterface> {
      public:
        BnNestedNoPrefixInterface();
        virtual ~BnNestedNoPrefixInterface();
      protected:
        ::ndk::SpAIBinder createBinder() override;
      private:
      };
      static constexpr uint32_t TRANSACTION_foo = FIRST_CALL_TRANSACTION + 0;

      static std::shared_ptr<INoPrefixInterface> fromBinder(const ::ndk::SpAIBinder& binder);
      static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<INoPrefixInterface>& instance);
      static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<INoPrefixInterface>* instance);
      static bool setDefaultImpl(const std::shared_ptr<INoPrefixInterface>& impl);
      static const std::shared_ptr<INoPrefixInterface>& getDefaultImpl();
      virtual ::ndk::ScopedAStatus foo() = 0;
    private:
      static std::shared_ptr<INoPrefixInterface> default_impl;
    };
    class INoPrefixInterfaceDefault : public INoPrefixInterface {
    public:
      ::ndk::ScopedAStatus foo() override;
      ::ndk::SpAIBinder asBinder() override;
      bool isRemote() override;
    };
    class BpNoPrefixInterface : public ::ndk::BpCInterface<INoPrefixInterface> {
    public:
      explicit BpNoPrefixInterface(const ::ndk::SpAIBinder& binder);
      virtual ~BpNoPrefixInterface();

      ::ndk::ScopedAStatus foo() override;
    };
    class BnNoPrefixInterface : public ::ndk::BnCInterface<INoPrefixInterface> {
    public:
      BnNoPrefixInterface();
      virtual ~BnNoPrefixInterface();
    protected:
      ::ndk::SpAIBinder createBinder() override;
    private:
    };
    ::ndk::SpAIBinder binder;
    ::ndk::SpAIBinder nullable_binder;
    std::vector<::ndk::SpAIBinder> binder_array;
    std::optional<std::vector<::ndk::SpAIBinder>> nullable_binder_array;
    std::vector<::ndk::SpAIBinder> binder_list;
    std::optional<std::vector<::ndk::SpAIBinder>> nullable_binder_list;
    ::ndk::ScopedFileDescriptor pfd;
    ::ndk::ScopedFileDescriptor nullable_pfd;
    std::vector<::ndk::ScopedFileDescriptor> pfd_array;
    std::optional<std::vector<::ndk::ScopedFileDescriptor>> nullable_pfd_array;
    std::vector<::ndk::ScopedFileDescriptor> pfd_list;
    std::optional<std::vector<::ndk::ScopedFileDescriptor>> nullable_pfd_list;
    ::aidl::android::aidl::tests::ITestService::Empty parcel;
    std::optional<::aidl::android::aidl::tests::ITestService::Empty> nullable_parcel;
    std::vector<::aidl::android::aidl::tests::ITestService::Empty> parcel_array;
    std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>> nullable_parcel_array;
    std::vector<::aidl::android::aidl::tests::ITestService::Empty> parcel_list;
    std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>> nullable_parcel_list;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

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

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
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
  };
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
  static const char* STRING_CONSTANT;
  static const char* STRING_CONSTANT2;
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
  static const char* STRING_CONSTANT_UTF8;
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
  static constexpr uint32_t TRANSACTION_UnimplementedMethod = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_Deprecated = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_TestOneway = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_RepeatBoolean = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_RepeatByte = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_RepeatChar = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_RepeatInt = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_RepeatLong = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_RepeatFloat = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_RepeatDouble = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_RepeatString = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_RepeatByteEnum = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_RepeatIntEnum = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_RepeatLongEnum = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_ReverseBoolean = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_ReverseByte = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_ReverseChar = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_ReverseInt = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_ReverseLong = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_ReverseFloat = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_ReverseDouble = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_ReverseString = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_ReverseByteEnum = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_ReverseIntEnum = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_ReverseLongEnum = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_GetOtherTestService = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_SetOtherTestService = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_VerifyName = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_GetInterfaceArray = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_VerifyNamesWithInterfaceArray = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_GetNullableInterfaceArray = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_VerifyNamesWithNullableInterfaceArray = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_GetInterfaceList = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_VerifyNamesWithInterfaceList = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_ReverseStringList = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_RepeatParcelFileDescriptor = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_ReverseParcelFileDescriptorArray = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_ThrowServiceException = FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_RepeatNullableIntArray = FIRST_CALL_TRANSACTION + 38;
  static constexpr uint32_t TRANSACTION_RepeatNullableByteEnumArray = FIRST_CALL_TRANSACTION + 39;
  static constexpr uint32_t TRANSACTION_RepeatNullableIntEnumArray = FIRST_CALL_TRANSACTION + 40;
  static constexpr uint32_t TRANSACTION_RepeatNullableLongEnumArray = FIRST_CALL_TRANSACTION + 41;
  static constexpr uint32_t TRANSACTION_RepeatNullableString = FIRST_CALL_TRANSACTION + 42;
  static constexpr uint32_t TRANSACTION_RepeatNullableStringList = FIRST_CALL_TRANSACTION + 43;
  static constexpr uint32_t TRANSACTION_RepeatNullableParcelable = FIRST_CALL_TRANSACTION + 44;
  static constexpr uint32_t TRANSACTION_RepeatNullableParcelableArray = FIRST_CALL_TRANSACTION + 45;
  static constexpr uint32_t TRANSACTION_RepeatNullableParcelableList = FIRST_CALL_TRANSACTION + 46;
  static constexpr uint32_t TRANSACTION_TakesAnIBinder = FIRST_CALL_TRANSACTION + 47;
  static constexpr uint32_t TRANSACTION_TakesANullableIBinder = FIRST_CALL_TRANSACTION + 48;
  static constexpr uint32_t TRANSACTION_TakesAnIBinderList = FIRST_CALL_TRANSACTION + 49;
  static constexpr uint32_t TRANSACTION_TakesANullableIBinderList = FIRST_CALL_TRANSACTION + 50;
  static constexpr uint32_t TRANSACTION_RepeatUtf8CppString = FIRST_CALL_TRANSACTION + 51;
  static constexpr uint32_t TRANSACTION_RepeatNullableUtf8CppString = FIRST_CALL_TRANSACTION + 52;
  static constexpr uint32_t TRANSACTION_ReverseUtf8CppString = FIRST_CALL_TRANSACTION + 53;
  static constexpr uint32_t TRANSACTION_ReverseNullableUtf8CppString = FIRST_CALL_TRANSACTION + 54;
  static constexpr uint32_t TRANSACTION_ReverseUtf8CppStringList = FIRST_CALL_TRANSACTION + 55;
  static constexpr uint32_t TRANSACTION_GetCallback = FIRST_CALL_TRANSACTION + 56;
  static constexpr uint32_t TRANSACTION_FillOutStructuredParcelable = FIRST_CALL_TRANSACTION + 57;
  static constexpr uint32_t TRANSACTION_RepeatExtendableParcelable = FIRST_CALL_TRANSACTION + 58;
  static constexpr uint32_t TRANSACTION_ReverseList = FIRST_CALL_TRANSACTION + 59;
  static constexpr uint32_t TRANSACTION_ReverseIBinderArray = FIRST_CALL_TRANSACTION + 60;
  static constexpr uint32_t TRANSACTION_ReverseNullableIBinderArray = FIRST_CALL_TRANSACTION + 61;
  static constexpr uint32_t TRANSACTION_RepeatSimpleParcelable = FIRST_CALL_TRANSACTION + 62;
  static constexpr uint32_t TRANSACTION_ReverseSimpleParcelables = FIRST_CALL_TRANSACTION + 63;
  static constexpr uint32_t TRANSACTION_GetOldNameInterface = FIRST_CALL_TRANSACTION + 64;
  static constexpr uint32_t TRANSACTION_GetNewNameInterface = FIRST_CALL_TRANSACTION + 65;
  static constexpr uint32_t TRANSACTION_GetUnionTags = FIRST_CALL_TRANSACTION + 66;
  static constexpr uint32_t TRANSACTION_GetCppJavaTests = FIRST_CALL_TRANSACTION + 67;
  static constexpr uint32_t TRANSACTION_getBackendType = FIRST_CALL_TRANSACTION + 68;
  static constexpr uint32_t TRANSACTION_GetCircular = FIRST_CALL_TRANSACTION + 69;

  static std::shared_ptr<ITestService> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ITestService>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ITestService>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ITestService>& impl);
  static const std::shared_ptr<ITestService>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus UnimplementedMethod(int32_t in_arg, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus Deprecated() __attribute__((deprecated("to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens"))) = 0;
  virtual ::ndk::ScopedAStatus TestOneway() = 0;
  virtual ::ndk::ScopedAStatus RepeatBoolean(bool in_token, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatByte(int8_t in_token, int8_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatChar(char16_t in_token, char16_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatInt(int32_t in_token, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatLong(int64_t in_token, int64_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatFloat(float in_token, float* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatDouble(double in_token, double* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatString(const std::string& in_token, std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatByteEnum(::aidl::android::aidl::tests::ByteEnum in_token, ::aidl::android::aidl::tests::ByteEnum* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatIntEnum(::aidl::android::aidl::tests::IntEnum in_token, ::aidl::android::aidl::tests::IntEnum* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatLongEnum(::aidl::android::aidl::tests::LongEnum in_token, ::aidl::android::aidl::tests::LongEnum* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseBoolean(const std::vector<bool>& in_input, std::vector<bool>* out_repeated, std::vector<bool>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseByte(const std::vector<uint8_t>& in_input, std::vector<uint8_t>* out_repeated, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseChar(const std::vector<char16_t>& in_input, std::vector<char16_t>* out_repeated, std::vector<char16_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseInt(const std::vector<int32_t>& in_input, std::vector<int32_t>* out_repeated, std::vector<int32_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseLong(const std::vector<int64_t>& in_input, std::vector<int64_t>* out_repeated, std::vector<int64_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseFloat(const std::vector<float>& in_input, std::vector<float>* out_repeated, std::vector<float>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseDouble(const std::vector<double>& in_input, std::vector<double>* out_repeated, std::vector<double>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseString(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseByteEnum(const std::vector<::aidl::android::aidl::tests::ByteEnum>& in_input, std::vector<::aidl::android::aidl::tests::ByteEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::ByteEnum>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseIntEnum(const std::vector<::aidl::android::aidl::tests::IntEnum>& in_input, std::vector<::aidl::android::aidl::tests::IntEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::IntEnum>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseLongEnum(const std::vector<::aidl::android::aidl::tests::LongEnum>& in_input, std::vector<::aidl::android::aidl::tests::LongEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::LongEnum>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetOtherTestService(const std::string& in_name, std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus SetOtherTestService(const std::string& in_name, const std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>& in_service, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus VerifyName(const std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>& in_service, const std::string& in_name, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetInterfaceArray(const std::vector<std::string>& in_names, std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus VerifyNamesWithInterfaceArray(const std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>& in_services, const std::vector<std::string>& in_names, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetNullableInterfaceArray(const std::optional<std::vector<std::optional<std::string>>>& in_names, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus VerifyNamesWithNullableInterfaceArray(const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>& in_services, const std::optional<std::vector<std::optional<std::string>>>& in_names, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetInterfaceList(const std::optional<std::vector<std::optional<std::string>>>& in_names, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus VerifyNamesWithInterfaceList(const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>& in_services, const std::optional<std::vector<std::optional<std::string>>>& in_names, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseStringList(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatParcelFileDescriptor(const ::ndk::ScopedFileDescriptor& in_read, ::ndk::ScopedFileDescriptor* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseParcelFileDescriptorArray(const std::vector<::ndk::ScopedFileDescriptor>& in_input, std::vector<::ndk::ScopedFileDescriptor>* out_repeated, std::vector<::ndk::ScopedFileDescriptor>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ThrowServiceException(int32_t in_code) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableIntArray(const std::optional<std::vector<int32_t>>& in_input, std::optional<std::vector<int32_t>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableByteEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::ByteEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::ByteEnum>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableIntEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::IntEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::IntEnum>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableLongEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::LongEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::LongEnum>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableString(const std::optional<std::string>& in_input, std::optional<std::string>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableStringList(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableParcelable(const std::optional<::aidl::android::aidl::tests::ITestService::Empty>& in_input, std::optional<::aidl::android::aidl::tests::ITestService::Empty>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableParcelableArray(const std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>& in_input, std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableParcelableList(const std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>& in_input, std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus TakesAnIBinder(const ::ndk::SpAIBinder& in_input) = 0;
  virtual ::ndk::ScopedAStatus TakesANullableIBinder(const ::ndk::SpAIBinder& in_input) = 0;
  virtual ::ndk::ScopedAStatus TakesAnIBinderList(const std::vector<::ndk::SpAIBinder>& in_input) = 0;
  virtual ::ndk::ScopedAStatus TakesANullableIBinderList(const std::optional<std::vector<::ndk::SpAIBinder>>& in_input) = 0;
  virtual ::ndk::ScopedAStatus RepeatUtf8CppString(const std::string& in_token, std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatNullableUtf8CppString(const std::optional<std::string>& in_token, std::optional<std::string>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseUtf8CppString(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseNullableUtf8CppString(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* out_repeated, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseUtf8CppStringList(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* out_repeated, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetCallback(bool in_return_null, std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus FillOutStructuredParcelable(::aidl::android::aidl::tests::StructuredParcelable* in_parcel) = 0;
  virtual ::ndk::ScopedAStatus RepeatExtendableParcelable(const ::aidl::android::aidl::tests::extension::ExtendableParcelable& in_ep, ::aidl::android::aidl::tests::extension::ExtendableParcelable* out_ep2) = 0;
  virtual ::ndk::ScopedAStatus ReverseList(const ::aidl::android::aidl::tests::RecursiveList& in_list, ::aidl::android::aidl::tests::RecursiveList* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseIBinderArray(const std::vector<::ndk::SpAIBinder>& in_input, std::vector<::ndk::SpAIBinder>* out_repeated, std::vector<::ndk::SpAIBinder>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseNullableIBinderArray(const std::optional<std::vector<::ndk::SpAIBinder>>& in_input, std::optional<std::vector<::ndk::SpAIBinder>>* out_repeated, std::optional<std::vector<::ndk::SpAIBinder>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus RepeatSimpleParcelable(const ::aidl::android::aidl::tests::SimpleParcelable& in_input, ::aidl::android::aidl::tests::SimpleParcelable* out_repeat, ::aidl::android::aidl::tests::SimpleParcelable* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus ReverseSimpleParcelables(const std::vector<::aidl::android::aidl::tests::SimpleParcelable>& in_input, std::vector<::aidl::android::aidl::tests::SimpleParcelable>* out_repeated, std::vector<::aidl::android::aidl::tests::SimpleParcelable>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetOldNameInterface(std::shared_ptr<::aidl::android::aidl::tests::IOldName>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetNewNameInterface(std::shared_ptr<::aidl::android::aidl::tests::INewName>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetUnionTags(const std::vector<::aidl::android::aidl::tests::Union>& in_input, std::vector<::aidl::android::aidl::tests::Union::Tag>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetCppJavaTests(::ndk::SpAIBinder* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getBackendType(::aidl::android::aidl::tests::BackendType* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus GetCircular(::aidl::android::aidl::tests::CircularParcelable* out_cp, std::shared_ptr<::aidl::android::aidl::tests::ICircular>* _aidl_return) = 0;
private:
  static std::shared_ptr<ITestService> default_impl;
};
class ITestServiceDefault : public ITestService {
public:
  ::ndk::ScopedAStatus UnimplementedMethod(int32_t in_arg, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus Deprecated() override __attribute__((deprecated("to make sure we have something in system/tools/aidl which does a compile check of deprecated and make sure this is reflected in goldens")));
  ::ndk::ScopedAStatus TestOneway() override;
  ::ndk::ScopedAStatus RepeatBoolean(bool in_token, bool* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatByte(int8_t in_token, int8_t* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatChar(char16_t in_token, char16_t* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatInt(int32_t in_token, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatLong(int64_t in_token, int64_t* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatFloat(float in_token, float* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatDouble(double in_token, double* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatString(const std::string& in_token, std::string* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatByteEnum(::aidl::android::aidl::tests::ByteEnum in_token, ::aidl::android::aidl::tests::ByteEnum* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatIntEnum(::aidl::android::aidl::tests::IntEnum in_token, ::aidl::android::aidl::tests::IntEnum* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatLongEnum(::aidl::android::aidl::tests::LongEnum in_token, ::aidl::android::aidl::tests::LongEnum* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseBoolean(const std::vector<bool>& in_input, std::vector<bool>* out_repeated, std::vector<bool>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseByte(const std::vector<uint8_t>& in_input, std::vector<uint8_t>* out_repeated, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseChar(const std::vector<char16_t>& in_input, std::vector<char16_t>* out_repeated, std::vector<char16_t>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseInt(const std::vector<int32_t>& in_input, std::vector<int32_t>* out_repeated, std::vector<int32_t>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseLong(const std::vector<int64_t>& in_input, std::vector<int64_t>* out_repeated, std::vector<int64_t>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseFloat(const std::vector<float>& in_input, std::vector<float>* out_repeated, std::vector<float>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseDouble(const std::vector<double>& in_input, std::vector<double>* out_repeated, std::vector<double>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseString(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseByteEnum(const std::vector<::aidl::android::aidl::tests::ByteEnum>& in_input, std::vector<::aidl::android::aidl::tests::ByteEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::ByteEnum>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseIntEnum(const std::vector<::aidl::android::aidl::tests::IntEnum>& in_input, std::vector<::aidl::android::aidl::tests::IntEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::IntEnum>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseLongEnum(const std::vector<::aidl::android::aidl::tests::LongEnum>& in_input, std::vector<::aidl::android::aidl::tests::LongEnum>* out_repeated, std::vector<::aidl::android::aidl::tests::LongEnum>* _aidl_return) override;
  ::ndk::ScopedAStatus GetOtherTestService(const std::string& in_name, std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>* _aidl_return) override;
  ::ndk::ScopedAStatus SetOtherTestService(const std::string& in_name, const std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>& in_service, bool* _aidl_return) override;
  ::ndk::ScopedAStatus VerifyName(const std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>& in_service, const std::string& in_name, bool* _aidl_return) override;
  ::ndk::ScopedAStatus GetInterfaceArray(const std::vector<std::string>& in_names, std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>* _aidl_return) override;
  ::ndk::ScopedAStatus VerifyNamesWithInterfaceArray(const std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>& in_services, const std::vector<std::string>& in_names, bool* _aidl_return) override;
  ::ndk::ScopedAStatus GetNullableInterfaceArray(const std::optional<std::vector<std::optional<std::string>>>& in_names, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>* _aidl_return) override;
  ::ndk::ScopedAStatus VerifyNamesWithNullableInterfaceArray(const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>& in_services, const std::optional<std::vector<std::optional<std::string>>>& in_names, bool* _aidl_return) override;
  ::ndk::ScopedAStatus GetInterfaceList(const std::optional<std::vector<std::optional<std::string>>>& in_names, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>* _aidl_return) override;
  ::ndk::ScopedAStatus VerifyNamesWithInterfaceList(const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>>>& in_services, const std::optional<std::vector<std::optional<std::string>>>& in_names, bool* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseStringList(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatParcelFileDescriptor(const ::ndk::ScopedFileDescriptor& in_read, ::ndk::ScopedFileDescriptor* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseParcelFileDescriptorArray(const std::vector<::ndk::ScopedFileDescriptor>& in_input, std::vector<::ndk::ScopedFileDescriptor>* out_repeated, std::vector<::ndk::ScopedFileDescriptor>* _aidl_return) override;
  ::ndk::ScopedAStatus ThrowServiceException(int32_t in_code) override;
  ::ndk::ScopedAStatus RepeatNullableIntArray(const std::optional<std::vector<int32_t>>& in_input, std::optional<std::vector<int32_t>>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableByteEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::ByteEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::ByteEnum>>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableIntEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::IntEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::IntEnum>>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableLongEnumArray(const std::optional<std::vector<::aidl::android::aidl::tests::LongEnum>>& in_input, std::optional<std::vector<::aidl::android::aidl::tests::LongEnum>>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableString(const std::optional<std::string>& in_input, std::optional<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableStringList(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableParcelable(const std::optional<::aidl::android::aidl::tests::ITestService::Empty>& in_input, std::optional<::aidl::android::aidl::tests::ITestService::Empty>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableParcelableArray(const std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>& in_input, std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableParcelableList(const std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>& in_input, std::optional<std::vector<std::optional<::aidl::android::aidl::tests::ITestService::Empty>>>* _aidl_return) override;
  ::ndk::ScopedAStatus TakesAnIBinder(const ::ndk::SpAIBinder& in_input) override;
  ::ndk::ScopedAStatus TakesANullableIBinder(const ::ndk::SpAIBinder& in_input) override;
  ::ndk::ScopedAStatus TakesAnIBinderList(const std::vector<::ndk::SpAIBinder>& in_input) override;
  ::ndk::ScopedAStatus TakesANullableIBinderList(const std::optional<std::vector<::ndk::SpAIBinder>>& in_input) override;
  ::ndk::ScopedAStatus RepeatUtf8CppString(const std::string& in_token, std::string* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatNullableUtf8CppString(const std::optional<std::string>& in_token, std::optional<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseUtf8CppString(const std::vector<std::string>& in_input, std::vector<std::string>* out_repeated, std::vector<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseNullableUtf8CppString(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* out_repeated, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseUtf8CppStringList(const std::optional<std::vector<std::optional<std::string>>>& in_input, std::optional<std::vector<std::optional<std::string>>>* out_repeated, std::optional<std::vector<std::optional<std::string>>>* _aidl_return) override;
  ::ndk::ScopedAStatus GetCallback(bool in_return_null, std::shared_ptr<::aidl::android::aidl::tests::INamedCallback>* _aidl_return) override;
  ::ndk::ScopedAStatus FillOutStructuredParcelable(::aidl::android::aidl::tests::StructuredParcelable* in_parcel) override;
  ::ndk::ScopedAStatus RepeatExtendableParcelable(const ::aidl::android::aidl::tests::extension::ExtendableParcelable& in_ep, ::aidl::android::aidl::tests::extension::ExtendableParcelable* out_ep2) override;
  ::ndk::ScopedAStatus ReverseList(const ::aidl::android::aidl::tests::RecursiveList& in_list, ::aidl::android::aidl::tests::RecursiveList* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseIBinderArray(const std::vector<::ndk::SpAIBinder>& in_input, std::vector<::ndk::SpAIBinder>* out_repeated, std::vector<::ndk::SpAIBinder>* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseNullableIBinderArray(const std::optional<std::vector<::ndk::SpAIBinder>>& in_input, std::optional<std::vector<::ndk::SpAIBinder>>* out_repeated, std::optional<std::vector<::ndk::SpAIBinder>>* _aidl_return) override;
  ::ndk::ScopedAStatus RepeatSimpleParcelable(const ::aidl::android::aidl::tests::SimpleParcelable& in_input, ::aidl::android::aidl::tests::SimpleParcelable* out_repeat, ::aidl::android::aidl::tests::SimpleParcelable* _aidl_return) override;
  ::ndk::ScopedAStatus ReverseSimpleParcelables(const std::vector<::aidl::android::aidl::tests::SimpleParcelable>& in_input, std::vector<::aidl::android::aidl::tests::SimpleParcelable>* out_repeated, std::vector<::aidl::android::aidl::tests::SimpleParcelable>* _aidl_return) override;
  ::ndk::ScopedAStatus GetOldNameInterface(std::shared_ptr<::aidl::android::aidl::tests::IOldName>* _aidl_return) override;
  ::ndk::ScopedAStatus GetNewNameInterface(std::shared_ptr<::aidl::android::aidl::tests::INewName>* _aidl_return) override;
  ::ndk::ScopedAStatus GetUnionTags(const std::vector<::aidl::android::aidl::tests::Union>& in_input, std::vector<::aidl::android::aidl::tests::Union::Tag>* _aidl_return) override;
  ::ndk::ScopedAStatus GetCppJavaTests(::ndk::SpAIBinder* _aidl_return) override;
  ::ndk::ScopedAStatus getBackendType(::aidl::android::aidl::tests::BackendType* _aidl_return) override;
  ::ndk::ScopedAStatus GetCircular(::aidl::android::aidl::tests::CircularParcelable* out_cp, std::shared_ptr<::aidl::android::aidl::tests::ICircular>* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
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
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag, 2> enum_values<aidl::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag> = {
  aidl::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag::n,
  aidl::android::aidl::tests::ITestService::CompilerChecks::UsingHasDeprecated::Tag::m,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
