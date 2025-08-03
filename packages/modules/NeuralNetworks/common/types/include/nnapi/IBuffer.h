/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IBUFFER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IBUFFER_H

#include "nnapi/Result.h"
#include "nnapi/Types.h"

namespace android::nn {

/**
 * This interface represents a device memory buffer.
 *
 * This interface is thread-safe, and any class that implements this interface must be thread-safe.
 */
class IBuffer {
   public:
    /**
     * Retrieves the token corresponding to this buffer.
     *
     * @return MemoryDomainToken corresponding to this buffer.
     */
    virtual Request::MemoryDomainToken getToken() const = 0;

    /**
     * Retrieves the content of this buffer to a shared memory region.
     *
     * The IBuffer object must have been initialized before the call to IBuffer::copyTo.
     * For more information on the state of the IBuffer object, refer to IDevice::allocate.
     *
     * @param dst The destination shared memory region.
     * @return Nothing on success, otherwise GeneralError.
     */
    virtual GeneralResult<void> copyTo(const SharedMemory& dst) const = 0;

    /**
     * Sets the content of this buffer from a shared memory region.
     *
     * @param src The source shared memory region.
     * @param dimensions Updated dimensional information. If the dimensions of the IBuffer object
     *     are not fully specified, then the dimensions must be fully specified here. If the
     *     dimensions of the IBuffer object are fully specified, then the dimensions may be empty
     *     here. If dimensions.size() > 0, then all dimensions must be specified here, and any
     *     dimension that was specified in the IBuffer object must have the same value here.
     * @return Nothing on success, otherwise GeneralError.
     */
    virtual GeneralResult<void> copyFrom(const SharedMemory& src,
                                         const Dimensions& dimensions) const = 0;

    // Public virtual destructor to allow objects to be stored (and destroyed) as smart pointers.
    // E.g., std::unique_ptr<IBuffer>.
    virtual ~IBuffer() = default;

   protected:
    // Protect the non-destructor special member functions to prevent object slicing.
    IBuffer() = default;
    IBuffer(const IBuffer&) = default;
    IBuffer(IBuffer&&) noexcept = default;
    IBuffer& operator=(const IBuffer&) = default;
    IBuffer& operator=(IBuffer&&) noexcept = default;
};

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_IBUFFER_H
