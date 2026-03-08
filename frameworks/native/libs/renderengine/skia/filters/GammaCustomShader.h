#pragma once

#include <SkRuntimeEffect.h>
#include <SkSurface.h>
#include <ui/GraphicTypes.h>

#include <mutex>
#include <string>
#include <vector>

namespace android {
namespace renderengine {
namespace skia {

class SkiaCapture;

// Loads RetroArch-compatible .slangp shader presets from /sdcard/GammaShader/ or
// RetroArch data directories, transpiles the .slang fragment shaders to SkSL,
// and runs them as post-process effects in the GammaOS shader pipeline.
//
// Supported features:
//  - Single-pass and multi-pass shader chains
//  - #pragma parameter (user-tunable uniforms)
//  - Source / Original / OutputSize / SourceSize / FrameCount semantics
//  - LUT textures (loaded from preset-relative paths)
//  - Nearest / linear filtering per pass
//  - FBO scaling per pass (source-relative, viewport-relative, absolute)
//
// Limitations:
//  - Vertex shader is ignored (fullscreen quad implied)
//  - PassFeedback / OriginalHistory not yet supported
//  - Only fragment-only effects translate cleanly
struct GammaCustomShader {
    static bool apply(SkSurface* dstSurface,
                      SkSurface* srcSurface,
                      SkiaCapture* capture,
                      ui::Dataspace outDataspace,
                      bool isProtected,
                      bool testOverlay,
                      bool ctmBfiBlack,
                      float defaultScanAngleDeg);
};

// Parsed representation of a .slangp shader preset.
struct SlangParam {
    std::string id;
    std::string desc;
    float initial = 0.0f;
    float minimum = 0.0f;
    float maximum = 1.0f;
    float step = 0.01f;
    float current = 0.0f; // runtime value (from preset override or initial)
};

struct SlangPass {
    std::string shaderPath;     // absolute path to .slang file
    std::string alias;          // pass alias for inter-pass referencing
    bool filterLinear = false;  // true=linear, false=nearest
    int scaleTypeX = 0;        // 0=source, 1=absolute, 2=viewport
    int scaleTypeY = 0;
    float scaleX = 1.0f;
    float scaleY = 1.0f;
    int absX = 0;
    int absY = 0;
    bool srgbFbo = false;
    bool floatFbo = false;
    bool mipmap = false;
    int frameCountMod = 0;

    // Transpiled SkSL and compiled effect (populated by loadAndCompile)
    std::string sksl;
    sk_sp<SkRuntimeEffect> effect;
};

struct SlangLut {
    std::string id;
    std::string path;           // absolute path to texture
    bool filterLinear = true;
    bool mipmap = false;
};

struct SlangPreset {
    std::string presetPath;     // absolute path to .slangp
    std::string presetDir;      // directory containing the preset
    std::vector<SlangPass> passes;
    std::vector<SlangLut> luts;
    std::vector<SlangParam> params;
    int feedbackPass = -1;
    bool valid = false;
};

// Parse a .slangp preset file into a SlangPreset.
bool parseSlangPreset(const std::string& path, SlangPreset& out);

// Read a .slang file and extract the fragment shader body (after #pragma stage fragment).
std::string extractSlangFragment(const std::string& path);

// Transpile a Vulkan GLSL fragment shader (from .slang) to SkSL.
// params: extracted #pragma parameter declarations to generate uniforms.
// passIndex: current pass (0-based) for semantic mapping.
// totalPasses: total number of passes in the chain.
// hasLuts: whether there are LUT textures to bind.
std::string transpileToSkSL(const std::string& glslFragment,
                            const std::vector<SlangParam>& params,
                            int passIndex, int totalPasses,
                            const std::vector<SlangLut>& luts);

// Load, transpile, and compile all passes in a preset.
bool loadAndCompilePreset(SlangPreset& preset);

} // namespace skia
} // namespace renderengine
} // namespace android
