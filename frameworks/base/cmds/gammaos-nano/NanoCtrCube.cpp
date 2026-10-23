// NanoCtrCube - the category cube (see NanoCtrCube.h).
//
// The cube is a rounded box (the applet banners' glass cubes have the same soft corners) built
// as six patches, one a face: a subdivided square pushed onto the rounded body, its bevel going
// with the face it belongs to. It lives in the top screen's layout space: the vertices are
// rotated, pushed through a small perspective and placed around (cx, cy); the layout projection
// then maps them to the panel like any pane. The look is the one the theme gives its glass
// solids (NanoCtrGlyph3d.cpp): a milky tinted body whose colour leans with the view angle, a
// fresnel rim, a broad highlight, blended over what is behind. The pictogram sits in the face's
// flat middle as ink, a deeper shade of the face's colour. A convex body needs no depth buffer:
// the triangles facing away are drawn first, then the ones facing the viewer.
#include "NanoCtrCube.h"

#include "NanoCtrLayout.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

namespace android {
namespace ctr {

namespace {

const char* kVs = R"(
attribute vec3 a_pos;
attribute vec3 a_n;
attribute vec2 a_uv;
uniform mat4 u_proj;
uniform mat3 u_rot;
uniform vec2 u_center;
uniform float u_half;
uniform float u_camera;
varying vec3 v_n;
varying vec3 v_p;
varying vec2 v_uv;
void main() {
    vec3 p = u_rot * (a_pos * u_half);
    v_n = u_rot * a_n;
    v_p = vec3(p.xy, p.z - u_camera);
    v_uv = a_uv;
    float f = u_camera / (u_camera - p.z);
    gl_Position = u_proj * vec4(u_center + p.xy * f, 0.0, 1.0);
}
)";

const char* kFs = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D u_tex;
uniform vec3 u_color;
uniform vec3 u_light;
uniform float u_pict;    // 1: the face carries a pictogram
uniform float u_back;    // 1: the pass of the triangles facing away (seen through the body)
varying vec3 v_n;
varying vec3 v_p;
varying vec2 v_uv;
void main() {
    vec3 n = normalize(v_n);
    if (u_back > 0.5) n = -n;
    vec3 l = normalize(u_light);
    vec3 v = normalize(-v_p);
    vec3 h = normalize(l + v);
    float diffuse = max(dot(n, l), 0.0);
    float spec = pow(max(dot(n, h), 0.0), 28.0);
    float facing = max(dot(n, v), 0.0);
    float fresnel = pow(1.0 - facing, 2.5);
    vec3 tint = u_color;
    vec3 shifted = mix(tint, tint.gbr * 0.9 + tint * 0.1, 0.3 * (1.0 - facing));
    vec3 milky = mix(shifted, vec3(1.0), 0.18);
    vec3 body = mix(milky * (0.78 + 0.22 * diffuse), shifted * shifted * 0.95, fresnel * 0.8);
    float gloss = pow(max(dot(n, h), 0.0), 24.0);
    vec3 rgb = body + vec3(0.35) * gloss + vec3(0.1) * spec;
    // the pictogram in the face's flat middle, ink a deeper shade of the face colour
    float a = u_pict > 0.5 ? texture2D(u_tex, v_uv).a : 0.0;
    rgb = mix(rgb, tint * 0.36, a * 0.92 * (0.55 + 0.45 * facing));
    float alpha = (u_back > 0.5 ? 0.55 : 0.84) + 0.16 * fresnel;
    gl_FragColor = vec4(rgb, min(alpha, 1.0));
}
)";

GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { glDeleteShader(s); return 0; }
    return s;
}

