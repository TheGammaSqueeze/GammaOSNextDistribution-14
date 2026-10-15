// NanoCtr3d - nw::gfx (CGFX) model renderer for the 3DS theme's top screen.
//
// Port of /work/3ds/web/app/src/gfx3d (docs/menu.md "Top screen 3D scene"): models exported by
// the 3ds-web pipeline (CGFX JSON + glTF geometry, packed by tools/ctr_pack.py as model_*.cpk),
// skeletal and material animation, and the PICA200 fragment pipeline (one directional light
// with its LUTs, Fresnel, six combiner stages, alpha test) generated as GLSL ES 1.00. Models are
// drawn into an offscreen target with depth and stencil (nano's framebuffers have none) that is
// then composited into the top screen rectangle.
#pragma once

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <array>
#include <functional>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "NanoCtrPack.h"

namespace android {
namespace ctr {

using V3 = std::array<float, 3>;
using V4 = std::array<float, 4>;

struct M4 {
    float m[16];
    static M4 identity() {
        M4 r{};
        r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1;
        return r;
    }
};

struct CgfxCurve {
    bool hasConstant = false;
    float constant = 0;
    float startFrame = 0, endFrame = 0;
    std::string preRepeat, postRepeat;
    struct Segment {
        float startFrame = 0, endFrame = 0;
        int interpolation = 2;   // 0 step 1 linear 2 hermite
        bool hasConstant = false;
        float constant = 0;
        std::vector<std::vector<float>> keys;
    };
    std::vector<Segment> segments;
    float evaluate(float frame) const;
};

struct CgfxAnimElement {
    std::string name, target, type;
    std::map<std::string, CgfxCurve> curves;
    bool hasCurve = false;
    CgfxCurve curve;
    std::vector<std::string> textures;
};

struct CgfxAnimation {
    std::string name;
    bool loop = false;
    float frameCount = 0;
    std::vector<CgfxAnimElement> elements;
};

struct CgfxBone {
    std::string name;
    int index = 0, parent = -1;
    V3 scale{1, 1, 1}, rotation{0, 0, 0}, translation{0, 0, 0};
    int billboard = 0;   // 0 off, 1 world/screen kinds, 2 y axial kinds
};

struct CgfxMapper {
    bool valid = false;
    std::string texture;
    GLint wrapS = GL_CLAMP_TO_EDGE, wrapT = GL_CLAMP_TO_EDGE, mag = GL_LINEAR, min = GL_LINEAR;
    bool borderS = false, borderT = false;
    V4 border{0, 0, 0, 1};
};

struct CgfxCoordinator {
    int sourceIndex = 0;
    bool uv = true;
    bool maya = false;
    float sx = 1, sy = 1, rotation = 0, tx = 0, ty = 0;
};

struct CgfxMaterialDef {
    std::string name;
    bool lighting = false;
    std::string texCoordConfig;
    int translucency = 0;     // 0 opaque 1 translucent 2 subtractive 3 additive
    V4 colors[11];            // emission ambient diffuse specular0 specular1 constant0..5
    int cull = 0;             // 0 none 1 back 2 front
    int depthFlags = 0;
    GLenum depthFunc = GL_ALWAYS;
    bool blendOn = false;
    GLenum eqColor = GL_FUNC_ADD, eqAlpha = GL_FUNC_ADD;
    GLenum srcColor = GL_ONE, dstColor = GL_ZERO, srcAlpha = GL_ONE, dstAlpha = GL_ZERO;
    V4 blendConstant{0, 0, 0, 1};
    bool stencilOn = false;
    GLenum stencilFunc = GL_ALWAYS, sFail = GL_KEEP, sZFail = GL_KEEP, sZPass = GL_KEEP;
    int stencilRef = 0, stencilMask = 0xff, stencilWriteMask = 0xff;
    std::vector<CgfxCoordinator> coordinators;
    CgfxMapper mappers[3];
    // fragment shader
    std::set<std::string> flags;
    int layerConfig = 0;
    std::string fresnel;      // none primary secondary both
    struct Lut { bool present = false; std::string input, table, sampler; float scale = 1; };
    Lut luts[6];              // distribution0 distribution1 fresnel reflectanceR G B
    struct Stage {
        int constant = 0;
        std::string colorSources[3], alphaSources[3], colorOperands[3], alphaOperands[3];
        std::string colorCombiner, alphaCombiner;
        float colorScale = 1, alphaScale = 1;
    };
    std::vector<Stage> stages;
    bool alphaTest = false;
    std::string alphaFunction;
    float alphaReference = 0;
    bool bufferColorIn[6] = {false}, bufferAlphaIn[6] = {false};
    V4 bufferColor{0, 0, 0, 0};
    std::string shaderKey;
};

struct GpuTex {
    GLuint id = 0;
    int width = 0, height = 0;
    bool owned = true;
    bool pixelArt = false;   // sampled nearest whatever the mapper says (a DS cartridge icon)
};

// A model file uploaded to the GPU.
class ModelResource {
public:
    ~ModelResource();
    bool load(Pack& pack, Pack* shared);
    std::string name;
    std::shared_ptr<void> keepAlive;   // the in-memory pack a ROM banner was loaded from
    std::vector<CgfxMaterialDef> materials;
    std::vector<CgfxBone> bones;
    struct Mesh { int shape = 0, material = 0; bool visible = true; int priority = 0; std::string node; };
    std::vector<Mesh> meshes;
    std::vector<V3> shapeCenters;
    std::vector<CgfxAnimation> skeletal, material;
    struct Light { bool present = false; V3 direction{0, 0, -1}; V3 ambient{}, diffuse{}, specular0{}, specular1{}; } light;
    struct Primitive {
        int mesh = 0, material = 0, bone = -1;
        GLuint buffers[6] = {0, 0, 0, 0, 0, 0};   // position normal color uv0 uv1 uv2
        GLenum types[6] = {0, 0, 0, 0, 0, 0};
        int components[6] = {0, 0, 0, 0, 0, 0};
        GLuint index = 0;
        GLenum indexType = GL_UNSIGNED_SHORT;
        int indexCount = 0;
    };
    std::vector<Primitive> primitives;
    std::map<std::string, std::shared_ptr<GpuTex>> textures;
    std::map<std::string, int> lutRows;
    GLuint lutTexture = 0;
    int lutCount = 0;
    void setTexture(const std::string& n, std::shared_ptr<GpuTex> t) { textures[n] = std::move(t); }
    bool hasTexture(const std::string& n) const { return textures.count(n) != 0; }
    void releaseGl();
private:
    std::vector<GLuint> mBuffers;
};

struct MaterialState {
    const CgfxMaterialDef* source = nullptr;
    V4 colors[11];
    struct Coord { float sx, sy, rotation, tx, ty; };
    std::vector<Coord> coords;
    std::string textures[3];
    std::map<int, V4> overrides;          // color index -> value (theme colours)
    std::map<int, std::string> textureOverrides;
    void reset();
    const std::string& texture(int unit) const {
        auto it = textureOverrides.find(unit);
        return it != textureOverrides.end() ? it->second : textures[unit];
    }
    void textureMatrix(int index, float out[8]) const;
};

class ModelInstance {
public:
    explicit ModelInstance(std::shared_ptr<ModelResource> res);
    std::shared_ptr<ModelResource> resource;
    M4 transform = M4::identity();
    int layer = 0;
    bool visible = true;
    std::set<int> hiddenMeshes;
    std::vector<MaterialState> materials;
    std::vector<M4> boneWorld;
    bool play(const std::string& name, int loop = -1, float speed = 1, float frame = 0);
    void stop(const std::string& name);
    bool isPlaying(const std::string& name) const;
    void update(float frames);
    const M4& boneMatrix(int index) const { return index >= 0 && index < (int)boneWorld.size() ? boneWorld[index] : transform; }
    MaterialState* findMaterial(const std::string& n);
private:
    struct Player { const CgfxAnimation* anim; float frame, speed; bool loop; };
    std::vector<Player> mSkeletal, mMaterial;
    struct Pose { V3 scale, rotation, translation; };
    std::vector<Pose> mPoses;
    void resetPoses();
    void computeBones();
};

struct Camera3d {
    V3 eye{0, 1, 44.786f}, target{0, 1, 0};
    float twist = 0, fovY = 0.5235988f, aspect = 5.0f / 3.0f, near = 26.5f, far = 1000;
    M4 view, projection;
    void update();
};

class Renderer3d {
public:
    Renderer3d();
    ~Renderer3d();
    void releaseGl();
    // Renders models into the offscreen target (w x h, cleared to `clear`); returns its texture.
    // `underlay` draws into the target after the clear, under the models (a background image the
    // banners sit on, like the console's layer behind the 3D pass); it gets the target size.
    GLuint render(const Camera3d& cam, const std::vector<ModelInstance*>& models, int w, int h, const float clear[3],
                  const std::function<void(int w, int h)>& underlay = nullptr);
private:
    struct Program;
    Program* program(const CgfxMaterialDef& m);
    void ensureTarget(int w, int h);
    GLuint mFbos[2] = {0, 0}, mColors[2] = {0, 0}, mDepth = 0, mStencil = 0, mWhite = 0;
    int mCur = 0;
    int mW = 0, mH = 0;
    PFNGLDISCARDFRAMEBUFFEREXTPROC mDiscard = nullptr;
    bool mPacked = false;
    std::map<std::string, std::unique_ptr<Program>> mPrograms;
};

void mat4Transform(M4& out, const V3& s, const V3& r, const V3& t);   // S, then R = Rz Ry Rx, then T
void mat4Mul(M4& out, const M4& a, const M4& b);

}  // namespace ctr
}  // namespace android
