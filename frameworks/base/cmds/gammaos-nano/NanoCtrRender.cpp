// NanoCtrRender - draws NW4C layouts with GLES2: PICA200 texture combiners as generated GLSL ES
// 1.00 programs (one per combiner configuration, cached), window frames and text.
// Port of /work/3ds/web/app/src/gfx (tev.ts, program.ts, renderer.ts, windowframes.ts).
#include "NanoCtrLayout.h"

#include <GLES2/gl2ext.h>

#include <algorithm>
#include <cmath>
#include <cstring>

#include <log/log.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

static const int kVertexFloats = 12;   // position 2, colour 4, three uv sets
static const int kMaxQuads = 1024;

struct ProgramInfo {
    GLuint program = 0;
    GLint matrix = -1, texMatrix = -1, texScale = -1, konst = -1, buffer = -1, alphaRef = -1;
    GLint tex[3] = {-1, -1, -1};
    // values last uploaded (uniforms belong to the program: equal values are not sent again)
    bool loaded = false;
    float mvp[16], texMatrix_[18], texScale_[6], konst_[24], buffer_[4], alphaRef_;
};

// ------------------------------------------------------------------ shader generation

static const char* kVertexShader = R"(
attribute vec2 a_position;
attribute vec4 a_color;
attribute vec2 a_uv0;
attribute vec2 a_uv1;
attribute vec2 a_uv2;
uniform mat4 u_matrix;
uniform vec3 u_texMatrix[6];
uniform vec2 u_texScale[3];
varying vec4 v_color;
varying vec2 v_uv0;
varying vec2 v_uv1;
varying vec2 v_uv2;
vec2 texCoord(vec2 uv, vec3 r0, vec3 r1, vec2 s) {
  vec3 p = vec3(uv, 1.0);
  return vec2(dot(r0, p), dot(r1, p)) * s;
}
void main() {
  v_color = a_color;
  v_uv0 = texCoord(a_uv0, u_texMatrix[0], u_texMatrix[1], u_texScale[0]);
  v_uv1 = texCoord(a_uv1, u_texMatrix[2], u_texMatrix[3], u_texScale[1]);
  v_uv2 = texCoord(a_uv2, u_texMatrix[4], u_texMatrix[5], u_texScale[2]);
  gl_Position = u_matrix * vec4(a_position, 0.0, 1.0);
}
)";

static std::string srcExpr(TevSrc s, const TevStage& st, int texCount) {
    switch (s) {
        case TevSrc::Tex0: return texCount > 0 ? "tex0" : "vec4(0.0)";
        case TevSrc::Tex1: return texCount > 1 ? "tex1" : "vec4(0.0)";
        case TevSrc::Tex2: return texCount > 2 ? "tex2" : "vec4(0.0)";
        case TevSrc::Constant:
            return "vec4(u_konst[" + std::to_string(st.konstColor) + "].rgb, u_konst[" + std::to_string(st.konstAlpha) + "].a)";
        case TevSrc::Primary: return "v_color";
        case TevSrc::Previous: return "previous";
        case TevSrc::Register: return "buf";
        default: return "vec4(0.0)";   // texture3 (procedural) is unused by layouts
    }
}

static std::string rgbOperand(const std::string& v, uint8_t op) {
    switch (op) {
        case 1: return "(vec3(1.0) - " + v + ".rgb)";
        case 2: return "vec3(" + v + ".a)";
        case 3: return "vec3(1.0 - " + v + ".a)";
        case 4: return "vec3(" + v + ".r)";
        case 5: return "vec3(1.0 - " + v + ".r)";
        case 6: return "vec3(" + v + ".g)";
        case 7: return "vec3(1.0 - " + v + ".g)";
        case 8: return "vec3(" + v + ".b)";
        case 9: return "vec3(1.0 - " + v + ".b)";
        default: return v + ".rgb";
    }
}

static std::string alphaOperand(const std::string& v, uint8_t op) {
    switch (op) {
        case 1: return "(1.0 - " + v + ".a)";
        case 2: return v + ".r";
        case 3: return "(1.0 - " + v + ".r)";
        case 4: return v + ".g";
        case 5: return "(1.0 - " + v + ".g)";
        case 6: return v + ".b";
        case 7: return "(1.0 - " + v + ".b)";
        default: return v + ".a";
    }
}

static std::string combine(const TevCombiner& c, const std::string* a, bool vec3) {
    const std::string one = vec3 ? "vec3(1.0)" : "1.0";
    const std::string half = vec3 ? "vec3(0.5)" : "0.5";
    switch (c.mode) {
        case TevMode::Replace: return a[0];
        case TevMode::Modulate: return "(" + a[0] + " * " + a[1] + ")";
        case TevMode::Add: return "(" + a[0] + " + " + a[1] + ")";
        case TevMode::AddSigned: return "(" + a[0] + " + " + a[1] + " - " + half + ")";
        case TevMode::Interpolate: return "mix(" + a[1] + ", " + a[0] + ", " + a[2] + ")";
        case TevMode::Subtract: return "(" + a[0] + " - " + a[1] + ")";
        case TevMode::MultiplyAdd: return "(" + a[0] + " * " + a[1] + " + " + a[2] + ")";
        case TevMode::AddMultiply: return "(min(" + a[0] + " + " + a[1] + ", " + one + ") * " + a[2] + ")";
        case TevMode::Dot3Rgb:
        case TevMode::Dot3Rgba:
            return vec3 ? "vec3(4.0 * dot(" + a[0] + " - vec3(0.5), " + a[1] + " - vec3(0.5)))"
                        : "(4.0 * (" + a[0] + " - 0.5) * (" + a[1] + " - 0.5))";
    }
    return a[0];
}

// `sdf`: texture 0 is a glyph atlas of distance fields (Font::sdf): its alpha becomes the glyph's
// coverage with an edge one screen pixel wide, so the console's small bitmap glyphs stay sharp
// however far a panel magnifies them.
static bool sDerivatives = false;   // GL_OES_standard_derivatives (fwidth)

