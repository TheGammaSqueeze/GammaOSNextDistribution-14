// DisplayBackend.h
//
// The display backend seam for drastic-nano.
//
// drastic-nano produces emulated DS frames into GL textures and then has to get
// those pixels onto the panel. Two ways exist to do that, and they differ only at
// the edges: how EGL and the render target come up, how a finished frame reaches
// the panel, how many panels there are, and how the panel is blanked. Everything
// in between (the DraStic core, the overlay, the OSK, RetroAchievements, input)
// is identical. This interface captures exactly those edges so the render loop
// drives either path through one set of calls.
//
// Two implementations exist:
//   - DrmDisplayBackend grabs DRM master and page-flips AHardwareBuffer-backed
//     FBOs straight to the panel CRTCs. It forwards to the existing NanoMenuDrm
//     code, so the DRM path behaves exactly as it always has.
//   - SfDisplayBackend is an ordinary SurfaceFlinger client: it renders into one
//     or more SurfaceControl layers and lets the system compositor scan them out,
//     so drastic-nano runs on devices whose panels belong to SurfaceFlinger.
//
// createDisplayBackend() picks the implementation from a property, defaulting to
// DRM so existing devices are untouched.

#pragma once

#include <cstdint>
#include <memory>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

namespace drastic_nano {

// The EGL handles and logical primary size a backend hands the render loop after
// it brings the display up. This mirrors the fields main() has always carried in
// its Display struct, so the loop body reads them the same way.
struct DisplayEnv {
    EGLDisplay eglDpy = EGL_NO_DISPLAY;
    EGLContext eglCtx = EGL_NO_CONTEXT;
    EGLSurface eglSurf = EGL_NO_SURFACE;   // current draw surface for makeCurrent
    int width = 0;                          // logical primary present width
    int height = 0;                         // logical primary present height
    const char* backendName = "";
};

// How the render loop arranges the two DS screens this run. The backend owns the
// decision; the loop switches on it.
//   kDualTarget   - two physical targets: top DS to the primary, bottom DS to the
//                   secondary. This is the two-panel handheld shape.
//   kLayoutPreset - one target holding both DS screens, placed by DsScreenLayout.
//                   This is the single-panel / single-window shape and is shared
//                   by the DRM single-panel path and the SurfaceFlinger window.
enum class ComposeMode {
    kDualTarget,
    kLayoutPreset,
};

// The render targets to draw into this frame. For DRM these are the AHB ring FBOs
// for the slot the backend just acquired; for SurfaceFlinger they are the window
// surfaces' default framebuffer (0) for each driven display. The width/height are
// the target's drawable size, which the loop uses to set the viewport.
struct FrameTargets {
    GLuint   primaryFbo   = 0;
    GLuint   secondaryFbo = 0;     // 0 when there is no second target
    uint32_t primaryW = 0, primaryH = 0;
    uint32_t secondaryW = 0, secondaryH = 0;
    int      slot = 0;             // ring index (DRM); 0 (SF)
};

// The backend contract. One instance per session, owned by main().
class IDisplayBackend {
public:
    virtual ~IDisplayBackend() = default;

    // Brings up EGL plus the present surface(s) and fills env. Returns false if
    // the display cannot be acquired, in which case main() reports the failure
    // and exits (or, for backend=auto, the caller falls back to DRM).
    virtual bool createContext(DisplayEnv* env) = 0;

    // Display topology, known after createContext().
    virtual int  displayCount() const = 0;     // number of physical panels driven
    virtual bool hasSecondary() const = 0;     // a real second target exists
    virtual void primarySize(uint32_t* w, uint32_t* h) const = 0;
    virtual void secondarySize(uint32_t* w, uint32_t* h) const = 0;

    // Compose shape for this run. kDualTarget when two targets are driven,
    // kLayoutPreset when both DS screens share the primary target.
    virtual ComposeMode composeMode() const = 0;

    // Per-frame target acquisition. On DRM this advances the triple-buffer ring
    // and returns that slot's FBOs; on SF it makes the primary window current and
    // returns framebuffer 0. bindPrimary()/bindSecondary() bind the matching FBO
    // (and, on SF dual, make the matching window's EGL surface current) so the
    // loop's draw calls land on the right target.
    virtual FrameTargets acquireFrameTargets() = 0;
    virtual void bindPrimary() = 0;
    virtual void bindSecondary() = 0;          // a no-op when !hasSecondary()

    // Hands the finished frame(s) to the panel. DRM creates the slot fence,
    // advances the ring, and page-flips each CRTC; SF calls eglSwapBuffers on each
    // window surface. The loop passes the targets it just drew.
    virtual void present(const FrameTargets& targets) = 0;

    // Paces the loop to the panel vblank after present(). DRM waits on the vblank
    // ioctl / drains page-flip events; SF relies on the BufferQueue to pace
    // eglSwapBuffers, so this is a no-op there.
    virtual void waitForVsync() = 0;

    // Rotation. usesPanelNativeViewport() is true when the loop must set the
    // viewport to the panel-native size and let the rotation matrix pre-rotate
    // vertices (the DRM PRIME scanout shape); it is false when the target is
    // already display-space (SF) and the loop uses the logical size with an
    // identity matrix. rotationMatrix() fills a column-major 2x2 NDC matrix.
    virtual void rotationMatrix(float out[4]) const = 0;
    virtual bool usesPanelNativeViewport() const = 0;

    // Power. blank() clears the panel(s) for sleep; resume() restores them on
    // wake. DRM clears its ring and page-flips / recommits the modeset; SF pushes
    // a black frame or drives the display power mode.
    virtual void blank() = 0;
    virtual void resume() = 0;

    // Releases the display. DRM drops master and closes the card fd; SF reparents
    // its layer(s) to null so the compositor recomposites the home behind them.
    virtual void teardown() = 0;

    virtual const char* name() const = 0;
};

// Constructs the backend chosen by persist.gammaos.drastic_nano.backend
// (drm | sf | auto). Defaults to drm. For auto and sf, a failed SurfaceFlinger
// bring-up falls back to DRM unless persist.gammaos.drastic_nano.sf_strict is 1.
std::unique_ptr<IDisplayBackend> createDisplayBackend();

} // namespace drastic_nano
