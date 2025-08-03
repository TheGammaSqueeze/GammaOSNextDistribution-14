/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/nested/DeeplyNested.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/DeeplyNested.aidl
 */
#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/tests/nested/DeeplyNested.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
namespace nested {
class DeeplyNested {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class B {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    class C {
    public:
      typedef std::false_type fixed_size;
      static const char* descriptor;

      class D {
      public:
        typedef std::false_type fixed_size;
        static const char* descriptor;

        enum class E : int8_t {
          OK = 0,
        };


        binder_status_t readFromParcel(const AParcel* parcel);
        binder_status_t writeToParcel(AParcel* parcel) const;

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

        static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
        inline std::string toString() const {
          std::ostringstream _aidl_os;
          _aidl_os << "D{";
          _aidl_os << "}";
          return _aidl_os.str();
        }
      };

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

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

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
      inline std::string toString() const {
        std::ostringstream _aidl_os;
        _aidl_os << "C{";
        _aidl_os << "}";
        return _aidl_os.str();
      }
    };

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

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

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "B{";
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };
  class A {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    ::aidl::android::aidl::tests::nested::DeeplyNested::B::C::D::E e = ::aidl::android::aidl::tests::nested::DeeplyNested::B::C::D::E::OK;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

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

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
    inline std::string toString() const {
      std::ostringstream _aidl_os;
      _aidl_os << "A{";
      _aidl_os << "e: " << ::android::internal::ToString(e);
      _aidl_os << "}";
      return _aidl_os.str();
    }
  };

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

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

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream _aidl_os;
    _aidl_os << "DeeplyNested{";
    _aidl_os << "}";
    return _aidl_os.str();
  }
};
}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
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
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::tests::nested::DeeplyNested::B::C::D::E, 1> enum_values<aidl::android::aidl::tests::nested::DeeplyNested::B::C::D::E> = {
  aidl::android::aidl::tests::nested::DeeplyNested::B::C::D::E::OK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
