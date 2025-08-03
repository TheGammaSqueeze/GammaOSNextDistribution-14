/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/INamedCallback.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INamedCallback.aidl
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
class INamedCallbackDelegator;

class INamedCallback : public ::ndk::ICInterface {
public:
  typedef INamedCallbackDelegator DefaultDelegator;
  static const char* descriptor;
  INamedCallback();
  virtual ~INamedCallback();

  static constexpr uint32_t TRANSACTION_GetName = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<INamedCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<INamedCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<INamedCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<INamedCallback>& impl);
  static const std::shared_ptr<INamedCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus GetName(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<INamedCallback> default_impl;
};
class INamedCallbackDefault : public INamedCallback {
public:
  ::ndk::ScopedAStatus GetName(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
