/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=cpp -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version current --log --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/staging/android/aidl/loggable/Enum.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-cpp-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/Enum.aidl
 */
#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace aidl {
namespace loggable {
enum class Enum : int8_t {
  FOO = 42,
};
}  // namespace loggable
}  // namespace aidl
}  // namespace android
namespace android {
namespace aidl {
namespace loggable {
[[nodiscard]] static inline std::string toString(Enum val) {
  switch(val) {
  case Enum::FOO:
    return "FOO";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace loggable
}  // namespace aidl
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::aidl::loggable::Enum, 1> enum_values<::android::aidl::loggable::Enum> = {
  ::android::aidl::loggable::Enum::FOO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
