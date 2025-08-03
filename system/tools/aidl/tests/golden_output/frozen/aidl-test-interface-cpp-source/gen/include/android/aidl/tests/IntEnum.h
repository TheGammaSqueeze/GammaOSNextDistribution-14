/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/IntEnum.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IntEnum.aidl
 */
#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace aidl {
namespace tests {
enum class IntEnum : int32_t {
  FOO = 1000,
  BAR = 2000,
  BAZ = 2001,
  QUX __attribute__((deprecated("do not use this"))) = 2002,
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace tests {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
[[nodiscard]] static inline std::string toString(IntEnum val) {
  switch(val) {
  case IntEnum::FOO:
    return "FOO";
  case IntEnum::BAR:
    return "BAR";
  case IntEnum::BAZ:
    return "BAZ";
  case IntEnum::QUX:
    return "QUX";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
#pragma clang diagnostic pop
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
template <>
constexpr inline std::array<::android::aidl::tests::IntEnum, 4> enum_values<::android::aidl::tests::IntEnum> = {
  ::android::aidl::tests::IntEnum::FOO,
  ::android::aidl::tests::IntEnum::BAR,
  ::android::aidl::tests::IntEnum::BAZ,
  ::android::aidl::tests::IntEnum::QUX,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
