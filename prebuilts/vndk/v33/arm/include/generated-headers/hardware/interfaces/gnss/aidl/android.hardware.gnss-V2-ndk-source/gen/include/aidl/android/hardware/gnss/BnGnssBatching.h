#pragma once

#include "aidl/android/hardware/gnss/IGnssBatching.h"

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
namespace gnss {
class BnGnssBatching : public ::ndk::BnCInterface<IGnssBatching> {
public:
  BnGnssBatching();
  virtual ~BnGnssBatching();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssBatchingDelegator : public BnGnssBatching {
public:
  explicit IGnssBatchingDelegator(const std::shared_ptr<IGnssBatching> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssBatching::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus init(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssBatchingCallback>& in_callback) override {
    return _impl->init(in_callback);
  }
  ::ndk::ScopedAStatus getBatchSize(int32_t* _aidl_return) override {
    return _impl->getBatchSize(_aidl_return);
  }
  ::ndk::ScopedAStatus start(const ::aidl::android::hardware::gnss::IGnssBatching::Options& in_options) override {
    return _impl->start(in_options);
  }
  ::ndk::ScopedAStatus flush() override {
    return _impl->flush();
  }
  ::ndk::ScopedAStatus stop() override {
    return _impl->stop();
  }
  ::ndk::ScopedAStatus cleanup() override {
    return _impl->cleanup();
  }
protected:
private:
  std::shared_ptr<IGnssBatching> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