static std::string fragmentShader(const Material& m, int texCount, bool sdf) {
    texCount = std::min(texCount, 3);
    std::string s = std::string(sdf && sDerivatives ? "#extension GL_OES_standard_derivatives : enable\n" : "") +
        // colour math at mediump (the combiners are 8 bit; fp16 runs at twice the rate on mobile
        // GPUs), texture coordinates at highp so tiling and wrap stay texel exact
        "#ifdef GL_FRAGMENT_PRECISION_HIGH\n#define HP highp\n#else\n#define HP mediump\n#endif\nprecision mediump float;\n"
        "varying vec4 v_color;\nvarying HP vec2 v_uv0;\nvarying HP vec2 v_uv1;\nvarying HP vec2 v_uv2;\n"
        "uniform sampler2D u_tex0;\nuniform sampler2D u_tex1;\nuniform sampler2D u_tex2;\n"
        "uniform vec4 u_konst[6];\nuniform vec4 u_buffer;\nuniform float u_alphaReference;\n"
        "void main() {\n";
    for (int i = 0; i < texCount; i++)
        s += "  vec4 tex" + std::to_string(i) + " = texture2D(u_tex" + std::to_string(i) + ", v_uv" + std::to_string(i) + ");\n";
    if (sdf && texCount > 0) {
        s += sDerivatives ? "  float sdfW = clamp(fwidth(tex0.a) * 0.7, 0.01, 0.25);\n" : "  float sdfW = 0.06;\n";
        s += "  tex0.a = smoothstep(0.5 - sdfW, 0.5 + sdfW, tex0.a);\n";
    }
    if (m.stages.empty()) {
        s += std::string("  vec4 tex = ") + (texCount > 0 ? "tex0" : "vec4(1.0)") + ";\n";
        s += "  vec4 result = mix(u_buffer, u_konst[0], tex) * v_color;\n";
    } else {
        s += "  vec4 previous = vec4(0.0);\n  vec4 buf = vec4(0.0);\n  vec4 nextBuffer = u_buffer;\n";
        for (size_t i = 0; i < m.stages.size(); i++) {
            const TevStage& st = m.stages[i];
            std::string ra[3], aa[3];
            for (int k = 0; k < 3; k++) {
                ra[k] = rgbOperand(srcExpr(st.rgb.src[k], st, texCount), st.rgb.op[k]);
                aa[k] = alphaOperand(srcExpr(st.alpha.src[k], st, texCount), st.alpha.op[k]);
            }
            char scale[16];
            snprintf(scale, sizeof(scale), "%d.0", st.rgb.scale);
            const std::string rgb = "clamp(" + combine(st.rgb, ra, true) + " * " + scale + ", 0.0, 1.0)";
            snprintf(scale, sizeof(scale), "%d.0", st.alpha.scale);
            const std::string alpha = st.rgb.mode == TevMode::Dot3Rgba ? std::string("rgb.r")
                                    : "clamp(" + combine(st.alpha, aa, false) + " * " + scale + ", 0.0, 1.0)";
            s += "  {\n    vec3 rgb = " + rgb + ";\n    float alpha = " + alpha + ";\n    previous = vec4(rgb, alpha);\n  }\n";
            s += "  buf = nextBuffer;\n";
            // the flag on stage n + 1 is the PICA buffer update bit of stage n
            if (i < 4 && i + 1 < m.stages.size()) {
                if (m.stages[i + 1].rgb.saveToBuffer) s += "  nextBuffer.rgb = previous.rgb;\n";
                if (m.stages[i + 1].alpha.saveToBuffer) s += "  nextBuffer.a = previous.a;\n";
            }
        }
        s += "  vec4 result = previous;\n";
    }
    static const char* kOps[] = {"", "<", "<=", "==", "!=", ">=", ">", ""};
    if (m.alphaFn == AlphaFn::Never) {
        s += "  discard;\n";
    } else if (m.alphaFn != AlphaFn::Always) {
        s += "  float alpha8 = floor(result.a * 255.0 + 0.5);\n";
        s += "  float reference8 = floor(u_alphaReference * 255.0 + 0.5);\n";
        s += std::string("  if (!(alpha8 ") + kOps[(int)m.alphaFn] + " reference8)) discard;\n";
    }
    s += "  gl_FragColor = result;\n}\n";
    return s;
}

