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

// Provides C++ classes to more easily use the Neural Networks API.
// TODO(b/117845862): this should be auto generated from NeuralNetworksWrapper.h.

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_SL_SUPPORT_LIBRARY_WRAPPER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_SL_SUPPORT_LIBRARY_WRAPPER_H

#include <android-base/unique_fd.h>
#include <android/hardware_buffer.h>
#include <math.h>
#include <unistd.h>

#include <algorithm>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include "NeuralNetworksWrapper.h"
#include "SupportLibrary.h"

using namespace ::android::nn::wrapper;

namespace android {
namespace nn {
namespace sl_wrapper {

using ::android::nn::wrapper::Duration;
using ::android::nn::wrapper::OperandType;
using ::android::nn::wrapper::Result;

class Memory {
   public:
    // Takes ownership of a ANeuralNetworksMemory
    Memory(const NnApiSupportLibrary* nnapi, ANeuralNetworksMemory* memory)
        : mNnApi(nnapi), mMemory(memory), mSize(0) {}

    // Create from a FD and may takes ownership of the fd.
    Memory(const NnApiSupportLibrary* nnapi, size_t size, int protect, int fd, size_t offset,
           bool ownsFd = false)
        : mNnApi(nnapi), mOwnedFd(ownsFd ? std::optional<int>{fd} : std::nullopt), mSize(size) {
        mValid = mNnApi->getFL5()->ANeuralNetworksMemory_createFromFd(
                         size, protect, fd, offset, &mMemory) == ANEURALNETWORKS_NO_ERROR;
    }

    // Create from a buffer, may take ownership.
    Memory(const NnApiSupportLibrary* nnapi, AHardwareBuffer* buffer, bool ownAHWB, size_t size)
        : mNnApi(nnapi), mOwnedAHWB(ownAHWB ? buffer : nullptr), mSize(size) {
        mValid = mNnApi->getFL5()->ANeuralNetworksMemory_createFromAHardwareBuffer(
                         buffer, &mMemory) == ANEURALNETWORKS_NO_ERROR;
    }

    // Create from a desc
    Memory(const NnApiSupportLibrary* nnapi, ANeuralNetworksMemoryDesc* desc, size_t size)
        : mNnApi(nnapi), mSize(size) {
        mValid = mNnApi->getFL5()->ANeuralNetworksMemory_createFromDesc(desc, &mMemory) ==
                 ANEURALNETWORKS_NO_ERROR;
    }

    virtual ~Memory() {
        if (mMemory) {
            mNnApi->getFL5()->ANeuralNetworksMemory_free(mMemory);
        }
        if (mOwnedFd) {
            close(*mOwnedFd);
        }
        if (mOwnedAHWB) {
            AHardwareBuffer_release(mOwnedAHWB);
        }
    }

    // Disallow copy semantics to ensure the runtime object can only be freed
    // once. Copy semantics could be enabled if some sort of reference counting
    // or deep-copy system for runtime objects is added later.
    Memory(const Memory&) = delete;
    Memory& operator=(const Memory&) = delete;

    // Move semantics to remove access to the runtime object from the wrapper
    // object that is being moved. This ensures the runtime object will be
    // freed only once.
    Memory(Memory&& other) { *this = std::move(other); }
    Memory& operator=(Memory&& other) {
        if (this != &other) {
            if (mMemory) {
                mNnApi->getFL5()->ANeuralNetworksMemory_free(mMemory);
            }
            if (mOwnedFd) {
                close(*mOwnedFd);
            }
            if (mOwnedAHWB) {
                AHardwareBuffer_release(mOwnedAHWB);
            }

            mMemory = other.mMemory;
            mValid = other.mValid;
            mNnApi = other.mNnApi;
            mOwnedFd = other.mOwnedFd;
            mOwnedAHWB = other.mOwnedAHWB;
            other.mMemory = nullptr;
            other.mValid = false;
            other.mOwnedFd.reset();
            other.mOwnedAHWB = nullptr;
        }
        return *this;
    }

