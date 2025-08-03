/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/DeprecatedEnum.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/DeprecatedEnum.aidl
 */
#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
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
}  // namespace aidl
namespace aidl {
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
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
template <>
constexpr inline std::array<aidl::android::aidl::tests::DeprecatedEnum, 3> enum_values<aidl::android::aidl::tests::DeprecatedEnum> = {
  aidl::android::aidl::tests::DeprecatedEnum::A,
  aidl::android::aidl::tests::DeprecatedEnum::B,
  aidl::android::aidl::tests::DeprecatedEnum::C,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
