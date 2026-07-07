// GammaVulkanShaderChain: native Vulkan shader pipeline for RetroArch .slangp
// presets.  Compiles .slang → SPIR-V via glslang, reflects bindings via
// SPIRV-Cross, and renders through raw Vulkan graphics pipelines.
//
// This bypasses SkSL entirely so there are no language-level restrictions.

#undef LOG_TAG
#define LOG_TAG "GammaVkShader"
#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include "GammaVulkanShaderChain.h"

#include <android-base/properties.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <GrDirectContext.h>
#include <GrBackendSurface.h>
#include <include/gpu/ganesh/SkImageGanesh.h>
#include <include/gpu/ganesh/SkSurfaceGanesh.h>
#include <include/gpu/ganesh/vk/GrVkBackendSurface.h>
#include <vk/GrVkTypes.h>

// glslang — GLSL to SPIR-V compiler
#include <glslang/Public/ShaderLang.h>
#include <SPIRV/GlslangToSpv.h>

// SPIRV-Cross — SPIR-V reflection
#include <spirv_cross.hpp>

#include <SkBitmap.h>
#include <SkCanvas.h>
#include <SkColorSpace.h>
#include <SkData.h>
#include <SkImage.h>
#include <SkPaint.h>
#include <SkRect.h>

#include "../debug/SkiaCapture.h"

#include <sys/stat.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <mutex>
#include <sstream>
#include <unordered_map>

using android::base::GetBoolProperty;
using android::base::GetProperty;

namespace android {
namespace renderengine {
namespace skia {

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

static std::string readFile(const std::string& path) {
    std::ifstream f(path);
    if (!f) return {};
    return {std::istreambuf_iterator<char>(f), std::istreambuf_iterator<char>()};
}

static std::string dirOf(const std::string& path) {
    auto pos = path.find_last_of('/');
    return (pos != std::string::npos) ? path.substr(0, pos) : ".";
}

static std::string trim(const std::string& s) {
    auto a = s.find_first_not_of(" \t\r\n");
    if (a == std::string::npos) return {};
    auto b = s.find_last_not_of(" \t\r\n");
    return s.substr(a, b - a + 1);
}

static std::string resolvePath(const std::string& base, const std::string& rel) {
    if (!rel.empty() && rel[0] == '/') return rel;
    return base + "/" + rel;
}

// ---------------------------------------------------------------------------
// .slangp preset parser (self-contained, no SkSL dependency)
// ---------------------------------------------------------------------------

bool parseGammaSlangPreset(const std::string& path, GammaSlangPreset& out) {
    std::string content = readFile(path);
    if (content.empty()) {
        ALOGE("GammaVkShader: cannot read preset '%s'", path.c_str());
        return false;
    }

    out = {};
    out.presetPath = path;
    out.presetDir  = dirOf(path);

    std::unordered_map<std::string, std::string> kv;
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        line = trim(line);
        if (line.empty() || line[0] == '#') continue;
        auto eq = line.find('=');
        if (eq == std::string::npos) continue;
        std::string key = trim(line.substr(0, eq));
        std::string val = trim(line.substr(eq + 1));
        // Strip surrounding quotes
        if (val.size() >= 2 && val.front() == '"' && val.back() == '"')
            val = val.substr(1, val.size() - 2);
        kv[key] = val;
    }

    int numShaders = 0;
    if (kv.count("shaders"))
        numShaders = std::atoi(kv["shaders"].c_str());
    if (numShaders <= 0 || numShaders > 64) {
        ALOGE("GammaVkShader: bad shader count %d in '%s'", numShaders, path.c_str());
        return false;
    }

    out.passes.resize(numShaders);
    for (int i = 0; i < numShaders; i++) {
        auto& p = out.passes[i];
        std::string idx = std::to_string(i);

        if (kv.count("shader" + idx))
            p.shaderPath = resolvePath(out.presetDir, kv["shader" + idx]);
        if (kv.count("alias" + idx))
            p.alias = kv["alias" + idx];
        if (kv.count("filter_linear" + idx))
            p.filterLinear = (kv["filter_linear" + idx] == "true");
        if (kv.count("mipmap_input" + idx))
            p.mipmap = (kv["mipmap_input" + idx] == "true");
        if (kv.count("srgb_framebuffer" + idx))
            p.srgbFbo = (kv["srgb_framebuffer" + idx] == "true");
        if (kv.count("float_framebuffer" + idx))
            p.floatFbo = (kv["float_framebuffer" + idx] == "true");
        if (kv.count("frame_count_mod" + idx))
            p.frameCountMod = std::atoi(kv["frame_count_mod" + idx].c_str());

        // Scale types
        auto parseScaleType = [](const std::string& v) -> int {
            if (v == "source")   return 0;
            if (v == "absolute") return 1;
            if (v == "viewport") return 2;
            return 0;
        };
        if (kv.count("scale_type" + idx)) {
            int t = parseScaleType(kv["scale_type" + idx]);
            p.scaleTypeX = p.scaleTypeY = t;
        }
        if (kv.count("scale_type_x" + idx))
            p.scaleTypeX = parseScaleType(kv["scale_type_x" + idx]);
        if (kv.count("scale_type_y" + idx))
            p.scaleTypeY = parseScaleType(kv["scale_type_y" + idx]);

        if (kv.count("scale" + idx)) {
            float s = std::strtof(kv["scale" + idx].c_str(), nullptr);
            p.scaleX = p.scaleY = s;
        }
        if (kv.count("scale_x" + idx))
            p.scaleX = std::strtof(kv["scale_x" + idx].c_str(), nullptr);
        if (kv.count("scale_y" + idx))
            p.scaleY = std::strtof(kv["scale_y" + idx].c_str(), nullptr);
        if (p.scaleTypeX == 1 && kv.count("scale_x" + idx))
            p.absX = std::atoi(kv["scale_x" + idx].c_str());
        if (p.scaleTypeY == 1 && kv.count("scale_y" + idx))
            p.absY = std::atoi(kv["scale_y" + idx].c_str());
        if (p.scaleTypeX == 1 && p.scaleTypeY == 1 && kv.count("scale" + idx)) {
            int a = std::atoi(kv["scale" + idx].c_str());
            if (p.absX == 0) p.absX = a;
            if (p.absY == 0) p.absY = a;
        }
    }

    // LUT textures
    if (kv.count("textures")) {
        std::istringstream ts(kv["textures"]);
        std::string tok;
        while (std::getline(ts, tok, ';')) {
            tok = trim(tok);
            if (tok.empty()) continue;
            GammaSlangLut lut;
            lut.id = tok;
            if (kv.count(tok))
                lut.path = resolvePath(out.presetDir, kv[tok]);
            if (kv.count(tok + "_linear"))
                lut.filterLinear = (kv[tok + "_linear"] == "true");
            if (kv.count(tok + "_mipmap"))
                lut.mipmap = (kv[tok + "_mipmap"] == "true");
            out.luts.push_back(std::move(lut));
        }
    }

    // Parameters (overrides from preset file)
    if (kv.count("parameters")) {
        std::istringstream ps(kv["parameters"]);
        std::string tok;
        while (std::getline(ps, tok, ';')) {
            tok = trim(tok);
            if (tok.empty()) continue;
            if (kv.count(tok)) {
                // Find or create param entry (actual metadata comes from shader)
                GammaSlangParam sp;
                sp.id = tok;
                sp.current = std::strtof(kv[tok].c_str(), nullptr);
                sp.initial = sp.current;
                out.params.push_back(std::move(sp));
            }
        }
    }

