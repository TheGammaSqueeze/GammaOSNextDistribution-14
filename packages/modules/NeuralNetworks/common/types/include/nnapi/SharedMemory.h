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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_SHARED_MEMORY_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_SHARED_MEMORY_H

#include <android-base/unique_fd.h>

#include <any>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <variant>
#include <vector>

#include "nnapi/Result.h"
#include "nnapi/Types.h"

namespace android::nn {

class MutableMemoryBuilder {
   public:
    explicit MutableMemoryBuilder(uint32_t poolIndex);

    DataLocation append(size_t length, size_t alignment = kMinMemoryAlignment,
                        size_t padding = kMinMemoryPadding);
    bool empty() const;

    GeneralResult<SharedMemory> finish();

   private:
    uint32_t mPoolIndex;
    size_t mSize = 0;
};

class ConstantMemoryBuilder {
   public:
    explicit ConstantMemoryBuilder(uint32_t poolIndex);

    DataLocation append(const void* data, size_t length);
    bool empty() const;

    GeneralResult<SharedMemory> finish();

   private:
    struct LazyCopy {
        const void* data;
        size_t length;
        size_t offset;
    };

    MutableMemoryBuilder mBuilder;
    std::vector<LazyCopy> mSlices;
};

GeneralResult<base::unique_fd> dupFd(int fd);

// Precondition: `*ForwardFdIt` must be convertible to `int`
template <typename ForwardFdIt>
GeneralResult<std::vector<base::unique_fd>> dupFds(ForwardFdIt first, ForwardFdIt last) {
    std::vector<base::unique_fd> fds;
    fds.reserve(std::distance(first, last));
    for (; first != last; ++first) {
        const int fd = *first;
        fds.push_back(NN_TRY(dupFd(fd)));
    }
    return fds;
}

// Precondition: size > 0
GeneralResult<SharedMemory> createSharedMemory(size_t size);

// Duplicates `fd` and takes ownership of the duplicate.
// Precondition: size > 0
GeneralResult<SharedMemory> createSharedMemoryFromFd(size_t size, int prot, int fd, size_t offset);

#ifdef __ANDROID__
// Precondition: ahwb != nullptr
GeneralResult<SharedMemory> createSharedMemoryFromAHWB(AHardwareBuffer* ahwb, bool takeOwnership);
#endif  // __ANDROID__

// Precondition: memory != nullptr
size_t getSize(const SharedMemory& memory);

bool isAhwbBlob(const Memory::HardwareBuffer& memory);

// Precondition: memory != nullptr
bool isAhwbBlob(const SharedMemory& memory);

struct Mapping {
    std::variant<const void*, void*> pointer;
    size_t size;
    std::any context;
};

GeneralResult<Mapping> map(const SharedMemory& memory);

bool flush(const Mapping& mapping);

// Indicates if the object contains no pointer-based data that could be relocated to shared memory.
bool hasNoPointerData(const Model& model);
bool hasNoPointerData(const Request& request);

// Relocate pointer-based data to shared memory. If `model` has no Operand::LifeTime::POINTER data,
// the function returns with a reference to `model`. If `model` has Operand::LifeTime::POINTER data,
// the model is copied to `maybeModelInSharedOut` with the POINTER data relocated to a memory pool,
// and the function returns with a reference to `*maybeModelInSharedOut`.
GeneralResult<std::reference_wrapper<const Model>> flushDataFromPointerToShared(
        const Model* model, std::optional<Model>* maybeModelInSharedOut);

// Record a relocation mapping between pointer-based data and shared memory.
// Only two specializations of this template may exist:
// - RelocationInfo<const void*> for request inputs
// - RelocationInfo<void*> for request outputs
template <typename PointerType>
struct RelocationInfo {
    PointerType data;
    size_t length;
    size_t offset;
};
using InputRelocationInfo = RelocationInfo<const void*>;
using OutputRelocationInfo = RelocationInfo<void*>;

// Keep track of the relocation mapping between pointer-based data and shared memory pool,
// and provide method to copy the data between pointers and the shared memory pool.
// Only two specializations of this template may exist:
// - RelocationTracker<InputRelocationInfo> for request inputs
// - RelocationTracker<OutputRelocationInfo> for request outputs
template <typename RelocationInfoType>
class RelocationTracker {
   public:
    static GeneralResult<std::unique_ptr<RelocationTracker>> create(
            std::vector<RelocationInfoType> relocationInfos, SharedMemory memory) {
        auto mapping = NN_TRY(map(memory));
        return std::make_unique<RelocationTracker<RelocationInfoType>>(
                std::move(relocationInfos), std::move(memory), std::move(mapping));
    }

    RelocationTracker(std::vector<RelocationInfoType> relocationInfos, SharedMemory memory,
                      Mapping mapping)
        : kRelocationInfos(std::move(relocationInfos)),
          kMemory(std::move(memory)),
          kMapping(std::move(mapping)) {}

    // Specializations defined in CommonUtils.cpp.
    // For InputRelocationTracker, this method will copy pointer data to the shared memory pool.
    // For OutputRelocationTracker, this method will copy shared memory data to the pointers.
    void flush() const;

   private:
    const std::vector<RelocationInfoType> kRelocationInfos;
    const SharedMemory kMemory;
    const Mapping kMapping;
};
using InputRelocationTracker = RelocationTracker<InputRelocationInfo>;
using OutputRelocationTracker = RelocationTracker<OutputRelocationInfo>;

struct RequestRelocation {
    std::unique_ptr<InputRelocationTracker> input;
    std::unique_ptr<OutputRelocationTracker> output;
};

// Relocate pointer-based data to shared memory. If `request` has no
// Request::Argument::LifeTime::POINTER data, the function returns with a reference to `request`. If
// `request` has Request::Argument::LifeTime::POINTER data, the request is copied to
// `maybeRequestInSharedOut` with the POINTER data relocated to a memory pool, and the function
// returns with a reference to `*maybeRequestInSharedOut`. The `relocationOut` will be set to track
// the input and output relocations.
//
// Unlike `flushDataFromPointerToShared`, this method will not copy the input pointer data to the
// shared memory pool. Use `relocationOut` to flush the input or output data after the call.
GeneralResult<std::reference_wrapper<const Request>> convertRequestFromPointerToShared(
        const Request* request, uint32_t alignment, uint32_t padding,
        std::optional<Request>* maybeRequestInSharedOut, RequestRelocation* relocationOut);

}  // namespace android::nn

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_COMMON_TYPES_NNAPI_SHARED_MEMORY_H
