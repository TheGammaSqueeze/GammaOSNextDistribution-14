/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=ndk -Weverything -Wno-missing-permission-annotation -Werror --min_sdk_version current --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging/android/aidl/tests/INewName.cpp.d -h out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/include/staging -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-ndk-source/gen/staging -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/INewName.aidl
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
class INewNameDelegator;

class INewName : public ::ndk::ICInterface {
public:
  typedef INewNameDelegator DefaultDelegator;
  static const char* descriptor;
  INewName();
  virtual ~INewName();

  static constexpr uint32_t TRANSACTION_RealName = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<INewName> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<INewName>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<INewName>* instance);
  static bool setDefaultImpl(const std::shared_ptr<INewName>& impl);
  static const std::shared_ptr<INewName>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus RealName(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<INewName> default_impl;
};
class INewNameDefault : public INewName {
public:
  ::ndk::ScopedAStatus RealName(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
