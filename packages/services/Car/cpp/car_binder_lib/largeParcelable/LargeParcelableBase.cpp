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

#define LOG_TAG "LargeParcelable"

#include "LargeParcelableBase.h"

#include "MappedFile.h"
#include "SharedMemory.h"

#include <android-base/unique_fd.h>
#include <android/binder_auto_utils.h>
#include <android/binder_parcel.h>
#include <android/binder_parcel_utils.h>
#include <android/binder_status.h>
#include <utils/Errors.h>
#include <utils/Log.h>

#include <stdint.h>

#include <cstring>
#include <iostream>
#include <memory>
#include <optional>
#include <sstream>
#include <string>

namespace android {
namespace automotive {
namespace car_binder_lib {

using ::android::base::borrowed_fd;
using ::android::base::unique_fd;
using ::ndk::ScopedAParcel;
using ::ndk::ScopedFileDescriptor;

borrowed_fd LargeParcelableBase::scopedFdToBorrowedFd(const ScopedFileDescriptor& fd) {
    borrowed_fd memoryFd(fd.get());
    return memoryFd;
}

unique_fd LargeParcelableBase::scopeFdToUniqueFd(ScopedFileDescriptor&& fd) {
    // ScopedFileDescriptor does not have release function, so we have to directly modify its
    // underlying fd pointer to remove ownership.
    unique_fd memoryFd(fd.get());
    *(fd.getR()) = INVALID_MEMORY_FD;
    return memoryFd;
}

binder_status_t LargeParcelableBase::readFromParcel(const AParcel* in) {
    mHasDeserializedParcelable = false;

    // Make this compatible with stable AIDL
    // payloadSize + Nullable Parcelable + Nullable ParcelFileDescriptor
    int32_t startPosition = AParcel_getDataPosition(in);
    int32_t totalPayloadSize;
    if (binder_status_t status = AParcel_readInt32(in, &totalPayloadSize); status != STATUS_OK) {
        ALOGE("failed to read Int32: %d", status);
        return status;
    }
    if (binder_status_t status = deserialize(*in); status != STATUS_OK) {
        ALOGE("failed to deserialize: %d", status);
        return status;
    }
    int32_t sharedMemoryPosition = AParcel_getDataPosition(in);
    ScopedFileDescriptor descriptor;
    if (binder_status_t status = AParcel_readNullableParcelFileDescriptor(in, &descriptor);
        status != STATUS_OK) {
        ALOGE("invalid data, failed to read file descirptor: %d", status);
        return status;
    }
    bool hasSharedMemory = (descriptor.get() != INVALID_MEMORY_FD);
    if (hasSharedMemory) {
        // Release descriptor to memoryFd.
        unique_fd memoryFd = scopeFdToUniqueFd(std::move(descriptor));
        if (binder_status_t status = deserializeSharedMemoryAndClose(std::move(memoryFd));
            status != STATUS_OK) {
            return status;
        }
    }
    if (DBG_PAYLOAD) {
        ALOGD("Read, start:%d totalPayloadSize:%d sharedMemoryPosition:%d hasSharedMemory:%d",
              startPosition, totalPayloadSize, sharedMemoryPosition, hasSharedMemory);
    }
    mHasDeserializedParcelable = true;
    return STATUS_OK;
}

binder_status_t LargeParcelableBase::prepareSharedMemory(AParcel* parcel) const {
    int32_t startPosition = AParcel_getDataPosition(parcel);
    if (binder_status_t status = serializeMemoryFdOrPayload(parcel, nullptr); status != STATUS_OK) {
        ALOGE("failed to serialize: %d", status);
        return status;
    }
    int32_t payloadSize = AParcel_getDataPosition(parcel) - startPosition;
    bool noSharedMemory = (payloadSize <= MAX_DIRECT_PAYLOAD_SIZE);
    if (noSharedMemory) {
        // Do nothing.
        mNeedSharedMemory = false;
        return STATUS_OK;
    }
    binder_status_t status;
    std::unique_ptr<SharedMemory> sharedMemory =
            serializeParcelToSharedMemory(*parcel, startPosition, payloadSize, &status);
    if (status != STATUS_OK) {
        ALOGE("failed to serialize parcel to shared memory: %d", status);
        return status;
    }
    mNeedSharedMemory = true;
    mSharedMemory = std::move(sharedMemory);
    return STATUS_OK;
}

binder_status_t LargeParcelableBase::writeToParcel(AParcel* dest) const {
    // Make this compatible with stable AIDL
    // payloadSize + Nullable Parcelable + Nullable ParcelFileDescriptor
    int startPosition = AParcel_getDataPosition(dest);
    if (!mNeedSharedMemory.has_value()) {
        if (binder_status_t status = prepareSharedMemory(dest); status != STATUS_OK) {
            ALOGE("failed to serialize payload to parcel: %d", status);
            return status;
        }
    }
    bool needSharedMemory = mNeedSharedMemory.value();
    if (needSharedMemory) {
        const SharedMemory* sharedMemory = mSharedMemory.get();
        AParcel_setDataPosition(dest, startPosition);
        if (binder_status_t status = serializeMemoryFdOrPayload(dest, sharedMemory);
            status != STATUS_OK) {
            ALOGE("failed to serialize shared memory fd to parcel: %d", status);
            return status;
        }
    }

    int32_t totalPayloadSize = AParcel_getDataPosition(dest) - startPosition;
    if (DBG_PAYLOAD) {
        ALOGD("Write, start:%d totalPayloadSize:%d hasSharedMemory:%d", startPosition,
              totalPayloadSize, needSharedMemory);
    }
    return OK;
}

binder_status_t LargeParcelableBase::deserializeSharedMemoryAndClose(unique_fd memoryFd) {
    ScopedAParcel parcel(AParcel_create());
    // This would close memoryFd after destruction.
    SharedMemory sharedMemory(std::move(memoryFd));
    if (!sharedMemory.isValid()) {
        ALOGE("invalid shared memory fd, status: %d", sharedMemory.getErr());
        return STATUS_FDS_NOT_ALLOWED;
    }
    if (binder_status_t status = copyFromSharedMemory(sharedMemory, parcel.get());
        status != STATUS_OK) {
        return status;
    }
    int32_t payloadSize;
    if (binder_status_t status = AParcel_readInt32(parcel.get(), &payloadSize);
        status != STATUS_OK) {
        ALOGE("failed to read Int32: %d", status);
        if (DBG_PAYLOAD) {
            ALOGD("parse shared memory file, payload size: %d", payloadSize);
        }
        return status;
    }
    if (binder_status_t status = deserialize(*(parcel.get())); status != STATUS_OK) {
        return status;
    }
    // There is an additional 0 for null file descriptor in the parcel we would ignore.
    return STATUS_OK;
}

binder_status_t LargeParcelableBase::copyFromSharedMemory(const SharedMemory& sharedMemory,
                                                          AParcel* parcel) {
    std::unique_ptr<MappedFile> mappedFile = sharedMemory.mapReadOnly();
    size_t mappedFileSize = mappedFile->getSize();
    if (!mappedFile->isValid()) {
        ALOGE("failed to map file for size: %zu, error: %d", sharedMemory.getSize(),
              mappedFile->getErr());
        return STATUS_FDS_NOT_ALLOWED;
    }
    if (binder_status_t status =
                AParcel_unmarshal(parcel, static_cast<const uint8_t*>(mappedFile->getAddr()),
                                  mappedFileSize);
        status != STATUS_OK) {
        return status;
    }
    AParcel_setDataPosition(parcel, 0);
    if (DBG_PAYLOAD) {
        size_t dumpSize = std::min(DBG_DUMP_LENGTH, mappedFileSize);
        bool truncated = (dumpSize < mappedFileSize);
        std::stringbuf buffer;
        std::ostream bd(&buffer);
        if (truncated) {
            bd << "unmarshalled(truncated):";
        } else {
            bd << "unmarshalled:";
        }
        bd << std::hex;
        size_t parcelStartPosition = AParcel_getDataPosition(parcel);
        std::unique_ptr<uint8_t[]> fromParcel(new uint8_t[mappedFileSize]);
        if (binder_status_t status = AParcel_marshal(parcel, fromParcel.get(), 0, dumpSize);
            status != STATUS_OK) {
            ALOGE("failed to marshal parcel: %d", status);
            return status;
        }
        for (size_t i = 0; i < dumpSize; i++) {
            bd << static_cast<int>(fromParcel[i]);
            if (i != dumpSize - 1) {
                bd << ",";
            }
        }
        bd << "=startPosition:" << parcelStartPosition;
        bd.flush();
        ALOGD("%s", buffer.str().c_str());
        AParcel_setDataPosition(parcel, parcelStartPosition);
    }
    return STATUS_OK;
}

binder_status_t LargeParcelableBase::writeSharedMemoryCompatibleToParcel(
        const SharedMemory* sharedMemory, AParcel* dest) {
    ScopedFileDescriptor descriptor;
    if (sharedMemory == nullptr) {
        return ::ndk::AParcel_writeNullableParcelFileDescriptor(dest, descriptor);
    }
    // non-null case
    unique_fd fd = sharedMemory->getDupFd();
    descriptor.set(fd.release());
    return ::ndk::AParcel_writeNullableParcelFileDescriptor(dest, descriptor);
}

std::unique_ptr<SharedMemory> LargeParcelableBase::serializeParcelToSharedMemory(
        const AParcel& p, int32_t start, int32_t size, binder_status_t* outStatus) {
    std::unique_ptr<SharedMemory> memory(new SharedMemory(size));
    if (!memory->isValid()) {
        ALOGE("failed to create memfile for size: %d, status: %d", size, memory->getErr());
        *outStatus = STATUS_UNKNOWN_ERROR;
        return std::unique_ptr<SharedMemory>(nullptr);
    }
    // This would be unmapped after function returns.
    std::unique_ptr<MappedFile> buffer = memory->mapReadWrite();
    if (!buffer->isValid()) {
        ALOGE("failed to map shared memory as read write for size: %d, status: %d", size,
              buffer->getErr());
        *outStatus = STATUS_UNKNOWN_ERROR;
        return std::unique_ptr<SharedMemory>(nullptr);
    }
    if (binder_status_t status =
                AParcel_marshal(&p, static_cast<uint8_t*>(buffer->getWriteAddr()), start, size);
        status != STATUS_OK) {
        ALOGE("failed to marshal parcel: %d", status);
        *outStatus = status;
        return std::unique_ptr<SharedMemory>(nullptr);
    }
    buffer->sync();

    if (status_t astatus = memory->lock() != OK) {
        ALOGE("Failed to set read-only protection on shared memory: %d", astatus);
        *outStatus = STATUS_UNKNOWN_ERROR;
        return std::unique_ptr<SharedMemory>(nullptr);
    }

    if (DBG_PAYLOAD) {
        size_t dumpSize = std::min(DBG_DUMP_LENGTH, static_cast<size_t>(size));
        std::stringbuf log;
        std::ostream bd(&log);
        const uint8_t* addr = static_cast<uint8_t*>(buffer->getWriteAddr());
        bd << "unmarshalled:" << std::hex;
        for (size_t i = 0; i < dumpSize; i++) {
            bd << static_cast<int>(addr[i]);
            if (i != dumpSize - 1) {
                bd << ",";
            }
        }
        bd.flush();
        ALOGD("%s", log.str().c_str());
    }
    *outStatus = STATUS_OK;
    return memory;
}

int32_t LargeParcelableBase::updatePayloadSize(AParcel* dest, int32_t startPosition) {
    int32_t lastPosition = AParcel_getDataPosition(dest);
    int32_t totalPayloadSize = lastPosition - startPosition;
    AParcel_setDataPosition(dest, startPosition);
    AParcel_writeInt32(dest, totalPayloadSize);
    AParcel_setDataPosition(dest, lastPosition);
    return totalPayloadSize;
}

bool LargeParcelableBase::hasDeserializedParcelable() const {
    return mHasDeserializedParcelable;
}

binder_status_t LargeParcelableBase::getParcelFromMemoryFile(const ScopedFileDescriptor& fd,
                                                             AParcel* parcel) {
    borrowed_fd memoryFd = scopedFdToBorrowedFd(fd);
    SharedMemory sharedMemory(memoryFd);
    if (!sharedMemory.isValid()) {
        ALOGE("invalid shared memory fd, status: %d", sharedMemory.getErr());
        return STATUS_FDS_NOT_ALLOWED;
    }
    if (binder_status_t status = copyFromSharedMemory(sharedMemory, parcel); status != STATUS_OK) {
        ALOGE("failed to copy from shared memory: %d", status);
        return status;
    }
    return STATUS_OK;
}

binder_status_t LargeParcelableBase::parcelToMemoryFile(const AParcel& parcel,
                                                        ScopedFileDescriptor* sharedMemoryFd) {
    int32_t payloadSize = AParcel_getDataPosition(&parcel);
    binder_status_t status = STATUS_OK;
    std::unique_ptr<SharedMemory> sharedMemory =
            serializeParcelToSharedMemory(parcel, /*start=*/0, payloadSize, &status);
    if (status != STATUS_OK) {
        ALOGE("failed to serialize parcel to shared memory: %d", status);
        return status;
    }

    unique_fd fd(sharedMemory->getDupFd());
    sharedMemoryFd->set(fd.release());
    return STATUS_OK;
}

binder_status_t LargeParcelableBase::serializeMemoryFdOrPayload(
        AParcel* dest, const SharedMemory* sharedMemory) const {
    // This is compatible with stable AIDL serialization:
    // payload size + payload + nullable fd
    // The shared Memory file might contain a serialized parcel created from this function.
    int32_t startPosition = AParcel_getDataPosition(dest);
    AParcel_writeInt32(dest, 0);
    if (sharedMemory == nullptr) {
        if (binder_status_t status = serialize(dest); status != STATUS_OK) {
            ALOGE("failed to serialize: %d", status);
            return status;
        }
    } else {
        serializeNullPayload(dest);
    }

    if (DBG_PAYLOAD) {
        int sharedMemoryPosition = AParcel_getDataPosition(dest) - startPosition;
        ALOGD("Serialize shared memory fd: sharedMemoryPosition:%d hasSharedMemory:%d",
              sharedMemoryPosition, sharedMemory != nullptr);
    }
    if (binder_status_t status = writeSharedMemoryCompatibleToParcel(sharedMemory, dest);
        status != STATUS_OK) {
        ALOGE("failed to write file descriptor to parcel: %d", status);
        return status;
    }
    updatePayloadSize(dest, startPosition);
    return STATUS_OK;
}

}  // namespace car_binder_lib
}  // namespace automotive
}  // namespace android
