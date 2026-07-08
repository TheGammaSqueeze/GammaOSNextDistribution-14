// GammaGLSLShaderChain: native GLSL shader pipeline for RetroArch .glslp
// presets.  Compiles .glsl shaders directly as GLES programs — no SPIR-V
// transpilation needed.  Renders through GL FBOs within Skia's GL context.

#undef LOG_TAG
#define LOG_TAG "GammaGLShader"
#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include "GammaGLSLShaderChain.h"

#include <android-base/properties.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <GrDirectContext.h>
#include <GrBackendSurface.h>
#include <include/gpu/ganesh/SkImageGanesh.h>
#include <include/gpu/ganesh/SkSurfaceGanesh.h>
#include <include/gpu/ganesh/gl/GrGLBackendSurface.h>
#include <include/gpu/gl/GrGLTypes.h>

#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>
#include <GLES2/gl2ext.h>

#include <SkCanvas.h>
#include <SkColorSpace.h>
#include <SkImage.h>
#include <SkPaint.h>
#include <SkRect.h>

#include "../debug/SkiaCapture.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <fstream>
#include <mutex>
#include <sstream>
#include <sys/stat.h>
#include <vector>

using android::base::GetBoolProperty;
using android::base::GetProperty;
using android::base::SetProperty;

// Publishes why the custom GLSL preset can (or cannot) render so the nano menu
// can surface it. Volatile sys. prop: "ok" | "passes:<n>/<max>" | "parse_fail" |
// "compile_fail". Throttled so it only writes on a change.
static void setShaderStatus(const std::string& s) {
    static std::string last;
    if (last != s) { last = s; SetProperty("sys.gammaos.shader.status", s); }
}

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

// Normalize /sdcard/ and /storage/emulated/0/ to /data/media/0/ so
// SurfaceFlinger can read shader files without relying on FUSE mounts.
static std::string normalizeStoragePath(const std::string& path) {
    static const char* kSdcard = "/sdcard/";
    static const char* kStorage = "/storage/emulated/0/";
    static const char* kDataMedia = "/data/media/0/";
    if (path.compare(0, strlen(kSdcard), kSdcard) == 0)
        return kDataMedia + path.substr(strlen(kSdcard));
    if (path.compare(0, strlen(kStorage), kStorage) == 0)
        return kDataMedia + path.substr(strlen(kStorage));
    return path;
}

// ---------------------------------------------------------------------------
// .glslp preset parser
// ---------------------------------------------------------------------------

struct GLSLParam {
    std::string id;
    std::string desc;
    float initial = 0, minimum = 0, maximum = 0, step = 0;
    float current = 0;
};

struct GLSLPassDef {
    std::string shaderPath;
    bool filterLinear = false;
    // Scale: "source" (default), "viewport", "absolute"
    std::string scaleTypeX = "source";
    std::string scaleTypeY = "source";
    float scaleX = 1.0f;
    float scaleY = 1.0f;
    std::string alias;
    int frameCountMod = 0;
    bool floatFramebuffer = false;
    bool srgbFramebuffer = false;
    bool mipmapInput = false;
    std::string wrapMode = "clamp_to_border";
};

struct GLSLPreset {
    std::vector<GLSLPassDef> passes;
    std::vector<GLSLParam> params;
};

static bool parseGLSLPreset(const std::string& path, GLSLPreset& out) {
    std::string content = readFile(path);
    if (content.empty()) {
        ALOGE("GammaGLShader: cannot read preset '%s'", path.c_str());
        return false;
    }

    std::string baseDir = dirOf(path);
    int numShaders = 0;

    // First pass: find shader count
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        std::string t = trim(line);
        if (t.empty() || t[0] == '#') continue;
        auto eq = t.find('=');
        if (eq == std::string::npos) continue;
        std::string key = trim(t.substr(0, eq));
        std::string val = trim(t.substr(eq + 1));
        // Strip quotes
        if (val.size() >= 2 && val.front() == '"' && val.back() == '"')
            val = val.substr(1, val.size() - 2);

        if (key == "shaders") {
            numShaders = std::atoi(val.c_str());
        }
    }

    if (numShaders <= 0 || numShaders > 32) {
        ALOGE("GammaGLShader: invalid shader count %d in '%s'", numShaders, path.c_str());
        return false;
    }

    out.passes.resize(numShaders);

    // Second pass: parse all keys
    stream.clear();
    stream.str(content);
    while (std::getline(stream, line)) {
        std::string t = trim(line);
        if (t.empty() || t[0] == '#') continue;
        auto eq = t.find('=');
        if (eq == std::string::npos) continue;
        std::string key = trim(t.substr(0, eq));
        std::string val = trim(t.substr(eq + 1));
        if (val.size() >= 2 && val.front() == '"' && val.back() == '"')
            val = val.substr(1, val.size() - 2);

        // Parse indexed keys like "shader0", "filter_linear1", etc.
        for (int i = 0; i < numShaders; i++) {
            std::string idx = std::to_string(i);
            if (key == "shader" + idx) {
                out.passes[i].shaderPath = resolvePath(baseDir, val);
            } else if (key == "filter_linear" + idx) {
                out.passes[i].filterLinear = (val == "true" || val == "1");
            } else if (key == "scale_type" + idx) {
                out.passes[i].scaleTypeX = val;
                out.passes[i].scaleTypeY = val;
            } else if (key == "scale_type_x" + idx) {
                out.passes[i].scaleTypeX = val;
            } else if (key == "scale_type_y" + idx) {
                out.passes[i].scaleTypeY = val;
            } else if (key == "scale" + idx) {
                out.passes[i].scaleX = std::strtof(val.c_str(), nullptr);
                out.passes[i].scaleY = out.passes[i].scaleX;
            } else if (key == "scale_x" + idx) {
                out.passes[i].scaleX = std::strtof(val.c_str(), nullptr);
            } else if (key == "scale_y" + idx) {
                out.passes[i].scaleY = std::strtof(val.c_str(), nullptr);
            } else if (key == "alias" + idx) {
                out.passes[i].alias = val;
            } else if (key == "frame_count_mod" + idx) {
                out.passes[i].frameCountMod = std::atoi(val.c_str());
            } else if (key == "float_framebuffer" + idx) {
                out.passes[i].floatFramebuffer = (val == "true" || val == "1");
            } else if (key == "srgb_framebuffer" + idx) {
                out.passes[i].srgbFramebuffer = (val == "true" || val == "1");
            } else if (key == "mipmap_input" + idx) {
                out.passes[i].mipmapInput = (val == "true" || val == "1");
            } else if (key == "wrap_mode" + idx) {
                out.passes[i].wrapMode = val;
            }
        }
    }

    // Parse #pragma parameter from all shader source files
    for (auto& pass : out.passes) {
        std::string src = readFile(pass.shaderPath);
        if (src.empty()) continue;
        std::istringstream ss(src);
        std::string sl;
        while (std::getline(ss, sl)) {
            std::string tl = trim(sl);
            if (tl.find("#pragma parameter") != 0) continue;
            GLSLParam p;
            char idBuf[64] = {}, descBuf[128] = {};
            float init = 0, mn = 0, mx = 0, st = 0;
            int n = sscanf(tl.c_str(),
                "#pragma parameter %63s \"%127[^\"]\" %f %f %f %f",
                idBuf, descBuf, &init, &mn, &mx, &st);
            if (n >= 4) {
                p.id = idBuf;
                p.desc = descBuf;
                p.initial = init;
                p.minimum = mn;
                p.maximum = mx;
                p.step = (n >= 6) ? st : 0.01f;
                p.current = init;
                // Avoid duplicates
                bool dup = false;
                for (auto& ep : out.params) {
                    if (ep.id == p.id) { dup = true; break; }
                }
                if (!dup) out.params.push_back(p);
            }
        }
    }

    ALOGI("GammaGLShader: parsed '%s': %d passes, %d params",
          path.c_str(), numShaders, (int)out.params.size());
    return true;
}

