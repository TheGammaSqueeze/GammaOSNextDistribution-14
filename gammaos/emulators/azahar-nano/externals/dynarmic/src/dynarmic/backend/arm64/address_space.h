/* This file is part of the dynarmic project.
 * Copyright (c) 2022 MerryMage
 * SPDX-License-Identifier: 0BSD
 */

#pragma once

#include <memory>

#include <map>
#include <vector>
#include <optional>

#include <mcl/stdint.hpp>
#include <oaknut/code_block.hpp>
#include <oaknut/oaknut.hpp>
#include <tsl/robin_map.h>
#include <tsl/robin_set.h>

#include "dynarmic/backend/arm64/emit_arm64.h"
#include "dynarmic/backend/arm64/fastmem.h"
#include "dynarmic/interface/halt_reason.h"
#include "dynarmic/ir/basic_block.h"
#include "dynarmic/ir/location_descriptor.h"

namespace Dynarmic::Backend::Arm64 {

class AddressSpace {
public:
    explicit AddressSpace(size_t code_cache_size);
    virtual ~AddressSpace();

    virtual IR::Block GenerateIR(IR::LocationDescriptor) const = 0;

    CodePtr Get(IR::LocationDescriptor descriptor);

    // Returns "most likely" LocationDescriptor assocated with the emitted code at that location
    std::optional<IR::LocationDescriptor> ReverseGetLocation(CodePtr host_pc);

    // Returns "most likely" entry_point associated with the emitted code at that location
    CodePtr ReverseGetEntryPoint(CodePtr host_pc);

    CodePtr GetOrEmit(IR::LocationDescriptor descriptor);

    /// Fast dispatch: a direct-mapped table (location -> host code) the emitted code looks up
    /// for indirect branches before returning to the dispatcher. Cleared whenever code is
    /// invalidated or the cache is cleared.
    struct FastDispatchEntry {
        u64 location = ~u64{0};
        CodePtr code_ptr = nullptr;
    };
    // GammaOS: table size as a power of two, settable once before the first address space
    // (16 = 64K entries, 1 MiB; smaller tables fit the L2 at the price of more collisions).
    static inline size_t fast_dispatch_bits = 16;
    static u64 FastDispatchIndex(u64 location) {
        return ((location ^ (location >> 32)) >> 1) & ((u64{1} << fast_dispatch_bits) - 1);
    }
    FastDispatchEntry* FastDispatchTable() { return fast_dispatch_table.get(); }
    void FastDispatchInsert(u64 location, CodePtr code_ptr) {
        auto& e = fast_dispatch_table[FastDispatchIndex(location)];
        e.location = location;
        e.code_ptr = code_ptr;
    }
    void FastDispatchClear() {
        for (size_t i = 0; i < (size_t{1} << fast_dispatch_bits); i++) {
            fast_dispatch_table[i] = FastDispatchEntry{};
        }
    }

    /// Raw location descriptors of all compiled blocks, in the order they were first
    /// compiled (oldest first), so a warm-up can compile the earliest needed first.
    std::vector<u64> GetBlockLocations() const {
        std::vector<u64> out;
        out.reserve(block_entries.size());
        for (const u64 value : emit_order) {
            if (block_entries.contains(IR::LocationDescriptor{value})) {
                out.push_back(value);
            }
        }
        return out;
    }

    /// GammaOS: the blocks first compiled since a previous call, for a cheap periodic save.
    /// `cursor` is the position in the compile order reached last time and `generation` the
    /// cache generation it belongs to (both start at 0); a cache clear since then starts over.
    std::vector<u64> GetBlockLocationsSince(std::size_t& cursor, std::uint64_t& generation) const {
        if (generation != clear_generation || cursor > emit_order.size()) {
            cursor = 0;
            generation = clear_generation;
        }
        std::vector<u64> out;
        out.reserve(emit_order.size() - cursor);
        for (std::size_t i = cursor; i < emit_order.size(); i++) {
            if (block_entries.contains(IR::LocationDescriptor{emit_order[i]})) {
                out.push_back(emit_order[i]);
            }
        }
        cursor = emit_order.size();
        return out;
    }

    /// Debug: (host code address, raw location descriptor) of every compiled block, so host
    /// profiler samples in generated code can be attributed to guest addresses.
    std::vector<std::pair<std::uint64_t, std::uint64_t>> GetBlockMap() const {
        std::vector<std::pair<std::uint64_t, std::uint64_t>> out;
        out.reserve(reverse_block_entries.size());
        for (const auto& [ptr, loc] : reverse_block_entries) {
            out.emplace_back(reinterpret_cast<std::uint64_t>(ptr), loc.Value());
        }
        return out;
    }

    void InvalidateBasicBlocks(const tsl::robin_set<IR::LocationDescriptor>& descriptors);

    void ClearCache();