    ANeuralNetworksMemory* get() const { return mMemory; }
    bool isValid() const { return mValid; }
    size_t getSize() const { return mSize; }
    Result copyTo(Memory& other) {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksMemory_copy(mMemory, other.mMemory));
    }

   private:
    const NnApiSupportLibrary* mNnApi = nullptr;
    ANeuralNetworksMemory* mMemory = nullptr;
    bool mValid = true;
    std::optional<int> mOwnedFd;
    AHardwareBuffer* mOwnedAHWB = nullptr;
    size_t mSize;
};

class Model {
   public:
    Model(const NnApiSupportLibrary* nnapi) : mNnApi(nnapi) {
        mValid = mNnApi->getFL5()->ANeuralNetworksModel_create(&mModel) == ANEURALNETWORKS_NO_ERROR;
    }
    ~Model() {
        if (mModel) {
            mNnApi->getFL5()->ANeuralNetworksModel_free(mModel);
        }
    }

    // Disallow copy semantics to ensure the runtime object can only be freed
    // once. Copy semantics could be enabled if some sort of reference counting
    // or deep-copy system for runtime objects is added later.
    Model(const Model&) = delete;
    Model& operator=(const Model&) = delete;

    // Move semantics to remove access to the runtime object from the wrapper
    // object that is being moved. This ensures the runtime object will be
    // freed only once.
    Model(Model&& other) { *this = std::move(other); }
    Model& operator=(Model&& other) {
        if (this != &other) {
            if (mModel != nullptr) {
                mNnApi->getFL5()->ANeuralNetworksModel_free(mModel);
            }
            mNnApi = other.mNnApi;
            mModel = other.mModel;
            mNextOperandId = other.mNextOperandId;
            mValid = other.mValid;
            mRelaxed = other.mRelaxed;
            mFinished = other.mFinished;
            mOperands = std::move(other.mOperands);
            mInputs = std::move(other.mInputs);
            mOutputs = std::move(other.mOutputs);
            other.mModel = nullptr;
            other.mNextOperandId = 0;
            other.mValid = false;
            other.mRelaxed = false;
            other.mFinished = false;
        }
        return *this;
    }

    Result finish() {
        if (mValid) {
            auto result =
                    static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksModel_finish(mModel));
            if (result != Result::NO_ERROR) {
                mValid = false;
            }
            mFinished = true;
            return result;
        } else {
            return Result::BAD_STATE;
        }
    }

    uint32_t addOperand(const OperandType* type) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_addOperand(mModel, &type->operandType) !=
            ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        } else {
            mOperands.push_back(*type);
        }

        if (type->channelQuant) {
            if (mNnApi->getFL5()->ANeuralNetworksModel_setOperandSymmPerChannelQuantParams(
                        mModel, mNextOperandId, &type->channelQuant.value().params) !=
                ANEURALNETWORKS_NO_ERROR) {
                mValid = false;
            }
        }

