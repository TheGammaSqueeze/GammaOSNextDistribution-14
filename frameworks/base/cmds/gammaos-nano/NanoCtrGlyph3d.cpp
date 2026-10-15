// NanoCtrGlyph3d - a monochrome icon as a solid 3D object (see NanoCtrGlyph3d.h).
#define LOG_TAG "NanoCtr"

#include "NanoCtrGlyph3d.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <unordered_map>
#include <log/log.h>

namespace android {
namespace ctr {

GLuint GlyphMesh::sProgram = 0;
GLint GlyphMesh::sMv = -1, GlyphMesh::sProj = -1, GlyphMesh::sNm = -1, GlyphMesh::sFace = -1, GlyphMesh::sSide = -1,
      GlyphMesh::sLight = -1, GlyphMesh::sCap = -1;

namespace {

constexpr int kUpsample = 2;      // field resolution against the glyph texture
constexpr float kSpread = 6.0f;   // field pixels from the edge to 0 / 255 in the face texture
constexpr float kSimplify = 0.3f; // outline simplification tolerance, field pixels
constexpr float kCornerDeg = 35;  // a wall normal further than this from the field's gradient: a corner

const char* kVs = R"(
attribute vec3 a_pos;
attribute vec3 a_n;
attribute vec2 a_uv;
uniform mat4 u_mv;
uniform mat4 u_proj;
uniform mat3 u_nm;
varying vec3 v_n;
varying vec2 v_uv;
varying vec3 v_p;
void main() {
    vec4 p = u_mv * vec4(a_pos, 1.0);
    v_p = p.xyz;
    v_n = u_nm * a_n;
    v_uv = a_uv;
    gl_Position = u_proj * p;
}
)";

const char* kFs = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D u_sdf;
uniform float u_cap;
uniform vec3 u_face;
uniform vec3 u_side;
uniform vec3 u_light;
varying vec3 v_n;
varying vec2 v_uv;
varying vec3 v_p;
void main() {
    if (u_cap > 0.5 && texture2D(u_sdf, v_uv).r < 0.5) discard;
    vec3 n = normalize(v_n);
    vec3 l = normalize(-u_light);
    vec3 v = normalize(-v_p);
    vec3 h = normalize(l + v);
    float diffuse = max(dot(n, l), 0.0);
    float spec = pow(max(dot(n, h), 0.0), 28.0);
    vec3 base = u_cap > 0.5 ? u_face : u_side;
    gl_FragColor = vec4(base * (0.55 + 0.5 * diffuse) + vec3(0.16) * spec, 1.0);
}
)";

GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512] = {};
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        ALOGE("glyph3d: shader: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

// Felzenszwalb and Huttenlocher's exact squared Euclidean distance transform, one dimension.
void edt1d(const float* f, float* d, int n, std::vector<int>& v, std::vector<float>& z) {
    v.assign(n, 0);
    z.assign(n + 1, 0);
    int k = 0;
    v[0] = 0;
    z[0] = -std::numeric_limits<float>::infinity();
    z[1] = std::numeric_limits<float>::infinity();
    for (int q = 1; q < n; q++) {
        float s;
        for (;;) {
            const int p = v[k];
            s = ((f[q] + (float)q * q) - (f[p] + (float)p * p)) / (2.0f * (q - p));
            if (s <= z[k] && k > 0) { k--; continue; }
            break;
        }
        k++;
        v[k] = q;
        z[k] = s;
        z[k + 1] = std::numeric_limits<float>::infinity();
    }
    k = 0;
    for (int q = 0; q < n; q++) {
        while (z[k + 1] < q) k++;
        const float dq = (float)(q - v[k]);
        d[q] = dq * dq + f[v[k]];
    }
}

// Squared distance from every pixel to the nearest pixel where `seed` is set.
std::vector<float> edt2d(const std::vector<uint8_t>& seed, int n) {
    const float inf = 1e20f;
    std::vector<float> g((size_t)n * n);
    for (size_t i = 0; i < g.size(); i++) g[i] = seed[i] ? 0.0f : inf;
    std::vector<float> f(n), d(n);
    std::vector<int> v;
    std::vector<float> z;
    for (int x = 0; x < n; x++) {   // columns
        for (int y = 0; y < n; y++) f[y] = g[(size_t)y * n + x];
        edt1d(f.data(), d.data(), n, v, z);
        for (int y = 0; y < n; y++) g[(size_t)y * n + x] = d[y];
    }
    for (int y = 0; y < n; y++) {   // rows
        edt1d(&g[(size_t)y * n], d.data(), n, v, z);
        memcpy(&g[(size_t)y * n], d.data(), sizeof(float) * n);
    }
    return g;
}

struct P2 { float x, y; };

float perpDist(const P2& p, const P2& a, const P2& b) {
    const float dx = b.x - a.x, dy = b.y - a.y;
    const float len = std::sqrt(dx * dx + dy * dy);
    if (len < 1e-6f) return std::hypot(p.x - a.x, p.y - a.y);
    return std::fabs((p.x - a.x) * dy - (p.y - a.y) * dx) / len;
}

void rdp(const std::vector<P2>& pts, int a, int b, float tol, std::vector<char>& keep) {
    if (b <= a + 1) return;
    float best = -1;
    int at = -1;
    for (int i = a + 1; i < b; i++) {
        const float d = perpDist(pts[i], pts[a], pts[b]);
        if (d > best) { best = d; at = i; }
    }
    if (best > tol) {
        keep[at] = 1;
        rdp(pts, a, at, tol, keep);
        rdp(pts, at, b, tol, keep);
    }
}

void normalMatrix(float out[9], const float m[16]) {
    // inverse transpose of the upper 3x3 (column major)
    const float a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], i = m[10];
    const float A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
    const float D = -(b * i - c * h), E = a * i - c * g, F = -(a * h - b * g);
    const float G = b * f - c * e, H = -(a * f - c * d), I = a * e - b * d;
    float det = a * A + b * B + c * C;
    if (std::fabs(det) < 1e-12f) det = 1;
    const float k = 1.0f / det;
    // inverse = (1/det) * adj^T; its transpose = (1/det) * cofactor matrix
    out[0] = A * k; out[3] = D * k; out[6] = G * k;
    out[1] = B * k; out[4] = E * k; out[7] = H * k;
    out[2] = C * k; out[5] = F * k; out[8] = I * k;
}

}  // namespace

