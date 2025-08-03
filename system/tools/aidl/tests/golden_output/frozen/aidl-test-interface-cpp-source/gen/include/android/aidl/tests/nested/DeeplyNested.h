/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/nested/DeeplyNested.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/DeeplyNested.aidl
 */
#pragma once

#include <android/aidl/tests/nested/DeeplyNested.h>
#include <android/binder_to_string.h>
#include <array>
#include <binder/Enums.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <tuple>
#include <utils/String16.h>

namespace android {
namespace aidl {
namespace tests {
namespace nested {
class DeeplyNested : public ::android::Parcelable {
public:
  class B : public ::android::Parcelable {
  public:
    class C : public ::android::Parcelable {
    public:
      class D : public ::android::Parcelable {
      public:
        enum class E : int8_t {
          OK = 0,
        };
        inline bool operator==(const D&) const {
          return std::tie() == std::tie();
        }
        inline bool operator<(const D&) const {
          return std::tie() < std::tie();
        }
        inline bool operator!=(const D& _rhs) const {
          return !(*this == _rhs);
        }
        inline bool operator>(const D& _rhs) const {
          return _rhs < *this;
        }
        inline bool operator>=(const D& _rhs) const {
          return !(*this < _rhs);
        }
        inline bool operator<=(const D& _rhs) const {
          return !(_rhs < *this);
        }

        ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
        ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
        static const ::android::String16& getParcelableDescriptor() {
          static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.nested.DeeplyNested.B.C.D");
          return DESCRIPTOR;
        }
        inline std::string toString() const {
          std::ostringstream _aidl_os;
          _aidl_os << "D{";
          _aidl_os << "}";
          return _aidl_os.str();
        }
      };  // class D
      inline bool operator==(const C&) const {
        return std::tie() == std::tie();
      }
      inline bool operator<(const C&) const {
        return std::tie() < std::tie();
      }
      inline bool operator!=(const C& _rhs) const {
        return !(*this == _rhs);
      }
      inline bool operator>(const C& _rhs) const {
        return _rhs < *this;
      }
      inline bool operator>=(const C& _rhs) const {
        return !(*this < _rhs);
      }
      inline bool operator<=(const C& _rhs) const {
        return !(_rhs < *this);
      }

      ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
      ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
      static const ::android::String16& getParcelableDescriptor() {
        static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.nested.DeeplyNested.B.C");
        return DESCRIPTOR;
      }
      inline std::string toString() const {
        std::ostringstream _aidl_os;
        _aidl_os << "C{";
        _aidl_os << "}";
        return _aidl_os.str();
      }
    };  // class C
    inline bool operator==(const B&) const {
      return std::tie() == std::tie();
    }
    inline bool operator<(const B&) const {
      return std::tie() < std::tie();
    }
    inline bool operator!=(const B& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const B& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const B& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const B& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.nested.DeeplyNested.B");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "B{";
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class B
  class A : public ::android::Parcelable {
  public:
    ::android::aidl::tests::nested::DeeplyNested::B::C::D::E e = ::android::aidl::tests::nested::DeeplyNested::B::C::D::E::OK;
    inline bool operator==(const A& _rhs) const {
      return std::tie(e) == std::tie(_rhs.e);
    }
    inline bool operator<(const A& _rhs) const {
      return std::tie(e) < std::tie(_rhs.e);
    }
    inline bool operator!=(const A& _rhs) const {
      return !(*this == _rhs);
    }
    inline bool operator>(const A& _rhs) const {
      return _rhs < *this;
    }
    inline bool operator>=(const A& _rhs) const {
      return !(*this < _rhs);
    }
    inline bool operator<=(const A& _rhs) const {
      return !(_rhs < *this);
    }

    ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
    ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
    static const ::android::String16& getParcelableDescriptor() {
      static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.nested.DeeplyNested.A");
      return DESCRIPTOR;
    }
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "A{";
      _aidl_os << "e: " << ::android::internal::ToString(e);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };  // class A
  inline bool operator==(const DeeplyNested&) const {
    return std::tie() == std::tie();
  }
  inline bool operator<(const DeeplyNested&) const {
    return std::tie() < std::tie();
  }
  inline bool operator!=(const DeeplyNested& _rhs) const {
    return !(*this == _rhs);
  }
  inline bool operator>(const DeeplyNested& _rhs) const {
    return _rhs < *this;
  }
  inline bool operator>=(const DeeplyNested& _rhs) const {
    return !(*this < _rhs);
  }
  inline bool operator<=(const DeeplyNested& _rhs) const {
    return !(_rhs < *this);
  }

  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCRIPTOR (u"android.aidl.tests.nested.DeeplyNested");
    return DESCRIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "DeeplyNested{";
    _aidl_os << "}";
    return _aidl_os.str();
  }
};  // class DeeplyNested
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace tests {
namespace nested {
[[nodiscard]] static inline std::string toString(DeeplyNested::B::C::D::E val) {
  switch(val) {
  case DeeplyNested::B::C::D::E::OK:
    return "OK";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::tests::nested::DeeplyNested::B::C::D::E, 1> enum_values<::android::aidl::tests::nested::DeeplyNested::B::C::D::E> = {
  ::android::aidl::tests::nested::DeeplyNested::B::C::D::E::OK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
