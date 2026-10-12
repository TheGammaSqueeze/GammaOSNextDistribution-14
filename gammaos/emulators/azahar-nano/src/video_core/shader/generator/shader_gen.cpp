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

/// GammaOS: persist.gammaos.azahar_nano.vs_bool_spec=0 keeps bool uniforms as runtime branches.
bool VsBoolSpecialization() {
    static const bool enabled = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.vs_bool_spec", v);
        return v[0] != '0';
    }();
    return enabled;
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
    if (VsBoolSpecialization()) {
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

PicaVSConfig::PicaVSConfig(const Pica::RegsInternal& regs, Pica::ShaderSetup& setup) {
    state.Init(regs, setup);
}

PicaFixedGSConfig::PicaFixedGSConfig(const Pica::RegsInternal& regs) {
    state.Init(regs);
}

} // namespace Pica::Shader::Generator