// ---------------------------------------------------------------------------
// GLSL shader compilation
// ---------------------------------------------------------------------------

// Split a .glsl file into vertex and fragment sources using #if defined(VERTEX)
// and #elif defined(FRAGMENT) guards.  If no guards found, treat whole file as
// fragment and use a stock vertex shader.
static bool splitGLSLSource(const std::string& path,
                            std::string& vertSrc, std::string& fragSrc) {
    std::string raw = readFile(path);
    if (raw.empty()) {
        ALOGE("GammaGLShader: cannot read shader '%s'", path.c_str());
        return false;
    }

    // Check if it uses the standard VERTEX/FRAGMENT guards
    bool hasVertexGuard = (raw.find("defined(VERTEX)") != std::string::npos);
    bool hasFragmentGuard = (raw.find("defined(FRAGMENT)") != std::string::npos);

    // GLES3 (#version 300 es) removed gl_FragColor. Most RetroArch shaders use
    // the compat guard and declare `out vec4 FragColor` at __VERSION__ >= 130,
    // but some still WRITE to gl_FragColor directly (e.g. the gameboy dot-matrix
    // and simpletex_lcd handheld shaders), which then fails to compile. Alias
    // gl_FragColor to that declared output. Inert for shaders that never use it.
    std::string fragCompat;
    if (raw.find("gl_FragColor") != std::string::npos)
        fragCompat = "#define gl_FragColor FragColor\n";

    if (hasVertexGuard && hasFragmentGuard) {
        // Build vertex source: #define VERTEX before the shader code
        vertSrc = "#version 300 es\n"
                  "#define VERTEX\n"
                  "#define PARAMETER_UNIFORM\n"
                  + raw;

        // Build fragment source: #define FRAGMENT
        // precision highp float must come after #version but before any
        // out declarations; multiple precision statements are fine in GLSL
        // (later one overrides), so safe even if the shader defines its own.
        fragSrc = "#version 300 es\n"
                  "precision highp float;\n"
                  "#define FRAGMENT\n"
                  "#define PARAMETER_UNIFORM\n"
                  + fragCompat
                  + raw;
    } else {
        // No guards — provide a stock vertex shader
        vertSrc = "#version 300 es\n"
                  "in vec4 VertexCoord;\n"
                  "in vec4 TexCoord;\n"
                  "out vec4 TEX0;\n"
                  "uniform mat4 MVPMatrix;\n"
                  "void main() {\n"
                  "    gl_Position = MVPMatrix * VertexCoord;\n"
                  "    TEX0.xy = TexCoord.xy;\n"
                  "}\n";

        fragSrc = "#version 300 es\n"
                  "#define FRAGMENT\n"
                  "#define PARAMETER_UNIFORM\n"
                  "precision highp float;\n"
                  + fragCompat
                  + raw;
    }

    // Strip lines that are invalid or cause issues in GLSL 300 es
    auto stripProblematic = [](std::string& s) {
        std::string result;
        std::istringstream ss(s);
        std::string line;
        while (std::getline(ss, line)) {
            std::string t = trim(line);
            if (t.find("#pragma parameter") == 0) {
                result += "// " + line + "\n";  // not valid GLSL
            } else if (t.find("#define texture(") == 0 ||
                       t.find("#define texture (") == 0) {
                // RetroArch compat macro: #define texture(c,d) COMPAT_TEXTURE(c,d)
                // Creates circular expansion with GLSL 300 es where
                // COMPAT_TEXTURE is already defined as texture.
                result += "// " + line + "\n";
            } else {
                result += line + "\n";
            }
        }
        s = result;
    };
    stripProblematic(vertSrc);
    stripProblematic(fragSrc);

    // Strip any existing #version directives from the raw source
    // (we prepend our own #version 300 es)
    auto stripVersion = [](std::string& s) {
        std::string result;
        bool firstVersion = true;
        std::istringstream ss(s);
        std::string line;
        while (std::getline(ss, line)) {
            std::string t = trim(line);
            if (t.find("#version") == 0) {
                if (firstVersion) {
                    firstVersion = false;
                    result += line + "\n";  // keep our prepended version
                } else {
                    result += "// " + line + "\n";  // comment out original
                }
            } else {
                result += line + "\n";
            }
        }
        s = result;
    };
    stripVersion(vertSrc);
    stripVersion(fragSrc);

    return true;
}

static GLuint compileShader(GLenum type, const std::string& source) {
    GLuint shader = glCreateShader(type);
    const char* src = source.c_str();
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);

    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char buf[1024];
        glGetShaderInfoLog(shader, sizeof(buf), nullptr, buf);
        ALOGE("GammaGLShader: %s compile error: %s",
              type == GL_VERTEX_SHADER ? "vert" : "frag", buf);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static GLuint linkProgram(GLuint vert, GLuint frag) {
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vert);
    glAttachShader(prog, frag);

    // Bind standard RetroArch attribute locations
    glBindAttribLocation(prog, 0, "VertexCoord");
    glBindAttribLocation(prog, 1, "TexCoord");
    glBindAttribLocation(prog, 2, "COLOR");

    glLinkProgram(prog);

    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char buf[1024];
        glGetProgramInfoLog(prog, sizeof(buf), nullptr, buf);
        ALOGE("GammaGLShader: link error: %s", buf);
        glDeleteProgram(prog);
        return 0;
    }
    return prog;
}

