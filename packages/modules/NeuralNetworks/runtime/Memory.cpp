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

#define LOG_TAG "Memory"

#include "Memory.h"

#include <CpuExecutor.h>
#include <LegacyUtils.h>
#include <android-base/scopeguard.h>
#include <nnapi/IBurst.h>
#include <nnapi/SharedMemory.h>
#include <nnapi/TypeUtils.h>
#include <nnapi/Types.h>
#include <nnapi/Validation.h>

#include <algorithm>
#include <memory>
#include <set>
#include <tuple>
#include <utility>
#include <vector>

#include "CompilationBuilder.h"
#include "Manager.h"
#include "TypeManager.h"

#ifdef __ANDROID__
#include <android/hardware_buffer.h>
#endif  // __ANDROID__

namespace android {
namespace nn {
namespace {

// The validator for a client-managed single-dimensional memory pool with a known size.
// The memory may be used for request inputs, request outputs, or model constants.
class SizedMemoryValidator : public MemoryValidatorBase {
   public:
    explicit SizedMemoryValidator(uint32_t size) : kSize(size) {}

    bool validate(const CompilationBuilder*, IOType, uint32_t, const ANeuralNetworksOperandType*,
                  uint32_t offset, uint32_t length) const override {
        NN_RET_CHECK(offset + length <= kSize) << "request size larger than the memory size.";
        NN_RET_CHECK(offset != 0 || length != 0) << "memory size cannot be implied.";
        return true;
    }

    Metadata getMetadata() const override { return {.logicalSize = kSize}; }
    bool updateMetadata(const Metadata& metadata) override {
        return metadata.logicalSize == 0 || metadata.logicalSize == kSize;
    }

   private:
    const uint32_t kSize;
};

// The validator for an AHardwareBuffer with Non-BLOB format.
// We require the memory only used for request inputs or request outputs,
// with both offset and length set to zero.
class AHardwareBufferNonBlobValidator : public MemoryValidatorBase {
   public:
    AHardwareBufferNonBlobValidator() = default;

    bool validate(const CompilationBuilder* compilation, IOType, uint32_t,
                  const ANeuralNetworksOperandType*, uint32_t offset,
                  uint32_t length) const override {
        NN_RET_CHECK(compilation != nullptr)
                << "cannot use Non-BLOB AHardwareBuffer as model constant";
        NN_RET_CHECK(offset == 0 && length == 0)
                << "non-zero offset (" << offset << ") and/or length (" << length
                << ") for Non-BLOB format AHardwareBuffer.";
        return true;
    }

    Metadata getMetadata() const override { return {}; }
    bool updateMetadata(const Metadata&) override { return true; }
};

// The validator for a memory created from ANNMemory_createFromDesc.
// We require the memory only used as one of the pre-specified roles,
// with both offset and length set to zero.
class DeviceMemoryValidator : public MemoryValidatorBase {
   public:
    DeviceMemoryValidator(std::set<CompilationRole> roles, Operand operand,
                          std::vector<uint32_t> dimensions)
        : kCompilationRoles(std::move(roles)),
          kOperand(std::move(operand)),
          kInitialDimensions(std::move(dimensions)),
          mUpdatedDimensions(kInitialDimensions) {}

    bool validate(const CompilationBuilder* compilation, IOType ioType, uint32_t index,
                  const ANeuralNetworksOperandType* type, uint32_t offset,
                  uint32_t length) const override {
        NN_RET_CHECK(kCompilationRoles.count({compilation, ioType, index}) > 0)
                << "invalid compilation role.";
        NN_RET_CHECK(offset == 0 && length == 0)
                << "non-zero offset and/or length for driver-allocated memory.";
        if (type) {
            const bool isTensor = TypeManager::get()->isTensorType(kOperand.type);
            NN_RET_CHECK(isTensor || type->dimensionCount == 0)
                    << "invalid dimensions for scalar memory.";
            std::vector<uint32_t> dimensions(type->dimensions,
                                             type->dimensions + type->dimensionCount);
            // We only check against kInitialDimensions here.
            // For input memories, mUpdatedDimensions will be checked in validateInputDimensions
            // at the beginning of a computation.
            const auto combined = combineDimensions(dimensions, kInitialDimensions);
            NN_RET_CHECK(combined.has_value())
                    << "incompatible dimensions between request and memory. (request: "
                    << toString(dimensions) << ", memory: " << toString(kInitialDimensions) << ")";
        }
        return true;
    }