// the faces' outward normals and the two in-plane axes (u right, v down on the pictogram)
struct FaceFrame { float n[3], u[3], v[3]; };
const FaceFrame kFrames[CategoryCube::kFaces] = {
    {{0, 0, 1}, {1, 0, 0}, {0, -1, 0}},     // +Z front
    {{1, 0, 0}, {0, 0, -1}, {0, -1, 0}},    // +X right
    {{0, 0, -1}, {-1, 0, 0}, {0, -1, 0}},   // -Z back
    {{-1, 0, 0}, {0, 0, 1}, {0, -1, 0}},    // -X left
    {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},      // +Y top
    {{0, -1, 0}, {1, 0, 0}, {0, 0, -1}},    // -Y bottom
};
// the yaw / pitch that bring each face to the front (yaw about Y first, then pitch about X)
const float kFaceYaw[CategoryCube::kFaces] = {0, -90, 180, 90, 0, 0};
const float kFacePitch[CategoryCube::kFaces] = {0, 0, 0, 0, 90, -90};

constexpr int kSub = 10;          // quads along a face edge
constexpr float kRadius = 0.26f;  // corner radius, of the half edge
constexpr float kPictSpan = 0.74f; // the pictogram's width, of the face

float wrapDeg(float a) {
    while (a > 180) a -= 360;
    while (a < -180) a += 360;
    return a;
}

struct Vertex { float p[3], n[3], uv[2]; };

}  // namespace

struct CategoryCube::Impl {
    GLuint program = 0;
    GLint uProj = -1, uRot = -1, uCenter = -1, uHalf = -1, uCamera = -1, uColor = -1, uLight = -1, uPict = -1, uBack = -1;
    TexturePtr tex[kFaces];
    float color[kFaces][3] = {};
    int current = 0;
    float yaw = 0, pitch = 0;      // the eased orientation (degrees)
    double lastT = -1;
    // the mesh: a patch a face, with its triangles' outward normals for the two pass split
    std::vector<Vertex> verts[kFaces];
    std::vector<uint16_t> tris[kFaces];    // 3 indices a triangle
    std::vector<uint16_t> order[2];        // per draw: the back then the front triangles

    void buildMesh() {
        for (int f = 0; f < kFaces; f++) {
            const FaceFrame& fr = kFrames[f];
            auto& vs = verts[f];
            auto& ts = tris[f];
            vs.clear(); ts.clear();
            const float inner = 1.0f - kRadius;
            for (int j = 0; j <= kSub; j++)
                for (int i = 0; i <= kSub; i++) {
                    // the point on the unit cube's face, then onto the rounded body: the box
                    // shrunk by the radius plus the radius along the outward direction
                    const float a = i / (float)kSub * 2 - 1, b = j / (float)kSub * 2 - 1;
                    float q[3];
                    for (int c = 0; c < 3; c++) q[c] = fr.n[c] + fr.u[c] * a + fr.v[c] * b;
                    float core[3], d[3];
                    for (int c = 0; c < 3; c++) {
                        core[c] = std::clamp(q[c], -inner, inner);
                        d[c] = q[c] - core[c];
                    }
                    float len = sqrtf(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                    if (len < 1e-6f) { memcpy(d, fr.n, sizeof(d)); len = 1; }
                    Vertex v;
                    for (int c = 0; c < 3; c++) {
                        v.n[c] = d[c] / len;
                        v.p[c] = core[c] + v.n[c] * kRadius;
                    }
                    // the pictogram over the flat middle (uv 0..1 across kPictSpan of the face)
                    v.uv[0] = 0.5f + a / kPictSpan * 0.5f;
                    v.uv[1] = 0.5f + b / kPictSpan * 0.5f;
                    vs.push_back(v);
                }
            for (int j = 0; j < kSub; j++)
                for (int i = 0; i < kSub; i++) {
                    const uint16_t o = (uint16_t)(j * (kSub + 1) + i);
                    const uint16_t r = (uint16_t)(o + kSub + 1);
                    ts.insert(ts.end(), {o, (uint16_t)(o + 1), r, (uint16_t)(o + 1), (uint16_t)(r + 1), r});
                }
        }
    }

    bool link() {
        if (program) return true;
        GLuint vs = compileShader(GL_VERTEX_SHADER, kVs), fs = compileShader(GL_FRAGMENT_SHADER, kFs);
        if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return false; }
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glBindAttribLocation(program, 0, "a_pos");
        glBindAttribLocation(program, 1, "a_n");
        glBindAttribLocation(program, 2, "a_uv");
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        GLint linked = 0;
        glGetProgramiv(program, GL_LINK_STATUS, &linked);
        if (!linked) { glDeleteProgram(program); program = 0; return false; }
        uProj = glGetUniformLocation(program, "u_proj");
        uRot = glGetUniformLocation(program, "u_rot");
        uCenter = glGetUniformLocation(program, "u_center");
        uHalf = glGetUniformLocation(program, "u_half");
        uCamera = glGetUniformLocation(program, "u_camera");
        uColor = glGetUniformLocation(program, "u_color");
        uLight = glGetUniformLocation(program, "u_light");
        uPict = glGetUniformLocation(program, "u_pict");
        uBack = glGetUniformLocation(program, "u_back");
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "u_tex"), 0);
        return true;
    }
};

