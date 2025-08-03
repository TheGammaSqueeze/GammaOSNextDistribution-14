#ifndef AIDL_GENERATED_ANDROID_BN_O_M_X_BUFFER_SOURCE_H_
#define AIDL_GENERATED_ANDROID_BN_O_M_X_BUFFER_SOURCE_H_

#include <binder/IInterface.h>
#include <android/IOMXBufferSource.h>

namespace android {

class BnOMXBufferSource : public ::android::BnInterface<IOMXBufferSource> {
public:
  explicit BnOMXBufferSource();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnOMXBufferSource

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_BN_O_M_X_BUFFER_SOURCE_H_
