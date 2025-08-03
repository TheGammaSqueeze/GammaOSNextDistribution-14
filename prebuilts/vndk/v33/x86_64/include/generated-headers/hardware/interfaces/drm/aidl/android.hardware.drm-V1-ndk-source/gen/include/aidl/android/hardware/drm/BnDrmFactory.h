#pragma once

#include "aidl/android/hardware/drm/IDrmFactory.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class BnDrmFactory : public ::ndk::BnCInterface<IDrmFactory> {
public:
  BnDrmFactory();
  virtual ~BnDrmFactory();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IDrmFactoryDelegator : public BnDrmFactory {
public:
  explicit IDrmFactoryDelegator(const std::shared_ptr<IDrmFactory> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IDrmFactory::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus createDrmPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::string& in_appPackageName, std::shared_ptr<::aidl::android::hardware::drm::IDrmPlugin>* _aidl_return) override {
    return _impl->createDrmPlugin(in_uuid, in_appPackageName, _aidl_return);
  }
  ::ndk::ScopedAStatus createCryptoPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::vector<uint8_t>& in_initData, std::shared_ptr<::aidl::android::hardware::drm::ICryptoPlugin>* _aidl_return) override {
    return _impl->createCryptoPlugin(in_uuid, in_initData, _aidl_return);
  }
  ::ndk::ScopedAStatus getSupportedCryptoSchemes(::aidl::android::hardware::drm::CryptoSchemes* _aidl_return) override {
    return _impl->getSupportedCryptoSchemes(_aidl_return);
  }
protected:
private:
  std::shared_ptr<IDrmFactory> _impl;
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
