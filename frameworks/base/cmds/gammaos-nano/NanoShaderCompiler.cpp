/*
 * Copyright (C) 2026 GammaOS
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

// nano_shaderc: turns one RetroArch .slang shader stage pair (Vulkan GLSL 450) into GLSL ES
// 3.00 for the DRM-direct home's shader chain (NanoGlobalShader.cpp), the way RetroArch's own
// GL core driver does: glslang compiles the stage to SPIR-V and SPIRV-Cross writes it back out
// as GLSL ES. Uniform blocks (the UBO and the push constant block, which becomes a uniform
// block too) and samplers keep their names, so the chain binds them by name through GL
// introspection.
//
// A separate program rather than a library inside nano: glslang and SPIRV-Cross add tens of
// megabytes that would otherwise sit locked in the home's memory (mlockall), and SPIRV-Cross is
// built to abort on an unsupported construct, which must not take the home down. A preset that
// cannot be compiled fails here, and the home reports it.
//
//   nano_shaderc <vertex.glsl> <fragment.glsl> <out.vert> <out.frag>
//
// Exit 0 with the two outputs written, else 1 with the reason on stderr.

#include <stdio.h>
#include <string.h>

#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include <glslang/Public/ShaderLang.h>
#include <SPIRV/GlslangToSpv.h>
#include <spirv_glsl.hpp>

namespace {

std::string readAll(const char* path) {
    std::ifstream f(path, std::ios::binary);
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

bool writeAll(const char* path, const std::string& text) {
    std::ofstream f(path, std::ios::binary | std::ios::trunc);
    if (!f) return false;
    f << text;
    return (bool)f;
}

TBuiltInResource defaultResources() {
    TBuiltInResource r = {};
    r.maxLights = 32; r.maxClipPlanes = 6; r.maxTextureUnits = 32; r.maxTextureCoords = 32;
    r.maxVertexAttribs = 64; r.maxVertexUniformComponents = 4096; r.maxVaryingFloats = 64;
    r.maxVertexTextureImageUnits = 32; r.maxCombinedTextureImageUnits = 80;
    r.maxTextureImageUnits = 32; r.maxFragmentUniformComponents = 4096; r.maxDrawBuffers = 32;
    r.maxVertexUniformVectors = 128; r.maxVaryingVectors = 8; r.maxFragmentUniformVectors = 16;
    r.maxVertexOutputVectors = 16; r.maxFragmentInputVectors = 15; r.minProgramTexelOffset = -8;
    r.maxProgramTexelOffset = 7; r.maxClipDistances = 8; r.maxComputeWorkGroupCountX = 65535;
    r.maxComputeWorkGroupCountY = 65535; r.maxComputeWorkGroupCountZ = 65535;
    r.maxComputeWorkGroupSizeX = 1024; r.maxComputeWorkGroupSizeY = 1024;
    r.maxComputeWorkGroupSizeZ = 64; r.maxComputeUniformComponents = 1024;
    r.maxComputeTextureImageUnits = 16; r.maxComputeImageUniforms = 8;
    r.maxComputeAtomicCounters = 8; r.maxComputeAtomicCounterBuffers = 1;
    r.maxVaryingComponents = 60; r.maxVertexOutputComponents = 64;
    r.maxGeometryInputComponents = 64; r.maxGeometryOutputComponents = 128;
    r.maxFragmentInputComponents = 128; r.maxImageUnits = 8;
    r.maxCombinedImageUnitsAndFragmentOutputs = 8; r.maxCombinedShaderOutputResources = 8;
    r.maxImageSamples = 0; r.maxVertexImageUniforms = 0; r.maxTessControlImageUniforms = 0;
    r.maxTessEvaluationImageUniforms = 0; r.maxGeometryImageUniforms = 0;
    r.maxFragmentImageUniforms = 8; r.maxCombinedImageUniforms = 8;
    r.maxGeometryTextureImageUnits = 16; r.maxGeometryOutputVertices = 256;
    r.maxGeometryTotalOutputComponents = 1024; r.maxGeometryUniformComponents = 1024;
    r.maxGeometryVaryingComponents = 64; r.maxTessControlInputComponents = 128;
    r.maxTessControlOutputComponents = 128; r.maxTessControlTextureImageUnits = 16;
    r.maxTessControlUniformComponents = 1024; r.maxTessControlTotalOutputComponents = 4096;
    r.maxTessEvaluationInputComponents = 128; r.maxTessEvaluationOutputComponents = 128;
    r.maxTessEvaluationTextureImageUnits = 16; r.maxTessEvaluationUniformComponents = 1024;
    r.maxTessPatchComponents = 120; r.maxPatchVertices = 32; r.maxTessGenLevel = 64;
    r.maxViewports = 16; r.maxVertexAtomicCounters = 0; r.maxTessControlAtomicCounters = 0;
    r.maxTessEvaluationAtomicCounters = 0; r.maxGeometryAtomicCounters = 0;
    r.maxFragmentAtomicCounters = 8; r.maxCombinedAtomicCounters = 8;
    r.maxAtomicCounterBindings = 1; r.maxVertexAtomicCounterBuffers = 0;
    r.maxTessControlAtomicCounterBuffers = 0; r.maxTessEvaluationAtomicCounterBuffers = 0;
    r.maxGeometryAtomicCounterBuffers = 0; r.maxFragmentAtomicCounterBuffers = 1;
    r.maxCombinedAtomicCounterBuffers = 1; r.maxAtomicCounterBufferSize = 16384;
    r.maxTransformFeedbackBuffers = 4; r.maxTransformFeedbackInterleavedComponents = 64;
    r.maxCullDistances = 8; r.maxCombinedClipAndCullDistances = 8; r.maxSamples = 4;
    r.limits.nonInductiveForLoops = true; r.limits.whileLoops = true;
    r.limits.doWhileLoops = true; r.limits.generalUniformIndexing = true;
    r.limits.generalAttributeMatrixVectorIndexing = true; r.limits.generalVaryingIndexing = true;
    r.limits.generalSamplerIndexing = true; r.limits.generalVariableIndexing = true;
    r.limits.generalConstantMatrixVectorIndexing = true;
    return r;
}

bool toSpirv(const std::string& source, EShLanguage stage, std::vector<uint32_t>& spirv,
             std::string& error) {
    static TBuiltInResource resources = defaultResources();
    glslang::TShader shader(stage);
    const char* src = source.c_str();
    shader.setStrings(&src, 1);
    const EShMessages messages = (EShMessages)(EShMsgDefault | EShMsgVulkanRules | EShMsgSpvRules);
    if (!shader.parse(&resources, 100, false, messages)) {
        error = shader.getInfoLog();
        return false;
    }
    glslang::TProgram program;
    program.addShader(&shader);
    if (!program.link(messages)) {
        error = program.getInfoLog();
        return false;
    }
    glslang::GlslangToSpv(*program.getIntermediate(stage), spirv);
    if (spirv.empty()) { error = "empty SPIR-V"; return false; }
    return true;
}

std::string toGlslEs(const std::vector<uint32_t>& spirv) {
    spirv_cross::CompilerGLSL comp(spirv);
    spirv_cross::CompilerGLSL::Options opt;
    opt.version = 300;
    opt.es = true;
    opt.vulkan_semantics = false;
    opt.emit_push_constant_as_uniform_buffer = true;
    opt.enable_420pack_extension = false;
    opt.fragment.default_float_precision = spirv_cross::CompilerGLSL::Options::Highp;
    opt.fragment.default_int_precision = spirv_cross::CompilerGLSL::Options::Highp;
    opt.vertex.fixup_clipspace = false;
    opt.vertex.flip_vert_y = false;
    comp.set_common_options(opt);
    return comp.compile();
}

}  // namespace

int main(int argc, char** argv) {
    if (argc != 5) {
        fprintf(stderr, "usage: nano_shaderc <vertex.glsl> <fragment.glsl> <out.vert> <out.frag>\n");
        return 2;
    }
    const std::string vert = readAll(argv[1]);
    const std::string frag = readAll(argv[2]);
    if (vert.empty() || frag.empty()) {
        fprintf(stderr, "cannot read the stage sources\n");
        return 1;
    }
    glslang::InitializeProcess();
    std::vector<uint32_t> vsSpv, fsSpv;
    std::string error;
    if (!toSpirv(vert, EShLangVertex, vsSpv, error)) {
        fprintf(stderr, "vertex: %s\n", error.c_str());
        glslang::FinalizeProcess();
        return 1;
    }
    if (!toSpirv(frag, EShLangFragment, fsSpv, error)) {
        fprintf(stderr, "fragment: %s\n", error.c_str());
        glslang::FinalizeProcess();
        return 1;
    }
    glslang::FinalizeProcess();
    // SPIRV-Cross aborts on a construct it cannot express; the home sees the exit status.
    const std::string vsGlsl = toGlslEs(vsSpv);
    const std::string fsGlsl = toGlslEs(fsSpv);
    if (vsGlsl.empty() || fsGlsl.empty()) {
        fprintf(stderr, "SPIRV-Cross produced no GLSL\n");
        return 1;
    }
    if (!writeAll(argv[3], vsGlsl) || !writeAll(argv[4], fsGlsl)) {
        fprintf(stderr, "cannot write the outputs\n");
        return 1;
    }
    return 0;
}
