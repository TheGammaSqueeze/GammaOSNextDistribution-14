/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MEMORY_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MEMORY_H

#include <CpuExecutor.h>
#include <LegacyUtils.h>
#include <android-base/macros.h>
#include <android-base/scopeguard.h>
#include <nnapi/IBuffer.h>
#include <nnapi/IBurst.h>
#include <nnapi/SharedMemory.h>
#include <nnapi/Validation.h>
#include <sys/mman.h>

#include <algorithm>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <tuple>
#include <unordered_map>
#include <utility>
#include <vector>

#include "NeuralNetworks.h"

namespace android {
namespace nn {

class CompilationBuilder;
class Device;
class ModelBuilder;
class RuntimePreparedModel;

// A utility template class to accumulate multiple objects and assign each
// a distinct index number, starting with 0.
//
// The user of this class is responsible for avoiding concurrent calls
// to this class from multiple threads.
template <typename ObjectType>
class ObjectTracker {
   public:
    // Adds the object, if it does not already exists.  Returns its index.
    // The objects should survive the tracker.
    uint32_t add(const ObjectType* object) {
        VLOG(MEMORY) << __func__ << "(" << SHOW_IF_DEBUG(object) << ")";
        // See if we already have this object. If so, return its index.
        auto i = mKnown.find(object);
        if (i != mKnown.end()) {
            return i->second;
        }
        VLOG(MEMORY) << "It's new";
        // It's a new one.  Save it an assign an index to it.
        size_t next = mKnown.size();
        uint32_t idx = static_cast<uint32_t>(next);
        mKnown[object] = idx;
        mObjects.push_back(object);
        return idx;
    }

    // Returns the number of objects contained.
    uint32_t size() const { return mObjects.size(); }
    // Returns the ith object.
    const ObjectType* operator[](size_t i) const {
        CHECK(i < size());
        return mObjects[i];
    }
    // Iteration
    auto begin() { return mObjects.begin(); }
    auto end() { return mObjects.end(); }
    auto begin() const { return mObjects.begin(); }
    auto end() const { return mObjects.end(); }
    const std::vector<const ObjectType*>& getObjects() const { return mObjects; }

   private:
    // The vector of object pointers we are building.
    std::vector<const ObjectType*> mObjects;
    // A faster way to see if we already have an object than doing find().
    std::unordered_map<const ObjectType*, uint32_t> mKnown;
};

using CompilationRole = std::tuple<const CompilationBuilder*, IOType, uint32_t>;

struct MemoryDescriptor {
    std::vector<uint32_t> dimensions;
    ObjectTracker<RuntimePreparedModel> preparedModels;
    std::vector<BufferRole> inputRoles, outputRoles;
};

class MemoryValidatorBase {
    DISALLOW_COPY_AND_ASSIGN(MemoryValidatorBase);

   public:
    MemoryValidatorBase() = default;
    virtual ~MemoryValidatorBase() = default;

    // Validate the memory usage and size information when passed in
    // ANeuralNetworks{Model,Compilation}_set*FromMemory.
    //
    // This method only validates the arguments against the memory. It does not validate the
    // correctness of the arguments themselves. E.g. it does not validate if the index is out of
    // range.
    //
    // Usages:
    //   - ANeuralNetworksModel_setOperandValueFromMemory:
    //         validate(nullptr, IOType::INPUT, operandIndex, nullptr, offset, length)
    //
    //   - ANeuralNetworksExecution_setInputFromMemory:
    //         validate(compilation, IOType::INPUT, inputIndex, type, offset, length)
    //
    //   - ANeuralNetworksExecution_setOutputFromMemory:
    //         validate(compilation, IOType::OUTPUT, outputIndex, type, offset, length)
    //
    virtual bool validate(const CompilationBuilder* compilation, IOType ioType, uint32_t index,
                          const ANeuralNetworksOperandType* type, uint32_t offset,
                          uint32_t length) const = 0;

    // Validate the memory dimensional information at the beginning of a computation.
    virtual bool validateInputDimensions(const std::vector<uint32_t>&) const { return true; }

    // The validation metadata for this memory.
    struct Metadata {
        // The byte size of the memory when it is transformed to a closely packed layout.
        // Set to 0 if unknown (e.g. non-BLOB mode AHWB or device memory with dynamic shape).
        uint32_t logicalSize;