    bool validateInputDimensions(const std::vector<uint32_t>& dimensions) const override {
        NN_RET_CHECK(mInitialized) << "using an uninitialized memory as input";
        NN_RET_CHECK(dimensions == mUpdatedDimensions)
                << "incompatible input dimensions between request and memory. (request: "
                << toString(dimensions) << ", memory: " << toString(mUpdatedDimensions) << ")";
        return true;
    }

    Metadata getMetadata() const override {
        return {.logicalSize = TypeManager::get()->getSizeOfData(kOperand.type, mUpdatedDimensions),
                .dimensions = mUpdatedDimensions,
                .operand = kOperand};
    }

    bool updateMetadata(const Metadata& metadata) override {
        NN_RET_CHECK(!metadata.operand.has_value() ||
                     (metadata.operand->type == kOperand.type &&
                      metadata.operand->scale == kOperand.scale &&
                      metadata.operand->zeroPoint == kOperand.zeroPoint &&
                      metadata.operand->extraParams == kOperand.extraParams));

        NN_RET_CHECK(metadata.dimensions.empty() ||
                     TypeManager::get()->isTensorType(kOperand.type));
        auto combined = combineDimensions(metadata.dimensions, kInitialDimensions);
        NN_RET_CHECK(combined.has_value());
        NN_RET_CHECK(metadata.logicalSize == 0 ||
                     metadata.logicalSize ==
                             TypeManager::get()->getSizeOfData(kOperand.type, combined.value()));
        mUpdatedDimensions = std::move(combined.value());
        return true;
    }

    bool createdWithUnknownShape() const override {
        return TypeManager::get()->getSizeOfData(kOperand.type, kInitialDimensions) == 0;
    }

    void setInitialized(bool initialized) override { mInitialized = initialized; }
    bool isInitialized() const override { return mInitialized; }

   private:
    const std::set<CompilationRole> kCompilationRoles;

    // Keep track of the data type, scale, zero point, and extra parameters of the target operand.
    // Other fields will be ignored, including dimensions, lifetime, location, etc.
    const Operand kOperand;

    // The dimensions of the memory when the memory object is created.
    // May have unknown dimensions or rank.
    const std::vector<uint32_t> kInitialDimensions;

    // The updated dimensions after a successful execution or memory copying.
    std::vector<uint32_t> mUpdatedDimensions;

