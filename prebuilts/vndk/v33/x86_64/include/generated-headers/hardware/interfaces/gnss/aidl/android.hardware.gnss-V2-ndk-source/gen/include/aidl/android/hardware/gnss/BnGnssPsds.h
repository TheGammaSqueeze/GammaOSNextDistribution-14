#pragma once

#include "aidl/android/hardware/gnss/IGnssPsds.h"

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
class BnGnssPsds : public ::ndk::BnCInterface<IGnssPsds> {
public:
  BnGnssPsds();
  virtual ~BnGnssPsds();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IGnssPsdsDelegator : public BnGnssPsds {
public:
  explicit IGnssPsdsDelegator(const std::shared_ptr<IGnssPsds> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IGnssPsds::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus injectPsdsData(::aidl::android::hardware::gnss::PsdsType in_psdsType, const std::vector<uint8_t>& in_psdsData) override {
    return _impl->injectPsdsData(in_psdsType, in_psdsData);
  }
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsdsCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
protected:
private:
  std::shared_ptr<IGnssPsds> _impl;
};

}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
