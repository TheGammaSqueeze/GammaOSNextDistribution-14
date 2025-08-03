/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_LARGEPARCELABLEVECTOR_H_
#define CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_LARGEPARCELABLEVECTOR_H_

#include "LargeParcelableBase.h"
#include "SharedMemory.h"

#include <android/binder_parcel.h>
#include <android/binder_parcel_utils.h>
#include <android/binder_status.h>

#include <memory>
#include <optional>

namespace android {
namespace automotive {
namespace car_binder_lib {

// This class allows a list of stable AIDL parcelables to be marshalled to a shared memory file if
// their serialized parcel exceeds binder limitation.
template <class T>
class LargeParcelableVector : public LargeParcelableBase {
public:
    LargeParcelableVector() {}

    // Use a list of stable AIDL parcelables to initiate this large parcelable.
    //
    // T must be a Parcelable.
    explicit LargeParcelableVector(std::vector<T> parcelable) :
          mParcelable(std::move(parcelable)) {}

    // Get the list of stable AIDL parcelable object. Caller is supposed to use
    // 'LargeParcelableVector' class to parse data from 'Parcel' and then use 'getParcelables' to
    // get the underlying parsed regular parcelable objects.
    inline const std::optional<const std::vector<T>*> getParcelables() const {
        if (!hasDeserializedParcelable()) {
            return std::nullopt;
        }
        return &mParcelable;
    }

protected:
    // Serialize this parcelable to 'dest'.
    binder_status_t serialize(AParcel* dest) const override;

    // Serialize a NULL parcelable for 'this' class to 'dest'.
    binder_status_t serializeNullPayload(AParcel* dest) const override;

    // Read a 'Parcelable' from the given Parcel. The src might be parsed to a NULL parcelable.
    binder_status_t deserialize(const AParcel& src) override;

private:
    std::vector<T> mParcelable;

    // Serialize a nullable Parcelabel 'payload' to 'dest'.
    static binder_status_t serializePayload(const std::vector<T>* payload, AParcel* dest);
};

template <class T>
binder_status_t LargeParcelableVector<T>::serializePayload(const std::vector<T>* payload,
                                                           AParcel* dest) {
    int32_t startPosition;
    if (DBG_PAYLOAD) {
        startPosition = AParcel_getDataPosition(dest);
    }
    if (binder_status_t status = ::ndk::AParcel_writeVector(dest, *payload); status != STATUS_OK) {
        ALOGE("failed to write parcelable vector to parcel, status: %d", status);
        return status;
    }
    if (DBG_PAYLOAD) {
        ALOGD("serialize-payload, start:%d size: %d", startPosition,
              (AParcel_getDataPosition(dest) - startPosition));
    }
    return STATUS_OK;
}

template <class T>
binder_status_t LargeParcelableVector<T>::serialize(AParcel* dest) const {
    return serializePayload(&mParcelable, dest);
}

template <class T>
binder_status_t LargeParcelableVector<T>::serializeNullPayload(AParcel* dest) const {
    std::vector<T> empty;
    return serializePayload(&empty, dest);
}

template <class T>
binder_status_t LargeParcelableVector<T>::deserialize(const AParcel& src) {
    int32_t startPosition;
    if (DBG_PAYLOAD) {
        startPosition = AParcel_getDataPosition(&src);
        ALOGD("start position: %d", startPosition);
    }
    if (binder_status_t status = ::ndk::AParcel_readVector(&src, &mParcelable);
        status != STATUS_OK) {
        ALOGE("failed to read parcelable vector from parcel, status: %d", status);
        return status;
    }
    if (DBG_PAYLOAD) {
        ALOGD("deserialize-payload, start:%d size: %d", startPosition,
              (AParcel_getDataPosition(&src) - startPosition));
    }
    return STATUS_OK;
}

}  // namespace car_binder_lib
}  // namespace automotive
}  // namespace android

#endif  // CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_LARGEPARCELABLEVECTOR_H_
