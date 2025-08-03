/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ListOfInterfaces.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ListOfInterfaces.aidl
 */
#pragma once

#include <array>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
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
#include <aidl/android/aidl/tests/ListOfInterfaces.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class ListOfInterfaces {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class IEmptyInterfaceDelegator;

  class IEmptyInterface : public ::ndk::ICInterface {
  public:
    typedef IEmptyInterfaceDelegator DefaultDelegator;
    static const char* descriptor;
    IEmptyInterface();
    virtual ~IEmptyInterface();


    static std::shared_ptr<IEmptyInterface> fromBinder(const ::ndk::SpAIBinder& binder);
    static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IEmptyInterface>& instance);
    static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IEmptyInterface>* instance);
    static bool setDefaultImpl(const std::shared_ptr<IEmptyInterface>& impl);
    static const std::shared_ptr<IEmptyInterface>& getDefaultImpl();
  private:
    static std::shared_ptr<IEmptyInterface> default_impl;
  };
  class IEmptyInterfaceDefault : public IEmptyInterface {
  public:
    ::ndk::SpAIBinder asBinder() override;
    bool isRemote() override;
  };
  class BpEmptyInterface : public ::ndk::BpCInterface<IEmptyInterface> {
  public:
    explicit BpEmptyInterface(const ::ndk::SpAIBinder& binder);
    virtual ~BpEmptyInterface();

  };
  class BnEmptyInterface : public ::ndk::BnCInterface<IEmptyInterface> {
  public:
    BnEmptyInterface();
    virtual ~BnEmptyInterface();
  protected:
    ::ndk::SpAIBinder createBinder() override;
  private:
  };
  class IMyInterfaceDelegator;

  class IMyInterface : public ::ndk::ICInterface {
  public:
    typedef IMyInterfaceDelegator DefaultDelegator;
    static const char* descriptor;
    IMyInterface();
    virtual ~IMyInterface();

    static constexpr uint32_t TRANSACTION_methodWithInterfaces = FIRST_CALL_TRANSACTION + 0;

    static std::shared_ptr<IMyInterface> fromBinder(const ::ndk::SpAIBinder& binder);
    static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IMyInterface>& instance);
    static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IMyInterface>* instance);
    static bool setDefaultImpl(const std::shared_ptr<IMyInterface>& impl);
    static const std::shared_ptr<IMyInterface>& getDefaultImpl();
    virtual ::ndk::ScopedAStatus methodWithInterfaces(const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_iface, const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_nullable_iface, const std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>& in_iface_list_in, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* out_iface_list_out, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* in_iface_list_inout, const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>& in_nullable_iface_list_in, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* out_nullable_iface_list_out, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* in_nullable_iface_list_inout, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* _aidl_return) = 0;
  private:
    static std::shared_ptr<IMyInterface> default_impl;
  };
  class IMyInterfaceDefault : public IMyInterface {
  public:
    ::ndk::ScopedAStatus methodWithInterfaces(const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_iface, const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_nullable_iface, const std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>& in_iface_list_in, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* out_iface_list_out, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* in_iface_list_inout, const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>& in_nullable_iface_list_in, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* out_nullable_iface_list_out, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* in_nullable_iface_list_inout, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* _aidl_return) override;
    ::ndk::SpAIBinder asBinder() override;
    bool isRemote() override;
  };
  class BpMyInterface : public ::ndk::BpCInterface<IMyInterface> {
  public:
    explicit BpMyInterface(const ::ndk::SpAIBinder& binder);
    virtual ~BpMyInterface();

    ::ndk::ScopedAStatus methodWithInterfaces(const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_iface, const std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>& in_nullable_iface, const std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>& in_iface_list_in, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* out_iface_list_out, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>* in_iface_list_inout, const std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>& in_nullable_iface_list_in, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* out_nullable_iface_list_out, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* in_nullable_iface_list_inout, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>* _aidl_return) override;
  };
  class BnMyInterface : public ::ndk::BnCInterface<IMyInterface> {
  public:
    BnMyInterface();
    virtual ~BnMyInterface();
  protected:
    ::ndk::SpAIBinder createBinder() override;
  private:
  };
  class MyParcelable {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface> iface;
    std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface> nullable_iface;
    std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>> iface_list;
    std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>> nullable_iface_list;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator==(const MyParcelable& _rhs) const {
      return std::tie(iface, nullable_iface, iface_list, nullable_iface_list) == std::tie(_rhs.iface, _rhs.nullable_iface, _rhs.iface_list, _rhs.nullable_iface_list);
    }
    inline bool operator<(const MyParcelable& _rhs) const {
      return std::tie(iface, nullable_iface, iface_list, nullable_iface_list) < std::tie(_rhs.iface, _rhs.nullable_iface, _rhs.iface_list, _rhs.nullable_iface_list);
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
      _aidl_os << "iface: " << ::android::internal::ToString(iface);
      _aidl_os << ", nullable_iface: " << ::android::internal::ToString(nullable_iface);
      _aidl_os << ", iface_list: " << ::android::internal::ToString(iface_list);
      _aidl_os << ", nullable_iface_list: " << ::android::internal::ToString(nullable_iface_list);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };
  class MyUnion {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    enum class Tag : int32_t {
      iface = 0,
      nullable_iface = 1,
      iface_list = 2,
      nullable_iface_list = 3,
    };

    // Expose tag symbols for legacy code
    static const inline Tag iface = Tag::iface;
    static const inline Tag nullable_iface = Tag::nullable_iface;
    static const inline Tag iface_list = Tag::iface_list;
    static const inline Tag nullable_iface_list = Tag::nullable_iface_list;

    template<typename _Tp>
    static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, MyUnion>;

    MyUnion() : _value(std::in_place_index<static_cast<size_t>(iface)>, std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>()) { }

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
      case iface: os << "iface: " << ::android::internal::ToString(get<iface>()); break;
      case nullable_iface: os << "nullable_iface: " << ::android::internal::ToString(get<nullable_iface>()); break;
      case iface_list: os << "iface_list: " << ::android::internal::ToString(get<iface_list>()); break;
      case nullable_iface_list: os << "nullable_iface_list: " << ::android::internal::ToString(get<nullable_iface_list>()); break;
      }
      os << "}";
      return os.str();
    }
  private:
    std::variant<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>, std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>, std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>, std::optional<std::vector<std::shared_ptr<::aidl::android::aidl::tests::ListOfInterfaces::IEmptyInterface>>>> _value;
  };

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator==(const ListOfInterfaces&) const {
    return std::tie() == std::tie();
  }
  inline bool operator<(const ListOfInterfaces&) const {
    return std::tie() < std::tie();
  }
  inline bool operator!=(const ListOfInterfaces& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const ListOfInterfaces& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const ListOfInterfaces& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const ListOfInterfaces& _rhs) const {
    return !(_rhs < *this);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "ListOfInterfaces{";
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(ListOfInterfaces::MyUnion::Tag val) {
  switch(val) {
  case ListOfInterfaces::MyUnion::Tag::iface:
    return "iface";
  case ListOfInterfaces::MyUnion::Tag::nullable_iface:
    return "nullable_iface";
  case ListOfInterfaces::MyUnion::Tag::iface_list:
    return "iface_list";
  case ListOfInterfaces::MyUnion::Tag::nullable_iface_list:
    return "nullable_iface_list";
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
constexpr inline std::array<aidl::android::aidl::tests::ListOfInterfaces::MyUnion::Tag, 4> enum_values<aidl::android::aidl::tests::ListOfInterfaces::MyUnion::Tag> = {
  aidl::android::aidl::tests::ListOfInterfaces::MyUnion::Tag::iface,
  aidl::android::aidl::tests::ListOfInterfaces::MyUnion::Tag::nullable_iface,
  aidl::android::aidl::tests::ListOfInterfaces::MyUnion::Tag::iface_list,
  aidl::android::aidl::tests::ListOfInterfaces::MyUnion::Tag::nullable_iface_list,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