static GLuint compile(GLenum type, const std::string& src) {
    GLuint sh = glCreateShader(type);
    const char* p = src.c_str();
    glShaderSource(sh, 1, &p, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024] = {0};
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        ALOGE("shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

// ------------------------------------------------------------------ renderer

Renderer::Renderer(Assets& assets) {
    (void)assets;
    mVerts.resize((size_t)kMaxQuads * 6 * kVertexFloats);
}

Renderer::~Renderer() { releaseGl(); }

void Renderer::releaseGl() {
    for (auto& p : mPrograms) if (p.second && p.second->program) glDeleteProgram(p.second->program);
    mPrograms.clear();
    for (int i = 0; i < kRing; i++) {
        if (mRing[i]) glDeleteBuffers(1, &mRing[i]);
        mRing[i] = 0;
        mRingSize[i] = 0;
    }
    mCmds.clear();
    mFrameVerts.clear();
    mKeep.clear();
    if (mBlitProgram) glDeleteProgram(mBlitProgram);
    mBlitProgram = 0;
    if (mWhiteTex) glDeleteTextures(1, &mWhiteTex);
    mWhiteTex = 0;
    mBoundProgram = 0;
    mBlendState = -1;
}

bool Renderer::init() {
    if (mRing[0]) return true;
    glGenBuffers(kRing, mRing);
    glGenTextures(1, &mWhiteTex);
    glBindTexture(GL_TEXTURE_2D, mWhiteTex);
    const uint8_t zero[4] = {0, 0, 0, 0};
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, zero);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    return true;
}

ProgramInfo* Renderer::program(const Material& m, int textureCount, bool sdf) {
    const std::string key = m.shaderKeyText + "#" + std::to_string(textureCount) + (sdf ? "s" : "");
    auto it = mPrograms.find(key);
    if (it != mPrograms.end()) return it->second.get();
    static bool checked = false;
    if (!checked) {
        checked = true;
        const char* ext = (const char*)glGetString(GL_EXTENSIONS);
        sDerivatives = ext && strstr(ext, "GL_OES_standard_derivatives");
    }
    auto info = std::make_unique<ProgramInfo>();
    GLuint vs = compile(GL_VERTEX_SHADER, kVertexShader);
    GLuint fs = compile(GL_FRAGMENT_SHADER, fragmentShader(m, textureCount, sdf));
    if (vs && fs) {
        GLuint p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glBindAttribLocation(p, 0, "a_position");
        glBindAttribLocation(p, 1, "a_color");
        glBindAttribLocation(p, 2, "a_uv0");
        glBindAttribLocation(p, 3, "a_uv1");
        glBindAttribLocation(p, 4, "a_uv2");
        glLinkProgram(p);
        GLint ok = 0;
        glGetProgramiv(p, GL_LINK_STATUS, &ok);
        if (!ok) {
            char log[1024] = {0};
            glGetProgramInfoLog(p, sizeof(log), nullptr, log);
            ALOGE("program link failed: %s", log);
            glDeleteProgram(p);
        } else {
            info->program = p;
            info->matrix = glGetUniformLocation(p, "u_matrix");
            info->texMatrix = glGetUniformLocation(p, "u_texMatrix");
            info->texScale = glGetUniformLocation(p, "u_texScale");
            info->konst = glGetUniformLocation(p, "u_konst");
            info->buffer = glGetUniformLocation(p, "u_buffer");
            info->alphaRef = glGetUniformLocation(p, "u_alphaReference");
            glUseProgram(p);
            for (int i = 0; i < 3; i++) {
                char n[16];
                snprintf(n, sizeof(n), "u_tex%d", i);
                info->tex[i] = glGetUniformLocation(p, n);
                if (info->tex[i] >= 0) glUniform1i(info->tex[i], i);
            }
            mBoundProgram = p;
        }
    }
    if (vs) glDeleteShader(vs);
    if (fs) glDeleteShader(fs);
    ProgramInfo* out = info.get();
    mPrograms.emplace(key, std::move(info));
    return out;
}

void Renderer::begin(float layoutW, float layoutH) {
    float p[16] = {0};
    p[0] = 2.0f / layoutW;
    p[5] = 2.0f / layoutH;
    p[10] = 0;
    p[15] = 1;
    beginWith(p);
    mLayoutW = layoutW;
    mLayoutH = layoutH;
}

void Renderer::beginWith(const float proj[16]) {
    init();
    memcpy(mProj, proj, sizeof(mProj));
    mCmds.clear();
    mFrameVerts.clear();
    mScissorOn = false;
    mHasClip = false;
}

void Renderer::layoutRectToWindow(float x, float y, float w, float h, int out[4]) const {
    // layout corners -> clip space -> window pixels
    float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
    const float cx[2] = {x, x + w}, cy[2] = {y, y + h};
    for (float px : cx)
        for (float py : cy) {
            const float ndcX = mProj[0] * px + mProj[4] * py + mProj[12];
            const float ndcY = mProj[1] * px + mProj[5] * py + mProj[13];
            const float wx = mVpX + (ndcX + 1) * 0.5f * mVpW;
            const float wy = mVpY + (ndcY + 1) * 0.5f * mVpH;
            minX = std::min(minX, wx); maxX = std::max(maxX, wx);
            minY = std::min(minY, wy); maxY = std::max(maxY, wy);
        }
    out[0] = (int)lroundf(minX);
    out[1] = (int)lroundf(minY);
    out[2] = std::max(0, (int)lroundf(maxX) - out[0]);
    out[3] = std::max(0, (int)lroundf(maxY) - out[1]);
}

void Renderer::setClip(float x, float y, float w, float h) {
    layoutRectToWindow(x, y, w, h, mClip);
    mHasClip = true;
    mScissorOn = true;
    memcpy(mScissor, mClip, sizeof(mScissor));
}

void Renderer::clearClip() {
    mHasClip = false;
    mScissorOn = false;
}

void Renderer::setScissor(bool on, float x, float y, float w, float h) {
    if (!on) {
        mScissorOn = mHasClip;
        if (mHasClip) memcpy(mScissor, mClip, sizeof(mScissor));
        return;
    }
    int r[4];
    layoutRectToWindow(x, y, w, h, r);
    if (mHasClip) {
        const int x0 = std::max(r[0], mClip[0]), y0 = std::max(r[1], mClip[1]);
        const int x1 = std::min(r[0] + r[2], mClip[0] + mClip[2]), y1 = std::min(r[1] + r[3], mClip[1] + mClip[3]);
        r[0] = x0; r[1] = y0; r[2] = std::max(0, x1 - x0); r[3] = std::max(0, y1 - y0);
    }
    mScissorOn = true;
    memcpy(mScissor, r, sizeof(mScissor));
}

// Issues the recorded draws: one upload, then state changes only where they differ.
void Renderer::issue(size_t from, size_t to, GLuint* bound, int* scissorOn, int* scissor, int* unit, int scissorDx,
                     int scissorDy) {
    for (size_t i = from; i < to; i++) {
        DrawCmd& c = mCmds[i];
        ProgramInfo* prog = c.prog;
        if (mBoundProgram != prog->program) {
            glUseProgram(prog->program);
            mBoundProgram = prog->program;
        }
        if (!prog->loaded || memcmp(prog->mvp, c.mvp, sizeof(c.mvp))) {
            glUniformMatrix4fv(prog->matrix, 1, GL_FALSE, c.mvp);
            memcpy(prog->mvp, c.mvp, sizeof(c.mvp));
        }
        if (!prog->loaded || memcmp(prog->texMatrix_, c.texMatrix, sizeof(c.texMatrix))) {
            glUniform3fv(prog->texMatrix, 6, c.texMatrix);
            memcpy(prog->texMatrix_, c.texMatrix, sizeof(c.texMatrix));
        }
        if (!prog->loaded || memcmp(prog->texScale_, c.texScale, sizeof(c.texScale))) {
            glUniform2fv(prog->texScale, 3, c.texScale);
            memcpy(prog->texScale_, c.texScale, sizeof(c.texScale));
        }
        if (!prog->loaded || memcmp(prog->konst_, c.konst, sizeof(c.konst))) {
            glUniform4fv(prog->konst, 6, c.konst);
            memcpy(prog->konst_, c.konst, sizeof(c.konst));
        }
        if (!prog->loaded || memcmp(prog->buffer_, c.buffer, sizeof(c.buffer))) {
            glUniform4fv(prog->buffer, 1, c.buffer);
            memcpy(prog->buffer_, c.buffer, sizeof(c.buffer));
        }
        if (!prog->loaded || prog->alphaRef_ != c.alphaRef) {
            glUniform1f(prog->alphaRef, c.alphaRef);
            prog->alphaRef_ = c.alphaRef;
        }
        prog->loaded = true;
        for (int u = 0; u < 3; u++) {
            Texture* t = c.texObj[u];
            const bool params = t && (t->wrapS != c.params[u][0] || t->wrapT != c.params[u][1] ||
                                      t->minF != c.params[u][2] || t->magF != c.params[u][3]);
            if (bound[u] == c.tex[u] && !params) continue;
            if (*unit != u) { glActiveTexture(GL_TEXTURE0 + u); *unit = u; }
            if (bound[u] != c.tex[u]) { glBindTexture(GL_TEXTURE_2D, c.tex[u]); bound[u] = c.tex[u]; }
            if (params) {
                if (t->wrapS != c.params[u][0]) { glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, c.params[u][0]); t->wrapS = c.params[u][0]; }
                if (t->wrapT != c.params[u][1]) { glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, c.params[u][1]); t->wrapT = c.params[u][1]; }
                if (t->minF != c.params[u][2]) { glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, c.params[u][2]); t->minF = c.params[u][2]; }
                if (t->magF != c.params[u][3]) { glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, c.params[u][3]); t->magF = c.params[u][3]; }
            }
        }
        applyBlend(c.blendKey, c.blendEq, c.blendSrc, c.blendDst);
        if (*scissorOn != (int)c.scissorOn) {
            if (c.scissorOn) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            *scissorOn = c.scissorOn;
        }
        if (c.scissorOn && memcmp(scissor, c.scissor, 4 * sizeof(int))) {
            glScissor(c.scissor[0] + scissorDx, c.scissor[1] + scissorDy, c.scissor[2], c.scissor[3]);
            memcpy(scissor, c.scissor, 4 * sizeof(int));
        }
        // following draws of the same state and adjacent vertices go as one
        int count = c.count;
        while (i + 1 < to) {
            const DrawCmd& n = mCmds[i + 1];
            if (n.first != c.first + count || n.prog != c.prog || memcmp(n.mvp, c.mvp, sizeof(c.mvp)) ||
                memcmp(n.texMatrix, c.texMatrix, sizeof(c.texMatrix)) || memcmp(n.texScale, c.texScale, sizeof(c.texScale)) ||
                memcmp(n.konst, c.konst, sizeof(c.konst)) || memcmp(n.buffer, c.buffer, sizeof(c.buffer)) ||
                n.alphaRef != c.alphaRef || memcmp(n.tex, c.tex, sizeof(c.tex)) || memcmp(n.params, c.params, sizeof(c.params)) ||
                n.blendKey != c.blendKey || n.scissorOn != c.scissorOn ||
                (c.scissorOn && memcmp(n.scissor, c.scissor, sizeof(c.scissor))))
                break;
            count += n.count;
            i++;
        }
        glDrawArrays(GL_TRIANGLES, c.first, count);
        mDrawCalls++;
    }
}