// ---------------------------------------------------------------------------
// GL pass state
// ---------------------------------------------------------------------------

struct GLSLPassState {
    GLuint program = 0;
    GLuint fbo = 0;
    GLuint outTexture = 0;
    int width = 0, height = 0;
    bool filterLinear = false;

    // Standard uniform locations
    GLint locMVP = -1;
    GLint locOutputSize = -1;
    GLint locTextureSize = -1;
    GLint locInputSize = -1;
    GLint locFrameCount = -1;
    GLint locFrameDirection = -1;
    GLint locTexture = -1;        // sampler2D Texture
    GLint locOrigTexture = -1;    // sampler2D OrigTexture
    GLint locOrigTextureSize = -1;
    GLint locOrigInputSize = -1;

    // Pass output texture samplers (PassNTexture — absolute index)
    GLint locPassTexture[16];
    GLint locPassTextureSize[16];
    GLint locPassInputSize[16];

    // PassPrevN texture samplers (relative: PassPrevN = pass[current - N])
    GLint locPassPrevTexture[16];
    GLint locPassPrevTextureSize[16];
    GLint locPassPrevInputSize[16];

    // Param uniform locations
    std::vector<GLint> paramLocs;

    GLSLPassState() {
        for (auto& v : locPassTexture) v = -1;
        for (auto& v : locPassTextureSize) v = -1;
        for (auto& v : locPassInputSize) v = -1;
        for (auto& v : locPassPrevTexture) v = -1;
        for (auto& v : locPassPrevTextureSize) v = -1;
        for (auto& v : locPassPrevInputSize) v = -1;
    }
};

struct GLSLChainState {
    std::vector<GLSLPassState> passes;
    GLuint quadVBO = 0;
    GLuint quadVAO = 0;
    bool valid = false;
    std::string loadedPreset;
    int displayW = 0, displayH = 0;

    void destroy() {
        for (auto& p : passes) {
            if (p.program) glDeleteProgram(p.program);
            if (p.fbo) glDeleteFramebuffers(1, &p.fbo);
            if (p.outTexture) glDeleteTextures(1, &p.outTexture);
        }
        passes.clear();
        if (quadVAO) glDeleteVertexArrays(1, &quadVAO);
        quadVAO = 0;
        if (quadVBO) glDeleteBuffers(1, &quadVBO);
        quadVBO = 0;
        valid = false;
    }
};

// ---------------------------------------------------------------------------
// Static state
// ---------------------------------------------------------------------------

static std::mutex sMutex;
static GLSLChainState sChain;
static GLSLPreset sPreset;
static std::string sLoadedPath;
static std::string sFailedPath;   // preset that failed initChain — skip until path changes
static int sInitRetryCount = 0;   // retry counter for transient initChain failures
static constexpr int kMaxInitRetries = 10;  // give up after this many failures
static uint32_t sFrameCount = 0;
static float sResScale = 1.0f;

// Cached property state — refreshed every kPropRefreshInterval calls instead of every frame
static constexpr uint32_t kPropRefreshInterval = 60;
static struct {
    bool debugLog = false;
    bool shaderOn = false;
    std::string type;
    std::string presetPath;
    std::string resScaleStr;
    uint32_t callCount = 0;
    uint32_t lastRefreshCall = 0;
    bool bootCompleted = false;

    void refresh() {
        uint32_t call = ++callCount;
        if (call - lastRefreshCall < kPropRefreshInterval && lastRefreshCall != 0) return;
        lastRefreshCall = call;
        debugLog = GetBoolProperty("persist.gammaos.shader.debug", false);
        shaderOn = GetBoolProperty("persist.gammaos.shader.enable", false);
        type = GetProperty("persist.gammaos.shader.type", "crt-simple");
        presetPath = GetProperty("persist.gammaos.shader.custom.preset", "");
        resScaleStr = GetProperty("persist.gammaos.shader.custom.res_scale", "full");
        if (!bootCompleted)
            bootCompleted = GetBoolProperty("sys.boot_completed", false);
    }
} sPropCache;

// Param file mtime tracking — only re-read when file is modified
static struct {
    time_t lastMtime = 0;
    std::string lastContent;
} sParamFileCache;

// Downscale FBO for res_scale < 1.0
static struct {
    GLuint program = 0;
    GLuint fbo = 0;
    GLuint texture = 0;
    int width = 0, height = 0;
    GLint locTex = -1;
} sDownscale;

static float parseResScale(const std::string& val) {
    if (val == "3/4" || val == "0.75") return 0.75f;
    if (val == "1/2" || val == "0.5" || val == "0.50") return 0.5f;
    if (val == "1/3" || val == "0.33") return 1.0f / 3.0f;
    if (val == "1/4" || val == "0.25") return 0.25f;
    if (val == "1/5" || val == "0.2")  return 0.2f;
    if (val == "1/6")                  return 1.0f / 6.0f;
    if (val == "1/8" || val == "0.125") return 0.125f;
    return 1.0f;  // "full" or unknown
}

// A display-wide post-process has no native game raster, so presets that emulate
// a low native panel - CRT scanline/beam/interlace AND handheld LCD/dot-matrix
// grids - key off InputSize.y and need a synthetic low source height to look
// right. Detect them by name/path so they default to a visible density instead
// of a flat full-res pass. Plain effect presets (blur/sharpen/colour grades) are
// left at full resolution.
static bool isLowResPreset(const std::string& type, const std::string& presetPath) {
    std::string s = type + "|" + presetPath;
    for (auto& c : s) c = (char)tolower((unsigned char)c);
    static const char* kLowRes[] = {
        // CRT families
        "crt", "scanline", "aperture", "lottes", "geom", "easymode", "royale",
        "ntsc", "slotmask", "dotmask", "trinitron", "hyllian", "zfast", "phosphor",
        // handheld LCD / dot-matrix families (emulate a 160-240 line panel)
        "handheld", "lcd", "dot-matrix", "gameboy", "dmg", "gba", "gbc"};
    for (auto* k : kLowRes) if (s.find(k) != std::string::npos) return true;
    return false;
}

