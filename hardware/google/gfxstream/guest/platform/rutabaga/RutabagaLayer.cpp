/*
 * Copyright 2023 The Android Open Source Project
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

#include "RutabagaLayer.h"

#include <inttypes.h>
#include <log/log.h>

#include <algorithm>
#include <cstdlib>
#include <future>
#include <memory>
#include <optional>
#include <queue>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
#include <variant>

// Blueprint and Meson builds place things differently
#if defined(ANDROID)
#include "rutabaga_gfx_ffi.h"
#else
#include "rutabaga_gfx/rutabaga_gfx_ffi.h"
#endif

namespace gfxstream {
namespace {

constexpr const uint32_t kInvalidContextId = 0;

std::vector<std::string> Split(const std::string& s, const std::string& delimiters) {
    if (delimiters.empty()) {
        return {};
    }

    std::vector<std::string> result;

    size_t base = 0;
    size_t found;
    while (true) {
        found = s.find_first_of(delimiters, base);
        result.push_back(s.substr(base, found - base));
        if (found == s.npos) break;
        base = found + 1;
    }

    return result;
}

std::string Join(const std::vector<std::string>& things, const std::string& separator) {
    if (things.empty()) {
        return "";
    }

    std::ostringstream result;
    result << *things.begin();
    for (auto it = std::next(things.begin()); it != things.end(); ++it) {
        result << separator << *it;
    }
    return result.str();
}

}  // namespace

class EmulatedVirtioGpu::EmulatedVirtioGpuImpl {
   public:
    EmulatedVirtioGpuImpl();
    ~EmulatedVirtioGpuImpl();

    bool Init(bool withGl, bool withVk, bool withVkSnapshots, EmulatedVirtioGpu* parent);

    bool GetCaps(uint32_t capsetId, uint32_t guestCapsSize, uint8_t* capset);

    std::optional<uint32_t> CreateContext(uint32_t contextInit);
    void DestroyContext(uint32_t contextId);

    uint8_t* Map(uint32_t resourceId);
    void Unmap(uint32_t resourceId);

    int SubmitCmd(uint32_t contextId, uint32_t cmdSize, void* cmd, uint32_t ringIdx,
                  VirtioGpuFenceFlags fenceFlags, uint32_t& fenceId,
                  std::optional<uint32_t> blobResourceId);

    int Wait(uint32_t resourceId);

    int TransferFromHost(uint32_t contextId, uint32_t resourceId, uint32_t transferOffset,
                         uint32_t transferSize);
    int TransferToHost(uint32_t contextId, uint32_t resourceId, uint32_t transferOffset,
                       uint32_t transferSize);

    std::optional<uint32_t> CreateBlob(uint32_t contextId, uint32_t blobMem, uint32_t blobFlags,
                                       uint64_t blobId, uint64_t blobSize);
    std::optional<uint32_t> CreateVirglBlob(uint32_t contextId, uint32_t width, uint32_t height,
                                            uint32_t virglFormat, uint32_t target, uint32_t bind,
                                            uint32_t size);

    void DestroyResource(uint32_t contextId, uint32_t resourceId);
    void SnapshotSave(const std::string& directory);
    void SnapshotRestore(const std::string& directory);

    uint32_t CreateEmulatedFence();

    void SignalEmulatedFence(uint32_t fenceId);

    int WaitOnEmulatedFence(int fenceAsFileDescriptor, int timeoutMilliseconds);

   private:
    struct VirtioGpuTaskContextAttachResource {
        uint32_t contextId;
        uint32_t resourceId;
    };
    struct VirtioGpuTaskContextDetachResource {
        uint32_t contextId;
        uint32_t resourceId;
    };
    struct VirtioGpuTaskCreateContext {
        uint32_t contextId;
        uint32_t contextInit;
        std::string contextName;
    };
    struct VirtioGpuTaskCreateBlob {
        uint32_t contextId;
        uint32_t resourceId;
        struct rutabaga_create_blob params;
    };
    struct VirtioGpuTaskCreateResource {
        uint32_t contextId;
        uint32_t resourceId;
        uint8_t* resourceBytes;
        struct rutabaga_create_3d params;
    };
    struct VirtioGpuTaskDestroyContext {
        uint32_t contextId;
    };
    struct VirtioGpuTaskMap {
        uint32_t resourceId;
        std::promise<uint8_t*> resourceMappedPromise;
    };
    struct VirtioGpuTaskSubmitCmd {
        uint32_t contextId;
        std::vector<std::byte> commandBuffer;
    };
    struct VirtioGpuTaskTransferToHost {
        uint32_t contextId;
        uint32_t resourceId;
        uint32_t transferOffset;
        uint32_t transferSize;
    };
    struct VirtioGpuTaskTransferFromHost {
        uint32_t contextId;
        uint32_t resourceId;
        uint32_t transferOffset;
        uint32_t transferSize;
    };
    struct VirtioGpuTaskUnrefResource {
        uint32_t resourceId;
    };
    struct VirtioGpuTaskSnapshotSave {
        std::string directory;
    };
    struct VirtioGpuTaskSnapshotRestore {
        std::string directory;
    };
    using VirtioGpuTask =
        std::variant<VirtioGpuTaskContextAttachResource, VirtioGpuTaskContextDetachResource,
                     VirtioGpuTaskCreateBlob, VirtioGpuTaskCreateContext,
                     VirtioGpuTaskCreateResource, VirtioGpuTaskDestroyContext, VirtioGpuTaskMap,
                     VirtioGpuTaskSubmitCmd, VirtioGpuTaskTransferFromHost,
                     VirtioGpuTaskTransferToHost, VirtioGpuTaskUnrefResource,
                     VirtioGpuTaskSnapshotSave, VirtioGpuTaskSnapshotRestore>;

    struct VirtioGpuFence {
        uint32_t fenceId;
        uint32_t ringIdx;
    };

    struct VirtioGpuTaskWithWaitable {
        uint32_t contextId;
        VirtioGpuTask task;
        std::promise<void> taskCompletedSignaler;
        std::optional<VirtioGpuFence> fence;
    };

    std::shared_future<void> EnqueueVirtioGpuTask(
        uint32_t contextId, VirtioGpuTask task, std::optional<VirtioGpuFence> fence = std::nullopt);
    void DoTask(VirtioGpuTaskContextAttachResource task);
    void DoTask(VirtioGpuTaskContextDetachResource task);
    void DoTask(VirtioGpuTaskCreateContext task);
    void DoTask(VirtioGpuTaskCreateBlob task);
    void DoTask(VirtioGpuTaskCreateResource task);
    void DoTask(VirtioGpuTaskDestroyContext task);
    void DoTask(VirtioGpuTaskMap task);
    void DoTask(VirtioGpuTaskSubmitCmd task);
    void DoTask(VirtioGpuTaskTransferFromHost task);
    void DoTask(VirtioGpuTaskTransferToHost task);
    void DoTask(VirtioGpuTaskWithWaitable task);
    void DoTask(VirtioGpuTaskUnrefResource task);
    void DoTask(VirtioGpuTaskSnapshotSave task);
    void DoTask(VirtioGpuTaskSnapshotRestore task);

    void RunVirtioGpuTaskProcessingLoop();

    std::atomic<uint32_t> mNextContextId{1};
    std::atomic<uint32_t> mNextVirtioGpuResourceId{1};
    std::atomic<uint32_t> mNextVirtioGpuFenceId{1};

    std::atomic_bool mShuttingDown{false};

    std::mutex mTasksMutex;
    std::queue<VirtioGpuTaskWithWaitable> mTasks;

    enum class EmulatedResourceType {
        kBlob,
        kPipe,
    };
    struct EmulatedResource {
        EmulatedResourceType type;

        std::mutex pendingWaitablesMutex;
        std::vector<std::shared_future<void>> pendingWaitables;

        // For non-blob resources, the guest shadow memory.
        std::unique_ptr<uint8_t[]> guestBytes;

        // For mappable blob resources, the host memory once it is mapped.
        std::shared_future<uint8_t*> mappedHostBytes;

        // For resources with iovecs.  Test layer just needs 1.
        struct iovec iovec;
    };
    std::mutex mResourcesMutex;
    std::unordered_map<uint32_t, EmulatedResource> mResources;

    EmulatedResource* CreateResource(uint32_t resourceId, EmulatedResourceType resourceType) {
        std::lock_guard<std::mutex> lock(mResourcesMutex);

        auto [it, created] = mResources.emplace(
            std::piecewise_construct, std::forward_as_tuple(resourceId), std::forward_as_tuple());
        if (!created) {
            ALOGE("Created resource %" PRIu32 " twice?", resourceId);
        }

        EmulatedResource* resource = &it->second;
        resource->type = resourceType;
        return resource;
    }

    EmulatedResource* GetResource(uint32_t resourceId) {
        std::lock_guard<std::mutex> lock(mResourcesMutex);

        auto it = mResources.find(resourceId);
        if (it == mResources.end()) {
            return nullptr;
        }

        return &it->second;
    }

    void DeleteResource(uint32_t resourceId) {
        std::lock_guard<std::mutex> lock(mResourcesMutex);
        mResources.erase(resourceId);
    }

    struct EmulatedFence {
        std::promise<void> signaler;
        std::shared_future<void> waitable;
    };
    std::mutex mVirtioGpuFencesMutex;
    std::unordered_map<uint32_t, EmulatedFence> mVirtioGpuFences;

    std::thread mWorkerThread;
    struct rutabaga* mRutabaga = nullptr;
    std::unordered_map<uint32_t, std::vector<uint8_t>> mCapsets;
};

EmulatedVirtioGpu::EmulatedVirtioGpuImpl::EmulatedVirtioGpuImpl()
    : mWorkerThread([this]() { RunVirtioGpuTaskProcessingLoop(); }) {}

EmulatedVirtioGpu::EmulatedVirtioGpuImpl::~EmulatedVirtioGpuImpl() {
    mShuttingDown = true;
    mWorkerThread.join();

    if (mRutabaga) {
        rutabaga_finish(&mRutabaga);
        mRutabaga = nullptr;
    }
}

namespace {

void WriteFenceTrampoline(uint64_t cookie, const struct rutabaga_fence* fence) {
    auto* gpu = reinterpret_cast<EmulatedVirtioGpu*>(cookie);
    gpu->SignalEmulatedFence(fence->fence_id);
}

}  // namespace

bool EmulatedVirtioGpu::EmulatedVirtioGpuImpl::Init(bool withGl, bool withVk, bool withVkSnapshots,
                                                    EmulatedVirtioGpu* parent) {
    int32_t ret;
    uint64_t capsetMask = 0;
    uint32_t numCapsets = 0;
    struct rutabaga_builder builder = {0};

    ret = setenv("ANDROID_GFXSTREAM_CAPTURE_VK_SNAPSHOT", withVkSnapshots ? "1" : "0",
                 1 /* replace */);
    if (ret) {
        ALOGE("Failed to set environment variable");
        return false;
    }

    if (withGl) {
        capsetMask |= (1 << RUTABAGA_CAPSET_GFXSTREAM_GLES);
    }

    if (withVk) {
        capsetMask |= (1 << RUTABAGA_CAPSET_GFXSTREAM_VULKAN);
    }

    builder.user_data = static_cast<uint64_t>(reinterpret_cast<uintptr_t>(parent)),
    builder.fence_cb = WriteFenceTrampoline;
    builder.capset_mask = capsetMask;
    builder.wsi = RUTABAGA_WSI_SURFACELESS;

    ret = rutabaga_init(&builder, &mRutabaga);
    if (ret) {
        ALOGE("Failed to initialize rutabaga");
        return false;
    }

    ret = rutabaga_get_num_capsets(mRutabaga, &numCapsets);
    if (ret) {
        ALOGE("Failed to get number of capsets");
        return false;
    }

    for (uint32_t i = 0; i < numCapsets; i++) {
        uint32_t capsetId, capsetVersion, capsetSize;
        std::vector<uint8_t> capsetData;

        ret = rutabaga_get_capset_info(mRutabaga, i, &capsetId, &capsetVersion, &capsetSize);
        if (ret) {
            ALOGE("Failed to get capset info");
            return false;
        }

        capsetData.resize(capsetSize);

        ret = rutabaga_get_capset(mRutabaga, capsetId, capsetVersion, (uint8_t*)capsetData.data(),
                                  capsetSize);

        if (ret) {
            ALOGE("Failed to get capset");
            return false;
        }

        mCapsets[capsetId] = capsetData;
    }

    return true;
}