        // The dimensions of the memory. Set to empty if undefined.
        std::vector<uint32_t> dimensions;

        // The data type, scale, zero point, and extra parameters of the target operand.
        // Other fields will be ignored, including dimensions, lifetime, location, etc.
        // Set to std::nullopt if undefined.
        std::optional<Operand> operand;
    };
    virtual Metadata getMetadata() const = 0;

    // Try update the memory metadata with the provided metadata. Return false if incompatible.
    virtual bool updateMetadata(const Metadata& metadata) = 0;

    // Whether the memory is created with unknown dimensions or rank.
    virtual bool createdWithUnknownShape() const { return false; }

    virtual void setInitialized(bool) {}
    virtual bool isInitialized() const { return true; }
};

int copyIBufferToMemory(const SharedBuffer& src, const SharedMemory& dst);

int copyMemoryToIBuffer(const SharedMemory& src, const SharedBuffer& dst,
                        const std::vector<uint32_t>& dimensions);

// Represents a memory region.
class RuntimeMemory {
    // Disallow copy and assign to prevent slicing
    DISALLOW_COPY_AND_ASSIGN(RuntimeMemory);

   public:
    virtual ~RuntimeMemory() = default;

    Request::MemoryPool getMemoryPool() const;
    const SharedMemory& getMemory() const { return kMemory; }
    const SharedBuffer& getIBuffer() const { return kBuffer; }
    virtual uint32_t getSize() const { return nn::getSize(getMemory()); }
    virtual std::optional<RunTimePoolInfo> getRunTimePoolInfo() const;

    MemoryValidatorBase& getValidator() const {
        CHECK(mValidator != nullptr);
        return *mValidator;
    }

    void setValidator(std::unique_ptr<MemoryValidatorBase> validator) {
        mValidator = std::move(validator);
    }

    // This function binds `cacheHold` to the memory object, holding it for as long as the Memory
    // object is alive. This keeps the cache present while the Memory object is alive. If
    // `cacheHold` is null, this function is a no-op.
    void hold(const IBurst::OptionalCacheHold& cacheHold) const;

    static int copy(const RuntimeMemory& src, const RuntimeMemory& dst);

   protected:
    explicit RuntimeMemory(SharedMemory memory);
    RuntimeMemory(SharedMemory memory, std::unique_ptr<MemoryValidatorBase> validator);
    explicit RuntimeMemory(SharedBuffer buffer);

    // The canonical representation for this memory.  We will use one of the
    // following values when communicating with the drivers.
    const SharedMemory kMemory = std::make_shared<const Memory>();
    const SharedBuffer kBuffer;

    std::unique_ptr<MemoryValidatorBase> mValidator;

   private:
    mutable std::mutex mMutex;

    // This set contains `CacheHold` objects, holding it for as long as the Memory object is alive.
    // This keeps the cache present while the Memory object is alive.
    mutable std::set<IBurst::OptionalCacheHold> mHold;

    mutable std::optional<RunTimePoolInfo> mCachedRunTimePoolInfo;
    mutable bool mHasCachedRunTimePoolInfo = false;
};

class MemoryBuilder {
    DISALLOW_COPY_AND_ASSIGN(MemoryBuilder);

   public:
    MemoryBuilder() = default;

    int addRole(const CompilationBuilder& compilation, IOType ioType, uint32_t index, float freq);
    int setDimensions(const std::vector<uint32_t>& dimensions);

    int finish();

    std::pair<int, std::unique_ptr<RuntimeMemory>> allocate() const;

   private:
    bool badState(const char* name) const;

    // The memory descriptor that the MemoryBuilder is building.
    MemoryDescriptor mDesc;

    // The roles that have been specified via addRole.
    // This is to check whether a new role has been seen before or not.
    std::set<CompilationRole> mRoles;

    // Keep track of the data type, scale, zero point, and extra parameters of the target operand.
    // Other fields will be ignored, including dimensions, lifetime, location, etc.
    // It is std::nullopt if no usage has been specified yet.
    std::optional<Operand> mOperand;

    // Once the descriptor has been finished, we should not allow further modifications.
    bool mFinished = false;

    // The following fields are only valid when finished.

    // The chosen device to allocate the memory. Set to nullptr if there are multiple devices.
    const Device* mAllocator = nullptr;

    // Whether BLOB mode AHWB is supported on all of the relevant devices of the roles.
    bool mSupportsAhwb = false;