static const char* kBlitVs = "attribute vec2 a_position; varying vec2 v_uv;"
                             "void main() { v_uv = a_position * 0.5 + 0.5; gl_Position = vec4(a_position, 0.0, 1.0); }";
static const char* kBlitFs = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                             "varying vec2 v_uv; uniform sampler2D u_tex;"
                             "void main() { gl_FragColor = texture2D(u_tex, v_uv); }";

// Issues the recorded draws: one upload, then state changes only where they differ.
void Renderer::flush() {
    if (mCmds.empty()) return;
    const size_t bytes = mFrameVerts.size() * sizeof(float);
    const int slot = mRingNext;
    mRingNext = (mRingNext + 1) % kRing;
    const GLsizei stride = kVertexFloats * 4;
    auto bindStream = [&]() {
        glBindBuffer(GL_ARRAY_BUFFER, mRing[slot]);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, stride, (void*)0);
        glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, stride, (void*)(2 * 4));
        glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, stride, (void*)(6 * 4));
        glVertexAttribPointer(3, 2, GL_FLOAT, GL_FALSE, stride, (void*)(8 * 4));
        glVertexAttribPointer(4, 2, GL_FLOAT, GL_FALSE, stride, (void*)(10 * 4));
    };
    glBindBuffer(GL_ARRAY_BUFFER, mRing[slot]);
    if (bytes) {
        if (mRingSize[slot] < bytes) {
            mRingSize[slot] = std::max(bytes + bytes / 2, (size_t)64 * 1024);
            glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)mRingSize[slot], nullptr, GL_STREAM_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, (GLsizeiptr)bytes, mFrameVerts.data());
    }
    for (GLuint i = 0; i < 5; i++) glEnableVertexAttribArray(i);
    bindStream();
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    // the LCD shows colour only; destination alpha stays as cleared
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_FALSE);
    mBlendState = -1;
    mBoundProgram = 0;
    GLuint bound[3] = {~0u, ~0u, ~0u};
    int scissorOn = -1;
    int scissor[4] = {-1, -1, -1, -1};
    glActiveTexture(GL_TEXTURE0);
    int unit = 0;
    GLint target = 0;
    auto blit = [&](const DrawCmd& sc) {
        CacheSlot* cs = sc.cache;
        if (!mBlitProgram) {
            GLuint vs = compile(GL_VERTEX_SHADER, kBlitVs), fs = compile(GL_FRAGMENT_SHADER, kBlitFs);
            mBlitProgram = glCreateProgram();
            if (vs) glAttachShader(mBlitProgram, vs);
            if (fs) glAttachShader(mBlitProgram, fs);
            glBindAttribLocation(mBlitProgram, 0, "a_position");
            glLinkProgram(mBlitProgram);
            if (vs) glDeleteShader(vs);
            if (fs) glDeleteShader(fs);
            glUseProgram(mBlitProgram);
            glUniform1i(glGetUniformLocation(mBlitProgram, "u_tex"), 0);
        }
        glUseProgram(mBlitProgram);
        mBoundProgram = mBlitProgram;
        if (unit != 0) { glActiveTexture(GL_TEXTURE0); unit = 0; }
        glBindTexture(GL_TEXTURE_2D, cs->tex);
        bound[0] = cs->tex;
        applyBlend(0, GL_FUNC_ADD, GL_ONE, GL_ZERO);
        if (sc.scissorOn) {
            if (scissorOn != 1) { glEnable(GL_SCISSOR_TEST); scissorOn = 1; }
            glScissor(sc.scissor[0], sc.scissor[1], sc.scissor[2], sc.scissor[3]);
            scissor[0] = scissor[1] = scissor[2] = scissor[3] = -1;
        } else if (scissorOn != 0) {
            glDisable(GL_SCISSOR_TEST);
            scissorOn = 0;
        }
        static const float quad[12] = {-1, -1, 1, -1, -1, 1, -1, 1, 1, -1, 1, 1};
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, quad);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        mDrawCalls++;
        bindStream();
    };
    size_t i = 0;
    while (i < mCmds.size()) {
        if (mCmds[i].special == 0) {
            size_t j = i;
            while (j < mCmds.size() && mCmds[j].special == 0) j++;
            issue(i, j, bound, &scissorOn, scissor, &unit, 0, 0);
            i = j;
            continue;
        }
        DrawCmd& c = mCmds[i];
        CacheSlot* cs = c.cache;
        if (c.special == 1) {
            // render the group into the cache target, then copy it
            const size_t n = (size_t)c.cacheFrom;
            if (!target) glGetIntegerv(GL_FRAMEBUFFER_BINDING, &target);
            if (!cs->fbo || cs->w != mVpW || cs->h != mVpH) {
                if (cs->fbo) glDeleteFramebuffers(1, &cs->fbo);
                if (cs->tex) glDeleteTextures(1, &cs->tex);
                glGenTextures(1, &cs->tex);
                glBindTexture(GL_TEXTURE_2D, cs->tex);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mVpW, mVpH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                bound[unit] = ~0u;
                glGenFramebuffers(1, &cs->fbo);
                glBindFramebuffer(GL_FRAMEBUFFER, cs->fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, cs->tex, 0);
                cs->w = mVpW;
                cs->h = mVpH;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, cs->fbo);
            glViewport(0, 0, mVpW, mVpH);
            if (scissorOn != 0) { glDisable(GL_SCISSOR_TEST); scissorOn = 0; }
            glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
            glClearColor(c.cacheClear[0], c.cacheClear[1], c.cacheClear[2], 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_FALSE);
            scissor[0] = scissor[1] = scissor[2] = scissor[3] = -1;
            issue(i + 1, i + 1 + n, bound, &scissorOn, scissor, &unit, -mVpX, -mVpY);
            glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)target);
            glViewport(mVpX, mVpY, mVpW, mVpH);
            scissor[0] = scissor[1] = scissor[2] = scissor[3] = -1;
            cs->valid = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
            blit(c);
            i += 1 + n;
            continue;
        }
        blit(c);   // special 2: the cached pixels
        i++;
    }
    if (unit != 0) glActiveTexture(GL_TEXTURE0);
    mCmds.clear();
    mFrameVerts.clear();
    mKeep.clear();
}