CategoryCube::CategoryCube() : mImpl(new Impl) { mImpl->buildMesh(); }
CategoryCube::~CategoryCube() { releaseGl(); }

void CategoryCube::releaseGl() {
    if (mImpl->program) { glDeleteProgram(mImpl->program); mImpl->program = 0; }
    for (auto& t : mImpl->tex) t.reset();
    mReady = false;
}

void CategoryCube::setFaces(Assets& assets, const Face faces[kFaces]) {
    for (int i = 0; i < kFaces; i++) {
        memcpy(mImpl->color[i], faces[i].color, sizeof(mImpl->color[i]));
        mImpl->tex[i].reset();
        if (!faces[i].rgba || faces[i].w <= 0 || faces[i].h <= 0) continue;
        // the pictogram fitted into a square (its alpha the shape), a transparent border around
        // it so the clamp outside the uv square reads as nothing
        const int N = 192, pad = 16, span = N - 2 * pad;
        std::vector<uint8_t> px((size_t)N * N * 4, 0);
        const float fit = span / (float)std::max(faces[i].w, faces[i].h);
        const float ox = (N - faces[i].w * fit) * 0.5f, oy = (N - faces[i].h * fit) * 0.5f;
        auto alphaAt = [&](float x, float y) {   // bilinear, outside = 0
            x -= 0.5f; y -= 0.5f;
            const int ix = (int)floorf(x), iy = (int)floorf(y);
            const float fx = x - ix, fy = y - iy;
            auto a = [&](int sx, int sy) {
                return sx < 0 || sy < 0 || sx >= faces[i].w || sy >= faces[i].h
                           ? 0.0f : faces[i].rgba[((size_t)sy * faces[i].w + sx) * 4 + 3] / 255.0f;
            };
            return (a(ix, iy) * (1 - fx) + a(ix + 1, iy) * fx) * (1 - fy) + (a(ix, iy + 1) * (1 - fx) + a(ix + 1, iy + 1) * fx) * fy;
        };
        const int ss = std::clamp((int)ceilf(1.0f / fit), 1, 6);   // supersample a shrunk source
        for (int ty = 0; ty < N; ty++)
            for (int tx = 0; tx < N; tx++) {
                float sum = 0;
                for (int sy = 0; sy < ss; sy++)
                    for (int sx = 0; sx < ss; sx++)
                        sum += alphaAt((tx + (sx + 0.5f) / ss - ox) / fit, (ty + (sy + 0.5f) / ss - oy) / fit);
                const uint8_t a = (uint8_t)lroundf(std::clamp(sum / (ss * ss), 0.0f, 1.0f) * 255);
                uint8_t* o = &px[((size_t)ty * N + tx) * 4];
                o[0] = o[1] = o[2] = 255;
                o[3] = a;
            }
        TexturePtr t = assets.rgbaTexture(px.data(), N, N);
        if (!t) continue;
        glBindTexture(GL_TEXTURE_2D, t->id);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        t->mipmapped = true;
        mImpl->tex[i] = t;
    }
    mImpl->yaw = kFaceYaw[mImpl->current];
    mImpl->pitch = kFacePitch[mImpl->current];
    mReady = true;
}

void CategoryCube::setCurrent(int face) {
    if (face < 0 || face >= kFaces) return;
    mImpl->current = face;
}

