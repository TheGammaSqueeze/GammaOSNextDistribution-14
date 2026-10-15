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
    bool coloured() const { return mColor != 0; }
    // Draws with the given model view and projection (column major), into the bound target with
    // depth testing. face / side: linear RGB colours of the faces and the walls; light: direction
    // the light travels, in view space.
    void draw(const float modelView[16], const float projection[16], const float face[3], const float side[3],
              const float light[3]);
    void releaseGl();
    bool ok() const { return mVbo != 0; }
    static void releaseProgram();   // the shared program (context loss, park)

private:
    GLuint mVbo = 0, mIbo = 0, mSdf = 0, mColor = 0;
    float mColorMap[4] = {1, 0, 1, 0};   // face uv -> colour texture uv (scale, offset per axis)
    int mSideIndices = 0, mCapIndices = 0;   // sides first, then the two caps
    static GLuint sProgram;
    static GLint sMv, sProj, sNm, sFace, sSide, sLight, sCap, sHasColor, sColorMap;
};

}  // namespace ctr
}  // namespace android