// ------------------------------------------------------------------ cached groups

uint64_t gTextureEpoch = 1;

static inline uint64_t mix64(uint64_t h, uint64_t v) {
    h ^= v + 0x9e3779b97f4a7c15ull + (h << 6) + (h >> 2);
    return h * 0xff51afd7ed558ccdull;
}

static uint64_t hashBytes(uint64_t h, const void* data, size_t n) {
    const uint8_t* p = (const uint8_t*)data;
    while (n >= 8) {
        uint64_t v;
        memcpy(&v, p, 8);
        h = mix64(h, v);
        p += 8;
        n -= 8;
    }
    uint64_t tail = 0;
    memcpy(&tail, p, n);
    return mix64(h, tail ^ ((uint64_t)n << 56));
}

void Renderer::beginGroup() { mGroupStart = (int)mCmds.size(); }

void Renderer::endGroup(CacheSlot& cs, const float clear[3]) {
    const int g0 = mGroupStart;
    mGroupStart = -1;
    if (g0 < 0) return;
    const size_t n = mCmds.size() - (size_t)g0;
    if (n == 0) return;
    // the group's whole draw stream: commands (offsets taken out), vertices, and what they read
    uint64_t h = 0x84222325cbf29ce4ull;
    const int vfirst = mCmds[g0].first;
    for (size_t i = g0; i < mCmds.size(); i++) {
        DrawCmd c;
        memcpy(&c, &mCmds[i], sizeof(c));   // bytes as recorded (padding included, zeroed at record)
        c.first -= vfirst;
        h = hashBytes(h, &c, sizeof(c));
    }
    const size_t f0 = (size_t)vfirst * kVertexFloats;
    h = hashBytes(h, mFrameVerts.data() + f0, (mFrameVerts.size() - f0) * sizeof(float));
    const int vp[4] = {mVpX, mVpY, mVpW, mVpH};
    h = hashBytes(h, vp, sizeof(vp));
    h = hashBytes(h, clear, 3 * sizeof(float));
    h = mix64(h, gTextureEpoch);
    h = mix64(h, mGroupSalt);
    if (h != cs.hash) {
        // changed: draw directly this frame, cache it if it holds still for the next one
        cs.hash = h;
        cs.valid = false;
        cs.misses++;
        return;
    }
    DrawCmd sp;
    memset(&sp, 0, sizeof(sp));
    sp.cache = &cs;
    // the copy covers only the screen clip (the group never draws outside it)
    sp.scissorOn = mHasClip;
    memcpy(sp.scissor, mClip, sizeof(sp.scissor));
    memcpy(sp.cacheClear, clear, sizeof(sp.cacheClear));
    if (cs.valid) {
        // unchanged: the cached pixels replace the whole group
        mCmds.resize((size_t)g0);
        mFrameVerts.resize(f0);
        sp.special = 2;
        mCmds.push_back(sp);
        cs.hits++;
        return;
    }
    sp.special = 1;
    sp.cacheFrom = (int)n;
    mCmds.insert(mCmds.begin() + g0, sp);
}

void Renderer::releaseCache(CacheSlot& cs) {
    if (cs.fbo) glDeleteFramebuffers(1, &cs.fbo);
    if (cs.tex) glDeleteTextures(1, &cs.tex);
    cs = CacheSlot{};
}

void Renderer::end() {
    flush();
    for (GLuint i = 0; i < 5; i++) glDisableVertexAttribArray(i);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glDisable(GL_SCISSOR_TEST);
    mHasClip = false;
    mScissorOn = false;
    glBlendEquation(GL_FUNC_ADD);
    glActiveTexture(GL_TEXTURE0);
    glUseProgram(0);
    mBoundProgram = 0;
    mBlendState = -1;
}

void Renderer::drawLayout(Layout& layout) {
    if (!layout.root) return;
    drawPaneTree(layout, layout.root);
}

void Renderer::drawPane(Layout& layout, Pane* pane) {
    if (pane) drawPaneTree(layout, pane);
}

void Renderer::drawPaneTree(Layout& L, Pane* p) {
    if (!p->visible) return;
    switch (p->kind) {
        case PaneKind::Picture: drawPicture(L, p); break;
        case PaneKind::Text: drawText(L, p); break;
        case PaneKind::Window: drawWindow(L, p); break;
        default: break;
    }
    for (Pane* c : p->children) drawPaneTree(L, c);
}

static inline float clamp255(float v) { return std::min(std::max(roundf(v), 0.0f), 255.0f); }

static Rgba vertexColor(const Rgba& c, float globalAlpha) {
    return {clamp255(c[0]) / 255.0f, clamp255(c[1]) / 255.0f, clamp255(c[2]) / 255.0f,
            clamp255(c[3]) / 255.0f * clamp255(globalAlpha) / 255.0f};
}

static const int kTriOrder[6] = {0, 2, 1, 1, 2, 3};

int Renderer::writeQuad(int index, float l, float t, float r, float b, const Rgba* colors, const QuadUv* const* uvSets) {
    if (index >= kMaxQuads) return index;
    const float cx[4] = {l, r, l, r}, cy[4] = {t, t, b, b};
    float* v = &mVerts[(size_t)index * 6 * kVertexFloats];
    for (int k = 0; k < 6; k++) {
        const int c = kTriOrder[k];
        *v++ = cx[c];
        *v++ = cy[c];
        *v++ = colors[c][0];
        *v++ = colors[c][1];
        *v++ = colors[c][2];
        *v++ = colors[c][3];
        for (int set = 0; set < 3; set++) {
            const QuadUv* uv = uvSets ? uvSets[set] : nullptr;
            *v++ = uv ? (*uv)[c][0] : 0.0f;
            *v++ = uv ? (*uv)[c][1] : 0.0f;
        }
    }
    return index + 1;
}