        return mNextOperandId++;
    }

    template <typename T>
    uint32_t addConstantOperand(const OperandType* type, const T& value) {
        static_assert(sizeof(T) <= ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES,
                      "Values larger than ANEURALNETWORKS_MAX_SIZE_OF_IMMEDIATELY_COPIED_VALUES "
                      "not supported");
        uint32_t index = addOperand(type);
        setOperandValue(index, &value);
        return index;
    }

    uint32_t addModelOperand(const Model* value) {
        OperandType operandType(Type::MODEL, {});
        uint32_t operand = addOperand(&operandType);
        setOperandValueFromModel(operand, value);
        return operand;
    }

    void setOperandValue(uint32_t index, const void* buffer, size_t length) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_setOperandValue(mModel, index, buffer, length) !=
            ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }

    template <typename T>
    void setOperandValue(uint32_t index, const T* value) {
        static_assert(!std::is_pointer<T>(), "No operand may have a pointer as its value");
        return setOperandValue(index, value, sizeof(T));
    }

    void setOperandValueFromMemory(uint32_t index, const Memory* memory, uint32_t offset,
                                   size_t length) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_setOperandValueFromMemory(
                    mModel, index, memory->get(), offset, length) != ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }

    void setOperandValueFromModel(uint32_t index, const Model* value) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_setOperandValueFromModel(
                    mModel, index, value->mModel) != ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }

    void setOperandValueFromModel(uint32_t index, ANeuralNetworksModel* value) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_setOperandValueFromModel(mModel, index, value) !=
            ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }

    void addOperation(ANeuralNetworksOperationType type, const std::vector<uint32_t>& inputs,
                      const std::vector<uint32_t>& outputs) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_addOperation(
                    mModel, type, static_cast<uint32_t>(inputs.size()), inputs.data(),
                    static_cast<uint32_t>(outputs.size()),
                    outputs.data()) != ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }
    void identifyInputsAndOutputs(const std::vector<uint32_t>& inputs,
                                  const std::vector<uint32_t>& outputs) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_identifyInputsAndOutputs(
                    mModel, static_cast<uint32_t>(inputs.size()), inputs.data(),
                    static_cast<uint32_t>(outputs.size()),
                    outputs.data()) != ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        } else {
            mInputs = inputs;
            mOutputs = outputs;
        }
    }

    void relaxComputationFloat32toFloat16(bool isRelax) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_relaxComputationFloat32toFloat16(
                    mModel, isRelax) == ANEURALNETWORKS_NO_ERROR) {
            mRelaxed = isRelax;
        }
    }

    void getExtensionOperandType(const std::string& extensionName,
                                 uint16_t operandCodeWithinExtension, int32_t* type) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_getExtensionOperandType(
                    mModel, extensionName.c_str(), operandCodeWithinExtension, type) !=
            ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }

    void getExtensionOperationType(const std::string& extensionName,
                                   uint16_t operandCodeWithinExtension,
                                   ANeuralNetworksOperationType* type) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_getExtensionOperationType(
                    mModel, extensionName.c_str(), operandCodeWithinExtension, type) !=
            ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }

    void setOperandExtensionData(int32_t operandId, const void* data, size_t length) {
        if (mNnApi->getFL5()->ANeuralNetworksModel_setOperandExtensionData(
                    mModel, operandId, data, length) != ANEURALNETWORKS_NO_ERROR) {
            mValid = false;
        }
    }

    ANeuralNetworksModel* getHandle() const { return mModel; }
    bool isValid() const { return mValid; }
    bool isRelaxed() const { return mRelaxed; }
    bool isFinished() const { return mFinished; }

    const std::vector<uint32_t>& getInputs() const { return mInputs; }
    const std::vector<uint32_t>& getOutputs() const { return mOutputs; }
    const std::vector<OperandType>& getOperands() const { return mOperands; }

   protected:
    const NnApiSupportLibrary* mNnApi = nullptr;
    ANeuralNetworksModel* mModel = nullptr;
    // We keep track of the operand ID as a convenience to the caller.
    uint32_t mNextOperandId = 0;
    // We keep track of the operand datatypes/dimensions as a convenience to the caller.
    std::vector<OperandType> mOperands;
    std::vector<uint32_t> mInputs;
    std::vector<uint32_t> mOutputs;
    bool mValid = true;
    bool mRelaxed = false;
    bool mFinished = false;
};

class Compilation {
   public:
    // On success, createForDevice(s) will return Result::NO_ERROR and the created compilation;
    // otherwise, it will return the error code and Compilation object wrapping a nullptr handle.
    static std::pair<Result, Compilation> createForDevice(const NnApiSupportLibrary* nnapi,
                                                          const Model* model,
                                                          const ANeuralNetworksDevice* device) {
        return createForDevices(nnapi, model, {device});
    }
    static std::pair<Result, Compilation> createForDevices(
            const NnApiSupportLibrary* nnapi, const Model* model,
            const std::vector<const ANeuralNetworksDevice*>& devices) {
        ANeuralNetworksCompilation* compilation = nullptr;
        const Result result =
                static_cast<Result>(nnapi->getFL5()->ANeuralNetworksCompilation_createForDevices(
                        model->getHandle(), devices.empty() ? nullptr : devices.data(),
                        devices.size(), &compilation));
        return {result, Compilation(nnapi, compilation)};
    }

    ~Compilation() { mNnApi->getFL5()->ANeuralNetworksCompilation_free(mCompilation); }