static GLuint downscaleTexture(GLuint srcTex, int srcW, int srcH,
                                int dstW, int dstH) {
    if (!sDownscale.program) {
        const char* vs =
            "#version 300 es\n"
            "in vec2 aPos;\n"
            "out vec2 vUV;\n"
            "void main(){\n"
            "  vUV = aPos * 0.5 + 0.5;\n"
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
            "}\n";
        const char* fs =
            "#version 300 es\n"
            "precision highp float;\n"
            "uniform sampler2D uTex;\n"
            "in vec2 vUV;\n"
            "out vec4 fragColor;\n"
            "void main(){\n"
            "  fragColor = texture(uTex, vUV);\n"
            "}\n";
        GLuint vsh = compileShader(GL_VERTEX_SHADER, vs);
        GLuint fsh = compileShader(GL_FRAGMENT_SHADER, fs);
        if (!vsh || !fsh) { glDeleteShader(vsh); glDeleteShader(fsh); return 0; }
        sDownscale.program = glCreateProgram();
        glAttachShader(sDownscale.program, vsh);
        glAttachShader(sDownscale.program, fsh);
        glBindAttribLocation(sDownscale.program, 0, "aPos");
        glLinkProgram(sDownscale.program);
        glDeleteShader(vsh);
        glDeleteShader(fsh);
        sDownscale.locTex = glGetUniformLocation(sDownscale.program, "uTex");
    }

    if (sDownscale.width != dstW || sDownscale.height != dstH) {
        if (sDownscale.fbo) glDeleteFramebuffers(1, &sDownscale.fbo);
        if (sDownscale.texture) glDeleteTextures(1, &sDownscale.texture);

        glGenTextures(1, &sDownscale.texture);
        glBindTexture(GL_TEXTURE_2D, sDownscale.texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, dstW, dstH, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glGenFramebuffers(1, &sDownscale.fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, sDownscale.fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, sDownscale.texture, 0);
        sDownscale.width = dstW;
        sDownscale.height = dstH;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, sDownscale.fbo);
    glViewport(0, 0, dstW, dstH);
    glUseProgram(sDownscale.program);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, srcTex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glUniform1i(sDownscale.locTex, 0);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);

    float tri[] = { -1,-1, 3,-1, -1,3 };
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, tri);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(0);

    return sDownscale.texture;
}

// ---------------------------------------------------------------------------
// OES → TEXTURE_2D copy (source textures on Android are often external)
// ---------------------------------------------------------------------------

static struct {
    GLuint program = 0;
    GLuint fbo = 0;
    GLuint texture = 0;
    int width = 0, height = 0;
} sOESCopy;

static GLuint copyExternalToTexture2D(GLuint oesTex, GLenum oesTarget,
                                       int w, int h) {
    if (!sOESCopy.program) {
        const char* vs =
            "#version 300 es\n"
            "in vec2 aPos;\n"
            "out vec2 vUV;\n"
            "void main(){\n"
            "  vUV = aPos * 0.5 + 0.5;\n"
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
            "}\n";
        const char* fs =
            "#version 300 es\n"
            "#extension GL_OES_EGL_image_external_essl3 : require\n"
            "precision highp float;\n"
            "uniform samplerExternalOES uTex;\n"
            "in vec2 vUV;\n"
            "out vec4 fragColor;\n"
            "void main(){\n"
            "  fragColor = texture(uTex, vUV);\n"
            "  fragColor.a = 1.0;\n"
            "}\n";

        GLuint vsh = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vsh, 1, &vs, nullptr);
        glCompileShader(vsh);
        GLuint fsh = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fsh, 1, &fs, nullptr);
        glCompileShader(fsh);

        GLint ok = 0;
        glGetShaderiv(fsh, GL_COMPILE_STATUS, &ok);
        if (!ok) {
            char buf[512];
            glGetShaderInfoLog(fsh, sizeof(buf), nullptr, buf);
            ALOGE("GammaGLShader: OES copy frag compile failed: %s", buf);
            glDeleteShader(vsh);
            glDeleteShader(fsh);
            return 0;
        }

        sOESCopy.program = glCreateProgram();
        glAttachShader(sOESCopy.program, vsh);
        glAttachShader(sOESCopy.program, fsh);
        glBindAttribLocation(sOESCopy.program, 0, "aPos");
        glLinkProgram(sOESCopy.program);
        glDeleteShader(vsh);
        glDeleteShader(fsh);

        glGetProgramiv(sOESCopy.program, GL_LINK_STATUS, &ok);
        if (!ok) {
            ALOGE("GammaGLShader: OES copy link failed");
            glDeleteProgram(sOESCopy.program);
            sOESCopy.program = 0;
            return 0;
        }
    }

    if (sOESCopy.width != w || sOESCopy.height != h) {
        if (sOESCopy.fbo) glDeleteFramebuffers(1, &sOESCopy.fbo);
        if (sOESCopy.texture) glDeleteTextures(1, &sOESCopy.texture);

        glGenTextures(1, &sOESCopy.texture);
        glBindTexture(GL_TEXTURE_2D, sOESCopy.texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glGenFramebuffers(1, &sOESCopy.fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, sOESCopy.fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, sOESCopy.texture, 0);
        sOESCopy.width = w;
        sOESCopy.height = h;
        ALOGI("GammaGLShader: OES copy FBO %dx%d", w, h);
    }

    glBindFramebuffer(GL_FRAMEBUFFER, sOESCopy.fbo);
    glViewport(0, 0, w, h);
    glUseProgram(sOESCopy.program);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(oesTarget, oesTex);
    glUniform1i(glGetUniformLocation(sOESCopy.program, "uTex"), 0);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);

    float tri[] = { -1,-1, 3,-1, -1,3 };
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, tri);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(0);
    glBindTexture(oesTarget, 0);

    return sOESCopy.texture;
}

// ---------------------------------------------------------------------------
// Compute pass output size (same logic as RetroArch)
// ---------------------------------------------------------------------------

static void computePassSize(const GLSLPassDef& def, int srcW, int srcH,
                             int viewW, int viewH, int& outW, int& outH) {
    // X dimension
    if (def.scaleTypeX == "viewport") {
        outW = (int)(viewW * def.scaleX);
    } else if (def.scaleTypeX == "absolute") {
        outW = (int)def.scaleX;
    } else {  // "source"
        outW = (int)(srcW * def.scaleX);
    }
    // Y dimension
    if (def.scaleTypeY == "viewport") {
        outH = (int)(viewH * def.scaleY);
    } else if (def.scaleTypeY == "absolute") {
        outH = (int)def.scaleY;
    } else {
        outH = (int)(srcH * def.scaleY);
    }
    if (outW <= 0) outW = viewW;
    if (outH <= 0) outH = viewH;
}

// ---------------------------------------------------------------------------
// Chain initialization
// ---------------------------------------------------------------------------