static void texSrtMatrix(const TexSrt& s, float* out) {
    const float D = (float)M_PI / 180.0f;
    const float sn = sinf(s.rotate * D), cs = cosf(s.rotate * D);
    const float a0 = cs * s.sx, a1 = -sn * s.sy, b0 = sn * s.sx, b1 = cs * s.sy;
    out[0] = a0; out[1] = a1; out[2] = -a0 * 0.5f - a1 * 0.5f + 0.5f + s.tx;
    out[3] = b0; out[4] = b1; out[5] = -b0 * 0.5f - b1 * 0.5f + 0.5f + s.ty;
}

static void blendOf(const Material& m, int* key, GLenum* eqOut, GLenum* src, GLenum* dst) {
    *key = m.blendOp == BlendOp::Disable ? 0 : (1 + (int)m.blendOp * 100 + (int)m.blendSrc * 10 + (int)m.blendDst);
    *eqOut = GL_FUNC_ADD;
    *src = GL_ONE;
    *dst = GL_ZERO;
    if (m.blendOp == BlendOp::Disable) return;
    GLenum eq = GL_FUNC_ADD;
    switch (m.blendOp) {
        case BlendOp::Subtract: eq = GL_FUNC_SUBTRACT; break;
        case BlendOp::ReverseSubtract: eq = GL_FUNC_REVERSE_SUBTRACT; break;
        case BlendOp::Min: eq = GL_MIN_EXT; break;
        case BlendOp::Max: eq = GL_MAX_EXT; break;
        default: break;
    }
    *eqOut = eq;
    // factors 2 and 3 name the other operand's colour (GX convention): in the destination slot
    // they mean the source colour
    auto factor = [](BlendFactor f, bool source) -> GLenum {
        switch (f) {
            case BlendFactor::Zero: return GL_ZERO;
            case BlendFactor::One: return GL_ONE;
            case BlendFactor::DstColor: return source ? GL_DST_COLOR : GL_SRC_COLOR;
            case BlendFactor::InvDstColor: return source ? GL_ONE_MINUS_DST_COLOR : GL_ONE_MINUS_SRC_COLOR;
            case BlendFactor::SrcAlpha: return GL_SRC_ALPHA;
            case BlendFactor::InvSrcAlpha: return GL_ONE_MINUS_SRC_ALPHA;
            case BlendFactor::DstAlpha: return GL_DST_ALPHA;
            case BlendFactor::InvDstAlpha: return GL_ONE_MINUS_DST_ALPHA;
            case BlendFactor::SrcColor: return GL_SRC_COLOR;
            case BlendFactor::InvSrcColor: return GL_ONE_MINUS_SRC_COLOR;
        }
        return GL_ONE;
    };
    *src = factor(m.blendSrc, true);
    *dst = factor(m.blendDst, false);
}

void Renderer::applyBlend(int key, GLenum eq, GLenum src, GLenum dst) {
    if (key == mBlendState) return;
    mBlendState = key;
    if (key == 0) { glDisable(GL_BLEND); return; }
    glEnable(GL_BLEND);
    glBlendEquation(eq);
    glBlendFunc(src, dst);
}

void Renderer::submit(const Pane* p, const Material& m, int quads, const TexturePtr* tex, int texCount,
                      const TexMap* samplers, const TexSrt* srts, int srtCount, GLuint glyphAtlas, bool glyphSdf) {
    if (quads <= 0) return;
    ProgramInfo* prog = program(m, texCount, glyphAtlas != 0 && glyphSdf);
    if (!prog || !prog->program) return;
    mCmds.emplace_back();
    DrawCmd& c = mCmds.back();
    memset(&c, 0, sizeof(c));   // padding too: the cached groups hash these bytes
    c.prog = prog;
    // projection * pane matrix
    for (int col = 0; col < 4; col++)
        for (int r = 0; r < 4; r++) {
            float s = 0;
            for (int k = 0; k < 4; k++) s += mProj[k * 4 + r] * p->global.m[col * 4 + k];
            c.mvp[col * 4 + r] = s;
        }
    for (int u = 0; u < 3; u++) {
        float row[6] = {1, 0, 0, 0, 1, 0};
        if (u < srtCount && srts) texSrtMatrix(srts[u], row);
        // vec3 rows: (a0 a1 a2), (b0 b1 b2)
        memcpy(&c.texMatrix[u * 6], row, sizeof(row));
        const Texture* t = (u < texCount && tex[u]) ? tex[u].get() : nullptr;
        c.texScale[u * 2] = (t && t->texWidth) ? (float)t->width / t->texWidth : 1.0f;
        c.texScale[u * 2 + 1] = (t && t->texHeight) ? (float)t->height / t->texHeight : 1.0f;
    }
    if (glyphAtlas) { c.texScale[0] = c.texScale[1] = 1.0f; }
    for (int i = 0; i < 6; i++)
        for (int k = 0; k < 4; k++) c.konst[i * 4 + k] = clamp255(m.konst[i][k]) / 255.0f;
    for (int k = 0; k < 4; k++) c.buffer[k] = clamp255(m.bufferColor[k]) / 255.0f;
    c.alphaRef = m.alphaRef;
    for (int u = 0; u < 3; u++) {
        c.texObj[u] = nullptr;
        c.params[u][0] = c.params[u][1] = c.params[u][2] = c.params[u][3] = 0;
        if (u == 0 && glyphAtlas) { c.tex[u] = glyphAtlas; continue; }
        Texture* t = (u < texCount && tex[u]) ? tex[u].get() : nullptr;
        if (!t || !t->id) { c.tex[u] = mWhiteTex; continue; }
        c.tex[u] = t->id;
        c.texObj[u] = t;
        mKeep.push_back(tex[u]);
        const TexMap* s = samplers ? &samplers[u] : nullptr;
        c.params[u][0] = s ? s->wrapS : GL_CLAMP_TO_EDGE;
        c.params[u][1] = s ? s->wrapT : GL_CLAMP_TO_EDGE;
        c.params[u][2] = s ? s->minF : GL_LINEAR;
        c.params[u][3] = s ? s->magF : GL_LINEAR;
    }
    blendOf(m, &c.blendKey, &c.blendEq, &c.blendSrc, &c.blendDst);
    c.scissorOn = mScissorOn;
    memcpy(c.scissor, mScissor, sizeof(c.scissor));
    const size_t floats = (size_t)quads * 6 * kVertexFloats;
    c.first = (int)(mFrameVerts.size() / kVertexFloats);
    c.count = quads * 6;
    mFrameVerts.insert(mFrameVerts.end(), mVerts.data(), mVerts.data() + floats);
}

