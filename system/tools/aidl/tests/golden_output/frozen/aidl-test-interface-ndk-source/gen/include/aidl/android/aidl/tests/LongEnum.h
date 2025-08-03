/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/LongEnum.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/LongEnum.aidl
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
enum class LongEnum : int64_t {
  FOO = 100000000000L,
  BAR = 200000000000L,
  BAZ = 200000000001L,
};

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
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
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::tests::LongEnum, 3> enum_values<aidl::android::aidl::tests::LongEnum> = {
  aidl::android::aidl::tests::LongEnum::FOO,
  aidl::android::aidl::tests::LongEnum::BAR,
  aidl::android::aidl::tests::LongEnum::BAZ,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