GlyphMesh::~GlyphMesh() { releaseGl(); }

void GlyphMesh::releaseGl() {
    if (mVbo) glDeleteBuffers(1, &mVbo);
    if (mIbo) glDeleteBuffers(1, &mIbo);
    if (mSdf) glDeleteTextures(1, &mSdf);
    mVbo = mIbo = mSdf = 0;
    mSideIndices = mCapIndices = 0;
}

void GlyphMesh::releaseProgram() {
    if (sProgram) glDeleteProgram(sProgram);
    sProgram = 0;
}

bool GlyphMesh::build(GLuint texture, int w, int h, float boxW, float boxH, float cx, float cy, float depth) {
    releaseGl();
    if (!texture || w <= 1 || h <= 1) return false;
    // the glyph's pixels
    std::vector<uint8_t> rgba((size_t)w * h * 4);
    {
        GLint prevFbo = 0;
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
        GLuint fbo = 0;
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        const bool ok = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
        if (ok) glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glDeleteFramebuffers(1, &fbo);
        if (!ok) {
            ALOGW("glyph3d: texture %u not readable", texture);
            return false;
        }
    }
    // alpha upsampled (bilinear) into a square field grid with a margin for the outline
    const int src = std::max(w, h);
    const int margin = (int)kSpread + 2;
    const int n = src * kUpsample + 2 * margin;
    auto alphaAt = [&](float x, float y) {   // x, y in texture pixels (centres at +0.5)
        x -= 0.5f;
        y -= 0.5f;
        const int x0 = (int)std::floor(x), y0 = (int)std::floor(y);
        const float fx = x - x0, fy = y - y0;
        auto px = [&](int xi, int yi) -> float {
            if (xi < 0 || yi < 0 || xi >= w || yi >= h) return 0.0f;
            return rgba[((size_t)yi * w + xi) * 4 + 3] / 255.0f;
        };
        return (px(x0, y0) * (1 - fx) + px(x0 + 1, y0) * fx) * (1 - fy) +
               (px(x0, y0 + 1) * (1 - fx) + px(x0 + 1, y0 + 1) * fx) * fy;
    };
    std::vector<float> alpha((size_t)n * n);
    std::vector<uint8_t> inside((size_t)n * n), outside((size_t)n * n);
    int bx0 = n, by0 = n, bx1 = -1, by1 = -1;
    for (int y = 0; y < n; y++) {
        for (int x = 0; x < n; x++) {
            const float a = alphaAt((x - margin + 0.5f) / kUpsample, (y - margin + 0.5f) / kUpsample);
            alpha[(size_t)y * n + x] = a;
            const bool in = a >= 0.5f;
            inside[(size_t)y * n + x] = in;
            outside[(size_t)y * n + x] = !in;
            if (in) { bx0 = std::min(bx0, x); by0 = std::min(by0, y); bx1 = std::max(bx1, x); by1 = std::max(by1, y); }
        }
    }
    if (bx1 < 0) return false;   // an empty glyph
    // signed distance (field pixels, negative inside), the anti-aliased alpha refining it near the edge
    const std::vector<float> dOut = edt2d(inside, n), dIn = edt2d(outside, n);
    std::vector<float> sd((size_t)n * n);
    for (size_t i = 0; i < sd.size(); i++) {
        const float a = alpha[i];
        if (inside[i]) sd[i] = -(std::sqrt(dIn[i]) - 0.5f);
        else sd[i] = std::sqrt(dOut[i]) - 0.5f;
        // within a pixel of the edge the coverage itself is the better estimate
        if (dIn[i] <= 1.0f && dOut[i] <= 1.0f) sd[i] = 0.5f - a;
        else if (inside[i] && dIn[i] <= 1.0f) sd[i] = std::min(sd[i], 0.5f - a);
        else if (!inside[i] && dOut[i] <= 1.0f) sd[i] = std::max(sd[i], 0.5f - a);
    }
    {   // one 3x3 smoothing pass takes the remaining stair steps off the outline
        std::vector<float> s2 = sd;
        for (int y = 1; y < n - 1; y++)
            for (int x = 1; x < n - 1; x++) {
                float acc = 0;
                for (int j = -1; j <= 1; j++)
                    for (int i = -1; i <= 1; i++) acc += sd[(size_t)(y + j) * n + (x + i)] * ((i == 0 ? 2 : 1) * (j == 0 ? 2 : 1));
                s2[(size_t)y * n + x] = acc / 16.0f;
            }
        sd.swap(s2);
    }
    // the faces' texture: the field around the edge, 0.5 on the outline, larger inside
    {
        std::vector<uint8_t> tex((size_t)n * n);
        for (size_t i = 0; i < tex.size(); i++)
            tex[i] = (uint8_t)std::clamp((int)std::lround((0.5f - sd[i] / (2 * kSpread)) * 255.0f), 0, 255);
        glGenTextures(1, &mSdf);
        glBindTexture(GL_TEXTURE_2D, mSdf);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, n, n, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE, tex.data());
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    auto sdAt = [&](float x, float y) {
        x = std::clamp(x, 0.0f, (float)n - 1.001f);
        y = std::clamp(y, 0.0f, (float)n - 1.001f);
        const int x0 = (int)x, y0 = (int)y;
        const float fx = x - x0, fy = y - y0;
        auto g = [&](int xi, int yi) { return sd[(size_t)yi * n + xi]; };
        return (g(x0, y0) * (1 - fx) + g(x0 + 1, y0) * fx) * (1 - fy) + (g(x0, y0 + 1) * (1 - fx) + g(x0 + 1, y0 + 1) * fx) * fy;
    };
    auto gradAt = [&](float x, float y) {
        const float gx = sdAt(x + 0.75f, y) - sdAt(x - 0.75f, y), gy = sdAt(x, y + 0.75f) - sdAt(x, y - 0.75f);
        const float len = std::sqrt(gx * gx + gy * gy);
        return len > 1e-6f ? P2{gx / len, gy / len} : P2{0, 0};
    };
    // the outline: marching squares at sd = 0 on the sample grid, segments oriented with the
    // inside on their left (x right, y down), joined into closed loops
    std::unordered_map<int64_t, P2> edgePoint;
    std::unordered_map<int64_t, int64_t> next;   // edge id -> the edge id the outline continues to
    auto edgeId = [&](int x, int y, bool vertical) -> int64_t { return ((int64_t)y * n + x) * 2 + (vertical ? 1 : 0); };
    auto point = [&](int64_t id) {
        auto it = edgePoint.find(id);
        if (it != edgePoint.end()) return it->second;
        const bool vertical = id & 1;
        const int64_t cell = id >> 1;
        const int x = (int)(cell % n), y = (int)(cell / n);
        const float a = sd[(size_t)y * n + x];
        const float b = vertical ? sd[(size_t)(y + 1) * n + x] : sd[(size_t)y * n + x + 1];
        const float t = std::clamp(a / (a - b), 0.0f, 1.0f);
        const P2 p = vertical ? P2{(float)x, y + t} : P2{x + t, (float)y};
        edgePoint[id] = p;
        return p;
    };
    for (int y = 0; y < n - 1; y++) {
        for (int x = 0; x < n - 1; x++) {
            const bool a = sd[(size_t)y * n + x] < 0, b = sd[(size_t)y * n + x + 1] < 0;
            const bool c = sd[(size_t)(y + 1) * n + x + 1] < 0, d = sd[(size_t)(y + 1) * n + x] < 0;
            const int code = (a ? 1 : 0) | (b ? 2 : 0) | (c ? 4 : 0) | (d ? 8 : 0);
            if (code == 0 || code == 15) continue;
            const int64_t top = edgeId(x, y, false), right = edgeId(x + 1, y, true);
            const int64_t bottom = edgeId(x, y + 1, false), left = edgeId(x, y, true);
            auto link = [&](int64_t from, int64_t to) { point(from); point(to); next[from] = to; };
            // corners a (top left), b (top right), c (bottom right), d (bottom left); each segment
            // runs with the inside corners on its left
            switch (code) {
                case 1: link(top, left); break;
                case 2: link(right, top); break;
                case 3: link(right, left); break;
                case 4: link(bottom, right); break;
                case 5: {   // a and c inside: the cell centre decides
                    const float centre = (sd[(size_t)y * n + x] + sd[(size_t)y * n + x + 1] + sd[(size_t)(y + 1) * n + x + 1] +
                                          sd[(size_t)(y + 1) * n + x]) / 4;
                    if (centre < 0) { link(top, right); link(bottom, left); }
                    else { link(top, left); link(bottom, right); }
                    break;
                }
                case 6: link(bottom, top); break;
                case 7: link(bottom, left); break;
                case 8: link(left, bottom); break;
                case 9: link(top, bottom); break;
                case 10: {
                    const float centre = (sd[(size_t)y * n + x] + sd[(size_t)y * n + x + 1] + sd[(size_t)(y + 1) * n + x + 1] +
                                          sd[(size_t)(y + 1) * n + x]) / 4;
                    if (centre < 0) { link(right, bottom); link(left, top); }
                    else { link(right, top); link(left, bottom); }
                    break;
                }
                case 11: link(right, bottom); break;
                case 12: link(left, right); break;
                case 13: link(top, right); break;
                case 14: link(left, top); break;
            }
        }
    }
    std::vector<std::vector<P2>> loops;
    {
        std::unordered_map<int64_t, char> used;
        for (const auto& kv : next) {
            if (used.count(kv.first)) continue;
            std::vector<P2> loop;
            int64_t at = kv.first;
            while (!used.count(at)) {
                used[at] = 1;
                loop.push_back(edgePoint[at]);
                auto it = next.find(at);
                if (it == next.end()) break;
                at = it->second;
            }
            if (loop.size() >= 3) loops.push_back(std::move(loop));
        }
    }
    // simplify each closed loop (split at its farthest point pair so RDP has two anchors)
    for (auto& loop : loops) {
        const int m = (int)loop.size();
        int far = 0;
        float best = -1;
        for (int i = 1; i < m; i++) {
            const float d = std::hypot(loop[i].x - loop[0].x, loop[i].y - loop[0].y);
            if (d > best) { best = d; far = i; }
        }
        std::vector<P2> open(loop.begin(), loop.end());
        open.push_back(loop[0]);
        std::vector<char> keep(open.size(), 0);
        keep[0] = keep[far] = keep[open.size() - 1] = 1;
        rdp(open, 0, far, kSimplify, keep);
        rdp(open, far, (int)open.size() - 1, kSimplify, keep);
        std::vector<P2> out;
        for (size_t i = 0; i + 1 < open.size(); i++) if (keep[i]) out.push_back(open[i]);
        loop.swap(out);
    }
    // field pixels -> model units: the glyph's ink box fitted into boxW x boxH, centred on (cx, cy);
    // the texture's rows run top first (as the home draws it on the grid), model y runs up
    const float inkW = (float)(bx1 - bx0 + 1), inkH = (float)(by1 - by0 + 1);
    const float scale = std::min(boxW / inkW, boxH / inkH);
    const float midX = (bx0 + bx1 + 1) * 0.5f, midY = (by0 + by1 + 1) * 0.5f;
    auto toModelX = [&](float x) { return cx + (x + 0.5f - midX) * scale; };
    auto toModelY = [&](float y) { return cy - (y + 0.5f - midY) * scale; };
    const float hz = depth * 0.5f;
    std::vector<float> verts;   // position 3, normal 3, uv 2
    std::vector<uint16_t> idx;
    auto vertex = [&](float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        verts.insert(verts.end(), {x, y, z, nx, ny, nz, u, v});
        return (uint16_t)(verts.size() / 8 - 1);
    };
    const float cosCorner = std::cos(kCornerDeg * (float)M_PI / 180.0f);
    for (const auto& loop : loops) {
        const int m = (int)loop.size();
        for (int i = 0; i < m; i++) {
            const P2 a = loop[i], b = loop[(i + 1) % m];
            float ex = b.x - a.x, ey = b.y - a.y;
            const float len = std::sqrt(ex * ex + ey * ey);
            if (len < 1e-5f) continue;
            ex /= len;
            ey /= len;
            // the wall faces outward: the field grows away from the ink
            P2 segN{ey, -ex};
            {
                const P2 g = gradAt((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f);
                if (g.x * segN.x + g.y * segN.y < 0) segN = {-segN.x, -segN.y};
            }
            auto wallNormal = [&](const P2& p) {
                const P2 g = gradAt(p.x, p.y);
                return (g.x * segN.x + g.y * segN.y) > cosCorner ? g : segN;
            };
            const P2 na = wallNormal(a), nb = wallNormal(b);
            if (verts.size() / 8 + 4 > 65535) break;
            // normals from field space (y down) into model space (y up)
            const uint16_t i0 = vertex(toModelX(a.x), toModelY(a.y), hz, na.x, -na.y, 0, 0, 0);
            const uint16_t i1 = vertex(toModelX(b.x), toModelY(b.y), hz, nb.x, -nb.y, 0, 0, 0);
            const uint16_t i2 = vertex(toModelX(b.x), toModelY(b.y), -hz, nb.x, -nb.y, 0, 0, 0);
            const uint16_t i3 = vertex(toModelX(a.x), toModelY(a.y), -hz, na.x, -na.y, 0, 0, 0);
            idx.insert(idx.end(), {i0, i1, i2, i0, i2, i3});
        }
    }
    mSideIndices = (int)idx.size();
    {   // the faces: the ink box (plus the field's margin) cut by the field in the shader
        const float x0 = std::max(0, bx0 - 2), y0 = std::max(0, by0 - 2);
        const float x1 = std::min(n - 1, bx1 + 3), y1 = std::min(n - 1, by1 + 3);
        auto uv = [&](float x, float y, float* u, float* v) { *u = (x + 0.5f) / n; *v = (y + 0.5f) / n; };
        float u0, v0, u1, v1;
        uv(x0, y0, &u0, &v0);
        uv(x1, y1, &u1, &v1);
        const float X0 = toModelX(x0), X1 = toModelX(x1), Y0 = toModelY(y0), Y1 = toModelY(y1);
        for (int side = 0; side < 2; side++) {
            const float z = side == 0 ? hz : -hz, nz = side == 0 ? 1.0f : -1.0f;
            const uint16_t a = vertex(X0, Y0, z, 0, 0, nz, u0, v0);
            const uint16_t b = vertex(X1, Y0, z, 0, 0, nz, u1, v0);
            const uint16_t c = vertex(X1, Y1, z, 0, 0, nz, u1, v1);
            const uint16_t d = vertex(X0, Y1, z, 0, 0, nz, u0, v1);
            idx.insert(idx.end(), {a, b, c, a, c, d});
        }
    }
    mCapIndices = (int)idx.size() - mSideIndices;
    glGenBuffers(1, &mVbo);
    glBindBuffer(GL_ARRAY_BUFFER, mVbo);
    glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glGenBuffers(1, &mIbo);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mIbo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size() * sizeof(uint16_t), idx.data(), GL_STATIC_DRAW);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    ALOGI("glyph3d: texture %u %dx%d -> %zu loops, %zu vertices, %d wall triangles", texture, w, h, loops.size(),
          verts.size() / 8, mSideIndices / 3);
    return true;
}