    bool mInitialized = false;
};

}  // namespace

RuntimeMemory::RuntimeMemory(SharedMemory memory) : kMemory(std::move(memory)) {
    CHECK(kMemory != nullptr);
    mValidator = std::make_unique<SizedMemoryValidator>(nn::getSize(kMemory));
}

RuntimeMemory::RuntimeMemory(SharedMemory memory, std::unique_ptr<MemoryValidatorBase> validator)
    : kMemory(std::move(memory)), mValidator(std::move(validator)) {
    CHECK(kMemory != nullptr);
}

RuntimeMemory::RuntimeMemory(SharedBuffer buffer) : kBuffer(std::move(buffer)) {}

Request::MemoryPool RuntimeMemory::getMemoryPool() const {
    if (kBuffer != nullptr) {
        return kBuffer->getToken();
    }
    return kMemory;
}

std::optional<RunTimePoolInfo> RuntimeMemory::getRunTimePoolInfo() const {
    std::lock_guard<std::mutex> guard(mMutex);
    if (!mHasCachedRunTimePoolInfo) {
        mCachedRunTimePoolInfo = RunTimePoolInfo::createFromMemory(kMemory);
        mHasCachedRunTimePoolInfo = true;
    }
    return mCachedRunTimePoolInfo;
}

void RuntimeMemory::hold(const IBurst::OptionalCacheHold& cacheHold) const {
    if (cacheHold != nullptr) {
        std::lock_guard<std::mutex> guard(mMutex);
        mHold.insert(cacheHold);
    }
}

static int copyHidlMemories(const std::optional<RunTimePoolInfo>& src,
                            const std::optional<RunTimePoolInfo>& dst) {
    if (!src.has_value() || !dst.has_value()) {
        LOG(ERROR) << "ANeuralNetworksMemory_copy -- unable to map memory";
        return ANEURALNETWORKS_UNMAPPABLE;
    }
    if (src->getSize() != dst->getSize()) {
        LOG(ERROR) << "ANeuralNetworksMemory_copy -- incompatible memory size";
        return ANEURALNETWORKS_BAD_DATA;
    }
    CHECK(src->getBuffer() != nullptr);
    CHECK(dst->getBuffer() != nullptr);
    std::copy(src->getBuffer(), src->getBuffer() + src->getSize(), dst->getBuffer());
    dst->flush();
    return ANEURALNETWORKS_NO_ERROR;
}

int copyIBufferToMemory(const SharedBuffer& src, const SharedMemory& dst) {
    const auto ret = src->copyTo(dst);
    if (!ret.has_value()) {
        LOG(ERROR) << "ANeuralNetworksMemory_copy failure: " << ret.error().message;
        return convertErrorStatusToResultCode(ret.error().code);
    }
    return ANEURALNETWORKS_NO_ERROR;
}

int copyMemoryToIBuffer(const SharedMemory& src, const SharedBuffer& dst,
                        const std::vector<uint32_t>& dimensions) {
    const auto ret = dst->copyFrom(src, dimensions);
    if (!ret.has_value()) {
        LOG(ERROR) << "ANeuralNetworksMemory_copy failure: " << ret.error().message;
        return convertErrorStatusToResultCode(ret.error().code);
    }
    return ANEURALNETWORKS_NO_ERROR;
}

static int copyIBuffers(const SharedBuffer& src, const SharedBuffer& dst,
                        const MemoryValidatorBase::Metadata& srcMetadata) {
#ifdef __ANDROID__
    const auto [n, runtimeMemory] = MemoryRuntimeAHWB::create(srcMetadata.logicalSize);
#else   // __ANDROID__
    const auto [n, runtimeMemory] = MemoryAshmem::create(srcMetadata.logicalSize);
#endif  // __ANDROID__
    NN_RETURN_IF_ERROR(n);
    const SharedMemory& memory = runtimeMemory->getMemory();
    if (!validate(memory).ok()) return ANEURALNETWORKS_OUT_OF_MEMORY;
    NN_RETURN_IF_ERROR(copyIBufferToMemory(src, memory));
    NN_RETURN_IF_ERROR(copyMemoryToIBuffer(memory, dst, srcMetadata.dimensions));
    return ANEURALNETWORKS_NO_ERROR;
}

static int copyInternal(const RuntimeMemory& src, const RuntimeMemory& dst) {
    if (&src == &dst) return ANEURALNETWORKS_NO_ERROR;

    if (!src.getValidator().isInitialized()) {
        LOG(ERROR) << "ANeuralNetworksMemory_copy -- uninitialized source memory";
        return ANEURALNETWORKS_BAD_DATA;
    }

    const auto srcMetadata = src.getValidator().getMetadata();
    if (!dst.getValidator().updateMetadata(srcMetadata)) {
        LOG(ERROR) << "ANeuralNetworksMemory_copy -- incompatible memories";
        return ANEURALNETWORKS_BAD_DATA;
    }

    bool srcHasMemory = validate(src.getMemory()).ok();
    bool dstHasMemory = validate(dst.getMemory()).ok();
    bool srcHasIBuffer = src.getIBuffer() != nullptr;
    bool dstHasIBuffer = dst.getIBuffer() != nullptr;
    if (srcHasIBuffer && dstHasIBuffer) {
        return copyIBuffers(src.getIBuffer(), dst.getIBuffer(), srcMetadata);
    } else if (srcHasMemory && dstHasMemory) {
        return copyHidlMemories(src.getRunTimePoolInfo(), dst.getRunTimePoolInfo());
    } else if (srcHasMemory && dstHasIBuffer) {
        return copyMemoryToIBuffer(src.getMemory(), dst.getIBuffer(), srcMetadata.dimensions);
    } else if (srcHasIBuffer && dstHasMemory) {
        return copyIBufferToMemory(src.getIBuffer(), dst.getMemory());
    }
    return ANEURALNETWORKS_OP_FAILED;
}

int RuntimeMemory::copy(const RuntimeMemory& src, const RuntimeMemory& dst) {
    int n = copyInternal(src, dst);
    dst.getValidator().setInitialized(n == ANEURALNETWORKS_NO_ERROR);
    return n;
}

bool MemoryBuilder::badState(const char* name) const {
    if (mFinished) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_" << name << " can't modify after finished";
        return true;
    }
    return false;
}

int MemoryBuilder::addRole(const CompilationBuilder& compilation, IOType ioType, uint32_t index,
                           float prob) {
    const char* tag = ioType == IOType::INPUT ? "addInputRole" : "addOutputRole";
    if (badState(tag)) {
        return ANEURALNETWORKS_BAD_STATE;
    }
    if (mRoles.count({&compilation, ioType, index}) > 0) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_" << tag
                   << " -- the same operand is specified twice.";
        return ANEURALNETWORKS_BAD_DATA;
    }

