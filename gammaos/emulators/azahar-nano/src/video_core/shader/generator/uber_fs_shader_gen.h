// GammaOS: fragment ubershader for azahar-nano.
//
// One fragment program that evaluates the PICA fragment pipeline (TEV stages,
// combiner buffer, alpha test, fog, fragment lighting, procedural texture,
// border emulation, logic op) from the FSConfig words in the uniform block
// instead of baking them into the code. Its pipelines therefore depend only on
// fixed-function state, which is the same for every game, so they can be
// compiled before any game runs. The math mirrors the GLSL generator in
// glsl_fs_shader_gen.cpp line by line.
//
// What still has to be fixed at compile time becomes a variant: the sampler
// type of texture unit 0 (2D, cube, shadow 2D, shadow cube), shadow rendering
// (an image binding) and whether the fragment writes its own depth.
#pragma once

#include <string>

#include "common/common_types.h"

namespace Pica::Shader {
struct Profile;
}

namespace Pica::Shader::Generator::Uber {

enum class Tex0Kind : u8 {
    Flat = 0,       ///< Texture2D, Projection2D or Disabled (sampler2D)
    Cube = 1,       ///< TextureCube (samplerCube)
    Shadow2D = 2,   ///< Shadow2D (usampler2D)
    ShadowCube = 3, ///< ShadowCube (usampler2D[6])
};

struct Variant {
    Tex0Kind tex0 = Tex0Kind::Flat;
    bool shadow_rendering = false;
    bool write_depth = true; ///< false when the vertex stage supplies the depth (vertex_depth)
    bool lighting = false;   ///< fragment lighting code present (register pressure)
    bool proctex = false;    ///< procedural texture code present

    u32 Key() const {
        return static_cast<u32>(tex0) | (shadow_rendering ? 4u : 0u) | (write_depth ? 8u : 0u) |
               (lighting ? 16u : 0u) | (proctex ? 32u : 0u);
    }
    static Variant FromKey(u32 key) {
        return Variant{static_cast<Tex0Kind>(key & 3), (key & 4) != 0, (key & 8) != 0,
                       (key & 16) != 0, (key & 32) != 0};
    }
};

/// Number of 32-bit words of the FSConfig copied into the uniform block.
constexpr u32 ConfigWords = 68;

std::string GenerateFragmentShader(const Variant& variant, const Profile& profile);

} // namespace Pica::Shader::Generator::Uber