void GlyphMesh::draw(const float modelView[16], const float projection[16], const float face[3], const float side[3],
                     const float light[3]) {
    if (!mVbo) return;
    if (!sProgram) {
        GLuint vs = compile(GL_VERTEX_SHADER, kVs), fs = compile(GL_FRAGMENT_SHADER, kFs);
        if (!vs || !fs) return;
        sProgram = glCreateProgram();
        glAttachShader(sProgram, vs);
        glAttachShader(sProgram, fs);
        glBindAttribLocation(sProgram, 0, "a_pos");
        glBindAttribLocation(sProgram, 1, "a_n");
        glBindAttribLocation(sProgram, 2, "a_uv");
        glLinkProgram(sProgram);
        glDeleteShader(vs);
        glDeleteShader(fs);
        GLint linked = 0;
        glGetProgramiv(sProgram, GL_LINK_STATUS, &linked);
        if (!linked) {
            glDeleteProgram(sProgram);
            sProgram = 0;
            return;
        }
        sMv = glGetUniformLocation(sProgram, "u_mv");
        sProj = glGetUniformLocation(sProgram, "u_proj");
        sNm = glGetUniformLocation(sProgram, "u_nm");
        sFace = glGetUniformLocation(sProgram, "u_face");
        sSide = glGetUniformLocation(sProgram, "u_side");
        sLight = glGetUniformLocation(sProgram, "u_light");
        sCap = glGetUniformLocation(sProgram, "u_cap");
        glUseProgram(sProgram);
        glUniform1i(glGetUniformLocation(sProgram, "u_sdf"), 0);
    }
    glUseProgram(sProgram);
    float nm[9];
    normalMatrix(nm, modelView);
    glUniformMatrix4fv(sMv, 1, GL_FALSE, modelView);
    glUniformMatrix4fv(sProj, 1, GL_FALSE, projection);
    glUniformMatrix3fv(sNm, 1, GL_FALSE, nm);
    glUniform3fv(sFace, 1, face);
    glUniform3fv(sSide, 1, side);
    glUniform3fv(sLight, 1, light);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);
    glDisable(GL_BLEND);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_CULL_FACE);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mSdf);
    glBindBuffer(GL_ARRAY_BUFFER, mVbo);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mIbo);
    for (int a = 0; a < 3; a++) glEnableVertexAttribArray(a);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 32, (const void*)0);
    glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 32, (const void*)12);
    glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, 32, (const void*)24);
    glUniform1f(sCap, 0.0f);
    glDrawElements(GL_TRIANGLES, mSideIndices, GL_UNSIGNED_SHORT, (const void*)0);
    glUniform1f(sCap, 1.0f);
    glDrawElements(GL_TRIANGLES, mCapIndices, GL_UNSIGNED_SHORT, (const void*)(mSideIndices * sizeof(uint16_t)));
    for (int a = 0; a < 3; a++) glDisableVertexAttribArray(a);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
}

}  // namespace ctr
}  // namespace android