    std::vector<std::tuple<const RuntimePreparedModel*, IOType, uint32_t>> roles;
    auto callback = [&roles](const auto* preparedModel, IOType type, uint32_t index) {
        roles.emplace_back(preparedModel, type, index);
    };
    if (ioType == IOType::INPUT) {
        if (compilation.forEachStepRoleOfInput(index, callback) != ANEURALNETWORKS_NO_ERROR) {
            return ANEURALNETWORKS_BAD_DATA;
        }
    } else {
        if (compilation.forEachStepRoleOfOutput(index, callback) != ANEURALNETWORKS_NO_ERROR) {
            return ANEURALNETWORKS_BAD_DATA;
        }
    }

    const ModelBuilder* model = compilation.getModel();
    CHECK(model != nullptr);
    Operand operand;
    if (ioType == IOType::INPUT) {
        if (index >= model->inputCount()) {
            LOG(ERROR) << "ANeuralNetworksMemoryDesc_addInputRole -- input index out of range.";
            return ANEURALNETWORKS_BAD_DATA;
        }
        operand = model->getInputOperand(index);
    } else {
        if (index >= model->outputCount()) {
            LOG(ERROR) << "ANeuralNetworksMemoryDesc_addOutputRole -- output index out of range.";
            return ANEURALNETWORKS_BAD_DATA;
        }
        operand = model->getOutputOperand(index);
    }
    if (mOperand.has_value()) {
        if (operand.type != mOperand->type || operand.scale != mOperand->scale ||
            operand.zeroPoint != mOperand->zeroPoint ||
            operand.extraParams != mOperand->extraParams) {
            LOG(ERROR) << "ANeuralNetworksMemoryDesc_" << tag
                       << " -- incompatible operand metadata.";
            return ANEURALNETWORKS_BAD_DATA;
        }
    }
    if (!TypeManager::get()->isTensorType(operand.type) && !mDesc.dimensions.empty()) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_" << tag << " -- incompatible dimensions.";
        return ANEURALNETWORKS_BAD_DATA;
    }
    auto combined = combineDimensions(mDesc.dimensions, operand.dimensions);
    if (!combined.has_value()) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_" << tag << " -- incompatible dimensions.";
        return ANEURALNETWORKS_BAD_DATA;
    }

    if (prob > 1.0f || prob <= 0.0f) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_" << tag << " -- invalid frequency " << prob;
        return ANEURALNETWORKS_BAD_DATA;
    }

    mRoles.emplace(&compilation, ioType, index);
    for (const auto& [preparedModel, type, ind] : roles) {
        uint32_t modelIndex = mDesc.preparedModels.add(preparedModel);
        BufferRole role = {.modelIndex = modelIndex, .ioIndex = ind, .probability = prob};
        if (type == IOType::INPUT) {
            mDesc.inputRoles.push_back(role);
        } else {
            mDesc.outputRoles.push_back(role);
        }
    }
    mOperand = std::move(operand);
    mDesc.dimensions = std::move(combined.value());
    return ANEURALNETWORKS_NO_ERROR;
}