    // Disallow copy semantics to ensure the runtime object can only be freed
    // once. Copy semantics could be enabled if some sort of reference counting
    // or deep-copy system for runtime objects is added later.
    Compilation(const Compilation&) = delete;
    Compilation& operator=(const Compilation&) = delete;

    // Move semantics to remove access to the runtime object from the wrapper
    // object that is being moved. This ensures the runtime object will be
    // freed only once.
    Compilation(Compilation&& other) { *this = std::move(other); }
    Compilation& operator=(Compilation&& other) {
        if (this != &other) {
            mNnApi = other.mNnApi;
            mNnApi->getFL5()->ANeuralNetworksCompilation_free(mCompilation);
            mCompilation = other.mCompilation;
            other.mCompilation = nullptr;
        }
        return *this;
    }

    Result setPreference(ExecutePreference preference) {
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksCompilation_setPreference(
                mCompilation, static_cast<int32_t>(preference)));
    }

    Result setPriority(ExecutePriority priority) {
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksCompilation_setPriority(
                mCompilation, static_cast<int32_t>(priority)));
    }

    Result setTimeout(uint64_t durationNs) {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksCompilation_setTimeout(mCompilation, durationNs));
    }

    Result setCaching(const std::string& cacheDir, const std::vector<uint8_t>& token) {
        if (token.size() != ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN) {
            return Result::BAD_DATA;
        }
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksCompilation_setCaching(
                mCompilation, cacheDir.c_str(), token.data()));
    }

    Result setCachingFromFds(const std::vector<int>& modelCacheFds,
                             const std::vector<int>& dataCacheFds,
                             const std::vector<uint8_t>& token) {
        if (token.size() != ANEURALNETWORKS_BYTE_SIZE_OF_CACHE_TOKEN) {
            return Result::BAD_DATA;
        }
        return static_cast<Result>(
                mNnApi->getFL5()->SL_ANeuralNetworksCompilation_setCachingFromFds(
                        mCompilation, modelCacheFds.data(), modelCacheFds.size(),
                        dataCacheFds.data(), dataCacheFds.size(), token.data()));
    }

    Result setCachingFromFds(const std::vector<base::unique_fd>& modelCacheOwnedFds,
                             const std::vector<base::unique_fd>& dataCacheOwnedFds,
                             const std::vector<uint8_t>& token) {
        std::vector<int> modelCacheFds, dataCacheFds;
        for (const auto& fd : modelCacheOwnedFds) {
            modelCacheFds.push_back(fd.get());
        }
        for (const auto& fd : dataCacheOwnedFds) {
            dataCacheFds.push_back(fd.get());
        }
        return setCachingFromFds(modelCacheFds, dataCacheFds, token);
    }

    Result finish() {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksCompilation_finish(mCompilation));
    }

    Result getPreferredMemoryAlignmentForInput(uint32_t index, uint32_t* alignment) const {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksCompilation_getPreferredMemoryAlignmentForInput(
                        mCompilation, index, alignment));
    };

    Result getPreferredMemoryPaddingForInput(uint32_t index, uint32_t* padding) const {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksCompilation_getPreferredMemoryPaddingForInput(
                        mCompilation, index, padding));
    };

    Result getPreferredMemoryAlignmentForOutput(uint32_t index, uint32_t* alignment) const {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksCompilation_getPreferredMemoryAlignmentForOutput(
                        mCompilation, index, alignment));
    };

    Result getPreferredMemoryPaddingForOutput(uint32_t index, uint32_t* padding) const {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksCompilation_getPreferredMemoryPaddingForOutput(
                        mCompilation, index, padding));
    };

    Result addExtensionAttribute(const std::string& extensionName,
                                 uint16_t attributeCodeWithinExtension,
                                 const std::vector<uint8_t>& extensionAttributeData) {
        return static_cast<Result>(
                mNnApi->getFL8()->ANeuralNetworksCompilation_addExtensionAttribute(
                        mCompilation, extensionName.c_str(), attributeCodeWithinExtension,
                        extensionAttributeData.data(), extensionAttributeData.size()));
    }

    ANeuralNetworksCompilation* getHandle() const { return mCompilation; }

   protected:
    // Takes the ownership of ANeuralNetworksCompilation.
    Compilation(const NnApiSupportLibrary* nnapi, ANeuralNetworksCompilation* compilation)
        : mNnApi(nnapi), mCompilation(compilation) {}

    const NnApiSupportLibrary* mNnApi = nullptr;
    ANeuralNetworksCompilation* mCompilation = nullptr;
};

