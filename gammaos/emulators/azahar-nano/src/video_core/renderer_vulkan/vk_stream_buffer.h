// Copyright 2019 yuzu Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <optional>
#include <span>
#include <tuple>
#include <vector>
#include "video_core/renderer_vulkan/vk_common.h"

namespace Vulkan {

enum class BufferType : u32 {
    Upload = 0,
    Download = 1,
    Stream = 2,
    /// GammaOS: a stream buffer in CPU cached memory (flushed at every commit when the type is
    /// not host coherent). For data the driver reads back on the CPU, like index buffers whose
    /// vertex range a Bifrost driver has to find: a read from write-combined memory goes to
    /// DRAM for every load.
    StreamCached = 3,
};

class Instance;
class Scheduler;

class StreamBuffer final {
    static constexpr std::size_t MAX_BUFFER_VIEWS = 3;

public:
    explicit StreamBuffer(const Instance& instance, Scheduler& scheduler,
                          vk::BufferUsageFlags usage, u64 size,
                          BufferType type = BufferType::Stream);
    ~StreamBuffer();

    /**
     * Reserves a region of memory from the stream buffer.
     * @param size Size to reserve.
     * @returns A pair of a raw memory pointer (with offset added), and the buffer offset
     */
    std::tuple<u8*, u32, bool> Map(u32 size, u64 alignment);

    /// GammaOS: how many times the ring has wrapped. Data committed in the current lap stays
    /// in place (and readable by later draws) until the ring wraps again.
    u64 Lap() const noexcept {
        return lap;
    }

    /// GammaOS: data committed earlier in this lap at `offset` is used again by work recorded
    /// now: keep it until that work completes (the wrap waits on it before overwriting).
    void Touch(u32 offset);

    /// GammaOS: makes sure the next `size` bytes (allocated by Maps before the next wrap) fit
    /// without wrapping, wrapping now if they would not. Called before a render pass begins:
    /// a wrap inside one may have to submit (to wait for reused data) and end the pass.
    void Reserve(u32 size);

    /// GammaOS debug: the host view of the buffer.
    const u8* MappedBase() const noexcept {
        return mapped;
    }

    /// Ensures that "size" bytes of memory are available to the GPU, potentially recording a copy.
    void Commit(u32 size);

    /// GammaOS: a StreamCached buffer in non-coherent memory cleans the CPU cache for its
    /// commits here, once, instead of at every commit (each clean ends in a barrier that waits
    /// for the write-backs). Called before every queue submit and before the ring wraps.
    void FlushPending();

    vk::Buffer Handle() const noexcept {
        return buffer;
    }

private:
    struct Watch {
        u64 tick{};
        u64 upper_bound{};
    };

    /// Creates Vulkan buffer handles committing the required the required memory.
    void CreateBuffers(u64 prefered_size);

    /// Increases the amount of watches available.
    void ReserveWatches(std::vector<Watch>& watches, std::size_t grow_size);

    void WaitPendingOperations(u64 requested_upper_bound);

private:
    const Instance& instance; ///< Vulkan instance.
    Scheduler& scheduler;     ///< Command scheduler.

    vk::Device device;
    vk::Buffer buffer;        ///< Mapped buffer.
    vk::DeviceMemory memory;  ///< Memory allocation.
    u8* mapped{};             ///< Pointer to the mapped memory
    u64 stream_buffer_size{}; ///< Stream buffer size.
    vk::BufferUsageFlags usage{};
    BufferType type;

    u32 offset{};       ///< Buffer iterator.
    u32 mapped_size{};  ///< Size reserved for the current copy.
    bool is_coherent{}; ///< True if the buffer is coherent

    std::vector<Watch> current_watches;           ///< Watches recorded in the current iteration.
    std::size_t current_watch_cursor{};           ///< Count of watches, reset on invalidation.
    std::optional<std::size_t> invalidation_mark; ///< Number of watches used in the previous cycle.

    std::vector<Watch> previous_watches; ///< Watches used in the previous iteration.
    std::size_t wait_cursor{};           ///< Last watch being waited for completion.
    u64 wait_bound{};                    ///< Highest offset being watched for completion.
    u64 lap = 0;
    u64 dirty_begin = 0; ///< GammaOS: committed range of a StreamCached buffer not cleaned yet.
    u64 dirty_end = 0;
};

} // namespace Vulkan