bool EmulatedVirtioGpu::EmulatedVirtioGpuImpl::GetCaps(uint32_t capsetId, uint32_t guestCapsSize,
                                                       uint8_t* capset) {
    if (mCapsets.count(capsetId) == 0) return false;

    auto capsetData = mCapsets[capsetId];
    auto copySize = std::min(static_cast<size_t>(guestCapsSize), capsetData.size());
    std::memcpy(capset, capsetData.data(), copySize);

    return true;
}

std::optional<uint32_t> EmulatedVirtioGpu::EmulatedVirtioGpuImpl::CreateContext(
    uint32_t contextInit) {
    const uint32_t contextId = mNextContextId++;

    VirtioGpuTaskCreateContext task = {
        .contextId = contextId,
        .contextInit = contextInit,
        .contextName = "EmulatedVirtioGpu Context " + std::to_string(contextId),
    };
    EnqueueVirtioGpuTask(contextId, std::move(task));
    return contextId;
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DestroyContext(uint32_t contextId) {
    VirtioGpuTaskDestroyContext task = {
        .contextId = contextId,
    };
    EnqueueVirtioGpuTask(contextId, std::move(task));
}

uint8_t* EmulatedVirtioGpu::EmulatedVirtioGpuImpl::Map(uint32_t resourceId) {
    EmulatedResource* resource = GetResource(resourceId);
    if (resource == nullptr) {
        ALOGE("Failed to Map() resource %" PRIu32 ": not found.", resourceId);
        return nullptr;
    }

    uint8_t* mapped = nullptr;
    if (resource->type == EmulatedResourceType::kBlob) {
        if (!resource->mappedHostBytes.valid()) {
            ALOGE("Failed to Map() resource %" PRIu32
                  ": attempting to map blob "
                  "without mappable flag?",
                  resourceId);
            return nullptr;
        }
        mapped = resource->mappedHostBytes.get();
    } else if (resource->type == EmulatedResourceType::kPipe) {
        mapped = resource->guestBytes.get();
    }
    return mapped;
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::Unmap(uint32_t resourceId) {
    rutabaga_resource_unmap(mRutabaga, resourceId);
}

int EmulatedVirtioGpu::EmulatedVirtioGpuImpl::Wait(uint32_t resourceId) {
    EmulatedResource* resource = GetResource(resourceId);
    if (resource == nullptr) {
        ALOGE("Failed to Wait() on resource %" PRIu32 ": not found.", resourceId);
        return -1;
    }

    std::vector<std::shared_future<void>> pendingWaitables;
    {
        std::lock_guard<std::mutex> lock(resource->pendingWaitablesMutex);
        pendingWaitables = resource->pendingWaitables;
        resource->pendingWaitables.clear();
    }
    for (auto& waitable : pendingWaitables) {
        waitable.wait();
    }

    return 0;
}

int EmulatedVirtioGpu::EmulatedVirtioGpuImpl::TransferFromHost(uint32_t contextId,
                                                               uint32_t resourceId,
                                                               uint32_t transferOffset,
                                                               uint32_t transferSize) {
    EmulatedResource* resource = GetResource(resourceId);
    if (resource == nullptr) {
        ALOGE("Failed to TransferFromHost() on resource %" PRIu32 ": not found.", resourceId);
        return -1;
    }

    VirtioGpuTaskTransferFromHost task = {
        .contextId = contextId,
        .resourceId = resourceId,
        .transferOffset = transferOffset,
        .transferSize = transferSize,
    };
    auto waitable = EnqueueVirtioGpuTask(contextId, std::move(task));

    {
        std::lock_guard<std::mutex> lock(resource->pendingWaitablesMutex);
        resource->pendingWaitables.push_back(std::move(waitable));
    }

    return 0;
}

int EmulatedVirtioGpu::EmulatedVirtioGpuImpl::TransferToHost(uint32_t contextId,
                                                             uint32_t resourceId,
                                                             uint32_t transferOffset,
                                                             uint32_t transferSize) {
    EmulatedResource* resource = GetResource(resourceId);
    if (resource == nullptr) {
        ALOGE("Failed to TransferFromHost() on resource %" PRIu32 ": not found.", resourceId);
        return -1;
    }

    VirtioGpuTaskTransferToHost task = {
        .contextId = contextId,
        .resourceId = resourceId,
        .transferOffset = transferOffset,
        .transferSize = transferSize,
    };
    auto waitable = EnqueueVirtioGpuTask(contextId, std::move(task));

    {
        std::lock_guard<std::mutex> lock(resource->pendingWaitablesMutex);
        resource->pendingWaitables.push_back(std::move(waitable));
    }

    return 0;
}

std::optional<uint32_t> EmulatedVirtioGpu::EmulatedVirtioGpuImpl::CreateBlob(
    uint32_t contextId, uint32_t blobMem, uint32_t blobFlags, uint64_t blobId, uint64_t blobSize) {
    const uint32_t resourceId = mNextVirtioGpuResourceId++;

    ALOGV("Enquing task to create blob resource-id:%d size:%" PRIu64, resourceId, blobSize);

    EmulatedResource* resource = CreateResource(resourceId, EmulatedResourceType::kBlob);

    VirtioGpuTaskCreateBlob createTask{
        .contextId = contextId,
        .resourceId = resourceId,
        .params =
            {
                .blob_mem = blobMem,
                .blob_flags = blobFlags,
                .blob_id = blobId,
                .size = blobSize,
            },
    };
    auto createBlobCompletedWaitable = EnqueueVirtioGpuTask(contextId, std::move(createTask));
    resource->pendingWaitables.push_back(std::move(createBlobCompletedWaitable));

    if (blobFlags & 0x001 /*kBlobFlagMappable*/) {
        std::promise<uint8_t*> mappedBytesPromise;
        std::shared_future<uint8_t*> mappedBytesWaitable = mappedBytesPromise.get_future();

        VirtioGpuTaskMap mapTask{
            .resourceId = resourceId,
            .resourceMappedPromise = std::move(mappedBytesPromise),
        };
        EnqueueVirtioGpuTask(contextId, std::move(mapTask));
        resource->mappedHostBytes = std::move(mappedBytesWaitable);
    }

    VirtioGpuTaskContextAttachResource attachTask{
        .contextId = contextId,
        .resourceId = resourceId,
    };
    EnqueueVirtioGpuTask(contextId, std::move(attachTask));

    return resourceId;
}

std::optional<uint32_t> EmulatedVirtioGpu::EmulatedVirtioGpuImpl::CreateVirglBlob(
    uint32_t contextId, uint32_t width, uint32_t height, uint32_t virglFormat, uint32_t target,
    uint32_t bind, uint32_t size) {
    const uint32_t resourceId = mNextVirtioGpuResourceId++;

    EmulatedResource* resource = CreateResource(resourceId, EmulatedResourceType::kPipe);

    resource->guestBytes = std::make_unique<uint8_t[]>(size);

    VirtioGpuTaskCreateResource task{
        .contextId = contextId,
        .resourceId = resourceId,
        .resourceBytes = resource->guestBytes.get(),
        .params =
            {
                .target = target,
                .format = virglFormat,
                .bind = bind,
                .width = width,
                .height = height,
                .depth = 1,
                .array_size = 1,
                .last_level = 0,
                .nr_samples = 0,
                .flags = 0,
            },
    };
    auto taskCompletedWaitable = EnqueueVirtioGpuTask(contextId, std::move(task));
    resource->pendingWaitables.push_back(std::move(taskCompletedWaitable));

    VirtioGpuTaskContextAttachResource attachTask{
        .contextId = contextId,
        .resourceId = resourceId,
    };
    EnqueueVirtioGpuTask(contextId, std::move(attachTask));

    return resourceId;
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DestroyResource(uint32_t contextId,
                                                               uint32_t resourceId) {
    DeleteResource(resourceId);

    VirtioGpuTaskUnrefResource unrefTask{
        .resourceId = resourceId,
    };
    EnqueueVirtioGpuTask(contextId, std::move(unrefTask));

    VirtioGpuTaskContextDetachResource detachTask{
        .contextId = contextId,
        .resourceId = resourceId,
    };
    EnqueueVirtioGpuTask(contextId, std::move(detachTask));
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::SnapshotSave(const std::string& directory) {
    uint32_t contextId = 0;

    VirtioGpuTaskSnapshotSave saveTask{
        .directory = directory,
    };
    EnqueueVirtioGpuTask(contextId, std::move(saveTask));
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::SnapshotRestore(const std::string& directory) {
    uint32_t contextId = 0;

    VirtioGpuTaskSnapshotRestore restoreTask{
        .directory = directory,
    };

    EnqueueVirtioGpuTask(contextId, std::move(restoreTask));
}

int EmulatedVirtioGpu::EmulatedVirtioGpuImpl::SubmitCmd(uint32_t contextId, uint32_t cmdSize,
                                                        void* cmd, uint32_t ringIdx,
                                                        VirtioGpuFenceFlags fenceFlags,
                                                        uint32_t& fenceId,
                                                        std::optional<uint32_t> blobResourceId) {
    std::optional<VirtioGpuFence> fence;
    if (fenceFlags & kFlagFence) {
        fence = VirtioGpuFence{.fenceId = CreateEmulatedFence(), .ringIdx = ringIdx};
    }

    const VirtioGpuTaskSubmitCmd task = {
        .contextId = contextId,
        .commandBuffer = std::vector<std::byte>(reinterpret_cast<std::byte*>(cmd),
                                                reinterpret_cast<std::byte*>(cmd) + cmdSize),
    };
    auto taskCompletedWaitable = EnqueueVirtioGpuTask(contextId, std::move(task), fence);

    if (blobResourceId) {
        EmulatedResource* resource = GetResource(*blobResourceId);
        if (resource == nullptr) {
            ALOGE("Failed to SubmitCmd() with resource %" PRIu32 ": not found.", *blobResourceId);
            return -1;
        }

        {
            std::lock_guard<std::mutex> lock(resource->pendingWaitablesMutex);
            resource->pendingWaitables.push_back(std::move(taskCompletedWaitable));
        }
    }

    if (fenceFlags & kFlagFence) {
        fenceId = (*fence).fenceId;
    }

    return 0;
}

int EmulatedVirtioGpu::EmulatedVirtioGpuImpl::WaitOnEmulatedFence(int fenceAsFileDescriptor,
                                                                  int timeoutMilliseconds) {
    uint32_t fenceId = static_cast<uint32_t>(fenceAsFileDescriptor);
    ALOGV("Waiting on fence:%d", (int)fenceId);

    std::shared_future<void> waitable;

    {
        std::lock_guard<std::mutex> lock(mVirtioGpuFencesMutex);

        auto fenceIt = mVirtioGpuFences.find(fenceId);
        if (fenceIt == mVirtioGpuFences.end()) {
            ALOGE("Fence:%d already signaled", (int)fenceId);
            return 0;
        }
        auto& fence = fenceIt->second;

        waitable = fence.waitable;
    }

    auto status = waitable.wait_for(std::chrono::milliseconds(timeoutMilliseconds));
    if (status == std::future_status::ready) {
        ALOGV("Finished waiting for fence:%d", (int)fenceId);
        return 0;
    } else {
        ALOGE("Timed out waiting for fence:%d", (int)fenceId);
        return -1;
    }
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::SignalEmulatedFence(uint32_t fenceId) {
    ALOGV("Signaling fence:%d", (int)fenceId);

    std::lock_guard<std::mutex> lock(mVirtioGpuFencesMutex);

    auto fenceIt = mVirtioGpuFences.find(fenceId);
    if (fenceIt == mVirtioGpuFences.end()) {
        ALOGE("Failed to find fence %" PRIu32, fenceId);
        return;
    }
    auto& fenceInfo = fenceIt->second;
    fenceInfo.signaler.set_value();
}

uint32_t EmulatedVirtioGpu::EmulatedVirtioGpuImpl::CreateEmulatedFence() {
    const uint32_t fenceId = mNextVirtioGpuFenceId++;

    ALOGV("Creating fence:%d", (int)fenceId);

    std::lock_guard<std::mutex> lock(mVirtioGpuFencesMutex);

    auto [fenceIt, fenceCreated] = mVirtioGpuFences.emplace(fenceId, EmulatedFence{});
    if (!fenceCreated) {
        ALOGE("Attempting to recreate fence %" PRIu32, fenceId);
    }

    auto& fenceInfo = fenceIt->second;
    fenceInfo.waitable = fenceInfo.signaler.get_future();

    return fenceId;
}

std::shared_future<void> EmulatedVirtioGpu::EmulatedVirtioGpuImpl::EnqueueVirtioGpuTask(
    uint32_t contextId, VirtioGpuTask task, std::optional<VirtioGpuFence> fence) {
    std::promise<void> taskCompletedSignaler;
    std::shared_future<void> taskCompletedWaitable(taskCompletedSignaler.get_future());

    std::lock_guard<std::mutex> lock(mTasksMutex);
    mTasks.push(VirtioGpuTaskWithWaitable{
        .contextId = contextId,
        .task = std::move(task),
        .taskCompletedSignaler = std::move(taskCompletedSignaler),
        .fence = fence,
    });

    return taskCompletedWaitable;
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskContextAttachResource task) {
    ALOGV("Performing task to attach resource-id:%d to context-id:%d", task.resourceId,
          task.contextId);

    rutabaga_context_attach_resource(mRutabaga, task.contextId, task.resourceId);

    ALOGV("Performing task to attach resource-id:%d to context-id:%d - done", task.resourceId,
          task.contextId);
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskContextDetachResource task) {
    ALOGV("Performing task to detach resource-id:%d to context-id:%d", task.resourceId,
          task.contextId);

    rutabaga_context_detach_resource(mRutabaga, task.contextId, task.resourceId);

    ALOGV("Performing task to detach resource-id:%d to context-id:%d - done", task.resourceId,
          task.contextId);
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskCreateBlob task) {
    ALOGV("Performing task to create blob resource-id:%d", task.resourceId);

    int ret =
        rutabaga_resource_create_blob(mRutabaga, task.contextId, task.resourceId, &task.params,
                                      /*iovecs=*/nullptr,
                                      /*handle=*/nullptr);
    if (ret) {
        ALOGE("Failed to create blob.");
    }

    ALOGV("Performing task to create blob resource-id:%d - done", task.resourceId);
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskCreateContext task) {
    ALOGV("Performing task to create context-id:%" PRIu32 " context-init:%" PRIu32
          " context-name:%s",
          task.contextId, task.contextInit, task.contextName.c_str());

    int ret = rutabaga_context_create(mRutabaga, task.contextId, task.contextName.size(),
                                      task.contextName.data(), task.contextInit);
    if (ret) {
        ALOGE("Failed to create context-id:%" PRIu32 ".", task.contextId);
        return;
    }

    ALOGV("Performing task to create context-id:%" PRIu32 " context-init:%" PRIu32
          " context-name:%s - done",
          task.contextId, task.contextInit, task.contextName.c_str());
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskCreateResource task) {
    ALOGV("Performing task to create resource resource:%d", task.resourceId);

    EmulatedResource* resource = GetResource(task.resourceId);

    int ret = rutabaga_resource_create_3d(mRutabaga, task.resourceId, &task.params);
    if (ret) {
        ALOGE("Failed to create resource:%d", task.resourceId);
    }

    resource->iovec.iov_base = task.resourceBytes;
    resource->iovec.iov_len = task.params.width;

    struct rutabaga_iovecs vecs = {0};
    vecs.iovecs = &resource->iovec;
    vecs.num_iovecs = 1;

    ret = rutabaga_resource_attach_backing(mRutabaga, task.resourceId, &vecs);
    if (ret) {
        ALOGE("Failed to attach iov to resource:%d", task.resourceId);
    }

    ALOGV("Performing task to create resource resource:%d - done", task.resourceId);

    rutabaga_context_attach_resource(mRutabaga, task.contextId, task.resourceId);
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskDestroyContext task) {
    ALOGV("Performing task to destroy context-id:%" PRIu32, task.contextId);

    rutabaga_context_destroy(mRutabaga, task.contextId);

    ALOGV("Performing task to destroy context-id:%" PRIu32 " - done", task.contextId);
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskMap task) {
    ALOGV("Performing task to map resource resource:%d", task.resourceId);

    struct rutabaga_mapping mapping;
    int ret = rutabaga_resource_map(mRutabaga, task.resourceId, &mapping);
    if (ret) {
        ALOGE("Failed to map resource:%d", task.resourceId);
        return;
    }

    task.resourceMappedPromise.set_value(reinterpret_cast<uint8_t*>(mapping.ptr));
    ALOGV("Performing task to map resource resource:%d - done", task.resourceId);
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskSubmitCmd task) {
    ALOGV("Performing task to execbuffer");

    if (task.commandBuffer.size() % 4 != 0) {
        ALOGE("Unaligned command buffer?");
        return;
    }

    struct rutabaga_command cmd = {
        .ctx_id = task.contextId,
        .cmd_size = static_cast<uint32_t>(task.commandBuffer.size()),
        .cmd = reinterpret_cast<uint8_t*>(task.commandBuffer.data()),
        .num_in_fences = 0,
        .fence_ids = nullptr,
    };

    int ret = rutabaga_submit_command(mRutabaga, &cmd);
    if (ret) {
        ALOGE("Failed to execbuffer.");
    }

    ALOGV("Performing task to execbuffer - done");
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskTransferFromHost task) {
    struct rutabaga_transfer transfer = {0};
    transfer.x = task.transferOffset;
    transfer.w = task.transferSize;
    transfer.h = 1;
    transfer.d = 1;

    int ret = rutabaga_resource_transfer_read(mRutabaga, task.contextId, task.resourceId, &transfer,
                                              nullptr);
    if (ret) {
        ALOGE("Failed to transferFromHost() for resource:%" PRIu32, task.resourceId);
    }
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskTransferToHost task) {
    struct rutabaga_transfer transfer = {0};
    transfer.x = task.transferOffset;
    transfer.w = task.transferSize;
    transfer.h = 1;
    transfer.d = 1;

    int ret =
        rutabaga_resource_transfer_write(mRutabaga, task.contextId, task.resourceId, &transfer);
    if (ret) {
        ALOGE("Failed to transferToHost() for resource:%" PRIu32, task.resourceId);
    }
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskUnrefResource task) {
    rutabaga_resource_unref(mRutabaga, task.resourceId);
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskSnapshotSave task) {
    int ret = rutabaga_snapshot(mRutabaga, task.directory.c_str());
    if (ret) {
        ALOGE("snapshotting failed");
    }
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskSnapshotRestore task) {
    int ret = rutabaga_restore(mRutabaga, task.directory.c_str());
    if (ret) {
        ALOGE("snapshot restore failed");
    }
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::DoTask(VirtioGpuTaskWithWaitable task) {
    std::visit(
        [this](auto&& work) {
            using T = std::decay_t<decltype(work)>;
            if constexpr (std::is_same_v<T, VirtioGpuTaskContextAttachResource>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskContextDetachResource>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskCreateBlob>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskCreateContext>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskCreateResource>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskDestroyContext>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskMap>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskSubmitCmd>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskTransferFromHost>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskTransferToHost>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskUnrefResource>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskSnapshotSave>) {
                DoTask(std::move(work));
            } else if constexpr (std::is_same_v<T, VirtioGpuTaskSnapshotRestore>) {
                DoTask(std::move(work));
            }
        },
        task.task);

    if (task.fence) {
        const struct rutabaga_fence fenceInfo = {
            .flags = RUTABAGA_FLAG_INFO_RING_IDX,
            .fence_id = (*task.fence).fenceId,
            .ctx_id = task.contextId,
            .ring_idx = (*task.fence).ringIdx,
        };
        int ret = rutabaga_create_fence(mRutabaga, &fenceInfo);
        if (ret) {
            ALOGE("Failed to create fence.");
        }
    }

    task.taskCompletedSignaler.set_value();
}

void EmulatedVirtioGpu::EmulatedVirtioGpuImpl::RunVirtioGpuTaskProcessingLoop() {
    while (!mShuttingDown.load()) {
        std::optional<VirtioGpuTaskWithWaitable> task;

        {
            std::lock_guard<std::mutex> lock(mTasksMutex);
            if (!mTasks.empty()) {
                task = std::move(mTasks.front());
                mTasks.pop();
            }
        }

        if (task) {
            DoTask(std::move(*task));
        }
    }
}

EmulatedVirtioGpu::EmulatedVirtioGpu()
    : mImpl{std::make_unique<EmulatedVirtioGpu::EmulatedVirtioGpuImpl>()} {}

namespace {

static std::mutex sInstanceMutex;
static std::weak_ptr<EmulatedVirtioGpu> sInstance;

}  // namespace

/*static*/
std::shared_ptr<EmulatedVirtioGpu> EmulatedVirtioGpu::Get() {
    std::lock_guard<std::mutex> lock(sInstanceMutex);

    std::shared_ptr<EmulatedVirtioGpu> instance = sInstance.lock();
    if (instance != nullptr) {
        return instance;
    }

    instance = std::shared_ptr<EmulatedVirtioGpu>(new EmulatedVirtioGpu());

    bool withGl = false;
    bool withVk = true;
    bool withVkSnapshots = false;

    struct Option {
        std::string env;
        bool* val;
    };
    const std::vector<Option> options = {
        {"GFXSTREAM_EMULATED_VIRTIO_GPU_WITH_GL", &withGl},
        {"GFXSTREAM_EMULATED_VIRTIO_GPU_WITH_VK", &withVk},
        {"GFXSTREAM_EMULATED_VIRTIO_GPU_WITH_VK_SNAPSHOTS", &withVkSnapshots},
    };
    for (const Option option : options) {
        const char* val = std::getenv(option.env.c_str());
        if (val != nullptr && (val[0] == 'Y' || val[0] == 'y')) {
            *option.val = true;
        }
    }

    ALOGE("Initializing withGl:%d withVk:%d withVkSnapshots:%d", withGl, withVk, withVkSnapshots);
    if (!instance->Init(withGl, withVk, withVkSnapshots)) {
        ALOGE("Failed to initialize EmulatedVirtioGpu.");
        return nullptr;
    }
    ALOGE("Successfully initialized EmulatedVirtioGpu.");
    sInstance = instance;
    return instance;
}

/*static*/
uint32_t EmulatedVirtioGpu::GetNumActiveUsers() {
    std::lock_guard<std::mutex> lock(sInstanceMutex);
    std::shared_ptr<EmulatedVirtioGpu> instance = sInstance.lock();
    return instance.use_count();
}

bool EmulatedVirtioGpu::Init(bool withGl, bool withVk, bool withVkSnapshots) {
    return mImpl->Init(withGl, withVk, withVkSnapshots, this);
}

std::optional<uint32_t> EmulatedVirtioGpu::CreateContext(uint32_t contextInit) {
    return mImpl->CreateContext(contextInit);
}

void EmulatedVirtioGpu::DestroyContext(uint32_t contextId) { mImpl->DestroyContext(contextId); }

bool EmulatedVirtioGpu::GetCaps(uint32_t capsetId, uint32_t guestCapsSize, uint8_t* capset) {
    return mImpl->GetCaps(capsetId, guestCapsSize, capset);
}

uint8_t* EmulatedVirtioGpu::Map(uint32_t resourceId) { return mImpl->Map(resourceId); }

void EmulatedVirtioGpu::Unmap(uint32_t resourceId) { mImpl->Unmap(resourceId); }

int EmulatedVirtioGpu::SubmitCmd(uint32_t contextId, uint32_t cmdSize, void* cmd, uint32_t ringIdx,
                                 VirtioGpuFenceFlags fenceFlags, uint32_t& fenceId,
                                 std::optional<uint32_t> blobResourceId) {
    return mImpl->SubmitCmd(contextId, cmdSize, cmd, ringIdx, fenceFlags, fenceId, blobResourceId);
}

int EmulatedVirtioGpu::Wait(uint32_t resourceId) { return mImpl->Wait(resourceId); }

int EmulatedVirtioGpu::TransferFromHost(uint32_t contextId, uint32_t resourceId, uint32_t offset,
                                        uint32_t size) {
    return mImpl->TransferFromHost(contextId, resourceId, offset, size);
}

int EmulatedVirtioGpu::TransferToHost(uint32_t contextId, uint32_t resourceId, uint32_t offset,
                                      uint32_t size) {
    return mImpl->TransferToHost(contextId, resourceId, offset, size);
}

std::optional<uint32_t> EmulatedVirtioGpu::CreateBlob(uint32_t contextId, uint32_t blobMem,
                                                      uint32_t blobFlags, uint64_t blobId,
                                                      uint64_t blobSize) {
    return mImpl->CreateBlob(contextId, blobMem, blobFlags, blobId, blobSize);
}

std::optional<uint32_t> EmulatedVirtioGpu::CreateVirglBlob(uint32_t contextId, uint32_t width,
                                                           uint32_t height, uint32_t virglFormat,
                                                           uint32_t target, uint32_t bind,
                                                           uint32_t size) {
    return mImpl->CreateVirglBlob(contextId, width, height, virglFormat, target, bind, size);
}

void EmulatedVirtioGpu::DestroyResource(uint32_t contextId, uint32_t resourceId) {
    mImpl->DestroyResource(contextId, resourceId);
}

void EmulatedVirtioGpu::SnapshotSave(const std::string& directory) {
    mImpl->SnapshotSave(directory);
}

void EmulatedVirtioGpu::SnapshotRestore(const std::string& directory) {
    mImpl->SnapshotRestore(directory);
}

int EmulatedVirtioGpu::WaitOnEmulatedFence(int fenceAsFileDescriptor, int timeoutMilliseconds) {
    return mImpl->WaitOnEmulatedFence(fenceAsFileDescriptor, timeoutMilliseconds);
}

void EmulatedVirtioGpu::SignalEmulatedFence(int fenceId) { mImpl->SignalEmulatedFence(fenceId); }

bool GetNumActiveEmulatedVirtioGpuUsers() { return EmulatedVirtioGpu::GetNumActiveUsers(); }

}  // namespace gfxstream