class Execution {
   public:
    Execution(const NnApiSupportLibrary* nnapi, const Compilation* compilation)
        : mNnApi(nnapi), mCompilation(compilation->getHandle()) {
        int result = mNnApi->getFL5()->ANeuralNetworksExecution_create(compilation->getHandle(),
                                                                       &mExecution);
        if (result != 0) {
            // TODO Handle the error
        }
    }

    ~Execution() {
        if (mExecution) {
            mNnApi->getFL5()->ANeuralNetworksExecution_free(mExecution);
        }
    }

    // Disallow copy semantics to ensure the runtime object can only be freed
    // once. Copy semantics could be enabled if some sort of reference counting
    // or deep-copy system for runtime objects is added later.
    Execution(const Execution&) = delete;
    Execution& operator=(const Execution&) = delete;

    // Move semantics to remove access to the runtime object from the wrapper
    // object that is being moved. This ensures the runtime object will be
    // freed only once.
    Execution(Execution&& other) { *this = std::move(other); }
    Execution& operator=(Execution&& other) {
        if (this != &other) {
            if (mExecution != nullptr) {
                mNnApi->getFL5()->ANeuralNetworksExecution_free(mExecution);
            }
            mNnApi = other.mNnApi;
            mCompilation = other.mCompilation;
            mExecution = other.mExecution;
            other.mCompilation = nullptr;
            other.mExecution = nullptr;
        }
        return *this;
    }

