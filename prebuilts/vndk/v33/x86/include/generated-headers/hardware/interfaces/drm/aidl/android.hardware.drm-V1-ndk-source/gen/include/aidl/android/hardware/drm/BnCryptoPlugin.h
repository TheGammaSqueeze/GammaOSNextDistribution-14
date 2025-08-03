#pragma once

#include "aidl/android/hardware/drm/ICryptoPlugin.h"

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
class BnCryptoPlugin : public ::ndk::BnCInterface<ICryptoPlugin> {
public:
  BnCryptoPlugin();
  virtual ~BnCryptoPlugin();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ICryptoPluginDelegator : public BnCryptoPlugin {
public:
  explicit ICryptoPluginDelegator(const std::shared_ptr<ICryptoPlugin> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ICryptoPlugin::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus decrypt(const ::aidl::android::hardware::drm::DecryptArgs& in_args, int32_t* _aidl_return) override {
    return _impl->decrypt(in_args, _aidl_return);
  }
  ::ndk::ScopedAStatus getLogMessages(std::vector<::aidl::android::hardware::drm::LogMessage>* _aidl_return) override {
    return _impl->getLogMessages(_aidl_return);
  }
  ::ndk::ScopedAStatus notifyResolution(int32_t in_width, int32_t in_height) override {
    return _impl->notifyResolution(in_width, in_height);
  }
  ::ndk::ScopedAStatus requiresSecureDecoderComponent(const std::string& in_mime, bool* _aidl_return) override {
    return _impl->requiresSecureDecoderComponent(in_mime, _aidl_return);
  }
  ::ndk::ScopedAStatus setMediaDrmSession(const std::vector<uint8_t>& in_sessionId) override {
    return _impl->setMediaDrmSession(in_sessionId);
  }
  ::ndk::ScopedAStatus setSharedBufferBase(const ::aidl::android::hardware::drm::SharedBuffer& in_base) override {
    return _impl->setSharedBufferBase(in_base);
  }
protected:
private:
  std::shared_ptr<ICryptoPlugin> _impl;
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