// Maximum number of shader passes allowed in the compositor.
// Complex multi-pass chains can block surfaceflinger's render thread beyond the
// vsync deadline, causing system hangs. 8 admits the popular 5-pass CRT families
// (easymode-halation, lottes-multipass, geom-deluxe) whose intermediate passes
// mostly run at fractional scale, while still rejecting the 10+ pass monsters
// (crt-royale, guest-dr-venom) that would blow the deadline on a Mali-G52.
static constexpr int kMaxGLSLPasses = 8;

static bool initChain(GLSLPreset& preset, int viewW, int viewH) {
    sChain.destroy();

    int numPasses = (int)preset.passes.size();
    if (numPasses > kMaxGLSLPasses) {
        ALOGE("GammaGLShader: preset has %d passes (max %d) — too many for compositor, skipping",
              numPasses, kMaxGLSLPasses);
        setShaderStatus("passes:" + std::to_string(numPasses) + "/" +
                        std::to_string(kMaxGLSLPasses));
        return false;
    }
    sChain.passes.resize(numPasses);

    int prevW = viewW, prevH = viewH;  // source size starts as view size

    for (int i = 0; i < numPasses; i++) {
        auto& def = preset.passes[i];
        auto& gl = sChain.passes[i];

        // Compile shader
        std::string vertSrc, fragSrc;
        if (!splitGLSLSource(def.shaderPath, vertSrc, fragSrc)) {
            ALOGE("GammaGLShader: failed to split shader %d: %s",
                  i, def.shaderPath.c_str());
            setShaderStatus("compile_fail");
            return false;
        }

        GLuint vert = compileShader(GL_VERTEX_SHADER, vertSrc);
        if (!vert) { setShaderStatus("compile_fail"); return false; }
        GLuint frag = compileShader(GL_FRAGMENT_SHADER, fragSrc);
        if (!frag) { glDeleteShader(vert); setShaderStatus("compile_fail"); return false; }

        gl.program = linkProgram(vert, frag);
        glDeleteShader(vert);
        glDeleteShader(frag);
        if (!gl.program) { setShaderStatus("compile_fail"); return false; }

        // Get uniform locations
        gl.locMVP = glGetUniformLocation(gl.program, "MVPMatrix");
        gl.locOutputSize = glGetUniformLocation(gl.program, "OutputSize");
        gl.locTextureSize = glGetUniformLocation(gl.program, "TextureSize");
        gl.locInputSize = glGetUniformLocation(gl.program, "InputSize");
        gl.locFrameCount = glGetUniformLocation(gl.program, "FrameCount");
        gl.locFrameDirection = glGetUniformLocation(gl.program, "FrameDirection");
        gl.locTexture = glGetUniformLocation(gl.program, "Texture");
        gl.locOrigTexture = glGetUniformLocation(gl.program, "OrigTexture");
        gl.locOrigTextureSize = glGetUniformLocation(gl.program, "OrigTextureSize");
        gl.locOrigInputSize = glGetUniformLocation(gl.program, "OrigInputSize");

        // Pass output textures (absolute: PassNTexture)
        for (int j = 0; j < 16; j++) {
            std::string idx = std::to_string(j);
            gl.locPassTexture[j] = glGetUniformLocation(gl.program,
                    ("Pass" + idx + "Texture").c_str());
            gl.locPassTextureSize[j] = glGetUniformLocation(gl.program,
                    ("Pass" + idx + "TextureSize").c_str());
            gl.locPassInputSize[j] = glGetUniformLocation(gl.program,
                    ("Pass" + idx + "InputSize").c_str());
        }
        // PassPrevN texture samplers (relative: PassPrevN in pass i = pass[i-N])
        for (int j = 1; j <= 15; j++) {
            std::string idx = std::to_string(j);
            gl.locPassPrevTexture[j] = glGetUniformLocation(gl.program,
                    ("PassPrev" + idx + "Texture").c_str());
            gl.locPassPrevTextureSize[j] = glGetUniformLocation(gl.program,
                    ("PassPrev" + idx + "TextureSize").c_str());
            gl.locPassPrevInputSize[j] = glGetUniformLocation(gl.program,
                    ("PassPrev" + idx + "InputSize").c_str());
        }

        // Parameter uniforms
        gl.paramLocs.resize(preset.params.size());
        for (int j = 0; j < (int)preset.params.size(); j++) {
            gl.paramLocs[j] = glGetUniformLocation(gl.program,
                    preset.params[j].id.c_str());
        }

        gl.filterLinear = def.filterLinear;

        // Compute pass output dimensions
        computePassSize(def, prevW, prevH, viewW, viewH, gl.width, gl.height);

        // Create FBO + texture
        glGenTextures(1, &gl.outTexture);
        glBindTexture(GL_TEXTURE_2D, gl.outTexture);

        GLenum internalFmt = GL_RGBA8;
        GLenum dataType = GL_UNSIGNED_BYTE;
        if (def.floatFramebuffer) {
            internalFmt = GL_RGBA16F;
            dataType = GL_HALF_FLOAT;
        }

        glTexImage2D(GL_TEXTURE_2D, 0, internalFmt,
                     gl.width, gl.height, 0, GL_RGBA, dataType, nullptr);

        GLenum filter = def.filterLinear ? GL_LINEAR : GL_NEAREST;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glGenFramebuffers(1, &gl.fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, gl.fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, gl.outTexture, 0);

        GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            ALOGE("GammaGLShader: FBO incomplete for pass %d: 0x%x", i, status);
            return false;
        }

        ALOGI("GammaGLShader: pass %d: %s, %dx%d, filter=%s, prog=%u",
              i, def.shaderPath.c_str(), gl.width, gl.height,
              def.filterLinear ? "linear" : "nearest", gl.program);

        prevW = gl.width;
        prevH = gl.height;
    }

    // Create quad VBO: interleaved VertexCoord(x,y,z,w) + TexCoord(x,y,z,w)
    // RetroArch convention: positions in [0,1], MVP maps to [-1,1] clip space
    float quadData[] = {
        // VertexCoord(4)  TexCoord(4)
        0.0f, 0.0f, 0.0f, 1.0f,   0.0f, 0.0f, 0.0f, 1.0f,  // BL
        1.0f, 0.0f, 0.0f, 1.0f,   1.0f, 0.0f, 0.0f, 1.0f,  // BR
        0.0f, 1.0f, 0.0f, 1.0f,   0.0f, 1.0f, 0.0f, 1.0f,  // TL
        1.0f, 1.0f, 0.0f, 1.0f,   1.0f, 1.0f, 0.0f, 1.0f,  // TR
    };
    glGenBuffers(1, &sChain.quadVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sChain.quadVBO);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quadData), quadData, GL_STATIC_DRAW);

    // Create VAO to cache vertex attribute state — avoids 6+ GL calls per pass
    glGenVertexArrays(1, &sChain.quadVAO);
    glBindVertexArray(sChain.quadVAO);
    glBindBuffer(GL_ARRAY_BUFFER, sChain.quadVBO);
    // VertexCoord: vec4 at offset 0, stride 8 floats
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE,
                          8 * sizeof(float), (void*)0);
    // TexCoord: vec4 at offset 4 floats
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE,
                          8 * sizeof(float), (void*)(4 * sizeof(float)));
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    sChain.displayW = viewW;
    sChain.displayH = viewH;
    sChain.valid = true;

    ALOGI("GammaGLShader: initialized %d passes, display %dx%d",
          numPasses, viewW, viewH);
    return true;
}

