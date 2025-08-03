/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/IDeprecated.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/IDeprecated.aidl
 */
#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class IDeprecatedDelegator;

class __attribute__((deprecated("test"))) IDeprecated : public ::ndk::ICInterface {
public:
  typedef IDeprecatedDelegator DefaultDelegator;
  static const char* descriptor;
  IDeprecated();
  virtual ~IDeprecated();


  static std::shared_ptr<IDeprecated> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IDeprecated>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IDeprecated>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IDeprecated>& impl);
  static const std::shared_ptr<IDeprecated>& getDefaultImpl();
private:
  static std::shared_ptr<IDeprecated> default_impl;
};
class __attribute__((deprecated("test"))) IDeprecatedDefault : public IDeprecated {
public:
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
