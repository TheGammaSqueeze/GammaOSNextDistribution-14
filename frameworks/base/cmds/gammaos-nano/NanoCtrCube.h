// NanoCtrCube - the category cube on the 3DS theme's top screen: a small glass cube in the
// right hint bar (where the console shows the camera) with a category pictogram on each face,
// turning the face of the category on the grid to the front and swaying while it waits. The L
// and R glyphs next to it say how to turn it.
#pragma once

#include <GLES2/gl2.h>

#include <cstdint>
#include <memory>

namespace android {
namespace ctr {

class Assets;
struct Texture;

class CategoryCube {
public:
    struct Face {
        const uint8_t* rgba = nullptr;   // the pictogram, rows top first, its alpha the shape
        int w = 0, h = 0;
        float color[3] = {1, 1, 1};      // the face's glass colour
    };
    static constexpr int kFaces = 6;     // +Z +X -Z -X +Y -Y

    CategoryCube();
    ~CategoryCube();
    // The six faces' pictograms and colours (uploaded as textures; the pixels are not kept).
    void setFaces(Assets& assets, const Face faces[kFaces]);
    // The face to show: it eases to the front from wherever the cube is.
    void setCurrent(int face);
    bool ready() const { return mReady; }
    // Draws the cube centred on (cx, cy) in layout units, `size` units an edge, with the layout's
    // projection (x, y -> clip). t is a clock in seconds for the sway and the easing.
    void draw(const float proj[16], float cx, float cy, float size, double t);
    void releaseGl();

private:
    struct Impl;
    std::unique_ptr<Impl> mImpl;
    bool mReady = false;
};

}  // namespace ctr
}  // namespace android