int MemoryBuilder::setDimensions(const std::vector<uint32_t>& dimensions) {
    if (badState("setDimensions")) return ANEURALNETWORKS_BAD_STATE;
    if (mOperand.has_value() && !TypeManager::get()->isTensorType(mOperand->type) &&
        !dimensions.empty()) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_setDimensions -- incompatible dimensions for "
                      "scalars.";
        return ANEURALNETWORKS_BAD_DATA;
    }
    auto combined = combineDimensions(mDesc.dimensions, dimensions);
    if (!combined.has_value()) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_setDimensions -- incompatible dimensions.";
        return ANEURALNETWORKS_BAD_DATA;
    }
    mDesc.dimensions = std::move(combined.value());
    return ANEURALNETWORKS_NO_ERROR;
}

static void logMemoryDescriptorToInfo(const MemoryDescriptor& desc, const Operand& operand) {
    LOG(INFO) << "MemoryDescriptor start";
    LOG(INFO) << "    Data type: " << operand.type;
    LOG(INFO) << "    Scale: " << operand.scale;
    LOG(INFO) << "    Zero point: " << operand.zeroPoint;
    LOG(INFO) << "    Extra params: " << operand.extraParams;
    LOG(INFO) << "    Dimensions: " << toString(desc.dimensions);
    LOG(INFO) << "    Prepared models [" << desc.preparedModels.size() << "]:";
    for (const auto* preparedModel : desc.preparedModels) {
        LOG(INFO) << "        service = " << preparedModel->getDevice()->getName();
    }
    LOG(INFO) << "    Input roles [" << desc.inputRoles.size() << "]:";
    for (const auto& usage : desc.inputRoles) {
        LOG(INFO) << "        " << usage;
    }
    LOG(INFO) << "    Output roles [" << desc.outputRoles.size() << "]:";
    for (const auto& usage : desc.outputRoles) {
        LOG(INFO) << "        " << usage;
    }
    LOG(INFO) << "MemoryDescriptor end";
}

static std::set<const Device*> getDevices(const MemoryDescriptor& desc) {
    std::set<const Device*> devices;
    for (const auto* preparedModel : desc.preparedModels) {
        const auto* device = preparedModel->getDevice();
        devices.insert(device);
    }
    return devices;
}

int MemoryBuilder::finish() {
    if (badState("finish")) return ANEURALNETWORKS_BAD_STATE;
    if (mRoles.empty()) {
        LOG(ERROR) << "ANeuralNetworksMemoryDesc_finish -- no role has been specified.";
        return ANEURALNETWORKS_BAD_DATA;
    }
    CHECK(mOperand.has_value());
    if (VLOG_IS_ON(MEMORY)) {
        logMemoryDescriptorToInfo(mDesc, mOperand.value());
    }
    std::set<const Device*> devices = getDevices(mDesc);
    if (devices.empty()) {
        // This can happen with interpreted control flow.
        mAllocator = nullptr;
    } else if (devices.size() == 1) {
        mAllocator = *devices.begin();
        VLOG(MEMORY) << "Using " << mAllocator->getName() << " as allocator.";
    } else {
        LOG(INFO) << "MemoryBuilder::finish -- cannot handle multiple devices.";
        mAllocator = nullptr;
    }
#ifdef __ANDROID__
    mSupportsAhwb = std::all_of(devices.begin(), devices.end(), [](const auto* device) {
        return isCompliantVersion(kHalVersionV1_3ToApi.canonical, device->getFeatureLevel());
    });
#else   // __ANDROID__
    mSupportsAhwb = false;
#endif  // __ANDROID__
    mShouldFallback = std::none_of(mRoles.begin(), mRoles.end(), [](const auto& role) {
        const auto* cb = std::get<const CompilationBuilder*>(role);
        return cb->createdWithExplicitDeviceList();
    });
    const uint32_t size = TypeManager::get()->getSizeOfData(mOperand->type, mDesc.dimensions);
    mShouldFallback &= (size != 0);
    mFinished = true;
    return ANEURALNETWORKS_NO_ERROR;
}

