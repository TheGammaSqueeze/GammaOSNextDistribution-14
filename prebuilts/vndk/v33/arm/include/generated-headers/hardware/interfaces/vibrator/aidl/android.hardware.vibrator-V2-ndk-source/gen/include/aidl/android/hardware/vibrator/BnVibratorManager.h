#pragma once

#include "aidl/android/hardware/vibrator/IVibratorManager.h"

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
namespace vibrator {
class BnVibratorManager : public ::ndk::BnCInterface<IVibratorManager> {
public:
  BnVibratorManager();
  virtual ~BnVibratorManager();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IVibratorManagerDelegator : public BnVibratorManager {
public:
  explicit IVibratorManagerDelegator(const std::shared_ptr<IVibratorManager> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IVibratorManager::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getCapabilities(int32_t* _aidl_return) override {
    return _impl->getCapabilities(_aidl_return);
  }
  ::ndk::ScopedAStatus getVibratorIds(std::vector<int32_t>* _aidl_return) override {
    return _impl->getVibratorIds(_aidl_return);
  }
  ::ndk::ScopedAStatus getVibrator(int32_t in_vibratorId, std::shared_ptr<::aidl::android::hardware::vibrator::IVibrator>* _aidl_return) override {
    return _impl->getVibrator(in_vibratorId, _aidl_return);
  }
  ::ndk::ScopedAStatus prepareSynced(const std::vector<int32_t>& in_vibratorIds) override {
    return _impl->prepareSynced(in_vibratorIds);
  }
  ::ndk::ScopedAStatus triggerSynced(const std::shared_ptr<::aidl::android::hardware::vibrator::IVibratorCallback>& in_callback) override {
    return _impl->triggerSynced(in_callback);
  }
  ::ndk::ScopedAStatus cancelSynced() override {
    return _impl->cancelSynced();
  }
protected:
private:
  std::shared_ptr<IVibratorManager> _impl;
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
