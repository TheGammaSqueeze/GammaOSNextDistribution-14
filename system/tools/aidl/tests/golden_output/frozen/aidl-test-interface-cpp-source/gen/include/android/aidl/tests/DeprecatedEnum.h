/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/DeprecatedEnum.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/DeprecatedEnum.aidl
 */
#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace aidl {
namespace tests {
enum class __attribute__((deprecated("test"))) DeprecatedEnum : int32_t {
  A = 0,
  B = 1,
  C = 2,
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace tests {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
[[nodiscard]] static inline std::string toString(DeprecatedEnum val) {
  switch(val) {
  case DeprecatedEnum::A:
    return "A";
  case DeprecatedEnum::B:
    return "B";
  case DeprecatedEnum::C:
    return "C";
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
constexpr inline std::array<::android::aidl::tests::DeprecatedEnum, 3> enum_values<::android::aidl::tests::DeprecatedEnum> = {
  ::android::aidl::tests::DeprecatedEnum::A,
  ::android::aidl::tests::DeprecatedEnum::B,
  ::android::aidl::tests::DeprecatedEnum::C,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