    // If set to true, allocate() will fallback to Ashmem or AHardwareBuffer if the memory
    // allocation fails on the chosen device, or if there is no device chosen.
    bool mShouldFallback = true;
};

class MemoryAshmem : public RuntimeMemory {
   public:
    // Creates a memory object containing a new android shared memory ("ashmem")
    // object of the size specified in bytes. Because this ashmem region can be
    // shared with and accessed by one or more driver processes, MemoryAshmem
    // has shared ownership over the ashmem region.
    //
    // On success, returns ANEURALNETWORKS_NO_ERROR and a memory object.
    // On error, returns the appropriate NNAPI error code and nullptr.
    static std::pair<int, std::unique_ptr<MemoryAshmem>> create(uint32_t size);

    // Get a pointer to the ashmem region of memory. The returned pointer is
    // valid for the lifetime of the MemoryAshmem object. This call always
    // returns non-null because it was validated during MemoryAshmem::create.
    uint8_t* getPointer() const;

    std::optional<RunTimePoolInfo> getRunTimePoolInfo() const override {
        return RunTimePoolInfo::createFromExistingBuffer(getPointer(), nn::getSize(kMemory));
    }

    // prefer using MemoryAshmem::create
    MemoryAshmem(SharedMemory memory, Mapping mapped);

   private:
    const Mapping kMapping;
};

class MemoryFd : public RuntimeMemory {
   public:
    // Create a memory object based on input size, prot, and fd. This function
    // duplicates the provided fd, and owns the duplicate.
    //
    // On success, returns ANEURALNETWORKS_NO_ERROR and a memory object.
    // On error, returns the appropriate NNAPI error code and nullptr.
    static std::pair<int, std::unique_ptr<MemoryFd>> create(size_t size, int prot, int fd,
                                                            size_t offset);

    // prefer using MemoryFd::create
    explicit MemoryFd(SharedMemory memory);
};

class MemoryAHWB : public RuntimeMemory {
   public:
    // Create a memory object to keep track of (but not take ownership of) the
    // provided AHardwareBuffer handle.
    //
    // On success, returns ANEURALNETWORKS_NO_ERROR and a memory object.
    // On error, returns the appropriate NNAPI error code and nullptr.
    static std::pair<int, std::unique_ptr<MemoryAHWB>> create(const AHardwareBuffer& ahwb);

    // prefer using MemoryAHWB::create
    MemoryAHWB(SharedMemory memory, std::unique_ptr<MemoryValidatorBase> validator)
        : RuntimeMemory(std::move(memory), std::move(validator)) {}
};

class MemoryRuntimeAHWB : public RuntimeMemory {
   public:
    // Create a memory object containing a new BLOB-mode AHardwareBuffer memory
    // object of the size specified in bytes. The created memory is managed and
    // owned by the NNAPI runtime.
    //
    // On success, returns ANEURALNETWORKS_NO_ERROR and a memory object.
    // On error, returns the appropriate NNAPI error code and nullptr.
    static std::pair<int, std::unique_ptr<MemoryRuntimeAHWB>> create(uint32_t size);

    // Get a pointer to the content of the memory. The returned pointer is
    // valid for the lifetime of the MemoryRuntimeAHWB object. This call always
    // returns non-null because it was validated during MemoryRuntimeAHWB::create.
    uint8_t* getPointer() const;

    std::optional<RunTimePoolInfo> getRunTimePoolInfo() const override {
        return RunTimePoolInfo::createFromExistingBuffer(getPointer(), nn::getSize(kMemory));
    }

    // prefer using MemoryRuntimeAHWB::create
    MemoryRuntimeAHWB(SharedMemory memory, Mapping mapping);

   private:
    const Mapping kMapping;
};

class MemoryFromDevice : public RuntimeMemory {
   public:
    // Create a memory object to keep track of a driver-allocated device memory.
    // The memory is recognized by the driver via a token.
    //
    // On success, returns ANEURALNETWORKS_NO_ERROR and a memory object.
    // On error, returns the appropriate NNAPI error code and nullptr.
    static std::pair<int, std::unique_ptr<MemoryFromDevice>> create(SharedBuffer buffer);

    // prefer using MemoryFromDevice::create
    explicit MemoryFromDevice(SharedBuffer buffer);
};

using MemoryTracker = ObjectTracker<RuntimeMemory>;

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_MEMORY_H