void CategoryCube::draw(const float proj[16], float cx, float cy, float size, double t) {
    Impl& I = *mImpl;
    if (!mReady || !I.link()) return;
    // ease towards the current face's orientation (the short way round), then sway
    float dt = I.lastT < 0 ? 0.0f : (float)std::clamp(t - I.lastT, 0.0, 0.1);
    I.lastT = t;
    const float k = 1.0f - expf(-dt * 6.0f);
    I.yaw += wrapDeg(kFaceYaw[I.current] - I.yaw) * k;
    I.pitch += wrapDeg(kFacePitch[I.current] - I.pitch) * k;
    const float yaw = (I.yaw + 16.0f * sinf((float)t * 0.9f)) * (float)M_PI / 180.0f;
    const float pitch = (I.pitch + 10.0f * sinf((float)t * 1.3f + 1.0f) - 8.0f) * (float)M_PI / 180.0f;
    const float cy_ = cosf(yaw), sy_ = sinf(yaw), cp = cosf(pitch), sp = sinf(pitch);
    // rotation = pitch about X after yaw about Y, column major for the shader
    const float rot[9] = {
        cy_, 0, -sy_,                       // column 0: the image of x
        sy_ * sp, cp, cy_ * sp,             // column 1: the image of y
        sy_ * cp, -sp, cy_ * cp,            // column 2: the image of z
    };
    auto rotate = [&](const float p[3], float out[3]) {
        for (int r = 0; r < 3; r++) out[r] = rot[r] * p[0] + rot[3 + r] * p[1] + rot[6 + r] * p[2];
    };
    const float half = size * 0.5f, camera = size * 3.2f;
    // split every face's triangles by where they face this frame
    for (int f = 0; f < kFaces; f++) {
        I.order[0].clear();
        I.order[1].clear();
    }
    const float light[3] = {-0.45f, 0.65f, 0.62f};
    const float center[2] = {cx, cy};
    glUseProgram(I.program);
    glUniformMatrix4fv(I.uProj, 1, GL_FALSE, proj);
    glUniformMatrix3fv(I.uRot, 1, GL_FALSE, rot);
    glUniform2fv(I.uCenter, 1, center);
    glUniform1f(I.uHalf, half);
    glUniform1f(I.uCamera, camera);
    glUniform3fv(I.uLight, 1, light);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_SCISSOR_TEST);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glActiveTexture(GL_TEXTURE0);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glEnableVertexAttribArray(2);
    for (int pass = 0; pass < 2; pass++) {   // 0: facing away, 1: facing the viewer
        glUniform1f(I.uBack, pass == 0 ? 1.0f : 0.0f);
        for (int f = 0; f < kFaces; f++) {
            const auto& vs = I.verts[f];
            const auto& ts = I.tris[f];
            std::vector<uint16_t>& o = I.order[pass];
            o.clear();
            for (size_t i = 0; i + 2 < ts.size(); i += 3) {
                float n[3] = {0, 0, 0}, rn[3];
                for (int k2 = 0; k2 < 3; k2++)
                    for (int c = 0; c < 3; c++) n[c] += vs[ts[i + k2]].n[c];
                rotate(n, rn);
                if ((rn[2] < 0) == (pass == 0)) o.insert(o.end(), {ts[i], ts[i + 1], ts[i + 2]});
            }
            if (o.empty()) continue;
            glUniform3fv(I.uColor, 1, I.color[f]);
            glUniform1f(I.uPict, I.tex[f] ? 1.0f : 0.0f);
            glBindTexture(GL_TEXTURE_2D, I.tex[f] ? I.tex[f]->id : 0);
            glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, sizeof(Vertex), &vs[0].p[0]);
            glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, sizeof(Vertex), &vs[0].n[0]);
            glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, sizeof(Vertex), &vs[0].uv[0]);
            glDrawElements(GL_TRIANGLES, (GLsizei)o.size(), GL_UNSIGNED_SHORT, o.data());
        }
    }
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);
    glDisableVertexAttribArray(2);
    glUseProgram(0);
}

}  // namespace ctr
}  // namespace android
