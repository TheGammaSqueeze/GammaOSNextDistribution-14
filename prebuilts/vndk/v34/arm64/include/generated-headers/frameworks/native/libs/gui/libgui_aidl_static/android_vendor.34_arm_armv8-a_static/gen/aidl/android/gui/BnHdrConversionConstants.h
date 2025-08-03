#pragma once

#include <binder/IInterface.h>
#include <android/gui/IHdrConversionConstants.h>
#include <android/gui/BnHdrConversionConstants.h>
#include <binder/Delegate.h>


namespace android {
namespace gui {
class BnHdrConversionConstants : public ::android::BnInterface<IHdrConversionConstants> {
public:
  explicit BnHdrConversionConstants();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnHdrConversionConstants

class IHdrConversionConstantsDelegator : public BnHdrConversionConstants {
public:
  explicit IHdrConversionConstantsDelegator(const ::android::sp<IHdrConversionConstants> &impl) : _aidl_delegate(impl) {}

  ::android::sp<IHdrConversionConstants> getImpl() { return _aidl_delegate; }
private:
  ::android::sp<IHdrConversionConstants> _aidl_delegate;
};  // class IHdrConversionConstantsDelegator
}  // namespace gui
}  // namespace android
