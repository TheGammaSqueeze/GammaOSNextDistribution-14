#pragma once

#include "aidl/android/hardware/drm/IDrmPluginListener.h"

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
class BnDrmPluginListener : public ::ndk::BnCInterface<IDrmPluginListener> {
public:
  BnDrmPluginListener();
  virtual ~BnDrmPluginListener();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IDrmPluginListenerDelegator : public BnDrmPluginListener {
public:
  explicit IDrmPluginListenerDelegator(const std::shared_ptr<IDrmPluginListener> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IDrmPluginListener::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onEvent(::aidl::android::hardware::drm::EventType in_eventType, const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_data) override {
    return _impl->onEvent(in_eventType, in_sessionId, in_data);
  }
  ::ndk::ScopedAStatus onExpirationUpdate(const std::vector<uint8_t>& in_sessionId, int64_t in_expiryTimeInMS) override {
    return _impl->onExpirationUpdate(in_sessionId, in_expiryTimeInMS);
  }
  ::ndk::ScopedAStatus onKeysChange(const std::vector<uint8_t>& in_sessionId, const std::vector<::aidl::android::hardware::drm::KeyStatus>& in_keyStatusList, bool in_hasNewUsableKey) override {
    return _impl->onKeysChange(in_sessionId, in_keyStatusList, in_hasNewUsableKey);
  }
  ::ndk::ScopedAStatus onSessionLostState(const std::vector<uint8_t>& in_sessionId) override {
    return _impl->onSessionLostState(in_sessionId);
  }
protected:
private:
  std::shared_ptr<IDrmPluginListener> _impl;
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
