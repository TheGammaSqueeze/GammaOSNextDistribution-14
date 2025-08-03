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

#pragma once

#include <memory>
#include <optional>

// see protocol.rs in crosvm
enum VirtioGpuFenceFlags : uint32_t {
    kFlagNone = 0x0000,
    kFlagFence = 0x0001,
    kFlagRingIdx = 0x0002,
    kFlagFenceShareable = 0x0004,
};

constexpr enum VirtioGpuFenceFlags operator|(const enum VirtioGpuFenceFlags self,
                                             const enum VirtioGpuFenceFlags other) {
    return (enum VirtioGpuFenceFlags)(uint32_t(self) | uint32_t(other));
}

namespace gfxstream {

// Emulates parts of the Linux Virtio GPU kernel module and parts of
// a virtual machine manager to allow speaking directly to the Gfxstream
// host server via rutabaga.
class EmulatedVirtioGpu {
  public:
   static std::shared_ptr<EmulatedVirtioGpu> Get();
   static uint32_t GetNumActiveUsers();

   bool Init(bool withGl, bool withVk, bool withVkSnapshots);

   bool GetCaps(uint32_t capsetId, uint32_t guestCapsSize, uint8_t* capset);

   std::optional<uint32_t> CreateContext(uint32_t contextInit);
   void DestroyContext(uint32_t contextId);

   std::optional<uint32_t> CreateBlob(uint32_t contextId, uint32_t blobMem, uint32_t blobFlags,
                                      uint64_t blobId, uint64_t blobSize);
   std::optional<uint32_t> CreateVirglBlob(uint32_t contextId, uint32_t width, uint32_t height,
                                           uint32_t virglFormat, uint32_t target, uint32_t bind,
                                           uint32_t size);

   void DestroyResource(uint32_t contextId, uint32_t resourceId);

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

   void SignalEmulatedFence(int fenceId);

   int WaitOnEmulatedFence(int fenceAsFileDescriptor, int timeoutMilliseconds);

   void SnapshotSave(const std::string& directory);
   void SnapshotRestore(const std::string& directory);

  private:
    EmulatedVirtioGpu();

    class EmulatedVirtioGpuImpl;
    std::unique_ptr<EmulatedVirtioGpuImpl> mImpl;
};

}  // namespace gfxstream