    out.valid = true;
    return true;
}

// ---------------------------------------------------------------------------
// .slang file reading with #include resolution
// ---------------------------------------------------------------------------

static bool readSlangFile(const std::string& path, std::vector<std::string>& lines,
                          int depth = 0) {
    if (depth > 16) {
        ALOGE("GammaVkShader: #include depth exceeded for '%s'", path.c_str());
        return false;
    }
    std::string content = readFile(path);
    if (content.empty()) {
        ALOGE("GammaVkShader: cannot read '%s'", path.c_str());
        return false;
    }
    std::string dir = dirOf(path);
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        // Handle #include "relative/path"
        if (line.find("#include") == 0) {
            auto q1 = line.find('"');
            auto q2 = line.find('"', q1 + 1);
            if (q1 != std::string::npos && q2 != std::string::npos) {
                std::string inc = line.substr(q1 + 1, q2 - q1 - 1);
                std::string incPath = resolvePath(dir, inc);
                if (!readSlangFile(incPath, lines, depth + 1))
                    return false;
                continue;
            }
        }
        lines.push_back(line);
    }
    return true;
}

static std::string buildStageSource(const std::vector<std::string>& lines,
                                    const std::string& stage) {
    std::string out;
    out.reserve(lines.size() * 40);
    bool active = true;
    bool firstLine = true;

    for (const auto& line : lines) {
        if (firstLine) {
            // First line should be #version
            out += line + "\n";
            firstLine = false;
            continue;
        }

        if (line.find("#pragma stage ") == 0) {
            std::string expected = "#pragma stage " + stage;
            active = (trim(line) == expected);
            continue;
        }

        // Skip #pragma name and #pragma format (metadata only)
        if (line.find("#pragma name ") == 0 || line.find("#pragma format ") == 0)
            continue;

        if (active)
            out += line;
        out += "\n";
    }
    return out;
}

static void parseSlangParams(const std::vector<std::string>& lines,
                             std::vector<GammaSlangParam>& params) {
    for (const auto& line : lines) {
        if (line.find("#pragma parameter ") != 0) continue;
        char id[64], desc[64];
        float initial, minimum, maximum, step;
        int ret = sscanf(line.c_str(),
                         "#pragma parameter %63s \"%63[^\"]\" %f %f %f %f",
                         id, desc, &initial, &minimum, &maximum, &step);
        if (ret == 5) { step = 0.1f * (maximum - minimum); ret = 6; }
        if (ret != 6) continue;

        // Check for duplicate
        bool found = false;
        for (auto& p : params) {
            if (p.id == id) { found = true; break; }
        }
        if (!found) {
            GammaSlangParam sp;
            sp.id = id;
            sp.desc = desc;
            sp.initial = initial;
            sp.minimum = minimum;
            sp.maximum = maximum;
            sp.step = step;
            sp.current = initial;
            params.push_back(std::move(sp));
        }
    }
}

// ---------------------------------------------------------------------------
// glslang → SPIR-V compilation
// ---------------------------------------------------------------------------

static std::mutex sGlslangMutex;
static bool sGlslangInitialized = false;

static TBuiltInResource makeDefaultResources() {
    TBuiltInResource r = {};
    r.maxLights                                = 32;
    r.maxClipPlanes                            = 6;
    r.maxTextureUnits                          = 32;
    r.maxTextureCoords                         = 32;
    r.maxVertexAttribs                         = 64;
    r.maxVertexUniformComponents               = 4096;
    r.maxVaryingFloats                         = 64;
    r.maxVertexTextureImageUnits               = 32;
    r.maxCombinedTextureImageUnits             = 80;
    r.maxTextureImageUnits                     = 32;
    r.maxFragmentUniformComponents             = 4096;
    r.maxDrawBuffers                           = 32;
    r.maxVertexUniformVectors                  = 128;
    r.maxVaryingVectors                        = 8;
    r.maxFragmentUniformVectors                = 16;
    r.maxVertexOutputVectors                   = 16;
    r.maxFragmentInputVectors                  = 15;
    r.minProgramTexelOffset                    = -8;
    r.maxProgramTexelOffset                    = 7;
    r.maxClipDistances                         = 8;
    r.maxComputeWorkGroupCountX                = 65535;
    r.maxComputeWorkGroupCountY                = 65535;
    r.maxComputeWorkGroupCountZ                = 65535;
    r.maxComputeWorkGroupSizeX                 = 1024;
    r.maxComputeWorkGroupSizeY                 = 1024;
    r.maxComputeWorkGroupSizeZ                 = 64;
    r.maxComputeUniformComponents              = 1024;
    r.maxComputeTextureImageUnits              = 16;
    r.maxComputeImageUniforms                  = 8;
    r.maxComputeAtomicCounters                 = 8;
    r.maxComputeAtomicCounterBuffers           = 1;
    r.maxVaryingComponents                     = 60;
    r.maxVertexOutputComponents                = 64;
    r.maxGeometryInputComponents               = 64;
    r.maxGeometryOutputComponents              = 128;
    r.maxFragmentInputComponents               = 128;
    r.maxImageUnits                            = 8;
    r.maxCombinedImageUnitsAndFragmentOutputs  = 8;
    r.maxCombinedShaderOutputResources         = 8;
    r.maxImageSamples                          = 0;
    r.maxVertexImageUniforms                   = 0;
    r.maxTessControlImageUniforms              = 0;
    r.maxTessEvaluationImageUniforms           = 0;
    r.maxGeometryImageUniforms                 = 0;
    r.maxFragmentImageUniforms                 = 8;
    r.maxCombinedImageUniforms                 = 8;
    r.maxGeometryTextureImageUnits             = 16;
    r.maxGeometryOutputVertices                = 256;
    r.maxGeometryTotalOutputComponents         = 1024;
    r.maxGeometryUniformComponents             = 1024;
    r.maxGeometryVaryingComponents             = 64;
    r.maxTessControlInputComponents            = 128;
    r.maxTessControlOutputComponents           = 128;
    r.maxTessControlTextureImageUnits          = 16;
    r.maxTessControlUniformComponents          = 1024;
    r.maxTessControlTotalOutputComponents      = 4096;
    r.maxTessEvaluationInputComponents         = 128;
    r.maxTessEvaluationOutputComponents        = 128;
    r.maxTessEvaluationTextureImageUnits       = 16;
    r.maxTessEvaluationUniformComponents       = 1024;
    r.maxTessPatchComponents                   = 120;
    r.maxPatchVertices                         = 32;
    r.maxTessGenLevel                          = 64;
    r.maxViewports                             = 16;
    r.maxVertexAtomicCounters                  = 0;
    r.maxTessControlAtomicCounters             = 0;
    r.maxTessEvaluationAtomicCounters          = 0;
    r.maxGeometryAtomicCounters                = 0;
    r.maxFragmentAtomicCounters                = 8;
    r.maxCombinedAtomicCounters                = 8;
    r.maxAtomicCounterBindings                 = 1;
    r.maxVertexAtomicCounterBuffers            = 0;
    r.maxTessControlAtomicCounterBuffers       = 0;
    r.maxTessEvaluationAtomicCounterBuffers    = 0;
    r.maxGeometryAtomicCounterBuffers          = 0;
    r.maxFragmentAtomicCounterBuffers          = 1;
    r.maxCombinedAtomicCounterBuffers          = 1;
    r.maxAtomicCounterBufferSize               = 16384;
    r.maxTransformFeedbackBuffers              = 4;
    r.maxTransformFeedbackInterleavedComponents = 64;
    r.maxCullDistances                         = 8;
    r.maxCombinedClipAndCullDistances          = 8;
    r.maxSamples                               = 4;
    r.limits.nonInductiveForLoops              = true;
    r.limits.whileLoops                        = true;
    r.limits.doWhileLoops                      = true;
    r.limits.generalUniformIndexing            = true;
    r.limits.generalAttributeMatrixVectorIndexing = true;
    r.limits.generalVaryingIndexing            = true;
    r.limits.generalSamplerIndexing            = true;
    r.limits.generalVariableIndexing           = true;
    r.limits.generalConstantMatrixVectorIndexing = true;
    return r;
}

static bool compileGlslToSpirv(const std::string& source, EShLanguage stage,
                                std::vector<uint32_t>& spirv) {
    std::lock_guard<std::mutex> lock(sGlslangMutex);
    if (!sGlslangInitialized) {
        glslang::InitializeProcess();
        sGlslangInitialized = true;
    }

    static TBuiltInResource resources = makeDefaultResources();

    glslang::TShader shader(stage);
    const char* src = source.c_str();
    shader.setStrings(&src, 1);

    EShMessages messages = static_cast<EShMessages>(
        EShMsgDefault | EShMsgVulkanRules | EShMsgSpvRules);

    std::string preprocessed;
    glslang::TShader::ForbidIncluder forbid;
    if (!shader.preprocess(&resources, 100, ENoProfile, false, false,
                           messages, &preprocessed, forbid)) {
        ALOGE("GammaVkShader: glslang preprocess failed:\n%s", shader.getInfoLog());
        return false;
    }

    if (!shader.parse(&resources, 100, false, messages)) {
        ALOGE("GammaVkShader: glslang parse failed:\n%s", shader.getInfoLog());
        return false;
    }

    glslang::TProgram program;
    program.addShader(&shader);
    if (!program.link(messages)) {
        ALOGE("GammaVkShader: glslang link failed:\n%s", program.getInfoLog());
        return false;
    }

    glslang::GlslangToSpv(*program.getIntermediate(stage), spirv);
    return !spirv.empty();
}

// ---------------------------------------------------------------------------
// GammaVulkanFilterChain — shader compilation
// ---------------------------------------------------------------------------

bool GammaVulkanFilterChain::compileSlangShader(
        const std::string& path,
        std::vector<uint32_t>& vertexSpirv,
        std::vector<uint32_t>& fragmentSpirv,
        std::vector<GammaSlangParam>& params) {

    std::vector<std::string> lines;
    if (!readSlangFile(path, lines)) return false;

    // Extract parameters from #pragma declarations
    parseSlangParams(lines, params);

    std::string vertSrc = buildStageSource(lines, "vertex");
    std::string fragSrc = buildStageSource(lines, "fragment");

    if (!compileGlslToSpirv(vertSrc, EShLangVertex, vertexSpirv)) {
        ALOGE("GammaVkShader: vertex compilation failed for '%s'", path.c_str());
        return false;
    }
    if (!compileGlslToSpirv(fragSrc, EShLangFragment, fragmentSpirv)) {
        ALOGE("GammaVkShader: fragment compilation failed for '%s'", path.c_str());
        return false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// SPIRV-Cross reflection
// ---------------------------------------------------------------------------

// Semantic name → texture semantic type mapping
enum TexSemantic {
    TEX_ORIGINAL = 0,
    TEX_SOURCE,
    TEX_ORIGINAL_HISTORY,
    TEX_PASS_OUTPUT,
    TEX_PASS_FEEDBACK,
    TEX_USER,
};

struct ReflectedBinding {
    TexSemantic semantic;
    int         index;    // e.g. PassOutput3 → index=3
    int         binding;  // descriptor binding
};

static bool identifyTextureSemantic(const std::string& name,
                                    const GammaSlangPreset& preset,
                                    TexSemantic& sem, int& index) {
    if (name == "Original" || name == "OriginalImage") {
        sem = TEX_ORIGINAL; index = 0; return true;
    }
    if (name == "Source" || name == "InputImage") {
        sem = TEX_SOURCE; index = 0; return true;
    }
    if (name.find("OriginalHistory") == 0) {
        sem = TEX_ORIGINAL_HISTORY;
        index = std::atoi(name.c_str() + 15);
        return true;
    }
    if (name.find("PassOutput") == 0) {
        sem = TEX_PASS_OUTPUT;
        index = std::atoi(name.c_str() + 10);
        return true;
    }
    if (name.find("PassFeedback") == 0) {
        sem = TEX_PASS_FEEDBACK;
        index = std::atoi(name.c_str() + 12);
        return true;
    }
    // Check pass aliases
    for (int i = 0; i < (int)preset.passes.size(); i++) {
        if (!preset.passes[i].alias.empty() && preset.passes[i].alias == name) {
            sem = TEX_PASS_OUTPUT; index = i; return true;
        }
        if (!preset.passes[i].alias.empty() &&
            (preset.passes[i].alias + "Feedback") == name) {
            sem = TEX_PASS_FEEDBACK; index = i; return true;
        }
    }
    // Check LUT names
    for (int i = 0; i < (int)preset.luts.size(); i++) {
        if (preset.luts[i].id == name) {
            sem = TEX_USER; index = i; return true;
        }
    }
    return false;
}

static int findUniformOffset(const spirv_cross::Compiler& comp,
                             const spirv_cross::Resource& ubo,
                             const std::string& name) {
    auto& type = comp.get_type(ubo.base_type_id);
    for (uint32_t i = 0; i < type.member_types.size(); i++) {
        if (comp.get_member_name(ubo.base_type_id, i) == name) {
            return (int)comp.type_struct_member_offset(type, i);
        }
    }
    return -1;
}

bool GammaVulkanFilterChain::reflectPass(int passIndex) {
    auto& pass   = mPreset->passes[passIndex];
    auto& res    = mPasses[passIndex];

    // Reflect the fragment shader (most bindings are here)
    spirv_cross::Compiler fragComp(pass.fragmentSpirv);
    spirv_cross::ShaderResources fragRes = fragComp.get_shader_resources();

    // Also reflect vertex shader for its UBO
    spirv_cross::Compiler vertComp(pass.vertexSpirv);
    spirv_cross::ShaderResources vertRes = vertComp.get_shader_resources();

    // --- UBO reflection ---
    // Find the UBO (typically binding 0, set 0)
    uint32_t maxUboSize = 0;
    auto reflectUboMembers = [&](const spirv_cross::Compiler& comp,
                                  const spirv_cross::Resource& ubo,
                                  VkPassResources::UniformOffsets& offsets) {
        auto& type = comp.get_type(ubo.base_type_id);
        uint32_t sz = (uint32_t)comp.get_declared_struct_size(type);
        if (sz > maxUboSize) maxUboSize = sz;

        offsets.mvp           = findUniformOffset(comp, ubo, "MVP");
        offsets.outputSize    = findUniformOffset(comp, ubo, "OutputSize");
        offsets.finalViewport = findUniformOffset(comp, ubo, "FinalViewportSize");
        offsets.frameCount    = findUniformOffset(comp, ubo, "FrameCount");
        offsets.frameDirection = findUniformOffset(comp, ubo, "FrameDirection");
        offsets.originalSize  = findUniformOffset(comp, ubo, "OriginalSize");
        offsets.sourceSize    = findUniformOffset(comp, ubo, "SourceSize");

        // Pass output sizes
        for (int i = 0; i < 16; i++) {
            offsets.passOutputSize[i] = findUniformOffset(
                comp, ubo, "PassOutputSize" + std::to_string(i));
        }
        // Also try alias-based names
        for (int i = 0; i < (int)mPreset->passes.size() && i < 16; i++) {
            if (!mPreset->passes[i].alias.empty()) {
                int off = findUniformOffset(comp, ubo,
                    mPreset->passes[i].alias + "Size");
                if (off >= 0 && offsets.passOutputSize[i] < 0)
                    offsets.passOutputSize[i] = off;
            }
        }

        // Pass feedback sizes
        for (int i = 0; i < 16; i++) {
            offsets.passFeedbackSize[i] = findUniformOffset(
                comp, ubo, "PassFeedbackSize" + std::to_string(i));
        }

        // Original history sizes
        for (int i = 0; i < 16; i++) {
            offsets.originalHistorySize[i] = findUniformOffset(
                comp, ubo, "OriginalHistorySize" + std::to_string(i));
        }

        // LUT sizes
        for (int i = 0; i < (int)mPreset->luts.size() && i < 16; i++) {
            offsets.userSize[i] = findUniformOffset(
                comp, ubo, mPreset->luts[i].id + "Size");
        }

        // Float parameters
        offsets.paramOffsets.resize(mPreset->params.size());
        for (int i = 0; i < (int)mPreset->params.size(); i++) {
            offsets.paramOffsets[i].offset = findUniformOffset(
                comp, ubo, mPreset->params[i].id);
        }
    };

    // Process UBOs from both stages
    for (auto& ubo : vertRes.uniform_buffers) {
        uint32_t set = vertComp.get_decoration(ubo.id, spv::DecorationDescriptorSet);
        uint32_t binding = vertComp.get_decoration(ubo.id, spv::DecorationBinding);
        if (set == 0 && binding == 0)
            reflectUboMembers(vertComp, ubo, res.uboOffsets);
    }
    for (auto& ubo : fragRes.uniform_buffers) {
        uint32_t set = fragComp.get_decoration(ubo.id, spv::DecorationDescriptorSet);
        uint32_t binding = fragComp.get_decoration(ubo.id, spv::DecorationBinding);
        if (set == 0 && binding == 0)
            reflectUboMembers(fragComp, ubo, res.uboOffsets);
    }

    // Push constants
    for (auto& pc : vertRes.push_constant_buffers) {
        auto& type = vertComp.get_type(pc.base_type_id);
        uint32_t sz = (uint32_t)vertComp.get_declared_struct_size(type);
        if (sz > res.pushConstantSize) res.pushConstantSize = sz;
        reflectUboMembers(vertComp, pc, res.pushOffsets);
    }
    for (auto& pc : fragRes.push_constant_buffers) {
        auto& type = fragComp.get_type(pc.base_type_id);
        uint32_t sz = (uint32_t)fragComp.get_declared_struct_size(type);
        if (sz > res.pushConstantSize) res.pushConstantSize = sz;
        reflectUboMembers(fragComp, pc, res.pushOffsets);
    }

    res.uboSize = std::max(maxUboSize, (uint32_t)256); // minimum 256 bytes

    // --- Texture/sampler reflection ---
    for (auto& img : fragRes.sampled_images) {
        uint32_t binding = fragComp.get_decoration(img.id, spv::DecorationBinding);
        std::string name = img.name;

        TexSemantic sem;
        int idx;
        if (identifyTextureSemantic(name, *mPreset, sem, idx)) {
            switch (sem) {
                case TEX_ORIGINAL:
                    res.texOriginal.binding = (int)binding; break;
                case TEX_SOURCE:
                    res.texSource.binding = (int)binding; break;
                case TEX_PASS_OUTPUT:
                    if (idx < 16) res.texPassOutput[idx].binding = (int)binding;
                    break;
                case TEX_PASS_FEEDBACK:
                    if (idx < 16) res.texPassFeedback[idx].binding = (int)binding;
                    break;
                case TEX_ORIGINAL_HISTORY:
                    if (idx < 16) res.texOriginalHistory[idx].binding = (int)binding;
                    break;
                case TEX_USER:
                    if (idx < 16) res.texUser[idx].binding = (int)binding;
                    break;
            }
        }
    }

    // Determine history size needed
    for (int i = 15; i >= 0; i--) {
        if (res.texOriginalHistory[i].binding >= 0) {
            if (i + 1 > mHistorySize) mHistorySize = i + 1;
            break;
        }
    }

    // Determine if any pass uses feedback
    for (int i = 0; i < 16; i++) {
        if (res.texPassFeedback[i].binding >= 0 && i < (int)mPreset->passes.size()) {
            mPreset->passes[i].feedback = true;
        }
    }

    return true;
}

// ---------------------------------------------------------------------------
// Vulkan function pointer loading
// ---------------------------------------------------------------------------

bool GammaVulkanFilterChain::loadVkFuncs() {
    if (mVkFuncsLoaded) return true;
    auto gpa = mCtx.getDeviceProcAddr;
    auto dev = mCtx.device;

#define LOAD_VK(name) name##_ = (PFN_##name)gpa(dev, #name); \
    if (!name##_) { ALOGE("GammaVkShader: failed to load " #name); return false; }

    LOAD_VK(vkCreateShaderModule)
    LOAD_VK(vkDestroyShaderModule)
    LOAD_VK(vkCreatePipelineLayout)
    LOAD_VK(vkDestroyPipelineLayout)
    LOAD_VK(vkCreateGraphicsPipelines)
    LOAD_VK(vkDestroyPipeline)
    LOAD_VK(vkCreateRenderPass)
    LOAD_VK(vkDestroyRenderPass)
    LOAD_VK(vkCreateFramebuffer)
    LOAD_VK(vkDestroyFramebuffer)
    LOAD_VK(vkCreateImageView)
    LOAD_VK(vkDestroyImageView)
    LOAD_VK(vkCreateImage)
    LOAD_VK(vkDestroyImage)
    LOAD_VK(vkAllocateMemory)
    LOAD_VK(vkFreeMemory)
    LOAD_VK(vkBindImageMemory)
    LOAD_VK(vkGetImageMemoryRequirements)
    LOAD_VK(vkCreateBuffer)
    LOAD_VK(vkDestroyBuffer)
    LOAD_VK(vkBindBufferMemory)
    LOAD_VK(vkGetBufferMemoryRequirements)
    LOAD_VK(vkMapMemory)
    LOAD_VK(vkUnmapMemory)
    LOAD_VK(vkCreateSampler)
    LOAD_VK(vkDestroySampler)
    LOAD_VK(vkCreateDescriptorSetLayout)
    LOAD_VK(vkDestroyDescriptorSetLayout)
    LOAD_VK(vkCreateDescriptorPool)
    LOAD_VK(vkDestroyDescriptorPool)
    LOAD_VK(vkAllocateDescriptorSets)
    LOAD_VK(vkUpdateDescriptorSets)
    LOAD_VK(vkAllocateCommandBuffers)
    LOAD_VK(vkFreeCommandBuffers)
    LOAD_VK(vkCreateCommandPool)
    LOAD_VK(vkDestroyCommandPool)
    LOAD_VK(vkBeginCommandBuffer)
    LOAD_VK(vkEndCommandBuffer)
    LOAD_VK(vkQueueSubmit)
    LOAD_VK(vkQueueWaitIdle)
    LOAD_VK(vkCreateFence)
    LOAD_VK(vkDestroyFence)
    LOAD_VK(vkWaitForFences)
    LOAD_VK(vkResetFences)
    LOAD_VK(vkResetCommandPool)
    LOAD_VK(vkCmdBeginRenderPass)
    LOAD_VK(vkCmdEndRenderPass)
    LOAD_VK(vkCmdBindPipeline)
    LOAD_VK(vkCmdBindDescriptorSets)
    LOAD_VK(vkCmdBindVertexBuffers)
    LOAD_VK(vkCmdDraw)
    LOAD_VK(vkCmdSetViewport)
    LOAD_VK(vkCmdSetScissor)
    LOAD_VK(vkCmdPipelineBarrier)
    LOAD_VK(vkCmdPushConstants)
    LOAD_VK(vkCmdCopyBufferToImage)
    LOAD_VK(vkCmdCopyImageToBuffer)
    LOAD_VK(vkFlushMappedMemoryRanges)

#undef LOAD_VK

    // Instance-level function
    auto ipa = mCtx.getInstanceProcAddr;
    vkGetPhysicalDeviceMemoryProperties_ =
        (PFN_vkGetPhysicalDeviceMemoryProperties)ipa(
            mCtx.instance, "vkGetPhysicalDeviceMemoryProperties");

    vkGetPhysicalDeviceMemoryProperties_(mCtx.physicalDevice, &mMemoryProperties);

    mVkFuncsLoaded = true;
    return true;
}

// ---------------------------------------------------------------------------
// Vulkan resource helpers
// ---------------------------------------------------------------------------

uint32_t GammaVulkanFilterChain::findMemoryType(
        uint32_t typeFilter, VkMemoryPropertyFlags properties) {
    for (uint32_t i = 0; i < mMemoryProperties.memoryTypeCount; i++) {
        if ((typeFilter & (1 << i)) &&
            (mMemoryProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }
    ALOGE("GammaVkShader: failed to find suitable memory type");
    return 0;
}

bool GammaVulkanFilterChain::createImage(
        int width, int height, VkFormat format, VkImageUsageFlags usage,
        VkImage& image, VkDeviceMemory& memory, VkImageView& view) {

    VkImageCreateInfo imgInfo = {};
    imgInfo.sType         = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imgInfo.imageType     = VK_IMAGE_TYPE_2D;
    imgInfo.format        = format;
    imgInfo.extent        = {(uint32_t)width, (uint32_t)height, 1};
    imgInfo.mipLevels     = 1;
    imgInfo.arrayLayers   = 1;
    imgInfo.samples       = VK_SAMPLE_COUNT_1_BIT;
    imgInfo.tiling        = VK_IMAGE_TILING_OPTIMAL;
    imgInfo.usage         = usage;
    imgInfo.sharingMode   = VK_SHARING_MODE_EXCLUSIVE;
    imgInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    if (vkCreateImage_(mCtx.device, &imgInfo, nullptr, &image) != VK_SUCCESS)
        return false;

    VkMemoryRequirements memReqs;
    vkGetImageMemoryRequirements_(mCtx.device, image, &memReqs);

    VkMemoryAllocateInfo allocInfo = {};
    allocInfo.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize  = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    if (vkAllocateMemory_(mCtx.device, &allocInfo, nullptr, &memory) != VK_SUCCESS) {
        vkDestroyImage_(mCtx.device, image, nullptr);
        image = VK_NULL_HANDLE;
        return false;
    }
    vkBindImageMemory_(mCtx.device, image, memory, 0);

    VkImageViewCreateInfo viewInfo = {};
    viewInfo.sType    = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image    = image;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format   = format;
    viewInfo.subresourceRange.aspectMask     = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel   = 0;
    viewInfo.subresourceRange.levelCount     = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount     = 1;

    if (vkCreateImageView_(mCtx.device, &viewInfo, nullptr, &view) != VK_SUCCESS) {
        vkFreeMemory_(mCtx.device, memory, nullptr);
        vkDestroyImage_(mCtx.device, image, nullptr);
        image = VK_NULL_HANDLE;
        memory = VK_NULL_HANDLE;
        return false;
    }
    return true;
}

bool GammaVulkanFilterChain::createSampler(bool linear, bool mipmap, VkSampler& sampler) {
    VkSamplerCreateInfo info = {};
    info.sType        = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    info.magFilter    = linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
    info.minFilter    = linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
    info.mipmapMode   = mipmap ? VK_SAMPLER_MIPMAP_MODE_LINEAR : VK_SAMPLER_MIPMAP_MODE_NEAREST;
    info.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    info.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    info.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    info.maxLod       = mipmap ? VK_LOD_CLAMP_NONE : 0.0f;
    return vkCreateSampler_(mCtx.device, &info, nullptr, &sampler) == VK_SUCCESS;
}

void GammaVulkanFilterChain::transitionImage(
        VkCommandBuffer cmd, VkImage image,
        VkImageLayout oldLayout, VkImageLayout newLayout) {
    VkImageMemoryBarrier barrier = {};
    barrier.sType               = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout           = oldLayout;
    barrier.newLayout           = newLayout;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image               = image;
    barrier.subresourceRange.aspectMask     = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel   = 0;
    barrier.subresourceRange.levelCount     = 1;
    barrier.subresourceRange.baseArrayLayer = 0;
    barrier.subresourceRange.layerCount     = 1;

    VkPipelineStageFlags srcStage, dstStage;
    if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
        barrier.srcAccessMask = 0;
        srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    } else {
        barrier.srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
        srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    }

    if (newLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
        barrier.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    } else if (newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    } else {
        barrier.dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
        dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    }

    vkCmdPipelineBarrier_(cmd, srcStage, dstStage, 0,
                          0, nullptr, 0, nullptr, 1, &barrier);
}

void GammaVulkanFilterChain::computePassSize(
        const GammaSlangPass& pass,
        int srcW, int srcH, int viewW, int viewH,
        int& outW, int& outH) {
    // X dimension
    switch (pass.scaleTypeX) {
        case 0: // source-relative
            outW = std::max(1, (int)(srcW * pass.scaleX));
            break;
        case 1: // absolute
            outW = pass.absX > 0 ? pass.absX : srcW;
            break;
        case 2: // viewport-relative
            outW = std::max(1, (int)(viewW * pass.scaleX));
            break;
        default:
            outW = srcW;
    }
    // Y dimension
    switch (pass.scaleTypeY) {
        case 0:
            outH = std::max(1, (int)(srcH * pass.scaleY));
            break;
        case 1:
            outH = pass.absY > 0 ? pass.absY : srcH;
            break;
        case 2:
            outH = std::max(1, (int)(viewH * pass.scaleY));
            break;
        default:
            outH = srcH;
    }
}

// ---------------------------------------------------------------------------
// Pipeline creation
// ---------------------------------------------------------------------------

bool GammaVulkanFilterChain::createPassFramebuffer(int passIndex, int width, int height) {
    auto& r = mPasses[passIndex];
    auto& pass = mPreset->passes[passIndex];

    // Determine format
    VkFormat fmt = VK_FORMAT_R8G8B8A8_UNORM;
    if (pass.srgbFbo) fmt = VK_FORMAT_R8G8B8A8_SRGB;
    if (pass.floatFbo) fmt = VK_FORMAT_R16G16B16A16_SFLOAT;
    r.fbFormat = fmt;
    r.fbWidth  = width;
    r.fbHeight = height;

    // Create image (TRANSFER_SRC needed for CPU readback via renderFromPixels)
    if (!createImage(width, height, fmt,
                     VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
                     VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                     r.fbImage, r.fbMemory, r.fbView))
        return false;

    // Create render pass
    VkAttachmentDescription attachment = {};
    attachment.format         = fmt;
    attachment.samples        = VK_SAMPLE_COUNT_1_BIT;
    attachment.loadOp         = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachment.storeOp        = VK_ATTACHMENT_STORE_OP_STORE;
    attachment.stencilLoadOp  = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachment.initialLayout  = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    attachment.finalLayout    = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkAttachmentReference colorRef = {};
    colorRef.attachment = 0;
    colorRef.layout     = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass = {};
    subpass.pipelineBindPoint    = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments    = &colorRef;

    VkRenderPassCreateInfo rpInfo = {};
    rpInfo.sType           = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    rpInfo.attachmentCount = 1;
    rpInfo.pAttachments    = &attachment;
    rpInfo.subpassCount    = 1;
    rpInfo.pSubpasses      = &subpass;

    if (vkCreateRenderPass_(mCtx.device, &rpInfo, nullptr, &r.renderPass) != VK_SUCCESS)
        return false;

    // Create framebuffer
    VkFramebufferCreateInfo fbInfo = {};
    fbInfo.sType           = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    fbInfo.renderPass      = r.renderPass;
    fbInfo.attachmentCount = 1;
    fbInfo.pAttachments    = &r.fbView;
    fbInfo.width           = width;
    fbInfo.height          = height;
    fbInfo.layers          = 1;

    if (vkCreateFramebuffer_(mCtx.device, &fbInfo, nullptr, &r.framebuffer) != VK_SUCCESS)
        return false;

    // Create sampler for this pass output
    if (!createSampler(pass.filterLinear, pass.mipmap, r.fbSampler))
        return false;

    return true;
}

bool GammaVulkanFilterChain::createUBO(int passIndex) {
    auto& r = mPasses[passIndex];

    VkBufferCreateInfo bufInfo = {};
    bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufInfo.size  = r.uboSize;
    bufInfo.usage = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;

    if (vkCreateBuffer_(mCtx.device, &bufInfo, nullptr, &r.ubo) != VK_SUCCESS)
        return false;

    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements_(mCtx.device, r.ubo, &memReqs);

    VkMemoryAllocateInfo allocInfo = {};
    allocInfo.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize  = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    if (vkAllocateMemory_(mCtx.device, &allocInfo, nullptr, &r.uboMemory) != VK_SUCCESS)
        return false;

    vkBindBufferMemory_(mCtx.device, r.ubo, r.uboMemory, 0);
    vkMapMemory_(mCtx.device, r.uboMemory, 0, r.uboSize, 0, &r.uboMapped);
    memset(r.uboMapped, 0, r.uboSize);

    return true;
}

bool GammaVulkanFilterChain::createPassPipeline(int passIndex, int viewW, int viewH) {
    auto& r    = mPasses[passIndex];
    auto& pass = mPreset->passes[passIndex];
    bool isFinal = (passIndex == (int)mPreset->passes.size() - 1);

    // Create shader modules
    VkShaderModuleCreateInfo vertModInfo = {};
    vertModInfo.sType    = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    vertModInfo.codeSize = pass.vertexSpirv.size() * sizeof(uint32_t);
    vertModInfo.pCode    = pass.vertexSpirv.data();
    if (vkCreateShaderModule_(mCtx.device, &vertModInfo, nullptr, &r.vertModule) != VK_SUCCESS)
        return false;

    VkShaderModuleCreateInfo fragModInfo = {};
    fragModInfo.sType    = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    fragModInfo.codeSize = pass.fragmentSpirv.size() * sizeof(uint32_t);
    fragModInfo.pCode    = pass.fragmentSpirv.data();
    if (vkCreateShaderModule_(mCtx.device, &fragModInfo, nullptr, &r.fragModule) != VK_SUCCESS)
        return false;

    // Shader stages
    VkPipelineShaderStageCreateInfo stages[2] = {};
    stages[0].sType  = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage  = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = r.vertModule;
    stages[0].pName  = "main";
    stages[1].sType  = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage  = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = r.fragModule;
    stages[1].pName  = "main";

    // Vertex input: Position (vec4) + TexCoord (vec2)
    VkVertexInputBindingDescription vertBinding = {};
    vertBinding.binding   = 0;
    vertBinding.stride    = sizeof(float) * 6; // vec4 pos + vec2 texcoord
    vertBinding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;

    VkVertexInputAttributeDescription vertAttribs[2] = {};
    vertAttribs[0].location = 0;
    vertAttribs[0].binding  = 0;
    vertAttribs[0].format   = VK_FORMAT_R32G32B32A32_SFLOAT;
    vertAttribs[0].offset   = 0;
    vertAttribs[1].location = 1;
    vertAttribs[1].binding  = 0;
    vertAttribs[1].format   = VK_FORMAT_R32G32_SFLOAT;
    vertAttribs[1].offset   = sizeof(float) * 4;

    VkPipelineVertexInputStateCreateInfo vertexInput = {};
    vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    vertexInput.vertexBindingDescriptionCount   = 1;
    vertexInput.pVertexBindingDescriptions      = &vertBinding;
    vertexInput.vertexAttributeDescriptionCount = 2;
    vertexInput.pVertexAttributeDescriptions    = vertAttribs;

    VkPipelineInputAssemblyStateCreateInfo inputAssembly = {};
    inputAssembly.sType    = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;

    // Dynamic viewport/scissor
    VkPipelineViewportStateCreateInfo viewportState = {};
    viewportState.sType         = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewportState.viewportCount = 1;
    viewportState.scissorCount  = 1;

    VkDynamicState dynStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo dynamicState = {};
    dynamicState.sType             = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
    dynamicState.dynamicStateCount = 2;
    dynamicState.pDynamicStates    = dynStates;

    VkPipelineRasterizationStateCreateInfo raster = {};
    raster.sType       = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    raster.polygonMode = VK_POLYGON_MODE_FILL;
    raster.cullMode    = VK_CULL_MODE_NONE;
    raster.frontFace   = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    raster.lineWidth   = 1.0f;

    VkPipelineMultisampleStateCreateInfo multisample = {};
    multisample.sType                = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineColorBlendAttachmentState blendAttach = {};
    blendAttach.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                 VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    blendAttach.blendEnable = VK_FALSE;

    VkPipelineColorBlendStateCreateInfo blend = {};
    blend.sType           = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    blend.attachmentCount = 1;
    blend.pAttachments    = &blendAttach;

    // Count how many texture bindings this pass uses
    int maxBinding = 0;
    auto checkBinding = [&](int b) { if (b >= 0 && b > maxBinding) maxBinding = b; };
    checkBinding(r.texOriginal.binding);
    checkBinding(r.texSource.binding);
    for (int i = 0; i < 16; i++) {
        checkBinding(r.texPassOutput[i].binding);
        checkBinding(r.texPassFeedback[i].binding);
        checkBinding(r.texOriginalHistory[i].binding);
        checkBinding(r.texUser[i].binding);
    }

    // Descriptor set layout: binding 0 = UBO, binding 1..N = combined image samplers
    std::vector<VkDescriptorSetLayoutBinding> bindings;

    // UBO at binding 0
    VkDescriptorSetLayoutBinding uboBinding = {};
    uboBinding.binding         = 0;
    uboBinding.descriptorType  = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    uboBinding.descriptorCount = 1;
    uboBinding.stageFlags      = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    bindings.push_back(uboBinding);

    // Texture samplers at their reflected bindings
    for (int b = 1; b <= maxBinding; b++) {
        VkDescriptorSetLayoutBinding texBinding = {};
        texBinding.binding         = b;
        texBinding.descriptorType  = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        texBinding.descriptorCount = 1;
        texBinding.stageFlags      = VK_SHADER_STAGE_FRAGMENT_BIT;
        bindings.push_back(texBinding);
    }

    VkDescriptorSetLayoutCreateInfo descLayoutInfo = {};
    descLayoutInfo.sType        = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    descLayoutInfo.bindingCount = (uint32_t)bindings.size();
    descLayoutInfo.pBindings    = bindings.data();
    if (vkCreateDescriptorSetLayout_(mCtx.device, &descLayoutInfo, nullptr,
                                     &r.descSetLayout) != VK_SUCCESS)
        return false;

    // Push constant range (if used)
    VkPushConstantRange pushRange = {};
    pushRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    pushRange.offset     = 0;
    pushRange.size       = r.pushConstantSize;

    VkPipelineLayoutCreateInfo layoutInfo = {};
    layoutInfo.sType          = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    layoutInfo.setLayoutCount = 1;
    layoutInfo.pSetLayouts    = &r.descSetLayout;
    if (r.pushConstantSize > 0) {
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges    = &pushRange;
    }

    if (vkCreatePipelineLayout_(mCtx.device, &layoutInfo, nullptr, &r.layout) != VK_SUCCESS)
        return false;

    // Graphics pipeline
    VkGraphicsPipelineCreateInfo pipeInfo = {};
    pipeInfo.sType               = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pipeInfo.stageCount          = 2;
    pipeInfo.pStages             = stages;
    pipeInfo.pVertexInputState   = &vertexInput;
    pipeInfo.pInputAssemblyState = &inputAssembly;
    pipeInfo.pViewportState      = &viewportState;
    pipeInfo.pRasterizationState = &raster;
    pipeInfo.pMultisampleState   = &multisample;
    pipeInfo.pColorBlendState    = &blend;
    pipeInfo.pDynamicState       = &dynamicState;
    pipeInfo.layout              = r.layout;
    pipeInfo.renderPass          = r.renderPass;
    pipeInfo.subpass             = 0;

    if (vkCreateGraphicsPipelines_(mCtx.device, VK_NULL_HANDLE, 1, &pipeInfo,
                                   nullptr, &r.pipeline) != VK_SUCCESS) {
        ALOGE("GammaVkShader: pipeline creation failed for pass %d", passIndex);
        return false;
    }

    return true;
}

// ---------------------------------------------------------------------------
// UBO fill
// ---------------------------------------------------------------------------

void GammaVulkanFilterChain::fillUBO(int passIndex, int srcW, int srcH,
                                     int viewW, int viewH,
                                     int passW, int passH,
                                     uint32_t frameCount) {
    auto& r = mPasses[passIndex];

    // Helper to write into mapped UBO and push constant memory
    auto writeFloat4 = [](void* base, int offset, float x, float y, float z, float w) {
        if (offset < 0 || !base) return;
        float* p = (float*)((uint8_t*)base + offset);
        p[0] = x; p[1] = y; p[2] = z; p[3] = w;
    };
    auto writeMat4Identity = [](void* base, int offset) {
        if (offset < 0 || !base) return;
        float* p = (float*)((uint8_t*)base + offset);
        memset(p, 0, 64);
        p[0] = 1.0f; p[5] = 1.0f; p[10] = 1.0f; p[15] = 1.0f;
    };
    auto writeUint = [](void* base, int offset, uint32_t v) {
        if (offset < 0 || !base) return;
        *(uint32_t*)((uint8_t*)base + offset) = v;
    };
    auto writeInt = [](void* base, int offset, int32_t v) {
        if (offset < 0 || !base) return;
        *(int32_t*)((uint8_t*)base + offset) = v;
    };
    auto writeFloat = [](void* base, int offset, float v) {
        if (offset < 0 || !base) return;
        *(float*)((uint8_t*)base + offset) = v;
    };

    auto fillOffsets = [&](void* base, const VkPassResources::UniformOffsets& offsets) {
        // MVP: orthographic projection that maps the fullscreen quad
        // Standard 2D ortho: maps [0,1] UV space
        if (offsets.mvp >= 0) {
            float* mvp = (float*)((uint8_t*)base + offsets.mvp);
            memset(mvp, 0, 64);
            mvp[0] = 2.0f; mvp[5] = 2.0f; mvp[10] = 1.0f; mvp[15] = 1.0f;
            mvp[12] = -1.0f; mvp[13] = -1.0f;
        }

        writeFloat4(base, offsets.outputSize,
                    (float)passW, (float)passH,
                    1.0f / passW, 1.0f / passH);
        writeFloat4(base, offsets.finalViewport,
                    (float)viewW, (float)viewH,
                    1.0f / viewW, 1.0f / viewH);
        writeFloat4(base, offsets.sourceSize,
                    (float)srcW, (float)srcH,
                    1.0f / srcW, 1.0f / srcH);
        writeFloat4(base, offsets.originalSize,
                    (float)srcW, (float)srcH,
                    1.0f / srcW, 1.0f / srcH);

        uint32_t fc = frameCount;
        auto& pass = mPreset->passes[passIndex];
        if (pass.frameCountMod > 0) fc = frameCount % (uint32_t)pass.frameCountMod;
        writeUint(base, offsets.frameCount, fc);
        writeInt(base, offsets.frameDirection, 1);

        // Pass output sizes
        for (int i = 0; i < (int)mPasses.size() && i < 16; i++) {
            int w = mPasses[i].fbWidth;
            int h = mPasses[i].fbHeight;
            if (w > 0 && h > 0) {
                writeFloat4(base, offsets.passOutputSize[i],
                            (float)w, (float)h, 1.0f / w, 1.0f / h);
            }
        }

        // LUT sizes
        for (int i = 0; i < (int)mPreset->luts.size() && i < 16; i++) {
            auto& lut = mPreset->luts[i];
            if (lut.width > 0 && lut.height > 0) {
                writeFloat4(base, offsets.userSize[i],
                            (float)lut.width, (float)lut.height,
                            1.0f / lut.width, 1.0f / lut.height);
            }
        }

        // User parameters
        for (int i = 0; i < (int)mPreset->params.size() &&
                         i < (int)offsets.paramOffsets.size(); i++) {
            writeFloat(base, offsets.paramOffsets[i].offset, mPreset->params[i].current);
        }
    };

    fillOffsets(r.uboMapped, r.uboOffsets);
}

// ---------------------------------------------------------------------------
// Constructor / Destructor
// ---------------------------------------------------------------------------

GammaVulkanFilterChain::GammaVulkanFilterChain() = default;

GammaVulkanFilterChain::~GammaVulkanFilterChain() {
    destroy();
}

void GammaVulkanFilterChain::destroy() {
    if (!mCtx.device) return;

    // Wait for all work to complete
    if (vkQueueWaitIdle_) vkQueueWaitIdle_(mCtx.queue);

    for (auto& r : mPasses) {
        if (r.pipeline)     vkDestroyPipeline_(mCtx.device, r.pipeline, nullptr);
        if (r.layout)       vkDestroyPipelineLayout_(mCtx.device, r.layout, nullptr);
        if (r.descSetLayout) vkDestroyDescriptorSetLayout_(mCtx.device, r.descSetLayout, nullptr);
        if (r.renderPass)   vkDestroyRenderPass_(mCtx.device, r.renderPass, nullptr);
        if (r.framebuffer)  vkDestroyFramebuffer_(mCtx.device, r.framebuffer, nullptr);
        if (r.fbView)       vkDestroyImageView_(mCtx.device, r.fbView, nullptr);
        if (r.fbSampler)    vkDestroySampler_(mCtx.device, r.fbSampler, nullptr);
        if (r.fbMemory)     vkFreeMemory_(mCtx.device, r.fbMemory, nullptr);
        if (r.fbImage)      vkDestroyImage_(mCtx.device, r.fbImage, nullptr);
        if (r.vertModule)   vkDestroyShaderModule_(mCtx.device, r.vertModule, nullptr);
        if (r.fragModule)   vkDestroyShaderModule_(mCtx.device, r.fragModule, nullptr);
        if (r.ubo)          vkDestroyBuffer_(mCtx.device, r.ubo, nullptr);
        if (r.uboMemory)    vkFreeMemory_(mCtx.device, r.uboMemory, nullptr);
        if (r.feedbackView)  vkDestroyImageView_(mCtx.device, r.feedbackView, nullptr);
        if (r.feedbackMemory) vkFreeMemory_(mCtx.device, r.feedbackMemory, nullptr);
        if (r.feedbackImage) vkDestroyImage_(mCtx.device, r.feedbackImage, nullptr);
    }
    mPasses.clear();

    for (auto& h : mHistory) {
        if (h.view)   vkDestroyImageView_(mCtx.device, h.view, nullptr);
        if (h.memory) vkFreeMemory_(mCtx.device, h.memory, nullptr);
        if (h.image)  vkDestroyImage_(mCtx.device, h.image, nullptr);
    }
    mHistory.clear();

    if (mPreset) {
        for (auto& lut : mPreset->luts) {
            if (lut.view)    vkDestroyImageView_(mCtx.device, lut.view, nullptr);
            if (lut.sampler) vkDestroySampler_(mCtx.device, lut.sampler, nullptr);
            if (lut.memory)  vkFreeMemory_(mCtx.device, lut.memory, nullptr);
            if (lut.image)   vkDestroyImage_(mCtx.device, lut.image, nullptr);
        }
    }

    if (mDescriptorPool)
        vkDestroyDescriptorPool_(mCtx.device, mDescriptorPool, nullptr);
    if (mVertexBuffer)
        vkDestroyBuffer_(mCtx.device, mVertexBuffer, nullptr);
    if (mVertexMemory)
        vkFreeMemory_(mCtx.device, mVertexMemory, nullptr);
    if (mCommandPool)
        vkDestroyCommandPool_(mCtx.device, mCommandPool, nullptr);

    mValid = false;
}

// ---------------------------------------------------------------------------
// init — compile shaders, reflect, create pipelines
// ---------------------------------------------------------------------------

bool GammaVulkanFilterChain::init(const GammaVkContext& ctx, GammaSlangPreset& preset,
                                   int displayW, int displayH) {
    mCtx    = ctx;
    mPreset = &preset;

    if (!loadVkFuncs()) return false;

    ALOGI("GammaVkShader: initializing %zu passes from '%s' (display %dx%d)",
          preset.passes.size(), preset.presetPath.c_str(), displayW, displayH);

    // Create command pool
    VkCommandPoolCreateInfo poolInfo = {};
    poolInfo.sType            = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags            = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = ctx.queueFamily;
    if (vkCreateCommandPool_(ctx.device, &poolInfo, nullptr, &mCommandPool) != VK_SUCCESS)
        return false;

    mPasses.resize(preset.passes.size());

    // Phase 1: compile all shaders and collect parameters
    for (int i = 0; i < (int)preset.passes.size(); i++) {
        auto& pass = preset.passes[i];
        ALOGI("GammaVkShader: compiling pass %d: %s", i, pass.shaderPath.c_str());
        if (!compileSlangShader(pass.shaderPath, pass.vertexSpirv, pass.fragmentSpirv,
                                preset.params)) {
            ALOGE("GammaVkShader: failed to compile pass %d", i);
            return false;
        }
    }

    // Apply parameter overrides from preset
    for (auto& presetParam : preset.params) {
        // Params extracted from shaders have initial values; preset overrides them
        for (auto& shaderParam : preset.params) {
            if (shaderParam.id == presetParam.id && presetParam.current != 0.0f) {
                shaderParam.current = presetParam.current;
            }
        }
    }

    // Phase 2: reflect all passes
    for (int i = 0; i < (int)preset.passes.size(); i++) {
        if (!reflectPass(i)) {
            ALOGE("GammaVkShader: reflection failed for pass %d", i);
            return false;
        }
    }

    // Phase 3: create framebuffers for ALL passes (including final)
    int defaultW = displayW, defaultH = displayH;
    int currentW = defaultW, currentH = defaultH;
    for (int i = 0; i < (int)preset.passes.size(); i++) {
        bool isFinal = (i == (int)preset.passes.size() - 1);
        int passW, passH;
        if (isFinal) {
            passW = defaultW;
            passH = defaultH;
        } else {
            computePassSize(preset.passes[i], currentW, currentH,
                            defaultW, defaultH, passW, passH);
        }

        if (!createPassFramebuffer(i, passW, passH)) {
            ALOGE("GammaVkShader: framebuffer creation failed for pass %d", i);
            return false;
        }

        currentW = passW;
        currentH = passH;
    }

    // Phase 4: create UBOs
    for (int i = 0; i < (int)mPasses.size(); i++) {
        if (!createUBO(i)) {
            ALOGE("GammaVkShader: UBO creation failed for pass %d", i);
            return false;
        }
    }

    // Phase 5: create pipelines (also creates descSetLayout, pipelineLayout)
    for (int i = 0; i < (int)preset.passes.size(); i++) {
        if (!createPassPipeline(i, defaultW, defaultH)) {
            ALOGE("GammaVkShader: pipeline creation failed for pass %d", i);
            return false;
        }
    }

    // Phase 6: create descriptor pool and sets (needs descSetLayout from Phase 5)
    {
        int totalSets = (int)mPasses.size();
        int totalUbos = totalSets;
        int totalSamplers = 0;
        for (auto& r : mPasses) {
            int maxB = 0;
            auto check = [&](int b) { if (b > maxB) maxB = b; };
            check(r.texOriginal.binding);
            check(r.texSource.binding);
            for (int j = 0; j < 16; j++) {
                check(r.texPassOutput[j].binding);
                check(r.texPassFeedback[j].binding);
                check(r.texOriginalHistory[j].binding);
                check(r.texUser[j].binding);
            }
            totalSamplers += maxB; // bindings 1..maxB
        }
        totalSamplers = std::max(totalSamplers, 1);

        VkDescriptorPoolSize poolSizes[2] = {};
        poolSizes[0].type            = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        poolSizes[0].descriptorCount = (uint32_t)totalUbos;
        poolSizes[1].type            = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        poolSizes[1].descriptorCount = (uint32_t)totalSamplers;

        VkDescriptorPoolCreateInfo dpInfo = {};
        dpInfo.sType         = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
        dpInfo.maxSets       = (uint32_t)totalSets;
        dpInfo.poolSizeCount = 2;
        dpInfo.pPoolSizes    = poolSizes;

        if (vkCreateDescriptorPool_(mCtx.device, &dpInfo, nullptr,
                                    &mDescriptorPool) != VK_SUCCESS)
            return false;

        mDescriptorSets.resize(totalSets);
        std::vector<VkDescriptorSetLayout> layouts;
        for (auto& r : mPasses) layouts.push_back(r.descSetLayout);

        VkDescriptorSetAllocateInfo dsInfo = {};
        dsInfo.sType              = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        dsInfo.descriptorPool     = mDescriptorPool;
        dsInfo.descriptorSetCount = (uint32_t)totalSets;
        dsInfo.pSetLayouts        = layouts.data();

        if (vkAllocateDescriptorSets_(mCtx.device, &dsInfo,
                                      mDescriptorSets.data()) != VK_SUCCESS)
            return false;
    }

    // Phase 7: create vertex buffer (fullscreen quad)
    {
        // 4 vertices: Position (vec4) + TexCoord (vec2)
        // RetroArch convention: positions in [0,1] space, MVP transforms to clip [-1,1]
        float quadData[] = {
            // pos x, y, z, w,   uv u, v
            0.0f, 0.0f, 0.0f, 1.0f,  0.0f, 0.0f,  // bottom-left
            1.0f, 0.0f, 0.0f, 1.0f,  1.0f, 0.0f,  // bottom-right
            0.0f, 1.0f, 0.0f, 1.0f,  0.0f, 1.0f,  // top-left
            1.0f, 1.0f, 0.0f, 1.0f,  1.0f, 1.0f,  // top-right
        };

        VkBufferCreateInfo bufInfo = {};
        bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
        bufInfo.size  = sizeof(quadData);
        bufInfo.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

        if (vkCreateBuffer_(mCtx.device, &bufInfo, nullptr, &mVertexBuffer) != VK_SUCCESS)
            return false;

        VkMemoryRequirements memReqs;
        vkGetBufferMemoryRequirements_(mCtx.device, mVertexBuffer, &memReqs);

        VkMemoryAllocateInfo allocInfo = {};
        allocInfo.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        allocInfo.allocationSize  = memReqs.size;
        allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        if (vkAllocateMemory_(mCtx.device, &allocInfo, nullptr, &mVertexMemory) != VK_SUCCESS)
            return false;

        vkBindBufferMemory_(mCtx.device, mVertexBuffer, mVertexMemory, 0);

        void* data;
        vkMapMemory_(mCtx.device, mVertexMemory, 0, sizeof(quadData), 0, &data);
        memcpy(data, quadData, sizeof(quadData));
        vkUnmapMemory_(mCtx.device, mVertexMemory);
    }

    // Phase 8: load LUT textures
    {
        VkCommandBufferAllocateInfo cbInfo = {};
        cbInfo.sType              = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        cbInfo.commandPool        = mCommandPool;
        cbInfo.level              = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        cbInfo.commandBufferCount = 1;

        VkCommandBuffer cmd;
        vkAllocateCommandBuffers_(mCtx.device, &cbInfo, &cmd);

        VkCommandBufferBeginInfo beginInfo = {};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        vkBeginCommandBuffer_(cmd, &beginInfo);

        loadLutTextures(cmd);

        vkEndCommandBuffer_(cmd);

        VkSubmitInfo submitInfo = {};
        submitInfo.sType              = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers    = &cmd;
        vkQueueSubmit_(mCtx.queue, 1, &submitInfo, VK_NULL_HANDLE);
        vkQueueWaitIdle_(mCtx.queue);

        vkFreeCommandBuffers_(mCtx.device, mCommandPool, 1, &cmd);
    }

    mValid = true;
    ALOGI("GammaVkShader: initialized successfully with %zu passes", preset.passes.size());
    return true;
}

// ---------------------------------------------------------------------------
// LUT texture loading
// ---------------------------------------------------------------------------

bool GammaVulkanFilterChain::loadLutTexture(GammaSlangLut& lut, VkCommandBuffer cmd) {
    // Try to read the image file using basic TGA/PNG decoding
    // For now, create a 1x1 white placeholder (proper image loading TODO)
    // The key point is the pipeline supports it — image decoding can be added later
    lut.width  = 1;
    lut.height = 1;

    if (!createImage(1, 1, VK_FORMAT_R8G8B8A8_UNORM,
                     VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                     lut.image, lut.memory, lut.view))
        return false;

    if (!createSampler(lut.filterLinear, lut.mipmap, lut.sampler))
        return false;

    // Upload 1x1 white pixel via staging buffer
    uint32_t white = 0xFFFFFFFF;
    VkBuffer stagingBuf;
    VkDeviceMemory stagingMem;

    VkBufferCreateInfo bufInfo = {};
    bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufInfo.size  = 4;
    bufInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

    vkCreateBuffer_(mCtx.device, &bufInfo, nullptr, &stagingBuf);
    VkMemoryRequirements memReqs;
    vkGetBufferMemoryRequirements_(mCtx.device, stagingBuf, &memReqs);
    VkMemoryAllocateInfo allocInfo = {};
    allocInfo.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize  = memReqs.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReqs.memoryTypeBits,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    vkAllocateMemory_(mCtx.device, &allocInfo, nullptr, &stagingMem);
    vkBindBufferMemory_(mCtx.device, stagingBuf, stagingMem, 0);

    void* data;
    vkMapMemory_(mCtx.device, stagingMem, 0, 4, 0, &data);
    memcpy(data, &white, 4);
    vkUnmapMemory_(mCtx.device, stagingMem);

    transitionImage(cmd, lut.image,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

    VkBufferImageCopy region = {};
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.layerCount = 1;
    region.imageExtent = {1, 1, 1};
    vkCmdCopyBufferToImage_(cmd, stagingBuf, lut.image,
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    transitionImage(cmd, lut.image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

    // Note: staging buffer will be freed after queue idle (caller waits)
    // For safety, store and free later — but for now this is fine since we
    // wait on the queue in init().
    vkDestroyBuffer_(mCtx.device, stagingBuf, nullptr);
    vkFreeMemory_(mCtx.device, stagingMem, nullptr);

    return true;
}

bool GammaVulkanFilterChain::loadLutTextures(VkCommandBuffer cmd) {
    for (auto& lut : mPreset->luts) {
        if (!loadLutTexture(lut, cmd)) {
            ALOGW("GammaVkShader: failed to load LUT '%s', using placeholder", lut.id.c_str());
        }
    }
    return true;
}

// ---------------------------------------------------------------------------
// render — execute the filter chain for one frame
// ---------------------------------------------------------------------------

bool GammaVulkanFilterChain::render(
        VkCommandBuffer cmd,
        VkImageView srcView, VkSampler srcSampler,
        int srcW, int srcH,
        int viewW, int viewH,
        uint32_t frameCount) {

    if (!mValid || mPasses.empty()) return false;

    // Track current source for chaining
    VkImageView  currentSrcView    = srcView;
    VkSampler    currentSrcSampler = srcSampler;
    int          currentW          = srcW;
    int          currentH          = srcH;

    for (int i = 0; i < (int)mPasses.size(); i++) {
        auto& r    = mPasses[i];
        auto& pass = mPreset->passes[i];
        bool isFinal = (i == (int)mPasses.size() - 1);

        int passW, passH;
        if (isFinal) {
            passW = viewW;
            passH = viewH;
        } else {
            computePassSize(pass, currentW, currentH, viewW, viewH, passW, passH);
        }
        // Use FBO's actual size (may differ if not yet resized)
        passW = r.fbWidth;
        passH = r.fbHeight;

        // Update UBO
        fillUBO(i, currentW, currentH, viewW, viewH, passW, passH, frameCount);

        // Update descriptor set: UBO + textures
        {
            std::vector<VkWriteDescriptorSet> writes;
            std::vector<VkDescriptorBufferInfo> bufInfos;
            std::vector<VkDescriptorImageInfo> imgInfos;
            bufInfos.reserve(1);
            imgInfos.reserve(32);

            // UBO write
            VkDescriptorBufferInfo uboBuf = {};
            uboBuf.buffer = r.ubo;
            uboBuf.offset = 0;
            uboBuf.range  = r.uboSize;
            bufInfos.push_back(uboBuf);

            VkWriteDescriptorSet uboWrite = {};
            uboWrite.sType           = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            uboWrite.dstSet          = mDescriptorSets[i];
            uboWrite.dstBinding      = 0;
            uboWrite.descriptorCount = 1;
            uboWrite.descriptorType  = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            uboWrite.pBufferInfo     = &bufInfos.back();
            writes.push_back(uboWrite);

            auto addTex = [&](int binding, VkImageView view, VkSampler sampler) {
                if (binding < 0 || view == VK_NULL_HANDLE) return;
                VkDescriptorImageInfo imgInf = {};
                imgInf.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                imgInf.imageView   = view;
                imgInf.sampler     = sampler;
                imgInfos.push_back(imgInf);

                VkWriteDescriptorSet w = {};
                w.sType           = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
                w.dstSet          = mDescriptorSets[i];
                w.dstBinding      = binding;
                w.descriptorCount = 1;
                w.descriptorType  = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                w.pImageInfo      = &imgInfos.back();
                writes.push_back(w);
            };

            addTex(r.texSource.binding, currentSrcView, currentSrcSampler);
            addTex(r.texOriginal.binding, srcView, srcSampler);

            for (int j = 0; j < 16 && j < (int)mPasses.size(); j++) {
                if (r.texPassOutput[j].binding >= 0 && j < i && mPasses[j].fbView) {
                    addTex(r.texPassOutput[j].binding,
                           mPasses[j].fbView, mPasses[j].fbSampler);
                }
            }
            for (int j = 0; j < 16 && j < (int)mPasses.size(); j++) {
                if (r.texPassFeedback[j].binding >= 0 && mPasses[j].feedbackView) {
                    addTex(r.texPassFeedback[j].binding,
                           mPasses[j].feedbackView, mPasses[j].fbSampler);
                }
            }
            for (int j = 0; j < 16 && j < (int)mPreset->luts.size(); j++) {
                if (r.texUser[j].binding >= 0 && mPreset->luts[j].view) {
                    addTex(r.texUser[j].binding,
                           mPreset->luts[j].view, mPreset->luts[j].sampler);
                }
            }

            if (!writes.empty()) {
                vkUpdateDescriptorSets_(mCtx.device,
                    (uint32_t)writes.size(), writes.data(), 0, nullptr);
            }
        }

        // Transition FBO to attachment
        transitionImage(cmd, r.fbImage,
                        VK_IMAGE_LAYOUT_UNDEFINED,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        VkRenderPassBeginInfo rpBegin = {};
        rpBegin.sType             = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        rpBegin.renderPass        = r.renderPass;
        rpBegin.framebuffer       = r.framebuffer;
        rpBegin.renderArea.extent = {(uint32_t)passW, (uint32_t)passH};

        vkCmdBeginRenderPass_(cmd, &rpBegin, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline_(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, r.pipeline);
        vkCmdBindDescriptorSets_(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, r.layout,
                                 0, 1, &mDescriptorSets[i], 0, nullptr);

        VkViewport viewport = {};
        viewport.width  = (float)passW;
        viewport.height = (float)passH;
        viewport.maxDepth = 1.0f;
        vkCmdSetViewport_(cmd, 0, 1, &viewport);

        VkRect2D scissor = {};
        scissor.extent = {(uint32_t)passW, (uint32_t)passH};
        vkCmdSetScissor_(cmd, 0, 1, &scissor);

        // Push constants
        if (r.pushConstantSize > 0) {
            std::vector<uint8_t> pushData(r.pushConstantSize, 0);
            auto writeFloat4pc = [&](int offset, float x, float y, float z, float w) {
                if (offset < 0 || offset + 16 > (int)pushData.size()) return;
                float* p = (float*)(pushData.data() + offset);
                p[0] = x; p[1] = y; p[2] = z; p[3] = w;
            };
            auto writeUintPc = [&](int offset, uint32_t v) {
                if (offset < 0 || offset + 4 > (int)pushData.size()) return;
                *(uint32_t*)(pushData.data() + offset) = v;
            };
            auto writeFloatPc = [&](int offset, float v) {
                if (offset < 0 || offset + 4 > (int)pushData.size()) return;
                *(float*)(pushData.data() + offset) = v;
            };
            auto& po = r.pushOffsets;
            writeFloat4pc(po.outputSize, (float)passW, (float)passH,
                         1.0f / passW, 1.0f / passH);
            writeFloat4pc(po.sourceSize, (float)currentW, (float)currentH,
                         1.0f / currentW, 1.0f / currentH);
            writeFloat4pc(po.originalSize, (float)srcW, (float)srcH,
                         1.0f / srcW, 1.0f / srcH);
            uint32_t fc = frameCount;
            if (pass.frameCountMod > 0) fc %= (uint32_t)pass.frameCountMod;
            writeUintPc(po.frameCount, fc);
            for (int j = 0; j < (int)mPreset->params.size() &&
                             j < (int)po.paramOffsets.size(); j++) {
                writeFloatPc(po.paramOffsets[j].offset, mPreset->params[j].current);
            }
            vkCmdPushConstants_(cmd, r.layout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                0, r.pushConstantSize, pushData.data());
        }

        // Draw fullscreen quad
        VkDeviceSize offset = 0;
        vkCmdBindVertexBuffers_(cmd, 0, 1, &mVertexBuffer, &offset);
        vkCmdDraw_(cmd, 4, 1, 0, 0);

        vkCmdEndRenderPass_(cmd);

        // Transition to shader read for next pass (or for Skia blit if final)
        transitionImage(cmd, r.fbImage,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        currentSrcView    = r.fbView;
        currentSrcSampler = r.fbSampler;
        currentW          = passW;
        currentH          = passH;
    }

    return true;
}

VkImage GammaVulkanFilterChain::getOutputImage() const {
    return mPasses.empty() ? VK_NULL_HANDLE : mPasses.back().fbImage;
}

VkImageView GammaVulkanFilterChain::getOutputView() const {
    return mPasses.empty() ? VK_NULL_HANDLE : mPasses.back().fbView;
}

int GammaVulkanFilterChain::getOutputWidth() const {
    return mPasses.empty() ? 0 : mPasses.back().fbWidth;
}

int GammaVulkanFilterChain::getOutputHeight() const {
    return mPasses.empty() ? 0 : mPasses.back().fbHeight;
}

void GammaVulkanFilterChain::setParam(const std::string& id, float value) {
    if (!mPreset) return;
    for (auto& p : mPreset->params) {
        if (p.id == id) {
            p.current = std::clamp(value, p.minimum, p.maximum);
            return;
        }
    }
}

VkCommandBuffer GammaVulkanFilterChain::allocCommandBuffer() {
    VkCommandBufferAllocateInfo info = {};
    info.sType              = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    info.commandPool        = mCommandPool;
    info.level              = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    info.commandBufferCount = 1;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    vkAllocateCommandBuffers_(mCtx.device, &info, &cmd);
    return cmd;
}

void GammaVulkanFilterChain::freeCommandBuffer(VkCommandBuffer cmd) {
    if (cmd) vkFreeCommandBuffers_(mCtx.device, mCommandPool, 1, &cmd);
}

VkImageView GammaVulkanFilterChain::createExternalImageView(VkImage image, VkFormat format) {
    VkImageViewCreateInfo info = {};
    info.sType    = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    info.image    = image;
    info.viewType = VK_IMAGE_VIEW_TYPE_2D;
    info.format   = format;
    info.subresourceRange.aspectMask     = VK_IMAGE_ASPECT_COLOR_BIT;
    info.subresourceRange.levelCount     = 1;
    info.subresourceRange.layerCount     = 1;
    VkImageView view = VK_NULL_HANDLE;
    vkCreateImageView_(mCtx.device, &info, nullptr, &view);
    return view;
}

void GammaVulkanFilterChain::destroyImageView(VkImageView view) {
    if (view) vkDestroyImageView_(mCtx.device, view, nullptr);
}

bool GammaVulkanFilterChain::beginCommandBuffer(VkCommandBuffer cmd) {
    VkCommandBufferBeginInfo info = {};
    info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    info.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    return vkBeginCommandBuffer_(cmd, &info) == VK_SUCCESS;
}

bool GammaVulkanFilterChain::endCommandBuffer(VkCommandBuffer cmd) {
    return vkEndCommandBuffer_(cmd) == VK_SUCCESS;
}

bool GammaVulkanFilterChain::submitAndWait(VkCommandBuffer cmd) {
    VkFenceCreateInfo fenceInfo = {};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    VkFence fence = VK_NULL_HANDLE;
    if (vkCreateFence_(mCtx.device, &fenceInfo, nullptr, &fence) != VK_SUCCESS)
        return false;

    VkSubmitInfo submitInfo = {};
    submitInfo.sType              = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers    = &cmd;
    vkQueueSubmit_(mCtx.queue, 1, &submitInfo, fence);
    vkWaitForFences_(mCtx.device, 1, &fence, VK_TRUE, UINT64_MAX);
    vkDestroyFence_(mCtx.device, fence, nullptr);
    return true;
}

bool GammaVulkanFilterChain::ensureSrcTexture(int w, int h) {
    if (mSrcImage && mSrcW == w && mSrcH == h) return true;
    // Destroy old
    if (mSrcView)   vkDestroyImageView_(mCtx.device, mSrcView, nullptr);
    if (mSrcImage)  vkDestroyImage_(mCtx.device, mSrcImage, nullptr);
    if (mSrcMemory) vkFreeMemory_(mCtx.device, mSrcMemory, nullptr);
    mSrcView = VK_NULL_HANDLE; mSrcImage = VK_NULL_HANDLE; mSrcMemory = VK_NULL_HANDLE;

    if (!createImage(w, h, VK_FORMAT_R8G8B8A8_UNORM,
                     VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT,
                     mSrcImage, mSrcMemory, mSrcView))
        return false;
    if (!mSrcSampler) createSampler(true, false, mSrcSampler);
    mSrcW = w; mSrcH = h;
    return true;
}

bool GammaVulkanFilterChain::ensureStagingBuffer(VkDeviceSize size) {
    if (mStagingBuf && mStagingSize >= size) return true;
    if (mStagingBuf)    vkDestroyBuffer_(mCtx.device, mStagingBuf, nullptr);
    if (mStagingMemory) vkFreeMemory_(mCtx.device, mStagingMemory, nullptr);
    mStagingBuf = VK_NULL_HANDLE; mStagingMemory = VK_NULL_HANDLE;

    VkBufferCreateInfo bufInfo = {};
    bufInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufInfo.size  = size;
    bufInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    if (vkCreateBuffer_(mCtx.device, &bufInfo, nullptr, &mStagingBuf) != VK_SUCCESS)
        return false;

    VkMemoryRequirements memReq;
    vkGetBufferMemoryRequirements_(mCtx.device, mStagingBuf, &memReq);
    VkMemoryAllocateInfo allocInfo = {};
    allocInfo.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocInfo.allocationSize  = memReq.size;
    allocInfo.memoryTypeIndex = findMemoryType(memReq.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (vkAllocateMemory_(mCtx.device, &allocInfo, nullptr, &mStagingMemory) != VK_SUCCESS)
        return false;
    vkBindBufferMemory_(mCtx.device, mStagingBuf, mStagingMemory, 0);
    mStagingSize = size;
    return true;
}

bool GammaVulkanFilterChain::renderFromPixels(
        const void* srcPixels, int srcW, int srcH,
        void* outPixels, int dstW, int dstH,
        uint32_t frameCount) {
    if (!mValid) return false;

    // Ensure source texture and staging buffer
    VkDeviceSize srcSize = (VkDeviceSize)srcW * srcH * 4;
    VkDeviceSize outSize = (VkDeviceSize)getOutputWidth() * getOutputHeight() * 4;
    VkDeviceSize stagingNeeded = std::max(srcSize, outSize);
    if (!ensureSrcTexture(srcW, srcH) || !ensureStagingBuffer(stagingNeeded))
        return false;

    // Upload src pixels to staging buffer
    void* mapped = nullptr;
    vkMapMemory_(mCtx.device, mStagingMemory, 0, srcSize, 0, &mapped);
    memcpy(mapped, srcPixels, srcSize);
    vkUnmapMemory_(mCtx.device, mStagingMemory);

    // Record command buffer: upload → render → readback
    VkCommandBuffer cmd = allocCommandBuffer();
    if (!cmd) return false;
    beginCommandBuffer(cmd);

    // Transition src image to TRANSFER_DST
    transitionImage(cmd, mSrcImage, VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

    // Copy staging → src image
    VkBufferImageCopy region = {};
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.layerCount = 1;
    region.imageExtent = {(uint32_t)srcW, (uint32_t)srcH, 1};
    vkCmdCopyBufferToImage_(cmd, mStagingBuf, mSrcImage,
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

    // Transition src image to SHADER_READ_ONLY
    transitionImage(cmd, mSrcImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

    // Run filter chain
    bool renderOk = render(cmd, mSrcView, mSrcSampler,
                            srcW, srcH, dstW, dstH, frameCount);
    if (!renderOk) {
        endCommandBuffer(cmd);
        freeCommandBuffer(cmd);
        return false;
    }

    // Transition output to TRANSFER_SRC for readback
    VkImage outImage = getOutputImage();
    int outW = getOutputWidth();
    int outH = getOutputHeight();
    transitionImage(cmd, outImage, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

    // Copy output image → staging buffer
    VkBufferImageCopy outRegion = {};
    outRegion.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    outRegion.imageSubresource.layerCount = 1;
    outRegion.imageExtent = {(uint32_t)outW, (uint32_t)outH, 1};
    vkCmdCopyImageToBuffer_(cmd, outImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            mStagingBuf, 1, &outRegion);

    endCommandBuffer(cmd);
    submitAndWait(cmd);
    freeCommandBuffer(cmd);

    // Read back pixels
    mapped = nullptr;
    outSize = (VkDeviceSize)outW * outH * 4;
    vkMapMemory_(mCtx.device, mStagingMemory, 0, outSize, 0, &mapped);
    memcpy(outPixels, mapped, outSize);
    vkUnmapMemory_(mCtx.device, mStagingMemory);

    // Diagnostic: check if output has any non-zero pixels and fix alpha
    {
        static int sLogCount = 0;
        uint8_t* px = (uint8_t*)outPixels;
        int nonZero = 0, alphaZero = 0;
        uint8_t maxR = 0, maxG = 0, maxB = 0, maxA = 0;
        for (int i = 0; i < outW * outH; i++) {
            uint8_t r = px[i*4], g = px[i*4+1], b = px[i*4+2], a = px[i*4+3];
            if (r || g || b || a) nonZero++;
            if (a == 0 && (r || g || b)) alphaZero++;
            if (r > maxR) maxR = r;
            if (g > maxG) maxG = g;
            if (b > maxB) maxB = b;
            if (a > maxA) maxA = a;
        }
        if (sLogCount < 5) {
            ALOGI("GammaVkShader: readback %dx%d, nonZero=%d/%d, alphaZero=%d, "
                  "maxRGBA=(%d,%d,%d,%d), px[0]=(%d,%d,%d,%d)",
                  outW, outH, nonZero, outW*outH, alphaZero,
                  maxR, maxG, maxB, maxA,
                  px[0], px[1], px[2], px[3]);
            sLogCount++;
        }
        // Force alpha to 255 if shader doesn't set it (common for CRT shaders)
        if (maxA == 0 && nonZero > 0) {
            for (int i = 0; i < outW * outH; i++) {
                px[i*4+3] = 255;
            }
        }
    }

    return true;
}

VkFormat GammaVulkanFilterChain::getOutputFormat() const {
    return mPasses.empty() ? VK_FORMAT_R8G8B8A8_UNORM
                           : VK_FORMAT_R8G8B8A8_UNORM; // FBOs always use RGBA8
}

bool GammaVulkanFilterChain::renderFromVkImage(
        VkImage srcImage, int srcW, int srcH,
        int viewW, int viewH, uint32_t frameCount) {
    if (!mValid) return false;

    // Ensure we have a view and sampler for the external source image
    if (!ensureSrcTexture(srcW, srcH)) return false;

    VkCommandBuffer cmd = allocCommandBuffer();
    if (!cmd) return false;
    beginCommandBuffer(cmd);

    // Transition the external source image from whatever layout Skia left it in
    // to SHADER_READ_ONLY for our pipeline.
    // Use GENERAL as source layout since Skia may leave images in various states.
    transitionImage(cmd, srcImage, VK_IMAGE_LAYOUT_GENERAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

    // Create a temporary VkImageView for the external source
    VkImageViewCreateInfo viewInfo = {};
    viewInfo.sType    = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image    = srcImage;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format   = VK_FORMAT_R8G8B8A8_UNORM;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.layerCount = 1;

    VkImageView srcView = VK_NULL_HANDLE;
    if (vkCreateImageView_(mCtx.device, &viewInfo, nullptr, &srcView) != VK_SUCCESS) {
        endCommandBuffer(cmd);
        freeCommandBuffer(cmd);
        return false;
    }

    // Run filter chain
    bool renderOk = render(cmd, srcView, mSrcSampler,
                           srcW, srcH, viewW, viewH, frameCount);

    // Transition source back to GENERAL for Skia
    transitionImage(cmd, srcImage, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_GENERAL);

    // Transition output to SHADER_READ_ONLY so Skia can sample it
    VkImage outImage = getOutputImage();
    transitionImage(cmd, outImage, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_IMAGE_LAYOUT_GENERAL);

    endCommandBuffer(cmd);
    submitAndWait(cmd);
    freeCommandBuffer(cmd);

    vkDestroyImageView_(mCtx.device, srcView, nullptr);

    return renderOk;
}

// ---------------------------------------------------------------------------
// Global state for the apply() entry point
// ---------------------------------------------------------------------------

static std::mutex sChainMutex;
static std::unique_ptr<GammaVulkanFilterChain> sChain;
static GammaSlangPreset sPreset;
static std::string sLoadedPresetPath;
static std::string sFailedPresetPath;  // avoid retrying failed shaders
static std::string sMetaWrittenPath;   // preset whose param meta is already published
static uint32_t sFrameCount = 0;
static int sChainDisplayW = 0;
static int sChainDisplayH = 0;

// surfaceflinger can't access /sdcard/ (FUSE), so remap to /data/media/0/
static std::string fixSdcardPath(const std::string& path) {
    const char* prefix = "/sdcard/";
    if (path.compare(0, 8, prefix) == 0)
        return "/data/media/0/" + path.substr(8);
    const char* prefix2 = "/storage/emulated/0/";
    if (path.compare(0, 20, prefix2) == 0)
        return "/data/media/0/" + path.substr(20);
    return path;
}

static bool ensureChainLoaded(bool debugLog, int displayW = 1920, int displayH = 1080) {
    std::string presetPath = fixSdcardPath(
            GetProperty("persist.gammaos.shader.custom.preset", ""));
    if (presetPath.empty()) {
        // Default search paths
        const char* searchDirs[] = {
            "/data/media/0/GammaShader",
            "/sdcard/GammaShader",
            "/sdcard/RetroArch/shaders",
        };
        for (auto dir : searchDirs) {
            std::string candidate = std::string(dir) + "/current.slangp";
            if (!readFile(candidate).empty()) {
                presetPath = candidate;
                break;
            }
        }
    }

    if (presetPath.empty()) return false;

    // Check if we need to reload (preset changed or display size grew)
    if (sChain && sChain->isValid() && sLoadedPresetPath == presetPath &&
        sChainDisplayW >= displayW && sChainDisplayH >= displayH) {
        return true;
    }

    // Don't retry a path that already failed (at the same dimensions)
    if (presetPath == sFailedPresetPath &&
        sChainDisplayW == displayW && sChainDisplayH == displayH)
        return false;

    // Load preset
    sPreset = {};
    if (!parseGammaSlangPreset(presetPath, sPreset)) {
        ALOGE("GammaVkShader: failed to parse preset '%s'", presetPath.c_str());
        return false;
    }

    // Get Vulkan context
    GammaVkContext ctx;
    if (!gammaGetVkContext(ctx)) {
        ALOGE("GammaVkShader: failed to get Vulkan context");
        return false;
    }

    // Create and init chain
    auto chain = std::make_unique<GammaVulkanFilterChain>();
    if (!chain->init(ctx, sPreset, displayW, displayH)) {
        ALOGE("GammaVkShader: chain init failed for '%s'", presetPath.c_str());
        sFailedPresetPath = presetPath;
        return false;
    }

    sChain = std::move(chain);
    sLoadedPresetPath = presetPath;
    sChainDisplayW = displayW;
    sChainDisplayH = displayH;

    // Publish parameter metadata so the on-screen UI (nano GammaShader menu /
    // ShaderControl app) can build sliders for this preset's #pragma parameters.
    // Format matches the SkSL/GLSL chains: one line per param
    // id|desc|initial|min|max|step. Only rewrite when the preset path changes so
    // we do not clobber live overrides on a display-size-driven reload.
    if (sLoadedPresetPath != sMetaWrittenPath) {
        const std::string metaDir = "/data/media/0/GammaShader";
        mkdir(metaDir.c_str(), 0775);
        const std::string metaPath = metaDir + "/.shader_param_meta";
        std::ofstream meta(metaPath, std::ios::trunc);
        if (meta.is_open()) {
            for (const auto& p : sPreset.params) {
                meta << p.id << "|" << p.desc << "|"
                     << p.initial << "|" << p.minimum << "|"
                     << p.maximum << "|" << p.step << "\n";
            }
            meta.close();
            chmod(metaPath.c_str(), 0664);
        }
        // Clear any stale param overrides from a previous preset so the new one
        // starts at its declared initials.
        const std::string paramsPath = metaDir + "/.shader_params";
        std::ofstream clear(paramsPath, std::ios::trunc);
        clear.close();
        chmod(paramsPath.c_str(), 0666);
        sMetaWrittenPath = sLoadedPresetPath;
    }

    return true;
}


// ---------------------------------------------------------------------------
// apply() — public entry point, drop-in replacement for GammaCustomShader
// ---------------------------------------------------------------------------

bool GammaVulkanShaderChain::apply(SkSurface* dstSurface,
                                    SkSurface* srcSurface,
                                    SkiaCapture* capture,
                                    ui::Dataspace /*outDataspace*/,
                                    bool isProtected,
                                    bool testOverlay,
                                    bool /*ctmBfiBlack*/,
                                    float /*defaultScanAngleDeg*/) {
    if (!dstSurface || !srcSurface || !capture) return false;

    const bool debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);
    const bool shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);
    const std::string type = GetProperty("persist.gammaos.shader.type", "crt-simple");
    if (!shaderOn || type != "custom-vk") return false;

    if (!GetBoolProperty("sys.boot_completed", false)) return false;

    std::lock_guard<std::mutex> lock(sChainMutex);

    if (!ensureChainLoaded(debugLog, dstSurface->width(), dstSurface->height())) return false;
    if (!sChain || !sChain->isValid()) return false;

    // Apply parameter overrides from params file
    {
        static std::string sLastParamsContent;
        const std::string paramsPath = "/data/media/0/GammaShader/.shader_params";
        std::string content = readFile(paramsPath);
        if (content != sLastParamsContent) {
            sLastParamsContent = content;
            // Reset all params to initial
            for (auto& p : sPreset.params) p.current = p.initial;
            // Parse overrides
            std::istringstream pStream(content);
            std::string pLine;
            while (std::getline(pStream, pLine)) {
                std::string trimLine = trim(pLine);
                if (trimLine.empty() || trimLine[0] == '#') continue;
                auto eq = trimLine.find('=');
                if (eq == std::string::npos) continue;
                std::string pId = trim(trimLine.substr(0, eq));
                std::string pVal = trim(trimLine.substr(eq + 1));
                sChain->setParam(pId, std::strtof(pVal.c_str(), nullptr));
            }
        }
    }

    sFrameCount++;

    // --- Get destination canvas ---
    SkCanvas* dstCanvas = capture->tryCapture(dstSurface);
    if (!dstCanvas) return false;

    // --- Get source image snapshot ---
    sk_sp<SkImage> srcSkImage = srcSurface->makeImageSnapshot();
    if (!srcSkImage) return false;

    int srcW = srcSkImage->width();
    int srcH = srcSkImage->height();
    int dstW = dstSurface->width();
    int dstH = dstSurface->height();

    if (debugLog) {
        ALOGD("GammaVkShader: apply() src=%dx%d dst=%dx%d", srcW, srcH, dstW, dstH);
    }

    // Skip small surfaces (screen captures, thumbnails, cursors)
    if (dstW < 480 || dstH < 320) return false;

    // --- Check if Skia Vulkan backend is active (GPU-direct path) ---
    GrDirectContext* grContext =
            static_cast<GrDirectContext*>(srcSurface->recordingContext());

    // Try to extract VkImage from source — if this works, we're on Vulkan backend
    GrBackendTexture srcBackendTex;
    GrVkImageInfo srcVkInfo = {};
    bool skiaVkBackend = false;
    if (grContext) {
        bool hasTex = SkImages::GetBackendTextureFromImage(
                srcSkImage, &srcBackendTex, false /* flushPendingGrContextIO */);
        if (hasTex && srcBackendTex.isValid()) {
            skiaVkBackend = GrBackendTextures::GetVkImageInfo(srcBackendTex, &srcVkInfo);
        }
    }

    {
        static bool sLoggedBackend = false;
        if (!sLoggedBackend && dstW >= 480) {
            ALOGI("GammaVkShader: using %s path (grContext=%p, skiaVk=%d) %dx%d",
                  skiaVkBackend ? "GPU-direct(VK)" : "CPU-readback",
                  grContext, skiaVkBackend ? 1 : 0, dstW, dstH);
            sLoggedBackend = true;
        }
    }

    if (skiaVkBackend) {

        // Flush Skia's pending work before we touch the VkImage
        grContext->flushAndSubmit(GrSyncCpu::kYes);

        // Run Vulkan filter chain directly on the VkImage
        bool renderOk = sChain->renderFromVkImage(
                srcVkInfo.fImage, srcW, srcH, dstW, dstH, sFrameCount);
        if (!renderOk) {
            if (debugLog) ALOGE("GammaVkShader: GPU-direct: renderFromVkImage failed");
            return false;
        }

        int outW = sChain->getOutputWidth();
        int outH = sChain->getOutputHeight();

        // Wrap Vulkan output as GrBackendTexture → SkImage
        GrVkImageInfo outVkInfo = {};
        outVkInfo.fImage       = sChain->getOutputImage();
        outVkInfo.fImageTiling = VK_IMAGE_TILING_OPTIMAL;
        outVkInfo.fImageLayout = VK_IMAGE_LAYOUT_GENERAL;
        outVkInfo.fFormat      = VK_FORMAT_R8G8B8A8_UNORM;
        outVkInfo.fImageUsageFlags = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT |
                                     VK_IMAGE_USAGE_SAMPLED_BIT;
        outVkInfo.fLevelCount  = 1;
        outVkInfo.fCurrentQueueFamily = sChain->getContext().queueFamily;

        GrBackendTexture outBackendTex =
                GrBackendTextures::MakeVk(outW, outH, outVkInfo);

        sk_sp<SkImage> outputSkImage = SkImages::BorrowTextureFrom(
                grContext, outBackendTex,
                kTopLeft_GrSurfaceOrigin,
                kRGBA_8888_SkColorType,
                kOpaque_SkAlphaType,
                nullptr /* colorSpace */);
        if (!outputSkImage) {
            if (debugLog) ALOGE("GammaVkShader: GPU-direct: BorrowTextureFrom failed");
            return false;
        }

        SkPaint paint;
        paint.setBlendMode(SkBlendMode::kSrc);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawImageRect(outputSkImage,
                SkRect::MakeWH(outW, outH),
                SkRect::MakeWH(dstW, dstH),
                SkSamplingOptions(SkFilterMode::kLinear),
                &paint, SkCanvas::kFast_SrcRectConstraint);
        dstCanvas->restore();

        if (debugLog) {
            ALOGD("GammaVkShader: GPU-direct frame %u, %dx%d -> %dx%d",
                  sFrameCount, srcW, srcH, outW, outH);
        }
    } else {
        // ===== CPU READBACK PATH: fallback when VK backend is not available =====

        int outW = sChain->getOutputWidth();
        int outH = sChain->getOutputHeight();
        if (outW <= 0 || outH <= 0) { outW = dstW; outH = dstH; }

        SkImageInfo readInfo = SkImageInfo::Make(srcW, srcH,
                kRGBA_8888_SkColorType, kPremul_SkAlphaType);
        std::vector<uint8_t> srcPixels(srcW * srcH * 4);

        if (!srcSkImage->readPixels(readInfo, srcPixels.data(), srcW * 4, 0, 0)) {
            if (!grContext || !srcSkImage->readPixels(grContext, readInfo,
                    srcPixels.data(), srcW * 4, 0, 0)) {
                if (debugLog) ALOGE("GammaVkShader: CPU path: failed to read source pixels");
                return false;
            }
        }

        std::vector<uint8_t> outPixels(outW * outH * 4);

        bool renderOk = sChain->renderFromPixels(
                srcPixels.data(), srcW, srcH,
                outPixels.data(), dstW, dstH,
                sFrameCount);
        if (!renderOk) {
            if (debugLog) ALOGE("GammaVkShader: CPU path: renderFromPixels failed");
            return false;
        }

        outW = sChain->getOutputWidth();
        outH = sChain->getOutputHeight();

        SkImageInfo outInfo = SkImageInfo::Make(outW, outH,
                kRGBA_8888_SkColorType, kPremul_SkAlphaType);
        sk_sp<SkData> outData = SkData::MakeWithCopy(outPixels.data(), outW * outH * 4);
        sk_sp<SkImage> outputSkImage = SkImages::RasterFromData(outInfo, outData, outW * 4);
        if (!outputSkImage) return false;

        SkPaint paint;
        paint.setBlendMode(SkBlendMode::kSrc);
        dstCanvas->save();
        dstCanvas->resetMatrix();
        dstCanvas->drawImageRect(outputSkImage,
                SkRect::MakeWH(outW, outH),
                SkRect::MakeWH(dstW, dstH),
                SkSamplingOptions(SkFilterMode::kLinear),
                &paint, SkCanvas::kFast_SrcRectConstraint);
        dstCanvas->restore();

        if (debugLog) {
            ALOGD("GammaVkShader: CPU path frame %u, %dx%d -> %dx%d",
                  sFrameCount, srcW, srcH, outW, outH);
        }
    }

    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android
