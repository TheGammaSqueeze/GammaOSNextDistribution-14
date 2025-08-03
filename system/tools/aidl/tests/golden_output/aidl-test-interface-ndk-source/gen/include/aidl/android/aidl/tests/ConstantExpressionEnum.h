/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ConstantExpressionEnum.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ConstantExpressionEnum.aidl
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
enum class ConstantExpressionEnum : int32_t {
  decInt32_1 = 1,
  decInt32_2 = 1,
  decInt64_1 = 1,
  decInt64_2 = 1,
  decInt64_3 = 1,
  decInt64_4 = 1,
  hexInt32_1 = 1,
  hexInt32_2 = 1,
  hexInt32_3 = 1,
  hexInt64_1 = 1,
};

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(ConstantExpressionEnum val) {
  switch(val) {
  case ConstantExpressionEnum::decInt32_1:
    return "decInt32_1";
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
constexpr inline std::array<aidl::android::aidl::tests::ConstantExpressionEnum, 10> enum_values<aidl::android::aidl::tests::ConstantExpressionEnum> = {
  aidl::android::aidl::tests::ConstantExpressionEnum::decInt32_1,
  aidl::android::aidl::tests::ConstantExpressionEnum::decInt32_2,
  aidl::android::aidl::tests::ConstantExpressionEnum::decInt64_1,
  aidl::android::aidl::tests::ConstantExpressionEnum::decInt64_2,
  aidl::android::aidl::tests::ConstantExpressionEnum::decInt64_3,
  aidl::android::aidl::tests::ConstantExpressionEnum::decInt64_4,
  aidl::android::aidl::tests::ConstantExpressionEnum::hexInt32_1,
  aidl::android::aidl::tests::ConstantExpressionEnum::hexInt32_2,
  aidl::android::aidl::tests::ConstantExpressionEnum::hexInt32_3,
  aidl::android::aidl::tests::ConstantExpressionEnum::hexInt64_1,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
