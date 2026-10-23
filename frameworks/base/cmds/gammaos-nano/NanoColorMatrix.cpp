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

#define LOG_TAG "GammaOSNano"

#include "NanoColorMatrix.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/system_properties.h>

// Exported by libc but declared only in the internal <sys/_system_properties.h>.
extern "C" uint32_t __system_property_serial(const prop_info* __pi);

#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {
namespace colormatrix {

void parse(const char* value, Matrix* out) {
    Matrix id;
    *out = id;
    if (!value || !value[0]) return;
    float v[12];
    const char* p = value;
    for (int i = 0; i < 12; i++) {
        char* end = nullptr;
        v[i] = strtof(p, &end);
        if (end == p) return;   // malformed: identity
        p = end;
        if (*p == ',') p++;
    }
    memcpy(out->m, v, sizeof v);
    out->identity = true;
    for (int i = 0; i < 12; i++) {
        const float want = (i == 0 || i == 4 || i == 8) ? 1.0f : 0.0f;
        if (v[i] < want - 0.0005f || v[i] > want + 0.0005f) { out->identity = false; break; }
    }
}

namespace {

// One property watched by serial: re-read only when it changed.
struct Watched {
    const char* name;
    const prop_info* pi = nullptr;
    uint32_t serial = 0;
    bool have = false;
    Matrix cur;
    bool poll() {
        if (!pi) pi = __system_property_find(name);
        if (!pi) return false;
        const uint32_t ser = __system_property_serial(pi);
        if (have && ser == serial) return false;
        serial = ser;
        have = true;
        char val[PROPERTY_VALUE_MAX] = {};
        property_get(name, val, "");
        Matrix next;
        parse(val, &next);
        const bool changed = memcmp(next.m, cur.m, sizeof next.m) != 0 || next.identity != cur.identity;
        cur = next;
        if (changed) ALOGI("colormatrix: %s = %s", name, next.identity ? "identity" : val);
        return changed;
    }
};

Watched gGlobal{kProp};
Watched gScreen[2] = {{kScreenPropTop}, {kScreenPropBottom}};

}  // namespace

bool poll(Matrix* out) {
    const bool changed = gGlobal.poll();
    *out = gGlobal.cur;
    return changed;
}

void multiply(const Matrix& a, const Matrix& b, Matrix* out) {
    Matrix r;
    // columns: r.col(c) = A * b.col(c); the offset column gets A's offset added
    for (int c = 0; c < 4; c++) {
        for (int row = 0; row < 3; row++) {
            float v = a.m[row] * b.m[c * 3] + a.m[3 + row] * b.m[c * 3 + 1] + a.m[6 + row] * b.m[c * 3 + 2];
            if (c == 3) v += a.m[9 + row];
            r.m[c * 3 + row] = v;
        }
    }
    r.identity = a.identity && b.identity;
    *out = r;
}

bool pollScreen(int screen, Matrix* out) {
    static Matrix sLast[2];
    static bool sHaveLast[2] = {false, false};
    if (screen < 0 || screen > 1) screen = 0;
    const bool g = gGlobal.poll();
    const bool sc = gScreen[screen].poll();
    if (!g && !sc && sHaveLast[screen]) { *out = sLast[screen]; return false; }
    Matrix m;
    multiply(gScreen[screen].cur, gGlobal.cur, &m);
    const bool changed = !sHaveLast[screen] || memcmp(m.m, sLast[screen].m, sizeof m.m) != 0;
    sLast[screen] = m;
    sHaveLast[screen] = true;
    *out = m;
    return changed;
}

void format(const Matrix& m, char* out, size_t outSize) {
    if (m.identity || outSize == 0) { if (outSize) out[0] = 0; return; }
    size_t n = 0;
    for (int i = 0; i < 12 && n < outSize; i++) {
        float v = m.m[i];
        if (v > -0.0005f && v < 0.0005f) v = 0.0f;   // no "-0.000"
        n += (size_t)snprintf(out + n, outSize - n, i ? ",%.3f" : "%.3f", v);
    }
}

void fromCalibration(int brightness, int contrast, int saturation, int hue, int red, int green,
                     int blue, Matrix* out) {
    auto clampi = [](int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; };
    brightness = clampi(brightness, 0, 100); contrast = clampi(contrast, 0, 100);
    saturation = clampi(saturation, 0, 100); hue = clampi(hue, 0, 100);
    red = clampi(red, 20, 100); green = clampi(green, 20, 100); blue = clampi(blue, 20, 100);
    // Applied in this order: brightness offset, contrast about mid grey, saturation and hue
    // (in YUV, hue a rotation of up to 30 degrees either way), then the channel levels.
    Matrix t1; t1.m[9] = t1.m[10] = t1.m[11] = (brightness - 50) / 100.0f;
    const float c = 1.0f + (contrast - 50) / 50.0f;
    Matrix t2; t2.m[0] = t2.m[4] = t2.m[8] = c; t2.m[9] = t2.m[10] = t2.m[11] = 0.5f - 0.5f * c;
    const float s = saturation / 50.0f;
    const float th = (float)((hue - 50) / 50.0 * (30.0 * M_PI / 180.0));
    const float cs = cosf(th), sn = sinf(th);
    // rgb -> yuv (BT.601), scale/rotate the chroma, yuv -> rgb
    const float toYuv[9] = { 0.299f, -0.14713f, 0.615f,   0.587f, -0.28886f, -0.51499f,   0.114f, 0.436f, -0.10001f };   // column-major
    const float chroma[9] = { 1, 0, 0,   0, s * cs, s * sn,   0, -s * sn, s * cs };
    const float toRgb[9] = { 1, 1, 1,   0, -0.39465f, 2.03211f,   1.13983f, -0.58060f, 0 };
    auto mul3 = [](const float* a, const float* b, float* r) {
        for (int col = 0; col < 3; col++)
            for (int row = 0; row < 3; row++)
                r[col * 3 + row] = a[row] * b[col * 3] + a[3 + row] * b[col * 3 + 1] + a[6 + row] * b[col * 3 + 2];
    };
    float tmp[9], h[9];
    mul3(chroma, toYuv, tmp);
    mul3(toRgb, tmp, h);
    Matrix t3; memcpy(t3.m, h, sizeof h);
    Matrix t4; t4.m[0] = red / 100.0f; t4.m[4] = green / 100.0f; t4.m[8] = blue / 100.0f;
    Matrix a, b;
    multiply(t2, t1, &a);
    multiply(t3, a, &b);
    multiply(t4, b, out);
    out->identity = brightness == 50 && contrast == 50 && saturation == 50 && hue == 50 &&
                    red == 100 && green == 100 && blue == 100;
}

namespace {

const char* const kVs =
    "attribute vec2 aPos;\n"
    "varying vec2 vUv;\n"
    "void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n";
const char* const kFs =
    "precision mediump float;\n"
    "uniform sampler2D uTex;\n"
    "uniform mat3 uM;\n"
    "uniform vec3 uOff;\n"
    "varying vec2 vUv;\n"
    "void main() {\n"
    "  vec4 c = texture2D(uTex, vUv);\n"
    "  gl_FragColor = vec4(clamp(uM * c.rgb + uOff, 0.0, 1.0), c.a);\n"
    "}\n";

GLuint gProg = 0, gTex = 0;
GLint gLocPos = -1, gLocTex = -1, gLocM = -1, gLocOff = -1;
int gTexW = 0, gTexH = 0;
bool gFailed = false;

GLuint compileShader(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512] = {};
        glGetShaderInfoLog(sh, sizeof log, nullptr, log);
        ALOGW("colormatrix: shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

bool program() {
    if (gProg) return true;
    if (gFailed) return false;
    const GLuint vs = compileShader(GL_VERTEX_SHADER, kVs);
    const GLuint fs = compileShader(GL_FRAGMENT_SHADER, kFs);
    if (!vs || !fs) { gFailed = true; return false; }
    const GLuint prog = glCreateProgram();
    glAttachShader(prog, vs); glAttachShader(prog, fs);
    glLinkProgram(prog);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512] = {};
        glGetProgramInfoLog(prog, sizeof log, nullptr, log);
        ALOGW("colormatrix: program link failed: %s", log);
        glDeleteProgram(prog);
        gFailed = true;
        return false;
    }
    gProg = prog;
    gLocPos = glGetAttribLocation(prog, "aPos");
    gLocTex = glGetUniformLocation(prog, "uTex");
    gLocM = glGetUniformLocation(prog, "uM");
    gLocOff = glGetUniformLocation(prog, "uOff");
    return true;
}

}  // namespace

