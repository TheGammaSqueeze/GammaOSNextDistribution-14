// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include "common/bit_set.h"
#include "common/logging/log.h"
#include "common/settings.h"
#include "video_core/pica/regs_internal.h"
#include "video_core/pica/shader_setup.h"
#include "video_core/shader/generator/shader_gen.h"

#include <sys/system_properties.h>
#include <unordered_map>
#include <atomic>
#include <array>
#include <nihstro/shader_bytecode.h>

namespace Pica::Shader::Generator {

void PicaGSConfigState::Init(const Pica::RegsInternal& regs) {
    vs_output_attributes_count = Common::BitSet<u32>(regs.vs.output_mask).Count();
    gs_output_attributes_count = vs_output_attributes_count;
    vs_output_total = regs.rasterizer.vs_output_total;

    memcpy(vs_output_attributes.data(), regs.rasterizer.vs_output_attributes,
           vs_output_total * sizeof(Pica::RasterizerRegs::VSOutputAttributes));
}

std::array<PicaGSConfigState::SemanticMap, 24> PicaGSConfigState::GetSemanticMaps() const {
    std::array<SemanticMap, 24> semantic_maps{};

    semantic_maps.fill({16, 0});
    for (u32 attrib = 0; attrib < vs_output_total; ++attrib) {
        const std::array semantics{
            vs_output_attributes[attrib].map_x.Value(),
            vs_output_attributes[attrib].map_y.Value(),
            vs_output_attributes[attrib].map_z.Value(),
            vs_output_attributes[attrib].map_w.Value(),
        };
        for (u32 comp = 0; comp < 4; ++comp) {
            const auto semantic = semantics[comp];
            if (static_cast<std::size_t>(semantic) < 24) {
                semantic_maps[static_cast<std::size_t>(semantic)] = {attrib, comp};
            } else if (semantic != Pica::RasterizerRegs::VSOutputAttributes::INVALID) {
                LOG_ERROR(Render, "Invalid/unknown semantic id: {}", semantic);
            }
        }
    }

    return semantic_maps;
}

namespace {

// GammaOS: the largest draw whose vertex shader gets its bool uniforms baked in (0 = none),
// set per title by SetVsBoolSpecializationTitle.
std::atomic<u32> g_vs_spec_max_vertices{0};

u32 VsBoolSpecMaxVertices() {
    return g_vs_spec_max_vertices.load(std::memory_order_relaxed);
}

/// The bool uniforms a program's flow control tests (IFU, CALLU, JMPU), cached per program.
u16 ReferencedBoolUniforms(const Pica::ShaderSetup& setup, u64 program_hash) {
    static std::unordered_map<u64, u16> cache;
    if (const auto it = cache.find(program_hash); it != cache.end()) {
        return it->second;
    }
    using nihstro::Instruction;
    using nihstro::OpCode;
    u16 mask = 0;
    for (const u32 word : setup.GetProgramCode()) {
        const Instruction instr{.hex = word};
        switch (instr.opcode.Value().EffectiveOpCode()) {
        case OpCode::Id::IFU:
        case OpCode::Id::CALLU:
        case OpCode::Id::JMPU:
            mask |= static_cast<u16>(1u << instr.flow_control.bool_uniform_id);
            break;
        default:
            break;
        }
    }
    cache.emplace(program_hash, mask);
    return mask;
}

} // namespace

void PicaVSConfigState::Init(const Pica::RegsInternal& regs, Pica::ShaderSetup& setup) {
    setup.DoProgramCodeFixup();
    program_hash = setup.GetProgramCodeHash();
    swizzle_hash = setup.GetSwizzleDataHash();
    main_offset = regs.vs.main_offset;

    bool_mask = 0;
    bool_values = 0;
    // Small draws only: there the GPU pays the program's run time for a handful of vertices
    // and the specialized program is shorter. On large meshes the specialized program ran
    // slower (Metroid at 2x: +4.7 ms a frame), so they keep the generic one.
    if (regs.pipeline.num_vertices <= VsBoolSpecMaxVertices()) {
        bool_mask = ReferencedBoolUniforms(setup, program_hash);
        for (u32 j = 0; j < setup.uniforms.b.size(); j++) {
            if ((bool_mask >> j) & 1u && setup.uniforms.b[j]) {
                bool_values |= static_cast<u16>(1u << j);
            }
        }
    }

    lighting_disable = regs.lighting.disable;

    num_outputs = 0;
    output_map.fill(16);

    for (u32 reg : Common::BitSet<u32>(regs.vs.output_mask)) {
        output_map[reg] = num_outputs++;
    }

    gs_state.Init(regs);
}

void SetVsBoolSpecializationTitle(u64 title_id) {
    // Whether the specialized programs are faster depends on the game: shorter for Kirby's
    // hundreds of draws (all sizes: 3 points of speed at 2x over small draws only) and Senran
    // Kagura's skinned characters (2.4 ms of GPU time a frame at 1x), slower on Metroid's large
    // meshes (0.9-4.7 ms at 2x).
    struct Entry {
        u64 title_id;
        u32 max_vertices;
    };
    static constexpr std::array<Entry, 3> table{{
        {0x00040000000F4500, 0xFFFFFFFF}, // Senran Kagura Burst (Europe)
        // Luigi's Mansion: Dark Moon: the 42k vertex environment VS (44 bool branches) drops
        // from about 6.7 to 4 ms per draw at 2x, two such draws a frame.
        {0x0004000000055F00, 0xFFFFFFFF},
        {0x0004000000183600, 0xFFFFFFFF}, // Kirby: Planet Robobot (USA)
    }};
    u32 max_vertices = 0;
    for (const Entry& e : table) {
        if (e.title_id == title_id && e.title_id != 0) {
            max_vertices = e.max_vertices;
        }
    }
    // persist.gammaos.azahar_nano.vs_bool_spec = 1 / 0 forces it on / off for every title;
    // vs_bool_spec_max_verts sets the largest draw specialized when on.
    char v[PROP_VALUE_MAX] = {};
    __system_property_get("persist.gammaos.azahar_nano.vs_bool_spec", v);
    if (v[0] == '0') {
        max_vertices = 0;
    } else if (v[0] == '1' && max_vertices == 0) {
        max_vertices = 64;
    }
    char m[PROP_VALUE_MAX] = {};
    __system_property_get("persist.gammaos.azahar_nano.vs_bool_spec_max_verts", m);
    if (m[0] && max_vertices != 0) {
        max_vertices = static_cast<u32>(atoi(m));
    }
    g_vs_spec_max_vertices.store(max_vertices, std::memory_order_relaxed);
    LOG_INFO(Render, "vertex shader bool specialization for {:016X}: up to {} vertices",
             title_id, max_vertices);
}

PicaVSConfig::PicaVSConfig(const Pica::RegsInternal& regs, Pica::ShaderSetup& setup) {
    state.Init(regs, setup);
}

PicaFixedGSConfig::PicaFixedGSConfig(const Pica::RegsInternal& regs) {
    state.Init(regs);
}

} // namespace Pica::Shader::Generator