    void DumpDisassembly() const;

protected:
    virtual EmitConfig GetEmitConfig() = 0;
    virtual void RegisterNewBasicBlock(const IR::Block& block, const EmittedBlockInfo& block_info) = 0;

    void ProtectCodeMemory() {
#if defined(DYNARMIC_ENABLE_NO_EXECUTE_SUPPORT) || defined(__APPLE__) || defined(__OpenBSD__)
        mem.protect();
#endif
    }

    void UnprotectCodeMemory() {
#if defined(DYNARMIC_ENABLE_NO_EXECUTE_SUPPORT) || defined(__APPLE__) || defined(__OpenBSD__)
        mem.unprotect();
#endif
    }

    size_t GetRemainingSize();
    /// GammaOS far code: makes room for a block of `ir_size` IR instructions in the current
    /// segment, opening the next one (or clearing the cache) when it does not fit. False when
    /// the block is too long for a segment and has to be emitted inline.
    bool FarCodeReserve(size_t ir_size);
    EmittedBlockInfo Emit(IR::Block ir_block);
    void Link(EmittedBlockInfo& block);
    void LinkBlockLinks(const CodePtr entry_point, const CodePtr target_ptr, const std::vector<BlockRelocation>& block_relocations_list);
    void RelinkForDescriptor(IR::LocationDescriptor target_descriptor, CodePtr target_ptr);

    FakeCall FastmemCallback(u64 host_pc);

    const size_t code_cache_size;
    oaknut::CodeBlock mem;
    oaknut::CodeGenerator code;

    // GammaOS far code (set by the frontend address space before the first block): the cache
    // after the prelude is cut into segments of far_segment_size bytes; block bodies fill the
    // first far_body_size bytes of a segment and their deferred code the rest, so a conditional
    // branch from a body reaches its fallback (range 1 MiB) and the bodies stay dense.
    static constexpr std::ptrdiff_t far_segment_size = 1024 * 1024;
    static constexpr std::ptrdiff_t far_body_size = 640 * 1024;
    bool far_code = false;
    std::ptrdiff_t far_segment = -1;   // offset of the current segment, -1 before the first
    std::ptrdiff_t far_offset = 0;     // where the next block's deferred code goes

    // A IR::LocationDescriptor will have one current CodePtr.
    // However, there can be multiple other CodePtrs which are older, previously invalidated blocks.
    std::unique_ptr<FastDispatchEntry[]> fast_dispatch_table{
        new FastDispatchEntry[size_t{1} << fast_dispatch_bits]};  // sized by the current bits
    tsl::robin_map<IR::LocationDescriptor, CodePtr> block_entries;
    std::vector<u64> emit_order;   // first-compile order (may hold invalidated entries)
    std::uint64_t clear_generation = 1;   // bumped by every ClearCache
    std::map<CodePtr, IR::LocationDescriptor> reverse_block_entries;
    tsl::robin_map<CodePtr, EmittedBlockInfo> block_infos;
    tsl::robin_map<IR::LocationDescriptor, tsl::robin_set<CodePtr>> block_references;

    ExceptionHandler exception_handler;
    FastmemManager fastmem_manager;

    struct PreludeInfo {
        std::ptrdiff_t end_of_prelude;

        using RunCodeFuncType = HaltReason (*)(CodePtr entry_point, void* jit_state, volatile u32* halt_reason);
        RunCodeFuncType run_code;
        RunCodeFuncType step_code;
        void* return_to_dispatcher;
        void* return_from_run_code;

        void* read_memory_8;
        void* read_memory_16;
        void* read_memory_32;
        void* read_memory_64;
        void* read_memory_128;
        void* wrapped_read_memory_8;
        void* wrapped_read_memory_16;
        void* wrapped_read_memory_32;
        void* wrapped_read_memory_64;
        void* wrapped_read_memory_128;
        void* exclusive_read_memory_8;
        void* exclusive_read_memory_16;
        void* exclusive_read_memory_32;
        void* exclusive_read_memory_64;
        void* exclusive_read_memory_128;
        void* write_memory_8;
        void* write_memory_16;
        void* write_memory_32;
        void* write_memory_64;
        void* write_memory_128;
        void* wrapped_write_memory_8;
        void* wrapped_write_memory_16;
        void* wrapped_write_memory_32;
        void* wrapped_write_memory_64;
        void* wrapped_write_memory_128;
        void* exclusive_write_memory_8;
        void* exclusive_write_memory_16;
        void* exclusive_write_memory_32;
        void* exclusive_write_memory_64;
        void* exclusive_write_memory_128;

        void* call_svc;
        void* exception_raised;
        void* dc_raised;
        void* ic_raised;
        void* isb_raised;

        void* get_cntpct;
        void* add_ticks;
        void* get_ticks_remaining;
    } prelude_info;
};

}  // namespace Dynarmic::Backend::Arm64
