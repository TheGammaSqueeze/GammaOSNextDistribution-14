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

#ifndef CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_LARGEPARCELABLE_H_
#define CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_LARGEPARCELABLE_H_

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

// This class allows a stable AIDL parcelable to be marshalled to a shared memory file if its
// serialized parcel exceeds binder limitation.
template <class T>
class LargeParcelable : public LargeParcelableBase {
public:
    LargeParcelable() {}

    // Use an existing stable AIDL parcelable to initiate this large parcelable. The input
    // parcelable's marshal/unmarshal method would be used to read/write from a shared memory
    // file if needed.
    //
    // T must be a Parcelable.
    explicit LargeParcelable(std::unique_ptr<T> parcelable) : mParcelable(std::move(parcelable)) {}

    // Get the stable AIDL parcelable object. Caller is supposed to use 'LargeParcelable' class to
    // parse data from 'AParcel' and then use 'getParcelable' to get the underlying parsed regular
    // parcelable object.
    inline const std::optional<const T*> getParcelable() const {
        if (!hasDeserializedParcelable() || mParcelable == nullptr) {
            return std::nullopt;
        }
        return mParcelable.get();
    }

protected:
    // Serialize this parcelable to 'dest'.
    binder_status_t serialize(AParcel* dest) const override;

    // Serialize a NULL parcelable for 'this' class to 'dest'.
    binder_status_t serializeNullPayload(AParcel* dest) const override;

    // Read a 'Parcelable' from the given AParcel. The src might be parsed to a NULL parcelable.
    binder_status_t deserialize(const AParcel& src) override;

private:
    std::unique_ptr<T> mParcelable;

    // Serialize a nullable Parcelabel 'payload' to 'dest'.
    static binder_status_t serializeNullablePayload(const T* payload, AParcel* dest);
};

template <class T>
binder_status_t LargeParcelable<T>::serializeNullablePayload(const T* payload, AParcel* dest) {
    int32_t startPosition;
    if (DBG_PAYLOAD) {
        startPosition = AParcel_getDataPosition(dest);
    }
    if (payload == nullptr) {
        // Write a null parcelable.
        if (binder_status_t status =
                    ::ndk::AParcel_writeNullableParcelable(dest, std::optional<T>(std::nullopt));
            status != STATUS_OK) {
            ALOGE("failed to write null parcelable to parcel, status: %d", status);
            return status;
        }
    } else {
        if (binder_status_t status = ::ndk::AParcel_writeParcelable(dest, *payload);
            status != STATUS_OK) {
            ALOGE("failed to write parcelable to parcel, status: %d", status);
            return status;
        }
    }

    if (DBG_PAYLOAD) {
        ALOGD("serialize-payload, start:%d size: %d", startPosition,
              (AParcel_getDataPosition(dest) - startPosition));
    }
    return STATUS_OK;
}

template <class T>
binder_status_t LargeParcelable<T>::serialize(AParcel* dest) const {
    return serializeNullablePayload(mParcelable.get(), dest);
}

template <class T>
binder_status_t LargeParcelable<T>::serializeNullPayload(AParcel* dest) const {
    return serializeNullablePayload(nullptr, dest);
}

template <class T>
binder_status_t LargeParcelable<T>::deserialize(const AParcel& src) {
    int32_t startPosition = AParcel_getDataPosition(&src);
    if (DBG_PAYLOAD) {
        ALOGD("start position: %d", startPosition);
    }
    std::optional<T> parcelable;
    if (binder_status_t status = ::ndk::AParcel_readNullableParcelable(&src, &parcelable);
        status != OK) {
        ALOGE("failed to read parcelable from parcel, status: %d", status);
        return status;
    }
    int32_t size = (AParcel_getDataPosition(&src) - startPosition);
    if (!parcelable.has_value()) {
        if (DBG_PAYLOAD) {
            ALOGD("deserialize-payload: null parcelable, start: %d, size: %d", startPosition, size);
        }
        mParcelable = nullptr;
        return STATUS_OK;
    }
    mParcelable = std::make_unique<T>(std::move(parcelable.value()));
    if (DBG_PAYLOAD) {
        ALOGD("deserialize-payload, start: %d, size: %d", startPosition, size);
    }
    return STATUS_OK;
}

}  // namespace car_binder_lib
}  // namespace automotive
}  // namespace android

#endif  // CPP_CAR_BINDER_LIB_LARGEPARCELABLE_INCLUDE_LARGEPARCELABLE_H_
