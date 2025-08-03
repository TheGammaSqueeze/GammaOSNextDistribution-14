/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/BackendType.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/BackendType.aidl
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
enum class BackendType : int8_t {
  CPP = 0,
  JAVA = 1,
  NDK = 2,
  RUST = 3,
};

}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(BackendType val) {
  switch(val) {
  case BackendType::CPP:
    return "CPP";
  case BackendType::JAVA:
    return "JAVA";
  case BackendType::NDK:
    return "NDK";
  case BackendType::RUST:
    return "RUST";
  default:
    return std::to_string(static_cast<int8_t>(val));
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
constexpr inline std::array<aidl::android::aidl::tests::BackendType, 4> enum_values<aidl::android::aidl::tests::BackendType> = {
  aidl::android::aidl::tests::BackendType::CPP,
  aidl::android::aidl::tests::BackendType::JAVA,
  aidl::android::aidl::tests::BackendType::NDK,
  aidl::android::aidl::tests::BackendType::RUST,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
