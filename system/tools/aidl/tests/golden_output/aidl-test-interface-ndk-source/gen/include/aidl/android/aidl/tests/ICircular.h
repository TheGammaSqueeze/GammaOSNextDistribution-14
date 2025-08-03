/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/ICircular.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ICircular.aidl
 */
#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/aidl/tests/ITestService.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::aidl::tests {
class ITestService;
}  // namespace aidl::android::aidl::tests
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class ICircularDelegator;

class ICircular : public ::ndk::ICInterface {
public:
  typedef ICircularDelegator DefaultDelegator;
  static const char* descriptor;
  ICircular();
  virtual ~ICircular();

  static constexpr uint32_t TRANSACTION_GetTestService = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<ICircular> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICircular>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICircular>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICircular>& impl);
  static const std::shared_ptr<ICircular>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus GetTestService(std::shared_ptr<::aidl::android::aidl::tests::ITestService>* _aidl_return) = 0;
private:
  static std::shared_ptr<ICircular> default_impl;
};
class ICircularDefault : public ICircular {
public:
  ::ndk::ScopedAStatus GetTestService(std::shared_ptr<::aidl::android::aidl::tests::ITestService>* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
