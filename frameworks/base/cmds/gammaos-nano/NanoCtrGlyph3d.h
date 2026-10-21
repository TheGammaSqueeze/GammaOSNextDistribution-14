// NanoCtrGlyph3d - a monochrome icon (nano's white system glyphs) as a solid 3D object for the 3DS
// theme's top screen: a game system's folder shows its system's icon there, extruded with some
// thickness and turning like the folder model it stands in for.
//
// The glyph's alpha is upsampled and turned into a signed distance field. Its outline (marching
// squares on the field, sub-pixel exact, simplified) is extruded into the side walls, with normals
// from the field's gradient so curves shade smoothly and corners stay crisp; the front and back faces
// are quads cut by the distance field, so their edges stay sharp at any size. Built once per glyph.
#pragma once

#include <GLES2/gl2.h>

#include <cstdint>
#include <memory>
#include <vector>

namespace android {
namespace ctr {

class GlyphMesh {
public:
    ~GlyphMesh();
    // Reads the glyph's alpha from a GL texture (w x h, rows top first) and builds the mesh, fitted
    // into a box of boxW x boxH units centred on (cx, cy), `depth` units thick. False when the
    // glyph is empty or the texture cannot be read.
    // colour: a full colour icon (PICO-8's blocks): the faces show its own pixels and each wall the
    // colour just inside it; face / side in draw() then scale those colours.
    bool build(GLuint texture, int w, int h, float boxW, float boxH, float cx, float cy, float depth, bool colour = false);
    // The build in three steps so the heavy middle one can run off the GL thread: readback copies
    // the glyph's pixels out of the texture (GL thread), compute turns them into the field, the
    // outline and the mesh (any thread, no GL), finish uploads the result (GL thread). build() is
    // the three in a row.
    struct Built {
        bool ok = false, colour = false;
        int w = 0, h = 0, n = 0;
        std::vector<uint8_t> rgb;      // the faces' colour texture (colour builds)
        std::vector<uint8_t> sdf;      // the n x n distance field texture
        std::vector<float> verts;
        std::vector<uint16_t> idx;
        int sideIndices = 0, capIndices = 0;
        float colorMap[4] = {1, 0, 1, 0};
        size_t loops = 0;
    };
    static bool readback(GLuint texture, int w, int h, std::vector<uint8_t>* rgba);
    static Built compute(const std::vector<uint8_t>& rgba, int w, int h, float boxW, float boxH, float cx, float cy,
                         float depth, bool colour);
    bool finish(const Built& b);
    bool coloured() const { return mColor != 0; }
    // Draws with the given model view and projection (column major), into the bound target with
    // depth testing. face / side: linear RGB colours of the faces and the walls; light: direction
    // the light travels, in view space.
    // hueMat: a 3x3 (column major) rotating the colours' hue, null for none; glass: a translucent
    // glass body (blended over what is already drawn) instead of the opaque solid.
    void draw(const float modelView[16], const float projection[16], const float face[3], const float side[3],
              const float light[3], const float* hueMat = nullptr, bool glass = false);
    void releaseGl();
    bool ok() const { return mVbo != 0; }
    static void releaseProgram();   // the shared program (context loss, park)

private:
    GLuint mVbo = 0, mIbo = 0, mSdf = 0, mColor = 0;
    float mColorMap[4] = {1, 0, 1, 0};   // face uv -> colour texture uv (scale, offset per axis)
    int mSideIndices = 0, mCapIndices = 0;   // sides first, then the two caps
    static GLuint sProgram;
    static GLint sMv, sProj, sNm, sFace, sSide, sLight, sCap, sHasColor, sColorMap, sHue, sGlass;
};

}  // namespace ctr
}  // namespace android