void apply(const Matrix& m, int x, int y, int w, int h) {
    if (m.identity || w < 1 || h < 1 || !program()) return;
    glActiveTexture(GL_TEXTURE0);
    if (gTex == 0 || gTexW != w || gTexH != h) {
        if (gTex == 0) glGenTextures(1, &gTex);
        glBindTexture(GL_TEXTURE_2D, gTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        gTexW = w; gTexH = h;
    } else {
        glBindTexture(GL_TEXTURE_2D, gTex);
    }
    // The finished frame, copied out of the bound framebuffer and drawn back corrected over the
    // same rectangle (texel (i, j) lands on pixel (x + i, y + j): no flip).
    glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, x, y, w, h);
    static const GLfloat pos[12] = { -1, -1,  1, -1,  1, 1,  1, 1,  -1, 1,  -1, -1 };
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_STENCIL_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glViewport(x, y, w, h);
    glUseProgram(gProg);
    glUniform1i(gLocTex, 0);
    glUniformMatrix3fv(gLocM, 1, GL_FALSE, m.m);
    glUniform3f(gLocOff, m.m[9], m.m[10], m.m[11]);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(gLocPos, 2, GL_FLOAT, GL_FALSE, 0, pos);
    glEnableVertexAttribArray(gLocPos);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(gLocPos);
    glUseProgram(0);
}

void releaseGl() {
    if (gTex) glDeleteTextures(1, &gTex);
    if (gProg) glDeleteProgram(gProg);
    gTex = 0; gProg = 0; gTexW = gTexH = 0; gFailed = false;
}

}  // namespace colormatrix
}  // namespace android
