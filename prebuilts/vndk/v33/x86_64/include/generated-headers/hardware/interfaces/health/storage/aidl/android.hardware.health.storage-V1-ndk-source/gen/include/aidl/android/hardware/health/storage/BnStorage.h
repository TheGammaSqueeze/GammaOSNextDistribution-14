#pragma once

#include "aidl/android/hardware/health/storage/IStorage.h"

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
namespace health {
namespace storage {
class BnStorage : public ::ndk::BnCInterface<IStorage> {
public:
  BnStorage();
  virtual ~BnStorage();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IStorageDelegator : public BnStorage {
public:
  explicit IStorageDelegator(const std::shared_ptr<IStorage> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IStorage::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus garbageCollect(int64_t in_timeoutSeconds, const std::shared_ptr<::aidl::android::hardware::health::storage::IGarbageCollectCallback>& in_callback) override {
    return _impl->garbageCollect(in_timeoutSeconds, in_callback);
  }
protected:
private:
  std::shared_ptr<IStorage> _impl;
};

}  // namespace storage
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