// ---------------------------------------------------------------------------
// Render chain
// ---------------------------------------------------------------------------

static bool renderChain(GLuint srcTexture, int srcW, int srcH,
                         int viewW, int viewH,
                         uint32_t frameCount,
                         GLSLPreset& preset) {
    if (!sChain.valid || sChain.passes.empty()) return false;

    int numPasses = (int)sChain.passes.size();
    GLuint currentSrcTex = srcTexture;
    int currentW = srcW, currentH = srcH;

    // MVP matrix: maps [0,1] quad to [-1,1] clip space (constant)
    static const float mvp[16] = {
        2.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 2.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
       -1.0f,-1.0f, 0.0f, 1.0f,
    };

    // Set GL state that doesn't change between passes
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glBindVertexArray(sChain.quadVAO);

    for (int i = 0; i < numPasses; i++) {
        auto& gl = sChain.passes[i];
        auto& def = preset.passes[i];

        glBindFramebuffer(GL_FRAMEBUFFER, gl.fbo);
        glViewport(0, 0, gl.width, gl.height);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(gl.program);

        // Set uniforms
        if (gl.locMVP >= 0) glUniformMatrix4fv(gl.locMVP, 1, GL_FALSE, mvp);
        if (gl.locOutputSize >= 0)
            glUniform2f(gl.locOutputSize, (float)gl.width, (float)gl.height);
        if (gl.locTextureSize >= 0)
            glUniform2f(gl.locTextureSize, (float)currentW, (float)currentH);
        if (gl.locInputSize >= 0)
            glUniform2f(gl.locInputSize, (float)currentW, (float)currentH);

        int fc = frameCount;
        if (def.frameCountMod > 0) fc = fc % def.frameCountMod;
        if (gl.locFrameCount >= 0) glUniform1i(gl.locFrameCount, fc);
        if (gl.locFrameDirection >= 0) glUniform1i(gl.locFrameDirection, 1);

        // Parameter uniforms
        for (int j = 0; j < (int)preset.params.size(); j++) {
            if (gl.paramLocs[j] >= 0) {
                glUniform1f(gl.paramLocs[j], preset.params[j].current);
            }
        }

        // Bind textures
        int texUnit = 0;

        // Main source texture
        if (gl.locTexture >= 0) {
            glActiveTexture(GL_TEXTURE0 + texUnit);
            glBindTexture(GL_TEXTURE_2D, currentSrcTex);
            GLenum filter = gl.filterLinear ? GL_LINEAR : GL_NEAREST;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glUniform1i(gl.locTexture, texUnit);
            texUnit++;
        }

        // Original texture (pass 0 source)
        if (gl.locOrigTexture >= 0) {
            glActiveTexture(GL_TEXTURE0 + texUnit);
            glBindTexture(GL_TEXTURE_2D, srcTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glUniform1i(gl.locOrigTexture, texUnit);
            if (gl.locOrigTextureSize >= 0)
                glUniform2f(gl.locOrigTextureSize, (float)srcW, (float)srcH);
            if (gl.locOrigInputSize >= 0)
                glUniform2f(gl.locOrigInputSize, (float)srcW, (float)srcH);
            texUnit++;
        }

        // Previous pass output textures (absolute: PassNTexture)
        for (int j = 0; j < 16 && j < i; j++) {
            if (gl.locPassTexture[j] >= 0) {
                glActiveTexture(GL_TEXTURE0 + texUnit);
                glBindTexture(GL_TEXTURE_2D, sChain.passes[j].outTexture);
                glUniform1i(gl.locPassTexture[j], texUnit);
                if (gl.locPassTextureSize[j] >= 0)
                    glUniform2f(gl.locPassTextureSize[j],
                                (float)sChain.passes[j].width,
                                (float)sChain.passes[j].height);
                if (gl.locPassInputSize[j] >= 0)
                    glUniform2f(gl.locPassInputSize[j],
                                (float)sChain.passes[j].width,
                                (float)sChain.passes[j].height);
                texUnit++;
            }
        }

        // PassPrevN texture samplers (relative: PassPrevN = pass[i - N])
        // PassPrev1 = previous pass (i-1), PassPrev2 = i-2, etc.
        // When i-N < 0, bind original source texture.
        for (int j = 1; j <= 15; j++) {
            if (gl.locPassPrevTexture[j] >= 0) {
                int refPass = i - j;
                glActiveTexture(GL_TEXTURE0 + texUnit);
                if (refPass >= 0) {
                    glBindTexture(GL_TEXTURE_2D, sChain.passes[refPass].outTexture);
                    glUniform1i(gl.locPassPrevTexture[j], texUnit);
                    if (gl.locPassPrevTextureSize[j] >= 0)
                        glUniform2f(gl.locPassPrevTextureSize[j],
                                    (float)sChain.passes[refPass].width,
                                    (float)sChain.passes[refPass].height);
                    if (gl.locPassPrevInputSize[j] >= 0)
                        glUniform2f(gl.locPassPrevInputSize[j],
                                    (float)sChain.passes[refPass].width,
                                    (float)sChain.passes[refPass].height);
                } else {
                    // Reference before first pass → original source
                    glBindTexture(GL_TEXTURE_2D, srcTexture);
                    glUniform1i(gl.locPassPrevTexture[j], texUnit);
                    if (gl.locPassPrevTextureSize[j] >= 0)
                        glUniform2f(gl.locPassPrevTextureSize[j],
                                    (float)srcW, (float)srcH);
                    if (gl.locPassPrevInputSize[j] >= 0)
                        glUniform2f(gl.locPassPrevInputSize[j],
                                    (float)srcW, (float)srcH);
                }
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                texUnit++;
            }
        }

        // Draw fullscreen quad (vertex state bound via VAO)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        // Update source for next pass
        currentSrcTex = gl.outTexture;
        currentW = gl.width;
        currentH = gl.height;
    }

    glBindVertexArray(0);

    // Force alpha=255 in last pass FBO (CRT shaders often don't write alpha)
    {
        auto& lastPass = sChain.passes.back();
        glBindFramebuffer(GL_FRAMEBUFFER, lastPass.fbo);
        glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_TRUE);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glUseProgram(0);
    return true;
}

// ---------------------------------------------------------------------------
// apply() — public entry point
// ---------------------------------------------------------------------------

bool GammaGLSLShaderChain::apply(SkSurface* dstSurface,
                                  SkSurface* srcSurface,
                                  SkiaCapture* capture,
                                  ui::Dataspace /*outDataspace*/,
                                  bool isProtected,
                                  bool testOverlay,
                                  bool /*ctmBfiBlack*/,
                                  float /*defaultScanAngleDeg*/) {
    if (!dstSurface || !srcSurface || !capture) return false;

    // Refresh cached properties periodically instead of every frame
    sPropCache.refresh();

    if (!sPropCache.shaderOn || sPropCache.type != "custom-gl") return false;

    int dstW = dstSurface->width();
    int dstH = dstSurface->height();

    // Skip small surfaces (cursors, thumbnails)
    if (dstW < 480 || dstH < 320) return false;

    // Wait for boot complete
    if (!sPropCache.bootCompleted) return false;

    const bool debugLog = sPropCache.debugLog;

    std::lock_guard<std::mutex> lock(sMutex);

    // Load / reload preset — normalize /sdcard/ to /data/media/0/
    const std::string presetPath = normalizeStoragePath(sPropCache.presetPath);
    if (presetPath.empty()) {
        ALOGE("GammaGLShader: no preset path configured");
        return false;
    }

    // Skip presets that already failed initChain (e.g. too many passes).
    // Only retry when the user selects a different preset.
    if (presetPath == sFailedPath) return false;

    bool needReload = (presetPath != sLoadedPath);
    if (needReload || (sPreset.passes.empty() && !presetPath.empty())) {
        sFailedPath.clear();  // new preset — clear failure cache
        sInitRetryCount = 0;
        sPreset = {};
        sParamFileCache.lastMtime = 0;  // force re-read of params for new preset
        sParamFileCache.lastContent.clear();
        if (!parseGLSLPreset(presetPath, sPreset)) {
            // Don't cache failure — retry on next frame
            ALOGE("GammaGLShader: preset parse failed, will retry");
            setShaderStatus("parse_fail");
            return false;
        }
        sLoadedPath = presetPath;
        sChain.valid = false;
        ALOGI("GammaGLShader: loaded preset '%s' (%d passes, %d params)",
              presetPath.c_str(), (int)sPreset.passes.size(),
              (int)sPreset.params.size());

        // Write parameter metadata for ShaderControl app UI
        {
            const std::string metaDir = "/data/media/0/GammaShader";
            mkdir(metaDir.c_str(), 0775);
            const std::string metaPath = metaDir + "/.shader_param_meta";
            std::ofstream meta(metaPath);
            if (meta.is_open()) {
                for (const auto& p : sPreset.params) {
                    float pMin = p.minimum;
                    // Extend overscale range to allow negative values.
                    // Border shaders need overscale < 0 on 4:3 displays to
                    // create border space (game aspect matches display aspect).
                    if (p.id == "overscale") pMin = -3.0f;
                    meta << p.id << "|" << p.desc << "|"
                         << p.initial << "|" << pMin << "|"
                         << p.maximum << "|" << p.step << "\n";
                }
                meta.close();
                chmod(metaPath.c_str(), 0664);
            }
            // Clear stale param overrides
            const std::string paramsPath = metaDir + "/.shader_params";
            std::ofstream clear(paramsPath, std::ios::trunc);
            clear.close();
            chmod(paramsPath.c_str(), 0666);
        }
    }

    if (sPreset.passes.empty()) {
        ALOGE("GammaGLShader: preset has no passes");
        return false;
    }

    sFrameCount++;

    // Get canvas
    SkCanvas* dstCanvas = capture->tryCapture(dstSurface);
    if (!dstCanvas) {
        ALOGE("GammaGLShader: tryCapture failed");
        return false;
    }

    // Get source image
    sk_sp<SkImage> srcSkImage = srcSurface->makeImageSnapshot();
    if (!srcSkImage) {
        ALOGE("GammaGLShader: makeImageSnapshot failed");
        return false;
    }

    int srcW = srcSkImage->width();
    int srcH = srcSkImage->height();

    if (debugLog) {
        ALOGD("GammaGLShader: apply() src=%dx%d dst=%dx%d frame=%u",
              srcW, srcH, dstW, dstH, sFrameCount);
    }

    // Get Skia's GrDirectContext
    GrDirectContext* grContext =
            static_cast<GrDirectContext*>(srcSurface->recordingContext());
    if (!grContext) return false;

    // Flush the source surface so its contents are committed to the GPU texture.
    // Without this, the texture backing the SkImage may still be empty because
    // Skia defers GPU work until a flush.
    skgpu::ganesh::FlushAndSubmit(srcSurface);
    grContext->flushAndSubmit(GrSyncCpu::kYes);

    // Extract GL texture from source (flush=true to ensure texture is populated)
    GrBackendTexture srcBackendTex;
    bool hasTex = SkImages::GetBackendTextureFromImage(
            srcSkImage, &srcBackendTex, true);
    if (!hasTex || !srcBackendTex.isValid()) return false;

    GrGLTextureInfo srcGLInfo = {};
    if (!GrBackendTextures::GetGLTextureInfo(srcBackendTex, &srcGLInfo)) {
        if (debugLog) ALOGE("GammaGLShader: not a GL backend");
        return false;
    }

    {
        static bool sLoggedOnce = false;
        if (!sLoggedOnce) {
            ALOGI("GammaGLShader: src tex id=%u target=0x%x fmt=0x%x",
                  srcGLInfo.fID, srcGLInfo.fTarget, srcGLInfo.fFormat);
            sLoggedOnce = true;
        }
    }

    // Save Skia's FBO
    GLint prevFBO = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFBO);

    // Copy external texture to TEXTURE_2D if needed
    GLuint srcTex = srcGLInfo.fID;
    if (srcGLInfo.fTarget != GL_TEXTURE_2D && srcGLInfo.fTarget != 0) {
        GLuint copied = copyExternalToTexture2D(srcTex, srcGLInfo.fTarget, srcW, srcH);
        if (copied) {
            srcTex = copied;
        } else {
            ALOGE("GammaGLShader: OES copy failed");
            glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);
            grContext->resetContext();
            return false;
        }
    }

    // res_scale = base render resolution: physically downscale the composite to a
    // low "source" before the shader chain.
    //
    // A display-wide post-process has no native game raster - the source IS the
    // full composited frame. Downscaling it is the base-resolution / performance
    // control AND gives CRT/LCD/pixel-art presets the low native raster they are
    // designed for (e.g. crt-geom-mini): their scanline/beam/interlace/grid terms
    // key off InputSize.y and only come alive when the source is well below the
    // display, the smaller source is much cheaper to sample, and the pixellation
    // is the intended low-res look. The chain still OUTPUTS at the display size
    // (passes run at the display viewport), so the scanline/mask overlay stays
    // crisp while the content carries the low-res source through.
    const std::string& resScaleStr = sPropCache.resScaleStr;
    sResScale = parseResScale(resScaleStr);

    int chainSrcW = srcW, chainSrcH = srcH;
    {
        bool isFull = resScaleStr.empty() || resScaleStr == "full";
        int targetH = srcH;
        if (isFull) {
            // CRT / handheld-LCD presets default to ~240 active lines (visible +
            // fast out of the box); everything else stays at full resolution.
            if (isLowResPreset(sPropCache.type, sPropCache.presetPath))
                targetH = std::min(srcH, 240);
        } else if (sResScale < 1.0f) {
            targetH = std::max(1, (int)(srcH * sResScale + 0.5f));
        }
        if (targetH < srcH) {
            int targetW = std::max(1, (int)((double)targetH * srcW / srcH + 0.5));
            GLuint scaled = downscaleTexture(srcTex, srcW, srcH, targetW, targetH);
            if (scaled) {
                srcTex = scaled;
                chainSrcW = targetW;
                chainSrcH = targetH;
                if (debugLog) {
                    static bool sLoggedScale = false;
                    if (!sLoggedScale) {
                        ALOGD("GammaGLShader: res_scale=%s src %dx%d -> %dx%d",
                              resScaleStr.c_str(), srcW, srcH, targetW, targetH);
                        sLoggedScale = true;
                    }
                }
            }
        }
    }

    // Initialize chain if needed. Pass sizes come from the display dims and the
    // source size feeds a per-frame uniform, so a res_scale change needs no
    // chain rebuild (downscaleTexture resizes its own FBO on demand).
    if (!sChain.valid || sChain.loadedPreset != sLoadedPath ||
        sChain.displayW != dstW || sChain.displayH != dstH) {
        if (!initChain(sPreset, dstW, dstH)) {
            sInitRetryCount++;
            if (sInitRetryCount >= kMaxInitRetries) {
                ALOGE("GammaGLShader: chain init failed %d times for '%s', giving up",
                      sInitRetryCount, presetPath.c_str());
                sFailedPath = presetPath;
            } else {
                ALOGE("GammaGLShader: chain init failed for '%s' (attempt %d/%d)",
                      presetPath.c_str(), sInitRetryCount, kMaxInitRetries);
            }
            glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);
            grContext->resetContext();
            return false;
        }
        sInitRetryCount = 0;
        sChain.loadedPreset = sLoadedPath;
        setShaderStatus("ok");
    }

    // Apply parameter overrides — only re-read file when mtime changes
    {
        static const char* sParamsPath = "/data/media/0/GammaShader/.shader_params";
        struct stat st;
        bool fileChanged = false;
        if (stat(sParamsPath, &st) == 0) {
            if (st.st_mtime != sParamFileCache.lastMtime) {
                sParamFileCache.lastMtime = st.st_mtime;
                sParamFileCache.lastContent = readFile(sParamsPath);
                fileChanged = true;
            }
        } else if (sParamFileCache.lastMtime != 0) {
            // File was deleted — reset
            sParamFileCache.lastMtime = 0;
            sParamFileCache.lastContent.clear();
            fileChanged = true;
        }
        if (fileChanged) {
            for (auto& p : sPreset.params) p.current = p.initial;
            std::istringstream pStream(sParamFileCache.lastContent);
            std::string pLine;
            while (std::getline(pStream, pLine)) {
                std::string tl = trim(pLine);
                if (tl.empty() || tl[0] == '#') continue;
                auto eq = tl.find('=');
                if (eq == std::string::npos) continue;
                std::string pId = trim(tl.substr(0, eq));
                std::string pVal = trim(tl.substr(eq + 1));
                for (auto& p : sPreset.params) {
                    if (p.id == pId) {
                        p.current = std::strtof(pVal.c_str(), nullptr);
                        break;
                    }
                }
            }
        }
    }

    // Render the shader chain
    bool renderOk = renderChain(srcTex, chainSrcW, chainSrcH, dstW, dstH,
                                 sFrameCount, sPreset);

    // Restore Skia's FBO
    glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);
    grContext->resetContext();

    if (!renderOk) {
        if (debugLog) ALOGE("GammaGLShader: render failed");
        return false;
    }

    // Wrap output texture as SkImage and draw to destination
    auto& lastPass = sChain.passes.back();
    int outW = lastPass.width;
    int outH = lastPass.height;

    GrGLTextureInfo outGLInfo = {};
    outGLInfo.fID = lastPass.outTexture;
    outGLInfo.fTarget = GL_TEXTURE_2D;

    // Match the internal format used when creating the FBO texture
    bool lastPassFloat = sPreset.passes.back().floatFramebuffer;
    outGLInfo.fFormat = lastPassFloat ? GL_RGBA16F : GL_RGBA8;

    GrBackendTexture outBackendTex =
            GrBackendTextures::MakeGL(outW, outH, skgpu::Mipmapped::kNo, outGLInfo);

    sk_sp<SkImage> outputSkImage = SkImages::BorrowTextureFrom(
            grContext, outBackendTex,
            kTopLeft_GrSurfaceOrigin,
            lastPassFloat ? kRGBA_F16_SkColorType : kRGBA_8888_SkColorType,
            kOpaque_SkAlphaType,
            nullptr);
    if (!outputSkImage) {
        if (debugLog) ALOGE("GammaGLShader: BorrowTextureFrom failed");
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
        ALOGD("GammaGLShader: frame %u, %dx%d -> %dx%d",
              sFrameCount, srcW, srcH, outW, outH);
    }

    return true;
}

} // namespace skia
} // namespace renderengine
} // namespace android