std::pair<int, std::unique_ptr<RuntimeMemory>> MemoryBuilder::allocate() const {
    if (!mFinished) {
        LOG(ERROR) << "ANeuralNetworksMemory_createFromDesc -- passed an unfinished descriptor";
        return {ANEURALNETWORKS_BAD_STATE, nullptr};
    }

    int n = ANEURALNETWORKS_OP_FAILED;
    std::unique_ptr<RuntimeMemory> memory;
    CHECK(mOperand.has_value());

    // Try allocate the memory on device.
    if (mAllocator != nullptr) {
        std::tie(n, memory) = mAllocator->allocate(mDesc, mOperand->type);
    }

    // If failed, fallback to ashmem or BLOB mode AHWB.
    if (n != ANEURALNETWORKS_NO_ERROR && mShouldFallback) {
        const uint32_t size = TypeManager::get()->getSizeOfData(mOperand->type, mDesc.dimensions);
        if (mSupportsAhwb) {
            VLOG(MEMORY) << "MemoryBuilder::allocate -- fallback to BLOB mode AHWB.";
            std::tie(n, memory) = MemoryRuntimeAHWB::create(size);
        } else {
            VLOG(MEMORY) << "MemoryBuilder::allocate -- fallback to ashmem.";
            std::tie(n, memory) = MemoryAshmem::create(size);
        }
    }

    if (n == ANEURALNETWORKS_NO_ERROR) {
        CHECK(memory != nullptr);
        auto validator =
                std::make_unique<DeviceMemoryValidator>(mRoles, mOperand.value(), mDesc.dimensions);
        memory->setValidator(std::move(validator));
    }
    return {n, std::move(memory)};
}

std::pair<int, std::unique_ptr<MemoryAshmem>> MemoryAshmem::create(uint32_t size) {
    auto memory = createSharedMemory(size);
    if (!memory.has_value()) {
        LOG(ERROR) << "RuntimeMemory::create() failed: " << memory.error().message;
        return {convertErrorStatusToResultCode(memory.error().code), nullptr};
    }
    auto mapping = map(memory.value());
    if (!mapping.has_value()) {
        LOG(ERROR) << "RuntimeMemory::create() map failed: " << mapping.error().message;
        return {convertErrorStatusToResultCode(mapping.error().code), nullptr};
    }
    return {ANEURALNETWORKS_NO_ERROR,
            std::make_unique<MemoryAshmem>(std::move(memory).value(), std::move(mapping).value())};
}

uint8_t* MemoryAshmem::getPointer() const {
    return static_cast<uint8_t*>(std::get<void*>(kMapping.pointer));
}

MemoryAshmem::MemoryAshmem(SharedMemory memory, Mapping mapping)
    : RuntimeMemory(std::move(memory)), kMapping(std::move(mapping)) {}

std::pair<int, std::unique_ptr<MemoryFd>> MemoryFd::create(size_t size, int prot, int fd,
                                                           size_t offset) {
    auto memory = createSharedMemoryFromFd(size, prot, fd, offset);
    if (!memory.has_value()) {
        LOG(ERROR) << "Failed to create memory from fd: " << memory.error().message;
        return {convertErrorStatusToResultCode(memory.error().code), nullptr};
    }
    return {ANEURALNETWORKS_NO_ERROR, std::make_unique<MemoryFd>(std::move(memory).value())};
}

MemoryFd::MemoryFd(SharedMemory memory) : RuntimeMemory(std::move(memory)) {}