void Renderer::drawPicture(Layout& L, Pane* p) {
    if (p->material < 0 || p->material >= (int)L.materials.size()) return;
    const Material& m = L.materials[p->material];
    float l, t, r, b;
    p->rect(&l, &t, &r, &b);
    Rgba colors[4];
    for (int i = 0; i < 4; i++) colors[i] = vertexColor(p->vertexColors[i], p->globalAlpha);
    const QuadUv* uv[3] = {nullptr, nullptr, nullptr};
    for (size_t i = 0; i < m.texMaps.size() && i < 3; i++) {
        const int set = m.texCoordSources[i];
        if (set >= 0 && set < (int)p->texCoords.size()) uv[i] = &p->texCoords[set];
    }
    const int n = writeQuad(0, l, t, r, b, colors, uv);
    TexturePtr tex[3];
    const int tc = (int)std::min<size_t>(3, m.texMaps.size());
    for (int i = 0; i < tc; i++) tex[i] = m.texMaps[i].texture;
    submit(p, m, n, tex, tc, m.texMaps.data(), m.texSrts.data(), (int)m.texSrts.size());
}

// ------------------------------------------------------------------ windows

static void textureSizeOf(const Material& m, float* w, float* h) {
    const Texture* t = m.texMaps.empty() ? nullptr : m.texMaps[0].texture.get();
    *w = t ? (float)t->width : 0.0f;
    *h = t ? (float)t->height : 0.0f;
}

static void flipQuad(QuadUv& q, uint8_t flip) {
    for (Vec2& p : q) {
        const float u = p[0], v = p[1];
        switch (flip) {
            case 1: p = {1 - u, v}; break;
            case 2: p = {u, 1 - v}; break;
            case 3: p = {v, 1 - u}; break;
            case 4: p = {1 - u, 1 - v}; break;
            case 5: p = {1 - v, u}; break;
            default: break;
        }
    }
}

static QuadUv boxQuad(float u0, float v0, float u1, float v1) {
    return QuadUv{Vec2{u0, v0}, Vec2{u1, v0}, Vec2{u0, v1}, Vec2{u1, v1}};
}

void Renderer::drawWindow(Layout& L, Pane* p) {
    if (p->material < 0 || p->material >= (int)L.materials.size()) return;
    float l, t, r, b;
    p->rect(&l, &t, &r, &b);
    // frame size from the frame textures (LT for left/top, RB for right/bottom)
    float fl = 0, ft = 0, fr = 0, fb = 0;
    if (!p->frames.empty()) {
        float w, h;
        textureSizeOf(L.materials[p->frames[0].material], &w, &h);
        fl = fr = w;
        ft = fb = h;
        if (p->frames.size() >= 4) {
            textureSizeOf(L.materials[p->frames[3].material], &w, &h);
            fr = w;
            fb = h;
        }
    }
    // content
    {
        const Material& m = L.materials[p->material];
        const float cl = l + fl - p->overflowL, ct = t - ft + p->overflowT;
        const float cr = r - fr + p->overflowR, cb = b + fb - p->overflowB;
        Rgba colors[4];
        for (int i = 0; i < 4; i++) colors[i] = vertexColor(p->vertexColors[i], p->globalAlpha);
        const QuadUv* uv[3] = {nullptr, nullptr, nullptr};
        for (size_t i = 0; i < m.texMaps.size() && i < 3; i++) {
            const int set = m.texCoordSources[i];
            if (set >= 0 && set < (int)p->texCoords.size()) uv[i] = &p->texCoords[set];
        }
        const int n = writeQuad(0, cl, ct, cr, cb, colors, uv);
        TexturePtr tex[3];
        const int tc = (int)std::min<size_t>(3, m.texMaps.size());
        for (int i = 0; i < tc; i++) tex[i] = m.texMaps[i].texture;
        submit(p, m, n, tex, tc, m.texMaps.data(), m.texSrts.data(), (int)m.texSrts.size());
    }
    if (p->frames.empty()) return;

    const Rgba white = vertexColor({255, 255, 255, 255}, p->globalAlpha);
    const Rgba fc[4] = {white, white, white, white};
    const float w = r - l, h = t - b;
    struct Spec { const Material* material; const Material* textures; uint8_t flip; float sw, sh; };
    auto spec = [&](size_t i) -> Spec {
        const WindowFrame& f = p->frames[std::min(i, p->frames.size() - 1)];
        const Material& fm = L.materials[f.material];
        const Material& first = L.materials[p->frames[0].material];
        float sw, sh;
        textureSizeOf(fm, &sw, &sh);
        return {fm.textureOnly ? &first : &fm, &fm, f.flip, sw, sh};
    };
    auto drawFrame = [&](const Spec& s, float ql, float qt, float qr, float qb, QuadUv uv) {
        flipQuad(uv, s.flip);
        const QuadUv* sets[3] = {&uv, &uv, &uv};
        const int n = writeQuad(0, ql, qt, qr, qb, fc, sets);
        TexturePtr tex[3];
        const int tc = (int)std::min<size_t>(3, s.textures->texMaps.size());
        for (int i = 0; i < tc; i++) tex[i] = s.textures->texMaps[i].texture;
        submit(p, *s.material, n, tex, tc, s.textures->texMaps.data(), s.textures->texSrts.data(), (int)s.textures->texSrts.size());
    };
    auto pinwheel = [&](const Spec& lt, const Spec& rt, const Spec& rb, const Spec& lb) {
        drawFrame(lt, l, t, r - fr, t - ft, boxQuad(0, 0, lt.sw ? (w - fr) / lt.sw : 1, 1));
        drawFrame(rt, r - fr, t, r, b + fb, boxQuad(0, 0, 1, rt.sh ? (h - fb) / rt.sh : 1));
        drawFrame(rb, l + fl, b + fb, r, b, boxQuad(rb.sw ? 1 - (w - fl) / rb.sw : 0, 0, 1, 1));
        drawFrame(lb, l, t - ft, l + fl, b, boxQuad(0, lb.sh ? 1 - (h - ft) / lb.sh : 0, 1, 1));
    };
    if (p->frames.size() < 4) {
        Spec s = spec(0);
        Spec a = s, bspec = s, c = s, d = s;
        a.flip = 0; bspec.flip = 1; c.flip = 4; d.flip = 2;
        pinwheel(a, bspec, c, d);
        return;
    }
    if (p->frames.size() < 8) {
        pinwheel(spec(0), spec(1), spec(3), spec(2));
        return;
    }
    drawFrame(spec(0), l, t, l + fl, t - ft, boxQuad(0, 0, 1, 1));
    drawFrame(spec(1), r - fr, t, r, t - ft, boxQuad(0, 0, 1, 1));
    drawFrame(spec(2), l, b + fb, l + fl, b, boxQuad(0, 0, 1, 1));
    drawFrame(spec(3), r - fr, b + fb, r, b, boxQuad(0, 0, 1, 1));
    drawFrame(spec(4), l, t - ft, l + fl, b + fb, boxQuad(0, 0, 1, 1));
    drawFrame(spec(5), r - fr, t - ft, r, b + fb, boxQuad(0, 0, 1, 1));
    drawFrame(spec(6), l + fl, t, r - fr, t - ft, boxQuad(0, 0, 1, 1));
    drawFrame(spec(7), l + fl, b + fb, r - fr, b, boxQuad(0, 0, 1, 1));
}