    Result setInput(uint32_t index, const void* buffer, size_t length,
                    const ANeuralNetworksOperandType* type = nullptr) {
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksExecution_setInput(
                mExecution, index, type, buffer, length));
    }

    template <typename T>
    Result setInput(uint32_t index, const T* value,
                    const ANeuralNetworksOperandType* type = nullptr) {
        static_assert(!std::is_pointer<T>(), "No operand may have a pointer as its value");
        return setInput(index, value, sizeof(T), type);
    }

    Result setInputFromMemory(uint32_t index, const Memory* memory, uint32_t offset,
                              uint32_t length, const ANeuralNetworksOperandType* type = nullptr) {
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksExecution_setInputFromMemory(
                mExecution, index, type, memory->get(), offset, length));
    }

    Result setOutput(uint32_t index, void* buffer, size_t length,
                     const ANeuralNetworksOperandType* type = nullptr) {
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksExecution_setOutput(
                mExecution, index, type, buffer, length));
    }

    template <typename T>
    Result setOutput(uint32_t index, T* value, const ANeuralNetworksOperandType* type = nullptr) {
        static_assert(!std::is_pointer<T>(), "No operand may have a pointer as its value");
        return setOutput(index, value, sizeof(T), type);
    }

    Result setOutputFromMemory(uint32_t index, const Memory* memory, uint32_t offset,
                               uint32_t length, const ANeuralNetworksOperandType* type = nullptr) {
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksExecution_setOutputFromMemory(
                mExecution, index, type, memory->get(), offset, length));
    }

    Result setLoopTimeout(uint64_t duration) {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksExecution_setLoopTimeout(mExecution, duration));
    }

    Result setMeasureTiming(bool measure) {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksExecution_setMeasureTiming(mExecution, measure));
    }

    Result setTimeout(uint64_t duration) {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksExecution_setTimeout(mExecution, duration));
    }

    Result getDuration(Duration durationCode, uint64_t* duration) {
        return static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksExecution_getDuration(
                mExecution, static_cast<int32_t>(durationCode), duration));
    }

    Result enableInputAndOutputPadding(bool enable) {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksExecution_enableInputAndOutputPadding(mExecution,
                                                                                       enable));
    }

    Result setReusable(bool reusable) {
        return static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksExecution_setReusable(mExecution, reusable));
    }

    // By default, compute() uses the synchronous API. Either an argument or
    // setComputeMode() can be used to change the behavior of compute() to
    // use the burst API
    // Returns the previous ComputeMode.
    enum class ComputeMode { SYNC, BURST, FENCED };
    static ComputeMode setComputeMode(ComputeMode mode) {
        ComputeMode oldComputeMode = mComputeMode;
        mComputeMode = mode;
        return oldComputeMode;
    }
    static ComputeMode getComputeMode() { return mComputeMode; }

    Result compute(ComputeMode computeMode = mComputeMode) {
        switch (computeMode) {
            case ComputeMode::SYNC: {
                return static_cast<Result>(
                        mNnApi->getFL5()->ANeuralNetworksExecution_compute(mExecution));
            }
            case ComputeMode::BURST: {
                ANeuralNetworksBurst* burst = nullptr;
                Result result = static_cast<Result>(
                        mNnApi->getFL5()->ANeuralNetworksBurst_create(mCompilation, &burst));
                if (result != Result::NO_ERROR) {
                    return result;
                }
                result = static_cast<Result>(
                        mNnApi->getFL5()->ANeuralNetworksExecution_burstCompute(mExecution, burst));
                mNnApi->getFL5()->ANeuralNetworksBurst_free(burst);
                return result;
            }
            case ComputeMode::FENCED: {
                ANeuralNetworksEvent* event = nullptr;
                Result result = static_cast<Result>(
                        mNnApi->getFL5()->ANeuralNetworksExecution_startComputeWithDependencies(
                                mExecution, nullptr, 0, 0, &event));
                if (result != Result::NO_ERROR) {
                    return result;
                }
                result = static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksEvent_wait(event));
                mNnApi->getFL5()->ANeuralNetworksEvent_free(event);
                return result;
            }
        }
        return Result::BAD_DATA;
    }

    Result startComputeWithDependencies(const std::vector<const ANeuralNetworksEvent*>& deps,
                                        uint64_t duration, Event* event) {
        ANeuralNetworksEvent* ev = nullptr;
        Result result = static_cast<Result>(
                NNAPI_CALL(ANeuralNetworksExecution_startComputeWithDependencies(
                        mExecution, deps.data(), deps.size(), duration, &ev)));
        event->set(ev);
        return result;
    }

    Result getOutputOperandDimensions(uint32_t index, std::vector<uint32_t>* dimensions) {
        uint32_t rank = 0;
        Result result =
                static_cast<Result>(mNnApi->getFL5()->ANeuralNetworksExecution_getOutputOperandRank(
                        mExecution, index, &rank));
        dimensions->resize(rank);
        if ((result != Result::NO_ERROR && result != Result::OUTPUT_INSUFFICIENT_SIZE) ||
            rank == 0) {
            return result;
        }
        result = static_cast<Result>(
                mNnApi->getFL5()->ANeuralNetworksExecution_getOutputOperandDimensions(
                        mExecution, index, dimensions->data()));
        return result;
    }

    Result addExtensionAttribute(const std::string& extensionName,
                                 uint16_t attributeCodeWithinExtension,
                                 const std::vector<uint8_t>& extensionAttributeData) {
        return static_cast<Result>(mNnApi->getFL8()->ANeuralNetworksExecution_addExtensionAttribute(
                mExecution, extensionName.c_str(), attributeCodeWithinExtension,
                extensionAttributeData.data(), extensionAttributeData.size()));
    }

    ANeuralNetworksExecution* getHandle() { return mExecution; };

   private:
    const NnApiSupportLibrary* mNnApi = nullptr;
    ANeuralNetworksCompilation* mCompilation = nullptr;
    ANeuralNetworksExecution* mExecution = nullptr;

    // Initialized to ComputeMode::SYNC in TestNeuralNetworksWrapper.cpp.
    static ComputeMode mComputeMode;
};

}  // namespace sl_wrapper
}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_SL_SUPPORT_LIBRARY_WRAPPER_H
