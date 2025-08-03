/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging/android/aidl/tests/LongEnum.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/LongEnum.aidl
 */
#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace aidl {
namespace tests {
enum class LongEnum : int64_t {
  FOO = 100000000000L,
  BAR = 200000000000L,
  BAZ = 200000000001L,
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(LongEnum val) {
  switch(val) {
  case LongEnum::FOO:
    return "FOO";
  case LongEnum::BAR:
    return "BAR";
  case LongEnum::BAZ:
    return "BAZ";
  default:
    return std::to_string(static_cast<int64_t>(val));
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
constexpr inline std::array<::android::aidl::tests::LongEnum, 3> enum_values<::android::aidl::tests::LongEnum> = {
  ::android::aidl::tests::LongEnum::FOO,
  ::android::aidl::tests::LongEnum::BAR,
  ::android::aidl::tests::LongEnum::BAZ,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