// ------------------------------------------------------------------ text

struct LineChar { uint32_t code; double scale; bool hasColor; Rgba color; };
struct Line { std::vector<LineChar> chars; double width = 0, scale = 1, extent = 0; };

static void layoutText(Pane* p, Font& f) {
    p->textQuads.clear();
    // double precision like the web layout code (JS numbers): the pen snaps with floor/ceil, so
    // single precision rounding can move a whole line by a unit
    const double sx = p->hasFontScale ? p->fontScale[0] : (f.width ? (double)p->fontSize[0] / f.width : 1.0);
    const double sy = p->hasFontScale ? p->fontScale[1] : (f.height ? (double)p->fontSize[1] / f.height : 1.0);
    std::vector<Line> lines;
    Line cur;
    auto close = [&](double feed) {
        const size_t n = cur.chars.size();
        if (n > 0) cur.width += (double)p->charSpace * (double)(n - 1);
        if (cur.extent == 0) cur.extent = feed;
        lines.push_back(std::move(cur));
        cur = Line();
        cur.scale = feed;
    };
    for (const TextRun& run : p->runs) {
        for (char32_t ch : run.text) {
            if (ch == U'\n') { close(run.scale); continue; }
            const Glyph* g = f.glyph(ch);
            cur.chars.push_back({(uint32_t)ch, (double)run.scale, run.hasColor, run.color});
            cur.width += (g ? g->charWidth : 0) * sx * (double)run.scale;
            cur.extent = std::max(cur.extent, (double)run.scale);
        }
    }
    close(1.0);

    double blockWidth = 0, blockHeight = 0;
    for (const Line& ln : lines) {
        blockWidth = std::max(blockWidth, ln.width);
        blockHeight += f.lineFeed * sy * ln.extent;
    }
    blockHeight += (double)p->lineSpace * (double)std::max<int>((int)lines.size() - 1, 0);
    // pane rect in double (Pane::rect is float)
    const double w = p->size[0] + p->growW, h = p->size[1] + p->growH;
    const double l = -w * p->originX * 0.5, t = h * p->originY * 0.5;
    const double r = l + w, b = t - h;
    const double rw = r - l, rh = t - b;
    const double centreX = (l + r) / 2;
    const double blockLeft = l + (rw - blockWidth) * p->textPosX / 2;
    const double blockTop = t - (rh - blockHeight) * p->textPosY / 2;
    const int align = p->alignment == 1 ? 0 : p->alignment == 2 ? 1 : p->alignment == 3 ? 2 : p->textPosX;
    double lineTop = blockTop;
    for (const Line& ln : lines) {
        const double lineHeight = f.lineFeed * sy * ln.scale;
        const double baseline = ceil(lineTop - f.ascent * sy * ln.scale);
        const double snap = p->textPosX == 1 ? centreX : 0.0;
        double pen = snap + floor(blockLeft - snap + (blockWidth - ln.width) * align / 2);
        for (const LineChar& ch : ln.chars) {
            const Glyph* g = f.glyph(ch.code);
            if (!g) continue;
            const double gx = sx * ch.scale, gy = sy * ch.scale;
            if (g->glyphWidth > 0) {
                Pane::GlyphQuad q;
                const double ql = pen + g->left * gx, qt = baseline + f.baseline * gy;
                q.l = (float)ql;
                q.t = (float)qt;
                q.r = (float)(ql + g->glyphWidth * gx);
                q.b = (float)(qt - f.cellHeight * gy);
                if (f.place(g, &q.u0, &q.v0, &q.u1, &q.v1)) {
                    q.hasColor = ch.hasColor;
                    q.color = ch.color;
                    p->textQuads.push_back(q);
                }
            }
            pen += g->charWidth * gx + p->charSpace;
        }
        lineTop -= lineHeight + (double)p->lineSpace;
    }
}

void Renderer::drawText(Layout& L, Pane* p) {
    if (!p->font || p->material < 0 || p->material >= (int)L.materials.size()) return;
    Font& f = *p->font;
    // geometry depends on the text, the pane and font sizes, and the atlas generation
    auto cacheKey = [&]() {
        uint64_t k = p->textRevision;
        k = k * 1315423911u + (uint64_t)((p->size[0] + p->growW) * 64) + ((uint64_t)((p->size[1] + p->growH) * 64) << 20);
        k = k * 2654435761u + (uint64_t)(p->fontSize[0] * 64) + ((uint64_t)(p->fontSize[1] * 64) << 24);
        return k * 40503u + f.generation();
    };
    const uint64_t key = cacheKey();
    if (key != p->textCacheKey) {
        layoutText(p, f);
        // placing glyphs may have restarted a full atlas: lay out again against the new one
        const uint64_t after = cacheKey();
        if (after != key) layoutText(p, f);
        p->textCacheKey = cacheKey();
    }
    if (p->textQuads.empty() || !f.atlas()) return;
    const Material& m = L.materials[p->material];
    const Rgba top = vertexColor(p->vertexColors[0], p->globalAlpha);
    const Rgba bottom = vertexColor(p->vertexColors[2], p->globalAlpha);
    int n = 0;
    QuadUv uv;
    const QuadUv* sets[3] = {&uv, nullptr, nullptr};
    TexturePtr none[1];
    TexMap glyphSampler;
    for (const Pane::GlyphQuad& q : p->textQuads) {
        const Rgba tc = q.hasColor ? vertexColor(q.color, p->globalAlpha) : top;
        const Rgba bc = q.hasColor ? tc : bottom;
        const Rgba cols[4] = {tc, tc, bc, bc};
        uv = boxQuad(q.u0, q.v0, q.u1, q.v1);
        n = writeQuad(n, q.l, q.t, q.r, q.b, cols, sets);
        if (n >= kMaxQuads) {
            submit(p, m, n, none, 1, &glyphSampler, nullptr, 0, f.atlas(), f.sdf());
            n = 0;
        }
    }
    submit(p, m, n, none, 1, &glyphSampler, nullptr, 0, f.atlas(), f.sdf());
}

}  // namespace ctr
}  // namespace android