std::pair<int, std::unique_ptr<MemoryAHWB>> MemoryAHWB::create(const AHardwareBuffer& ahwb) {
#ifdef __ANDROID__
    auto memory = createSharedMemoryFromAHWB(const_cast<AHardwareBuffer*>(&ahwb),
                                             /*takeOwnership=*/false);
    if (!memory.has_value()) {
        LOG(ERROR) << "Failed to create memory from AHWB: " << memory.error().message;
        return {convertErrorStatusToResultCode(memory.error().code), nullptr};
    }

    std::unique_ptr<MemoryValidatorBase> validator;
    if (isAhwbBlob(memory.value())) {
        validator = std::make_unique<SizedMemoryValidator>(nn::getSize(memory.value()));
    } else {
        validator = std::make_unique<AHardwareBufferNonBlobValidator>();
    }

    auto memoryAHWB = std::make_unique<MemoryAHWB>(std::move(memory).value(), std::move(validator));
    return {ANEURALNETWORKS_NO_ERROR, std::move(memoryAHWB)};
#else   // __ANDROID__
    LOG(FATAL) << "std::pair<int, std::unique_ptr<MemoryAHWB>> MemoryAHWB::create(const "
                  "AHardwareBuffer& ahwb): Not Available on Host Build";
    (void)ahwb;
    return {ANEURALNETWORKS_OP_FAILED, nullptr};
#endif  // __ANDROID__
}

std::pair<int, std::unique_ptr<MemoryRuntimeAHWB>> MemoryRuntimeAHWB::create(uint32_t size) {
#ifdef __ANDROID__
    AHardwareBuffer* ahwb = nullptr;
    const auto usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;
    const AHardwareBuffer_Desc desc = {
            .width = size,
            .height = 1,
            .layers = 1,
            .format = AHARDWAREBUFFER_FORMAT_BLOB,
            .usage = usage,
            .stride = size,
    };
    int err = AHardwareBuffer_allocate(&desc, &ahwb);
    if (err != 0 || ahwb == nullptr) {
        LOG(ERROR) << "Failed to allocate BLOB mode AHWB.";
        return {ANEURALNETWORKS_OP_FAILED, nullptr};
    }

    auto memory = createSharedMemoryFromAHWB(ahwb, /*takeOWnership=*/true);
    if (!memory.has_value()) {
        LOG(ERROR) << "Failed to allocate BLOB mode AHWB: " << memory.error().message;
        return {convertErrorStatusToResultCode(memory.error().code), nullptr};
    }
    auto mapping = map(memory.value());
    if (!mapping.has_value()) {
        LOG(ERROR) << "Failed to map BLOB mode AHWB: " << mapping.error().message;
        return {convertErrorStatusToResultCode(mapping.error().code), nullptr};
    }
    auto memoryAHWB = std::make_unique<MemoryRuntimeAHWB>(std::move(memory).value(),
                                                          std::move(mapping).value());
    return {ANEURALNETWORKS_NO_ERROR, std::move(memoryAHWB)};
#else   // __ANDROID__
    LOG(FATAL) << "std::pair<int, std::unique_ptr<MemoryRuntimeAHWB>> "
                  "MemoryRuntimeAHWB::create(uint32_t size): Not Available on Host Build";
    (void)size;
    return {ANEURALNETWORKS_OP_FAILED, nullptr};
#endif  // __ANDROID__
}

uint8_t* MemoryRuntimeAHWB::getPointer() const {
    return static_cast<uint8_t*>(std::get<void*>(kMapping.pointer));
}

MemoryRuntimeAHWB::MemoryRuntimeAHWB(SharedMemory memory, Mapping mapping)
    : RuntimeMemory(std::move(memory)), kMapping(std::move(mapping)) {}

std::pair<int, std::unique_ptr<MemoryFromDevice>> MemoryFromDevice::create(SharedBuffer buffer) {
    if (buffer == nullptr) {
        LOG(ERROR) << "nullptr IBuffer for device memory.";
        return {ANEURALNETWORKS_OP_FAILED, nullptr};
    }
    return {ANEURALNETWORKS_NO_ERROR, std::make_unique<MemoryFromDevice>(std::move(buffer))};
}

MemoryFromDevice::MemoryFromDevice(SharedBuffer buffer) : RuntimeMemory(std::move(buffer)) {}

}  // namespace nn
}  // namespace android
