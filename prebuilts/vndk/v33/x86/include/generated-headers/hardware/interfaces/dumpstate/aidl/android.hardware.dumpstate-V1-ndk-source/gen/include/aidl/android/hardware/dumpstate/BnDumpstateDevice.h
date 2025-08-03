#pragma once

#include "aidl/android/hardware/dumpstate/IDumpstateDevice.h"

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
namespace dumpstate {
class BnDumpstateDevice : public ::ndk::BnCInterface<IDumpstateDevice> {
public:
  BnDumpstateDevice();
  virtual ~BnDumpstateDevice();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IDumpstateDeviceDelegator : public BnDumpstateDevice {
public:
  explicit IDumpstateDeviceDelegator(const std::shared_ptr<IDumpstateDevice> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IDumpstateDevice::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus dumpstateBoard(const std::vector<::ndk::ScopedFileDescriptor>& in_fd, ::aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode in_mode, int64_t in_timeoutMillis) override {
    return _impl->dumpstateBoard(in_fd, in_mode, in_timeoutMillis);
  }
  ::ndk::ScopedAStatus getVerboseLoggingEnabled(bool* _aidl_return) override {
    return _impl->getVerboseLoggingEnabled(_aidl_return);
  }
  ::ndk::ScopedAStatus setVerboseLoggingEnabled(bool in_enable) override {
    return _impl->setVerboseLoggingEnabled(in_enable);
  }
protected:
private:
  std::shared_ptr<IDumpstateDevice> _impl;
};

}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
}  // namespace aidl
