// Host test harness for the nano 3DS theme layout engine (NanoCtrPack/Layout/Render).
// Renders layouts offscreen with Mesa EGL + GLES2 and writes a PPM, for pixel comparison with
// the 3ds-web viewer. Not part of the device build.
//
//   ctrhost <pack dir> <out.ppm> <scale> <screen top|bottom> <archive>/<layout>[@anim:frame[,anim:frame]] ...
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "../../NanoCtrLayout.h"

using namespace android::ctr;

bool initEgl(int w, int h) {
    EGLDisplay dpy = EGL_NO_DISPLAY;
    auto getPlatformDisplay = (PFNEGLGETPLATFORMDISPLAYEXTPROC)eglGetProcAddress("eglGetPlatformDisplayEXT");
    if (getPlatformDisplay) dpy = getPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, nullptr);
    if (dpy == EGL_NO_DISPLAY) dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (!eglInitialize(dpy, nullptr, nullptr)) { fprintf(stderr, "eglInitialize failed\n"); return false; }
    eglBindAPI(EGL_OPENGL_ES_API);
    const EGLint cfgAttr[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                              EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig cfg;
    EGLint n = 0;
    if (!eglChooseConfig(dpy, cfgAttr, &cfg, 1, &n) || n == 0) {
        const EGLint cfg2[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE};
        if (!eglChooseConfig(dpy, cfg2, &cfg, 1, &n) || n == 0) { fprintf(stderr, "no EGL config\n"); return false; }
    }
    const EGLint ctxAttr[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr);
    if (ctx == EGL_NO_CONTEXT) { fprintf(stderr, "no context\n"); return false; }
    EGLSurface surf = EGL_NO_SURFACE;
    const EGLint pb[] = {EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE};
    surf = eglCreatePbufferSurface(dpy, cfg, pb);
    if (!eglMakeCurrent(dpy, surf, surf, ctx)) { fprintf(stderr, "makeCurrent failed\n"); return false; }
    return true;
}

#ifndef CTRHOST_NO_MAIN
int main(int argc, char** argv) {
    if (argc < 6) {
        fprintf(stderr, "usage: ctrhost <packdir> <out.ppm> <scale> <top|bottom> <archive>/<layout>[@anim:frame,...] ...\n");
        return 2;
    }
    const std::string packDir = argv[1], out = argv[2];
    const int scale = atoi(argv[3]);
    const bool top = std::string(argv[4]) == "top";
    const int lw = top ? 400 : 320, lh = 240;
    const int w = lw * scale, h = lh * scale;
    if (!initEgl(w, h)) return 1;

    GLuint fbo, tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);

    Assets assets(packDir);
    assets.setLanguage(getenv("CTR_LANG") ? getenv("CTR_LANG") : "English");
    std::vector<std::unique_ptr<LayoutInstance>> layouts;
    for (int i = 5; i < argc; i++) {
        std::string spec = argv[i];
        std::string anims;
        const size_t at = spec.find('@');
        if (at != std::string::npos) { anims = spec.substr(at + 1); spec = spec.substr(0, at); }
        const size_t slash = spec.find('/');
        auto inst = assets.create(spec.substr(0, slash), spec.substr(slash + 1));
        if (!inst) { fprintf(stderr, "cannot create %s\n", spec.c_str()); return 1; }
        size_t p = 0;
        float lastFrame = 0;
        while (!anims.empty() && p < anims.size()) {
            size_t q = anims.find(',', p);
            std::string a = anims.substr(p, q == std::string::npos ? std::string::npos : q - p);
            float frame = 0;
            const size_t c = a.find(':');
            if (c != std::string::npos) { frame = (float)atof(a.c_str() + c + 1); a = a.substr(0, c); }
            PlayOptions opt;
            // like the web viewer: one shot, whole layout, paused, every clip seeked at the end
            opt.groups = getenv("CTR_GROUPS") ? 1 : 0;
            opt.loop = 0;
            opt.paused = true;
            Animator* an = inst->play(a, opt);
            if (!an) fprintf(stderr, "no animation %s\n", a.c_str());
            lastFrame = frame;
            if (q == std::string::npos) break;
            p = q + 1;
        }
        if (const char* hide = getenv("CTR_HIDE")) {
            std::string hs = hide;
            size_t a = 0;
            while (a <= hs.size()) {
                size_t b = hs.find(',', a);
                std::string pn = hs.substr(a, b == std::string::npos ? std::string::npos : b - a);
                inst->setVisible(pn, false);
                if (b == std::string::npos) break;
                a = b + 1;
            }
        }
        for (auto& an : inst->animators) an->seek(lastFrame);
        inst->refresh();
        layouts.push_back(std::move(inst));
    }
    glViewport(0, 0, w, h);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    const char* cc = getenv("CTR_CLEAR");
    int cr = 0, cg = 0, cb = 0;
    if (cc) sscanf(cc, "%d,%d,%d", &cr, &cg, &cb);
    glClearColor(cr / 255.0f, cg / 255.0f, cb / 255.0f, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    Renderer& r = assets.renderer();
    r.setViewport(0, 0, w, h);
    r.begin((float)lw, (float)lh);
    for (auto& l : layouts) r.drawLayout(l->layout());
    r.end();   // draws are recorded and issued at end()
    glFinish();
    std::vector<uint8_t> px((size_t)w * h * 4);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    FILE* f = fopen(out.c_str(), "wb");
    if (!f) return 1;
    fprintf(f, "P6\n%d %d\n255\n", w, h);
    for (int y = h - 1; y >= 0; y--)
        for (int x = 0; x < w; x++) fwrite(&px[((size_t)y * w + x) * 4], 1, 3, f);
    fclose(f);
    fprintf(stderr, "draw calls %d, GL error 0x%x\n", r.drawCalls(), glGetError());
    return 0;
}
#endif
